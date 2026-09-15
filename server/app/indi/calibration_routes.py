from __future__ import annotations

from fastapi import HTTPException

from app import _main_core as _core
from app.indi.calibration import build_near_zenith_calibration_target


app = _core.app


@app.get("/mount/calibration-target")
def mount_calibration_target():
    """Return a safe high-altitude J2000 target for GEM astrometric SYNC."""
    gps = _core.gps_service.status()
    system = _core.system_service.status()
    onstep_location = _core.indi_service.mount_location()

    timestamp_utc, time_source = _core._resolve_time(
        gps,
        system,
    )
    if (
        timestamp_utc is None
        or time_source not in {"gps", "android"}
    ):
        raise HTTPException(
            status_code=409,
            detail="Heure GPS/Android requise pour la calibration zénithale",
        )

    (
        latitude,
        longitude,
        altitude,
        location_source,
    ) = _core._resolve_location(
        gps,
        onstep_location,
        "device",
    )

    if latitude is None or longitude is None:
        raise HTTPException(
            status_code=409,
            detail="Position GPS/OnStep requise pour la calibration zénithale",
        )

    try:
        target = build_near_zenith_calibration_target(
            latitude_deg=float(latitude),
            longitude_deg=float(longitude),
            altitude_m=(
                float(altitude)
                if altitude is not None
                else None
            ),
            timestamp_utc=str(timestamp_utc),
        )
    except (ValueError, RuntimeError) as exc:
        raise HTTPException(
            status_code=422,
            detail=str(exc),
        ) from exc

    return {
        "status": "ready",
        **target,
        "latitude_deg": float(latitude),
        "longitude_deg": float(longitude),
        "altitude_m": altitude,
        "location_source": location_source,
        "time_source": time_source,
    }
