from __future__ import annotations

from pathlib import Path

import numpy as np
from astropy.io import fits


FINAL_STACK_METHOD = "sigma-clipped-mean-v1"
FINAL_STACK_SIGMA = 3.0
FINAL_STACK_TILE_ROWS = 32
FINAL_STACK_CUBE_BUDGET_BYTES = 64 * 1024 * 1024


def _shape_and_header(path: Path) -> tuple[tuple[int, int], fits.Header]:
    with fits.open(path, memmap=True) as hdul:
        data = np.squeeze(np.asarray(hdul[0].data))
        header = hdul[0].header.copy()
        if data.ndim != 2:
            raise ValueError(f"Dimensions FITS inattendues: {data.shape}")
        return (int(data.shape[0]), int(data.shape[1])), header


def _safe_tile_rows(
    *,
    frame_count: int,
    width: int,
    requested_rows: int,
) -> int:
    bytes_per_row = max(1, frame_count * width * np.dtype(np.float32).itemsize)
    budget_rows = max(1, FINAL_STACK_CUBE_BUDGET_BYTES // bytes_per_row)
    return max(1, min(int(requested_rows), int(budget_rows)))


def build_sigma_clipped_stack(
    paths: list[Path],
    destination: Path,
    *,
    sigma: float = FINAL_STACK_SIGMA,
    tile_rows: int = FINAL_STACK_TILE_ROWS,
) -> dict:
    if not paths:
        raise ValueError("Aucune image enregistrée à combiner")

    shape, header = _shape_and_header(paths[0])
    for path in paths[1:]:
        other_shape, _ = _shape_and_header(path)
        if other_shape != shape:
            raise ValueError("Dimensions différentes dans le stack")

    effective_tile_rows = _safe_tile_rows(
        frame_count=len(paths),
        width=shape[1],
        requested_rows=max(1, int(tile_rows)),
    )
    final = np.zeros(shape, dtype=np.float32)

    # One tile at a time bounds RAM. Each FITS is opened only long enough to
    # copy that tile, so a multi-hour session cannot hit the process open-file
    # descriptor limit when hundreds or thousands of frames are accepted.
    for y0 in range(0, shape[0], effective_tile_rows):
        y1 = min(shape[0], y0 + effective_tile_rows)
        cube = np.empty(
            (len(paths), y1 - y0, shape[1]),
            dtype=np.float32,
        )
        for index, path in enumerate(paths):
            with fits.open(path, memmap=True) as hdul:
                cube[index, :, :] = np.asarray(
                    hdul[0].data[y0:y1, :],
                    dtype=np.float32,
                )

        cube[~np.isfinite(cube)] = np.nan
        center = np.nanmedian(cube, axis=0)
        mad = np.nanmedian(np.abs(cube - center[None, :, :]), axis=0)
        robust_sigma = np.maximum(1.4826 * mad, 1.0)
        accepted = np.abs(cube - center[None, :, :]) <= (
            float(sigma) * robust_sigma[None, :, :]
        )
        accepted &= np.isfinite(cube)

        clipped_sum = np.nansum(np.where(accepted, cube, np.nan), axis=0)
        clipped_count = np.sum(accepted, axis=0)
        tile = np.divide(
            clipped_sum,
            clipped_count,
            out=center.astype(np.float64, copy=True),
            where=clipped_count > 0,
        )
        final[y0:y1, :] = tile.astype(np.float32)

    header["SPSTACK"] = (True, "StellarPilot final stack")
    header["SPMETH"] = (FINAL_STACK_METHOD, "Final stack combination method")
    header["SPNFRM"] = (len(paths), "Registered accepted frames combined")
    header["SPSIGMA"] = (float(sigma), "Sigma clipping threshold")

    destination.parent.mkdir(parents=True, exist_ok=True)
    fits.PrimaryHDU(data=final, header=header).writeto(
        destination,
        overwrite=True,
    )

    return {
        "status": "ready",
        "path": str(destination),
        "method": FINAL_STACK_METHOD,
        "sigma": float(sigma),
        "frames": len(paths),
        "tile_rows": effective_tile_rows,
    }
