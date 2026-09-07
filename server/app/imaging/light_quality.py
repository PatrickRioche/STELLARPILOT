from __future__ import annotations

from pathlib import Path
from typing import Any

import numpy as np
from astropy.io import fits
from scipy.ndimage import gaussian_filter, maximum_filter


MAX_SAMPLE_STARS = 120


def _robust_sigma(values: np.ndarray, center: float | None = None) -> float:
    finite = values[np.isfinite(values)]
    if finite.size == 0:
        return 1.0
    if center is None:
        center = float(np.median(finite))
    mad = float(np.median(np.abs(finite - center)))
    return max(1.4826 * mad, 1.0)


def _star_shape_metrics(
    smooth: np.ndarray,
    peaks: np.ndarray,
    background: float,
) -> tuple[float | None, float | None, float | None]:
    coordinates = np.argwhere(peaks)
    if coordinates.size == 0:
        return None, None, None

    peak_values = smooth[peaks]
    if coordinates.shape[0] > MAX_SAMPLE_STARS:
        indices = np.argsort(peak_values)[-MAX_SAMPLE_STARS:]
        coordinates = coordinates[indices]

    fwhm_values: list[float] = []
    ellipticity_values: list[float] = []
    signals: list[float] = []

    for y, x in coordinates:
        y0 = max(0, int(y) - 4)
        y1 = min(smooth.shape[0], int(y) + 5)
        x0 = max(0, int(x) - 4)
        x1 = min(smooth.shape[1], int(x) + 5)
        patch = smooth[y0:y1, x0:x1].astype(np.float64, copy=False)
        weights = np.clip(patch - background, 0.0, None)
        total = float(np.sum(weights))
        if total <= 0.0:
            continue

        yy, xx = np.mgrid[y0:y1, x0:x1]
        cx = float(np.sum(xx * weights) / total)
        cy = float(np.sum(yy * weights) / total)
        var_x = float(np.sum(((xx - cx) ** 2) * weights) / total)
        var_y = float(np.sum(((yy - cy) ** 2) * weights) / total)
        if var_x <= 0.0 or var_y <= 0.0:
            continue

        sigma_major = float(np.sqrt(max(var_x, var_y)))
        sigma_minor = float(np.sqrt(min(var_x, var_y)))
        fwhm = 2.355 * np.sqrt((var_x + var_y) / 2.0) * 2.0
        ellipticity = 1.0 - sigma_minor / sigma_major

        fwhm_values.append(float(fwhm))
        ellipticity_values.append(float(ellipticity))
        signals.append(float(np.max(patch) - background))

    if not fwhm_values:
        return None, None, None

    return (
        float(np.median(fwhm_values)),
        float(np.median(ellipticity_values)),
        float(np.median(signals)),
    )


def _baseline_value(samples: list[dict[str, Any]], key: str) -> float | None:
    values = [
        float(item[key])
        for item in samples
        if item.get(key) is not None and np.isfinite(float(item[key]))
    ]
    if not values:
        return None
    return float(np.median(values))


def analyze_light_quality(
    image: Path | str,
    *,
    baseline: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    path = Path(image)
    if not path.exists():
        return {
            "status": "error",
            "accepted": False,
            "reasons": ["missing_file"],
            "image": str(path),
        }

    try:
        with fits.open(path, memmap=False) as hdul:
            data = np.squeeze(np.asarray(hdul[0].data)).astype(np.float32, copy=False)
            header = hdul[0].header
    except Exception as exc:
        return {
            "status": "error",
            "accepted": False,
            "reasons": ["fits_read_error"],
            "image": str(path),
            "detail": f"{exc.__class__.__name__}: {exc}",
        }

    if data.ndim != 2:
        return {
            "status": "error",
            "accepted": False,
            "reasons": ["invalid_geometry"],
            "image": str(path),
        }

    finite = data[np.isfinite(data)]
    if finite.size == 0:
        return {
            "status": "error",
            "accepted": False,
            "reasons": ["no_finite_pixels"],
            "image": str(path),
        }

    background = float(np.median(finite))
    background_sigma = _robust_sigma(finite, background)

    saturation_level = header.get("SPSATLVL")
    try:
        saturation_level = float(saturation_level)
    except (TypeError, ValueError):
        saturation_level = None

    saturated_percent = 0.0
    if saturation_level is not None and saturation_level > 0.0:
        saturated_percent = float(
            np.count_nonzero(finite >= saturation_level)
            / finite.size
            * 100.0
        )

    sample = data[::2, ::2]
    smooth = gaussian_filter(
        np.nan_to_num(sample, nan=background),
        sigma=1.0,
    )
    sample_background = float(np.median(smooth))
    sample_sigma = _robust_sigma(smooth, sample_background)
    threshold = sample_background + 6.0 * sample_sigma
    local_max = maximum_filter(smooth, size=5, mode="nearest")
    peaks = (smooth == local_max) & (smooth > threshold)
    if saturation_level is not None:
        peaks &= smooth < saturation_level

    star_count = int(np.count_nonzero(peaks))
    fwhm_px, ellipticity, star_signal = _star_shape_metrics(
        smooth,
        peaks,
        sample_background,
    )

    reasons: list[str] = []
    if saturated_percent >= 1.0:
        reasons.append("overexposed")
    if star_count < 5:
        reasons.append("too_few_stars")
    if fwhm_px is not None and fwhm_px > 12.0:
        reasons.append("blurred")
    if ellipticity is not None and ellipticity > 0.65:
        reasons.append("trailed")

    baseline = baseline or []
    if len(baseline) >= 3:
        base_star_count = _baseline_value(baseline, "star_count")
        base_fwhm = _baseline_value(baseline, "fwhm_px")
        base_ellipticity = _baseline_value(baseline, "ellipticity")
        base_signal = _baseline_value(baseline, "star_signal")
        base_noise = _baseline_value(baseline, "background_sigma")

        if (
            base_star_count is not None
            and star_count < max(5.0, base_star_count * 0.35)
        ):
            reasons.append("transparency_drop")
        if (
            base_signal is not None
            and star_signal is not None
            and star_signal < base_signal * 0.35
        ):
            reasons.append("signal_drop")
        if (
            base_fwhm is not None
            and fwhm_px is not None
            and fwhm_px > max(12.0, base_fwhm * 1.8)
        ):
            reasons.append("focus_or_seeing_degraded")
        if (
            base_ellipticity is not None
            and ellipticity is not None
            and ellipticity > max(0.55, base_ellipticity + 0.25)
        ):
            reasons.append("tracking_degraded")
        if (
            base_noise is not None
            and background_sigma > max(base_noise * 2.5, base_noise + 20.0)
        ):
            reasons.append("background_degraded")

    # Keep reasons deterministic and compact for API/UI counters.
    reasons = list(dict.fromkeys(reasons))

    score = 100.0
    score -= min(saturated_percent / 1.0, 1.0) * 35.0
    if star_count < 25:
        score -= min((25 - star_count) / 25.0, 1.0) * 25.0
    if fwhm_px is not None:
        score -= min(max(fwhm_px - 4.0, 0.0) / 8.0, 1.0) * 20.0
    if ellipticity is not None:
        score -= min(max(ellipticity - 0.15, 0.0) / 0.5, 1.0) * 20.0
    score = int(round(max(0.0, min(100.0, score))))

    return {
        "status": "ok",
        "accepted": not reasons,
        "reasons": reasons,
        "image": str(path),
        "score": score,
        "star_count": star_count,
        "background": round(background, 3),
        "background_sigma": round(background_sigma, 3),
        "saturated_percent": round(saturated_percent, 6),
        "fwhm_px": round(fwhm_px, 3) if fwhm_px is not None else None,
        "ellipticity": round(ellipticity, 4) if ellipticity is not None else None,
        "star_signal": round(star_signal, 3) if star_signal is not None else None,
    }
