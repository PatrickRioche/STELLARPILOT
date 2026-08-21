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

    def _mount_type(self, name: str) -> dict:
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
                    f"{name}.TELESCOPE_MOUNT_TYPE.*",
                ],
                capture_output=True,
                text=True,
                timeout=4,
                check=False,
            )

            output = result.stdout

        except (OSError, subprocess.SubprocessError):
            output = ""

        if ".ALTAZ=On" in output:
            return {
                "type": "altaz",
                "type_label": "Alt-Az",
            }

        if ".EQ_FORK=On" in output:
            return {
                "type": "eq_fork",
                "type_label": "Equatorial Fork",
            }

        if ".EQ_GEM=On" in output:
            return {
                "type": "eq_gem",
                "type_label": "German Equatorial Mount",
            }

        return {
            "type": None,
            "type_label": None,
        }

    def _camera_info(self, name: str) -> dict:
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
                    f"{name}.CCD_INFO.*",
                    f"{name}.CCD_FRAME.*",
                    f"{name}.CCD_BINNING.*",
                    f"{name}.CCD_TEMPERATURE.*",
                    f"{name}.CCD_CONTROLS.*",
                    f"{name}.CCD_FRAME_TYPE.*",
                    f"{name}.CCD_EXPOSURE.*",
                ],
                capture_output=True,
                text=True,
                timeout=5,
                check=False,
            )

            output = result.stdout

        except (OSError, subprocess.SubprocessError):
            output = ""

        values = {}

        for line in output.splitlines():
            if "=" not in line:
                continue

            key, value = line.split("=", 1)

            prefix = f"{name}."

            if key.startswith(prefix):
                key = key[len(prefix):]

            values[key] = value

        def number(key):
            value = values.get(key)

            if value is None:
                return None

            try:
                return float(value)
            except ValueError:
                return None

        def integer(key):
            value = number(key)

            if value is None:
                return None

            return int(value)

        frame_type = None

        for element, label in (
            ("FRAME_LIGHT", "light"),
            ("FRAME_BIAS", "bias"),
            ("FRAME_DARK", "dark"),
            ("FRAME_FLAT", "flat"),
        ):
            if values.get(
                f"CCD_FRAME_TYPE.{element}"
            ) == "On":
                frame_type = label
                break

        return {
            "sensor": {
                "width": integer(
                    "CCD_INFO.CCD_MAX_X"
                ),
                "height": integer(
                    "CCD_INFO.CCD_MAX_Y"
                ),
                "pixel_size_um": number(
                    "CCD_INFO.CCD_PIXEL_SIZE"
                ),
                "pixel_size_x_um": number(
                    "CCD_INFO.CCD_PIXEL_SIZE_X"
                ),
                "pixel_size_y_um": number(
                    "CCD_INFO.CCD_PIXEL_SIZE_Y"
                ),
                "bits_per_pixel": integer(
                    "CCD_INFO.CCD_BITSPERPIXEL"
                ),
            },
            "capture": {
                "exposure_s": number(
                    "CCD_EXPOSURE.CCD_EXPOSURE_VALUE"
                ),
                "gain": number(
                    "CCD_CONTROLS.Gain"
                ),
                "offset": number(
                    "CCD_CONTROLS.Offset"
                ),
                "bin_x": integer(
                    "CCD_BINNING.HOR_BIN"
                ),
                "bin_y": integer(
                    "CCD_BINNING.VER_BIN"
                ),
                "frame_width": integer(
                    "CCD_FRAME.WIDTH"
                ),
                "frame_height": integer(
                    "CCD_FRAME.HEIGHT"
                ),
                "frame_type": frame_type,
            },
            "temperature_c": number(
                "CCD_TEMPERATURE.CCD_TEMPERATURE_VALUE"
            ),
        }

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
                    **self._mount_type(name),
                }

            if (
                "playerone" in lower
                or "ccd" in lower
                or "camera" in lower
            ):
                camera = {
                    "status": "ready",
                    "name": name,
                    **self._camera_info(name),
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
