import shutil
import subprocess
import time
from datetime import datetime, timezone
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
    _preferred_mount_names = ("LX200 OnStep",)

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
    def _indi_get_exact(
        *property_names: str,
        indi_timeout_s: int = 1,
        process_timeout_s: int = 2,
    ) -> str:
        """Read exact INDI properties and preserve partial stdout on timeout."""
        try:
            result = subprocess.run(
                [
                    "indi_getprop",
                    "-h",
                    "127.0.0.1",
                    "-p",
                    "7624",
                    "-t",
                    str(indi_timeout_s),
                    *property_names,
                ],
                capture_output=True,
                text=True,
                timeout=process_timeout_s,
                check=False,
            )
            return (result.stdout or "").strip()

        except subprocess.TimeoutExpired as exc:
            output = exc.stdout or ""

            if isinstance(output, bytes):
                output = output.decode(
                    errors="replace"
                )

            return output.strip()

        except (
            OSError,
            subprocess.SubprocessError,
        ):
            return ""

    def _find_connected_mount(self) -> str | None:
        """
        Reutilise la monture deja identifiee et valide sa connexion avec
        une requete INDI explicite, sans wildcard.

        La decouverte globale n'est utilisee que si aucune monture connue
        n'est disponible, afin de conserver la compatibilite avec d'autres
        drivers tout en evitant les timeouts intermittents OnStep.
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

        if cached_name is not None:
            if (
                now - cached_at
                <= self._mount_cache_ttl_s
            ):
                return cached_name

            property_name = (
                f"{cached_name}.CONNECTION.CONNECT"
            )
            output = self._indi_get_exact(
                property_name
            )

            if f"{property_name}=On" in output:
                self._cached_mount_at = now
                return cached_name

        # StellarPilot cible actuellement OnStep. Tester le nom hardware
        # connu sans wildcard evite de dependre d'une decouverte globale
        # pour chaque session ou apres expiration du cache.
        for candidate in self._preferred_mount_names:
            property_name = (
                f"{candidate}.CONNECTION.CONNECT"
            )
            output = self._indi_get_exact(
                property_name
            )

            if f"{property_name}=On" in output:
                self._cached_mount_name = candidate
                self._cached_mount_at = now
                return candidate

        # Compatibilite de repli pour d'autres noms/drivers.
        mount_name = super()._find_connected_mount()

        if mount_name is not None:
            self._cached_mount_name = mount_name
            self._cached_mount_at = now

        return mount_name

    @staticmethod
    def _tracking_mode_output(
        mount_name: str,
    ) -> str:
        properties = [
            (
                f"{mount_name}.TELESCOPE_TRACK_MODE."
                f"{element}"
            )
            for element in _TRACKING_MODE_ELEMENTS.values()
        ]

        output = IndiService._indi_get_exact(
            *properties,
            indi_timeout_s=1,
            process_timeout_s=2,
        )

        if not output:
            raise RuntimeError(
                "TELESCOPE_TRACK_MODE indisponible"
            )

        return output

    def set_tracking_mode(
        self,
        mount_name: str,
        tracking_mode: str,
    ) -> str:
        """
        Selectionne le mode via la propriete standard INDI
        TELESCOPE_TRACK_MODE, puis confirme le readback.
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

    @staticmethod
    def _normalize_utc(value: str) -> str:
        """Normalize an INDI TIME_UTC.UTC value as an explicit UTC ISO time."""
        normalized = value.strip()

        if normalized.endswith("Z"):
            normalized = normalized[:-1] + "+00:00"

        instant = datetime.fromisoformat(normalized)

        if instant.tzinfo is None:
            instant = instant.replace(tzinfo=timezone.utc)

        return (
            instant.astimezone(timezone.utc)
            .isoformat(timespec="seconds")
            .replace("+00:00", "Z")
        )

    @staticmethod
    def _utc_instant(value: str) -> datetime:
        normalized = value.strip()

        if normalized.endswith("Z"):
            normalized = normalized[:-1] + "+00:00"

        instant = datetime.fromisoformat(normalized)

        if instant.tzinfo is None:
            instant = instant.replace(tzinfo=timezone.utc)

        return instant.astimezone(timezone.utc)

    def mount_time_status(
        self,
        mount_name: str | None = None,
        reference_utc: str | None = None,
        reference_source: str | None = None,
    ) -> dict:
        """Read the OnStep/LX200 clock directly from INDI."""
        unavailable = {
            "status": "unavailable",
            "source": "indi",
            "mount": mount_name,
            "utc": None,
            "offset_hours": None,
            "indi_state": None,
            "indi_permission": None,
            "reference_utc": reference_utc,
            "reference_source": reference_source,
            "drift_seconds": None,
            "synchronized": None,
            "synchronization": "unverified",
            "detail": None,
        }

        if mount_name is None:
            mount_name = self._find_connected_mount()
            unavailable["mount"] = mount_name

        if mount_name is None:
            unavailable["detail"] = "Aucune monture INDI connectee"
            return unavailable

        property_names = (
            f"{mount_name}.TIME_UTC.UTC",
            f"{mount_name}.TIME_UTC.OFFSET",
            f"{mount_name}.TIME_UTC._STATE",
            f"{mount_name}.TIME_UTC._PERM",
        )

        output = self._indi_get_exact(
            *property_names,
            indi_timeout_s=2,
            process_timeout_s=4,
        )

        values: dict[str, str] = {}
        prefix = f"{mount_name}.TIME_UTC."

        for line in output.splitlines():
            if "=" not in line:
                continue

            key, value = line.split("=", 1)
            key = key.strip()

            if key.startswith(prefix):
                values[key[len(prefix):]] = value.strip()

        utc_raw = values.get("UTC")

        if not utc_raw:
            unavailable["detail"] = (
                "Propriete INDI TIME_UTC.UTC indisponible"
            )
            return unavailable

        try:
            utc_value = self._normalize_utc(utc_raw)
        except ValueError as exc:
            return {
                **unavailable,
                "status": "invalid",
                "utc_raw": utc_raw,
                "detail": f"Heure OnStep invalide: {exc}",
            }

        offset_hours = None
        offset_raw = values.get("OFFSET")

        if offset_raw is not None:
            try:
                offset_hours = float(offset_raw)
            except ValueError:
                offset_hours = None

        indi_state = values.get("_STATE")
        indi_permission = values.get("_PERM")
        drift_seconds = None
        synchronized = None
        synchronization = "unverified"
        normalized_reference = reference_utc

        if (
            reference_utc
            and reference_source in {"gps", "android"}
        ):
            try:
                normalized_reference = self._normalize_utc(reference_utc)
                mount_instant = self._utc_instant(utc_value)
                reference_instant = self._utc_instant(normalized_reference)
                drift_seconds = abs(
                    (mount_instant - reference_instant).total_seconds()
                )
                state_is_alert = (
                    (indi_state or "").strip().lower() == "alert"
                )
                synchronized = (
                    drift_seconds <= 10.0
                    and not state_is_alert
                )
                synchronization = (
                    "synchronized"
                    if synchronized
                    else (
                        "alert"
                        if state_is_alert
                        else "drift"
                    )
                )
            except ValueError:
                synchronization = "unverified"

        return {
            "status": "available",
            "source": "indi",
            "mount": mount_name,
            "utc": utc_value,
            "utc_raw": utc_raw,
            "offset_hours": offset_hours,
            "indi_state": indi_state,
            "indi_permission": indi_permission,
            "reference_utc": normalized_reference,
            "reference_source": reference_source,
            "drift_seconds": (
                round(drift_seconds, 3)
                if drift_seconds is not None
                else None
            ),
            "synchronized": synchronized,
            "synchronization": synchronization,
            "detail": None,
        }

    def status_snapshot(self) -> dict:
        """Add an independent OnStep clock readback to the normal snapshot."""
        snapshot = super().status_snapshot()
        mount = dict(snapshot.get("mount") or {})
        mount["time"] = self.mount_time_status(
            mount.get("name")
        )
        snapshot["mount"] = mount
        return snapshot

    def capture(
        self,
        exposure_s: float,
        output_dir: str | Path | None = None,
        prefix: str | None = None,
    ) -> dict:
        """Capture a FITS and persist or relocate it according to its caller."""
        result = super().capture(exposure_s)

        if result.get("status") != "captured" or not result.get("image"):
            return result

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
        """Send a hardware GOTO using only exact INDI property reads."""
        ra = ra % 24.0

        try:
            normalized, _ = self.tracking_mode_element(
                tracking_mode
            )
        except ValueError as exc:
            return {
                "status": "error",
                "mode": "device",
                "detail": str(exc),
                "ra": ra,
                "dec": dec,
            }

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
                "ra": ra,
                "dec": dec,
                "tracking_mode": normalized,
            }

        coordinate_property = None

        for candidate in (
            "EQUATORIAL_EOD_COORD",
            "EQUATORIAL_COORD",
        ):
            property_name = (
                f"{mount_name}.{candidate}.RA"
            )
            output = self._indi_get_exact(
                property_name
            )

            if f"{property_name}=" in output:
                coordinate_property = candidate
                break

        if coordinate_property is None:
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
                "tracking_mode": confirmed_mode,
            }

        goto_action = None

        for candidate in ("TRACK", "SLEW"):
            property_name = (
                f"{mount_name}.ON_COORD_SET."
                f"{candidate}"
            )
            output = self._indi_get_exact(
                property_name
            )

            if f"{property_name}=" in output:
                goto_action = candidate
                break

        if goto_action is None:
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
                "tracking_mode": confirmed_mode,
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
                "tracking_mode": confirmed_mode,
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
        self._goto_tracking_mode = confirmed_mode

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
            "tracking_mode": confirmed_mode,
        }

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
