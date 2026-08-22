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

    @staticmethod
    def mount_guidance(mount_type: str | None) -> dict:
        """
        Convertit les types INDI / OnStep en deux familles
        StellarPilot :

        AZ -> zénith
        EQ -> pôle céleste
        """

        value = (mount_type or "").upper()

        # --------------------------------------------------------
        # ALT-AZ
        # OnStep :
        #   ALTAZM
        #   ALTAZM_UNL
        #
        # INDI :
        #   ALTAZ
        # --------------------------------------------------------

        if value in {
            "AZ",
            "ALTAZ",
            "ALTAZM",
            "ALTAZM_UNL",
        }:
            return {
                "family": "az",
                "family_label": "Alt-Az",
                "startup_target": "zenith",
            }

        # --------------------------------------------------------
        # EQUATORIAL
        # OnStep :
        #   GEM
        #   GEM_TA
        #   GEM_TAC
        #   FORK
        #   FORK_TA
        #   FORK_TAC
        #
        # INDI :
        #   EQ_GEM
        #   EQ_FORK
        # --------------------------------------------------------

        if value in {
            "EQ",
            "EQUATORIAL",
            "EQ_GEM",
            "EQ_FORK",
            "GEM",
            "GEM_TA",
            "GEM_TAC",
            "FORK",
            "FORK_TA",
            "FORK_TAC",
        }:
            return {
                "family": "eq",
                "family_label": "Equatorial",
                "startup_target": "celestial_pole",
            }

        return {
            "family": None,
            "family_label": None,
            "startup_target": None,
        }

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

        mount_type = None
        type_label = None

        if ".ALTAZ=On" in output:
            mount_type = "altaz"
            type_label = "Alt-Az"

        elif ".EQ_FORK=On" in output:
            mount_type = "eq_fork"
            type_label = "Equatorial Fork"

        elif ".EQ_GEM=On" in output:
            mount_type = "eq_gem"
            type_label = "German Equatorial Mount"

        return {
            "type": mount_type,
            "type_label": type_label,
            **self.mount_guidance(mount_type),
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
