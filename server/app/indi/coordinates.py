from __future__ import annotations

import subprocess
from datetime import datetime, timezone
from typing import Any

import astropy.units as u
from astropy.coordinates import FK5, SkyCoord
from astropy.time import Time


J2000 = Time("J2000")


def _epoch_of_date_time() -> Time:
    return Time(datetime.now(timezone.utc))


def _j2000_coord(
    ra_hours: float,
    dec_deg: float,
) -> SkyCoord:
    return SkyCoord(
        ra=ra_hours * u.hourangle,
        dec=dec_deg * u.deg,
        frame=FK5(equinox=J2000),
    )


def _eod_coord(
    ra_hours: float,
    dec_deg: float,
) -> SkyCoord:
    return SkyCoord(
        ra=ra_hours * u.hourangle,
        dec=dec_deg * u.deg,
        frame=FK5(equinox=_epoch_of_date_time()),
    )


def j2000_to_epoch_of_date(
    ra_hours: float,
    dec_deg: float,
) -> tuple[float, float]:
    transformed = _j2000_coord(
        ra_hours,
        dec_deg,
    ).transform_to(
        FK5(equinox=_epoch_of_date_time())
    )
    return (
        float(transformed.ra.hour) % 24.0,
        float(transformed.dec.deg),
    )


def epoch_of_date_to_j2000(
    ra_hours: float,
    dec_deg: float,
) -> tuple[float, float]:
    transformed = _eod_coord(
        ra_hours,
        dec_deg,
    ).transform_to(
        FK5(equinox=J2000)
    )
    return (
        float(transformed.ra.hour) % 24.0,
        float(transformed.dec.deg),
    )


def mount_equatorial_property(
    indi_service: Any,
) -> tuple[str, str]:
    """Return the connected mount and the coordinate property core GOTO uses.

    `_service_core.goto()` gives priority to EQUATORIAL_EOD_COORD, then falls
    back to EQUATORIAL_COORD. Mirror that order here so coordinates are
    transformed into exactly the frame the hardware command will receive.
    """
    mount_name = indi_service._find_connected_mount()
    if mount_name is None:
        raise RuntimeError("Aucune monture INDI connectée")

    try:
        result = subprocess.run(
            [
                "indi_getprop",
                "-h",
                "127.0.0.1",
                "-p",
                "7624",
                "-t",
                "3",
                f"{mount_name}.EQUATORIAL_EOD_COORD.*",
                f"{mount_name}.EQUATORIAL_COORD.*",
            ],
            capture_output=True,
            text=True,
            timeout=5,
            check=False,
        )
    except (OSError, subprocess.SubprocessError) as exc:
        raise RuntimeError(str(exc)) from exc

    output = result.stdout
    if f"{mount_name}.EQUATORIAL_EOD_COORD." in output:
        return mount_name, "EQUATORIAL_EOD_COORD"
    if f"{mount_name}.EQUATORIAL_COORD." in output:
        return mount_name, "EQUATORIAL_COORD"

    raise RuntimeError(
        "La monture n'expose pas de coordonnées équatoriales pilotables"
    )


def prepare_j2000_for_mount(
    indi_service: Any,
    ra_hours: float,
    dec_deg: float,
) -> dict[str, Any]:
    """Convert a catalog/J2000 target into the frame expected by INDI."""
    mount_name, coordinate_property = mount_equatorial_property(
        indi_service
    )

    source_ra = ra_hours % 24.0
    source_dec = max(-90.0, min(90.0, dec_deg))

    if coordinate_property == "EQUATORIAL_EOD_COORD":
        mount_ra, mount_dec = j2000_to_epoch_of_date(
            source_ra,
            source_dec,
        )
        target_frame = "JNow"
    else:
        mount_ra, mount_dec = source_ra, source_dec
        target_frame = "J2000"

    return {
        "mount": mount_name,
        "coordinate_property": coordinate_property,
        "source_frame": "J2000",
        "target_frame": target_frame,
        "source_ra_hours": source_ra,
        "source_dec_deg": source_dec,
        "mount_ra_hours": mount_ra,
        "mount_dec_deg": mount_dec,
    }


def mount_position_to_j2000(
    *,
    ra_hours: float,
    dec_deg: float,
    coordinate_property: str | None,
) -> tuple[float, float]:
    if coordinate_property == "EQUATORIAL_EOD_COORD":
        return epoch_of_date_to_j2000(ra_hours, dec_deg)
    return ra_hours % 24.0, dec_deg


def sync_mount_j2000(
    indi_service: Any,
    ra_deg: float,
    dec_deg: float,
) -> dict[str, Any]:
    """Synchronize the mount to an astrometric J2000 field center."""
    ra_hours = (ra_deg / 15.0) % 24.0
    prepared = prepare_j2000_for_mount(
        indi_service,
        ra_hours,
        dec_deg,
    )

    mount_name = prepared["mount"]
    coordinate_property = prepared["coordinate_property"]

    try:
        properties = subprocess.run(
            [
                "indi_getprop",
                "-h",
                "127.0.0.1",
                "-p",
                "7624",
                "-t",
                "3",
                f"{mount_name}.ON_COORD_SET.*",
            ],
            capture_output=True,
            text=True,
            timeout=5,
            check=False,
        )
    except (OSError, subprocess.SubprocessError) as exc:
        raise RuntimeError(str(exc)) from exc

    if f"{mount_name}.ON_COORD_SET.SYNC=" not in properties.stdout:
        raise RuntimeError(
            "La monture INDI n'expose pas ON_COORD_SET.SYNC"
        )

    def set_property(value: str) -> None:
        result = subprocess.run(
            [
                "indi_setprop",
                "-h",
                "127.0.0.1",
                "-p",
                "7624",
                "-t",
                "5",
                value,
            ],
            capture_output=True,
            text=True,
            timeout=7,
            check=False,
        )
        if result.returncode != 0:
            detail = (
                result.stderr.strip()
                or result.stdout.strip()
                or "Erreur INDI inconnue"
            )
            raise RuntimeError(detail)

    set_property(
        f"{mount_name}.ON_COORD_SET.SYNC=On"
    )
    set_property(
        f"{mount_name}.{coordinate_property}.RA;DEC="
        f"{prepared['mount_ra_hours']:.8f};"
        f"{prepared['mount_dec_deg']:.8f}"
    )

    snapshot = indi_service._mount_snapshot(
        mount_name,
        coordinate_property,
    )

    return {
        "status": "synced",
        **prepared,
        "solved_ra_deg": ra_deg % 360.0,
        "solved_dec_deg": dec_deg,
        "readback": snapshot,
    }


# `app.main` imports this module after `_main_core.app` exists. Importing the
# field-test routes here registers the diagnostic-only mount-frame endpoint
# without changing the public J2000 `/mount/goto` contract.
from app.indi import field_test_routes as _field_test_routes  # noqa: E402,F401
