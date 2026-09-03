import app.main as main_module

from fastapi.testclient import TestClient


client = TestClient(main_module.app)


def _allow_trusted_android_time(monkeypatch):
    monkeypatch.setattr(
        main_module._core.gps_service,
        "status",
        lambda: {"status": "no_fix", "time_utc": None},
    )
    monkeypatch.setattr(
        main_module._core.system_service,
        "status",
        lambda: {"datetime": "2026-09-03T10:00:00+00:00"},
    )
    monkeypatch.setattr(
        main_module._core,
        "_resolve_time",
        lambda _gps, _system: ("2026-09-03T10:00:00Z", "android"),
    )
    monkeypatch.setattr(
        main_module._core.state,
        "client_timezone_offset_minutes",
        None,
    )
    monkeypatch.setattr(
        main_module._core.indi_service,
        "mount_time_status",
        lambda reference_utc=None, reference_source=None: {
            "status": "available",
            "indi_state": "Ok",
            "offset_hours": 2.0,
            "reference_utc": reference_utc,
            "reference_source": reference_source,
        },
    )


def test_mount_frame_goto_uses_exact_mount_coordinates(monkeypatch):
    _allow_trusted_android_time(monkeypatch)

    monkeypatch.setattr(
        main_module._core.indi_service,
        "mount_status",
        lambda: {
            "status": "tracking",
            "ra": 12.315678,
            "dec": 44.25,
        },
    )

    called = {}

    def fake_goto(ra, dec, tracking_mode="sidereal"):
        called.update(
            ra=ra,
            dec=dec,
            tracking_mode=tracking_mode,
        )
        return {
            "status": "slewing",
            "mode": "device",
            "ra": ra,
            "dec": dec,
            "tracking_mode": tracking_mode,
        }

    monkeypatch.setattr(
        main_module._core.indi_service,
        "goto",
        fake_goto,
    )

    response = client.post(
        "/mount/goto-mount-frame",
        json={
            "ra": 12.345678,
            "dec": 44.25,
            "tracking_mode": "sidereal",
        },
    )

    assert response.status_code == 200
    body = response.json()

    assert body["status"] == "slewing"
    assert body["coordinate_frame"] == "mount"
    assert body["coordinate_transform"]["transformed"] is False
    assert body["requested_mount_frame"] == {
        "ra_hours": 12.345678,
        "dec_deg": 44.25,
    }
    assert body["diagnostic_safety"]["requested_ra_delta_deg"] == 0.45
    assert body["diagnostic_safety"]["requested_dec_delta_deg"] == 0.0
    assert called == {
        "ra": 12.345678,
        "dec": 44.25,
        "tracking_mode": "sidereal",
    }


def test_mount_frame_goto_rejects_large_move(monkeypatch):
    _allow_trusted_android_time(monkeypatch)

    monkeypatch.setattr(
        main_module._core.indi_service,
        "mount_status",
        lambda: {
            "status": "tracking",
            "ra": 12.0,
            "dec": 44.0,
        },
    )

    def forbidden_goto(*args, **kwargs):
        raise AssertionError("Large diagnostic movement must be blocked")

    monkeypatch.setattr(
        main_module._core.indi_service,
        "goto",
        forbidden_goto,
    )

    response = client.post(
        "/mount/goto-mount-frame",
        json={
            "ra": 12.2,
            "dec": 44.0,
            "tracking_mode": "sidereal",
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "error"
    assert "0,75" in body["detail"]


def test_mount_frame_goto_keeps_time_alert_safety(monkeypatch):
    monkeypatch.setattr(
        main_module._core.gps_service,
        "status",
        lambda: {"status": "fix", "time_utc": "2026-09-03T10:00:00Z"},
    )
    monkeypatch.setattr(
        main_module._core.system_service,
        "status",
        lambda: {},
    )
    monkeypatch.setattr(
        main_module._core,
        "_resolve_time",
        lambda _gps, _system: ("2026-09-03T10:00:00Z", "gps"),
    )
    monkeypatch.setattr(
        main_module._core.indi_service,
        "mount_time_status",
        lambda reference_utc=None, reference_source=None: {
            "status": "available",
            "indi_state": "Alert",
            "offset_hours": 2.0,
        },
    )

    def forbidden_goto(*args, **kwargs):
        raise AssertionError("GOTO must be blocked while TIME_UTC is Alert")

    monkeypatch.setattr(
        main_module._core.indi_service,
        "goto",
        forbidden_goto,
    )

    response = client.post(
        "/mount/goto-mount-frame",
        json={
            "ra": 12.0,
            "dec": 45.0,
            "tracking_mode": "sidereal",
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "error"
    assert body["coordinate_frame"] == "mount"
    assert "Alert" in body["detail"]
