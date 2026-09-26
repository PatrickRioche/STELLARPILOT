from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np
from astropy.io import fits

from app.imaging import darks
from app.imaging.flat_capture import capture_flat_frame
from app.imaging.quality import analyze_fits


SERVER_ROOT = Path(__file__).resolve().parents[2]
FLAT_ROOT = SERVER_ROOT / "data" / "calibration" / "flats"
MASTER_METHOD = "sigma-clipped-mean-normalized-v1"
MASTER_SIGMA = 3.0
MIN_VALID_FLATS = 5


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _session_path(session_id: str) -> Path:
    return FLAT_ROOT / session_id


def _metadata_path(session_id: str) -> Path:
    return _session_path(session_id) / "session.json"


def _write(metadata: dict[str, Any]) -> dict[str, Any]:
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


def _read(session_id: str) -> dict[str, Any]:
    path = _metadata_path(session_id)
    if not path.exists():
        raise KeyError(session_id)
    return json.loads(path.read_text(encoding="utf-8"))


def _number(value: Any) -> float | None:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if np.isfinite(number) else None


def _compatibility_profile(metadata: dict[str, Any]) -> dict[str, Any]:
    setup = metadata.get("setup_profile") or {}
    camera = setup.get("camera") or {}
    capture = camera.get("capture") or {}
    sensor = camera.get("sensor") or {}
    fits_profile = metadata.get("fits_profile") or {}

    # Unlike darks, flat exposure and temperature do not have to match LIGHTs.
    # The captured FITS is authoritative for settings that the driver writes.
    return {
        "camera": camera.get("name"),
        "gain": fits_profile.get("gain") if fits_profile.get("gain") is not None else capture.get("gain"),
        "offset": fits_profile.get("offset") if fits_profile.get("offset") is not None else capture.get("offset"),
        "bin_x": capture.get("bin_x"),
        "bin_y": capture.get("bin_y"),
        "frame_width": fits_profile.get("width") or capture.get("frame_width") or sensor.get("width"),
        "frame_height": fits_profile.get("height") or capture.get("frame_height") or sensor.get("height"),
        "bits_per_pixel": sensor.get("bits_per_pixel"),
        "bayer_pattern": fits_profile.get("bayer_pattern"),
    }


def start_flat_session(
    *,
    exposure_s: float = 0.2,
    requested_count: int = 20,
) -> dict[str, Any]:
    if not 0.0001 <= exposure_s <= 30.0:
        raise ValueError("exposure_s hors limites")
    if not MIN_VALID_FLATS <= requested_count <= 100:
        raise ValueError(f"requested_count doit être compris entre {MIN_VALID_FLATS} et 100")

    now = _now()
    session_id = now.strftime("%Y%m%dT%H%M%SZ") + f"_{now.microsecond:06d}"
    root = _session_path(session_id)
    (root / "raw").mkdir(parents=True, exist_ok=False)
    (root / "products").mkdir(parents=True, exist_ok=True)

    metadata: dict[str, Any] = {
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
        "setup_profile": darks._snapshot_setup(float(exposure_s)),
        "fits_profile": None,
        "compatibility": None,
        "master_flat": None,
        "calibration_policy": {
            "bias_frames_required": False,
            "dark_flat_required": False,
            "temperature_matching_required": False,
            "note": "r6: master flat normalisé sans série bias séparée",
        },
    }
    return _write(metadata)


def capture_flat(session_id: str) -> dict[str, Any]:
    metadata = _read(session_id)
    requested = int(metadata["requested_count"])
    captured = int(metadata["captured_count"])

    if captured >= requested:
        if not metadata.get("master_flat"):
            _finalize_flat_products(metadata)
        metadata["status"] = "complete"
        return _write(metadata)

    index = captured + 1
    result = capture_flat_frame(
        float(metadata["exposure_s"]),
        output_dir=_session_path(session_id) / "raw",
        prefix=f"flat_{index:03d}",
    )
    if result.get("status") != "captured" or not result.get("image"):
        metadata["status"] = "error"
        metadata["detail"] = result.get("detail", "Capture flat impossible")
        return _write(metadata)

    path = Path(result["image"])
    quality = analyze_fits(str(path))
    try:
        fits_profile = darks._fits_profile(path)
    except Exception as exc:
        fits_profile = {"error": str(exc)}

    if metadata.get("fits_profile") is None and "error" not in fits_profile:
        metadata["fits_profile"] = fits_profile

    reference = metadata.get("fits_profile") or {}
    same_geometry = (
        not reference
        or (
            fits_profile.get("width") == reference.get("width")
            and fits_profile.get("height") == reference.get("height")
            and fits_profile.get("bayer_pattern") == reference.get("bayer_pattern")
        )
    )

    median = _number(quality.get("median"))
    saturated = _number(quality.get("saturated_percent"))
    valid = (
        path.exists()
        and path.stat().st_size > 0
        and quality.get("status") == "ok"
        and median is not None
        and median > 0.0
        and saturated is not None
        and saturated < 5.0
        and "error" not in fits_profile
        and same_geometry
    )

    metadata["captured_count"] = index
    if valid:
        metadata["valid_count"] = int(metadata["valid_count"]) + 1

    metadata["frames"].append(
        {
            "index": index,
            "image": str(path),
            "size_bytes": path.stat().st_size if path.exists() else None,
            "valid": valid,
            "frame_type": result.get("frame_type", "flat"),
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
            _finalize_flat_products(metadata)
            metadata["status"] = "complete"
            metadata.pop("detail", None)
        except Exception as exc:
            metadata["status"] = "error"
            metadata["detail"] = f"Création Master Flat impossible: {exc}"
    else:
        metadata["status"] = "capturing"
        metadata.pop("detail", None)

    return _write(metadata)


def _load_frame(path: Path) -> tuple[np.ndarray, fits.Header]:
    with fits.open(path, memmap=False) as hdul:
        data = np.squeeze(np.asarray(hdul[0].data))
        header = hdul[0].header.copy()
    if data.ndim != 2:
        raise ValueError(f"Dimensions FITS inattendues: {data.shape}")
    return data.astype(np.float32, copy=False), header


def _normalize_master(master: np.ndarray, pattern: str | None) -> np.ndarray:
    normalized = master.astype(np.float32, copy=True)
    pattern = (pattern or "").upper()

    if pattern in {"RGGB", "BGGR", "GRBG", "GBRG"}:
        for row in range(2):
            for col in range(2):
                plane = normalized[row::2, col::2]
                finite = plane[np.isfinite(plane) & (plane > 0)]
                if finite.size == 0:
                    raise ValueError("Flat Bayer invalide: plan sans signal")
                level = float(np.median(finite))
                if level <= 0:
                    raise ValueError("Flat Bayer invalide: médiane nulle")
                plane /= level
    else:
        finite = normalized[np.isfinite(normalized) & (normalized > 0)]
        if finite.size == 0:
            raise ValueError("Flat invalide: aucun signal")
        level = float(np.median(finite))
        if level <= 0:
            raise ValueError("Flat invalide: médiane nulle")
        normalized /= level

    # Protect the division stage against dead/empty flat pixels without hiding
    # ordinary dust donuts or vignetting, which remain well above this floor.
    return np.clip(normalized, 0.05, 20.0)


def _build_master(paths: list[Path], destination: Path) -> tuple[np.ndarray, fits.Header]:
    if len(paths) < MIN_VALID_FLATS:
        raise ValueError(f"Au moins {MIN_VALID_FLATS} flats valides sont nécessaires")

    first, header = _load_frame(paths[0])
    shape = first.shape
    total = np.zeros(shape, dtype=np.float64)
    total_sq = np.zeros(shape, dtype=np.float64)

    for path in paths:
        data, _ = _load_frame(path)
        if data.shape != shape:
            raise ValueError("Dimensions différentes dans la série de flats")
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

    pattern = str(header.get("BAYERPAT", "")).strip().upper() or None
    master = _normalize_master(master, pattern)

    header["IMAGETYP"] = ("MASTER FLAT", "StellarPilot calibration product")
    header["SPFLAT"] = (True, "StellarPilot master flat")
    header["SPNFRM"] = (len(paths), "Valid flat frames combined")
    header["SPMETH"] = (MASTER_METHOD, "Master flat combination method")
    header["SPSIGMA"] = (MASTER_SIGMA, "Sigma clipping threshold")
    header["SPNORM"] = ("BAYER-PLANE" if pattern else "GLOBAL", "Flat normalization")

    destination.parent.mkdir(parents=True, exist_ok=True)
    fits.PrimaryHDU(data=master, header=header).writeto(destination, overwrite=True)
    return master, header


def _finalize_flat_products(metadata: dict[str, Any]) -> None:
    valid_paths = [
        Path(frame["image"])
        for frame in metadata.get("frames", [])
        if frame.get("valid") and frame.get("image")
    ]
    if len(valid_paths) < MIN_VALID_FLATS:
        raise ValueError("Pas assez de flats valides pour créer le Master Flat")

    products = _session_path(metadata["id"]) / "products"
    products.mkdir(parents=True, exist_ok=True)
    master_path = products / "master_flat.fits"
    profile_path = products / "profile.json"

    _master, _header = _build_master(valid_paths, master_path)
    compatibility = _compatibility_profile(metadata)

    profile = {
        "id": metadata["id"],
        "created_at": metadata["created_at"],
        "master_created_at": _now().isoformat(timespec="seconds"),
        "setup": metadata.get("setup_profile"),
        "fits": metadata.get("fits_profile"),
        "compatibility": compatibility,
        "requested_count": metadata.get("requested_count"),
        "valid_count": len(valid_paths),
        "master_method": MASTER_METHOD,
        "master_sigma": MASTER_SIGMA,
        "master_flat": str(master_path),
        "calibration_policy": metadata.get("calibration_policy"),
    }
    profile_path.write_text(
        json.dumps(profile, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    metadata["compatibility"] = compatibility
    metadata["master_flat"] = {
        "path": str(master_path),
        "profile": str(profile_path),
        "method": MASTER_METHOD,
        "sigma": MASTER_SIGMA,
        "valid_frames": len(valid_paths),
        "rejected_frames": int(metadata.get("requested_count") or 0) - len(valid_paths),
        "normalized": True,
    }


def flat_status(session_id: str) -> dict[str, Any]:
    return _read(session_id)


def flat_library() -> dict[str, Any]:
    if not FLAT_ROOT.exists():
        return {"status": "ready", "count": 0, "masters": []}

    masters: list[dict[str, Any]] = []
    for directory in sorted(FLAT_ROOT.iterdir(), reverse=True):
        session_file = directory / "session.json"
        if not session_file.exists():
            continue
        try:
            metadata = json.loads(session_file.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue
        if not metadata.get("master_flat"):
            continue
        masters.append(
            {
                "id": metadata.get("id"),
                "created_at": metadata.get("created_at"),
                "compatibility": metadata.get("compatibility"),
                "master_flat": metadata.get("master_flat"),
                "setup_profile": metadata.get("setup_profile"),
                "calibration_policy": metadata.get("calibration_policy"),
            }
        )

    return {
        "status": "ready",
        "count": len(masters),
        "masters": masters,
    }
