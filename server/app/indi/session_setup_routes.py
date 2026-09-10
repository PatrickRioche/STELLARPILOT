from __future__ import annotations

import subprocess
import time

from app import _main_core as _core
from app.indi.field_test_routes import (
    mount_time_sync,
    mount_time_verification,
)


_REQUIRED_SLEW_RATE = 6


def _read_slew_rate(
    mount_name: str,
    required_rate: int = _REQUIRED_SLEW_RATE,
) -> dict:
    """Read both INDI representations of the active OnStep slew rate."""
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
                f"{mount_name}.Max slew Rate.maxSlew",
                f"{mount_name}.TELESCOPE_SLEW_RATE.{required_rate}",
            ],
            capture_output=True,
            text=True,
            timeout=4,
            check=False,
        )
    except (OSError, subprocess.SubprocessError) as exc:
        return {
            "status": "error",
            "mount": mount_name,
            "required_rate": required_rate,
            "max_slew": None,
            "level_selected": None,
            "detail": str(exc),
        }

    values: dict[str, str] = {}
    for line in result.stdout.splitlines():
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()

    max_key = f"{mount_name}.Max slew Rate.maxSlew"
    level_key = f"{mount_name}.TELESCOPE_SLEW_RATE.{required_rate}"

    max_slew = None
    try:
        if max_key in values:
            max_slew = float(values[max_key])
    except ValueError:
        max_slew = None

    level_selected = None
    if level_key in values:
        level_selected = values[level_key].lower() == "on"

    ready = (
        max_slew is not None
        and abs(max_slew - float(required_rate)) <= 0.01
        and level_selected is True
    )

    return {
        "status": "ready" if ready else "required",
        "mount": mount_name,
        "required_rate": required_rate,
        "max_slew": max_slew,
        "level_selected": level_selected,
        "detail": None if ready else (
            "Vitesse GOTO OnStep non confirmée au niveau "
            f"{required_rate}"
        ),
    }


def _set_required_slew_rate(
    required_rate: int = _REQUIRED_SLEW_RATE,
) -> dict:
    """Set and verify the OnStep GOTO slew level without moving the mount."""
    mount_name = _core.indi_service._find_connected_mount()
    if mount_name is None:
        return {
            "status": "unavailable",
            "mount": None,
            "required_rate": required_rate,
            "max_slew": None,
            "level_selected": None,
            "detail": "Aucune monture INDI connectée",
        }

    before = _read_slew_rate(mount_name, required_rate)
    if before.get("status") == "ready":
        return {
            **before,
            "changed": False,
            "before": before,
        }

    property_value = (
        f"{mount_name}.Max slew Rate.maxSlew={required_rate}"
    )

    try:
        result = subprocess.run(
            [
                "indi_setprop",
                "-h",
                "127.0.0.1",
                "-p",
                "7624",
                "-t",
                "2",
                property_value,
            ],
            capture_output=True,
            text=True,
            timeout=4,
            check=False,
        )
    except (OSError, subprocess.SubprocessError) as exc:
        return {
            "status": "error",
            "mount": mount_name,
            "required_rate": required_rate,
            "max_slew": before.get("max_slew"),
            "level_selected": before.get("level_selected"),
            "changed": False,
            "before": before,
            "detail": str(exc),
        }

    if result.returncode != 0:
        return {
            "status": "error",
            "mount": mount_name,
            "required_rate": required_rate,
            "max_slew": before.get("max_slew"),
            "level_selected": before.get("level_selected"),
            "changed": False,
            "before": before,
            "detail": (
                result.stderr.strip()
                or result.stdout.strip()
                or "Écriture de la vitesse GOTO OnStep impossible"
            ),
        }

    readback = None
    for delay_s in (0.10, 0.20, 0.40):
        time.sleep(delay_s)
        readback = _read_slew_rate(mount_name, required_rate)
        if readback.get("status") == "ready":
            return {
                **readback,
                "changed": True,
                "before": before,
            }

    return {
        **(readback or before),
        "status": "error",
        "changed": True,
        "before": before,
        "detail": (
            "Vitesse GOTO écrite mais le readback OnStep ne confirme pas "
            f"le niveau {required_rate}"
        ),
    }


@_core.app.post("/mount/session/prepare")
def prepare_mount_session():
    """Prepare OnStep once for an observing session without moving it."""
    time_sync = mount_time_sync()
    if time_sync.get("status") != "synced":
        return {
            "status": "error",
            "detail": time_sync.get(
                "detail",
                "Synchronisation TIME_UTC OnStep impossible",
            ),
            "time_sync": time_sync,
            "slew_rate": None,
            "time_verification": None,
        }

    slew_rate = _set_required_slew_rate()
    if slew_rate.get("status") != "ready":
        return {
            "status": "error",
            "detail": slew_rate.get(
                "detail",
                "Configuration de la vitesse GOTO OnStep impossible",
            ),
            "time_sync": time_sync,
            "slew_rate": slew_rate,
            "time_verification": None,
        }

    verification = mount_time_verification()
    if verification.get("control_ready") is not True:
        return {
            "status": "error",
            "detail": (
                "TIME_UTC OnStep n'est pas vérifié après préparation"
            ),
            "time_sync": time_sync,
            "slew_rate": slew_rate,
            "time_verification": verification,
        }

    return {
        "status": "ready",
        "detail": None,
        "time_sync": time_sync,
        "slew_rate": slew_rate,
        "time_verification": verification,
        "movement_commanded": False,
    }


@_core.app.get("/mount/session/status")
def mount_session_status():
    """Read session preparation state without changing OnStep."""
    verification = mount_time_verification()
    mount_name = _core.indi_service._find_connected_mount()

    if mount_name is None:
        slew_rate = {
            "status": "unavailable",
            "mount": None,
            "required_rate": _REQUIRED_SLEW_RATE,
            "max_slew": None,
            "level_selected": None,
            "detail": "Aucune monture INDI connectée",
        }
    else:
        slew_rate = _read_slew_rate(
            mount_name,
            _REQUIRED_SLEW_RATE,
        )

    ready = (
        verification.get("control_ready") is True
        and slew_rate.get("status") == "ready"
    )

    return {
        "status": "ready" if ready else "required",
        "control_ready": ready,
        "time_verification": verification,
        "slew_rate": slew_rate,
        "movement_commanded": False,
    }
