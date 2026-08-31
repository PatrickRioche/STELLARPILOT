from __future__ import annotations

import json
import os
import shutil
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from app.imaging.sessions import CaptureSessionService, capture_session_service


SERVER_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_ASTROMETRY_ROOT = SERVER_ROOT / "data" / "astrometry"


def _astrometry_root(archive_root: Path | str | None = None) -> Path:
    return Path(
        archive_root
        or os.environ.get("STELLARPILOT_ASTROMETRY_ROOT")
        or DEFAULT_ASTROMETRY_ROOT
    )


def _write_json_atomic(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    temporary.replace(path)


def _copy_atomic(source: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(destination.suffix + ".tmp")
    shutil.copy2(source, temporary)
    temporary.replace(destination)


def _archive_initial_capture(
    *,
    metadata: dict[str, Any],
    image: Path,
    preview_path: Path,
    capture_result: dict[str, Any],
    service: CaptureSessionService,
    archive_root: Path | str | None,
    attempt: int,
) -> dict[str, Any]:
    existing = metadata.get("initial_astrometry_archive")
    if isinstance(existing, dict) and existing.get("fits"):
        return existing

    captured_at = datetime.now(timezone.utc)
    root = _astrometry_root(archive_root)
    archive_dir = (
        root
        / captured_at.strftime("%Y")
        / captured_at.strftime("%Y-%m-%d")
        / metadata["id"]
    )
    archive_dir.mkdir(parents=True, exist_ok=True)

    fits_path = archive_dir / "initial.fits"
    persistent_preview = archive_dir / "initial.jpg"
    metadata_path = archive_dir / "metadata.json"

    # Never overwrite the first scientific frame. This also makes a retry
    # safe if the Pi lost power after copying the FITS but before updating
    # session.json.
    if not fits_path.exists():
        _copy_atomic(image, fits_path)

    if not persistent_preview.exists():
        try:
            if preview_path.exists():
                _copy_atomic(preview_path, persistent_preview)
            else:
                persistent_preview.write_bytes(
                    service._fits_preview_bytes(fits_path)
                )
        except Exception:
            # The scientific FITS is the mandatory persistent artifact.
            # A preview failure must not destroy or replace it.
            pass

    capture_time_utc = captured_at.isoformat(timespec="seconds")
    archive_metadata = {
        "session_id": metadata["id"],
        "capture_time_utc": capture_time_utc,
        "centering_attempt": int(attempt),
        "target": {
            "name": metadata["target"].get("name"),
            "ra_hours": metadata["target"].get("ra_hours"),
            "dec_deg": metadata["target"].get("dec_deg"),
            "object_type": metadata["target"].get("object_type"),
            "tracking_mode": metadata["target"].get("tracking_mode"),
        },
        "exposure_s": metadata["setup"].get("exposure_s"),
        "camera": capture_result.get("camera"),
        "files": {
            "fits": "initial.fits",
            "preview": (
                "initial.jpg" if persistent_preview.exists() else None
            ),
        },
        "solver": {
            "status": None,
            "solver": None,
            "detail": None,
            "solve_ra_deg": None,
            "solve_dec_deg": None,
            "pixel_scale_arcsec": None,
            "centering_error_arcsec": None,
        },
    }

    if not metadata_path.exists():
        _write_json_atomic(metadata_path, archive_metadata)
    else:
        try:
            previous = json.loads(
                metadata_path.read_text(encoding="utf-8")
            )
            capture_time_utc = previous.get(
                "capture_time_utc",
                capture_time_utc,
            )
            attempt = int(previous.get("centering_attempt", attempt))
            previous.setdefault("files", {})["preview"] = (
                "initial.jpg" if persistent_preview.exists() else None
            )
            _write_json_atomic(metadata_path, previous)
        except (OSError, ValueError, TypeError, json.JSONDecodeError):
            _write_json_atomic(metadata_path, archive_metadata)

    return {
        "directory": str(archive_dir),
        "fits": str(fits_path),
        "preview": (
            str(persistent_preview)
            if persistent_preview.exists()
            else None
        ),
        "metadata": str(metadata_path),
        "capture_time_utc": capture_time_utc,
        "centering_attempt": int(attempt),
    }


def _update_initial_archive_after_solve(
    metadata: dict[str, Any],
    centering: dict[str, Any],
) -> None:
    archive = metadata.get("initial_astrometry_archive")
    if not isinstance(archive, dict):
        return

    archive_attempt = int(archive.get("centering_attempt", 0) or 0)
    current_attempt = int(centering.get("attempts", 0) or 0)
    if archive_attempt != current_attempt:
        return

    metadata_path_value = archive.get("metadata")
    if not metadata_path_value:
        return

    metadata_path = Path(metadata_path_value)
    if not metadata_path.exists():
        return

    try:
        archive_metadata = json.loads(
            metadata_path.read_text(encoding="utf-8")
        )
    except (OSError, json.JSONDecodeError):
        return

    archive_metadata["solver"] = {
        "status": centering.get("solver_status"),
        "solver": centering.get("solver"),
        "detail": centering.get("solver_detail"),
        "solve_ra_deg": centering.get("solve_ra_deg"),
        "solve_dec_deg": centering.get("solve_dec_deg"),
        "pixel_scale_arcsec": centering.get("pixel_scale_arcsec"),
        "centering_error_arcsec": centering.get("error_arcsec"),
    }
    _write_json_atomic(metadata_path, archive_metadata)


def capture_centering_frame(
    session_id: str,
    service: CaptureSessionService | None = None,
    archive_root: Path | str | None = None,
) -> dict[str, Any]:
    """Capture one centering frame and expose its preview immediately.

    The first centering exposure is copied to persistent storage below
    ``stellarpilot-server/data/astrometry`` before plate solving starts.
    Later corrective centering frames remain in the temporary session tree.
    """
    service = service or capture_session_service

    with service._lock:
        metadata = service._read(session_id)
        attempt = int(metadata["centering"].get("attempts", 0)) + 1

    result = service._capture_into(
        metadata,
        f"center_{attempt:03d}",
    )

    if result.get("status") != "captured":
        return {
            "status": "error",
            "detail": result.get("detail", "Capture impossible"),
            "session": metadata,
        }

    image = Path(result["image"])
    preview_path = (
        service._session_dir(session_id)
        / "previews"
        / "latest.jpg"
    )

    try:
        preview_path.write_bytes(
            service._fits_preview_bytes(image)
        )
        metadata["preview"] = str(preview_path)
    except Exception:
        metadata["preview"] = None

    metadata["centering"] = {
        "status": "captured",
        "attempts": attempt,
        "error_arcsec": None,
        "solve_ra_deg": None,
        "solve_dec_deg": None,
        "correction_ra_hours": None,
        "correction_dec_deg": None,
        "image": str(image),
        "solver_status": None,
        "solver": None,
        "solver_detail": None,
        "pixel_scale_arcsec": None,
    }
    metadata["state"] = "framing"

    if not metadata.get("initial_astrometry_archive"):
        try:
            metadata["initial_astrometry_archive"] = (
                _archive_initial_capture(
                    metadata=metadata,
                    image=image,
                    preview_path=preview_path,
                    capture_result=result,
                    service=service,
                    archive_root=archive_root,
                    attempt=attempt,
                )
            )
            metadata.pop("initial_astrometry_archive_error", None)
        except Exception as exc:
            metadata["initial_astrometry_archive_error"] = str(exc)
            with service._lock:
                service._write(metadata)
            return {
                "status": "error",
                "detail": (
                    "Archivage persistant de la première astrométrie "
                    f"impossible : {exc}"
                ),
                "session": metadata,
            }

    with service._lock:
        service._write(metadata)

    return {
        "status": "captured",
        "session": metadata,
    }


def solve_centering_frame(
    session_id: str,
    service: CaptureSessionService | None = None,
) -> dict[str, Any]:
    """Plate-solve the most recently captured centering frame."""
    service = service or capture_session_service

    with service._lock:
        metadata = service._read(session_id)

    current = metadata.get("centering") or {}
    image = current.get("image") or metadata.get("last_frame")

    if not image:
        return {
            "status": "error",
            "detail": "Aucune image de centrage à résoudre",
            "session": metadata,
        }

    target = metadata["target"]
    solution = service.solver.solve_robust(
        image,
        ra_hint=target["ra_hours"] * 15.0,
        dec_hint=target["dec_deg"],
    )

    centering = {
        "status": "unsolved",
        "attempts": int(current.get("attempts", 0)),
        "error_arcsec": None,
        "solve_ra_deg": solution.get("ra"),
        "solve_dec_deg": solution.get("dec"),
        "correction_ra_hours": None,
        "correction_dec_deg": None,
        "image": image,
        "solver_status": solution.get("status"),
        "solver": solution.get("solver"),
        "solver_detail": solution.get("detail"),
        "pixel_scale_arcsec": solution.get("pixel_scale_arcsec"),
    }

    if (
        solution.get("status") == "solved"
        and solution.get("ra") is not None
        and solution.get("dec") is not None
    ):
        centering.update(
            service._centering_result(
                target["ra_hours"],
                target["dec_deg"],
                float(solution["ra"]),
                float(solution["dec"]),
                metadata["setup"]["centering_tolerance_arcsec"],
            )
        )

    metadata["centering"] = centering
    metadata["state"] = (
        "centered"
        if centering["status"] == "centered"
        else "framing"
    )

    # Only the solve of the first exposure enriches the persistent archive.
    # Later corrective solves must never change the initial capture record.
    _update_initial_archive_after_solve(metadata, centering)

    with service._lock:
        service._write(metadata)

    return {
        "status": centering["status"],
        "centering": centering,
        "session": metadata,
    }
