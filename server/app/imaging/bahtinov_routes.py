from fastapi import HTTPException
from pydantic import BaseModel

from app import _main_core as _core
from app.imaging.bahtinov import analyze_bahtinov, validate_reference_library


class BahtinovQualityPayload(BaseModel):
    image: str


app = _core.app


@app.post("/bahtinov/quality")
def bahtinov_quality(payload: BahtinovQualityPayload):
    result = analyze_bahtinov(payload.image)
    if result.get("status") != "ok":
        raise HTTPException(
            status_code=422,
            detail=result.get("detail", "Analyse Bahtinov impossible"),
        )
    return result


@app.get("/bahtinov/reference/validate")
def bahtinov_reference_validate():
    return validate_reference_library()
