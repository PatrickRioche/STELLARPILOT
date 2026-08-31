import subprocess
import time

from app.indi._service_core import IndiService as _CoreIndiService


_TRACKING_MODE_ELEMENTS = {
    "sidereal": "TRACK_SIDEREAL",
    "solar": "TRACK_SOLAR",
    "lunar": "TRACK_LUNAR",
}


class IndiService(_CoreIndiService):
    """Facade INDI ajoutant le choix portable du mode de suivi."""

    @staticmethod
    def tracking_mode_element(
        tracking_mode: str | None,
    ) -> tuple[str, str]:
        normalized = (
            tracking_mode or "sidereal"
        ).strip().lower()

        element = _TRACKING_MODE_ELEMENTS.get(
            normalized
        )

        if element is None:
            raise ValueError(
                "tracking_mode must be sidereal, solar or lunar"
            )

        return normalized, element

    @staticmethod
    def _tracking_mode_output(
        mount_name: str,
    ) -> str:
        result = subprocess.run(
            [
                "indi_getprop",
                "-h",
                "127.0.0.1",
                "-p",
                "7624",
                "-t",
                "2",
                f"{mount_name}.TELESCOPE_TRACK_MODE.*",
            ],
            capture_output=True,
            text=True,
            timeout=4,
            check=False,
        )

        if result.returncode != 0:
            detail = (
                result.stderr.strip()
                or result.stdout.strip()
                or "TELESCOPE_TRACK_MODE indisponible"
            )
            raise RuntimeError(detail)

        return result.stdout

    def set_tracking_mode(
        self,
        mount_name: str,
        tracking_mode: str,
    ) -> str:
        """
        Selectionne un mode via la propriete standard INDI
        TELESCOPE_TRACK_MODE, puis confirme le readback.
        """
        normalized, element = self.tracking_mode_element(
            tracking_mode
        )

        property_name = (
            f"{mount_name}."
            f"TELESCOPE_TRACK_MODE.{element}"
        )

        available = self._tracking_mode_output(
            mount_name
        )

        if f"{property_name}=" not in available:
            raise RuntimeError(
                "La monture INDI ne supporte pas le mode "
                f"de suivi {normalized}"
            )

        result = subprocess.run(
            [
                "indi_setprop",
                "-h",
                "127.0.0.1",
                "-p",
                "7624",
                "-t",
                "5",
                "-s",
                f"{property_name}=On",
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

        for _ in range(3):
            readback = self._tracking_mode_output(
                mount_name
            )

            if f"{property_name}=On" in readback:
                self._goto_tracking_mode = normalized
                return normalized

            time.sleep(0.1)

        raise RuntimeError(
            "Le mode de suivi INDI n'a pas ete confirme: "
            f"{normalized}"
        )

    def goto(
        self,
        ra: float,
        dec: float,
        tracking_mode: str = "sidereal",
    ) -> dict:
        try:
            normalized, _ = self.tracking_mode_element(
                tracking_mode
            )
        except ValueError as exc:
            return {
                "status": "error",
                "mode": "device",
                "detail": str(exc),
                "ra": ra % 24.0,
                "dec": dec,
            }

        mount_name = self._find_connected_mount()

        if mount_name is None:
            return super().goto(
                ra,
                dec,
            )

        try:
            confirmed_mode = self.set_tracking_mode(
                mount_name,
                normalized,
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
                "ra": ra % 24.0,
                "dec": dec,
                "tracking_mode": normalized,
            }

        result = super().goto(
            ra,
            dec,
        )

        result["tracking_mode"] = confirmed_mode
        return result

    def mount_status(self) -> dict:
        result = super().mount_status()
        result["tracking_mode"] = getattr(
            self,
            "_goto_tracking_mode",
            None,
        )

        if result.get("mode") == "device":
            result["virtual_position"] = False

        return result


indi_service = IndiService()
