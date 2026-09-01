from types import SimpleNamespace

from fastapi.testclient import TestClient

import app.main as main_module
from app.indi.service import IndiService
from app.main import app


client = TestClient(app)


def test_mount_time_status_reads_indi_and_compares_reference(monkeypatch):
    def fake_run(*args, **kwargs):
        return SimpleNamespace(
            returncode=0,
            stdout=(
                "LX200 OnStep.TIME_UTC.UTC=2026-09-01T07:30:00\n"
                "LX200 OnStep.TIME_UTC.OFFSET=2.00\n"
            ),
            stderr="",
        )

    monkeypatch.setattr(
        "app.indi.service.subprocess.run",
        fake_run,
    )

    service = IndiService()
    result = service.mount_time_status(
        mount_name="LX200 OnStep",
        reference_utc="2026-09-01T07:30:04Z",
        reference_source="gps",
    )

    assert result["status"] == "available"
    assert result["source"] == "indi"
    assert result["utc"] == "2026-09-01T07:30:00Z"
    assert result["offset_hours"] == 2.0
    assert result["drift_seconds"] == 4.0
    assert result["synchronized"] is True
    assert result["synchronization"] == "synchronized"


def test_mount_time_status_marks_large_drift(monkeypatch):
    def fake_run(*args, **kwargs):
        return SimpleNamespace(
            returncode=0,
            stdout=(
                "LX200 OnStep.TIME_UTC.UTC=2026-09-01T07:28:00\n"
                "LX200 OnStep.TIME_UTC.OFFSET=2.00\n"
            ),
            stderr="",
        )

    monkeypatch.setattr(
        "app.indi.service.subprocess.run",
        fake_run,
    )

    service = IndiService()
    result = service.mount_time_status(
        mount_name="LX200 OnStep",
        reference_utc="2026-09-01T07:30:00Z",
        reference_source="android",
    )

    assert result["drift_seconds"] == 120.0
    assert result["synchronized"] is False
    assert result["synchronization"] == "drift"


def test_mount_time_endpoint_uses_trusted_reference(monkeypatch):
    monkeypatch.setattr(
        main_module._core.gps_service,
        "status",
        lambda: {
            "status": "fix",
            "time_utc": "2026-09-01T07:30:00Z",
        },
    )
    monkeypatch.setattr(
        main_module._core.system_service,
        "status",
        lambda: {
            "datetime": "2026-09-01T09:30:00+02:00",
        },
    )

    received = {}

    def fake_mount_time_status(
        mount_name=None,
        reference_utc=None,
        reference_source=None,
    ):
        received["reference_utc"] = reference_utc
        received["reference_source"] = reference_source
        return {
            "status": "available",
            "utc": "2026-09-01T07:30:00Z",
            "reference_utc": reference_utc,
            "reference_source": reference_source,
            "drift_seconds": 0.0,
            "synchronized": True,
            "synchronization": "synchronized",
        }

    monkeypatch.setattr(
        main_module._core.indi_service,
        "mount_time_status",
        fake_mount_time_status,
    )

    response = client.get("/mount/time")

    assert response.status_code == 200
    assert response.json()["synchronized"] is True
    assert received == {
        "reference_utc": "2026-09-01T07:30:00Z",
        "reference_source": "gps",
    }
