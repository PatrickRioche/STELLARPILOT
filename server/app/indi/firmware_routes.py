from __future__ import annotations

import subprocess

from app import _main_core as _core


def read_mount_firmware() -> dict:
    """Read the firmware information published by the connected OnStep driver."""
    mount_name = _core.indi_service._find_connected_mount()

    unavailable = {
        "status": "unavailable",
        "source": "indi",
        "mount": mount_name,
        "number": None,
        "name": None,
        "date": None,
        "time": None,
        "detail": None,
    }

    if mount_name is None:
        unavailable["detail"] = "Aucune monture INDI connectee"
        return unavailable

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
                f"{mount_name}.Firmware Info.*",
            ],
            capture_output=True,
            text=True,
            timeout=4,
            check=False,
        )
    except (OSError, subprocess.SubprocessError) as exc:
        unavailable["detail"] = str(exc)
        return unavailable

    values: dict[str, str] = {}
    prefix = f"{mount_name}.Firmware Info."

    # Some INDI versions can publish the same property more than once during
    # the timeout window. Keeping the last value makes the endpoint stable.
    for line in result.stdout.splitlines():
        if "=" not in line:
            continue

        key, value = line.split("=", 1)
        key = key.strip()

        if key.startswith(prefix):
            values[key[len(prefix):]] = value.strip()

    number = values.get("Number")

    if not number:
        unavailable["detail"] = (
            result.stderr.strip()
            or "Propriete INDI Firmware Info.Number indisponible"
        )
        return unavailable

    return {
        "status": "available",
        "source": "indi",
        "mount": mount_name,
        "number": number,
        "name": values.get("Name"),
        "date": values.get("Date"),
        "time": values.get("Time"),
        "detail": None,
    }


@_core.app.get("/mount/firmware")
def mount_firmware():
    """Expose the real OnStep firmware version published by INDI."""
    return read_mount_firmware()
