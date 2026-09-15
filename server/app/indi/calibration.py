from __future__ import annotations

from datetime import datetime, timezone
from math import asin, atan2, cos, degrees, radians, sin, tan

import astropy.units as u
from astropy.coordinates import FK5, SkyCoord
from astropy.time import Time


J2000 = Time("J2000")
SAFE_HOUR_ANGLE_HOURS = -1.5
TARGET_DECLINATION_LIMIT_DEG = 65.0
MIN_TARGET_ALTITUDE_DEG = 55.0


def _parse_utc(value: str) -> datetime:
    normalized = value.strip()
    if normalized.endswith("Z"):
        normalized = normalized[:-1] + "+00:00"

    dt = datetime.fromisoformat(normalized)
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)


def _julian_date(dt: datetime) -> float:
    return dt.timestamp() / 86400.0 + 2440587.5


def _local_sidereal_degrees(
    dt: datetime,
    longitude_deg: float,
) -> float:
    jd = _julian_date(dt)
    t = (jd - 2451545.0) / 36525.0
    gmst = (
        280.46061837
        + 360.98564736629 * (jd - 2451545.0)
        + 0.000387933 * t * t
        - (t * t * t) / 38710000.0
    )
    return (gmst + longitude_deg) % 360.0


def _horizontal_from_hour_angle(
    *,
    latitude_deg: float,
    dec_deg: float,
    hour_angle_hours: float,
) -> tuple[float, float]:
    ha = radians(hour_angle_hours * 15.0)
    dec = radians(dec_deg)
    lat = radians(latitude_deg)

    sin_alt = (
        sin(dec) * sin(lat)
        + cos(dec) * cos(lat) * cos(ha)
    )
    sin_alt = max(-1.0, min(1.0, sin_alt))
    altitude = asin(sin_alt)

    azimuth = atan2(
        -sin(ha),
        tan(dec) * cos(lat)
        - sin(lat) * cos(ha),
    )

    return (
        degrees(altitude),
        (degrees(azimuth) + 360.0) % 360.0,
    )


def build_near_zenith_calibration_target(
    *,
    latitude_deg: float,
    longitude_deg: float,
    timestamp_utc: str,
    altitude_m: float | None = None,
) -> dict:
    """Build a high-altitude GEM calibration target away from pole/meridian.

    The target is intentionally not the mathematical zenith. It is placed at
    hour angle -1.5 h (22.5 degrees east of the meridian), with a declination
    close to the observer latitude but clamped away from the celestial poles.
    Sidereal time and horizontal geometry use deterministic local formulae so
    the Raspberry Pi remains fully offline. Astropy is used only for FK5
    precession from epoch-of-date coordinates to J2000.
    """
    if not -90.0 <= latitude_deg <= 90.0:
        raise ValueError("latitude must be between -90 and 90")
    if not -180.0 <= longitude_deg <= 180.0:
        raise ValueError("longitude must be between -180 and 180")

    dt = _parse_utc(timestamp_utc)
    obstime = Time(dt)

    target_dec_deg = max(
        -TARGET_DECLINATION_LIMIT_DEG,
        min(TARGET_DECLINATION_LIMIT_DEG, latitude_deg),
    )

    lst_deg = _local_sidereal_degrees(dt, longitude_deg)
    lst_hours = lst_deg / 15.0

    # Hour angle H = LST - RA. H = -1.5 h means east of the meridian.
    target_ra_eod_hours = (
        lst_hours - SAFE_HOUR_ANGLE_HOURS
    ) % 24.0

    altitude_deg, azimuth_deg = _horizontal_from_hour_angle(
        latitude_deg=latitude_deg,
        dec_deg=target_dec_deg,
        hour_angle_hours=SAFE_HOUR_ANGLE_HOURS,
    )

    if altitude_deg < MIN_TARGET_ALTITUDE_DEG:
        raise RuntimeError(
            "Impossible de calculer une zone zénithale sûre : "
            f"altitude cible {altitude_deg:.1f}° < "
            f"{MIN_TARGET_ALTITUDE_DEG:.1f}°"
        )

    target_eod = SkyCoord(
        ra=target_ra_eod_hours * u.hourangle,
        dec=target_dec_deg * u.deg,
        frame=FK5(equinox=obstime),
    )
    target_j2000 = target_eod.transform_to(FK5(equinox=J2000))

    return {
        "strategy": "near_zenith_east",
        "hour_angle_hours": SAFE_HOUR_ANGLE_HOURS,
        "ra_j2000_hours": float(target_j2000.ra.hour) % 24.0,
        "dec_j2000_deg": float(target_j2000.dec.deg),
        "ra_eod_hours": target_ra_eod_hours,
        "dec_eod_deg": target_dec_deg,
        "altitude_deg": altitude_deg,
        "azimuth_deg": azimuth_deg,
        "timestamp_utc": dt.isoformat().replace("+00:00", "Z"),
        "observer_altitude_m": altitude_m,
    }
