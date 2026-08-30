import app.main as main_module

from fastapi.testclient import TestClient
from app.main import app


client = TestClient(app)


def test_status():
    response = client.get("/status")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_location():
    response = client.post(
        "/system/location",
        json={
            "latitude": 47.47,
            "longitude": -0.55,
            "altitude": 50,
            "timestamp": "2026-08-11T13:52:00+02:00",
        },
    )

    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_mount_type():
    response = client.post(
        "/system/mount-type",
        json={"mount_type": "EQ"},
    )

    assert response.status_code == 200
    assert response.json()["mount_type"] == "EQ"


def test_mount_goto_device(monkeypatch):
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

    monkeypatch.setattr(
        main_module.indi_service,
        "sync_mount_time",
        lambda utc_iso, timezone_offset_minutes: {
            "status": "ok",
            "mode": "device",
            "utc": utc_iso,
            "timezone_offset_minutes": (
                timezone_offset_minutes
            ),
        },
    )

    monkeypatch.setattr(
        main_module.indi_service,
        "goto",
        lambda ra, dec: {
            "status": "slewing",
            "mode": "device",
            "mount": "LX200 OnStep",
            "ra": ra,
            "dec": dec,
        },
    )

    response = client.post(
        "/mount/goto",
        json={
            "ra": 5.5,
            "dec": 22.0,
        },
    )

    assert response.status_code == 200

    body = response.json()

    assert body["status"] == "slewing"
    assert body["mode"] == "device"
    assert body["time_source"] == "android"
    assert body["time_sync"]["status"] == "ok"


def test_mount_status_device(monkeypatch):
    monkeypatch.setattr(
        main_module.indi_service,
        "mount_status",
        lambda: {
            "status": "idle",
            "mode": "device",
            "mount": "LX200 OnStep",
            "ra": None,
            "dec": None,
            "target_ra": None,
            "target_dec": None,
            "progress": None,
            "progress_percent": None,
        },
    )

    response = client.get("/mount/status")

    assert response.status_code == 200
    assert response.json()["status"] == "idle"
    assert response.json()["mode"] == "device"
