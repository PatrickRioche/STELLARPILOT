from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import numpy as np
from astropy.io import fits

from app.imaging import darks, flats
from app.imaging import stack_calibration as legacy
from app.imaging import sessions as sessions_module


_ORIGINAL_SELECT = sessions_module.select_compatible_master
_ORIGINAL_CALIBRATE = sessions_module.calibrate_light
_INSTALLED = False


def _number(value: Any) -> float | None:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if np.isfinite(number) else None


def fixed_dark_compatibility_profile(metadata: dict[str, Any]) -> dict[str, Any]:
    """Build compatibility from the exposure that was actually captured.

    r5 used the initial INDI snapshot for gain/offset even when the FITS itself
    contained the authoritative values. On cameras where that snapshot omitted
    gain/offset, a perfectly valid Master Dark was consequently rejected.
    """
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
        else fits_profile.get("temperature_c")
        if fits_profile.get("temperature_c") is not None
        else camera.get("temperature_c")
    )

    exposure = fits_profile.get("exposure_s")
    if exposure is None:
        exposure = metadata.get("exposure_s")

    return {
        "camera": camera.get("name"),
        "exposure_s": float(exposure) if exposure is not None else None,
        "gain": fits_profile.get("gain") if fits_profile.get("gain") is not None else capture.get("gain"),
        "offset": fits_profile.get("offset") if fits_profile.get("offset") is not None else capture.get("offset"),
        "bin_x": capture.get("bin_x"),
        "bin_y": capture.get("bin_y"),
        "frame_width": fits_profile.get("width") or capture.get("frame_width") or sensor.get("width"),
        "frame_height": fits_profile.get("height") or capture.get("frame_height") or sensor.get("height"),
        "bits_per_pixel": sensor.get("bits_per_pixel"),
        "bayer_pattern": fits_profile.get("bayer_pattern"),
        "temperature_c": temperature_c,
        "temperature_tolerance_c": legacy.TEMPERATURE_TOLERANCE_C,
    }


def repair_dark_library_metadata() -> int:
    """Repair r5 session metadata in place; raw/master FITS files are untouched."""
    root = darks.DARK_ROOT
    if not root.exists():
        return 0

    repaired = 0
    for directory in root.iterdir():
        path = directory / "session.json"
        if not path.exists():
            continue
        try:
            metadata = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue
        if not metadata.get("master_dark"):
            continue

        profile = fixed_dark_compatibility_profile(metadata)
        if metadata.get("compatibility") == profile:
            continue

        metadata["compatibility"] = profile
        temporary = path.with_suffix(".json.tmp")
        temporary.write_text(
            json.dumps(metadata, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        temporary.replace(path)

        profile_path_value = (metadata.get("master_dark") or {}).get("profile")
        if profile_path_value:
            profile_path = Path(profile_path_value)
            if profile_path.exists():
                try:
                    master_profile = json.loads(profile_path.read_text(encoding="utf-8"))
                    master_profile["compatibility"] = profile
                    profile_path.write_text(
                        json.dumps(master_profile, ensure_ascii=False, indent=2),
                        encoding="utf-8",
                    )
                except (OSError, json.JSONDecodeError):
                    pass
        repaired += 1
    return repaired


def _same_number(left: Any, right: Any, tolerance: float = 1e-3) -> bool:
    a = _number(left)
    b = _number(right)
    return a is not None and b is not None and abs(a - b) <= tolerance


def _flat_mismatches(light: dict[str, Any], master: dict[str, Any]) -> list[str]:
    mismatches: list[str] = []
    for key in ("camera", "bin_x", "bin_y", "frame_width", "frame_height", "bayer_pattern"):
        left = light.get(key)
        right = master.get(key)
        if left is None or right is None or left != right:
            mismatches.append(key)

    for key in ("gain", "offset"):
        if not _same_number(light.get(key), master.get(key)):
            mismatches.append(key)

    # bits_per_pixel is advisory because FITS storage can be 16-bit while the
    # physical ADC is 12-bit. Geometry+Bayer+camera identify the same sensor.
    return mismatches


def select_compatible_flat(light_profile: dict[str, Any]) -> dict[str, Any]:
    library = flats.flat_library()
    compatible: list[dict[str, Any]] = []
    diagnostics: list[dict[str, Any]] = []

    for item in library.get("masters", []):
        profile = item.get("compatibility") or {}
        mismatches = _flat_mismatches(light_profile, profile)
        diagnostics.append(
            {
                "id": item.get("id"),
                "created_at": item.get("created_at"),
                "mismatches": mismatches,
                "master_profile": profile,
            }
        )
        if not mismatches:
            compatible.append(item)

    if not compatible:
        return {
            "status": "unavailable",
            "master": None,
            "diagnostics": diagnostics,
            "detail": (
                "Aucun Master Flat compatible : stacking possible, mais sans correction "
                "des poussières/vignetage."
            ),
        }

    # flat_library is newest-first; use created_at as an explicit invariant.
    best = max(compatible, key=lambda item: str(item.get("created_at") or ""))
    return {
        "status": "ready",
        "master": best,
        "diagnostics": diagnostics,
        "detail": None,
    }


def select_compatible_master(
    image: Path | str,
    *,
    exposure_s: float,
    snapshot: dict[str, Any] | None = None,
    preferred_master_id: str | None = None,
) -> dict[str, Any]:
    repair_dark_library_metadata()
    dark_selection = _ORIGINAL_SELECT(
        image,
        exposure_s=exposure_s,
        snapshot=snapshot,
        preferred_master_id=preferred_master_id,
    )

    if dark_selection.get("status") != "ready":
        return dark_selection

    flat_selection = select_compatible_flat(dark_selection.get("profile") or {})
    enriched = dict(dark_selection)
    enriched["flat_selection"] = flat_selection
    enriched["flat_status"] = flat_selection.get("status")
    return enriched


def _record_flat_state(destination: Path, flat_selection: dict[str, Any]) -> None:
    # destination = <session>/calibrated/light_xxx.fits
    session_json = destination.parent.parent / "session.json"
    if not session_json.exists():
        return
    try:
        metadata = json.loads(session_json.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return

    calibration = metadata.setdefault("calibration", {})
    calibration["flat_status"] = flat_selection.get("status")
    calibration["flat_detail"] = flat_selection.get("detail")

    master = flat_selection.get("master") or {}
    master_meta = master.get("master_flat") or {}
    calibration["master_flat_id"] = master.get("id")
    calibration["master_flat"] = master_meta.get("path")

    temporary = session_json.with_suffix(".json.tmp")
    temporary.write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    temporary.replace(session_json)


def calibrate_light(
    image: Path | str,
    selection: dict[str, Any],
    destination: Path | str,
) -> dict[str, Any]:
    destination = Path(destination)
    result = _ORIGINAL_CALIBRATE(image, selection, destination)
    flat_selection = selection.get("flat_selection") or {
        "status": "unavailable",
        "master": None,
        "detail": "Master Flat non recherché",
    }

    if flat_selection.get("status") != "ready" or not flat_selection.get("master"):
        _record_flat_state(destination, flat_selection)
        result.update(
            {
                "flat_status": flat_selection.get("status"),
                "master_flat_id": None,
                "master_flat": None,
            }
        )
        return result

    item = flat_selection["master"]
    master_meta = item.get("master_flat") or {}
    master_path = Path(master_meta.get("path") or "")
    if not master_path.exists():
        raise FileNotFoundError(f"Master Flat introuvable: {master_path}")

    with fits.open(destination, memmap=False) as hdul:
        data = np.squeeze(np.asarray(hdul[0].data)).astype(np.float32, copy=False)
        header = hdul[0].header.copy()
    with fits.open(master_path, memmap=False) as hdul:
        flat = np.squeeze(np.asarray(hdul[0].data)).astype(np.float32, copy=False)

    if data.ndim != 2 or flat.ndim != 2 or data.shape != flat.shape:
        raise ValueError("Dimensions Master Flat / LIGHT incompatibles")
    if not np.all(np.isfinite(flat)):
        raise ValueError("Master Flat contient des valeurs non finies")

    safe_flat = np.clip(flat, 0.05, 20.0)
    calibrated = data / safe_flat
    header["SPFLAT"] = (True, "StellarPilot flat calibration applied")
    header["SPFLATID"] = (str(item.get("id") or "")[:68], "Master Flat session")

    fits.PrimaryHDU(data=calibrated.astype(np.float32), header=header).writeto(
        destination,
        overwrite=True,
    )

    _record_flat_state(destination, flat_selection)
    result.update(
        {
            "flat_status": "applied",
            "master_flat_id": item.get("id"),
            "master_flat": str(master_path),
        }
    )
    return result


def install() -> None:
    global _INSTALLED
    if _INSTALLED:
        return

    # Future dark masters use FITS values first; existing r5 masters are fixed
    # lazily and non-destructively before selection.
    darks._compatibility_profile = fixed_dark_compatibility_profile
    repair_dark_library_metadata()

    # sessions.py imported these symbols directly, so patch its module globals.
    sessions_module.select_compatible_master = select_compatible_master
    sessions_module.calibrate_light = calibrate_light
    _INSTALLED = True
