from __future__ import annotations

from app import _main_core as _core
from app.imaging.centering_capture import cancel_centering_solve
from app.imaging.sessions import CaptureSessionService, capture_session_service


def start_stack_test(
    session_id: str,
    service: CaptureSessionService | None = None,
):
    """Start stacking for V0.6.7 field tests without requiring centering."""
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
        stacking = metadata["stacking"]
        stacking["start_centering_status"] = centering_status
        stacking["test_mode"] = centering_status != "centered"
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
