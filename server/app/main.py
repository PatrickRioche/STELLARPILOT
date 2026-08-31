from typing import Literal

from app import _main_core as _core
from app._main_core import *


app = _core.app


class TrackingGotoPayload(_core.GotoPayload):
    tracking_mode: Literal[
        "sidereal",
        "solar",
        "lunar",
    ] = "sidereal"


# Remplace uniquement la route historique /mount/goto.
# Toutes les autres routes restent definies dans _main_core.
app.router.routes[:] = [
    route
    for route in app.router.routes
    if not (
        getattr(route, "path", None) == "/mount/goto"
        and "POST" in (
            getattr(route, "methods", set())
            or set()
        )
    )
]


@app.post("/mount/goto")
def mount_goto(payload: TrackingGotoPayload):
    gps = _core.gps_service.status()
    system = _core.system_service.status()

    timestamp_utc, time_source = _core._resolve_time(
        gps,
        system,
    )

    # Un GOTO astronomique ne doit pas etre lance
    # avec une heure potentiellement fausse.
    if (
        timestamp_utc is None
        or time_source not in {"gps", "android"}
    ):
        return {
            "status": "error",
            "detail": (
                "Aucune source d'heure fiable "
                "disponible pour initialiser OnStep"
            ),
            "time_source": time_source,
        }

    offset_minutes = (
        _core.state.client_timezone_offset_minutes
    )

    # En cas de GPS disponible mais sans information
    # de fuseau provenant d'Android, utilise le fuseau
    # configure sur le Raspberry Pi.
    if offset_minutes is None:
        try:
            value = timestamp_utc

            if value.endswith("Z"):
                value = (
                    value[:-1]
                    + "+00:00"
                )

            instant = _core.datetime.fromisoformat(
                value
            )

            if instant.tzinfo is None:
                instant = instant.replace(
                    tzinfo=_core.timezone.utc
                )

            local_offset = (
                instant
                .astimezone()
                .utcoffset()
            )

            offset_minutes = int(
                (
                    local_offset.total_seconds()
                    if local_offset is not None
                    else 0
                )
                / 60
            )

        except ValueError:
            offset_minutes = 0

    time_sync = _core.indi_service.sync_mount_time(
        utc_iso=timestamp_utc,
        timezone_offset_minutes=offset_minutes,
    )

    if time_sync.get("status") not in {
        "ok",
    }:
        return {
            "status": "error",
            "detail": (
                "Synchronisation de l'heure "
                "OnStep impossible"
            ),
            "time_source": time_source,
            "time_sync": time_sync,
        }

    result = _core.indi_service.goto(
        payload.ra,
        payload.dec,
        tracking_mode=payload.tracking_mode,
    )

    result["time_source"] = time_source
    result["time_sync"] = time_sync

    return result


def __getattr__(name: str):
    return getattr(_core, name)
