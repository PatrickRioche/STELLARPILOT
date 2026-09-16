from __future__ import annotations

from pathlib import Path
from typing import Any

import numpy as np
from astropy.io import fits
from scipy.ndimage import median_filter

from app.imaging import darks
from app.indi.service import indi_service


NUMERIC_TOLERANCE = 1e-3
TEMPERATURE_TOLERANCE_C = 5.0

MISMATCH_LABELS = {
    "camera": "caméra",
    "exposure_s": "temps de pose",
    "gain": "gain",
    "offset": "offset",
    "bin_x": "binning X",
    "bin_y": "binning Y",
    "frame_width": "largeur",
    "frame_height": "hauteur",
    "bits_per_pixel": "profondeur",
    "bayer_pattern": "matrice Bayer",
    "temperature_c": "température",
}


def _number(value: Any) -> float | None:
    try:
        result = float(value)
    except (TypeError, ValueError):
        return None
    return result if np.isfinite(result) else None


def _header_number(header: fits.Header, *keys: str) -> float | None:
    for key in keys:
        value = _number(header.get(key))
        if value is not None:
            return value
    return None


def light_profile(
    image: Path | str,
    *,
    exposure_s: float,
    snapshot: dict[str, Any] | None = None,
) -> dict[str, Any]:
    path = Path(image)
    snapshot = snapshot or indi_service.status_snapshot()
    camera = snapshot.get("camera") or {}
    sensor = camera.get("sensor") or {}
    capture = camera.get("capture") or {}

    with fits.open(path, memmap=False) as hdul:
        header = hdul[0].header
        data = np.squeeze(np.asarray(hdul[0].data))
        if data.ndim != 2:
            raise ValueError(f"Dimensions FITS inattendues: {data.shape}")

        pattern = str(header.get("BAYERPAT", "")).strip().upper() or None
        fits_exposure = _header_number(header, "EXPTIME", "EXPOSURE")
        fits_gain = _header_number(header, "GAIN", "EGAIN")
        fits_offset = _header_number(header, "OFFSET", "BLACKLEV")
        fits_temperature = _header_number(
            header,
            "CCD-TEMP",
            "CCDTEMP",
            "SENSOR_T",
        )

    return {
        "camera": camera.get("name"),
        "exposure_s": fits_exposure if fits_exposure is not None else float(exposure_s),
        "gain": fits_gain if fits_gain is not None else capture.get("gain"),
        "offset": fits_offset if fits_offset is not None else capture.get("offset"),
        "bin_x": capture.get("bin_x"),
        "bin_y": capture.get("bin_y"),
        "frame_width": int(data.shape[1]),
        "frame_height": int(data.shape[0]),
        "bits_per_pixel": sensor.get("bits_per_pixel") or abs(int(header.get("BITPIX", 0))),
        "bayer_pattern": pattern,
        # The FITS value describes the temperature of this exact exposure.
        # Fall back to the live INDI value only when the image has no temperature metadata.
        "temperature_c": (
            fits_temperature
            if fits_temperature is not None
            else camera.get("temperature_c")
        ),
    }


def _same_number(left: Any, right: Any, tolerance: float = NUMERIC_TOLERANCE) -> bool:
    a = _number(left)
    b = _number(right)
    return a is not None and b is not None and abs(a - b) <= tolerance


def compatibility_mismatches(
    light: dict[str, Any],
    master: dict[str, Any],
) -> list[str]:
    mismatches: list[str] = []

    for key in (
        "camera",
        "bin_x",
        "bin_y",
        "frame_width",
        "frame_height",
        "bits_per_pixel",
        "bayer_pattern",
    ):
        if light.get(key) is None or master.get(key) is None or light.get(key) != master.get(key):
            mismatches.append(key)

    for key in ("exposure_s", "gain", "offset"):
        if not _same_number(light.get(key), master.get(key)):
            mismatches.append(key)

    light_temperature = _number(light.get("temperature_c"))
    master_temperature = _number(master.get("temperature_c"))

    if light_temperature is None or master_temperature is None:
        mismatches.append("temperature_c")
    elif abs(light_temperature - master_temperature) > TEMPERATURE_TOLERANCE_C:
        mismatches.append("temperature_c")

    return mismatches


def _format_profile_value(key: str, value: Any) -> str:
    if value is None:
        return "—"
    number = _number(value)
    if key == "temperature_c" and number is not None:
        return f"{number:.1f} °C"
    if key == "exposure_s" and number is not None:
        return f"{number:g} s"
    if key in {"gain", "offset"} and number is not None:
        return f"{number:g}"
    return str(value)


def _diagnostic_detail(
    profile: dict[str, Any],
    diagnostics: list[dict[str, Any]],
) -> str:
    if not diagnostics:
        return (
            "Aucun Master Dark n'est disponible dans la bibliothèque. "
            "Refaites les darks avant de reprendre le stacking."
        )

    def diagnostic_rank(item: dict[str, Any]) -> tuple[int, float, str]:
        master_profile = item.get("master_profile") or {}
        light_temperature = _number(profile.get("temperature_c"))
        master_temperature = _number(master_profile.get("temperature_c"))
        if light_temperature is not None and master_temperature is not None:
            temperature_delta = abs(light_temperature - master_temperature)
        else:
            temperature_delta = float("inf")
        return (
            len(item.get("mismatches") or []),
            temperature_delta,
            str(item.get("created_at") or ""),
        )

    candidate = min(diagnostics, key=diagnostic_rank)
    mismatches = list(candidate.get("mismatches") or [])
    master_profile = candidate.get("master_profile") or {}
    candidate_id = candidate.get("id") or "inconnu"

    reasons: list[str] = []
    for key in mismatches:
        label = MISMATCH_LABELS.get(key, key)
        if key == "temperature_c":
            light_temperature = _number(profile.get("temperature_c"))
            master_temperature = _number(master_profile.get("temperature_c"))
            if light_temperature is not None and master_temperature is not None:
                delta = abs(light_temperature - master_temperature)
                reasons.append(
                    f"{label} LIGHT {light_temperature:.1f} °C / DARK "
                    f"{master_temperature:.1f} °C (écart {delta:.1f} °C > "
                    f"tolérance ±{TEMPERATURE_TOLERANCE_C:.1f} °C)"
                )
            else:
                reasons.append(
                    f"{label} indisponible (LIGHT "
                    f"{_format_profile_value(key, profile.get(key))} / DARK "
                    f"{_format_profile_value(key, master_profile.get(key))})"
                )
        else:
            reasons.append(
                f"{label} LIGHT {_format_profile_value(key, profile.get(key))} / "
                f"DARK {_format_profile_value(key, master_profile.get(key))}"
            )

    if not reasons:
        reasons.append("profil incompatible")

    return (
        f"Master Dark {candidate_id} refusé : "
        + "; ".join(reasons)
        + ". Refaites les darks avec les paramètres actuels, puis relancez le stacking."
    )


def _select_best_compatible(
    compatible: list[tuple[float, str, dict[str, Any]]],
) -> tuple[float, str, dict[str, Any]]:
    # Prefer the closest sensor temperature, then the most recent master.
    best_delta = min(entry[0] for entry in compatible)
    closest = [entry for entry in compatible if abs(entry[0] - best_delta) <= 1e-9]
    return max(closest, key=lambda entry: entry[1])


def select_compatible_master(
    image: Path | str,
    *,
    exposure_s: float,
    snapshot: dict[str, Any] | None = None,
    preferred_master_id: str | None = None,
) -> dict[str, Any]:
    profile = light_profile(
        image,
        exposure_s=exposure_s,
        snapshot=snapshot,
    )
    library = darks.dark_library()
    compatible: list[tuple[float, str, dict[str, Any]]] = []
    diagnostics: list[dict[str, Any]] = []
    preferred_item: dict[str, Any] | None = None
    preferred_delta: float | None = None

    for item in library.get("masters", []):
        master_profile = item.get("compatibility") or {}
        mismatches = compatibility_mismatches(profile, master_profile)
        light_temperature = _number(profile.get("temperature_c"))
        master_temperature = _number(master_profile.get("temperature_c"))
        temperature_delta = (
            abs(light_temperature - master_temperature)
            if light_temperature is not None and master_temperature is not None
            else None
        )
        diagnostics.append(
            {
                "id": item.get("id"),
                "created_at": item.get("created_at"),
                "mismatches": mismatches,
                "master_profile": master_profile,
                "temperature_delta_c": (
                    round(temperature_delta, 3)
                    if temperature_delta is not None
                    else None
                ),
                "temperature_tolerance_c": TEMPERATURE_TOLERANCE_C,
            }
        )
        if mismatches:
            continue

        delta = float(temperature_delta or 0.0)

        if preferred_master_id and item.get("id") == preferred_master_id:
            preferred_item = item
            preferred_delta = delta

        compatible.append(
            (
                delta,
                str(item.get("created_at") or ""),
                item,
            )
        )

    if preferred_item is not None:
        return {
            "status": "ready",
            "profile": profile,
            "master": preferred_item,
            "temperature_delta_c": round(float(preferred_delta or 0.0), 3),
            "temperature_tolerance_c": TEMPERATURE_TOLERANCE_C,
            "diagnostics": diagnostics,
            "detail": None,
            "switched_master": False,
        }

    if compatible:
        best = _select_best_compatible(compatible)
        item = best[2]
        switched = bool(preferred_master_id and item.get("id") != preferred_master_id)
        return {
            "status": "ready",
            "profile": profile,
            "master": item,
            "temperature_delta_c": round(best[0], 3),
            "temperature_tolerance_c": TEMPERATURE_TOLERANCE_C,
            "diagnostics": diagnostics,
            "detail": None,
            "switched_master": switched,
            "previous_master_id": preferred_master_id if switched else None,
        }

    return {
        "status": "unavailable",
        "profile": profile,
        "master": None,
        "temperature_delta_c": None,
        "temperature_tolerance_c": TEMPERATURE_TOLERANCE_C,
        "diagnostics": diagnostics,
        "detail": _diagnostic_detail(profile, diagnostics),
        "recreate_darks_recommended": True,
        "previous_master_id": preferred_master_id,
    }


def _load_image(path: Path) -> tuple[np.ndarray, fits.Header, np.dtype]:
    with fits.open(path, memmap=False) as hdul:
        data = np.squeeze(np.asarray(hdul[0].data))
        header = hdul[0].header.copy()
    if data.ndim != 2:
        raise ValueError(f"Dimensions FITS inattendues: {data.shape}")
    return data.astype(np.float32, copy=False), header, data.dtype


def _repair_hot_pixels(
    calibrated: np.ndarray,
    hot_mask: np.ndarray,
    pattern: str | None,
) -> int:
    if hot_mask.shape != calibrated.shape:
        raise ValueError("Carte de pixels chauds incompatible avec le LIGHT")

    hot_count = int(np.count_nonzero(hot_mask))
    if hot_count == 0:
        return 0

    if pattern in {"RGGB", "BGGR", "GRBG", "GBRG"}:
        for row in range(2):
            for col in range(2):
                plane = calibrated[row::2, col::2]
                plane_mask = hot_mask[row::2, col::2].astype(bool, copy=False)
                if not np.any(plane_mask):
                    continue
                local_median = median_filter(plane, size=3, mode="mirror")
                plane[plane_mask] = local_median[plane_mask]
    else:
        local_median = median_filter(calibrated, size=3, mode="mirror")
        mask = hot_mask.astype(bool, copy=False)
        calibrated[mask] = local_median[mask]

    return hot_count


def calibrate_light(
    image: Path | str,
    selection: dict[str, Any],
    destination: Path | str,
) -> dict[str, Any]:
    if selection.get("status") != "ready" or not selection.get("master"):
        raise ValueError(selection.get("detail") or "Master Dark indisponible")

    source = Path(image)
    destination = Path(destination)
    master_item = selection["master"]
    master_meta = master_item.get("master_dark") or {}
    master_path = Path(master_meta.get("path") or "")
    if not master_path.exists():
        raise FileNotFoundError(f"Master Dark introuvable: {master_path}")

    light, header, source_dtype = _load_image(source)
    master, _master_header, _ = _load_image(master_path)
    if light.shape != master.shape:
        raise ValueError("Dimensions Master Dark / LIGHT incompatibles")

    calibrated = light - master
    hot_count = 0
    hot_meta = master_item.get("hot_pixels") or {}
    mask_path_value = hot_meta.get("mask_fits")
    if mask_path_value:
        mask_path = Path(mask_path_value)
        if mask_path.exists():
            with fits.open(mask_path, memmap=False) as hdul:
                hot_mask = np.squeeze(np.asarray(hdul[0].data))
            pattern = str(header.get("BAYERPAT", "")).strip().upper() or None
            hot_count = _repair_hot_pixels(calibrated, hot_mask, pattern)

    if np.issubdtype(source_dtype, np.integer):
        full_scale = float(np.iinfo(source_dtype).max)
    else:
        full_scale = float(np.nanmax(light)) if np.any(np.isfinite(light)) else 0.0

    header["IMAGETYP"] = ("CALIBRATED LIGHT", "StellarPilot calibrated light")
    header["SPCALIB"] = (True, "StellarPilot dark calibration applied")
    header["SPDARKID"] = (str(master_item.get("id") or "")[:68], "Master Dark session")
    header["SPHOTFIX"] = (hot_count, "Hot pixels repaired")
    header["SPSATLVL"] = (full_scale * 0.98, "Source saturation threshold")

    destination.parent.mkdir(parents=True, exist_ok=True)
    fits.PrimaryHDU(data=calibrated.astype(np.float32), header=header).writeto(
        destination,
        overwrite=True,
    )

    return {
        "status": "calibrated",
        "image": str(destination),
        "source": str(source),
        "master_id": master_item.get("id"),
        "master_dark": str(master_path),
        "temperature_delta_c": selection.get("temperature_delta_c"),
        "hot_pixels_repaired": hot_count,
        "profile": selection.get("profile"),
    }
