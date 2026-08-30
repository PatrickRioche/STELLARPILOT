from app.imaging.quality import analyze_fits
from app.indi.service import indi_service


def run_auto_capture(
    start_exposure_s: float,
    min_exposure_s: float = 0.001,
    max_exposure_s: float = 10.0,
    max_attempts: int = 5,
) -> dict:
    exposure_s = max(
        min_exposure_s,
        min(start_exposure_s, max_exposure_s),
    )

    attempts = []
    seen_exposures = set()
    lower_exposure_s = None
    upper_exposure_s = None

    for attempt_number in range(1, max_attempts + 1):
        exposure_key = round(exposure_s, 6)

        if exposure_key in seen_exposures:
            return {
                "status": "exhausted",
                "reason": "exposure_cycle_detected",
                "attempts": attempts,
            }

        seen_exposures.add(exposure_key)

        capture = indi_service.capture(exposure_s)

        attempt = {
            "attempt": attempt_number,
            "exposure_s": exposure_key,
            "capture_status": capture.get("status"),
            "image": capture.get("image"),
        }

        if capture.get("status") != "captured":
            attempts.append(attempt)
            return {
                "status": "error",
                "reason": "capture_failed",
                "attempts": attempts,
                "capture": capture,
            }

        quality = analyze_fits(capture["image"])
        attempt["quality"] = quality
        attempts.append(attempt)

        if quality.get("status") != "ok":
            return {
                "status": "error",
                "reason": "quality_analysis_failed",
                "attempts": attempts,
                "capture": capture,
                "quality": quality,
            }

        classification = quality.get("classification")

        if classification == "astrometry_ready":
            capture["quality"] = quality
            return {
                "status": "ready",
                "reason": "astrometry_ready",
                "exposure_s": exposure_key,
                "capture": capture,
                "attempts": attempts,
            }

        if classification == "overexposed":
            if (
                upper_exposure_s is None
                or exposure_s < upper_exposure_s
            ):
                upper_exposure_s = exposure_s

            if lower_exposure_s is not None:
                next_exposure_s = (
                    lower_exposure_s + upper_exposure_s
                ) / 2.0
            else:
                saturated = float(
                    quality.get("saturated_percent", 0.0)
                )
                median_percent = float(
                    quality.get(
                        "median_percent_full_scale",
                        0.0,
                    )
                )

                if (
                    saturated >= 5.0
                    or median_percent >= 90.0
                ):
                    factor = 0.25
                else:
                    factor = 0.5

                next_exposure_s = exposure_s * factor

        else:
            if (
                lower_exposure_s is None
                or exposure_s > lower_exposure_s
            ):
                lower_exposure_s = exposure_s

            if upper_exposure_s is not None:
                next_exposure_s = (
                    lower_exposure_s + upper_exposure_s
                ) / 2.0
            else:
                star_count = int(
                    quality.get("star_count", 0)
                )

                if star_count == 0:
                    factor = 2.5
                elif star_count < 10:
                    factor = 2.0
                else:
                    factor = 1.5

                next_exposure_s = exposure_s * factor

        next_exposure_s = max(
            min_exposure_s,
            min(next_exposure_s, max_exposure_s),
        )

        next_key = round(next_exposure_s, 6)
        attempt["next_exposure_s"] = next_key

        if next_key == exposure_key:
            return {
                "status": "exhausted",
                "reason": "exposure_limit_reached",
                "attempts": attempts,
            }

        exposure_s = next_exposure_s

    return {
        "status": "exhausted",
        "reason": "max_attempts_reached",
        "attempts": attempts,
    }
