from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

from app import _main_core as _core


# The core app is shared with the previous POC generation. The V0.6 extension
# is loaded by app.main, so expose the effective API version here as well.
_core.app.version = "0.6.0-poc"

_MAX_DIAGNOSTIC_AXIS_DELTA_DEG = 0.75
_MAX_OTHER_AXIS_DRIFT_DEG = 0.08


class MountFrameGotoPayload(BaseModel):
    """Coordinates already expressed in the frame published by the mount."""

    ra: float = Field(ge=0, lt=24)
    dec: float = Field(ge=-90, le=90)
    tracking_mode: Literal[
        "sidereal",
        "solar",
        "lunar",
    ] = "sidereal"


def _trusted_mount_clock() -> tuple[dict | None, str | None, dict | None]:
    """Apply the same read-only clock safeguards as the public GOTO route."""
    gps = _core.gps_service.status()
    system = _core.system_service.status()
    timestamp_utc, time_source = _core._resolve_time(gps, system)

    if timestamp_utc is None or time_source not in {"gps", "android"}:
        return None, (
            "Aucune source d'heure fiable disponible pour le diagnostic monture"
        ), None

    clock_check = _core.indi_service.mount_time_status(
        reference_utc=timestamp_utc,
        reference_source=time_source,
    )

    if clock_check.get("status") != "available":
        return None, (
            "État horaire OnStep indisponible : diagnostic monture bloqué"
        ), clock_check

    indi_state = str(clock_check.get("indi_state") or "").strip().lower()
    if indi_state == "alert":
        return None, (
            "TIME_UTC OnStep est en Alert : diagnostic monture bloqué"
        ), clock_check

    expected_offset_hours = None
    offset_minutes = _core.state.client_timezone_offset_minutes
    if offset_minutes is not None:
        expected_offset_hours = offset_minutes / 60.0
        actual_offset_hours = clock_check.get("offset_hours")
        if (
            actual_offset_hours is not None
            and abs(actual_offset_hours - expected_offset_hours) > 0.01
        ):
            enriched = {
                **clock_check,
                "expected_offset_hours": expected_offset_hours,
                "offset_matches_reference": False,
            }
            return None, (
                "Offset OnStep incohérent avec la tablette : "
                f"OnStep {actual_offset_hours:+.2f} h, "
                f"attendu {expected_offset_hours:+.2f} h"
            ), enriched

    enriched = {
        **clock_check,
        "expected_offset_hours": expected_offset_hours,
        "offset_matches_reference": (
            None
            if expected_offset_hours is None
            or clock_check.get("offset_hours") is None
            else abs(clock_check["offset_hours"] - expected_offset_hours) <= 0.01
        ),
    }
    return enriched, time_source, None


def _signed_hour_delta(start_hours: float, end_hours: float) -> float:
    delta = (end_hours - start_hours) % 24.0
    if delta > 12.0:
        delta -= 24.0
    return delta


def _validate_small_field_move(payload: MountFrameGotoPayload) -> dict:
    """Refuse any diagnostic command that is not a small single-axis move."""
    status = _core.indi_service.mount_status()
    start_ra = status.get("ra")
    start_dec = status.get("dec")

    if start_ra is None or start_dec is None:
        raise RuntimeError(
            "Position RA/DEC OnStep indisponible : diagnostic moteur bloqué"
        )

    ra_delta_deg = _signed_hour_delta(
        float(start_ra),
        payload.ra,
    ) * 15.0
    dec_delta_deg = payload.dec - float(start_dec)

    ra_moves = abs(ra_delta_deg) > _MAX_OTHER_AXIS_DRIFT_DEG
    dec_moves = abs(dec_delta_deg) > _MAX_OTHER_AXIS_DRIFT_DEG

    if ra_moves and dec_moves:
        raise RuntimeError(
            "Diagnostic refusé : un seul axe peut être déplacé à la fois"
        )

    if (
        abs(ra_delta_deg) > _MAX_DIAGNOSTIC_AXIS_DELTA_DEG
        or abs(dec_delta_deg) > _MAX_DIAGNOSTIC_AXIS_DELTA_DEG
    ):
        raise RuntimeError(
            "Diagnostic refusé : déplacement demandé supérieur à 0,75°"
        )

    return {
        "start_ra_hours": float(start_ra),
        "start_dec_deg": float(start_dec),
        "requested_ra_delta_deg": ra_delta_deg,
        "requested_dec_delta_deg": dec_delta_deg,
        "max_axis_delta_deg": _MAX_DIAGNOSTIC_AXIS_DELTA_DEG,
    }


@_core.app.post("/mount/goto-mount-frame")
def mount_goto_mount_frame(payload: MountFrameGotoPayload):
    """Small diagnostic GOTO without J2000/JNow conversion.

    This endpoint is intentionally separate from `/mount/goto`. It is only for
    relative field tests that start from coordinates just read from
    `/mount/status`. Those coordinates already use the mount's own INDI frame,
    so precessing them again would corrupt the requested motor displacement.
    """
    clock_check, time_source_or_detail, clock_error = _trusted_mount_clock()

    if clock_check is None:
        return {
            "status": "error",
            "detail": time_source_or_detail,
            "time_check": clock_error,
            "coordinate_frame": "mount",
        }

    try:
        safety = _validate_small_field_move(payload)
    except RuntimeError as exc:
        return {
            "status": "error",
            "detail": str(exc),
            "time_source": time_source_or_detail,
            "time_check": clock_check,
            "coordinate_frame": "mount",
        }

    result = _core.indi_service.goto(
        payload.ra,
        payload.dec,
        tracking_mode=payload.tracking_mode,
    )

    result["coordinate_frame"] = "mount"
    result["requested_mount_frame"] = {
        "ra_hours": payload.ra,
        "dec_deg": payload.dec,
    }
    result["coordinate_transform"] = {
        "source_frame": "mount",
        "target_frame": "mount",
        "transformed": False,
        "mount_ra_hours": payload.ra,
        "mount_dec_deg": payload.dec,
    }
    result["diagnostic_safety"] = safety
    result["time_source"] = time_source_or_detail
    result["time_check"] = clock_check
    result["time_sync"] = {
        "status": "preserved",
        "mode": "read_only",
        "detail": (
            "Horloge OnStep conservée ; aucune écriture TIME_UTC pendant le test"
        ),
    }
    return result
