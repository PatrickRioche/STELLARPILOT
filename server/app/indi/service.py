import subprocess
import time
from datetime import datetime, timezone
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path



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

    def mount_location(
        self,
        name: str | None = None,
    ) -> dict:
        unavailable = {
            "status": "unavailable",
            "latitude": None,
            "longitude": None,
            "altitude": None,
            "source": None,
        }

        if name is None:
            for line in self._connection_properties().splitlines():
                line = line.strip()

                if ".CONNECTION.CONNECT=On" not in line:
                    continue

                candidate = line.split(
                    ".CONNECTION.CONNECT=",
                    1,
                )[0]

                if "onstep" in candidate.lower():
                    name = candidate
                    break

        if name is None or "onstep" not in name.lower():
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
                    f"{name}.GEOGRAPHIC_COORD.*",
                ],
                capture_output=True,
                text=True,
                timeout=4,
                check=False,
            )

        except (
            OSError,
            subprocess.SubprocessError,
        ):
            return unavailable

        values = {}
        prefix = f"{name}.GEOGRAPHIC_COORD."

        for line in result.stdout.splitlines():

            if "=" not in line:
                continue

            key, value = line.split("=", 1)

            if key.startswith(prefix):
                values[
                    key[len(prefix):]
                ] = value.strip()

        def number(key):
            try:
                return float(values[key])
            except (
                KeyError,
                ValueError,
            ):
                return None

        latitude = number("LAT")
        raw_longitude = number("LONG")
        altitude = number("ELEV")

        if (
            latitude is None
            or raw_longitude is None
        ):
            return unavailable

        if not -90.0 <= latitude <= 90.0:
            return unavailable

        # LX200 / OnStep :
        # longitude Ouest positive.
        #
        # StellarPilot :
        # longitude Est positive.
        west_positive = raw_longitude

        while west_positive > 180.0:
            west_positive -= 360.0

        while west_positive <= -180.0:
            west_positive += 360.0

        longitude = -west_positive

        return {
            "status": "available",
            "latitude": latitude,
            "longitude": longitude,
            "altitude": altitude,
            "raw_longitude": raw_longitude,
            "source": "onstep",
            "mount": name,
        }

    def status_snapshot(self) -> dict:
        """
        Lit en un seul appel INDI toutes les proprietes necessaires
        a /status.

        Cela evite l'accumulation des delais de indi_getprop.
        """

        unavailable_location = {
            "status": "unavailable",
            "latitude": None,
            "longitude": None,
            "altitude": None,
            "source": None,
        }

        try:
            result = subprocess.run(
                [
                    "indi_getprop",
                    "-h",
                    "127.0.0.1",
                    "-p",
                    "7624",
                    "-t",
                    "1",
                    "*.CONNECTION.*",
                    "*.TELESCOPE_MOUNT_TYPE.*",
                    "*.GEOGRAPHIC_COORD.*",
                    "*.CCD_INFO.*",
                    "*.CCD_FRAME.*",
                    "*.CCD_BINNING.*",
                    "*.CCD_TEMPERATURE.*",
                    "*.CCD_CONTROLS.*",
                    "*.CCD_FRAME_TYPE.*",
                    "*.CCD_EXPOSURE.*",
                ],
                capture_output=True,
                text=True,
                timeout=3,
                check=False,
            )

            output = result.stdout

        except (
            OSError,
            subprocess.SubprocessError,
        ):
            output = ""

        values = {}

        for line in output.splitlines():
            if "=" not in line:
                continue

            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()

        mount_name = None
        camera_name = None

        for key, value in values.items():
            if (
                not key.endswith(".CONNECTION.CONNECT")
                or value != "On"
            ):
                continue

            name = key[
                :-len(".CONNECTION.CONNECT")
            ]

            lower = name.lower()

            if (
                mount_name is None
                and (
                    "onstep" in lower
                    or "lx200" in lower
                    or "mount" in lower
                    or "telescope" in lower
                )
            ):
                mount_name = name

            if (
                camera_name is None
                and (
                    "playerone" in lower
                    or "ccd" in lower
                    or "camera" in lower
                )
            ):
                camera_name = name

        def number(
            device,
            property_name,
        ):
            if device is None:
                return None

            value = values.get(
                f"{device}.{property_name}"
            )

            if value is None:
                return None

            try:
                return float(value)
            except ValueError:
                return None

        def integer(
            device,
            property_name,
        ):
            value = number(
                device,
                property_name,
            )

            if value is None:
                return None

            return int(value)

        # ----------------------------------------------------
        # MONTURE
        # ----------------------------------------------------

        mount = {
            "status": "unavailable",
            "name": None,
        }

        if mount_name is not None:
            mount_type = None
            type_label = None

            if values.get(
                f"{mount_name}."
                "TELESCOPE_MOUNT_TYPE.ALTAZ"
            ) == "On":
                mount_type = "altaz"
                type_label = "Alt-Az"

            elif values.get(
                f"{mount_name}."
                "TELESCOPE_MOUNT_TYPE.EQ_FORK"
            ) == "On":
                mount_type = "eq_fork"
                type_label = "Equatorial Fork"

            elif values.get(
                f"{mount_name}."
                "TELESCOPE_MOUNT_TYPE.EQ_GEM"
            ) == "On":
                mount_type = "eq_gem"
                type_label = (
                    "German Equatorial Mount"
                )

            mount = {
                "status": "ready",
                "name": mount_name,
                "type": mount_type,
                "type_label": type_label,
                **self.mount_guidance(
                    mount_type
                ),
            }

        # ----------------------------------------------------
        # CAMERA
        # ----------------------------------------------------

        camera = {
            "status": "unavailable",
            "name": None,
        }

        if camera_name is not None:
            frame_type = None

            for element, label in (
                ("FRAME_LIGHT", "light"),
                ("FRAME_BIAS", "bias"),
                ("FRAME_DARK", "dark"),
                ("FRAME_FLAT", "flat"),
            ):
                if values.get(
                    f"{camera_name}."
                    f"CCD_FRAME_TYPE.{element}"
                ) == "On":
                    frame_type = label
                    break

            camera = {
                "status": "ready",
                "name": camera_name,
                "sensor": {
                    "width": integer(
                        camera_name,
                        "CCD_INFO.CCD_MAX_X",
                    ),
                    "height": integer(
                        camera_name,
                        "CCD_INFO.CCD_MAX_Y",
                    ),
                    "pixel_size_um": number(
                        camera_name,
                        "CCD_INFO.CCD_PIXEL_SIZE",
                    ),
                    "pixel_size_x_um": number(
                        camera_name,
                        "CCD_INFO.CCD_PIXEL_SIZE_X",
                    ),
                    "pixel_size_y_um": number(
                        camera_name,
                        "CCD_INFO.CCD_PIXEL_SIZE_Y",
                    ),
                    "bits_per_pixel": integer(
                        camera_name,
                        "CCD_INFO.CCD_BITSPERPIXEL",
                    ),
                },
                "capture": {
                    "exposure_s": number(
                        camera_name,
                        "CCD_EXPOSURE."
                        "CCD_EXPOSURE_VALUE",
                    ),
                    "gain": number(
                        camera_name,
                        "CCD_CONTROLS.Gain",
                    ),
                    "offset": number(
                        camera_name,
                        "CCD_CONTROLS.Offset",
                    ),
                    "bin_x": integer(
                        camera_name,
                        "CCD_BINNING.HOR_BIN",
                    ),
                    "bin_y": integer(
                        camera_name,
                        "CCD_BINNING.VER_BIN",
                    ),
                    "frame_width": integer(
                        camera_name,
                        "CCD_FRAME.WIDTH",
                    ),
                    "frame_height": integer(
                        camera_name,
                        "CCD_FRAME.HEIGHT",
                    ),
                    "frame_type": frame_type,
                },
                "temperature_c": number(
                    camera_name,
                    "CCD_TEMPERATURE."
                    "CCD_TEMPERATURE_VALUE",
                ),
            }

        # ----------------------------------------------------
        # LOCALISATION ONSTEP
        # ----------------------------------------------------

        location = unavailable_location

        if (
            mount_name is not None
            and "onstep" in mount_name.lower()
        ):
            latitude = number(
                mount_name,
                "GEOGRAPHIC_COORD.LAT",
            )

            raw_longitude = number(
                mount_name,
                "GEOGRAPHIC_COORD.LONG",
            )

            altitude = number(
                mount_name,
                "GEOGRAPHIC_COORD.ELEV",
            )

            if (
                latitude is not None
                and raw_longitude is not None
                and -90.0 <= latitude <= 90.0
            ):
                # LX200 / OnStep :
                # Ouest positif.
                #
                # StellarPilot :
                # Est positif.
                west_positive = raw_longitude

                while west_positive > 180.0:
                    west_positive -= 360.0

                while west_positive <= -180.0:
                    west_positive += 360.0

                longitude = -west_positive

                location = {
                    "status": "available",
                    "latitude": latitude,
                    "longitude": longitude,
                    "altitude": altitude,
                    "raw_longitude": raw_longitude,
                    "source": "onstep",
                    "mount": mount_name,
                }

        return {
            "mount": mount,
            "camera": camera,
            "location": location,
        }

    def device_status(self) -> dict:
        """
        Retourne un instantan? de l'?tat des p?riph?riques INDI.

        La d?tection de la monture et de la cam?ra est effectu?e
        ? partir d'une seule lecture des propri?t?s CONNECTION.

        Les lectures d?taill?es de la monture et de la cam?ra sont
        ensuite ex?cut?es en parall?le. Cette organisation ?vite
        d'additionner leurs d?lais d'attente lorsque le mat?riel
        r?pond lentement ou devient temporairement indisponible.
        """

        mount_name = None
        camera_name = None

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
                mount_name is None
                and (
                    "onstep" in lower
                    or "lx200" in lower
                    or "mount" in lower
                    or "telescope" in lower
                )
            ):
                mount_name = name

            if (
                camera_name is None
                and (
                    "playerone" in lower
                    or "ccd" in lower
                    or "camera" in lower
                )
            ):
                camera_name = name

        mount = {
            "status": "unavailable",
            "name": None,
        }

        camera = {
            "status": "unavailable",
            "name": None,
        }

        # Les deux interrogations d?taill?es sont ind?pendantes.
        # Elles sont donc ex?cut?es en parall?le afin que /status
        # reste r?actif m?me lorsqu'un p?riph?rique r?pond lentement.
        with ThreadPoolExecutor(
            max_workers=2,
            thread_name_prefix="stellarpilot-indi",
        ) as executor:
            mount_future = (
                executor.submit(
                    self._mount_type,
                    mount_name,
                )
                if mount_name is not None
                else None
            )

            camera_future = (
                executor.submit(
                    self._camera_info,
                    camera_name,
                )
                if camera_name is not None
                else None
            )

            if mount_name is not None:
                mount = {
                    "status": "ready",
                    "name": mount_name,
                    **mount_future.result(),
                }

            if camera_name is not None:
                camera = {
                    "status": "ready",
                    "name": camera_name,
                    **camera_future.result(),
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
        """
        R?alise une acquisition avec la cam?ra INDI.

        En mode device, le fichier FITS est enregistr? localement
        sur le Raspberry Pi dans /tmp/stellarpilot-captures.
        """

        camera_name = None

        for line in self._connection_properties().splitlines():
            line = line.strip()

            if ".CONNECTION.CONNECT=" not in line:
                continue

            if not line.endswith("=On"):
                continue

            name = line.split(".CONNECTION.", 1)[0]
            lower = name.lower()

            if (
                "playerone" in lower
                or "ccd" in lower
                or "camera" in lower
            ):
                camera_name = name
                break

        if camera_name is None:
            return {
                "status": "error",
                "mode": "device",
                "detail": "Aucune cam?ra INDI connect?e",
            }

        capture_dir = Path("/tmp/stellarpilot-captures")
        capture_dir.mkdir(
            parents=True,
            exist_ok=True,
        )

        timestamp = time.strftime("%Y%m%d_%H%M%S")
        capture_id = time.time_ns()
        prefix = f"stellarpilot_{timestamp}_{capture_id}"

        def set_property(property_name: str) -> None:
            result = subprocess.run(
                [
                    "indi_setprop",
                    "-h",
                    "127.0.0.1",
                    "-p",
                    "7624",
                    "-t",
                    "5",
                    property_name,
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

        try:
            # Le fichier doit ?tre enregistr? sur le Raspberry Pi.
            set_property(
                f"{camera_name}."
                "UPLOAD_MODE.UPLOAD_LOCAL=On"
            )

            # Image astronomique standard.
            set_property(
                f"{camera_name}."
                "CCD_FRAME_TYPE.FRAME_LIGHT=On"
            )

            # Premi?re astrom?trie en binning natif 1x1.
            set_property(
                f"{camera_name}."
                "CCD_BINNING.HOR_BIN=1"
            )

            set_property(
                f"{camera_name}."
                "CCD_BINNING.VER_BIN=1"
            )

            # R?pertoire et pr?fixe du FITS.
            set_property(
                f"{camera_name}."
                f"UPLOAD_SETTINGS.UPLOAD_DIR={capture_dir}"
            )

            set_property(
                f"{camera_name}."
                f"UPLOAD_SETTINGS.UPLOAD_PREFIX={prefix}"
            )

            # D?clenchement de l'exposition.
            set_property(
                f"{camera_name}."
                "CCD_EXPOSURE.CCD_EXPOSURE_VALUE="
                f"{exposure_s}"
            )

        except (
            OSError,
            subprocess.SubprocessError,
            RuntimeError,
        ) as exc:
            return {
                "status": "error",
                "mode": "device",
                "camera": camera_name,
                "detail": str(exc),
            }

        # L'exposition elle-m?me peut ?tre longue.
        deadline = (
            time.monotonic()
            + float(exposure_s)
            + 20.0
        )

        stable_path = None
        stable_size = None
        stable_mtime_ns = None
        stable_count = 0

        while time.monotonic() < deadline:
            candidates = sorted(
                (
                    item
                    for item in capture_dir.iterdir()
                    if item.is_file()
                    and item.name.startswith(prefix)
                    and item.suffix.lower()
                    in {".fits", ".fit", ".fts"}
                ),
                key=lambda item: item.stat().st_mtime_ns,
                reverse=True,
            )

            if candidates:
                image = candidates[0]

                try:
                    stat = image.stat()
                    size = stat.st_size
                    mtime_ns = stat.st_mtime_ns
                except OSError:
                    time.sleep(0.25)
                    continue

                if (
                    image == stable_path
                    and size == stable_size
                    and mtime_ns == stable_mtime_ns
                ):
                    stable_count += 1
                else:
                    stable_path = image
                    stable_size = size
                    stable_mtime_ns = mtime_ns
                    stable_count = 0

                if (
                    stable_count >= 2
                    and size >= 2880
                    and size % 2880 == 0
                ):
                    try:
                        with image.open("rb") as handle:
                            header = handle.read(9)
                    except OSError:
                        time.sleep(0.25)
                        continue

                    if header == b"SIMPLE  =":
                        return {
                            "status": "captured",
                            "mode": "device",
                            "camera": camera_name,
                            "exposure_s": exposure_s,
                            "image": str(image),
                            "size_bytes": size,
                            "fits_valid": True,
                            "write_stable": True,
                        }

            time.sleep(0.25)
        return {
            "status": "error",
            "mode": "device",
            "camera": camera_name,
            "exposure_s": exposure_s,
            "detail": "Timeout en attente du fichier FITS",
        }

    def _find_connected_mount(self) -> str | None:
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
                return name

        return None

    def _mount_snapshot(
        self,
        mount_name: str,
        coordinate_property: str | None = None,
    ) -> dict | None:
        properties = (
            [coordinate_property]
            if coordinate_property
            else [
                "EQUATORIAL_EOD_COORD",
                "EQUATORIAL_COORD",
            ]
        )

        queries = []

        for property_name in properties:
            queries.extend(
                [
                    f"{mount_name}.{property_name}.RA",
                    f"{mount_name}.{property_name}.DEC",
                    f"{mount_name}.{property_name}._STATE",
                ]
            )

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
                    *queries,
                ],
                capture_output=True,
                text=True,
                timeout=4,
                check=False,
            )
        except (
            OSError,
            subprocess.SubprocessError,
        ):
            return None

        values = {}

        for line in result.stdout.splitlines():
            if "=" not in line:
                continue

            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()

        for property_name in properties:
            prefix = f"{mount_name}.{property_name}."

            ra_value = values.get(prefix + "RA")
            dec_value = values.get(prefix + "DEC")

            if ra_value is None or dec_value is None:
                continue

            try:
                ra = float(ra_value)
                dec = float(dec_value)
            except ValueError:
                continue

            return {
                "ra": ra,
                "dec": dec,
                "indi_state": values.get(
                    prefix + "_STATE"
                ),
                "coordinate_property": property_name,
            }

        return None

    @staticmethod
    def _equatorial_distance_deg(
        ra_a: float,
        dec_a: float,
        ra_b: float,
        dec_b: float,
    ) -> float:
        from math import cos, hypot, radians

        delta_ra_hours = (
            (ra_a - ra_b + 12.0) % 24.0
        ) - 12.0

        mean_dec = radians(
            (dec_a + dec_b) / 2.0
        )

        delta_ra_deg = (
            delta_ra_hours
            * 15.0
            * cos(mean_dec)
        )

        delta_dec_deg = dec_a - dec_b

        return hypot(
            delta_ra_deg,
            delta_dec_deg,
        )

    def sync_mount_time(
        self,
        utc_iso: str,
        timezone_offset_minutes: int,
    ) -> dict:
        """
        Synchronise l'horloge de la monture avec une source
        temporelle fiable fournie par StellarPilot.

        TIME_UTC est envoye atomiquement a INDI :
        UTC + OFFSET.
        """

        mount_name = self._find_connected_mount()

        if mount_name is None:
            return {
                "status": "error",
                "mode": "device",
                "detail": (
                    "Aucune monture INDI connectee"
                ),
            }

        try:
            value = utc_iso.strip()

            if value.endswith("Z"):
                value = value[:-1] + "+00:00"

            instant = datetime.fromisoformat(value)

            if instant.tzinfo is None:
                instant = instant.replace(
                    tzinfo=timezone.utc
                )

            instant = instant.astimezone(
                timezone.utc
            )

            utc_value = instant.strftime(
                "%Y-%m-%dT%H:%M:%S"
            )

        except ValueError as exc:
            return {
                "status": "error",
                "mode": "device",
                "mount": mount_name,
                "detail": (
                    "Heure UTC StellarPilot invalide: "
                    f"{exc}"
                ),
            }

        offset_hours = (
            timezone_offset_minutes / 60.0
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
                    (
                        f"{mount_name}."
                        "TIME_UTC.UTC;OFFSET="
                        f"{utc_value};"
                        f"{offset_hours:.2f}"
                    ),
                ],
                capture_output=True,
                text=True,
                timeout=4,
                check=False,
            )

        except (
            OSError,
            subprocess.SubprocessError,
        ) as exc:
            return {
                "status": "error",
                "mode": "device",
                "mount": mount_name,
                "detail": str(exc),
            }

        if result.returncode != 0:
            detail = (
                result.stderr.strip()
                or result.stdout.strip()
                or "Erreur INDI inconnue"
            )

            return {
                "status": "error",
                "mode": "device",
                "mount": mount_name,
                "detail": detail,
            }

        return {
            "status": "ok",
            "mode": "device",
            "mount": mount_name,
            "utc": utc_value,
            "timezone_offset_minutes": (
                timezone_offset_minutes
            ),
            "offset_hours": offset_hours,
        }

    def goto(self, ra: float, dec: float) -> dict:
        ra = ra % 24.0

        mount_name = self._find_connected_mount()

        if mount_name is None:
            return {
                "status": "error",
                "mode": "device",
                "detail": "Aucune monture INDI connectee",
                "ra": ra,
                "dec": dec,
            }

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
                    f"{mount_name}.EQUATORIAL_EOD_COORD.*",
                    f"{mount_name}.EQUATORIAL_COORD.*",
                ],
                capture_output=True,
                text=True,
                timeout=5,
                check=False,
            )
        except (
            OSError,
            subprocess.SubprocessError,
        ) as exc:
            return {
                "status": "error",
                "mode": "device",
                "mount": mount_name,
                "detail": str(exc),
                "ra": ra,
                "dec": dec,
            }

        output = properties.stdout

        if (
            f"{mount_name}.EQUATORIAL_EOD_COORD."
            in output
        ):
            coordinate_property = (
                "EQUATORIAL_EOD_COORD"
            )
        elif (
            f"{mount_name}.EQUATORIAL_COORD."
            in output
        ):
            coordinate_property = (
                "EQUATORIAL_COORD"
            )
        else:
            return {
                "status": "error",
                "mode": "device",
                "mount": mount_name,
                "detail": (
                    "La monture n'expose pas de "
                    "coordonnees equatoriales pilotables"
                ),
                "ra": ra,
                "dec": dec,
            }

        if (
            f"{mount_name}.ON_COORD_SET.TRACK="
            in output
        ):
            goto_action = "TRACK"
        elif (
            f"{mount_name}.ON_COORD_SET.SLEW="
            in output
        ):
            goto_action = "SLEW"
        else:
            return {
                "status": "error",
                "mode": "device",
                "mount": mount_name,
                "detail": (
                    "La monture n'expose pas "
                    "ON_COORD_SET TRACK/SLEW"
                ),
                "ra": ra,
                "dec": dec,
            }

        start_snapshot = self._mount_snapshot(
            mount_name,
            coordinate_property,
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

        try:
            set_property(
                f"{mount_name}."
                f"ON_COORD_SET.{goto_action}=On"
            )

            # RA and DEC are sent atomically to INDI.
            set_property(
                f"{mount_name}."
                f"{coordinate_property}."
                "RA;DEC="
                f"{ra:.8f};{dec:.8f}"
            )
        except (
            OSError,
            subprocess.SubprocessError,
            RuntimeError,
        ) as exc:
            return {
                "status": "error",
                "mode": "device",
                "mount": mount_name,
                "detail": str(exc),
                "ra": ra,
                "dec": dec,
            }

        self._goto_mount_name = mount_name
        self._goto_coordinate_property = coordinate_property
        self._goto_target_ra = ra
        self._goto_target_dec = dec
        self._goto_start_ra = (
            start_snapshot.get("ra")
            if start_snapshot
            else None
        )
        self._goto_start_dec = (
            start_snapshot.get("dec")
            if start_snapshot
            else None
        )

        return {
            "status": "slewing",
            "mode": "device",
            "mount": mount_name,
            "action": goto_action.lower(),
            "coordinate_property": coordinate_property,
            "ra": ra,
            "dec": dec,
            "start_ra": self._goto_start_ra,
            "start_dec": self._goto_start_dec,
        }

    def mount_status(self) -> dict:
        target_ra = getattr(
            self,
            "_goto_target_ra",
            None,
        )
        target_dec = getattr(
            self,
            "_goto_target_dec",
            None,
        )

        mount_name = getattr(
            self,
            "_goto_mount_name",
            None,
        ) or self._find_connected_mount()

        if mount_name is None:
            return {
                "status": "error",
                "mode": "device",
                "detail": "Aucune monture INDI connectee",
                "virtual_position": True,
            }

        coordinate_property = getattr(
            self,
            "_goto_coordinate_property",
            None,
        )

        snapshot = self._mount_snapshot(
            mount_name,
            coordinate_property,
        )

        if snapshot is None:
            return {
                "status": "error",
                "mode": "device",
                "mount": mount_name,
                "detail": "Position equatoriale INDI indisponible",
                "virtual_position": True,
            }

        current_ra = snapshot["ra"]
        current_dec = snapshot["dec"]
        indi_state = snapshot.get("indi_state")

        start_ra = getattr(
            self,
            "_goto_start_ra",
            None,
        )
        start_dec = getattr(
            self,
            "_goto_start_dec",
            None,
        )

        progress = None
        remaining_deg = None

        if target_ra is not None and target_dec is not None:
            remaining_deg = self._equatorial_distance_deg(
                current_ra,
                current_dec,
                target_ra,
                target_dec,
            )

            if start_ra is not None and start_dec is not None:
                initial_deg = self._equatorial_distance_deg(
                    start_ra,
                    start_dec,
                    target_ra,
                    target_dec,
                )

                if initial_deg <= 1.0e-6:
                    progress = 1.0
                else:
                    progress = max(
                        0.0,
                        min(
                            1.0,
                            1.0 - (
                                remaining_deg /
                                initial_deg
                            ),
                        ),
                    )

        reached = (
            remaining_deg is not None
            and remaining_deg <= 0.05
        )

        normalized_state = (
            (indi_state or "").strip().lower()
        )

        if normalized_state == "alert":
            status = "error"
        elif target_ra is None or target_dec is None:
            status = "idle"
        elif reached:
            status = "tracking"
            progress = 1.0
        elif normalized_state == "busy":
            status = "slewing"
        else:
            status = "slewing"

        return {
            "status": status,
            "mode": "device",
            "mount": mount_name,
            "coordinate_property": snapshot[
                "coordinate_property"
            ],
            "ra": current_ra,
            "dec": current_dec,
            "target_ra": target_ra,
            "target_dec": target_dec,
            "start_ra": start_ra,
            "start_dec": start_dec,
            "progress": progress,
            "progress_percent": (
                round(progress * 100.0, 1)
                if progress is not None
                else None
            ),
            "remaining_deg": (
                round(remaining_deg, 4)
                if remaining_deg is not None
                else None
            ),
            "indi_state": indi_state,
            "virtual_position": True,
        }


indi_service = IndiService()
