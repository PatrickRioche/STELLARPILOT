from __future__ import annotations

import math
from typing import Any

from app.imaging.sessions import CaptureSessionService
from app.indi.coordinates import mount_position_to_j2000


MAX_AUTOMATIC_CORRECTION_DEG = 5.0
POLAR_AUTOMATIC_CORRECTION_LIMIT_DEG = 80.0


def _wrap_degrees(value: float) -> float:
    return (value + 180.0) % 360.0 - 180.0


def _angular_error_arcsec(
    ra_a_deg: float,
    dec_a_deg: float,
    ra_b_deg: float,
    dec_b_deg: float,
) -> float:
    dec_a = math.radians(dec_a_deg)
    dec_b = math.radians(dec_b_deg)
    delta_ra = math.radians(_wrap_degrees(ra_a_deg - ra_b_deg))
    cosine = (
        math.sin(dec_a) * math.sin(dec_b)
        + math.cos(dec_a) * math.cos(dec_b) * math.cos(delta_ra)
    )
    cosine = min(1.0, max(-1.0, cosine))
    return math.degrees(math.acos(cosine)) * 3600.0


def safe_centering_result(
    self: CaptureSessionService,
    target_ra_hours: float,
    target_dec_deg: float,
    solve_ra_deg: float,
    solve_dec_deg: float,
    tolerance_arcsec: float,
) -> dict[str, Any]:
    """Build one bounded absolute correction from the live mount readback.

    Field tests on 2026-09-21 showed that using
    ``target + (target - solve)`` is only valid for the first correction while
    the commanded position still equals the catalogue target. Repeating that
    formula from an already corrected position can diverge catastrophically.

    On real hardware the safe correction is computed from the current mount
    readback, converted back to J2000, plus the measured plate-solve error.
    Corrections larger than five degrees or near the celestial poles are never
    sent automatically.

    Test/simulation INDI doubles predating mount_status() keep the historical
    small-correction contract. The same five-degree safety bound still applies;
    real device services always use the live readback path.
    """
    target_ra_deg = (target_ra_hours * 15.0) % 360.0
    error_arcsec = _angular_error_arcsec(
        target_ra_deg,
        target_dec_deg,
        solve_ra_deg,
        solve_dec_deg,
    )

    status = (
        "centered"
        if error_arcsec <= tolerance_arcsec
        else "correction_required"
    )
    base = {
        "status": status,
        "error_arcsec": round(error_arcsec, 3),
        "solve_ra_deg": solve_ra_deg,
        "solve_dec_deg": solve_dec_deg,
        # Preserve the historical response contract for an already centered
        # target. Clients never issue a correction GOTO when status=centered,
        # but older tests/UI may still inspect these coordinates.
        "correction_ra_hours": (
            target_ra_hours % 24.0 if status == "centered" else None
        ),
        "correction_dec_deg": (
            target_dec_deg if status == "centered" else None
        ),
        "automatic_correction_blocked": False,
        "automatic_correction_reason": None,
        "target_ra_deg": target_ra_deg,
        "target_dec_deg": target_dec_deg,
    }

    if status == "centered":
        return base

    correction_distance_deg = error_arcsec / 3600.0
    if correction_distance_deg > MAX_AUTOMATIC_CORRECTION_DEG:
        return {
            **base,
            "automatic_correction_blocked": True,
            "automatic_correction_reason": (
                "correction_too_large: "
                f"{correction_distance_deg:.3f} deg > "
                f"{MAX_AUTOMATIC_CORRECTION_DEG:.1f} deg"
            ),
        }

    if (
        abs(target_dec_deg) >= POLAR_AUTOMATIC_CORRECTION_LIMIT_DEG
        or abs(solve_dec_deg) >= POLAR_AUTOMATIC_CORRECTION_LIMIT_DEG
    ):
        return {
            **base,
            "automatic_correction_blocked": True,
            "automatic_correction_reason": "near_celestial_pole",
        }

    delta_ra_deg = _wrap_degrees(target_ra_deg - solve_ra_deg)
    delta_dec_deg = target_dec_deg - solve_dec_deg

    mount_status = getattr(self.indi, "mount_status", None)
    if not callable(mount_status):
        # Compatibility only for unit-test/simulation doubles. This path is
        # deliberately bounded above, so it cannot recreate the field runaway.
        correction_ra_deg = (target_ra_deg + delta_ra_deg) % 360.0
        correction_dec_deg = max(
            -90.0,
            min(90.0, target_dec_deg + delta_dec_deg),
        )
        return {
            **base,
            "correction_ra_hours": correction_ra_deg / 15.0,
            "correction_dec_deg": correction_dec_deg,
            "correction_basis": "bounded_legacy_no_mount_readback",
            "delta_ra_deg": delta_ra_deg,
            "delta_dec_deg": delta_dec_deg,
        }

    try:
        mount = mount_status()
    except Exception as exc:
        return {
            **base,
            "automatic_correction_blocked": True,
            "automatic_correction_reason": f"mount_readback_error: {exc}",
        }

    coordinate_property = mount.get("coordinate_property")
    current_ra = mount.get("ra")
    current_dec = mount.get("dec")

    if (
        mount.get("status") == "error"
        or current_ra is None
        or current_dec is None
        or coordinate_property not in {
            "EQUATORIAL_EOD_COORD",
            "EQUATORIAL_COORD",
        }
    ):
        return {
            **base,
            "automatic_correction_blocked": True,
            "automatic_correction_reason": (
                mount.get("detail")
                or "mount_readback_unavailable"
            ),
        }

    try:
        current_ra_j2000_h, current_dec_j2000 = mount_position_to_j2000(
            ra_hours=float(current_ra),
            dec_deg=float(current_dec),
            coordinate_property=str(coordinate_property),
        )
    except Exception as exc:
        return {
            **base,
            "automatic_correction_blocked": True,
            "automatic_correction_reason": f"mount_frame_conversion_error: {exc}",
        }

    if abs(current_dec_j2000) >= POLAR_AUTOMATIC_CORRECTION_LIMIT_DEG:
        return {
            **base,
            "automatic_correction_blocked": True,
            "automatic_correction_reason": "mount_near_celestial_pole",
        }

    correction_ra_deg = (
        current_ra_j2000_h * 15.0 + delta_ra_deg
    ) % 360.0
    correction_dec_deg = max(
        -90.0,
        min(90.0, current_dec_j2000 + delta_dec_deg),
    )

    move_arcsec = _angular_error_arcsec(
        current_ra_j2000_h * 15.0,
        current_dec_j2000,
        correction_ra_deg,
        correction_dec_deg,
    )
    if move_arcsec / 3600.0 > MAX_AUTOMATIC_CORRECTION_DEG:
        return {
            **base,
            "automatic_correction_blocked": True,
            "automatic_correction_reason": "computed_move_exceeds_safety_limit",
        }

    return {
        **base,
        "correction_ra_hours": correction_ra_deg / 15.0,
        "correction_dec_deg": correction_dec_deg,
        "correction_basis": "live_mount_readback_j2000_plus_solve_error",
        "mount_readback_ra_hours": float(current_ra),
        "mount_readback_dec_deg": float(current_dec),
        "mount_coordinate_property": coordinate_property,
        "mount_readback_j2000_ra_hours": current_ra_j2000_h,
        "mount_readback_j2000_dec_deg": current_dec_j2000,
        "delta_ra_deg": delta_ra_deg,
        "delta_dec_deg": delta_dec_deg,
    }


def install_safe_centering_patch() -> None:
    # Install a normal instance method so each CaptureSessionService uses its
    # own INDI facade/test double instead of a process-global device object.
    CaptureSessionService._centering_result = safe_centering_result


install_safe_centering_patch()
