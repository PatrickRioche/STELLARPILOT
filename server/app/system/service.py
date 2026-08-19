import subprocess
from datetime import datetime, timezone

from app.config import get_mode


def run_command(args: list[str], timeout: float = 3.0) -> str:
    try:
        result = subprocess.run(
            args,
            capture_output=True,
            text=True,
            timeout=timeout,
            check=False,
        )
        return result.stdout.strip()
    except (OSError, subprocess.SubprocessError):
        return ""


class SystemService:
    def status(self) -> dict:
        now = datetime.now().astimezone()

        if get_mode() != "device":
            return {
                "datetime": now.isoformat(),
                "time_utc": datetime.now(timezone.utc).isoformat(),
                "time_synced": True,
                "time_source": "simulation",
            }

        synchronized = (
            run_command(
                [
                    "timedatectl",
                    "show",
                    "-p",
                    "NTPSynchronized",
                    "--value",
                ]
            ).lower()
            == "yes"
        )

        source = "unknown"
        chrony = run_command(["chronyc", "sources", "-n"])

        for line in chrony.splitlines():
            line = line.strip()

            if line.startswith("#*"):
                source = "gps"
                break

            if line.startswith("^*"):
                source = "ntp"

        return {
            "datetime": now.isoformat(),
            "time_utc": datetime.now(timezone.utc).isoformat(),
            "time_synced": synchronized,
            "time_source": source,
        }


system_service = SystemService()
