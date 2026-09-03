from types import SimpleNamespace

import pytest

from app.indi import coordinates


class DummyIndiService:
    def _find_connected_mount(self):
        return "LX200 OnStep"

    def _mount_snapshot(self, mount_name, coordinate_property):
        return {
            "mount": mount_name,
            "coordinate_property": coordinate_property,
            "ra": 1.0,
            "dec": 2.0,
        }


def test_j2000_epoch_of_date_roundtrip():
    original_ra = 18.6156
    original_dec = 38.7837

    eod_ra, eod_dec = coordinates.j2000_to_epoch_of_date(
        original_ra,
        original_dec,
    )
    roundtrip_ra, roundtrip_dec = coordinates.epoch_of_date_to_j2000(
        eod_ra,
        eod_dec,
    )

    assert roundtrip_ra == pytest.approx(original_ra, abs=1e-5)
    assert roundtrip_dec == pytest.approx(original_dec, abs=1e-4)


def test_mount_equatorial_property_prefers_eod(monkeypatch):
    def fake_run(*args, **kwargs):
        return SimpleNamespace(
            returncode=0,
            stdout=(
                "LX200 OnStep.EQUATORIAL_EOD_COORD.RA=12\n"
                "LX200 OnStep.EQUATORIAL_EOD_COORD.DEC=45\n"
                "LX200 OnStep.EQUATORIAL_COORD.RA=12\n"
                "LX200 OnStep.EQUATORIAL_COORD.DEC=45\n"
            ),
            stderr="",
        )

    monkeypatch.setattr(coordinates.subprocess, "run", fake_run)

    mount, prop = coordinates.mount_equatorial_property(
        DummyIndiService()
    )

    assert mount == "LX200 OnStep"
    assert prop == "EQUATORIAL_EOD_COORD"


def test_prepare_j2000_for_eod_mount(monkeypatch):
    monkeypatch.setattr(
        coordinates,
        "mount_equatorial_property",
        lambda _service: ("LX200 OnStep", "EQUATORIAL_EOD_COORD"),
    )

    result = coordinates.prepare_j2000_for_mount(
        DummyIndiService(),
        18.6156,
        38.7837,
    )

    assert result["source_frame"] == "J2000"
    assert result["target_frame"] == "JNow"
    assert result["coordinate_property"] == "EQUATORIAL_EOD_COORD"
    assert result["mount_ra_hours"] != pytest.approx(18.6156, abs=1e-5)


def test_sync_selects_sync_then_sends_coordinates(monkeypatch):
    calls = []

    monkeypatch.setattr(
        coordinates,
        "prepare_j2000_for_mount",
        lambda _service, ra_hours, dec_deg: {
            "mount": "LX200 OnStep",
            "coordinate_property": "EQUATORIAL_EOD_COORD",
            "source_frame": "J2000",
            "target_frame": "JNow",
            "source_ra_hours": ra_hours,
            "source_dec_deg": dec_deg,
            "mount_ra_hours": 12.345678,
            "mount_dec_deg": 45.6789,
        },
    )

    def fake_run(command, **kwargs):
        calls.append(command)
        if "indi_getprop" in command[0]:
            return SimpleNamespace(
                returncode=0,
                stdout="LX200 OnStep.ON_COORD_SET.SYNC=Off\n",
                stderr="",
            )
        return SimpleNamespace(
            returncode=0,
            stdout="",
            stderr="",
        )

    monkeypatch.setattr(coordinates.subprocess, "run", fake_run)

    result = coordinates.sync_mount_j2000(
        DummyIndiService(),
        ra_deg=180.0,
        dec_deg=45.0,
    )

    assert result["status"] == "synced"
    set_commands = [command[-1] for command in calls if "indi_setprop" in command[0]]
    assert "LX200 OnStep.ON_COORD_SET.SYNC=On" in set_commands
    assert any(
        command.startswith(
            "LX200 OnStep.EQUATORIAL_EOD_COORD.RA;DEC="
        )
        for command in set_commands
    )
