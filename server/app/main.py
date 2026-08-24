import json
import logging
from concurrent.futures import ThreadPoolExecutor
from time import perf_counter

from fastapi import (
    FastAPI,
    HTTPException,
    WebSocket,
    WebSocketDisconnect,
)
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
    version="0.4-proto",
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


@app.post("/mount/goto")
def mount_goto(payload: GotoPayload):
    return indi_service.goto(payload.ra, payload.dec)


@app.post("/solve")
def solve(payload: SolvePayload):
    return plate_solver.solve(payload.image)



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
