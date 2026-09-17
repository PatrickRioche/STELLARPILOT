from __future__ import annotations

from pathlib import Path
from typing import Any

from app.imaging.quality import analyze_fits
from app.imaging.sessions import CaptureSessionService, capture_session_service
from app.imaging.solve_control import request_cancel, solve_robust_cancellable


def capture_centering_frame(
    session_id: str,
    service: CaptureSessionService | None = None,
) -> dict[str, Any]:
    """Capture one centering frame and expose its preview immediately.

    Capture and Assistant 3 now use the same runtime FITS preview renderer.
    The FITS is also scored before plate solving so the Capture screen can
    display the same astrometry-quality indicator as Preparation.
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

    metadata["centering_quality"] = analyze_fits(str(image))
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
    solution = solve_robust_cancellable(
        session_id=session_id,
        solver=service.solver,
        image=image,
        ra_hint=target["ra_hours"] * 15.0,
        dec_hint=target["dec_deg"],
    )

    solver_status = solution.get("status")
    centering_status = (
        "cancelled"
        if solver_status == "cancelled"
        else "busy"
        if solver_status == "busy"
        else "unsolved"
    )

    centering = {
        "status": centering_status,
        "attempts": int(current.get("attempts", 0)),
        "error_arcsec": None,
        "solve_ra_deg": solution.get("ra"),
        "solve_dec_deg": solution.get("dec"),
        "correction_ra_hours": None,
        "correction_dec_deg": None,
        "image": image,
        "solver_status": solver_status,
        "solver": solution.get("solver"),
        "solver_detail": solution.get("detail"),
        "pixel_scale_arcsec": solution.get("pixel_scale_arcsec"),
    }

    if (
        solver_status == "solved"
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


def cancel_centering_solve(
    session_id: str,
    service: CaptureSessionService | None = None,
) -> dict[str, Any]:
    """Request immediate cancellation of this session's active solve."""
    service = service or capture_session_service

    with service._lock:
        metadata = service._read(session_id)

    requested = request_cancel(session_id)
    current = dict(metadata.get("centering") or {})

    if requested:
        current["status"] = "cancelling"
        current["solver_status"] = "cancelling"
        current["solver_detail"] = "Arrêt de l'astrométrie demandé"
        metadata["centering"] = current
        metadata["state"] = "framing"

        with service._lock:
            service._write(metadata)

        return {
            "status": "cancelling",
            "detail": "Arrêt de l'astrométrie demandé",
            "session": metadata,
        }

    return {
        "status": "idle",
        "detail": "Aucune astrométrie active pour cette session",
        "session": metadata,
    }


# app.main imports this module only after app._main_core has finished creating
# the FastAPI instance. Register Assistant extension routes at this late point
# to avoid circular imports during INDI/imaging package initialization.
from app.imaging import assistant_reference_routes as _assistant_reference_routes  # noqa: E402,F401
from app.imaging import bahtinov_routes as _bahtinov_routes  # noqa: E402,F401
from app.imaging import cancel_routes as _cancel_routes  # noqa: E402,F401
from app.imaging import dark_routes as _dark_routes  # noqa: E402,F401
