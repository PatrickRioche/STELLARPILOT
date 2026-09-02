import subprocess
import tempfile
from time import perf_counter
from pathlib import Path


class PlateSolverService:
    """
    Résolution astrométrique locale avec astrometry.net.

    Aucun accès Internet n'est nécessaire : solve-field utilise les index
    installés localement dans /usr/share/astrometry.

    Pour l'Assistant 3, StellarPilot essaie d'abord d'utiliser la position
    équatoriale réellement publiée par la monture INDI comme centre de
    recherche. La RA INDI est exprimée en heures et est convertie en degrés
    avant d'être transmise à solve-field. Un blind solve reste toujours le
    dernier recours.
    """

    @staticmethod
    def _current_mount_position_hint() -> dict | None:
        """Return a safe astrometry.net position hint from the connected mount.

        INDI telescope RA values are in hours while astrometry.net --ra expects
        degrees. The hint is advisory only: if it is unavailable or invalid,
        solve_robust continues with scale-only/blind strategies.
        """
        try:
            # Lazy import avoids coupling the module import graph to INDI.
            from app.indi.service import indi_service

            status = indi_service.mount_status()
        except Exception:
            return None

        try:
            ra_hours = float(status.get("ra"))
            dec_deg = float(status.get("dec"))
        except (TypeError, ValueError):
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
        """Run one solve-field attempt.

        ``ra_hint`` is expressed in degrees, matching astrometry.net --ra.
        ``dec_hint`` and ``radius_deg`` are also expressed in degrees.
        """
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

                if (
                    ra_hint is not None
                    and dec_hint is not None
                ):
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

                if (
                    downsample is not None
                    and downsample > 1
                ):
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

                if (
                    not solved_file.exists()
                    or not wcs_file.exists()
                ):
                    output = (
                        result.stdout.strip()
                        or result.stderr.strip()
                        or "Aucune solution astrométrique"
                    )

                    # Avoid returning very large solve-field logs through API.
                    detail = output[-2000:]

                    return {
                        "status": "unsolved",
                        "solver": "astrometry.net",
                        "image": str(image_path),
                        "detail": detail,
                    }

                info = subprocess.run(
                    [
                        "wcsinfo",
                        str(wcs_file),
                    ],
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
                    "orientation_deg": number(
                        "orientation"
                    ),
                    "pixel_scale_arcsec": number(
                        "pixscale"
                    ),
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

    def solve_robust(
        self,
        image: str,
        ra_hint: float | None = None,
        dec_hint: float | None = None,
        expected_scale_arcsec: float = 1.22,
    ) -> dict:
        """Solve with mount-assisted searches followed by a blind fallback.

        When no explicit position is supplied, the current INDI mount
        coordinates are used automatically for the two fast constrained
        attempts. The final ``scale_broad`` strategy deliberately ignores the
        position hint, so an inaccurate mount cannot prevent a blind solve.
        """
        attempts = []
        position_hint = None

        if ra_hint is None and dec_hint is None:
            position_hint = self._current_mount_position_hint()

            if position_hint is not None:
                ra_hint = position_hint["ra_deg"]
                dec_hint = position_hint["dec_deg"]
        elif ra_hint is not None and dec_hint is not None:
            position_hint = {
                "source": "caller",
                "ra_deg": ra_hint,
                "dec_deg": dec_hint,
            }

        strategies = [
            {
                "name": "scale_narrow",
                "scale_low": expected_scale_arcsec * 0.70,
                "scale_high": expected_scale_arcsec * 1.40,
                "radius": 8.0,
                "timeout": 15,
                "use_position": True,
            },
            {
                "name": "scale_wide",
                "scale_low": expected_scale_arcsec * 0.45,
                "scale_high": expected_scale_arcsec * 2.00,
                "radius": 20.0,
                "timeout": 30,
                "use_position": True,
            },
            {
                "name": "scale_broad",
                "scale_low": 0.2,
                "scale_high": 5.0,
                "radius": None,
                "timeout": 30,
                "use_position": False,
            },
        ]

        total_start = perf_counter()

        for strategy in strategies:
            attempt_start = perf_counter()

            use_position = (
                strategy["use_position"]
                and ra_hint is not None
                and dec_hint is not None
            )

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
                    "scale_low_arcsec": strategy["scale_low"],
                    "scale_high_arcsec": strategy["scale_high"],
                    "position_hint_used": use_position,
                    "ra_hint_deg": ra_hint if use_position else None,
                    "dec_hint_deg": dec_hint if use_position else None,
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
