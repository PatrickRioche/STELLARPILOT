import pytest

from app.indi.calibration import build_near_zenith_calibration_target
from app.indi import coordinates


def test_near_zenith_target_is_high_and_away_from_pole():
    target = build_near_zenith_calibration_target(
        latitude_deg=47.4608,
        longitude_deg=-0.6104,
        altitude_m=70.0,
        timestamp_utc="2026-09-15T20:00:00Z",
    )

    assert target["strategy"] == "near_zenith_east"
    assert target["hour_angle_hours"] == pytest.approx(-1.5)
    assert 0.0 <= target["ra_j2000_hours"] < 24.0
    assert abs(target["dec_j2000_deg"]) < 70.0
    assert target["altitude_deg"] >= 55.0
    assert 0.0 <= target["azimuth_deg"] < 360.0


def test_near_zenith_target_is_deterministic_for_same_session():
    kwargs = dict(
        latitude_deg=47.4608,
        longitude_deg=-0.6104,
        altitude_m=70.0,
        timestamp_utc="2026-09-15T20:00:00Z",
    )

    first = build_near_zenith_calibration_target(**kwargs)
    second = build_near_zenith_calibration_target(**kwargs)

    assert second["ra_j2000_hours"] == pytest.approx(
        first["ra_j2000_hours"], abs=1e-12
    )
    assert second["dec_j2000_deg"] == pytest.approx(
        first["dec_j2000_deg"], abs=1e-12
    )


def test_sync_rejects_polar_astrometry_before_touching_indi():
    class ForbiddenIndiService:
        def _find_connected_mount(self):
            raise AssertionError("INDI must not be touched for an unsafe pole SYNC")

    with pytest.raises(RuntimeError, match="pôle céleste"):
        coordinates.sync_mount_j2000(
            ForbiddenIndiService(),
            ra_deg=37.5,
            dec_deg=89.3,
        )


def test_sync_limit_accepts_safe_declination(monkeypatch):
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
            "mount_ra_hours": ra_hours,
            "mount_dec_deg": dec_deg,
        },
    )

    class DummyService:
        def _mount_snapshot(self, mount_name, coordinate_property):
            return {
                "mount": mount_name,
                "coordinate_property": coordinate_property,
                "ra": 12.0,
                "dec": 70.0,
            }

    class Result:
        returncode = 0
        stderr = ""
        stdout = "LX200 OnStep.ON_COORD_SET.SYNC=Off\n"

    monkeypatch.setattr(
        coordinates.subprocess,
        "run",
        lambda *args, **kwargs: Result(),
    )

    result = coordinates.sync_mount_j2000(
        DummyService(),
        ra_deg=180.0,
        dec_deg=70.0,
    )

    assert result["status"] == "synced"
