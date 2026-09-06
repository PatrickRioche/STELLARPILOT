from pathlib import Path

import numpy as np
from astropy.io import fits
from scipy.ndimage import gaussian_filter, maximum_filter


def _quality_label(score: int) -> str:
    if score >= 75:
        return "Bonne"
    if score >= 50:
        return "Correcte"
    if score >= 25:
        return "Faible"
    return "Insuffisante"


def _recommended_exposure_factor(
    *,
    classification: str,
    star_count: int,
    score: int,
) -> float:
    if classification == "overexposed":
        return 0.5
    if star_count < 8:
        return 4.0
    if star_count < 25:
        return 2.0
    if score < 75:
        return 1.5
    return 1.0


def analyze_fits(image: str) -> dict:
    path = Path(image)

    if not path.exists():
        return {
            "status": "error",
            "detail": "Fichier FITS introuvable",
            "image": str(path),
        }

    try:
        with fits.open(path, memmap=False) as hdul:
            data = hdul[0].data
            header = hdul[0].header

            if data is None:
                return {
                    "status": "error",
                    "detail": "FITS sans donnees image",
                    "image": str(path),
                }

            data = np.asarray(data)

            if data.ndim > 2:
                data = np.squeeze(data)

            if data.ndim != 2:
                return {
                    "status": "error",
                    "detail": f"Dimensions FITS inattendues: {data.shape}",
                    "image": str(path),
                }

            finite = data[np.isfinite(data)]

            if finite.size == 0:
                return {
                    "status": "error",
                    "detail": "Aucun pixel exploitable",
                    "image": str(path),
                }

            median = float(np.median(finite))
            mad = float(np.median(np.abs(finite - median)))
            sigma = 1.4826 * mad

            minimum = float(np.min(finite))
            maximum = float(np.max(finite))
            p01 = float(np.percentile(finite, 1.0))
            p99 = float(np.percentile(finite, 99.0))
            p999 = float(np.percentile(finite, 99.9))

            bitpix = abs(int(header.get("BITPIX", 16)))
            bzero = float(header.get("BZERO", 0.0))

            if np.issubdtype(data.dtype, np.unsignedinteger):
                full_scale = float(np.iinfo(data.dtype).max)
            elif bitpix == 16 and bzero == 32768.0:
                full_scale = 65535.0
            elif bitpix <= 16:
                full_scale = 32767.0
            else:
                full_scale = maximum

            saturation_level = full_scale * 0.98
            saturated_percent = float(
                np.count_nonzero(finite >= saturation_level)
                / finite.size
                * 100.0
            )

            # Detection approximative de sources.
            # Travail sur une image 1 pixel sur 2 pour rester rapide.
            sample = data[::2, ::2].astype(np.float32, copy=False)
            smooth = gaussian_filter(sample, sigma=1.0)

            sample_median = float(np.median(smooth))
            sample_mad = float(
                np.median(np.abs(smooth - sample_median))
            )
            sample_sigma = max(1.4826 * sample_mad, 1.0)

            threshold = sample_median + 6.0 * sample_sigma

            local_max = maximum_filter(
                smooth,
                size=5,
                mode="nearest",
            )

            peaks = (
                (smooth == local_max)
                & (smooth > threshold)
                & (smooth < saturation_level)
            )

            star_count = int(np.count_nonzero(peaks))

            median_percent_full_scale = (
                median / full_scale * 100.0
                if full_scale > 0
                else 0.0
            )

            p999_excess_sigma = (
                (p999 - median) / max(sigma, 1.0)
            )

            # Seuils provisoires, a calibrer sur les captures reelles du setup.
            if (
                saturated_percent >= 1.0
                or median_percent_full_scale >= 70.0
            ):
                classification = "overexposed"
            elif (
                star_count >= 25
                and p999_excess_sigma >= 8.0
            ):
                classification = "astrometry_ready"
            else:
                classification = "insufficient_stars"

            # Score exclusivement destine a juger l'aptitude au plate solving,
            # et non la qualite esthetique de la photographie.
            star_component = min(star_count / 60.0, 1.0) * 55.0
            signal_component = (
                min(max((p999_excess_sigma - 3.0) / 12.0, 0.0), 1.0)
                * 30.0
            )
            saturation_penalty = min(saturated_percent / 1.0, 1.0) * 40.0
            background_penalty = (
                min(
                    max((median_percent_full_scale - 50.0) / 20.0, 0.0),
                    1.0,
                )
                * 20.0
            )
            raw_score = (
                15.0
                + star_component
                + signal_component
                - saturation_penalty
                - background_penalty
            )
            score = int(round(min(100.0, max(0.0, raw_score))))

            if classification == "overexposed":
                score = min(score, 24)
            elif classification == "insufficient_stars":
                score = min(score, 49)
            else:
                score = max(score, 50)

            quality_label = _quality_label(score)
            recommended_factor = _recommended_exposure_factor(
                classification=classification,
                star_count=star_count,
                score=score,
            )

            return {
                "status": "ok",
                "image": str(path),
                "width": int(data.shape[1]),
                "height": int(data.shape[0]),
                "dtype": str(data.dtype),
                "bitpix": bitpix,
                "minimum": round(minimum, 3),
                "median": round(median, 3),
                "background_sigma": round(sigma, 3),
                "p01": round(p01, 3),
                "p99": round(p99, 3),
                "p999": round(p999, 3),
                "maximum": round(maximum, 3),
                "full_scale": round(full_scale, 3),
                "saturated_percent": round(saturated_percent, 6),
                "star_threshold": round(threshold, 3),
                "star_count": star_count,
                "median_percent_full_scale": round(
                    median_percent_full_scale,
                    3,
                ),
                "p999_excess_sigma": round(
                    p999_excess_sigma,
                    3,
                ),
                "classification": classification,
                "astrometry_score": score,
                "quality_label": quality_label,
                "recommended_exposure_factor": recommended_factor,
                "astrometry_ready": score >= 50,
            }

    except Exception as exc:
        return {
            "status": "error",
            "image": str(path),
            "detail": f"{exc.__class__.__name__}: {exc}",
        }
