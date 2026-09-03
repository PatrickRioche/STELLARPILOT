from __future__ import annotations

import time
from typing import Literal

from pydantic import BaseModel, Field

from app import _main_core as _core


# The core app is shared with the previous POC generation. The V0.6 extension
# is loaded by app.main, so expose the effective API version here as well.
_core.app.version = "0.6.0-poc"

_MAX_DIAGNOSTIC_AXIS_DELTA_DEG = 0.75
_MAX_OTHER_AXIS_DRIFT_DEG = 0.08
_MOUNT_TIME_TOLERANCE_SECONDS = 10.0
_MOUNT_TIME_SYNC_MAX_AGE_SECONDS = 12 * 60 * 60


class MountFrameGotoPayload(BaseModel):
    """Coordinates already expressed in the frame published by the mount."""

    ra: float = Field(ge=0, lt=24)
    dec: float = Field(ge=-90, le=90)
    tracking_mode: Literal[
        "sidereal",
        "solar",
        "lunar",
    ] = "sidereal"


def _trusted_reference_time() -> tuple[str | None, str | None]:
    gps = _core.gps_service.status()
    system = _core.system_service.status()
    return _core._resolve_time(gps, system)


def _verified_time_sync(clock_check: dict) -> dict:
    """Validate the last explicit TIME_UTC write for this server session.

    The LX200 OnStep INDI driver publishes TIME_UTC as the last written
    setpoint. It does not increment that property every second, so comparing
    the published timestamp continuously with GPS creates an artificial drift.
    A mount is therefore trusted after one successful write/readback pair,
    provided the same setpoint and offset are still published and the
    verification is recent enough for the observing session.
    """
    state = _core.state
    synced_at = state.mount_time_sync_monotonic_s
    recorded_mount_utc = state.mount_time_sync_mount_utc
    recorded_offset_minutes = state.mount_time_sync_offset_minutes

    if synced_at is None or recorded_mount_utc is None:
        return {
            "verified": False,
            "status": "required",
            "detail": "Aucune synchronisation TIME_UTC verifiee dans cette session",
            "age_seconds": None,
            "readback_matches_setpoint": None,
            "offset_matches_sync": None,
        }

    age_seconds = max(0.0, time.monotonic() - synced_at)
    current_mount_utc = clock_check.get("utc")
    readback_matches = current_mount_utc == recorded_mount_utc

    offset_matches_sync = None
    current_offset = clock_check.get("offset_hours")
    if recorded_offset_minutes is not None and current_offset is not None:
        offset_matches_sync = (
            abs(current_offset - recorded_offset_minutes / 60.0) <= 0.01
        )

    verified = (
        age_seconds <= _MOUNT_TIME_SYNC_MAX_AGE_SECONDS
        and readback_matches
        and offset_matches_sync is not False
    )

    detail = None
    if age_seconds > _MOUNT_TIME_SYNC_MAX_AGE_SECONDS:
        detail = "Synchronisation TIME_UTC trop ancienne pour cette session"
    elif not readback_matches:
        detail = "Le readback TIME_UTC ne correspond plus au dernier point de consigne"
    elif offset_matches_sync is False:
        detail = "L'offset OnStep a change depuis la derniere synchronisation"

    return {
        "verified": verified,
        "status": "verified" if verified else "required",
        "detail": detail,
        "age_seconds": round(age_seconds, 3),
        "max_age_seconds": _MOUNT_TIME_SYNC_MAX_AGE_SECONDS,
        "reference_utc": state.mount_time_sync_reference_utc,
        "mount_setpoint_utc": recorded_mount_utc,
        "reference_source": state.mount_time_sync_source,
        "timezone_offset_minutes": recorded_offset_minutes,
        "readback_matches_setpoint": readback_matches,
        "offset_matches_sync": offset_matches_sync,
        "readback_kind": "indi_setpoint",
    }


def _trusted_mount_clock() -> tuple[dict | None, str | None, dict | None]:
    """Require trusted session time and a verified OnStep TIME_UTC setpoint."""
    timestamp_utc, time_source = _trusted_reference_time()

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

    verification = _verified_time_sync(clock_check)
    enriched = {
        **clock_check,
        "expected_offset_hours": expected_offset_hours,
        "offset_matches_reference": (
            None
            if expected_offset_hours is None
            or clock_check.get("offset_hours") is None
            else abs(clock_check["offset_hours"] - expected_offset_hours) <= 0.01
        ),
        "time_sync_verification": verification,
        "raw_drift_advisory": True,
    }

    if not verification["verified"]:
        return None, (
            "Synchronisation horaire OnStep requise : lancez /mount/time/sync "
            "avant les mouvements"
        ), enriched

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

    ra_delta_deg = _signed_hour_delta(float(start_ra), payload.ra) * 15.0
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


@_core.app.post("/mount/time/sync")
def mount_time_sync():
    """Synchronize OnStep once from a trusted GPS/Android reference."""
    timestamp_utc, time_source = _trusted_reference_time()

    if timestamp_utc is None or time_source not in {"gps", "android"}:
        return {
            "status": "error",
            "detail": "Aucune source d'heure GPS/Android fiable disponible",
            "reference_source": time_source,
        }

    before = _core.indi_service.mount_time_status(
        reference_utc=timestamp_utc,
        reference_source=time_source,
    )

    offset_minutes = _core.state.client_timezone_offset_minutes
    offset_source = "android"

    if offset_minutes is None:
        existing_offset_hours = before.get("offset_hours")
        if existing_offset_hours is None:
            return {
                "status": "error",
                "detail": (
                    "Fuseau inconnu : ni la tablette ni OnStep ne publient "
                    "un offset exploitable"
                ),
                "reference_source": time_source,
                "reference_utc": timestamp_utc,
            }
        offset_minutes = int(round(float(existing_offset_hours) * 60.0))
        offset_source = "onstep_readback"

    write_result = _core.indi_service.sync_mount_time(
        utc_iso=timestamp_utc,
        timezone_offset_minutes=offset_minutes,
    )

    if write_result.get("status") != "ok":
        return {
            "status": "error",
            "detail": write_result.get(
                "detail",
                "Écriture TIME_UTC OnStep impossible",
            ),
            "before": before,
            "write": write_result,
        }

    readback = None
    for delay_s in (0.15, 0.35, 0.75):
        time.sleep(delay_s)
        current_reference_utc, current_source = _trusted_reference_time()
        readback = _core.indi_service.mount_time_status(
            reference_utc=current_reference_utc or timestamp_utc,
            reference_source=current_source or time_source,
        )
        if readback.get("synchronized") is True:
            break

    if readback is None or readback.get("synchronized") is not True:
        return {
            "status": "error",
            "detail": (
                "TIME_UTC a été écrit mais le readback OnStep reste hors "
                f"tolérance ({_MOUNT_TIME_TOLERANCE_SECONDS:.0f} s)"
            ),
            "before": before,
            "write": write_result,
            "readback": readback,
        }

    _core.state.mount_time_sync_reference_utc = timestamp_utc
    _core.state.mount_time_sync_mount_utc = readback.get("utc")
    _core.state.mount_time_sync_source = time_source
    _core.state.mount_time_sync_offset_minutes = offset_minutes
    _core.state.mount_time_sync_monotonic_s = time.monotonic()

    verification = _verified_time_sync(readback)

    return {
        "status": "synced",
        "reference_source": time_source,
        "reference_utc": timestamp_utc,
        "timezone_offset_minutes": offset_minutes,
        "timezone_offset_source": offset_source,
        "before": before,
        "write": write_result,
        "readback": readback,
        "verification": verification,
        "tolerance_seconds": _MOUNT_TIME_TOLERANCE_SECONDS,
        "note": (
            "TIME_UTC INDI est un point de consigne statique. La validation "
            "repose sur l'ecriture suivie du readback, pas sur son avance seconde par seconde."
        ),
    }


@_core.app.get("/mount/time/verification")
def mount_time_verification():
    """Verify session time safety without commanding any mount movement."""
    timestamp_utc, time_source = _trusted_reference_time()

    clock_check = _core.indi_service.mount_time_status(
        reference_utc=timestamp_utc,
        reference_source=time_source,
    )
    verification = _verified_time_sync(clock_check)
    indi_state = str(clock_check.get("indi_state") or "").strip().lower()
    trusted_reference = (
        timestamp_utc is not None and time_source in {"gps", "android"}
    )
    control_ready = (
        trusted_reference
        and clock_check.get("status") == "available"
        and indi_state != "alert"
        and verification["verified"]
    )

    return {
        "status": "verified" if control_ready else "required",
        "control_ready": control_ready,
        "reference_source": time_source,
        "reference_utc": timestamp_utc,
        "raw_readback": clock_check,
        "verification": verification,
        "note": (
            "Le drift_seconds brut augmente normalement car TIME_UTC est un "
            "point de consigne INDI statique ; il n'est pas utilise comme horloge temps reel."
        ),
    }


@_core.app.post("/mount/goto-mount-frame")
def mount_goto_mount_frame(payload: MountFrameGotoPayload):
    """Small diagnostic GOTO without J2000/JNow conversion."""
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
        "status": "verified",
        "mode": "explicit_write_readback",
        "detail": (
            "Point de consigne TIME_UTC verifie et conserve pendant la session"
        ),
    }
    return result
