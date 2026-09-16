from fastapi import HTTPException
from pydantic import BaseModel, Field

from app import _main_core as _core
from app.imaging import darks as dark_module
from app.imaging.darks import (
    capture_dark,
    dark_library,
    dark_status,
    start_dark_session,
)


# V0.6.9 policy: persist the same ±5 °C tolerance that the stacking selector
# actually applies at runtime. This also keeps newly created Master metadata
# consistent with the diagnostics shown to the observer.
dark_module.TEMPERATURE_TOLERANCE_C = 5.0


class DarkStartPayload(BaseModel):
    exposure_s: float = Field(default=4.0, gt=0, le=3600)
    requested_count: int = Field(default=10, ge=3, le=100)


app = _core.app


@app.post("/calibration/darks")
def create_dark_session(payload: DarkStartPayload):
    return start_dark_session(
        exposure_s=payload.exposure_s,
        requested_count=payload.requested_count,
    )


@app.get("/calibration/darks/library")
def get_dark_library():
    return dark_library()


@app.get("/calibration/darks/{session_id}")
def get_dark_session(session_id: str):
    try:
        return dark_status(session_id)
    except KeyError as exc:
        raise HTTPException(status_code=404, detail="Dark session introuvable") from exc


@app.post("/calibration/darks/{session_id}/capture")
def capture_dark_frame_route(session_id: str):
    try:
        result = capture_dark(session_id)
    except KeyError as exc:
        raise HTTPException(status_code=404, detail="Dark session introuvable") from exc

    if result.get("status") == "error":
        raise HTTPException(
            status_code=422,
            detail=result.get("detail", "Capture dark impossible"),
        )
    return result
