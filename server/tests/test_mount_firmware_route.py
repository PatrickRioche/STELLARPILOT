from types import SimpleNamespace

from app.indi import firmware_routes


def test_mount_firmware_reads_onstep_number(monkeypatch):
    monkeypatch.setattr(
        firmware_routes._core.indi_service,
        "_find_connected_mount",
        lambda: "LX200 OnStep",
    )

    output = "\n".join(
        [
            "LX200 OnStep.Firmware Info.Date=Jul 21 2026",
            "LX200 OnStep.Firmware Info.Time=08:35:51",
            "LX200 OnStep.Firmware Info.Number=10.28u",
            "LX200 OnStep.Firmware Info.Name=On-Step",
            # Repeated publications are valid; the latest value must win.
            "LX200 OnStep.Firmware Info.Number=10.28u",
        ]
    )

    monkeypatch.setattr(
        firmware_routes.subprocess,
        "run",
        lambda *args, **kwargs: SimpleNamespace(
            stdout=output,
            stderr="",
            returncode=0,
        ),
    )

    result = firmware_routes.read_mount_firmware()

    assert result == {
        "status": "available",
        "source": "indi",
        "mount": "LX200 OnStep",
        "number": "10.28u",
        "name": "On-Step",
        "date": "Jul 21 2026",
        "time": "08:35:51",
        "detail": None,
    }


def test_mount_firmware_is_unavailable_without_mount(monkeypatch):
    monkeypatch.setattr(
        firmware_routes._core.indi_service,
        "_find_connected_mount",
        lambda: None,
    )

    result = firmware_routes.read_mount_firmware()

    assert result["status"] == "unavailable"
    assert result["number"] is None
    assert "Aucune monture" in result["detail"]
