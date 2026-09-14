from __future__ import annotations

import csv
import json
import math
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np
from astropy.io import fits

from app.imaging.dark_capture import capture_dark_frame
from app.imaging.quality import analyze_fits
from app.indi.service import indi_service
from app.setup.service import setup_service


SERVER_ROOT = Path(__file__).resolve().parents[2]
DARK_ROOT = SERVER_ROOT / "data" / "calibration" / "darks"
MASTER_METHOD = "sigma-clipped-mean-v1"
MASTER_SIGMA = 3.0
HOT_PIXEL_SIGMA = 8.0
HOT_PIXEL_MIN_EXCESS_ADU = 32.0
TEMPERATURE_TOLERANCE_C = 2.0
DARK_SERIES_SIGMA = 6.0
DARK_SERIES_MEDIAN_REL_TOLERANCE = 0.35
DARK_SERIES_NOISE_REL_TOLERANCE = 0.50
DARK_SERIES_MIN_TOLERANCE_ADU = 8.0


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _session_path(session_id: str) -> Path:
    return DARK_ROOT / session_id


def _metadata_path(session_id: str) -> Path:
    return _session_path(session_id) / "session.json"


def _write(metadata: dict) -> dict:
    path = _metadata_path(metadata["id"])
    path.parent.mkdir(parents=True, exist_ok=True)
    metadata["updated_at"] = _now().isoformat(timespec="seconds")
    temporary = path.with_suffix(".json.tmp")
    temporary.write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    temporary.replace(path)
    return metadata


def _read(session_id: str) -> dict:
    path = _metadata_path(session_id)
    if not path.exists():
        raise KeyError(session_id)
    return json.loads(path.read_text(encoding="utf-8"))


def _camera_profile(snapshot: dict[str, Any], exposure_s: float) -> dict[str, Any]:
    camera = snapshot.get("camera") or {}
    sensor = camera.get("sensor") or {}
    capture = camera.get("capture") or {}
    return {
        "status": camera.get("status"),
        "name": camera.get("name"),
        "sensor": {
            "width": sensor.get("width"),
            "height": sensor.get("height"),
            "pixel_size_um": sensor.get("pixel_size_um"),
            "pixel_size_x_um": sensor.get("pixel_size_x_um"),
            "pixel_size_y_um": sensor.get("pixel_size_y_um"),
            "bits_per_pixel": sensor.get("bits_per_pixel"),
        },
        "capture": {
            "exposure_s": float(exposure_s),
            "gain": capture.get("gain"),
            "offset": capture.get("offset"),
            "bin_x": capture.get("bin_x"),
            "bin_y": capture.get("bin_y"),
            "frame_width": capture.get("frame_width"),
            "frame_height": capture.get("frame_height"),
            "frame_type": "dark",
        },
        "temperature_c": camera.get("temperature_c"),
    }


def _snapshot_setup(exposure_s: float) -> dict[str, Any]:
    try:
        indi_snapshot = indi_service.status_snapshot()
    except Exception as exc:
        indi_snapshot = {
            "mount": {"status": "unavailable", "name": None},
            "camera": {"status": "unavailable", "name": None},
            "location": {"status": "unavailable"},
            "detail": str(exc),
        }

    try:
        optical = setup_service.status(indi_snapshot)
    except Exception as exc:
        optical = {
            "status": "unavailable",
            "source": "kstars",
            "detail": str(exc),
        }

    return {
        "captured_at": _now().isoformat(timespec="seconds"),
        "camera": _camera_profile(indi_snapshot, exposure_s),
        "mount": indi_snapshot.get("mount") or {},
        "optical": optical,
    }


def _fits_profile(path: Path) -> dict[str, Any]:
    with fits.open(path, memmap=False) as hdul:
        header = hdul[0].header
        data = np.asarray(hdul[0].data)
        data = np.squeeze(data)
        if data.ndim != 2:
            raise ValueError(f"Dimensions FITS inattendues: {data.shape}")

        return {
            "width": int(data.shape[1]),
            "height": int(data.shape[0]),
            "bitpix": int(header.get("BITPIX", 0)),
            "bzero": float(header.get("BZERO", 0.0)),
            "bayer_pattern": (
                str(header.get("BAYERPAT", "")).strip().upper() or None
            ),
            "image_type": (
                str(
                    header.get(
                        "IMAGETYP",
                        header.get("FRAME", ""),
                    )
                ).strip()
                or None
            ),
            "exposure_s": _header_number(
                header,
                "EXPTIME",
                "EXPOSURE",
            ),
            "gain": _header_number(
                header,
                "GAIN",
                "EGAIN",
            ),
            "offset": _header_number(
                header,
                "OFFSET",
                "BLACKLEV",
            ),
            "temperature_c": _header_number(
                header,
                "CCD-TEMP",
                "CCDTEMP",
                "SENSOR_T",
            ),
        }


def _header_number(header: fits.Header, *keys: str) -> float | None:
    for key in keys:
        value = header.get(key)
        if value is None:
            continue
        try:
            return float(value)
        except (TypeError, ValueError):
            continue
    return None


def _frame_temperature() -> float | None:
    try:
        snapshot = indi_service.status_snapshot()
        return (snapshot.get("camera") or {}).get("temperature_c")
    except Exception:
        return None


def _quality_number(value: Any) -> float | None:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if math.isfinite(number) else None


def _series_reference(
    values: list[float],
    *,
    relative_tolerance: float,
) -> tuple[float, float, float]:
    center = float(np.median(values))
    mad = float(np.median(np.abs(np.asarray(values, dtype=np.float64) - center)))
    robust_sigma = 1.4826 * mad
    tolerance = max(
        DARK_SERIES_SIGMA * robust_sigma,
        abs(center) * relative_tolerance,
        DARK_SERIES_MIN_TOLERANCE_ADU,
    )
    return center, tolerance, robust_sigma


def _apply_series_quality_filter(metadata: dict[str, Any]) -> dict[str, Any]:
    frames = metadata.get("frames", [])
    candidates: list[dict[str, Any]] = []

    for frame in frames:
        capture_valid = bool(frame.get("capture_valid", frame.get("valid")))
        frame["capture_valid"] = capture_valid
        if not capture_valid:
            frame["valid"] = False
            frame["series_quality"] = {"status": "skipped"}
            continue

        median = _quality_number(frame.get("median"))
        noise = _quality_number(frame.get("background_sigma"))
        if median is not None and noise is not None:
            candidates.append(frame)

    if len(candidates) < 3:
        metadata["valid_count"] = sum(
            1 for frame in frames if frame.get("valid")
        )
        summary = {
            "status": "insufficient_samples",
            "sample_count": len(candidates),
            "rejected_count": 0,
        }
        metadata["series_quality"] = summary
        return summary

    medians = [float(frame["median"]) for frame in candidates]
    noises = [float(frame["background_sigma"]) for frame in candidates]
    median_ref, median_tol, median_sigma = _series_reference(
        medians,
        relative_tolerance=DARK_SERIES_MEDIAN_REL_TOLERANCE,
    )
    noise_ref, noise_tol, noise_sigma = _series_reference(
        noises,
        relative_tolerance=DARK_SERIES_NOISE_REL_TOLERANCE,
    )

    rejected = 0
    for frame in frames:
        capture_valid = bool(frame.get("capture_valid", frame.get("valid")))
        if not capture_valid:
            continue

        reasons: list[str] = []
        median = _quality_number(frame.get("median"))
        noise = _quality_number(frame.get("background_sigma"))

        if median is None or noise is None:
            reasons.append("quality_metrics_unavailable")
        else:
            if abs(median - median_ref) > median_tol:
                reasons.append("median_outlier")
            if abs(noise - noise_ref) > noise_tol:
                reasons.append("background_noise_outlier")

        frame["series_quality"] = {
            "status": "rejected" if reasons else "ok",
            "median_reference": round(median_ref, 3),
            "median_tolerance": round(median_tol, 3),
            "background_sigma_reference": round(noise_ref, 3),
            "background_sigma_tolerance": round(noise_tol, 3),
        }
        frame["valid"] = not reasons

        if reasons:
            rejected += 1
            frame["rejection_reasons"] = reasons
        else:
            frame.pop("rejection_reasons", None)

    metadata["valid_count"] = sum(
        1 for frame in frames if frame.get("valid")
    )
    summary = {
        "status": "ok",
        "sample_count": len(candidates),
        "rejected_count": rejected,
        "median_reference": round(median_ref, 3),
        "median_robust_sigma": round(median_sigma, 3),
        "median_tolerance": round(median_tol, 3),
        "background_sigma_reference": round(noise_ref, 3),
        "background_sigma_robust_sigma": round(noise_sigma, 3),
        "background_sigma_tolerance": round(noise_tol, 3),
    }
    metadata["series_quality"] = summary
    return summary


def _compatibility_profile(metadata: dict[str, Any]) -> dict[str, Any]:
    setup = metadata.get("setup_profile") or {}
    camera = setup.get("camera") or {}
    capture = camera.get("capture") or {}
    sensor = camera.get("sensor") or {}
    fits_profile = metadata.get("fits_profile") or {}

    temperatures = [
        frame.get("temperature_c")
        for frame in metadata.get("frames", [])
        if frame.get("valid") and frame.get("temperature_c") is not None
    ]
    temperature_c = (
        round(float(np.median(temperatures)), 2)
        if temperatures
        else camera.get("temperature_c")
    )

    return {
        "camera": camera.get("name"),
        "exposure_s": float(metadata["exposure_s"]),
        "gain": capture.get("gain"),
        "offset": capture.get("offset"),
        "bin_x": capture.get("bin_x"),
        "bin_y": capture.get("bin_y"),
        "frame_width": fits_profile.get("width") or capture.get("frame_width") or sensor.get("width"),
        "frame_height": fits_profile.get("height") or capture.get("frame_height") or sensor.get("height"),
        "bits_per_pixel": sensor.get("bits_per_pixel"),
        "bayer_pattern": fits_profile.get("bayer_pattern"),
        "temperature_c": temperature_c,
        "temperature_tolerance_c": TEMPERATURE_TOLERANCE_C,
    }


def start_dark_session(
    *,
    exposure_s: float = 4.0,
    requested_count: int = 20,
) -> dict:
    if not 0.001 <= exposure_s <= 3600.0:
        raise ValueError("exposure_s hors limites")
    if not 1 <= requested_count <= 100:
        raise ValueError("requested_count hors limites")

    now = _now()
    session_id = now.strftime("%Y%m%dT%H%M%SZ") + f"_{now.microsecond:06d}"
    root = _session_path(session_id)
    (root / "raw").mkdir(parents=True, exist_ok=False)
    (root / "products").mkdir(parents=True, exist_ok=True)

    metadata = {
        "id": session_id,
        "created_at": now.isoformat(timespec="seconds"),
        "updated_at": now.isoformat(timespec="seconds"),
        "status": "ready",
        "exposure_s": float(exposure_s),
        "requested_count": int(requested_count),
        "captured_count": 0,
        "valid_count": 0,
        "frames": [],
        "storage": str(root),
        "setup_profile": _snapshot_setup(float(exposure_s)),
        "fits_profile": None,
        "compatibility": None,
        "series_quality": None,
        "master_dark": None,
        "hot_pixels": None,
    }
    return _write(metadata)


def capture_dark(session_id: str) -> dict:
    metadata = _read(session_id)
    requested = int(metadata["requested_count"])
    captured = int(metadata["captured_count"])

    if captured >= requested:
        if not metadata.get("master_dark"):
            _finalize_dark_products(metadata)
        metadata["status"] = "complete"
        return _write(metadata)

    index = captured + 1
    result = capture_dark_frame(
        float(metadata["exposure_s"]),
        output_dir=_session_path(session_id) / "raw",
        prefix=f"dark_{index:03d}",
    )
    if result.get("status") != "captured" or not result.get("image"):
        metadata["status"] = "error"
        metadata["detail"] = result.get("detail", "Capture dark impossible")
        _write(metadata)
        return metadata

    path = Path(result["image"])
    quality = analyze_fits(str(path))
    try:
        fits_profile = _fits_profile(path)
    except Exception as exc:
        fits_profile = {"error": str(exc)}

    if metadata.get("fits_profile") is None and "error" not in fits_profile:
        metadata["fits_profile"] = fits_profile

    reference_profile = metadata.get("fits_profile") or {}
    same_geometry = (
        not reference_profile
        or (
            fits_profile.get("width") == reference_profile.get("width")
            and fits_profile.get("height") == reference_profile.get("height")
            and fits_profile.get("bayer_pattern") == reference_profile.get("bayer_pattern")
        )
    )

    valid = (
        path.exists()
        and path.stat().st_size > 0
        and quality.get("status") == "ok"
        and float(quality.get("saturated_percent") or 0.0) < 1.0
        and "error" not in fits_profile
        and same_geometry
    )

    temperature_c = _frame_temperature()
    if temperature_c is None:
        temperature_c = fits_profile.get("temperature_c")

    metadata["captured_count"] = index
    if valid:
        metadata["valid_count"] = int(metadata["valid_count"]) + 1

    metadata["frames"].append(
        {
            "index": index,
            "image": str(path),
            "size_bytes": path.stat().st_size if path.exists() else None,
            "valid": valid,
            "capture_valid": valid,
            "frame_type": result.get("frame_type", "dark"),
            "temperature_c": temperature_c,
            "median": quality.get("median"),
            "background_sigma": quality.get("background_sigma"),
            "saturated_percent": quality.get("saturated_percent"),
            "maximum": quality.get("maximum"),
            "fits": fits_profile,
        }
    )

    if index >= requested:
        metadata["status"] = "processing"
        _write(metadata)
        try:
            _finalize_dark_products(metadata)
            metadata["status"] = "complete"
            metadata.pop("detail", None)
        except Exception as exc:
            metadata["status"] = "error"
            metadata["detail"] = f"Création Master Dark impossible: {exc}"
    else:
        metadata["status"] = "capturing"
        metadata.pop("detail", None)

    return _write(metadata)


def _load_frame(path: Path) -> tuple[np.ndarray, fits.Header]:
    with fits.open(path, memmap=False) as hdul:
        data = np.asarray(hdul[0].data)
        header = hdul[0].header.copy()
    data = np.squeeze(data)
    if data.ndim != 2:
        raise ValueError(f"Dimensions FITS inattendues: {data.shape}")
    return data.astype(np.float32, copy=False), header


def _build_master(paths: list[Path], destination: Path) -> tuple[np.ndarray, fits.Header]:
    if len(paths) < 3:
        raise ValueError("Au moins 3 darks valides sont nécessaires")

    first, header = _load_frame(paths[0])
    shape = first.shape
    total = np.zeros(shape, dtype=np.float64)
    total_sq = np.zeros(shape, dtype=np.float64)

    for path in paths:
        data, _ = _load_frame(path)
        if data.shape != shape:
            raise ValueError("Dimensions différentes dans la série de darks")
        total += data
        total_sq += data.astype(np.float64) ** 2

    count = float(len(paths))
    mean = total / count
    variance = np.maximum(total_sq / count - mean * mean, 0.0)
    std = np.sqrt(variance)
    del total, total_sq, variance

    clipped_sum = np.zeros(shape, dtype=np.float64)
    clipped_count = np.zeros(shape, dtype=np.uint16)
    threshold = np.maximum(std * MASTER_SIGMA, 1.0)

    for path in paths:
        data, _ = _load_frame(path)
        accepted = np.abs(data - mean) <= threshold
        clipped_sum[accepted] += data[accepted]
        clipped_count[accepted] += 1

    master = np.divide(
        clipped_sum,
        clipped_count,
        out=mean,
        where=clipped_count > 0,
    ).astype(np.float32)

    header["IMAGETYP"] = ("MASTER DARK", "StellarPilot calibration product")
    header["SPDARK"] = (True, "StellarPilot master dark")
    header["SPNFRM"] = (len(paths), "Valid dark frames combined")
    header["SPMETH"] = (MASTER_METHOD, "Master dark combination method")
    header["SPSIGMA"] = (MASTER_SIGMA, "Sigma clipping threshold")

    destination.parent.mkdir(parents=True, exist_ok=True)
    fits.PrimaryHDU(data=master, header=header).writeto(destination, overwrite=True)
    return master, header


def _bayer_channel(pattern: str | None, y: int, x: int) -> str:
    matrices = {
        "RGGB": (("R", "G1"), ("G2", "B")),
        "BGGR": (("B", "G1"), ("G2", "R")),
        "GRBG": (("G1", "R"), ("B", "G2")),
        "GBRG": (("G1", "B"), ("R", "G2")),
    }
    matrix = matrices.get((pattern or "").upper())
    return matrix[y % 2][x % 2] if matrix else "MONO"


def _build_hot_pixel_map(
    master: np.ndarray,
    header: fits.Header,
    mask_path: Path,
    csv_path: Path,
    json_path: Path,
) -> dict[str, Any]:
    pattern = str(header.get("BAYERPAT", "")).strip().upper() or None
    hot_mask = np.zeros(master.shape, dtype=np.uint8)
    thresholds: list[dict[str, Any]] = []

    parity_planes = (
        [(0, 0), (0, 1), (1, 0), (1, 1)]
        if pattern in {"RGGB", "BGGR", "GRBG", "GBRG"}
        else [(0, 0)]
    )

    if len(parity_planes) == 1:
        samples = [(0, 0, master)]
    else:
        samples = [
            (row, col, master[row::2, col::2])
            for row, col in parity_planes
        ]

    for row, col, plane in samples:
        finite = plane[np.isfinite(plane)]
        if finite.size == 0:
            continue
        median = float(np.median(finite))
        mad = float(np.median(np.abs(finite - median)))
        sigma = max(1.4826 * mad, 1.0)
        threshold = median + max(HOT_PIXEL_SIGMA * sigma, HOT_PIXEL_MIN_EXCESS_ADU)
        plane_hot = plane > threshold

        if len(parity_planes) == 1:
            hot_mask[:, :] = plane_hot.astype(np.uint8)
        else:
            hot_mask[row::2, col::2] = plane_hot.astype(np.uint8)

        thresholds.append(
            {
                "channel": _bayer_channel(pattern, row, col),
                "row_parity": row,
                "col_parity": col,
                "median": round(median, 3),
                "sigma": round(sigma, 3),
                "threshold": round(threshold, 3),
                "count": int(np.count_nonzero(plane_hot)),
            }
        )

    hot_y, hot_x = np.nonzero(hot_mask)
    hot_count = int(hot_x.size)

    mask_header = header.copy()
    mask_header["IMAGETYP"] = ("HOT PIXEL MAP", "StellarPilot calibration product")
    mask_header["SPHOTPIX"] = (True, "StellarPilot hot-pixel mask")
    mask_header["HOTCOUNT"] = (hot_count, "Detected persistent hot pixels")
    mask_header["HOTSIGMA"] = (HOT_PIXEL_SIGMA, "Hot-pixel sigma threshold")
    fits.PrimaryHDU(data=hot_mask, header=mask_header).writeto(mask_path, overwrite=True)

    with csv_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["x", "y", "value", "channel"])
        for y, x in zip(hot_y.tolist(), hot_x.tolist()):
            writer.writerow(
                [
                    x,
                    y,
                    round(float(master[y, x]), 3),
                    _bayer_channel(pattern, y, x),
                ]
            )

    summary = {
        "count": hot_count,
        "sigma_threshold": HOT_PIXEL_SIGMA,
        "minimum_excess_adu": HOT_PIXEL_MIN_EXCESS_ADU,
        "bayer_pattern": pattern,
        "thresholds": thresholds,
        "mask_fits": str(mask_path),
        "coordinates_csv": str(csv_path),
    }
    json_path.write_text(
        json.dumps(summary, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    return {**summary, "summary_json": str(json_path)}


def _finalize_dark_products(metadata: dict[str, Any]) -> None:
    _apply_series_quality_filter(metadata)

    valid_paths = [
        Path(frame["image"])
        for frame in metadata.get("frames", [])
        if frame.get("valid") and frame.get("image")
    ]
    if len(valid_paths) < 3:
        raise ValueError("Pas assez de darks valides pour créer le Master Dark")

    products = _session_path(metadata["id"]) / "products"
    products.mkdir(parents=True, exist_ok=True)
    master_path = products / "master_dark.fits"
    hot_mask_path = products / "hot_pixels.fits"
    hot_csv_path = products / "hot_pixels.csv"
    hot_json_path = products / "hot_pixels.json"
    profile_path = products / "profile.json"

    master, header = _build_master(valid_paths, master_path)
    hot_pixels = _build_hot_pixel_map(
        master,
        header,
        hot_mask_path,
        hot_csv_path,
        hot_json_path,
    )

    compatibility = _compatibility_profile(metadata)
    profile = {
        "id": metadata["id"],
        "created_at": metadata["created_at"],
        "master_created_at": _now().isoformat(timespec="seconds"),
        "setup": metadata.get("setup_profile"),
        "fits": metadata.get("fits_profile"),
        "compatibility": compatibility,
        "series_quality": metadata.get("series_quality"),
        "requested_count": metadata.get("requested_count"),
        "valid_count": len(valid_paths),
        "master_method": MASTER_METHOD,
        "master_sigma": MASTER_SIGMA,
        "master_dark": str(master_path),
        "hot_pixels": hot_pixels,
    }
    profile_path.write_text(
        json.dumps(profile, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    metadata["compatibility"] = compatibility
    metadata["master_dark"] = {
        "path": str(master_path),
        "profile": str(profile_path),
        "method": MASTER_METHOD,
        "sigma": MASTER_SIGMA,
        "valid_frames": len(valid_paths),
        "rejected_frames": int(metadata.get("requested_count") or 0) - len(valid_paths),
    }
    metadata["hot_pixels"] = hot_pixels


def dark_status(session_id: str) -> dict:
    return _read(session_id)


def dark_library() -> dict[str, Any]:
    if not DARK_ROOT.exists():
        return {"status": "ready", "count": 0, "masters": []}

    masters: list[dict[str, Any]] = []
    for directory in sorted(DARK_ROOT.iterdir(), reverse=True):
        session_file = directory / "session.json"
        if not session_file.exists():
            continue
        try:
            metadata = json.loads(session_file.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue
        if not metadata.get("master_dark"):
            continue
        masters.append(
            {
                "id": metadata.get("id"),
                "created_at": metadata.get("created_at"),
                "compatibility": metadata.get("compatibility"),
                "master_dark": metadata.get("master_dark"),
                "hot_pixels": metadata.get("hot_pixels"),
                "setup_profile": metadata.get("setup_profile"),
            }
        )

    return {
        "status": "ready",
        "count": len(masters),
        "masters": masters,
    }
