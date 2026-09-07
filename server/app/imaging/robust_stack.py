from __future__ import annotations

from pathlib import Path

import numpy as np
from astropy.io import fits


FINAL_STACK_METHOD = "sigma-clipped-mean-v1"
FINAL_STACK_SIGMA = 3.0


def _load(path: Path) -> tuple[np.ndarray, fits.Header]:
    with fits.open(path, memmap=False) as hdul:
        data = np.squeeze(np.asarray(hdul[0].data))
        header = hdul[0].header.copy()
    if data.ndim != 2:
        raise ValueError(f"Dimensions FITS inattendues: {data.shape}")
    return data.astype(np.float32, copy=False), header


def build_sigma_clipped_stack(
    paths: list[Path],
    destination: Path,
    *,
    sigma: float = FINAL_STACK_SIGMA,
) -> dict:
    if not paths:
        raise ValueError("Aucune image enregistrée à combiner")

    first, header = _load(paths[0])
    shape = first.shape
    total = np.zeros(shape, dtype=np.float64)
    total_sq = np.zeros(shape, dtype=np.float64)
    count = np.zeros(shape, dtype=np.uint32)

    for path in paths:
        data, _ = _load(path)
        if data.shape != shape:
            raise ValueError("Dimensions différentes dans le stack")
        finite = np.isfinite(data)
        total[finite] += data[finite]
        total_sq[finite] += data[finite].astype(np.float64) ** 2
        count[finite] += 1

    mean = np.divide(
        total,
        count,
        out=np.zeros(shape, dtype=np.float64),
        where=count > 0,
    )
    second_moment = np.divide(
        total_sq,
        count,
        out=np.zeros(shape, dtype=np.float64),
        where=count > 0,
    )
    variance = np.maximum(second_moment - mean * mean, 0.0)
    std = np.sqrt(variance)
    del total, total_sq, second_moment, variance

    clipped_sum = np.zeros(shape, dtype=np.float64)
    clipped_count = np.zeros(shape, dtype=np.uint32)
    threshold = np.maximum(std * float(sigma), 1.0)

    for path in paths:
        data, _ = _load(path)
        finite = np.isfinite(data)
        accepted = finite & (np.abs(data - mean) <= threshold)
        clipped_sum[accepted] += data[accepted]
        clipped_count[accepted] += 1

    final = np.divide(
        clipped_sum,
        clipped_count,
        out=mean,
        where=clipped_count > 0,
    ).astype(np.float32)

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
    }
