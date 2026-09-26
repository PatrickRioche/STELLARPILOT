from fastapi import HTTPException
from pydantic import BaseModel, Field

from app import _main_core as _core
from app.imaging.calibration_r6 import install as install_r6_calibration
from app.imaging.target_guard_r6 import install as install_target_guard
from app.imaging.flats import (
    capture_flat,
    flat_library,
    flat_status,
    start_flat_session,
)


class FlatStartPayload(BaseModel):
    exposure_s: float = Field(default=0.2, gt=0, le=30.0)
    requested_count: int = Field(default=20, ge=5, le=100)


app = _core.app
install_r6_calibration()
install_target_guard()


@app.post("/calibration/flats")
def create_flat_session(payload: FlatStartPayload):
    return start_flat_session(
        exposure_s=payload.exposure_s,
        requested_count=payload.requested_count,
    )


@app.get("/calibration/flats/library")
def get_flat_library():
    return flat_library()


@app.get("/calibration/flats/{session_id}")
def get_flat_session(session_id: str):
    try:
        return flat_status(session_id)
    except KeyError as exc:
        raise HTTPException(status_code=404, detail="Flat session introuvable") from exc


@app.post("/calibration/flats/{session_id}/capture")
def capture_flat_frame_route(session_id: str):
    try:
        result = capture_flat(session_id)
    except KeyError as exc:
        raise HTTPException(status_code=404, detail="Flat session introuvable") from exc

    if result.get("status") == "error":
        raise HTTPException(
            status_code=422,
            detail=result.get("detail", "Capture flat impossible"),
        )
    return result
