from fastapi import FastAPI, WebSocket
from pydantic import BaseModel, Field

from app.config import get_mode
from app.gps.service import gps_service
from app.indi.service import indi_service
from app.session.state import state
from app.solving.service import plate_solver
from app.system.service import system_service


app = FastAPI(
    title="StellarPilot POC API",
    version="0.4-poc",
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
        "protocol": "poc-3",
    }


@app.get("/status")
def status():
    mode = get_mode()
    indi = indi_service.device_status()
    gps = gps_service.status()
    system = system_service.status()

    if mode == "device":
        session_latitude = gps["latitude"]
        session_longitude = gps["longitude"]
        session_altitude = gps["altitude"]
        session_timestamp = system["datetime"]
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
        "poc": True,
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


@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()

    await websocket.send_json(
        {
            "event": "connected",
            "service": "stellarpilot",
            "mode": get_mode(),
            "protocol": "poc-3",
        }
    )

    while True:
        message = await websocket.receive_text()

        await websocket.send_json(
            {
                "event": "echo",
                "message": message,
            }
        )
