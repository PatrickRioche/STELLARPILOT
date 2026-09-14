from __future__ import annotations

from app import _main_core as _core
from app.imaging.centering_capture import cancel_centering_solve


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
