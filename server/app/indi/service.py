import subprocess

from app.config import get_mode


class IndiService:
    def _connection_properties(self) -> str:
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
                    "*.CONNECTION.*",
                ],
                capture_output=True,
                text=True,
                timeout=4,
                check=False,
            )
            return result.stdout.strip()
        except (OSError, subprocess.SubprocessError):
            return ""

    def device_status(self) -> dict:
        if get_mode() != "device":
            return {
                "mount": {
                    "status": "ready",
                    "name": "OnStep Simulator",
                },
                "camera": {
                    "status": "ready",
                    "name": "Player One Uranus-C Simulator",
                },
            }

        mount = {
            "status": "unavailable",
            "name": None,
        }

        camera = {
            "status": "unavailable",
            "name": None,
        }

        for line in self._connection_properties().splitlines():
            line = line.strip()

            if ".CONNECTION.CONNECT=On" not in line:
                continue

            name = line.split(
                ".CONNECTION.CONNECT=",
                1,
            )[0]

            lower = name.lower()

            if (
                "onstep" in lower
                or "lx200" in lower
                or "mount" in lower
                or "telescope" in lower
            ):
                mount = {
                    "status": "ready",
                    "name": name,
                }

            if (
                "playerone" in lower
                or "ccd" in lower
                or "camera" in lower
            ):
                camera = {
                    "status": "ready",
                    "name": name,
                }

        return {
            "mount": mount,
            "camera": camera,
        }

    def list_devices(self) -> list[dict]:
        status = self.device_status()

        return [
            {
                "name": status["camera"]["name"],
                "type": "camera",
                "connected": status["camera"]["status"] == "ready",
            },
            {
                "name": status["mount"]["name"],
                "type": "mount",
                "connected": status["mount"]["status"] == "ready",
            },
        ]

    def capture(self, exposure_s: float) -> dict:
        if get_mode() == "device":
            return {
                "status": "not_implemented",
                "mode": "device",
                "detail": "Real camera capture is not enabled in POC v0.3",
            }

        return {
            "status": "simulated",
            "exposure_s": exposure_s,
            "image": "captures/poc_capture_0001.fits",
        }

    def goto(self, ra: float, dec: float) -> dict:
        if get_mode() == "device":
            return {
                "status": "not_implemented",
                "mode": "device",
                "detail": "Real mount goto is not enabled in POC v0.3",
                "ra": ra,
                "dec": dec,
            }

        return {
            "status": "simulated",
            "ra": ra,
            "dec": dec,
        }


indi_service = IndiService()
