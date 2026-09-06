from __future__ import annotations

import math
import os
from pathlib import Path
from typing import Iterable

import numpy as np
from astropy.io import fits
from scipy.ndimage import gaussian_filter, gaussian_filter1d


REFERENCE_ROOT = Path(
    os.environ.get("STELLARPILOT_REFERENCE_ROOT", "data/reference-v2")
)

_CALIBRATION_CACHE_KEY: tuple | None = None
_CALIBRATION_CACHE: dict | None = None


def _axial_distance_deg(a: float, b: float) -> float:
    delta = abs(a - b) % 180.0
    return min(delta, 180.0 - delta)


def _load_fits(path: Path) -> np.ndarray:
    with fits.open(path, memmap=False) as hdul:
        data = hdul[0].data
        if data is None:
            raise ValueError("FITS sans donnees image")
        array = np.asarray(data)

    if array.ndim > 2:
        array = np.squeeze(array)
    if array.ndim != 2:
        raise ValueError(f"Dimensions FITS inattendues: {array.shape}")

    array = array.astype(np.float32, copy=False)
    finite = np.isfinite(array)
    if not finite.any():
        raise ValueError("Aucun pixel exploitable")
    if not finite.all():
        replacement = float(np.median(array[finite]))
        array = np.where(finite, array, replacement)
    return array


def _brightest_crop(
    data: np.ndarray,
    *,
    crop_size: int = 640,
) -> tuple[np.ndarray, tuple[float, float], tuple[int, int]]:
    height, width = data.shape
    background = float(np.median(data))
    work = np.clip(data - background, 0.0, None)

    # Reject isolated hot pixels while keeping the bright stellar core.
    locator = gaussian_filter(work, sigma=2.5)
    peak_index = int(np.argmax(locator))
    peak_y, peak_x = np.unravel_index(peak_index, locator.shape)

    size = int(min(crop_size, height, width))
    half = size // 2
    x0 = max(0, min(width - size, int(peak_x) - half))
    y0 = max(0, min(height - size, int(peak_y) - half))
    crop = data[y0 : y0 + size, x0 : x0 + size]

    return (
        crop,
        (float(peak_x - x0), float(peak_y - y0)),
        (x0, y0),
    )


def _weighted_hough_lines(crop: np.ndarray) -> list[dict]:
    background = float(np.median(crop))
    signal = np.clip(crop.astype(np.float32) - background, 0.0, None)

    positive = signal[signal > 0.0]
    if positive.size < 100:
        raise ValueError("Signal Bahtinov insuffisant")

    # Long spikes must influence the detector more than the saturated core.
    threshold = float(np.percentile(positive, 85.0))
    ys, xs = np.nonzero(signal >= threshold)
    if xs.size < 100:
        raise ValueError("Pas assez de pixels brillants pour detecter les aigrettes")

    weights = np.sqrt(np.maximum(signal[ys, xs], 0.0)).astype(np.float64)
    x = xs.astype(np.float64) - (crop.shape[1] - 1.0) / 2.0
    y = ys.astype(np.float64) - (crop.shape[0] - 1.0) / 2.0

    rho_radius = int(math.ceil(math.hypot(*crop.shape) / 2.0)) + 4
    bins_count = 2 * rho_radius + 1

    candidates: list[dict] = []
    for normal_deg in range(180):
        theta = math.radians(float(normal_deg))
        rho = x * math.cos(theta) + y * math.sin(theta)
        bins = np.rint(rho).astype(np.int32) + rho_radius
        histogram = np.bincount(
            bins,
            weights=weights,
            minlength=bins_count,
        ).astype(np.float64)
        histogram = gaussian_filter1d(histogram, sigma=1.0)
        peak_bin = int(np.argmax(histogram))
        candidates.append(
            {
                "normal_deg": float(normal_deg),
                "rho_px": float(peak_bin - rho_radius),
                "strength": float(histogram[peak_bin]),
            }
        )

    ordered = sorted(candidates, key=lambda item: item["strength"], reverse=True)
    selected: list[dict] = []
    for candidate in ordered:
        if all(
            _axial_distance_deg(
                candidate["normal_deg"],
                existing["normal_deg"],
            ) >= 10.0
            for existing in selected
        ):
            selected.append(candidate)
            if len(selected) == 3:
                break

    if len(selected) != 3:
        raise ValueError("Trois familles d'aigrettes Bahtinov non detectees")
    return selected


def _identify_geometry(lines: list[dict]) -> dict:
    best: tuple[float, int, int, int] | None = None

    for central_index in range(3):
        outer = [index for index in range(3) if index != central_index]
        central = lines[central_index]
        first = lines[outer[0]]
        second = lines[outer[1]]

        d1 = _axial_distance_deg(central["normal_deg"], first["normal_deg"])
        d2 = _axial_distance_deg(central["normal_deg"], second["normal_deg"])
        outer_separation = _axial_distance_deg(
            first["normal_deg"], second["normal_deg"]
        )
        if min(d1, d2) < 8.0 or outer_separation < 16.0:
            continue

        symmetry_error = abs(d1 - d2)
        candidate = (symmetry_error, central_index, outer[0], outer[1])
        if best is None or candidate[0] < best[0]:
            best = candidate

    if best is None:
        raise ValueError("Geometrie des trois aigrettes incoherente")

    symmetry_error, central_index, first_index, second_index = best
    central = lines[central_index]
    first = lines[first_index]
    second = lines[second_index]

    def normal(line: dict) -> np.ndarray:
        theta = math.radians(line["normal_deg"])
        return np.array([math.cos(theta), math.sin(theta)], dtype=np.float64)

    matrix = np.vstack([normal(first), normal(second)])
    if abs(float(np.linalg.det(matrix))) < 0.08:
        raise ValueError("Aigrettes exterieures presque paralleles")

    intersection = np.linalg.solve(
        matrix,
        np.array([first["rho_px"], second["rho_px"]], dtype=np.float64),
    )
    central_normal = normal(central)
    signed_error = float(central["rho_px"] - np.dot(central_normal, intersection))

    mean_strength = float(np.mean([line["strength"] for line in lines]))
    weakest = float(min(line["strength"] for line in lines))
    strength_balance = weakest / max(mean_strength, 1e-9)
    confidence = max(
        0.0,
        min(
            1.0,
            strength_balance * max(0.0, 1.0 - symmetry_error / 12.0),
        ),
    )

    return {
        "signed_error_px": signed_error,
        "absolute_error_px": abs(signed_error),
        "intersection_x_px": float(intersection[0]),
        "intersection_y_px": float(intersection[1]),
        "central_normal_deg": float(central["normal_deg"]),
        "outer_normal_deg": [
            float(first["normal_deg"]),
            float(second["normal_deg"]),
        ],
        "symmetry_error_deg": float(symmetry_error),
        "geometry_confidence": float(confidence),
        "lines": lines,
    }


def _analyze_geometry(path: Path) -> dict:
    data = _load_fits(path)
    crop, peak_in_crop, crop_origin = _brightest_crop(data)
    geometry = _identify_geometry(_weighted_hough_lines(crop))
    geometry.update(
        {
            "image": str(path),
            "width": int(data.shape[1]),
            "height": int(data.shape[0]),
            "crop_width": int(crop.shape[1]),
            "crop_height": int(crop.shape[0]),
            "crop_origin_x": int(crop_origin[0]),
            "crop_origin_y": int(crop_origin[1]),
            "brightest_x": round(crop_origin[0] + peak_in_crop[0], 3),
            "brightest_y": round(crop_origin[1] + peak_in_crop[1], 3),
        }
    )
    return geometry


def _latest_reference_sets() -> tuple[Path | None, list[Path], list[Path]]:
    if not REFERENCE_ROOT.exists():
        return None, [], []

    dated = sorted(
        [item for item in REFERENCE_ROOT.iterdir() if item.is_dir()],
        key=lambda item: item.name,
        reverse=True,
    )
    for root in dated:
        optimum = sorted((root / "bahtinov" / "optimum").glob("*.fits"))
        other = sorted((root / "bahtinov" / "other_side").glob("*.fits"))
        if optimum:
            return root, optimum, other
    return None, [], []


def _safe_geometry(paths: Iterable[Path]) -> list[dict]:
    results: list[dict] = []
    for path in paths:
        try:
            results.append(_analyze_geometry(path))
        except Exception as exc:
            results.append(
                {
                    "image": str(path),
                    "error": f"{exc.__class__.__name__}: {exc}",
                }
            )
    return results


def _reference_cache_key(
    root: Path,
    optimum_paths: list[Path],
    other_paths: list[Path],
) -> tuple:
    files = optimum_paths + other_paths
    return (
        str(root.resolve()),
        tuple(
            (
                str(path.resolve()),
                path.stat().st_mtime_ns,
                path.stat().st_size,
            )
            for path in files
        ),
    )


def _calibration() -> dict | None:
    global _CALIBRATION_CACHE_KEY, _CALIBRATION_CACHE

    root, optimum_paths, other_paths = _latest_reference_sets()
    if root is None or not optimum_paths:
        return None

    key = _reference_cache_key(root, optimum_paths, other_paths)
    if key == _CALIBRATION_CACHE_KEY:
        return _CALIBRATION_CACHE

    optimum_results = [
        item for item in _safe_geometry(optimum_paths) if "signed_error_px" in item
    ]
    other_results = [
        item for item in _safe_geometry(other_paths) if "signed_error_px" in item
    ]
    if not optimum_results:
        return None

    optimum_errors = np.array(
        [item["signed_error_px"] for item in optimum_results],
        dtype=np.float64,
    )
    baseline = float(np.median(optimum_errors))
    spread = float(np.median(np.abs(optimum_errors - baseline)))
    optimum_tolerance = max(1.5, spread * 3.0)

    other_median: float | None = None
    bad_distance: float | None = None
    if other_results:
        other_errors = np.array(
            [item["signed_error_px"] for item in other_results],
            dtype=np.float64,
        )
        other_median = float(np.median(other_errors))
        bad_distance = max(
            abs(other_median - baseline),
            optimum_tolerance * 2.0,
        )

    result = {
        "reference_root": str(root),
        "baseline_error_px": baseline,
        "optimum_tolerance_px": optimum_tolerance,
        "other_side_error_px": other_median,
        "bad_distance_px": bad_distance,
        "optimum_count": len(optimum_results),
        "other_side_count": len(other_results),
    }
    _CALIBRATION_CACHE_KEY = key
    _CALIBRATION_CACHE = result
    return result


def _score_focus(error_from_optimum: float, calibration: dict | None) -> int:
    distance = abs(error_from_optimum)
    if calibration is None:
        return int(round(max(0.0, min(100.0, 100.0 - distance * 8.0))))

    tolerance = float(calibration["optimum_tolerance_px"])
    bad_distance = calibration.get("bad_distance_px")

    if distance <= tolerance:
        ratio = distance / max(tolerance, 1e-9)
        return int(round(100.0 - 2.0 * ratio))

    if bad_distance is None or float(bad_distance) <= tolerance:
        return int(round(max(0.0, 98.0 - (distance - tolerance) * 8.0)))

    bad_distance = float(bad_distance)
    if distance <= bad_distance:
        ratio = (distance - tolerance) / (bad_distance - tolerance)
        return int(round(98.0 - 53.0 * ratio))

    beyond = (distance - bad_distance) / max(bad_distance, 1.0)
    return int(round(max(0.0, 45.0 - 45.0 * min(beyond, 1.0))))


def _focus_label(score: int) -> str:
    if score >= 98:
        return "Optimum"
    if score >= 90:
        return "Excellent"
    if score >= 75:
        return "Bon"
    if score >= 60:
        return "Moyen"
    if score >= 40:
        return "Mauvais"
    return "Tres mauvais"


def analyze_bahtinov(image: str) -> dict:
    path = Path(image)
    if not path.exists():
        return {
            "status": "error",
            "image": str(path),
            "detail": "Fichier FITS introuvable",
        }

    try:
        geometry = _analyze_geometry(path)
        calibration = _calibration()
        baseline = (
            float(calibration["baseline_error_px"])
            if calibration is not None
            else 0.0
        )
        error_from_optimum = float(geometry["signed_error_px"] - baseline)
        score = _score_focus(error_from_optimum, calibration)
        label = _focus_label(score)

        side = "optimum"
        instruction = "Mise au point optimale"
        if score < 98:
            side = "unknown_side"
            instruction = "Ajuster la mise au point puis refaire une pose de 4 s"
            if calibration is not None:
                other_error = calibration.get("other_side_error_px")
                if other_error is not None:
                    other_delta = float(other_error) - baseline
                    if other_delta != 0.0:
                        if error_from_optimum * other_delta > 0.0:
                            side = "other_side"
                            instruction = (
                                "Vous avez depasse l'optimum : revenir en sens inverse"
                            )
                        else:
                            side = "first_side"
                            instruction = "Continuer progressivement vers l'optimum"

        return {
            "status": "ok",
            "image": str(path),
            "focus_score": score,
            "focus_label": label,
            "focus_ready": score >= 98,
            "focus_side": side,
            "instruction": instruction,
            "signed_error_px": round(float(geometry["signed_error_px"]), 3),
            "error_from_optimum_px": round(error_from_optimum, 3),
            "absolute_error_px": round(abs(error_from_optimum), 3),
            "geometry_confidence": round(
                float(geometry["geometry_confidence"]), 4
            ),
            "symmetry_error_deg": round(
                float(geometry["symmetry_error_deg"]), 3
            ),
            "central_normal_deg": round(
                float(geometry["central_normal_deg"]), 3
            ),
            "outer_normal_deg": [
                round(float(value), 3)
                for value in geometry["outer_normal_deg"]
            ],
            "brightest_x": geometry["brightest_x"],
            "brightest_y": geometry["brightest_y"],
            "calibration": calibration,
        }
    except Exception as exc:
        return {
            "status": "error",
            "image": str(path),
            "detail": f"{exc.__class__.__name__}: {exc}",
        }


def validate_reference_library() -> dict:
    root, optimum_paths, other_paths = _latest_reference_sets()
    optimum = _safe_geometry(optimum_paths)
    other = _safe_geometry(other_paths)

    def summarized(items: list[dict]) -> list[dict]:
        summary: list[dict] = []
        for item in items:
            if "signed_error_px" in item:
                scored = analyze_bahtinov(item["image"])
                summary.append(
                    {
                        "image": item["image"],
                        "signed_error_px": round(
                            float(item["signed_error_px"]), 3
                        ),
                        "focus_score": scored.get("focus_score"),
                        "focus_label": scored.get("focus_label"),
                        "focus_side": scored.get("focus_side"),
                        "geometry_confidence": round(
                            float(item["geometry_confidence"]), 4
                        ),
                    }
                )
            else:
                summary.append(item)
        return summary

    return {
        "status": "ok" if root is not None else "reference_missing",
        "reference_root": str(root) if root is not None else None,
        "calibration": _calibration(),
        "optimum": summarized(optimum),
        "other_side": summarized(other),
    }
