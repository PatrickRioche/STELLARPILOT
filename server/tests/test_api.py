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


def test_mount_goto_device_preserves_validated_onstep_clock(monkeypatch):
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
        "mount_time_status",
        lambda reference_utc=None, reference_source=None: {
            "status": "available",
            "source": "indi",
            "mount": "LX200 OnStep",
            "utc": "2026-08-29T07:30:00Z",
            "offset_hours": 2.0,
            "indi_state": "Ok",
            "reference_utc": reference_utc,
            "reference_source": reference_source,
            "drift_seconds": 0.0,
            "synchronized": True,
            "synchronization": "synchronized",
        },
    )

    def forbidden_sync(*args, **kwargs):
        raise AssertionError("GOTO must not rewrite OnStep TIME_UTC")

    monkeypatch.setattr(
        main_module.indi_service,
        "sync_mount_time",
        forbidden_sync,
    )

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
            "mount_ra_hours": 5.51,
            "mount_dec_deg": 22.1,
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

    response = client.post(
        "/mount/goto",
        json={
            "ra": 5.5,
            "dec": 22.0,
            "tracking_mode": "solar",
        },
    )

    assert response.status_code == 200

    body = response.json()

    assert body["status"] == "slewing"
    assert body["mode"] == "device"
    assert body["tracking_mode"] == "solar"
    assert body["ra"] == 5.51
    assert body["dec"] == 22.1
    assert body["requested_j2000"] == {
        "ra_hours": 5.5,
        "dec_deg": 22.0,
    }
    assert body["coordinate_transform"]["target_frame"] == "JNow"
    assert body["time_source"] == "android"
    assert body["time_sync"]["status"] == "preserved"
    assert body["time_sync"]["mode"] == "read_only"
    assert body["time_check"]["indi_state"] == "Ok"
    assert body["time_check"]["offset_matches_reference"] is True


def test_mount_goto_blocks_indi_time_alert(monkeypatch):
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
            "time_utc": None,
        },
    )
    monkeypatch.setattr(
        main_module.system_service,
        "status",
        lambda: {
            "datetime": "2026-08-29T09:30:00+02:00",
        },
    )
    monkeypatch.setattr(
        main_module.indi_service,
        "mount_time_status",
        lambda reference_utc=None, reference_source=None: {
            "status": "available",
            "utc": "2026-08-29T07:30:00Z",
            "offset_hours": 2.0,
            "indi_state": "Alert",
            "reference_utc": reference_utc,
            "reference_source": reference_source,
        },
    )

    def forbidden_goto(*args, **kwargs):
        raise AssertionError("GOTO must be blocked while TIME_UTC is Alert")

    monkeypatch.setattr(
        main_module.indi_service,
        "goto",
        forbidden_goto,
    )

    response = client.post(
        "/mount/goto",
        json={
            "ra": 5.5,
            "dec": 22.0,
            "tracking_mode": "solar",
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "error"
    assert "Alert" in body["detail"]


def test_mount_goto_blocks_wrong_onstep_offset(monkeypatch):
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
            "time_utc": None,
        },
    )
    monkeypatch.setattr(
        main_module.system_service,
        "status",
        lambda: {
            "datetime": "2026-08-29T09:30:00+02:00",
        },
    )
    monkeypatch.setattr(
        main_module.indi_service,
        "mount_time_status",
        lambda reference_utc=None, reference_source=None: {
            "status": "available",
            "utc": "2026-08-29T07:30:00Z",
            "offset_hours": 1.0,
            "indi_state": "Ok",
            "reference_utc": reference_utc,
            "reference_source": reference_source,
        },
    )

    response = client.post(
        "/mount/goto",
        json={
            "ra": 5.5,
            "dec": 22.0,
            "tracking_mode": "solar",
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "error"
    assert "Offset OnStep incohérent" in body["detail"]
    assert body["time_check"]["offset_matches_reference"] is False


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
