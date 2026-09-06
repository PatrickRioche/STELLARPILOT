from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path

from app.imaging.quality import analyze_fits
from app.indi.service import indi_service


SERVER_ROOT = Path(__file__).resolve().parents[2]
DARK_ROOT = SERVER_ROOT / "data" / "calibration" / "darks"


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _session_path(session_id: str) -> Path:
    return DARK_ROOT / session_id


def _metadata_path(session_id: str) -> Path:
    return _session_path(session_id) / "session.json"


def _write(metadata: dict) -> dict:
    path = _metadata_path(metadata["id"])
    path.parent.mkdir(parents=True, exist_ok=True)
    metadata["updated_at"] = _now().isoformat(timespec="seconds")
    temporary = path.with_suffix(".json.tmp")
    temporary.write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    temporary.replace(path)
    return metadata


def _read(session_id: str) -> dict:
    path = _metadata_path(session_id)
    if not path.exists():
        raise KeyError(session_id)
    return json.loads(path.read_text(encoding="utf-8"))


def start_dark_session(
    *,
    exposure_s: float = 4.0,
    requested_count: int = 10,
) -> dict:
    if not 0.001 <= exposure_s <= 3600.0:
        raise ValueError("exposure_s hors limites")
    if not 1 <= requested_count <= 100:
        raise ValueError("requested_count hors limites")

    now = _now()
    session_id = now.strftime("%Y%m%dT%H%M%SZ") + f"_{now.microsecond:06d}"
    root = _session_path(session_id)
    (root / "raw").mkdir(parents=True, exist_ok=False)

    metadata = {
        "id": session_id,
        "created_at": now.isoformat(timespec="seconds"),
        "updated_at": now.isoformat(timespec="seconds"),
        "status": "ready",
        "exposure_s": float(exposure_s),
        "requested_count": int(requested_count),
        "captured_count": 0,
        "valid_count": 0,
        "frames": [],
        "storage": str(root),
    }
    return _write(metadata)


def capture_dark(session_id: str) -> dict:
    metadata = _read(session_id)
    requested = int(metadata["requested_count"])
    captured = int(metadata["captured_count"])

    if captured >= requested:
        metadata["status"] = "complete"
        return _write(metadata)

    index = captured + 1
    result = indi_service.capture(
        float(metadata["exposure_s"]),
        output_dir=_session_path(session_id) / "raw",
        prefix=f"dark_{index:03d}",
    )
    if result.get("status") != "captured" or not result.get("image"):
        metadata["status"] = "error"
        metadata["detail"] = result.get("detail", "Capture dark impossible")
        _write(metadata)
        return metadata

    path = Path(result["image"])
    quality = analyze_fits(str(path))
    valid = (
        path.exists()
        and path.stat().st_size > 0
        and quality.get("status") == "ok"
        and float(quality.get("saturated_percent") or 0.0) < 1.0
    )

    metadata["captured_count"] = index
    if valid:
        metadata["valid_count"] = int(metadata["valid_count"]) + 1

    metadata["frames"].append(
        {
            "index": index,
            "image": str(path),
            "size_bytes": path.stat().st_size if path.exists() else None,
            "valid": valid,
            "median": quality.get("median"),
            "background_sigma": quality.get("background_sigma"),
            "saturated_percent": quality.get("saturated_percent"),
            "maximum": quality.get("maximum"),
        }
    )
    metadata["status"] = "complete" if index >= requested else "capturing"
    metadata.pop("detail", None)
    return _write(metadata)


def dark_status(session_id: str) -> dict:
    return _read(session_id)
