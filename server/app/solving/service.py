import subprocess
import tempfile
from pathlib import Path


class PlateSolverService:
    """
    R?solution astrom?trique locale avec astrometry.net.

    Aucun acc?s Internet n'est n?cessaire :
    solve-field utilise les index install?s localement
    dans /usr/share/astrometry.
    """

    def solve(
        self,
        image: str,
        ra_hint: float | None = None,
        dec_hint: float | None = None,
        radius_deg: float | None = None,
        downsample: int | None = None,
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
                        or "Aucune solution astrom?trique"
                    )

                    # On ?vite de renvoyer plusieurs dizaines
                    # de kilo-octets de logs dans l'API.
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
                    "field_width_deg": number(
                        "fieldw"
                    ),
                    "field_height_deg": number(
                        "fieldh"
                    ),
                    "parity": values.get("parity"),
                }

        except subprocess.TimeoutExpired:
            return {
                "status": "timeout",
                "solver": "astrometry.net",
                "image": str(image_path),
                "detail": (
                    "R?solution astrom?trique interrompue "
                    f"apr?s {timeout_s} secondes"
                ),
            }

        except OSError as exc:
            return {
                "status": "error",
                "solver": "astrometry.net",
                "image": str(image_path),
                "detail": str(exc),
            }


plate_solver = PlateSolverService()
