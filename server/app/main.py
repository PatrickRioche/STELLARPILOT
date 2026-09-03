from pathlib import Path
from typing import Literal

from fastapi import HTTPException
from fastapi.responses import Response
from pydantic import BaseModel, Field

from app import _main_core as _core
from app._main_core import *
from app.imaging.centering_capture import (
    capture_centering_frame,
    solve_centering_frame,
)
from app.imaging.preparation_astrometry import (
    preparation_astrometry_archive,
)
from app.imaging.preview import preview_headers, render_fits_preview
from app.imaging.quality import analyze_fits
from app.imaging.sessions import CaptureSessionService, capture_session_service
from app.indi.coordinates import (
    prepare_j2000_for_mount,
    sync_mount_j2000,
)


app = _core.app


class TrackingGotoPayload(_core.GotoPayload):
    tracking_mode: Literal[
        "sidereal",
        "solar",
        "lunar",
    ] = "sidereal"


class MountSyncPayload(BaseModel):
    ra_deg: float = Field(ge=0, lt=360)
    dec_deg: float = Field(ge=-90, le=90)


class CaptureSessionPayload(BaseModel):
    target_name: str = Field(min_length=1, max_length=120)
    target_ra_hours: float = Field(ge=0, lt=24)
    target_dec_deg: float = Field(ge=-90, le=90)
    object_type: str = "unknown"
    tracking_mode: Literal[
        "sidereal",
        "solar",
        "lunar",
    ] = "sidereal"
    exposure_s: float = Field(default=4.0, gt=0, le=3600)
    centering_tolerance_arcsec: float = Field(
        default=30.0,
        gt=0,
        le=3600,
    )
    recenter_tolerance_arcsec: float = Field(
        default=30.0,
        gt=0,
        le=3600,
    )
    astrometry_interval_frames: int = Field(
        default=8,
        ge=1,
        le=1000,
    )
    registration_recenter_pixels: float = Field(
        default=12.0,
        gt=0,
        le=1000,
    )


class CameraQualityPayload(BaseModel):
    image: str


# CaptureSessionService historically had its own, simpler FITS -> JPEG path.
# Route every Capture/Stack/Gallery preview through the same renderer as
# Preparation Assistant 3 without duplicating processing logic.
def _unified_session_preview_bytes(path: Path) -> bytes:
    content, _diagnostics = render_fits_preview(path)
    return content


CaptureSessionService._fits_preview_bytes = staticmethod(
    _unified_session_preview_bytes
)


# Replace the routes that need behavior layered on top of _main_core.
_OVERRIDDEN_ROUTES = {
    ("/mount/goto", "POST"),
    ("/camera/capture", "POST"),
    ("/camera/preview.jpg", "GET"),
    ("/solve", "POST"),
}

app.router.routes[:] = [
    route
    for route in app.router.routes
    if not any(
        getattr(route, "path", None) == path
        and method
        in (getattr(route, "methods", set()) or set())
        for path, method in _OVERRIDDEN_ROUTES
    )
]


@app.post("/camera/capture")
def capture(payload: _core.CapturePayload):
    """Capture Assistant 3 frame and persist the FITS immediately."""
    result = _core.capture(payload)

    if result.get("status") != "captured":
        return result

    # The INDI facade may already have persisted the frame. Keep this route as
    # the authoritative Assistant 3 boundary while avoiding duplicate archives.
    archive = result.get("persistent_astrometry")
    if not isinstance(archive, dict):
        try:
            archive = preparation_astrometry_archive.archive_capture(result)
        except Exception as exc:
            _core.logger.exception(
                "Unable to persist Preparation Assistant 3 astrometry capture"
            )
            raise HTTPException(
                status_code=500,
                detail=(
                    "Capture réalisée mais archivage persistant "
                    f"Assistant 3 impossible : {exc}"
                ),
            ) from exc

    enriched = dict(result)
    enriched["persistent_archive"] = archive
    return enriched


@app.get("/camera/preview.jpg")
def camera_preview():
    """Return the canonical StellarPilot preview for Assistant 3."""
    capture_dir = Path("/tmp/stellarpilot-captures")

    if not capture_dir.exists():
        raise HTTPException(
            status_code=404,
            detail="No FITS capture available",
        )

    candidates = [
        item
        for item in capture_dir.iterdir()
        if item.is_file()
        and item.suffix.lower() in {".fits", ".fit", ".fts"}
    ]
    if not candidates:
        raise HTTPException(
            status_code=404,
            detail="No FITS capture available",
        )

    latest = max(candidates, key=lambda item: item.stat().st_mtime)

    try:
        content, diagnostics = render_fits_preview(latest)
    except Exception as exc:
        _core.logger.exception("Unable to generate unified camera preview")
        raise HTTPException(
            status_code=500,
            detail=f"Camera preview generation error: {exc}",
        ) from exc

    headers = preview_headers(latest.name, diagnostics)

    try:
        preparation_astrometry_archive.record_preview(
            latest.name,
            content,
            headers=headers,
        )
    except Exception:
        # initial.fits is already safe; a JPEG can always be regenerated.
        _core.logger.exception(
            "Unable to persist Preparation Assistant 3 preview"
        )

    return Response(
        content=content,
        media_type="image/jpeg",
        headers=headers,
    )


@app.post("/camera/quality")
def camera_quality(payload: CameraQualityPayload):
    """Score whether one FITS frame is suitable for plate solving."""
    result = analyze_fits(payload.image)
    if result.get("status") != "ok":
        raise HTTPException(
            status_code=422,
            detail=result.get("detail", "Analyse qualité impossible"),
        )
    return result


@app.post("/solve")
def solve(payload: _core.SolvePayload):
    """Solve the Assistant 3 frame and enrich persistent metadata."""
    result = _core.solve(payload)

    try:
        preparation_astrometry_archive.record_solve(
            payload.image,
            result,
        )
    except Exception:
        _core.logger.exception(
            "Unable to update Preparation Assistant 3 solve metadata"
        )

    return result


@app.post("/mount/goto")
def mount_goto(payload: TrackingGotoPayload):
    """Point a J2000 catalog target without rewriting the OnStep clock."""
    gps = _core.gps_service.status()
    system = _core.system_service.status()

    timestamp_utc, time_source = _core._resolve_time(
        gps,
        system,
    )

    # StellarPilot still requires a trusted session time for sky calculations,
    # but hardware tests showed that rewriting TIME_UTC before every GOTO can
    # disturb an otherwise correct OnStep configuration. GOTO is therefore
    # read-only with respect to the mount clock.
    if (
        timestamp_utc is None
        or time_source not in {"gps", "android"}
    ):
        return {
            "status": "error",
            "detail": (
                "Aucune source d'heure fiable disponible pour le pointage"
            ),
            "time_source": time_source,
        }

    clock_check = _core.indi_service.mount_time_status(
        reference_utc=timestamp_utc,
        reference_source=time_source,
    )

    if clock_check.get("status") != "available":
        return {
            "status": "error",
            "detail": (
                "État horaire OnStep indisponible : pointage bloqué"
            ),
            "time_source": time_source,
            "time_check": clock_check,
        }

    indi_state = str(
        clock_check.get("indi_state") or ""
    ).strip().lower()

    if indi_state == "alert":
        return {
            "status": "error",
            "detail": (
                "TIME_UTC OnStep est en Alert : corrigez l'heure de la "
                "monture avant le pointage"
            ),
            "time_source": time_source,
            "time_check": clock_check,
        }

    expected_offset_hours = None
    offset_minutes = _core.state.client_timezone_offset_minutes

    if offset_minutes is not None:
        expected_offset_hours = offset_minutes / 60.0
        actual_offset_hours = clock_check.get("offset_hours")

        if (
            actual_offset_hours is not None
            and abs(actual_offset_hours - expected_offset_hours) > 0.01
        ):
            return {
                "status": "error",
                "detail": (
                    "Offset OnStep incohérent avec la tablette : "
                    f"OnStep {actual_offset_hours:+.2f} h, "
                    f"attendu {expected_offset_hours:+.2f} h"
                ),
                "time_source": time_source,
                "time_check": {
                    **clock_check,
                    "expected_offset_hours": expected_offset_hours,
                    "offset_matches_reference": False,
                },
            }

    # Catalog coordinates used by StellarPilot are J2000. The INDI standard
    # distinguishes EQUATORIAL_COORD (J2000) and EQUATORIAL_EOD_COORD (JNow).
    # The core driver prefers EOD when it is exposed, so precess the target to
    # the same frame before sending the hardware command.
    try:
        prepared = prepare_j2000_for_mount(
            _core.indi_service,
            payload.ra,
            payload.dec,
        )
    except RuntimeError as exc:
        return {
            "status": "error",
            "detail": str(exc),
            "time_source": time_source,
            "time_check": clock_check,
        }

    result = _core.indi_service.goto(
        prepared["mount_ra_hours"],
        prepared["mount_dec_deg"],
        tracking_mode=payload.tracking_mode,
    )

    result["requested_j2000"] = {
        "ra_hours": payload.ra,
        "dec_deg": payload.dec,
    }
    result["coordinate_transform"] = prepared
    result["time_source"] = time_source
    result["time_check"] = {
        **clock_check,
        "expected_offset_hours": expected_offset_hours,
        "offset_matches_reference": (
            None
            if expected_offset_hours is None
            or clock_check.get("offset_hours") is None
            else abs(
                clock_check["offset_hours"] - expected_offset_hours
            ) <= 0.01
        ),
    }
    # Kept for compatibility with clients that already display time_sync.
    result["time_sync"] = {
        "status": "preserved",
        "mode": "read_only",
        "detail": (
            "Horloge OnStep conservée ; aucune écriture TIME_UTC avant GOTO"
        ),
    }

    return result


@app.post("/mount/sync")
def mount_sync(payload: MountSyncPayload):
    """Sync OnStep to an astrometry.net J2000 field center."""
    try:
        return sync_mount_j2000(
            _core.indi_service,
            ra_deg=payload.ra_deg,
            dec_deg=payload.dec_deg,
        )
    except RuntimeError as exc:
        return {
            "status": "error",
            "detail": str(exc),
            "ra_deg": payload.ra_deg,
            "dec_deg": payload.dec_deg,
        }


@app.get("/mount/time")
def mount_time():
    """Read the INDI-published OnStep TIME_UTC diagnostic value."""
    gps = _core.gps_service.status()
    system = _core.system_service.status()
    reference_utc, reference_source = _core._resolve_time(
        gps,
        system,
    )

    return _core.indi_service.mount_time_status(
        reference_utc=reference_utc,
        reference_source=reference_source,
    )


def _time_drift_seconds(
    value: str | None,
    reference: str | None,
) -> float | None:
    if not value or not reference:
        return None

    try:
        instant = _core.indi_service._utc_instant(value)
        reference_instant = _core.indi_service._utc_instant(reference)
    except ValueError:
        return None

    return round(
        abs((instant - reference_instant).total_seconds()),
        3,
    )


def _time_source_entry(
    *,
    utc_value: str | None,
    reference_utc: str | None,
    trusted_reference: bool,
    available: bool,
    status: str,
    comparison_enabled: bool = True,
    extra: dict | None = None,
) -> dict:
    normalized_utc = utc_value

    if utc_value:
        try:
            normalized_utc = _core.indi_service._normalize_utc(
                utc_value
            )
        except ValueError:
            normalized_utc = utc_value

    drift_seconds = None
    synchronized = None

    if comparison_enabled:
        drift_seconds = _time_drift_seconds(
            normalized_utc,
            reference_utc,
        )

        if reference_utc and available and drift_seconds is not None:
            synchronized = drift_seconds <= 10.0

    return {
        "available": available,
        "status": status,
        "utc": normalized_utc,
        "drift_seconds": drift_seconds,
        "synchronized": synchronized,
        "trusted_reference": trusted_reference,
        **(extra or {}),
    }


@app.get("/time/synchronization")
def time_synchronization():
    """Compare session time sources without rewriting the OnStep clock."""
    gps = _core.gps_service.status()
    system = _core.system_service.status()
    android_utc = _core._client_time_utc()
    reference_utc, reference_source = _core._resolve_time(
        gps,
        system,
    )

    trusted_reference = (
        reference_source in {"gps", "android"}
        and reference_utc is not None
    )

    normalized_reference = reference_utc
    if reference_utc:
        try:
            normalized_reference = _core.indi_service._normalize_utc(
                reference_utc
            )
        except ValueError:
            pass

    gps_utc = gps.get("time_utc")
    gps_available = (
        gps.get("status") == "fix"
        and bool(gps_utc)
    )

    system_utc = system.get("datetime")
    system_available = bool(system_utc)

    onstep = _core.indi_service.mount_time_status(
        reference_utc=normalized_reference,
        reference_source=reference_source,
    )

    expected_offset_hours = None
    if _core.state.client_timezone_offset_minutes is not None:
        expected_offset_hours = (
            _core.state.client_timezone_offset_minutes / 60.0
        )

    onstep_offset = onstep.get("offset_hours")
    offset_matches_reference = None
    if expected_offset_hours is not None and onstep_offset is not None:
        offset_matches_reference = (
            abs(onstep_offset - expected_offset_hours) <= 0.01
        )

    onstep_state = str(
        onstep.get("indi_state") or ""
    ).strip().lower()
    onstep_available = onstep.get("status") == "available"
    onstep_control_ready = (
        trusted_reference
        and onstep_available
        and onstep_state != "alert"
        and offset_matches_reference is not False
    )

    sources = {
        "gps": _time_source_entry(
            utc_value=gps_utc,
            reference_utc=normalized_reference,
            trusted_reference=(reference_source == "gps"),
            available=gps_available,
            status=gps.get("status", "unknown"),
            comparison_enabled=trusted_reference,
            extra={
                "latitude": gps.get("latitude"),
                "longitude": gps.get("longitude"),
            },
        ),
        "android": _time_source_entry(
            utc_value=android_utc,
            reference_utc=normalized_reference,
            trusted_reference=(reference_source == "android"),
            available=android_utc is not None,
            status=(
                "available"
                if android_utc is not None
                else "unavailable"
            ),
            comparison_enabled=trusted_reference,
            extra={
                "timezone_offset_minutes": (
                    _core.state.client_timezone_offset_minutes
                ),
            },
        ),
        "raspberry_pi": _time_source_entry(
            utc_value=system_utc,
            reference_utc=normalized_reference,
            trusted_reference=False,
            available=system_available,
            status=(
                "available"
                if system_available
                else "unavailable"
            ),
            comparison_enabled=trusted_reference,
            extra={
                "authoritative": False,
            },
        ),
        "onstep": {
            "available": onstep_available,
            "status": onstep.get("status", "unavailable"),
            "utc": onstep.get("utc"),
            # TIME_UTC can be a published/cached value and is therefore kept
            # as an advisory diagnostic instead of a GOTO readiness criterion.
            "drift_seconds": onstep.get("drift_seconds"),
            "synchronized": None,
            "trusted_reference": False,
            "offset_hours": onstep_offset,
            "expected_offset_hours": expected_offset_hours,
            "offset_matches_reference": offset_matches_reference,
            "control_ready": onstep_control_ready,
            "indi_state": onstep.get("indi_state"),
            "indi_permission": onstep.get("indi_permission"),
            "synchronization": onstep.get("synchronization"),
            "readback_kind": "indi_published_time",
            "drift_advisory": True,
            "detail": onstep.get("detail"),
        },
    }

    if not trusted_reference:
        overall = "unverified"
    else:
        comparable_checks = [
            source.get("synchronized")
            for key, source in sources.items()
            if key != "onstep"
            and source.get("available")
            and source.get("utc") is not None
            and source.get("synchronized") is not None
        ]
        any_bad = any(value is False for value in comparable_checks)
        any_alert = onstep_state == "alert"
        offset_bad = offset_matches_reference is False
        all_sources_available = all(
            source.get("available")
            for source in sources.values()
        )

        if any_bad or any_alert or offset_bad:
            overall = "attention"
        elif all_sources_available and onstep_control_ready:
            overall = "synchronized"
        elif onstep_control_ready:
            overall = "partial"
        else:
            overall = "attention"

    return {
        "status": overall,
        "tolerance_seconds": 10.0,
        "reference_source": reference_source,
        "reference_utc": normalized_reference,
        "mount_control_ready": onstep_control_ready,
        "sources": sources,
        "note": (
            "TIME_UTC est une valeur diagnostic publiée par INDI. "
            "StellarPilot ne réécrit plus l'horloge OnStep avant un GOTO ; "
            "le pointage conserve le réglage validé directement sur la monture."
        ),
    }


@app.post("/capture/sessions")
def create_capture_session(payload: CaptureSessionPayload):
    return capture_session_service.create_session(
        target_name=payload.target_name,
        target_ra_hours=payload.target_ra_hours,
        target_dec_deg=payload.target_dec_deg,
        object_type=payload.object_type,
        tracking_mode=payload.tracking_mode,
        exposure_s=payload.exposure_s,
        centering_tolerance_arcsec=(
            payload.centering_tolerance_arcsec
        ),
        recenter_tolerance_arcsec=(
            payload.recenter_tolerance_arcsec
        ),
        astrometry_interval_frames=(
            payload.astrometry_interval_frames
        ),
        registration_recenter_pixels=(
            payload.registration_recenter_pixels
        ),
    )


@app.get("/capture/sessions")
def capture_sessions():
    return {
        "sessions": capture_session_service.list_sessions(),
    }


def _capture_session_or_404(session_id: str) -> dict:
    try:
        return capture_session_service.get_session(session_id)
    except KeyError as exc:
        raise HTTPException(
            status_code=404,
            detail="Capture session not found",
        ) from exc


@app.get("/capture/sessions/{session_id}")
def capture_session(session_id: str):
    return _capture_session_or_404(session_id)


@app.post("/capture/sessions/{session_id}/center")
def capture_session_center(session_id: str):
    _capture_session_or_404(session_id)
    return capture_session_service.center_step(session_id)


@app.post("/capture/sessions/{session_id}/center/capture")
def capture_session_center_capture(session_id: str):
    _capture_session_or_404(session_id)
    return capture_centering_frame(session_id)


@app.post("/capture/sessions/{session_id}/center/solve")
def capture_session_center_solve(session_id: str):
    _capture_session_or_404(session_id)
    return solve_centering_frame(session_id)


@app.post("/capture/sessions/{session_id}/stack/start")
def capture_session_stack_start(session_id: str):
    _capture_session_or_404(session_id)
    return capture_session_service.start_stack(session_id)


@app.post("/capture/sessions/{session_id}/stack/resume")
def capture_session_stack_resume(session_id: str):
    _capture_session_or_404(session_id)
    return capture_session_service.resume_stack(session_id)


@app.post("/capture/sessions/{session_id}/stack/stop")
def capture_session_stack_stop(session_id: str):
    _capture_session_or_404(session_id)
    return capture_session_service.stop_stack(session_id)


@app.post("/capture/sessions/{session_id}/finalize")
def capture_session_finalize(session_id: str):
    _capture_session_or_404(session_id)
    return capture_session_service.finalize(session_id)


@app.get("/capture/sessions/{session_id}/preview.jpg")
def capture_session_preview(session_id: str):
    _capture_session_or_404(session_id)
    try:
        content = capture_session_service.preview_bytes(
            session_id,
            stack=False,
        )
    except FileNotFoundError as exc:
        raise HTTPException(
            status_code=404,
            detail="Capture preview not available",
        ) from exc
    return Response(
        content=content,
        media_type="image/jpeg",
        headers={"Cache-Control": "no-store"},
    )


@app.get("/capture/sessions/{session_id}/stack/preview.jpg")
def capture_session_stack_preview(session_id: str):
    _capture_session_or_404(session_id)
    try:
        content = capture_session_service.preview_bytes(
            session_id,
            stack=True,
        )
    except FileNotFoundError as exc:
        raise HTTPException(
            status_code=404,
            detail="Stack preview not available",
        ) from exc
    return Response(
        content=content,
        media_type="image/jpeg",
        headers={"Cache-Control": "no-store"},
    )


@app.get("/galleries/sessions")
def gallery_sessions():
    return {
        "sessions": capture_session_service.list_galleries(),
    }


@app.get("/galleries/sessions/{session_id}/preview.jpg")
def gallery_session_preview(session_id: str):
    try:
        content = capture_session_service.gallery_preview_bytes(
            session_id
        )
    except FileNotFoundError as exc:
        raise HTTPException(
            status_code=404,
            detail="Gallery preview not available",
        ) from exc
    return Response(
        content=content,
        media_type="image/jpeg",
        headers={"Cache-Control": "no-store"},
    )


def __getattr__(name: str):
    return getattr(_core, name)
