from __future__ import annotations

from pathlib import Path
from typing import Iterable

import numpy as np

from app.imaging.bahtinov import REFERENCE_ROOT, _analyze_geometry


def _latest_reference_sets() -> tuple[
    Path | None,
    list[Path],
    list[Path],
    list[Path],
]:
    if not REFERENCE_ROOT.exists():
        return None, [], [], []

    dated = sorted(
        [item for item in REFERENCE_ROOT.iterdir() if item.is_dir()],
        key=lambda item: item.name,
        reverse=True,
    )
    for root in dated:
        optimum = sorted((root / "bahtinov" / "optimum").glob("*.fits"))
        side_a = sorted((root / "bahtinov" / "side_a").glob("*.fits"))
        side_b = sorted((root / "bahtinov" / "side_b").glob("*.fits"))
        if optimum:
            return root, optimum, side_a, side_b
    return None, [], [], []


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


def _calibration() -> dict | None:
    root, optimum_paths, side_a_paths, side_b_paths = _latest_reference_sets()
    if root is None or not optimum_paths:
        return None

    optimum = [
        item for item in _safe_geometry(optimum_paths)
        if "signed_error_px" in item
    ]
    side_a = [
        item for item in _safe_geometry(side_a_paths)
        if "signed_error_px" in item
    ]
    side_b = [
        item for item in _safe_geometry(side_b_paths)
        if "signed_error_px" in item
    ]
    if not optimum:
        return None

    optimum_errors = np.array(
        [item["signed_error_px"] for item in optimum],
        dtype=np.float64,
    )
    baseline = float(np.median(optimum_errors))
    spread = float(np.median(np.abs(optimum_errors - baseline)))
    tolerance = max(1.5, spread * 3.0)

    def side_delta(items: list[dict]) -> float | None:
        if not items:
            return None
        values = np.array(
            [item["signed_error_px"] - baseline for item in items],
            dtype=np.float64,
        )
        return float(np.median(values))

    delta_a = side_delta(side_a)
    delta_b = side_delta(side_b)

    return {
        "reference_root": str(root),
        "baseline_error_px": baseline,
        "optimum_tolerance_px": tolerance,
        "side_a_delta_px": delta_a,
        "side_b_delta_px": delta_b,
        "side_a_scale_px": abs(delta_a) if delta_a is not None else None,
        "side_b_scale_px": abs(delta_b) if delta_b is not None else None,
        "optimum_count": len(optimum),
        "side_a_count": len(side_a),
        "side_b_count": len(side_b),
    }


def _choose_side(delta: float, calibration: dict) -> tuple[str, float | None]:
    delta_a = calibration.get("side_a_delta_px")
    delta_b = calibration.get("side_b_delta_px")

    candidates: list[tuple[str, float, float]] = []
    if delta_a is not None:
        candidates.append(
            (
                "side_a",
                float(delta_a),
                float(calibration.get("side_a_scale_px") or 0.0),
            )
        )
    if delta_b is not None:
        candidates.append(
            (
                "side_b",
                float(delta_b),
                float(calibration.get("side_b_scale_px") or 0.0),
            )
        )

    if not candidates:
        return "unknown_side", None

    # Prefer the reference on the same signed side of the optimum. The sign is
    # only used to identify the side; each side has its own pixel scale so an
    # asymmetric Hough response does not bias the focus score.
    same_sign = [
        item for item in candidates
        if delta == 0.0 or item[1] == 0.0 or delta * item[1] > 0.0
    ]
    pool = same_sign or candidates
    side, _reference_delta, scale = min(
        pool,
        key=lambda item: abs(abs(delta) - abs(item[1])),
    )
    return side, (scale if scale > 0.0 else None)


def _score_focus(
    delta: float,
    calibration: dict | None,
) -> tuple[int, str, float | None]:
    distance = abs(delta)
    if calibration is None:
        score = int(round(max(0.0, min(100.0, 100.0 - distance * 8.0))))
        return score, "unknown_side", None

    tolerance = float(calibration["optimum_tolerance_px"])
    if distance <= tolerance:
        ratio = distance / max(tolerance, 1e-9)
        return int(round(100.0 - 2.0 * ratio)), "optimum", 0.0

    side, side_scale = _choose_side(delta, calibration)
    if side_scale is None or side_scale <= tolerance:
        score = int(round(max(0.0, 98.0 - (distance - tolerance) * 8.0)))
        return score, side, None

    normalized = distance / side_scale
    tolerance_ratio = min(0.95, tolerance / side_scale)

    if normalized <= 1.0:
        progress = (
            (normalized - tolerance_ratio)
            / max(1.0 - tolerance_ratio, 1e-9)
        )
        score = 98.0 - 53.0 * max(0.0, min(1.0, progress))
    else:
        score = 45.0 * max(0.0, 2.0 - normalized)

    return int(round(max(0.0, min(100.0, score)))), side, normalized


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
        delta = float(geometry["signed_error_px"] - baseline)
        score, side, normalized = _score_focus(delta, calibration)
        label = _focus_label(score)

        if side == "optimum":
            instruction = "Mise au point optimale"
        elif side == "side_a":
            instruction = "Cote A du foyer : ajuster progressivement vers l'optimum"
        elif side == "side_b":
            instruction = "Cote B du foyer : ajuster progressivement vers l'optimum"
        else:
            instruction = "Ajuster la mise au point puis refaire une pose de 4 s"

        return {
            "status": "ok",
            "image": str(path),
            "focus_score": score,
            "focus_label": label,
            "focus_ready": score >= 98,
            "focus_side": side,
            "instruction": instruction,
            "signed_error_px": round(float(geometry["signed_error_px"]), 3),
            "error_from_optimum_px": round(delta, 3),
            "absolute_error_px": round(abs(delta), 3),
            "normalized_focus_error": (
                round(float(normalized), 4)
                if normalized is not None
                else None
            ),
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
    root, optimum_paths, side_a_paths, side_b_paths = _latest_reference_sets()

    def summarized(paths: list[Path]) -> list[dict]:
        result: list[dict] = []
        for item in _safe_geometry(paths):
            if "signed_error_px" not in item:
                result.append(item)
                continue
            scored = analyze_bahtinov(item["image"])
            result.append(
                {
                    "image": item["image"],
                    "signed_error_px": round(float(item["signed_error_px"]), 3),
                    "focus_score": scored.get("focus_score"),
                    "focus_label": scored.get("focus_label"),
                    "focus_side": scored.get("focus_side"),
                    "normalized_focus_error": scored.get("normalized_focus_error"),
                    "geometry_confidence": round(
                        float(item["geometry_confidence"]), 4
                    ),
                }
            )
        return result

    return {
        "status": "ok" if root is not None else "reference_missing",
        "reference_root": str(root) if root is not None else None,
        "calibration": _calibration(),
        "optimum": summarized(optimum_paths),
        "side_a": summarized(side_a_paths),
        "side_b": summarized(side_b_paths),
    }
