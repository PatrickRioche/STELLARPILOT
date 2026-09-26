from __future__ import annotations

import math
from pathlib import Path

from app.imaging import sessions as sessions_module


MAX_GALLERY_TARGET_ERROR_DEG = 2.0
_ORIGINAL_FINALIZE = sessions_module.CaptureSessionService.finalize
_INSTALLED = False


def _angular_separation_deg(
    ra1_hours: float,
    dec1_deg: float,
    ra2_deg: float,
    dec2_deg: float,
) -> float:
    ra1 = math.radians(float(ra1_hours) * 15.0)
    dec1 = math.radians(float(dec1_deg))
    ra2 = math.radians(float(ra2_deg))
    dec2 = math.radians(float(dec2_deg))
    cosine = (
        math.sin(dec1) * math.sin(dec2)
        + math.cos(dec1) * math.cos(dec2) * math.cos(ra1 - ra2)
    )
    cosine = max(-1.0, min(1.0, cosine))
    return math.degrees(math.acos(cosine))


def finalize_with_target_guard(self, session_id: str) -> dict:
    metadata = self._read(session_id)
    target = metadata.get("target") or {}
    stack_path_value = metadata.get("stack_fits")

    if (
        stack_path_value
        and target.get("ra_hours") is not None
        and target.get("dec_deg") is not None
    ):
        stack_path = Path(stack_path_value)
        if stack_path.exists():
            try:
                solution = self.solver.solve_robust(
                    str(stack_path),
                    ra_hint=float(target["ra_hours"]) * 15.0,
                    dec_hint=float(target["dec_deg"]),
                )
            except Exception as exc:
                solution = {
                    "status": "error",
                    "detail": str(exc),
                }

            validation = {
                "status": solution.get("status"),
                "target_name": target.get("name"),
                "target_ra_hours": target.get("ra_hours"),
                "target_dec_deg": target.get("dec_deg"),
                "solve_ra_deg": solution.get("ra"),
                "solve_dec_deg": solution.get("dec"),
                "max_error_deg": MAX_GALLERY_TARGET_ERROR_DEG,
            }

            if (
                solution.get("status") == "solved"
                and solution.get("ra") is not None
                and solution.get("dec") is not None
            ):
                error_deg = _angular_separation_deg(
                    float(target["ra_hours"]),
                    float(target["dec_deg"]),
                    float(solution["ra"]),
                    float(solution["dec"]),
                )
                validation["error_deg"] = round(error_deg, 4)
                validation["matches_target"] = error_deg <= MAX_GALLERY_TARGET_ERROR_DEG
                metadata["target_validation"] = validation
                self._write(metadata)

                if error_deg > MAX_GALLERY_TARGET_ERROR_DEG:
                    raise ValueError(
                        "Enregistrement galerie bloqué : le champ astrométrique est "
                        f"à {error_deg:.2f}° de la cible déclarée "
                        f"{target.get('name') or 'inconnue'}. Changez de cible / "
                        "créez une nouvelle session avant d'enregistrer."
                    )
            else:
                validation["matches_target"] = None
                metadata["target_validation"] = validation
                self._write(metadata)

    return _ORIGINAL_FINALIZE(self, session_id)


def install() -> None:
    global _INSTALLED
    if _INSTALLED:
        return
    sessions_module.CaptureSessionService.finalize = finalize_with_target_guard
    _INSTALLED = True
