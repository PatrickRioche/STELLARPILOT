import shutil
import subprocess
from datetime import datetime, timezone
from pathlib import Path


SERVER_ROOT = Path(__file__).resolve().parents[2]


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
    @staticmethod
    def storage_status() -> dict:
        try:
            usage = shutil.disk_usage(SERVER_ROOT)
        except OSError as exc:
            return {
                "status": "unavailable",
                "path": str(SERVER_ROOT),
                "total_bytes": None,
                "used_bytes": None,
                "available_bytes": None,
                "used_percent": None,
                "detail": str(exc),
            }

        usable_bytes = usage.used + usage.free
        used_percent = (
            round((usage.used / usable_bytes) * 100.0, 1)
            if usable_bytes > 0
            else None
        )

        return {
            "status": "ready",
            "path": str(SERVER_ROOT),
            "total_bytes": usage.total,
            "used_bytes": usage.used,
            "available_bytes": usage.free,
            "used_percent": used_percent,
            "detail": None,
        }

    def status(self) -> dict:
        now = datetime.now().astimezone()

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
            "storage": self.storage_status(),
        }


system_service = SystemService()
