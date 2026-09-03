import time

import app.main as main_module
import pytest

from fastapi.testclient import TestClient


client = TestClient(main_module.app)


def _clear_verified_sync():
    state = main_module._core.state
    state.mount_time_sync_reference_utc = None
    state.mount_time_sync_mount_utc = None
    state.mount_time_sync_source = None
    state.mount_time_sync_offset_minutes = None
    state.mount_time_sync_monotonic_s = None


def _set_verified_sync(mount_utc="2026-09-03T10:00:00Z"):
    state = main_module._core.state
    state.mount_time_sync_reference_utc = mount_utc
    state.mount_time_sync_mount_utc = mount_utc
    state.mount_time_sync_source = "android"
    state.mount_time_sync_offset_minutes = 120
    state.mount_time_sync_monotonic_s = time.monotonic()


def _allow_trusted_android_time(monkeypatch):
    _clear_verified_sync()
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
        120,
    )
    monkeypatch.setattr(
        main_module._core.indi_service,
        "mount_time_status",
        lambda reference_utc=None, reference_source=None: {
            "status": "available",
            "utc": "2026-09-03T10:00:00Z",
            "indi_state": "Ok",
            "offset_hours": 2.0,
            "reference_utc": reference_utc,
            "reference_source": reference_source,
            "drift_seconds": 120.0,
            "synchronized": False,
            "synchronization": "drift",
        },
    )
    _set_verified_sync()


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
        called.update(ra=ra, dec=dec, tracking_mode=tracking_mode)
        return {
            "status": "slewing",
            "mode": "device",
            "ra": ra,
            "dec": dec,
            "tracking_mode": tracking_mode,
        }

    monkeypatch.setattr(main_module._core.indi_service, "goto", fake_goto)

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
    assert body["time_check"]["time_sync_verification"]["verified"] is True
    assert body["time_check"]["raw_drift_advisory"] is True
    assert body["diagnostic_safety"]["requested_ra_delta_deg"] == pytest.approx(
        0.45,
        abs=1e-9,
    )
    assert called == {
        "ra": 12.345678,
        "dec": 44.25,
        "tracking_mode": "sidereal",
    }


def test_static_time_utc_setpoint_remains_valid_after_raw_drift(monkeypatch):
    _allow_trusted_android_time(monkeypatch)

    monkeypatch.setattr(
        main_module._core.indi_service,
        "mount_status",
        lambda: {"status": "tracking", "ra": 12.0, "dec": 44.0},
    )
    monkeypatch.setattr(
        main_module._core.indi_service,
        "goto",
        lambda ra, dec, tracking_mode="sidereal": {
            "status": "slewing",
            "ra": ra,
            "dec": dec,
            "tracking_mode": tracking_mode,
        },
    )

    response = client.post(
        "/mount/goto-mount-frame",
        json={"ra": 12.03, "dec": 44.0, "tracking_mode": "sidereal"},
    )
    body = response.json()

    assert body["status"] == "slewing"
    assert body["time_check"]["drift_seconds"] == 120.0
    verification = body["time_check"]["time_sync_verification"]
    assert verification["verified"] is True
    assert verification["readback_kind"] == "indi_setpoint"
    assert verification["readback_matches_setpoint"] is True


def test_mount_frame_goto_rejects_large_move(monkeypatch):
    _allow_trusted_android_time(monkeypatch)

    monkeypatch.setattr(
        main_module._core.indi_service,
        "mount_status",
        lambda: {"status": "tracking", "ra": 12.0, "dec": 44.0},
    )

    def forbidden_goto(*args, **kwargs):
        raise AssertionError("Large diagnostic movement must be blocked")

    monkeypatch.setattr(main_module._core.indi_service, "goto", forbidden_goto)

    response = client.post(
        "/mount/goto-mount-frame",
        json={"ra": 12.2, "dec": 44.0, "tracking_mode": "sidereal"},
    )

    body = response.json()
    assert body["status"] == "error"
    assert "0,75" in body["detail"]


def test_mount_frame_goto_keeps_time_alert_safety(monkeypatch):
    _clear_verified_sync()
    monkeypatch.setattr(
        main_module._core.gps_service,
        "status",
        lambda: {"status": "fix", "time_utc": "2026-09-03T10:00:00Z"},
    )
    monkeypatch.setattr(main_module._core.system_service, "status", lambda: {})
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
            "utc": "2026-09-03T10:00:00Z",
            "indi_state": "Alert",
            "offset_hours": 2.0,
        },
    )

    response = client.post(
        "/mount/goto-mount-frame",
        json={"ra": 12.0, "dec": 45.0, "tracking_mode": "sidereal"},
    )
    body = response.json()
    assert body["status"] == "error"
    assert "Alert" in body["detail"]


def test_mount_frame_goto_requires_explicit_time_sync(monkeypatch):
    _clear_verified_sync()
    monkeypatch.setattr(
        main_module._core.gps_service,
        "status",
        lambda: {"status": "fix", "time_utc": "2026-09-03T12:56:10Z"},
    )
    monkeypatch.setattr(main_module._core.system_service, "status", lambda: {})
    monkeypatch.setattr(
        main_module._core,
        "_resolve_time",
        lambda _gps, _system: ("2026-09-03T12:56:10Z", "gps"),
    )
    monkeypatch.setattr(
        main_module._core.state,
        "client_timezone_offset_minutes",
        120,
    )
    monkeypatch.setattr(
        main_module._core.indi_service,
        "mount_time_status",
        lambda reference_utc=None, reference_source=None: {
            "status": "available",
            "utc": "2026-09-01T01:40:39Z",
            "indi_state": "Ok",
            "offset_hours": 2.0,
            "synchronized": False,
            "drift_seconds": 213331.0,
        },
    )

    response = client.post(
        "/mount/goto-mount-frame",
        json={"ra": 12.0, "dec": 45.0, "tracking_mode": "sidereal"},
    )
    body = response.json()
    assert body["status"] == "error"
    assert "/mount/time/sync" in body["detail"]


def test_mount_time_sync_writes_once_and_confirms_readback(monkeypatch):
    _clear_verified_sync()
    monkeypatch.setattr(
        main_module._core.gps_service,
        "status",
        lambda: {"status": "fix", "time_utc": "2026-09-03T12:56:10Z"},
    )
    monkeypatch.setattr(main_module._core.system_service, "status", lambda: {})
    monkeypatch.setattr(
        main_module._core,
        "_resolve_time",
        lambda _gps, _system: ("2026-09-03T12:56:10Z", "gps"),
    )
    monkeypatch.setattr(
        main_module._core.state,
        "client_timezone_offset_minutes",
        120,
    )

    reads = iter(
        [
            {
                "status": "available",
                "utc": "2026-09-01T01:40:39Z",
                "indi_state": "Ok",
                "offset_hours": 2.0,
                "synchronized": False,
                "drift_seconds": 213331.0,
            },
            {
                "status": "available",
                "utc": "2026-09-03T12:56:10Z",
                "indi_state": "Ok",
                "offset_hours": 2.0,
                "synchronized": True,
                "drift_seconds": 0.5,
            },
        ]
    )
    monkeypatch.setattr(
        main_module._core.indi_service,
        "mount_time_status",
        lambda reference_utc=None, reference_source=None: next(reads),
    )

    writes = []
    monkeypatch.setattr(
        main_module._core.indi_service,
        "sync_mount_time",
        lambda utc_iso, timezone_offset_minutes: (
            writes.append((utc_iso, timezone_offset_minutes))
            or {"status": "ok", "utc": utc_iso}
        ),
    )
    monkeypatch.setattr(
        "app.indi.field_test_routes.time.sleep",
        lambda _seconds: None,
    )

    response = client.post("/mount/time/sync")
    body = response.json()

    assert body["status"] == "synced"
    assert body["verification"]["verified"] is True
    assert body["verification"]["readback_kind"] == "indi_setpoint"
    assert writes == [("2026-09-03T12:56:10Z", 120)]
    assert (
        main_module._core.state.mount_time_sync_mount_utc
        == "2026-09-03T12:56:10Z"
    )
