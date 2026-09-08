from types import SimpleNamespace

from app.indi.coordinates import mount_equatorial_property
from app.indi.service import IndiService


def test_mount_time_status_uses_exact_properties(monkeypatch):
    calls = []

    def fake_run(command, **kwargs):
        calls.append(command)
        return SimpleNamespace(
            returncode=0,
            stdout=(
                "LX200 OnStep.TIME_UTC.UTC=2026-09-08T20:15:18\n"
                "LX200 OnStep.TIME_UTC.OFFSET=2.00\n"
                "LX200 OnStep.TIME_UTC._STATE=Ok\n"
                "LX200 OnStep.TIME_UTC._PERM=rw\n"
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
        reference_utc="2026-09-08T20:15:19Z",
        reference_source="gps",
    )

    assert result["status"] == "available"

    getprop = calls[0]
    assert "LX200 OnStep.TIME_UTC.UTC" in getprop
    assert "LX200 OnStep.TIME_UTC.OFFSET" in getprop
    assert "LX200 OnStep.TIME_UTC._STATE" in getprop
    assert "LX200 OnStep.TIME_UTC._PERM" in getprop
    assert not any("*" in value for value in getprop)


def test_cached_mount_is_revalidated_without_wildcard(monkeypatch):
    calls = []

    def fake_run(command, **kwargs):
        calls.append(command)
        property_name = "LX200 OnStep.CONNECTION.CONNECT"
        return SimpleNamespace(
            returncode=0,
            stdout=f"{property_name}=On\n",
            stderr="",
        )

    monkeypatch.setattr(
        "app.indi.service.subprocess.run",
        fake_run,
    )

    service = IndiService()
    service._cached_mount_name = "LX200 OnStep"
    service._cached_mount_at = 0.0

    assert service._find_connected_mount() == "LX200 OnStep"

    getprop = calls[0]
    assert "LX200 OnStep.CONNECTION.CONNECT" in getprop
    assert not any("*" in value for value in getprop)


def test_goto_readback_queries_are_exact(monkeypatch):
    service = IndiService()
    read_properties = []

    monkeypatch.setattr(
        service,
        "_find_connected_mount",
        lambda: "LX200 OnStep",
    )
    monkeypatch.setattr(
        service,
        "set_tracking_mode",
        lambda mount_name, tracking_mode: "sidereal",
    )
    monkeypatch.setattr(
        service,
        "_mount_snapshot",
        lambda mount_name, coordinate_property: {
            "ra": 1.0,
            "dec": 2.0,
        },
    )

    def fake_get_exact(*property_names, **kwargs):
        read_properties.extend(property_names)

        property_name = property_names[0]

        if property_name.endswith(
            "EQUATORIAL_EOD_COORD.RA"
        ):
            return f"{property_name}=1.0"

        if property_name.endswith(
            "ON_COORD_SET.TRACK"
        ):
            return f"{property_name}=Off"

        return ""

    monkeypatch.setattr(
        service,
        "_indi_get_exact",
        fake_get_exact,
    )

    monkeypatch.setattr(
        "app.indi.service.subprocess.run",
        lambda *args, **kwargs: SimpleNamespace(
            returncode=0,
            stdout="",
            stderr="",
        ),
    )

    result = service.goto(
        1.5,
        20.0,
        tracking_mode="sidereal",
    )

    assert result["status"] == "slewing"
    assert result["coordinate_property"] == "EQUATORIAL_EOD_COORD"
    assert result["action"] == "track"
    assert read_properties
    assert not any("*" in value for value in read_properties)


def test_coordinate_frame_detection_uses_exact_ra_property(monkeypatch):
    calls = []

    class DummyService:
        def _find_connected_mount(self):
            return "LX200 OnStep"

    def fake_run(command, **kwargs):
        calls.append(command)
        property_name = (
            "LX200 OnStep.EQUATORIAL_EOD_COORD.RA"
        )
        return SimpleNamespace(
            returncode=0,
            stdout=f"{property_name}=1.0\n",
            stderr="",
        )

    monkeypatch.setattr(
        "app.indi.coordinates.subprocess.run",
        fake_run,
    )

    mount_name, coordinate_property = mount_equatorial_property(
        DummyService()
    )

    assert mount_name == "LX200 OnStep"
    assert coordinate_property == "EQUATORIAL_EOD_COORD"

    getprop = calls[0]
    assert "LX200 OnStep.EQUATORIAL_EOD_COORD.RA" in getprop
    assert not any("*" in value for value in getprop)
