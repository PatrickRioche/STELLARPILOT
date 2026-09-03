from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

from app import _main_core as _core


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
