import subprocess
import tempfile
from time import perf_counter
from pathlib import Path


class PlateSolverService:
    """
    Résolution astrométrique locale avec astrometry.net.

    Aucun accès Internet n'est nécessaire : solve-field utilise
    les index installés localement dans /usr/share/astrometry.

    Depuis les essais du 5 septembre 2026, le solveur peut lire
    automatiquement la position équatoriale de la monture via INDI
    et l'utiliser comme simple hint. La solution issue de l'image
    reste toujours la référence.
    """

    def solve(
        self,
        image: str,
        ra_hint: float | None = None,
        dec_hint: float | None = None,
        radius_deg: float | None = None,
        downsample: int | None = None,
        scale_low_arcsec: float | None = None,
        scale_high_arcsec: float | None = None,
        timeout_s: int = 90,
    ) -> dict:
        image_path = Path(image)

        if not image_path.exists():
            return {
                "status": "error",
                "solver": "astrometry.net",
                "image": image,
                "detail": "Fichier FITS introuvable",
            }

        if image_path.suffix.lower() not in {
            ".fits",
            ".fit",
            ".fts",
        }:
            return {
                "status": "error",
                "solver": "astrometry.net",
                "image": image,
                "detail": "Le fichier n'est pas un FITS",
            }

        try:
            with tempfile.TemporaryDirectory(
                prefix="stellarpilot-solve-"
            ) as work_dir:
                work = Path(work_dir)

                wcs_file = work / "solution.wcs"
                solved_file = work / "solution.solved"

                command = [
                    "solve-field",
                    "--overwrite",
                    "--no-plots",
                    "--fits-image",
                    "--dir",
                    str(work),
                    "--out",
                    "solution",
                    "--wcs",
                    str(wcs_file),
                    "--solved",
                    str(solved_file),
                    "--new-fits",
                    "none",
                    str(image_path),
                ]

                if ra_hint is not None and dec_hint is not None:
                    command[-1:-1] = [
                        "--ra",
                        str(ra_hint),
                        "--dec",
                        str(dec_hint),
                    ]

                    if radius_deg is not None:
                        command[-1:-1] = [
                            "--radius",
                            str(radius_deg),
                        ]

                if downsample is not None and downsample > 1:
                    command[-1:-1] = [
                        "--downsample",
                        str(downsample),
                    ]

                if (
                    scale_low_arcsec is not None
                    and scale_high_arcsec is not None
                    and scale_low_arcsec > 0
                    and scale_high_arcsec > scale_low_arcsec
                ):
                    command[-1:-1] = [
                        "--scale-units",
                        "arcsecperpix",
                        "--scale-low",
                        str(scale_low_arcsec),
                        "--scale-high",
                        str(scale_high_arcsec),
                    ]

                result = subprocess.run(
                    command,
                    capture_output=True,
                    text=True,
                    timeout=timeout_s,
                    check=False,
                )

                if not solved_file.exists() or not wcs_file.exists():
                    output = (
                        result.stdout.strip()
                        or result.stderr.strip()
                        or "Aucune solution astrométrique"
                    )

                    return {
                        "status": "unsolved",
                        "solver": "astrometry.net",
                        "image": str(image_path),
                        "detail": output[-2000:],
                    }

                info = subprocess.run(
                    ["wcsinfo", str(wcs_file)],
                    capture_output=True,
                    text=True,
                    timeout=10,
                    check=False,
                )

                if info.returncode != 0:
                    return {
                        "status": "error",
                        "solver": "astrometry.net",
                        "image": str(image_path),
                        "detail": (
                            info.stderr.strip()
                            or "Impossible de lire le WCS"
                        ),
                    }

                values = {}

                for line in info.stdout.splitlines():
                    line = line.strip()

                    if not line:
                        continue

                    parts = line.split(None, 1)

                    if len(parts) != 2:
                        continue

                    key, value = parts
                    values[key] = value

                def number(key: str):
                    value = values.get(key)

                    if value is None:
                        return None

                    try:
                        return float(value)
                    except ValueError:
                        return None

                return {
                    "status": "solved",
                    "solver": "astrometry.net",
                    "image": str(image_path),
                    "ra": number("ra_center"),
                    "dec": number("dec_center"),
                    # Conservé dans les données techniques WCS ;
                    # l'interface Android ne l'affiche plus.
                    "orientation_deg": number("orientation"),
                    "pixel_scale_arcsec": number("pixscale"),
                    "field_width_deg": (
                        number("fieldw") / 60.0
                        if number("fieldw") is not None
                        else None
                    ),
                    "field_height_deg": (
                        number("fieldh") / 60.0
                        if number("fieldh") is not None
                        else None
                    ),
                    "parity": values.get("parity"),
                }

        except subprocess.TimeoutExpired:
            return {
                "status": "timeout",
                "solver": "astrometry.net",
                "image": str(image_path),
                "detail": (
                    "Résolution astrométrique interrompue "
                    f"après {timeout_s} secondes"
                ),
            }

        except OSError as exc:
            return {
                "status": "error",
                "solver": "astrometry.net",
                "image": str(image_path),
                "detail": str(exc),
            }

    @staticmethod
    def _read_indi_position_hint() -> dict | None:
        """
        Lit la position courante de la monture via le service INDI.

        `mount_status()` expose RA en heures et DEC en degrés.
        astrometry.net attend RA en degrés : conversion x15 ici.

        Une monture signalée Busy n'est pas utilisée comme hint :
        la position peut encore évoluer pendant la pose / résolution.
        """
        try:
            from app.indi.service import indi_service

            status = indi_service.mount_status()
        except Exception:
            return None

        if not isinstance(status, dict):
            return None

        if status.get("status") == "error":
            return None

        indi_state = str(status.get("indi_state") or "").strip().lower()

        if indi_state in {"busy", "alert"}:
            return None

        try:
            ra_hours = float(status["ra"])
            dec_deg = float(status["dec"])
        except (KeyError, TypeError, ValueError):
            return None

        if not 0.0 <= ra_hours < 24.0:
            return None

        if not -90.0 <= dec_deg <= 90.0:
            return None

        return {
            "source": "indi_mount_readback",
            "mount": status.get("mount"),
            "coordinate_property": status.get("coordinate_property"),
            "ra_hours": ra_hours,
            "ra_deg": ra_hours * 15.0,
            "dec_deg": dec_deg,
            "mount_status": status.get("status"),
            "indi_state": status.get("indi_state"),
        }

    def solve_robust(
        self,
        image: str,
        ra_hint: float | None = None,
        dec_hint: float | None = None,
        expected_scale_arcsec: float = 1.218,
    ) -> dict:
        """
        Résolution robuste issue des essais réels du 5 septembre 2026.

        Ordre :
        1. position INDI + fenêtre d'échelle étroite ;
        2. blind solve avec la même fenêtre d'échelle ;
        3. blind solve avec une fenêtre élargie.

        Le hint INDI accélère la recherche lorsqu'il est fiable, mais
        n'est jamais indispensable : le blind solve est obligatoire.
        """
        attempts = []
        position_hint = None

        if ra_hint is None or dec_hint is None:
            position_hint = self._read_indi_position_hint()

            if position_hint is not None:
                ra_hint = position_hint["ra_deg"]
                dec_hint = position_hint["dec_deg"]
        else:
            position_hint = {
                "source": "caller",
                "ra_deg": ra_hint,
                "dec_deg": dec_hint,
            }

        # Les valeurs 0,90–1,50 arcsec/pixel sont directement
        # validées par le retest de la nuit du 5 septembre pour
        # une échelle mesurée moyenne de 1,217212 arcsec/pixel.
        narrow_low = expected_scale_arcsec * 0.74
        narrow_high = expected_scale_arcsec * 1.24

        strategies = [
            {
                "name": "scale_narrow_position",
                "scale_low": narrow_low,
                "scale_high": narrow_high,
                "radius": 8.0,
                "timeout": 12,
                "use_position": True,
            },
            {
                "name": "scale_narrow_blind",
                "scale_low": narrow_low,
                "scale_high": narrow_high,
                "radius": None,
                "timeout": 60,
                "use_position": False,
            },
            {
                "name": "scale_wide_blind",
                "scale_low": 0.50,
                "scale_high": 2.50,
                "radius": None,
                "timeout": 30,
                "use_position": False,
            },
        ]

        total_start = perf_counter()

        for strategy in strategies:
            use_position = (
                strategy["use_position"]
                and ra_hint is not None
                and dec_hint is not None
            )

            # Si aucune position INDI n'est disponible, on saute la
            # première stratégie et on commence directement en blind.
            if strategy["use_position"] and not use_position:
                continue

            attempt_start = perf_counter()

            result = self.solve(
                image=image,
                ra_hint=ra_hint if use_position else None,
                dec_hint=dec_hint if use_position else None,
                radius_deg=(
                    strategy["radius"]
                    if use_position
                    else None
                ),
                scale_low_arcsec=strategy["scale_low"],
                scale_high_arcsec=strategy["scale_high"],
                timeout_s=strategy["timeout"],
            )

            attempt_duration = round(
                perf_counter() - attempt_start,
                3,
            )

            attempts.append(
                {
                    "strategy": strategy["name"],
                    "status": result.get("status"),
                    "duration_s": attempt_duration,
                    "scale_low_arcsec": round(
                        strategy["scale_low"],
                        6,
                    ),
                    "scale_high_arcsec": round(
                        strategy["scale_high"],
                        6,
                    ),
                    "position_hint_used": use_position,
                    "ra_hint_deg": (
                        ra_hint if use_position else None
                    ),
                    "dec_hint_deg": (
                        dec_hint if use_position else None
                    ),
                    "radius_deg": (
                        strategy["radius"]
                        if use_position
                        else None
                    ),
                }
            )

            if result.get("status") == "solved":
                result["strategy"] = strategy["name"]
                result["attempts"] = attempts
                result["position_hint"] = position_hint
                result["total_duration_s"] = round(
                    perf_counter() - total_start,
                    3,
                )
                return result

            if result.get("status") == "error":
                result["strategy"] = strategy["name"]
                result["attempts"] = attempts
                result["position_hint"] = position_hint
                result["total_duration_s"] = round(
                    perf_counter() - total_start,
                    3,
                )
                return result

        return {
            "status": "unsolved",
            "solver": "astrometry.net",
            "image": image,
            "strategy": "exhausted",
            "attempts": attempts,
            "position_hint": position_hint,
            "total_duration_s": round(
                perf_counter() - total_start,
                3,
            ),
            "detail": "Toutes les stratégies ont échoué",
        }


plate_solver = PlateSolverService()
