import shutil
import subprocess
import time
from pathlib import Path

from app.indi._service_core import IndiService as _CoreIndiService
from app.imaging.preparation_astrometry import preparation_astrometry_archive


_TRACKING_MODE_ELEMENTS = {
    "sidereal": "TRACK_SIDEREAL",
    "solar": "TRACK_SOLAR",
    "lunar": "TRACK_LUNAR",
}


class IndiService(_CoreIndiService):
    """Facade INDI ajoutant le suivi portable et le stockage de session."""

    _mount_cache_ttl_s = 5.0

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

    def _find_connected_mount(self) -> str | None:
        """
        Evite plusieurs lectures INDI identiques pendant un meme GOTO.

        sync_mount_time(), set_tracking_mode() puis le GOTO sont executes
        a quelques secondes d'intervalle. La lecture CONNECTION est donc
        reutilisee sur une courte fenetre afin de ne pas cumuler les
        timeouts de indi_getprop avant le demarrage du mouvement.
        """
        now = time.monotonic()
        cached_name = getattr(
            self,
            "_cached_mount_name",
            None,
        )
        cached_at = getattr(
            self,
            "_cached_mount_at",
            0.0,
        )

        if (
            cached_name is not None
            and now - cached_at
            <= self._mount_cache_ttl_s
        ):
            return cached_name

        mount_name = super()._find_connected_mount()

        if mount_name is not None:
            self._cached_mount_name = mount_name
            self._cached_mount_at = now

        return mount_name

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
                "1",
                f"{mount_name}.TELESCOPE_TRACK_MODE.*",
            ],
            capture_output=True,
            text=True,
            timeout=3,
            check=False,
        )

        output = result.stdout.strip()

        # Selon la version des outils INDI, indi_getprop peut terminer
        # avec un code non nul apres son delai tout en ayant deja renvoye
        # la propriete demandee. Le contenu recu reste alors exploitable.
        if not output:
            detail = (
                result.stderr.strip()
                or "TELESCOPE_TRACK_MODE indisponible"
            )
            raise RuntimeError(detail)

        return output

    def set_tracking_mode(
        self,
        mount_name: str,
        tracking_mode: str,
    ) -> str:
        """
        Selectionne le mode via la propriete standard INDI
        TELESCOPE_TRACK_MODE, puis confirme le readback.

        indi_setprop ne possede pas d'option -s : la commande doit suivre
        directement la syntaxe standard device.property.element=value.
        """
        normalized, element = self.tracking_mode_element(
            tracking_mode
        )

        property_name = (
            f"{mount_name}."
            f"TELESCOPE_TRACK_MODE.{element}"
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
                    f"{property_name}=On",
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
            raise RuntimeError(str(exc)) from exc

        if result.returncode != 0:
            detail = (
                result.stderr.strip()
                or result.stdout.strip()
                or (
                    "La monture INDI ne supporte pas le mode "
                    f"de suivi {normalized}"
                )
            )
            raise RuntimeError(detail)

        # Le driver peut mettre quelques dizaines de millisecondes a
        # publier le nouvel etat. Deux lectures courtes suffisent tout en
        # evitant de bloquer /mount/goto pendant plusieurs secondes.
        for _ in range(2):
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

    def capture(
        self,
        exposure_s: float,
        output_dir: str | Path | None = None,
        prefix: str | None = None,
    ) -> dict:
        """Capture a FITS and persist or relocate it according to its caller.

        ``/camera/capture`` (Preparation Assistant 3) calls this method
        without ``output_dir``. Its completed FITS is therefore copied
        immediately to persistent application data below
        ``server/data/astrometry/assistant-3`` before the API returns.

        Capture sessions pass ``output_dir`` and keep their existing runtime
        workflow below ``stellarpilot-server/tmp``. ``shutil.move`` supports
        Linux /tmp being a separate tmpfs/filesystem.
        """
        result = super().capture(exposure_s)

        if result.get("status") != "captured" or not result.get("image"):
            return result

        # Historical /camera/capture = Preparation / Assistant 3.
        # Persist the scientific FITS before Android can request preview/solve.
        if output_dir is None:
            try:
                archive = preparation_astrometry_archive.archive_capture(
                    result
                )
            except Exception as exc:
                return {
                    **result,
                    "status": "error",
                    "detail": (
                        "Capture FITS recue mais archivage persistant "
                        f"Assistant 3 impossible : {exc}"
                    ),
                }

            result["persistent_astrometry"] = archive
            result["storage"] = "assistant-3-persistent"
            return result

        source = Path(result["image"])
        destination_dir = Path(output_dir)
        destination_dir.mkdir(parents=True, exist_ok=True)

        suffix = source.suffix.lower() or ".fits"
        destination_name = (
            f"{prefix}{suffix}"
            if prefix
            else source.name
        )
        destination = destination_dir / destination_name

        try:
            shutil.move(str(source), str(destination))
        except OSError as exc:
            return {
                **result,
                "status": "error",
                "detail": (
                    "Capture FITS recue mais impossible a deplacer "
                    f"dans la session: {exc}"
                ),
            }

        result["image"] = str(destination)
        result["storage"] = "session"
        return result

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
