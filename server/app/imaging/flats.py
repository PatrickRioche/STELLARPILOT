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

# Flat exposure policy. A dust/vignetting flat should live well away from both
# the black point and saturation. The first capture request can therefore act
# as an exposure probe and is not counted until it falls inside this window.
FLAT_TARGET_PERCENT = 40.0
FLAT_MIN_PERCENT = 25.0
FLAT_MAX_PERCENT = 60.0
FLAT_MAX_SATURATED_PERCENT = 0.10
FLAT_MAX_P99_PERCENT = 90.0
FLAT_MIN_EXPOSURE_S = 0.001
FLAT_MAX_EXPOSURE_S = 5.0
FLAT_AUTO_MAX_ATTEMPTS = 7


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
    if not FLAT_MIN_EXPOSURE_S <= exposure_s <= FLAT_MAX_EXPOSURE_S:
        raise ValueError(
            f"exposure_s doit être compris entre {FLAT_MIN_EXPOSURE_S} et {FLAT_MAX_EXPOSURE_S} s"
        )
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
        "exposure_probe_history": [],
        "storage": str(root),
        "setup_profile": darks._snapshot_setup(float(exposure_s)),
        "fits_profile": None,
        "compatibility": None,
        "master_flat": None,
        "flat_exposure_policy": {
            "automatic": True,
            "target_percent_full_scale": FLAT_TARGET_PERCENT,
            "accepted_min_percent": FLAT_MIN_PERCENT,
            "accepted_max_percent": FLAT_MAX_PERCENT,
            "max_saturated_percent": FLAT_MAX_SATURATED_PERCENT,
            "max_p99_percent": FLAT_MAX_P99_PERCENT,
        },
        "calibration_policy": {
            "bias_frames_required": False,
            "dark_flat_required": False,
            "temperature_matching_required": False,
            "note": "r6: master flat normalisé sans série bias séparée",
        },
    }
    return _write(metadata)


def _flat_quality(quality: dict[str, Any]) -> tuple[bool, list[str], dict[str, float | None]]:
    median_percent = _number(quality.get("median_percent_full_scale"))
    saturated = _number(quality.get("saturated_percent"))
    p99 = _number(quality.get("p99"))
    full_scale = _number(quality.get("full_scale"))
    p99_percent = (
        p99 / full_scale * 100.0
        if p99 is not None and full_scale is not None and full_scale > 0
        else None
    )

    reasons: list[str] = []
    if quality.get("status") != "ok":
        reasons.append(str(quality.get("detail") or "analyse FITS impossible"))
    if median_percent is None:
        reasons.append("niveau médian inconnu")
    elif median_percent < FLAT_MIN_PERCENT:
        reasons.append(
            f"flat trop sombre ({median_percent:.1f}% < {FLAT_MIN_PERCENT:.0f}%)"
        )
    elif median_percent > FLAT_MAX_PERCENT:
        reasons.append(
            f"flat trop clair ({median_percent:.1f}% > {FLAT_MAX_PERCENT:.0f}%)"
        )

    if saturated is None:
        reasons.append("saturation inconnue")
    elif saturated > FLAT_MAX_SATURATED_PERCENT:
        reasons.append(
            f"pixels saturés ({saturated:.3f}% > {FLAT_MAX_SATURATED_PERCENT:.2f}%)"
        )

    if p99_percent is not None and p99_percent > FLAT_MAX_P99_PERCENT:
        reasons.append(
            f"P99 trop élevé ({p99_percent:.1f}% > {FLAT_MAX_P99_PERCENT:.0f}%)"
        )

    return (
        not reasons,
        reasons,
        {
            "median_percent_full_scale": median_percent,
            "p99_percent_full_scale": p99_percent,
            "saturated_percent": saturated,
        },
    )


def _next_flat_exposure(current_s: float, metrics: dict[str, float | None]) -> float:
    median_percent = metrics.get("median_percent_full_scale")
    saturated = metrics.get("saturated_percent")
    p99_percent = metrics.get("p99_percent_full_scale")

    if (
        (saturated is not None and saturated > FLAT_MAX_SATURATED_PERCENT)
        or (p99_percent is not None and p99_percent > FLAT_MAX_P99_PERCENT)
    ):
        factor = 0.5
    elif median_percent is None or median_percent <= 0.05:
        factor = 4.0
    else:
        factor = FLAT_TARGET_PERCENT / median_percent
        factor = min(4.0, max(0.25, factor))

    candidate = current_s * factor
    return round(
        min(FLAT_MAX_EXPOSURE_S, max(FLAT_MIN_EXPOSURE_S, candidate)),
        6,
    )


def _capture_until_exposed(
    metadata: dict[str, Any],
    *,
    frame_index: int,
) -> tuple[dict[str, Any], Path, dict[str, Any], dict[str, Any], list[str]]:
    exposure = float(metadata["exposure_s"])
    last_result: dict[str, Any] = {}
    last_path: Path | None = None
    last_quality: dict[str, Any] = {}
    last_fits: dict[str, Any] = {}
    last_reasons: list[str] = []

    # Once a valid exposure has been established we keep it fixed for the
    # series; only the first accepted frame is allowed to auto-tune it.
    auto_tune = int(metadata.get("captured_count", 0)) == 0
    attempts = FLAT_AUTO_MAX_ATTEMPTS if auto_tune else 1

    for attempt in range(1, attempts + 1):
        result = capture_flat_frame(
            exposure,
            output_dir=_session_path(metadata["id"]) / "raw",
            prefix=f"flat_{frame_index:03d}_try{attempt:02d}",
        )
        if result.get("status") != "captured" or not result.get("image"):
            raise RuntimeError(result.get("detail", "Capture flat impossible"))

        path = Path(result["image"])
        quality = analyze_fits(str(path))
        try:
            fits_profile = darks._fits_profile(path)
        except Exception as exc:
            fits_profile = {"error": str(exc)}

        quality_valid, reasons, metrics = _flat_quality(quality)
        if "error" in fits_profile:
            quality_valid = False
            reasons.append(f"métadonnées FITS invalides: {fits_profile['error']}")

        metadata.setdefault("exposure_probe_history", []).append(
            {
                "frame_index": frame_index,
                "attempt": attempt,
                "exposure_s": exposure,
                **metrics,
                "valid": quality_valid,
                "reasons": reasons,
                "image": str(path),
            }
        )

        last_result = result
        last_path = path
        last_quality = quality
        last_fits = fits_profile
        last_reasons = reasons

        if quality_valid:
            metadata["exposure_s"] = exposure
            metadata["flat_exposure_percent"] = metrics.get("median_percent_full_scale")
            metadata["flat_exposure_locked"] = True
            return result, path, quality, fits_profile, []

        if not auto_tune:
            break

        next_exposure = _next_flat_exposure(exposure, metrics)
        if abs(next_exposure - exposure) < 1e-9:
            break
        exposure = next_exposure
        metadata["exposure_s"] = exposure

    assert last_path is not None
    return last_result, last_path, last_quality, last_fits, last_reasons


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
    try:
        result, path, quality, fits_profile, rejection_reasons = _capture_until_exposed(
            metadata,
            frame_index=index,
        )
    except Exception as exc:
        metadata["status"] = "error"
        metadata["detail"] = f"Capture flat impossible: {exc}"
        return _write(metadata)

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
    if not same_geometry:
        rejection_reasons = list(rejection_reasons) + ["géométrie/Bayer différent de la série"]

    valid = not rejection_reasons and "error" not in fits_profile and same_geometry

    metadata["captured_count"] = index
    if valid:
        metadata["valid_count"] = int(metadata["valid_count"]) + 1

    metadata["frames"].append(
        {
            "index": index,
            "image": str(path),
            "size_bytes": path.stat().st_size if path.exists() else None,
            "valid": valid,
            "rejection_reasons": rejection_reasons,
            "frame_type": result.get("frame_type", "flat"),
            "exposure_s": metadata.get("exposure_s"),
            "median": quality.get("median"),
            "median_percent_full_scale": quality.get("median_percent_full_scale"),
            "background_sigma": quality.get("background_sigma"),
            "p99": quality.get("p99"),
            "full_scale": quality.get("full_scale"),
            "saturated_percent": quality.get("saturated_percent"),
            "maximum": quality.get("maximum"),
            "fits": fits_profile,
        }
    )

    if not valid:
        metadata["status"] = "error"
        metadata["detail"] = (
            "Flat invalide après réglage automatique: "
            + ("; ".join(rejection_reasons) if rejection_reasons else "raison inconnue")
            + f". Dernière exposition: {metadata.get('exposure_s')} s."
        )
        return _write(metadata)

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
        metadata["detail"] = (
            f"Flat {index}/{requested} valide • exposition {float(metadata['exposure_s']):.4f} s "
            f"• médiane {float(metadata.get('flat_exposure_percent') or 0.0):.1f}%"
        )

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
        rejected = [
            reason
            for frame in metadata.get("frames", [])
            if not frame.get("valid")
            for reason in frame.get("rejection_reasons", [])
        ]
        reason_text = "; ".join(dict.fromkeys(rejected)) if rejected else "qualité insuffisante"
        raise ValueError(
            f"Pas assez de flats valides ({len(valid_paths)}/{metadata.get('requested_count')}). "
            f"Causes: {reason_text}"
        )

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
        "flat_exposure_s": metadata.get("exposure_s"),
        "flat_median_percent": metadata.get("flat_exposure_percent"),
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
        "exposure_s": metadata.get("exposure_s"),
        "median_percent_full_scale": metadata.get("flat_exposure_percent"),
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
