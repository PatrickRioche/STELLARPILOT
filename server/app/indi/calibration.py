from __future__ import annotations

from datetime import datetime, timezone

import astropy.units as u
from astropy.coordinates import AltAz, EarthLocation, FK5, SkyCoord
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
    This keeps the field high in the sky while avoiding a meridian-flip
    boundary and ill-conditioned RA synchronization near DEC +/-90 degrees.
    """
    if not -90.0 <= latitude_deg <= 90.0:
        raise ValueError("latitude must be between -90 and 90")
    if not -180.0 <= longitude_deg <= 180.0:
        raise ValueError("longitude must be between -180 and 180")

    dt = _parse_utc(timestamp_utc)
    obstime = Time(dt)
    location = EarthLocation.from_geodetic(
        lon=longitude_deg * u.deg,
        lat=latitude_deg * u.deg,
        height=(altitude_m or 0.0) * u.m,
    )

    local_sidereal = obstime.sidereal_time(
        "apparent",
        longitude=location.lon,
    )

    target_dec_deg = max(
        -TARGET_DECLINATION_LIMIT_DEG,
        min(TARGET_DECLINATION_LIMIT_DEG, latitude_deg),
    )

    # Hour angle H = LST - RA. H = -1.5 h means east of the meridian.
    target_ra_eod_hours = (
        float(local_sidereal.hour) - SAFE_HOUR_ANGLE_HOURS
    ) % 24.0

    target_eod = SkyCoord(
        ra=target_ra_eod_hours * u.hourangle,
        dec=target_dec_deg * u.deg,
        frame=FK5(equinox=obstime),
    )
    target_j2000 = target_eod.transform_to(FK5(equinox=J2000))
    target_altaz = target_eod.transform_to(
        AltAz(
            obstime=obstime,
            location=location,
        )
    )

    altitude_deg = float(target_altaz.alt.deg)
    azimuth_deg = float(target_altaz.az.deg) % 360.0

    if altitude_deg < MIN_TARGET_ALTITUDE_DEG:
        raise RuntimeError(
            "Impossible de calculer une zone zénithale sûre : "
            f"altitude cible {altitude_deg:.1f}° < "
            f"{MIN_TARGET_ALTITUDE_DEG:.1f}°"
        )

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
    }
