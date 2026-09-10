import time

import app.main as main_module
from fastapi.testclient import TestClient
from app.main import app


client = TestClient(app)


def _prepare_android_reference(monkeypatch):
    response = client.post(
        "/system/time",
        json={
            "utc_epoch_ms": 1787988600000,
            "timezone_offset_minutes": 120,
        },
    )
    assert response.status_code == 200

    monkeypatch.setattr(
        main_module.gps_service,
        "status",
        lambda: {
            "status": "no_fix",
            "latitude": None,
            "longitude": None,
            "altitude": None,
            "mode": 1,
            "time_utc": None,
        },
    )

    monkeypatch.setattr(
        main_module.system_service,
        "status",
        lambda: {
            "datetime": "2026-08-29T09:30:00+02:00",
            "time_utc": "2026-08-29T07:30:00+00:00",
            "time_synced": False,
            "time_source": "unknown",
        },
    )


def _set_valid_session(monkeypatch, age_seconds=1.0):
    state = main_module._core.state

    monkeypatch.setattr(
        state,
        "mount_time_sync_reference_utc",
        "2026-08-29T07:30:00Z",
    )
    monkeypatch.setattr(
        state,
        "mount_time_sync_mount_utc",
        "2026-08-29T07:30:00Z",
    )
    monkeypatch.setattr(
        state,
        "mount_time_sync_source",
        "android",
    )
    monkeypatch.setattr(
        state,
        "mount_time_sync_offset_minutes",
        120,
    )
    monkeypatch.setattr(
        state,
        "mount_time_sync_monotonic_s",
        time.monotonic() - age_seconds,
    )


def _timeout_clock(
    reference_utc=None,
    reference_source=None,
):
    return {
        "status": "unavailable",
        "source": "indi",
        "mount": "LX200 OnStep",
        "utc": None,
        "offset_hours": None,
        "indi_state": None,
        "indi_permission": None,
        "reference_utc": reference_utc,
        "reference_source": reference_source,
        "drift_seconds": None,
        "synchronized": None,
        "synchronization": "unverified",
        "detail": (
            "Command ['indi_getprop'] "
            "timed out after 4 seconds"
        ),
    }


def _mock_goto(monkeypatch):
    monkeypatch.setattr(
        main_module,
        "prepare_j2000_for_mount",
        lambda _service, ra, dec: {
            "mount": "LX200 OnStep",
            "coordinate_property": "EQUATORIAL_EOD_COORD",
            "source_frame": "J2000",
            "target_frame": "JNow",
            "source_ra_hours": ra,
            "source_dec_deg": dec,
            "mount_ra_hours": 5.31,
            "mount_dec_deg": 46.0,
        },
    )

    monkeypatch.setattr(
        main_module.indi_service,
        "goto",
        lambda ra, dec, tracking_mode="sidereal": {
            "status": "slewing",
            "mode": "device",
            "mount": "LX200 OnStep",
            "ra": ra,
            "dec": dec,
            "tracking_mode": tracking_mode,
        },
    )


def test_goto_allows_time_timeout_with_valid_session(
    monkeypatch,
):
    _prepare_android_reference(monkeypatch)
    _set_valid_session(monkeypatch)

    monkeypatch.setattr(
        main_module.indi_service,
        "mount_time_status",
        _timeout_clock,
    )

    def forbidden_sync(*args, **kwargs):
        raise AssertionError(
            "GOTO must never rewrite TIME_UTC"
        )

    monkeypatch.setattr(
        main_module.indi_service,
        "sync_mount_time",
        forbidden_sync,
    )

    _mock_goto(monkeypatch)

    response = client.post(
        "/mount/goto",
        json={
            "ra": 5.278155,
            "dec": 45.997991,
            "tracking_mode": "sidereal",
        },
    )

    assert response.status_code == 200

    body = response.json()

    assert body["status"] == "slewing"
    assert body["time_sync"]["status"] == "preserved"
    assert body["time_sync"]["mode"] == "read_only"

    assert (
        body["time_check"]["live_readback_fallback"]
        is True
    )

    assert (
        body["time_check"]
        ["session_verification"]
        ["verified"]
        is True
    )

    assert (
        body["time_check"]
        ["session_verification"]
        ["reason"]
        == "transient_live_read_timeout"
    )


def test_goto_blocks_time_timeout_without_session(
    monkeypatch,
):
    _prepare_android_reference(monkeypatch)

    state = main_module._core.state

    monkeypatch.setattr(
        state,
        "mount_time_sync_monotonic_s",
        None,
    )
    monkeypatch.setattr(
        state,
        "mount_time_sync_mount_utc",
        None,
    )
    monkeypatch.setattr(
        state,
        "mount_time_sync_source",
        None,
    )
    monkeypatch.setattr(
        state,
        "mount_time_sync_offset_minutes",
        None,
    )

    monkeypatch.setattr(
        main_module.indi_service,
        "mount_time_status",
        _timeout_clock,
    )

    def forbidden_goto(*args, **kwargs):
        raise AssertionError(
            "GOTO must be blocked"
        )

    monkeypatch.setattr(
        main_module.indi_service,
        "goto",
        forbidden_goto,
    )

    response = client.post(
        "/mount/goto",
        json={
            "ra": 5.278155,
            "dec": 45.997991,
        },
    )

    assert response.status_code == 200

    body = response.json()

    assert body["status"] == "error"

    assert (
        body["time_check"]["live_readback_fallback"]
        is False
    )

    assert (
        body["time_check"]
        ["session_verification"]
        ["verified"]
        is False
    )


def test_goto_blocks_time_timeout_with_expired_session(
    monkeypatch,
):
    _prepare_android_reference(monkeypatch)

    _set_valid_session(
        monkeypatch,
        age_seconds=(12 * 60 * 60) + 5,
    )

    monkeypatch.setattr(
        main_module.indi_service,
        "mount_time_status",
        _timeout_clock,
    )

    def forbidden_goto(*args, **kwargs):
        raise AssertionError(
            "GOTO must be blocked"
        )

    monkeypatch.setattr(
        main_module.indi_service,
        "goto",
        forbidden_goto,
    )

    response = client.post(
        "/mount/goto",
        json={
            "ra": 5.278155,
            "dec": 45.997991,
        },
    )

    assert response.status_code == 200

    body = response.json()

    assert body["status"] == "error"

    assert (
        body["time_check"]
        ["session_verification"]
        ["verified"]
        is False
    )
