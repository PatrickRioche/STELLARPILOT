import json
import logging
from concurrent.futures import ThreadPoolExecutor
from io import BytesIO
from pathlib import Path
from datetime import datetime, timezone
from time import monotonic, perf_counter

import numpy as np
from astropy.io import fits
from PIL import Image

from fastapi import (
    FastAPI,
    HTTPException,
    WebSocket,
    WebSocketDisconnect,
)
from fastapi.responses import Response
from pydantic import BaseModel, Field

from app.catalog.service import catalog_service
from app.gps.service import gps_service
from app.indi.service import indi_service
from app.imaging.quality import analyze_fits
from app.imaging.auto_capture import run_auto_capture
from app.session.state import state
from app.sky.service import sky_service
from app.sky.objects import sky_objects_service
from app.solving.service import plate_solver
from app.system.service import system_service


logger = logging.getLogger("stellarpilot.websocket")


app = FastAPI(
    title="StellarPilot Server",
    version="0.5.0",
)


class LocationPayload(BaseModel):
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    altitude: float | None = None
    timestamp: str


class TimePayload(BaseModel):
    utc_epoch_ms: int = Field(gt=0)
    timezone_offset_minutes: int = Field(
        ge=-840,
        le=840,
    )


class MountTypePayload(BaseModel):
    mount_type: str


class CapturePayload(BaseModel):
    exposure_s: float = Field(gt=0, le=3600)


class AutoCapturePayload(BaseModel):
    start_exposure_s: float = Field(gt=0, le=10)
    max_attempts: int = Field(default=5, ge=1, le=8)


class GotoPayload(BaseModel):
    ra: float = Field(ge=0, lt=24)
    dec: float = Field(ge=-90, le=90)


class SolvePayload(BaseModel):
    image: str


def _client_time_utc() -> str | None:
    if (
        state.client_time_epoch_s is None
        or state.client_time_monotonic_s is None
    ):
        return None

    epoch_s = (
        state.client_time_epoch_s
        + max(
            0.0,
            monotonic()
            - state.client_time_monotonic_s,
        )
    )

    return (
        datetime.fromtimestamp(
            epoch_s,
            tz=timezone.utc,
        )
        .isoformat(timespec="seconds")
        .replace("+00:00", "Z")
    )


def _resolve_time(
    gps: dict,
    system: dict,
) -> tuple[str | None, str]:

    if (
        gps.get("status") == "fix"
        and gps.get("time_utc")
    ):
        return gps["time_utc"], "gps"

    client_time = _client_time_utc()

    if client_time is not None:
        return client_time, "android"

    return (
        system.get("datetime"),
        "system_untrusted",
    )


def _resolve_location(
    gps: dict,
    onstep: dict,
    mode: str,
):
    if (
        mode != "device"
        and state.latitude is not None
        and state.longitude is not None
    ):
        return (
            state.latitude,
            state.longitude,
            state.altitude,
            "manual",
        )

    if (
        gps.get("status") == "fix"
        and gps.get("latitude") is not None
        and gps.get("longitude") is not None
    ):
        return (
            gps["latitude"],
            gps["longitude"],
            gps.get("altitude"),
            "gps",
        )

    if (
        onstep.get("status") == "available"
        and onstep.get("latitude") is not None
        and onstep.get("longitude") is not None
    ):
        return (
            onstep["latitude"],
            onstep["longitude"],
            onstep.get("altitude"),
            "onstep",
        )

    if (
        state.latitude is not None
        and state.longitude is not None
    ):
        return (
            state.latitude,
            state.longitude,
            state.altitude,
            "manual",
        )

    return None, None, None, None


@app.get("/health")
def health():
    return {
        "service": "stellarpilot",
        "status": "ok",
        "mode": "device",
        "protocol": "proto-1",
    }


@app.get("/status")
def status():
    """
    Construit l'?tat global du serveur.

    Les sous-syst?mes ind?pendants sont interrog?s en parall?le.
    Une cam?ra lente, le GPS ou chrony ne doivent pas bloquer
    successivement toute la r?ponse /status.
    """
    mode = "device"
    logging.getLogger("uvicorn.error").info(
        "M103 solve START"
    )

    started_at = perf_counter()

    with ThreadPoolExecutor(
        max_workers=4,
        thread_name_prefix="stellarpilot-status",
    ) as executor:
        indi_future = executor.submit(
            indi_service.status_snapshot
        )
        gps_future = executor.submit(
            gps_service.status
        )
        system_future = executor.submit(
            system_service.status
        )
        catalog_future = executor.submit(
            catalog_service.status
        )

        indi_snapshot = indi_future.result()
        gps = gps_future.result()
        system = system_future.result()
        catalog = catalog_future.result()

    indi = {
        "mount": indi_snapshot["mount"],
        "camera": indi_snapshot["camera"],
    }

    onstep_location = indi_snapshot["location"]

    status_duration_ms = int(
        (perf_counter() - started_at) * 1000
    )

    (
        session_latitude,
        session_longitude,
        session_altitude,
        location_source,
    ) = _resolve_location(
        gps,
        onstep_location,
        mode,
    )

    session_timestamp, time_source = (
        _resolve_time(
            gps,
            system,
        )
    )

    detected_mount_type = (
        indi["mount"].get("type")
        or state.mount_type
    )

    mount_type_source = (
        "indi"
        if indi["mount"].get("type")
        else (
            "manual"
            if state.mount_type
            else None
        )
    )

    mount_guidance = indi_service.mount_guidance(
        detected_mount_type
    )

    return {
        "service": "stellarpilot",
        "status": "ok",
        # Champ historique conserv? pendant la transition
        # afin de ne pas casser les clients issus du ancienne version.
        "protocol": "proto-1",
        "mode": mode,
        "devices": {
            "server": {
                "status": "online",
            },
            "mount": indi["mount"],
            "camera": indi["camera"],
            "gps": gps,
        },
        "system": system,
        "catalog": catalog,
        "diagnostics": {
            "status_duration_ms": status_duration_ms,
        },
        "session": {
            "latitude": session_latitude,
            "longitude": session_longitude,
            "altitude": session_altitude,
            "timestamp": session_timestamp,
            "location_source": location_source,
            "time_source": time_source,
            "timezone_offset_minutes": (
                state.client_timezone_offset_minutes
            ),
            "mount_type": detected_mount_type,
            "mount_type_source": mount_type_source,
            "mount_family": mount_guidance["family"],
            "mount_family_label": mount_guidance[
                "family_label"
            ],
            "startup_target": mount_guidance[
                "startup_target"
            ],
        },
    }


@app.get("/sky/bright-stars")
def bright_stars(
    latitude: float | None = None,
    longitude: float | None = None,
    at: str | None = None,
):
    gps = gps_service.status()
    onstep_location = indi_service.mount_location()
    system = system_service.status()

    resolved_at, _time_source = _resolve_time(
        gps,
        system,
    )

    if at is not None:
        resolved_at = at

    if (
        (latitude is None)
        != (longitude is None)
    ):
        return {
            "status": "error",
            "detail": (
                "latitude and longitude "
                "must be provided together"
            ),
        }

    if (
        latitude is not None
        and longitude is not None
    ):
        observer_latitude = latitude
        observer_longitude = longitude
        location_source = "query"

    else:
        (
            observer_latitude,
            observer_longitude,
            _observer_altitude,
            location_source,
        ) = _resolve_location(
            gps,
            onstep_location,
            "device",
        )

        if (
            observer_latitude is None
            or observer_longitude is None
        ):
            return {
                "status": "location_required",
                "detail": (
                    "No GPS fix, OnStep location "
                    "or manual location is currently available"
                ),
            }

    return sky_service.bright_stars(
        latitude=observer_latitude,
        longitude=observer_longitude,
        at=resolved_at,
        location_source=location_source,
    )



@app.get("/sky/objects")
def sky_objects(
    latitude: float | None = None,
    longitude: float | None = None,
    at: str | None = None,
    category: str = "all",
    q: str | None = None,
    min_altitude: float = 15.0,
    direction: str | None = None,
    constellation: str | None = None,
    sort: str = "magnitude",
    order: str = "asc",
    offset: int = 0,
    limit: int = 100,
):
    gps = gps_service.status()
    onstep_location = indi_service.mount_location()
    system = system_service.status()

    resolved_at, _time_source = _resolve_time(
        gps,
        system,
    )

    if at is not None:
        resolved_at = at

    if (
        (latitude is None)
        != (longitude is None)
    ):
        return {
            "status": "error",
            "detail": (
                "latitude and longitude "
                "must be provided together"
            ),
        }

    if (
        latitude is not None
        and longitude is not None
    ):
        observer_latitude = latitude
        observer_longitude = longitude
        location_source = "query"

    else:
        (
            observer_latitude,
            observer_longitude,
            _observer_altitude,
            location_source,
        ) = _resolve_location(
            gps,
            onstep_location,
            "device",
        )

        if (
            observer_latitude is None
            or observer_longitude is None
        ):
            return {
                "status": "location_required",
                "detail": (
                    "No GPS fix, OnStep location "
                    "or manual location is currently available"
                ),
            }

    try:
        return sky_objects_service.objects(
            latitude=observer_latitude,
            longitude=observer_longitude,
            at=resolved_at,
            category=category,
            query=q,
            min_altitude=min_altitude,
            direction=direction,
            constellation=constellation,
            sort=sort,
            order=order,
            offset=offset,
            limit=limit,
            location_source=location_source,
        )

    except ValueError as error:
        raise HTTPException(
            status_code=400,
            detail=str(error),
        )


@app.get("/devices")
def devices():
    return {
        "devices": indi_service.list_devices()
    }


@app.post("/system/time")
def set_time(payload: TimePayload):
    state.client_time_epoch_s = (
        payload.utc_epoch_ms / 1000.0
    )

    state.client_time_monotonic_s = monotonic()

    state.client_timezone_offset_minutes = (
        payload.timezone_offset_minutes
    )

    return {
        "status": "ok",
        "time_source": "android",
        "timestamp_utc": _client_time_utc(),
        "timezone_offset_minutes": (
            state.client_timezone_offset_minutes
        ),
    }


@app.post("/system/location")
def set_location(payload: LocationPayload):
    state.latitude = payload.latitude
    state.longitude = payload.longitude
    state.altitude = payload.altitude
    state.timestamp = payload.timestamp

    try:
        timestamp_ms = int(payload.timestamp)
    except (TypeError, ValueError):
        timestamp_ms = 0

    if timestamp_ms > 1_000_000_000_000:
        state.client_time_epoch_s = (
            timestamp_ms / 1000.0
        )
        state.client_time_monotonic_s = monotonic()

    return {
        "status": "ok"
    }


@app.post("/system/mount-type")
def set_mount_type(payload: MountTypePayload):
    value = payload.mount_type.upper()

    if value not in {"EQ", "AZ"}:
        return {
            "status": "error",
            "detail": "mount_type must be EQ or AZ",
        }

    state.mount_type = value

    return {
        "status": "ok",
        "mount_type": value,
    }


@app.post("/camera/capture")
def capture(payload: CapturePayload):
    return indi_service.capture(payload.exposure_s)


@app.post("/camera/auto-capture")
def auto_capture(payload: AutoCapturePayload):
    return run_auto_capture(
        start_exposure_s=payload.start_exposure_s,
        max_attempts=payload.max_attempts,
    )


@app.get("/camera/preview.jpg")
def camera_preview():
    """
    Build a color JPEG preview from the latest RAW FITS.

    The original FITS is never modified and remains the source
    used by astrometry.net.
    """

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

    latest = max(
        candidates,
        key=lambda item: item.stat().st_mtime,
    )

    try:
        with fits.open(
            latest,
            memmap=False,
        ) as hdul:

            image_data = np.asarray(
                hdul[0].data
            )

            header = hdul[0].header

        while image_data.ndim > 2:
            image_data = image_data[0]

        if image_data.ndim != 2:
            raise ValueError(
                "FITS does not contain a usable 2D image"
            )

        image_data = image_data.astype(
            np.float32,
            copy=False,
        )

        finite = np.isfinite(image_data)

        if not finite.any():
            raise ValueError(
                "Image contains no finite pixels"
            )

        # Keep even dimensions for the Bayer matrix.
        height = (
            image_data.shape[0]
            - image_data.shape[0] % 2
        )

        width = (
            image_data.shape[1]
            - image_data.shape[1] % 2
        )

        image_data = image_data[
            :height,
            :width,
        ]

        bayer_pattern = (
            str(
                header.get(
                    "BAYERPAT",
                    "",
                )
            )
            .strip()
            .upper()
        )

        x_offset = int(
            header.get(
                "XBAYROFF",
                0,
            )
            or 0
        )

        y_offset = int(
            header.get(
                "YBAYROFF",
                0,
            )
            or 0
        )

        # Determine the effective Bayer pattern after offsets.
        patterns = {
            "RGGB": [
                ["R", "G"],
                ["G", "B"],
            ],
            "BGGR": [
                ["B", "G"],
                ["G", "R"],
            ],
            "GRBG": [
                ["G", "R"],
                ["B", "G"],
            ],
            "GBRG": [
                ["G", "B"],
                ["R", "G"],
            ],
        }

        matrix = patterns.get(
            bayer_pattern
        )

        if matrix is not None:
            matrix = [
                [
                    matrix[
                        (row + y_offset) % 2
                    ][
                        (col + x_offset) % 2
                    ]
                    for col in range(2)
                ]
                for row in range(2)
            ]

            channels = {
                "R": [],
                "G": [],
                "B": [],
            }

            for row in range(2):
                for col in range(2):

                    channel_name = matrix[
                        row
                    ][
                        col
                    ]

                    channels[
                        channel_name
                    ].append(
                        image_data[
                            row::2,
                            col::2,
                        ]
                    )

            red = channels["R"][0]

            green = sum(
                channels["G"]
            ) / len(
                channels["G"]
            )

            blue = channels["B"][0]

            #
            # Neutralise the Bayer color cast of the sky
            # background for the screen preview only.
            #
            red_background = float(
                np.median(
                    red[np.isfinite(red)]
                )
            )

            green_background = float(
                np.median(
                    green[np.isfinite(green)]
                )
            )

            blue_background = float(
                np.median(
                    blue[np.isfinite(blue)]
                )
            )

            neutral_background = float(
                np.median(
                    [
                        red_background,
                        green_background,
                        blue_background,
                    ]
                )
            )

            red = (
                red
                + neutral_background
                - red_background
            )

            green = (
                green
                + neutral_background
                - green_background
            )

            blue = (
                blue
                + neutral_background
                - blue_background
            )

            #
            # IMPORTANT:
            #
            # One global black/white level is used for all
            # three channels. This preserves the real color
            # relationships between R, G and B.
            #
            source_values = image_data[
                np.isfinite(image_data)
            ]

            #
            # Automatic astronomical preview stretch.
            #
            # The median estimates the sky background.
            # MAD gives a robust estimate of background noise,
            # without being dominated by bright stars.
            #
            background = float(
                np.median(source_values)
            )

            mad = float(
                np.median(
                    np.abs(
                        source_values - background
                    )
                )
            )

            noise = 1.4826 * mad

            if noise <= 0.0:
                p16, p84 = np.percentile(
                    source_values,
                    [16.0, 84.0],
                )

                noise = max(
                    float(
                        (p84 - p16) / 2.0
                    ),
                    1.0,
                )

            high_percentile = float(
                np.percentile(
                    source_values,
                    99.8,
                )
            )

            #
            # Keep the sky background dark while preserving
            # faint sources slightly above the background.
            #
            black = (
                background
                - 0.5 * noise
            )

            white = max(
                high_percentile,
                background
                + 8.0 * noise,
            )

            if white <= black:
                black = float(
                    source_values.min()
                )

                white = float(
                    source_values.max()
                )

            if white <= black:
                white = black + 1.0

            def stretch(channel):
                value = (
                    channel - black
                ) / (
                    white - black
                )

                value = np.clip(
                    value,
                    0.0,
                    1.0,
                )

                #
                # Asinh stretch is well suited to astronomical
                # previews: faint stars are enhanced while
                # bright stars are less easily saturated.
                #
                stretch_strength = 8.0

                value = (
                    np.arcsinh(
                        value * stretch_strength
                    )
                    / np.arcsinh(
                        stretch_strength
                    )
                )

                return value

            red = stretch(red)
            green = stretch(green)
            blue = stretch(blue)

            rgb = np.stack(
                [
                    red,
                    green,
                    blue,
                ],
                axis=-1,
            )

            #
            # Small saturation boost for the tablet preview.
            # This does not affect the FITS file.
            #
            luminance = (
                0.2126 * rgb[..., 0]
                + 0.7152 * rgb[..., 1]
                + 0.0722 * rgb[..., 2]
            )

            saturation = 1.08

            rgb = (
                luminance[..., None]
                + saturation
                * (
                    rgb
                    - luminance[..., None]
                )
            )

            rgb = np.clip(
                rgb,
                0.0,
                1.0,
            )

        else:
            #
            # Monochrome fallback when no Bayer pattern
            # is declared by the FITS header.
            #
            source_values = image_data[
                np.isfinite(image_data)
            ]

            background = float(
                np.median(source_values)
            )

            mad = float(
                np.median(
                    np.abs(
                        source_values - background
                    )
                )
            )

            noise = 1.4826 * mad

            if noise <= 0.0:
                p16, p84 = np.percentile(
                    source_values,
                    [16.0, 84.0],
                )

                noise = max(
                    float(
                        (p84 - p16) / 2.0
                    ),
                    1.0,
                )

            high_percentile = float(
                np.percentile(
                    source_values,
                    99.8,
                )
            )

            black = (
                background
                - 0.5 * noise
            )

            white = max(
                high_percentile,
                background
                + 8.0 * noise,
            )

            if white <= black:
                black = float(
                    source_values.min()
                )

                white = float(
                    source_values.max()
                )

            if white <= black:
                white = black + 1.0

            mono = (
                image_data - black
            ) / (
                white - black
            )

            mono = np.clip(
                mono,
                0.0,
                1.0,
            )

            stretch_strength = 8.0

            mono = (
                np.arcsinh(
                    mono * stretch_strength
                )
                / np.arcsinh(
                    stretch_strength
                )
            )

            rgb = np.stack(
                [
                    mono,
                    mono,
                    mono,
                ],
                axis=-1,
            )

        jpeg_data = (
            rgb * 255.0
        ).astype(
            np.uint8
        )

        preview = Image.fromarray(
            jpeg_data
        )

        max_width = 1600

        if preview.width > max_width:

            new_height = round(
                preview.height
                * max_width
                / preview.width
            )

            preview = preview.resize(
                (
                    max_width,
                    new_height,
                ),
                Image.Resampling.LANCZOS,
            )

        buffer = BytesIO()

        preview.save(
            buffer,
            format="JPEG",
            quality=92,
            optimize=True,
        )

        #
        # Simple preview diagnostic.
        #
        # This does not attempt to identify stars yet.
        # It only evaluates how strongly the image differs
        # from its estimated background noise.
        #
        p99 = float(
            np.percentile(
                source_values,
                99.0,
            )
        )

        contrast_sigma = max(
            0.0,
            (
                p99 - background
            )
            / max(
                noise,
                1.0e-6,
            ),
        )

        if contrast_sigma < 3.0:
            preview_status = "uniform"
        elif contrast_sigma < 6.0:
            preview_status = "weak-signal"
        else:
            preview_status = "structured"

        return Response(
            content=buffer.getvalue(),
            media_type="image/jpeg",
            headers={
                "Cache-Control": "no-store",
                "X-StellarPilot-Source":
                    latest.name,
                "X-StellarPilot-Bayer":
                    bayer_pattern or "NONE",
                "X-StellarPilot-Preview":
                    "auto-background-asinh",
                "X-StellarPilot-Preview-Status":
                    preview_status,
                "X-StellarPilot-Preview-Background":
                    f"{background:.3f}",
                "X-StellarPilot-Preview-Noise":
                    f"{noise:.3f}",
                "X-StellarPilot-Preview-Contrast":
                    f"{contrast_sigma:.3f}",
            },
        )

    except Exception as exc:

        logger.exception(
            "Unable to generate camera preview"
        )

        raise HTTPException(
            status_code=500,
            detail=(
                "Camera preview generation error: "
                f"{exc}"
            ),
        ) from exc


@app.post("/mount/goto")
def mount_goto(payload: GotoPayload):
    gps = gps_service.status()
    system = system_service.status()

    timestamp_utc, time_source = _resolve_time(
        gps,
        system,
    )

    # Un GOTO astronomique ne doit pas etre lance
    # avec une heure potentiellement fausse.
    if (
        timestamp_utc is None
        or time_source not in {"gps", "android"}
    ):
        return {
            "status": "error",
            "detail": (
                "Aucune source d'heure fiable "
                "disponible pour initialiser OnStep"
            ),
            "time_source": time_source,
        }

    offset_minutes = (
        state.client_timezone_offset_minutes
    )

    # En cas de GPS disponible mais sans information
    # de fuseau provenant d'Android, utilise le fuseau
    # configure sur le Raspberry Pi.
    if offset_minutes is None:
        try:
            value = timestamp_utc

            if value.endswith("Z"):
                value = (
                    value[:-1]
                    + "+00:00"
                )

            instant = datetime.fromisoformat(
                value
            )

            if instant.tzinfo is None:
                instant = instant.replace(
                    tzinfo=timezone.utc
                )

            local_offset = (
                instant
                .astimezone()
                .utcoffset()
            )

            offset_minutes = int(
                (
                    local_offset.total_seconds()
                    if local_offset is not None
                    else 0
                )
                / 60
            )

        except ValueError:
            offset_minutes = 0

    time_sync = indi_service.sync_mount_time(
        utc_iso=timestamp_utc,
        timezone_offset_minutes=offset_minutes,
    )

    if time_sync.get("status") not in {
        "ok",
    }:
        return {
            "status": "error",
            "detail": (
                "Synchronisation de l'heure "
                "OnStep impossible"
            ),
            "time_source": time_source,
            "time_sync": time_sync,
        }

    result = indi_service.goto(
        payload.ra,
        payload.dec,
    )

    result["time_source"] = time_source
    result["time_sync"] = time_sync

    return result


@app.get("/mount/status")
def mount_status():
    return indi_service.mount_status()


@app.post("/solve")
def solve(payload: SolvePayload):
    return plate_solver.solve_robust(payload.image)


@app.get("/catalog/status")
def catalog_status():
    return catalog_service.status()


@app.get("/catalog/search")
def catalog_search(
    q: str,
    limit: int = 20,
    object_type: str | None = None,
):
    return catalog_service.search(
        query=q,
        limit=limit,
        object_type=object_type,
    )


@app.get("/catalog/{object_id}")
def catalog_object(
    object_id: int,
):
    result = catalog_service.get(object_id)

    if result is None:
        raise HTTPException(
            status_code=404,
            detail="Catalog object not found",
        )

    return result


@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    """
    Maintient le canal temps r?el entre StellarPilot Android
    et StellarPilot Server.

    La fermeture normale d'un client est trait?e explicitement
    afin qu'une perte Wi-Fi, une mise en veille Android ou un
    changement de r?seau ne g?n?re pas une erreur serveur inutile.
    """
    await websocket.accept()

    client = websocket.client

    client_label = (
        f"{client.host}:{client.port}"
        if client is not None
        else "client inconnu"
    )

    logger.info(
        "WebSocket connect? : %s",
        client_label,
    )

    await websocket.send_json(
        {
            "event": "connected",
            "service": "stellarpilot",
            "mode": "device",
            "protocol": "proto-1",
        }
    )

    try:
        while True:
            message = await websocket.receive_text()

            # Les messages applicatifs StellarPilot utilisent JSON.
            # Pendant la transition du ancienne version vers le serveur,
            # les anciennes trames restent accept?es.
            try:
                payload = json.loads(message)
            except (json.JSONDecodeError, TypeError):
                payload = None

            if (
                isinstance(payload, dict)
                and payload.get("type") == "hello"
            ):
                await websocket.send_json(
                    {
                        "event": "welcome",
                        "service": "stellarpilot",
                        "mode": "device",
                        "protocol": "proto-1",
                        "client": payload.get(
                            "client",
                            "unknown",
                        ),
                    }
                )
                continue

            await websocket.send_json(
                {
                    "event": "echo",
                    "message": message,
                }
            )

    except WebSocketDisconnect:
        logger.info(
            "WebSocket d?connect? : %s",
            client_label,
        )

    except Exception:
        logger.exception(
            "Erreur WebSocket pour %s",
            client_label,
        )
