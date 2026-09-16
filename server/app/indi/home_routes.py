from __future__ import annotations

import subprocess
import time
from typing import Any

from pydantic import BaseModel, Field

from app import _main_core as _core
from app.indi.field_test_routes import _trusted_mount_clock


_POLE_CONFIRMATION_LIMIT_DEG = 80.0
_LOCATION_TOLERANCE_DEG = 0.02


class MountHomeSetPayload(BaseModel):
    confirmed_physical_home: bool = False
    pole_solve_dec_deg: float = Field(ge=-90.0, le=90.0)


def _indi_get(*properties: str) -> str:
    try:
        result = subprocess.run(
            [
                "indi_getprop",
                "-h",
                "127.0.0.1",
                "-p",
                "7624",
                "-t",
                "2",
                *properties,
            ],
            capture_output=True,
            text=True,
            timeout=4,
            check=False,
        )
    except subprocess.TimeoutExpired as exc:
        output = exc.stdout or ""
        if isinstance(output, bytes):
            output = output.decode(errors="replace")
        return output
    except (OSError, subprocess.SubprocessError) as exc:
        raise RuntimeError(f"Lecture INDI impossible : {exc}") from exc

    return result.stdout or ""


def _indi_set(value: str) -> dict[str, Any]:
    try:
        result = subprocess.run(
            [
                "indi_setprop",
                "-h",
                "127.0.0.1",
                "-p",
                "7624",
                "-t",
                "3",
                value,
            ],
            capture_output=True,
            text=True,
            timeout=5,
            check=False,
        )
    except (OSError, subprocess.SubprocessError) as exc:
        raise RuntimeError(f"Écriture INDI impossible : {exc}") from exc

    if result.returncode != 0:
        detail = (
            result.stderr.strip()
            or result.stdout.strip()
            or "Commande INDI refusée"
        )
        raise RuntimeError(detail)

    return {
        "status": "sent",
        "property": value.split("=", 1)[0],
        "stdout": result.stdout.strip() or None,
        "stderr": result.stderr.strip() or None,
    }


def _connected_mount() -> str:
    mount = _core.indi_service._find_connected_mount()
    if not mount:
        raise RuntimeError("Aucune monture INDI connectée")
    return mount


def _home_properties(mount: str) -> dict[str, Any]:
    output = _indi_get(
        f"{mount}.TELESCOPE_HOME.*",
        f"{mount}.TELESCOPE_HOME._STATE",
        f"{mount}.TELESCOPE_TRACK_STATE.*",
        f"{mount}.TELESCOPE_PARK.*",
    )
    return {
        "raw": output,
        "home_set_supported": f"{mount}.TELESCOPE_HOME.SET=" in output,
        "home_go_supported": f"{mount}.TELESCOPE_HOME.GO=" in output,
        "parked": f"{mount}.TELESCOPE_PARK.PARK=On" in output,
        "tracking": f"{mount}.TELESCOPE_TRACK_STATE.TRACK_ON=On" in output,
        "home_state": next(
            (
                line.split("=", 1)[1].strip()
                for line in output.splitlines()
                if line.startswith(f"{mount}.TELESCOPE_HOME._STATE=")
            ),
            None,
        ),
    }


def _sync_location_from_gps(mount: str) -> dict[str, Any]:
    gps = _core.gps_service.status()
    current = _core.indi_service.status_snapshot().get("location") or {}

    if (
        gps.get("status") != "fix"
        or gps.get("latitude") is None
        or gps.get("longitude") is None
    ):
        if (
            current.get("status") == "available"
            and current.get("latitude") is not None
            and current.get("longitude") is not None
        ):
            return {
                "status": "kept",
                "source": "onstep",
                "latitude": current.get("latitude"),
                "longitude": current.get("longitude"),
                "altitude_m": current.get("altitude"),
                "detail": "GPS sans fix : position OnStep existante conservée",
            }
        raise RuntimeError(
            "Position indisponible : un fix GPS ou une position OnStep valide est requis"
        )

    latitude = float(gps["latitude"])
    longitude = float(gps["longitude"])
    altitude = gps.get("altitude")
    if altitude is None:
        altitude = current.get("altitude")
    if altitude is None:
        altitude = 0.0
    altitude = float(altitude)

    # LX200/OnStep publishes longitude west-positive in GEOGRAPHIC_COORD,
    # while StellarPilot/GPS uses east-positive. Keep the conversion in one
    # explicit place and write the three values atomically.
    onstep_longitude = (-longitude) % 360.0
    write = _indi_set(
        f"{mount}.GEOGRAPHIC_COORD.LAT;LONG;ELEV="
        f"{latitude:.8f};{onstep_longitude:.8f};{altitude:.3f}"
    )

    time.sleep(0.2)
    readback = _core.indi_service.status_snapshot().get("location") or {}
    read_lat = readback.get("latitude")
    read_lon = readback.get("longitude")
    matches = (
        readback.get("status") == "available"
        and read_lat is not None
        and read_lon is not None
        and abs(float(read_lat) - latitude) <= _LOCATION_TOLERANCE_DEG
        and abs(float(read_lon) - longitude) <= _LOCATION_TOLERANCE_DEG
    )
    if not matches:
        raise RuntimeError(
            "Position GPS écrite mais readback OnStep incohérent : "
            f"GPS {latitude:+.5f}/{longitude:+.5f}, "
            f"OnStep {read_lat}/{read_lon}"
        )

    return {
        "status": "synced",
        "source": "gps",
        "latitude": latitude,
        "longitude": longitude,
        "altitude_m": altitude,
        "onstep_raw_longitude": onstep_longitude,
        "write": write,
        "readback": readback,
        "tolerance_deg": _LOCATION_TOLERANCE_DEG,
    }


def _validate_pole_geometry(
    solved_dec_deg: float,
    latitude: float | None,
) -> dict[str, Any]:
    if abs(solved_dec_deg) < _POLE_CONFIRMATION_LIMIT_DEG:
        raise RuntimeError(
            "Reset HOME refusé : la première astrométrie n'est pas assez "
            f"proche d'un pôle (DEC={solved_dec_deg:+.3f}°)."
        )

    expected_sign = None
    if latitude is not None:
        expected_sign = 1.0 if float(latitude) >= 0.0 else -1.0
        if solved_dec_deg * expected_sign < _POLE_CONFIRMATION_LIMIT_DEG:
            hemisphere = "Nord" if expected_sign > 0 else "Sud"
            raise RuntimeError(
                "Reset HOME refusé : l'astrométrie ne correspond pas au pôle "
                f"céleste {hemisphere} attendu (DEC={solved_dec_deg:+.3f}°)."
            )

    return {
        "status": "verified",
        "solved_dec_deg": solved_dec_deg,
        "minimum_abs_dec_deg": _POLE_CONFIRMATION_LIMIT_DEG,
        "expected_hemisphere": (
            "north" if expected_sign == 1.0
            else "south" if expected_sign == -1.0
            else None
        ),
    }


@_core.app.get("/mount/home/status")
def mount_home_status():
    try:
        mount = _connected_mount()
        properties = _home_properties(mount)
        location = _core.indi_service.status_snapshot().get("location") or {}
        return {
            "status": "available" if properties["home_set_supported"] else "unsupported",
            "mount": mount,
            "home": properties,
            "location": location,
            "note": (
                "SET utilise la propriété standard INDI TELESCOPE_HOME.SET ; "
                "aucun accès série direct n'est effectué."
            ),
        }
    except RuntimeError as exc:
        return {
            "status": "error",
            "detail": str(exc),
        }


@_core.app.post("/mount/home/set")
def mount_home_set(payload: MountHomeSetPayload):
    """Declare the observer-confirmed mechanical CWD position as OnStep HOME.

    Safety gates deliberately prevent using this route as a generic astrometric
    SYNC. The telescope must physically be in HOME/CWD and the immediately
    preceding plate solve must confirm a field close to the expected pole.
    """
    if not payload.confirmed_physical_home:
        return {
            "status": "error",
            "detail": (
                "Confirmation physique obligatoire : placez la monture en "
                "HOME/CWD (contrepoids vers le bas, tube parallèle à l'axe "
                "polaire) puis confirmez."
            ),
        }

    try:
        mount = _connected_mount()
        before = _home_properties(mount)
        if not before["home_set_supported"]:
            raise RuntimeError(
                "Le driver INDI OnStep n'expose pas TELESCOPE_HOME.SET"
            )
        if before["parked"]:
            raise RuntimeError(
                "Reset HOME refusé : la monture est PARKED. Déparquez-la explicitement d'abord."
            )

        clock_check, time_source, clock_error = _trusted_mount_clock()
        if clock_check is None:
            raise RuntimeError(
                str(time_source or "Synchronisation horaire OnStep requise")
            )

        location = _sync_location_from_gps(mount)
        pole = _validate_pole_geometry(
            payload.pole_solve_dec_deg,
            location.get("latitude"),
        )

        tracking_write = None
        if before["tracking"]:
            tracking_write = _indi_set(
                f"{mount}.TELESCOPE_TRACK_STATE.TRACK_OFF=On"
            )
            time.sleep(0.15)

        home_write = _indi_set(
            f"{mount}.TELESCOPE_HOME.SET=On"
        )
        time.sleep(0.25)

        after = _home_properties(mount)
        mount_readback = _core.indi_service.mount_status()

        return {
            "status": "home_set",
            "mount": mount,
            "confirmed_physical_home": True,
            "pole_verification": pole,
            "time_source": time_source,
            "time_check": clock_check,
            "location": location,
            "tracking_stopped": before["tracking"],
            "tracking_write": tracking_write,
            "home_write": home_write,
            "home_before": before,
            "home_after": after,
            "mount_readback": mount_readback,
            "note": (
                "HOME mécanique réinitialisé via INDI TELESCOPE_HOME.SET "
                "(:hF# dans le driver LX200 OnStep). Aucun SYNC astrométrique "
                "n'a été envoyé près du pôle."
            ),
        }
    except RuntimeError as exc:
        return {
            "status": "error",
            "detail": str(exc),
        }
