from fastapi import FastAPI, WebSocket
from pydantic import BaseModel, Field

from app.indi.service import indi_service
from app.session.state import state
from app.solving.service import plate_solver

app = FastAPI(title="StellarPilot POC API", version="0.1-poc")


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


@app.get("/status")
def status():
    return {
        "service": "stellarpilot",
        "status": "ok",
        "poc": True,
        "session": {
            "latitude": state.latitude,
            "longitude": state.longitude,
            "altitude": state.altitude,
            "timestamp": state.timestamp,
            "mount_type": state.mount_type,
        },
    }


@app.get("/devices")
def devices():
    return {"devices": indi_service.list_devices()}


@app.post("/system/location")
def set_location(payload: LocationPayload):
    state.latitude = payload.latitude
    state.longitude = payload.longitude
    state.altitude = payload.altitude
    state.timestamp = payload.timestamp
    return {"status": "ok"}


@app.post("/system/mount-type")
def set_mount_type(payload: MountTypePayload):
    value = payload.mount_type.upper()
    if value not in {"EQ", "AZ"}:
        return {"status": "error", "detail": "mount_type must be EQ or AZ"}
    state.mount_type = value
    return {"status": "ok", "mount_type": value}


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
    await websocket.send_json({"event": "connected", "service": "stellarpilot"})
    while True:
        message = await websocket.receive_text()
        await websocket.send_json({"event": "echo", "message": message})
