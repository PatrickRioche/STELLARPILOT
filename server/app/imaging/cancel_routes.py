from __future__ import annotations

from fastapi.responses import Response

from app import _main_core as _core
from app.imaging.centering_capture import cancel_centering_solve
from app.imaging.sessions import CaptureSessionService, capture_session_service
from app.imaging import field_safety as _field_safety  # noqa: F401


def start_stack_test(
    session_id: str,
    service: CaptureSessionService | None = None,
):
    """Start a safe field-test stack without requiring validated centering.

    When the target is not centered, calibration, quality filtering,
    registration and stacking remain active, but periodic astrometry is
    disabled so a test session can never trigger an automatic mount GOTO.
    """
    active_service = service or capture_session_service
    with active_service._lock:
        metadata = active_service._read(session_id)
        thread = active_service._threads.get(session_id)
        if thread is not None and thread.is_alive():
            return {"status": "already_running", "session": metadata}

        if metadata.get("gallery_path") or metadata.get("state") == "completed":
            return {
                "status": "finalized",
                "detail": "Cette session est déjà enregistrée dans la galerie",
                "session": metadata,
            }

        centering_status = (
            metadata.get("centering", {}).get("status") or "not_checked"
        )
        test_mode = centering_status != "centered"
        stacking = metadata["stacking"]
        stacking["start_centering_status"] = centering_status
        stacking["test_mode"] = test_mode
        stacking["automatic_recenter_enabled"] = not test_mode
        stacking["astrometry_required"] = not test_mode
        stacking["recenter_required"] = False
        stacking["recenter_reason"] = None

        return active_service._launch_stack_locked(session_id, metadata)


@_core.app.post("/capture/sessions/{session_id}/center/cancel")
def cancel_capture_session_astrometry(session_id: str):
    """Cancel the active astrometry.net solve while preserving the FITS."""
    try:
        return cancel_centering_solve(session_id)
    except FileNotFoundError:
        return {
            "status": "error",
            "detail": "Session de capture introuvable",
        }


@_core.app.post("/capture/sessions/{session_id}/stack/start-test")
def start_capture_session_stack_test(session_id: str):
    """Allow field-test stacking even when centering is not validated."""
    try:
        return start_stack_test(session_id)
    except (FileNotFoundError, KeyError):
        return {
            "status": "error",
            "detail": "Session de capture introuvable",
        }


@_core.app.get("/capture/sessions/{session_id}/last-preview.jpg")
def capture_session_last_preview(session_id: str):
    """Render the most recently captured frame, accepted or rejected."""
    try:
        metadata = capture_session_service.get_session(session_id)
        source = metadata.get("last_frame")
        if not source:
            return Response(status_code=404)
        content = capture_session_service._fits_preview_bytes(
            __import__("pathlib").Path(source)
        )
        return Response(
            content=content,
            media_type="image/jpeg",
            headers={"Cache-Control": "no-store"},
        )
    except (FileNotFoundError, KeyError, OSError, ValueError):
        return Response(status_code=404)
