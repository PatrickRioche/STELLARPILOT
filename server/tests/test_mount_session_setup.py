from types import SimpleNamespace

from fastapi.testclient import TestClient

import app.main as main_module
from app.indi import session_setup_routes
from app.main import app


client = TestClient(app)


def test_set_required_slew_rate_writes_and_confirms_level_6(monkeypatch):
    monkeypatch.setattr(
        main_module._core.indi_service,
        "_find_connected_mount",
        lambda: "LX200 OnStep",
    )

    calls = []
    read_count = {"value": 0}

    def fake_run(args, **kwargs):
        calls.append(args)
        executable = args[0]

        if executable == "indi_getprop":
            read_count["value"] += 1
            if read_count["value"] == 1:
                stdout = (
                    "LX200 OnStep.Max slew Rate.maxSlew=5\n"
                    "LX200 OnStep.TELESCOPE_SLEW_RATE.6=On\n"
                )
            else:
                stdout = (
                    "LX200 OnStep.Max slew Rate.maxSlew=6\n"
                    "LX200 OnStep.TELESCOPE_SLEW_RATE.6=On\n"
                )
            return SimpleNamespace(returncode=0, stdout=stdout, stderr="")

        return SimpleNamespace(returncode=0, stdout="", stderr="")

    monkeypatch.setattr(
        session_setup_routes.subprocess,
        "run",
        fake_run,
    )
    monkeypatch.setattr(
        session_setup_routes.time,
        "sleep",
        lambda _seconds: None,
    )

    result = session_setup_routes._set_required_slew_rate()

    assert result["status"] == "ready"
    assert result["max_slew"] == 6.0
    assert result["level_selected"] is True
    assert result["changed"] is True
    assert any(
        "LX200 OnStep.Max slew Rate.maxSlew=6" in call
        for call in calls
        if call and call[0] == "indi_setprop"
    )


def test_prepare_mount_session_combines_time_and_slew(monkeypatch):
    monkeypatch.setattr(
        session_setup_routes,
        "mount_time_sync",
        lambda: {
            "status": "synced",
            "reference_source": "gps",
        },
    )
    monkeypatch.setattr(
        session_setup_routes,
        "_set_required_slew_rate",
        lambda: {
            "status": "ready",
            "required_rate": 6,
            "max_slew": 6.0,
            "level_selected": True,
            "changed": False,
        },
    )
    monkeypatch.setattr(
        session_setup_routes,
        "mount_time_verification",
        lambda: {
            "status": "verified",
            "control_ready": True,
        },
    )

    response = client.post("/mount/session/prepare")

    assert response.status_code == 200
    payload = response.json()
    assert payload["status"] == "ready"
    assert payload["time_sync"]["status"] == "synced"
    assert payload["slew_rate"]["max_slew"] == 6.0
    assert payload["time_verification"]["control_ready"] is True
    assert payload["movement_commanded"] is False


def test_prepare_mount_session_reports_time_failure(monkeypatch):
    monkeypatch.setattr(
        session_setup_routes,
        "mount_time_sync",
        lambda: {
            "status": "error",
            "detail": "Aucune monture INDI connectée",
        },
    )

    response = client.post("/mount/session/prepare")

    assert response.status_code == 200
    payload = response.json()
    assert payload["status"] == "error"
    assert payload["slew_rate"] is None
    assert payload.get("movement_commanded", False) is False
