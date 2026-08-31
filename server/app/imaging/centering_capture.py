from __future__ import annotations

from pathlib import Path
from typing import Any

from app.imaging.sessions import CaptureSessionService, capture_session_service


def capture_centering_frame(
    session_id: str,
    service: CaptureSessionService | None = None,
) -> dict[str, Any]:
    """Capture one centering frame and expose its preview immediately.

    The previous /center endpoint performed capture + astrometry in one
    blocking request.  That meant Android could not display the image until
    plate solving had completed.  This first phase ends as soon as the FITS
    has been acquired and its JPEG preview has been written.
    """
    service = service or capture_session_service

    with service._lock:
        metadata = service._read(session_id)
        attempt = int(metadata["centering"].get("attempts", 0)) + 1

    result = service._capture_into(
        metadata,
        f"center_{attempt:03d}",
    )

    if result.get("status") != "captured":
        return {
            "status": "error",
            "detail": result.get("detail", "Capture impossible"),
            "session": metadata,
        }

    image = result["image"]
    preview_path = (
        service._session_dir(session_id)
        / "previews"
        / "latest.jpg"
    )

    try:
        preview_path.write_bytes(
            service._fits_preview_bytes(Path(image))
        )
        metadata["preview"] = str(preview_path)
    except Exception:
        metadata["preview"] = None

    metadata["centering"] = {
        "status": "captured",
        "attempts": attempt,
        "error_arcsec": None,
        "solve_ra_deg": None,
        "solve_dec_deg": None,
        "correction_ra_hours": None,
        "correction_dec_deg": None,
        "image": image,
        "solver_status": None,
        "solver": None,
        "solver_detail": None,
        "pixel_scale_arcsec": None,
    }
    metadata["state"] = "framing"

    with service._lock:
        service._write(metadata)

    return {
        "status": "captured",
        "session": metadata,
    }


def solve_centering_frame(
    session_id: str,
    service: CaptureSessionService | None = None,
) -> dict[str, Any]:
    """Plate-solve the most recently captured centering frame."""
    service = service or capture_session_service

    with service._lock:
        metadata = service._read(session_id)

    current = metadata.get("centering") or {}
    image = current.get("image") or metadata.get("last_frame")

    if not image:
        return {
            "status": "error",
            "detail": "Aucune image de centrage à résoudre",
            "session": metadata,
        }

    target = metadata["target"]
    solution = service.solver.solve_robust(
        image,
        ra_hint=target["ra_hours"] * 15.0,
        dec_hint=target["dec_deg"],
    )

    centering = {
        "status": "unsolved",
        "attempts": int(current.get("attempts", 0)),
        "error_arcsec": None,
        "solve_ra_deg": solution.get("ra"),
        "solve_dec_deg": solution.get("dec"),
        "correction_ra_hours": None,
        "correction_dec_deg": None,
        "image": image,
        "solver_status": solution.get("status"),
        "solver": solution.get("solver"),
        "solver_detail": solution.get("detail"),
        "pixel_scale_arcsec": solution.get("pixel_scale_arcsec"),
    }

    if (
        solution.get("status") == "solved"
        and solution.get("ra") is not None
        and solution.get("dec") is not None
    ):
        centering.update(
            service._centering_result(
                target["ra_hours"],
                target["dec_deg"],
                float(solution["ra"]),
                float(solution["dec"]),
                metadata["setup"]["centering_tolerance_arcsec"],
            )
        )

    metadata["centering"] = centering
    metadata["state"] = (
        "centered"
        if centering["status"] == "centered"
        else "framing"
    )

    with service._lock:
        service._write(metadata)

    return {
        "status": centering["status"],
        "centering": centering,
        "session": metadata,
    }
