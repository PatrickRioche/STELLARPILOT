from __future__ import annotations

from pathlib import Path

from app.imaging.cancel_routes import start_stack_test
from app.imaging.sessions import CaptureSessionService


def _service_with_session(tmp_path: Path, centering_status: str):
    service = CaptureSessionService(
        runtime_root=tmp_path / "runtime",
        galleries_root=tmp_path / "galleries",
    )
    session_id = "field-test"
    service._ensure_tree(service._session_dir(session_id))
    metadata = {
        "id": session_id,
        "state": "framing",
        "centering": {"status": centering_status},
        "stacking": {
            "running": False,
            "astrometry_required": True,
            "active_started_at": None,
            "accumulated_active_seconds": 0.0,
        },
        "counts": {"captured": 0, "accepted": 0, "rejected": 0},
        "gallery_path": None,
    }
    service._write(metadata)
    return service, session_id


def test_uncentered_stack_test_disables_automatic_recenter(
    tmp_path,
    monkeypatch,
):
    service, session_id = _service_with_session(
        tmp_path,
        "correction_required",
    )

    monkeypatch.setattr(
        service,
        "_launch_stack_locked",
        lambda _session_id, metadata: {
            "status": "stacking",
            "session": metadata,
        },
    )

    result = start_stack_test(session_id, service=service)
    stacking = result["session"]["stacking"]

    assert result["status"] == "stacking"
    assert stacking["test_mode"] is True
    assert stacking["automatic_recenter_enabled"] is False
    assert stacking["astrometry_required"] is False
    assert stacking["recenter_required"] is False
    assert stacking["recenter_reason"] is None


def test_centered_start_test_keeps_normal_astrometry(
    tmp_path,
    monkeypatch,
):
    service, session_id = _service_with_session(
        tmp_path,
        "centered",
    )

    monkeypatch.setattr(
        service,
        "_launch_stack_locked",
        lambda _session_id, metadata: {
            "status": "stacking",
            "session": metadata,
        },
    )

    result = start_stack_test(session_id, service=service)
    stacking = result["session"]["stacking"]

    assert stacking["test_mode"] is False
    assert stacking["automatic_recenter_enabled"] is True
    assert stacking["astrometry_required"] is True
