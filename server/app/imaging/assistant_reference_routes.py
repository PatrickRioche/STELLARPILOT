from fastapi import HTTPException, Query
from fastapi.responses import Response

from app import _main_core as _core
from app.imaging.assistant_reference import (
    astrometry_reference,
    bahtinov_reference,
    catalog,
    preview_bytes,
)


app = _core.app


@app.get("/assistant/test/reference")
def assistant_reference_catalog():
    return catalog()


@app.get("/assistant/test/reference/astrometry")
def assistant_reference_astrometry(index: int = Query(default=0, ge=0)):
    try:
        return astrometry_reference(index)
    except FileNotFoundError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.get("/assistant/test/reference/bahtinov/{kind}")
def assistant_reference_bahtinov(
    kind: str,
    index: int = Query(default=0, ge=0),
):
    if kind not in {"side_a", "optimum", "side_b"}:
        raise HTTPException(status_code=404, detail="Classe Bahtinov inconnue")
    try:
        return bahtinov_reference(kind, index)
    except FileNotFoundError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


@app.get("/assistant/test/reference/preview.jpg")
def assistant_reference_preview(image: str):
    try:
        payload = preview_bytes(image)
    except (FileNotFoundError, PermissionError, ValueError) as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    return Response(content=payload, media_type="image/jpeg")
