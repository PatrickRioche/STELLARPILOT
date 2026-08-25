import json
import logging
from concurrent.futures import ThreadPoolExecutor
from io import BytesIO
from pathlib import Path
from time import perf_counter

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
from app.config import get_mode
from app.gps.service import gps_service
from app.indi.service import indi_service
from app.session.state import state
from app.sky.service import sky_service
from app.sky.objects import sky_objects_service
from app.solving.service import plate_solver
from app.system.service import system_service


logger = logging.getLogger("stellarpilot.websocket")


app = FastAPI(
    title="StellarPilot Prototype API",
    version="0.5.0-poc",
)


class LocationPayload(BaseModel):
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    altitude: float | None = None
    timestamp: str


class MountTypePayload(BaseModel):
    mount_type: str


class CapturePayload(BaseModel):
    exposure_s: float = Field(gt=0, le=3600)


class GotoPayload(BaseModel):
    ra: float
    dec: float = Field(ge=-90, le=90)


class SolvePayload(BaseModel):
    image: str


@app.get("/health")
def health():
    return {
        "service": "stellarpilot",
        "status": "ok",
        "mode": get_mode(),
        "protocol": "proto-1",
    }


@app.get("/status")
def status():
    """
    Construit l'?tat global du prototype.

    Les sous-syst?mes ind?pendants sont interrog?s en parall?le.
    Une cam?ra lente, le GPS ou chrony ne doivent pas bloquer
    successivement toute la r?ponse /status.
    """
    mode = get_mode()
    started_at = perf_counter()

    with ThreadPoolExecutor(
        max_workers=4,
        thread_name_prefix="stellarpilot-status",
    ) as executor:
        indi_future = executor.submit(
            indi_service.device_status
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

        indi = indi_future.result()
        gps = gps_future.result()
        system = system_future.result()
        catalog = catalog_future.result()

    status_duration_ms = int(
        (perf_counter() - started_at) * 1000
    )

    if mode == "device":
        if (
            gps.get("status") == "fix"
            and gps.get("latitude") is not None
            and gps.get("longitude") is not None
        ):
            session_latitude = gps["latitude"]
            session_longitude = gps["longitude"]
            session_altitude = gps["altitude"]
            session_timestamp = system["datetime"]
        else:
            session_latitude = state.latitude
            session_longitude = state.longitude
            session_altitude = state.altitude
            session_timestamp = (
                state.timestamp
                if state.timestamp is not None
                else system["datetime"]
            )
    else:
        session_latitude = (
            state.latitude
            if state.latitude is not None
            else gps["latitude"]
        )
        session_longitude = (
            state.longitude
            if state.longitude is not None
            else gps["longitude"]
        )
        session_altitude = (
            state.altitude
            if state.altitude is not None
            else gps["altitude"]
        )
        session_timestamp = (
            state.timestamp
            if state.timestamp is not None
            else system["datetime"]
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
        # afin de ne pas casser les clients issus du POC.
        "poc": True,
        "prototype": True,
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

    elif (
        gps.get("latitude") is not None
        and gps.get("longitude") is not None
    ):
        observer_latitude = gps["latitude"]
        observer_longitude = gps["longitude"]
        location_source = "gps"

    elif (
        state.latitude is not None
        and state.longitude is not None
    ):
        observer_latitude = state.latitude
        observer_longitude = state.longitude
        location_source = "manual"

    else:
        return {
            "status": "location_required",
            "detail": (
                "No GPS fix or manual location "
                "is currently available"
            ),
        }

    return sky_service.bright_stars(
        latitude=observer_latitude,
        longitude=observer_longitude,
        at=at,
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
    limit: int = 100,
):
    gps = gps_service.status()

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

    elif (
        gps.get("latitude") is not None
        and gps.get("longitude") is not None
    ):
        observer_latitude = gps["latitude"]
        observer_longitude = gps["longitude"]
        location_source = "gps"

    elif (
        state.latitude is not None
        and state.longitude is not None
    ):
        observer_latitude = state.latitude
        observer_longitude = state.longitude
        location_source = "manual"

    else:
        return {
            "status": "location_required",
            "detail": (
                "No GPS fix or manual location "
                "is currently available"
            ),
        }

    try:
        return sky_objects_service.objects(
            latitude=observer_latitude,
            longitude=observer_longitude,
            at=at,
            category=category,
            query=q,
            min_altitude=min_altitude,
            direction=direction,
            constellation=constellation,
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


@app.post("/system/location")
def set_location(payload: LocationPayload):
    state.latitude = payload.latitude
    state.longitude = payload.longitude
    state.altitude = payload.altitude
    state.timestamp = payload.timestamp

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
            # IMPORTANT:
            #
            # One global black/white level is used for all
            # three channels. This preserves the real color
            # relationships between R, G and B.
            #
            source_values = image_data[
                np.isfinite(image_data)
            ]

            black, white = np.percentile(
                source_values,
                [0.5, 99.7],
            )

            black = float(black)
            white = float(white)

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

                # Gamma-like stretch for screen preview.
                value = np.sqrt(
                    value
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

            saturation = 1.20

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

            black, white = np.percentile(
                source_values,
                [0.5, 99.7],
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

            mono = np.sqrt(
                mono
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
                    "color-global-stretch",
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
    return indi_service.goto(payload.ra, payload.dec)


@app.post("/solve")
def solve(payload: SolvePayload):
    return plate_solver.solve(payload.image)


@app.post("/demo/m103/solve")
def solve_demo_m103():
    demo_image = (
        Path(__file__).resolve().parents[1]
        / "demo"
        / "M103.fits"
    )

    started_at = perf_counter()

    result = plate_solver.solve(
        str(demo_image),
        ra_hint=23.3458,
        dec_hint=60.6500,
        radius_deg=5.0,
        downsample=2,
        timeout_s=15,
    )

    result["demo"] = "M103"
    result["reference_ra"] = 23.3458
    result["reference_dec"] = 60.6500
    result["solve_duration_ms"] = int(
        (perf_counter() - started_at) * 1000
    )

    return result



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
            "mode": get_mode(),
            "protocol": "proto-1",
        }
    )

    try:
        while True:
            message = await websocket.receive_text()

            # Les messages applicatifs StellarPilot utilisent JSON.
            # Pendant la transition du POC vers le prototype,
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
                        "mode": get_mode(),
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
