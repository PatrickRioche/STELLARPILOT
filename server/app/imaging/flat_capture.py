from __future__ import annotations

import subprocess
import time
from pathlib import Path
from typing import Any

from app.imaging.dark_capture import _connected_camera_name, _set_property


def capture_flat_frame(
    exposure_s: float,
    *,
    output_dir: str | Path,
    prefix: str,
) -> dict[str, Any]:
    """Capture one flat frame while preserving gain/offset/binning/ROI.

    Player One/INDI normally exposes FRAME_FLAT. Some drivers do not; in that
    case StellarPilot falls back to FRAME_LIGHT because a uniformly illuminated
    LIGHT is still a valid raw flat for our calibration pipeline.
    """
    camera_name = _connected_camera_name()
    if camera_name is None:
        return {
            "status": "error",
            "mode": "device",
            "detail": "Aucune caméra INDI connectée",
        }

    capture_dir = Path(output_dir)
    capture_dir.mkdir(parents=True, exist_ok=True)

    frame_type = "flat"
    try:
        _set_property(f"{camera_name}.UPLOAD_MODE.UPLOAD_LOCAL=On")
        try:
            _set_property(f"{camera_name}.CCD_FRAME_TYPE.FRAME_FLAT=On")
        except RuntimeError:
            _set_property(f"{camera_name}.CCD_FRAME_TYPE.FRAME_LIGHT=On")
            frame_type = "light-flat"
        _set_property(f"{camera_name}.UPLOAD_SETTINGS.UPLOAD_DIR={capture_dir}")
        _set_property(f"{camera_name}.UPLOAD_SETTINGS.UPLOAD_PREFIX={prefix}")
        _set_property(
            f"{camera_name}.CCD_EXPOSURE.CCD_EXPOSURE_VALUE={float(exposure_s)}"
        )
    except (OSError, subprocess.SubprocessError, RuntimeError) as exc:
        return {
            "status": "error",
            "mode": "device",
            "camera": camera_name,
            "frame_type": frame_type,
            "detail": str(exc),
        }

    deadline = time.monotonic() + float(exposure_s) + 20.0
    stable_path: Path | None = None
    stable_size: int | None = None
    stable_mtime_ns: int | None = None
    stable_count = 0

    while time.monotonic() < deadline:
        candidates = sorted(
            (
                item
                for item in capture_dir.iterdir()
                if item.is_file()
                and item.name.startswith(prefix)
                and item.suffix.lower() in {".fits", ".fit", ".fts"}
            ),
            key=lambda item: item.stat().st_mtime_ns,
            reverse=True,
        )

        if candidates:
            image = candidates[0]
            try:
                stat = image.stat()
            except OSError:
                time.sleep(0.25)
                continue

            if (
                image == stable_path
                and stat.st_size == stable_size
                and stat.st_mtime_ns == stable_mtime_ns
            ):
                stable_count += 1
            else:
                stable_path = image
                stable_size = stat.st_size
                stable_mtime_ns = stat.st_mtime_ns
                stable_count = 0

            if stable_count >= 2 and stat.st_size >= 2880 and stat.st_size % 2880 == 0:
                try:
                    with image.open("rb") as handle:
                        header = handle.read(9)
                except OSError:
                    time.sleep(0.25)
                    continue

                if header == b"SIMPLE  =":
                    return {
                        "status": "captured",
                        "mode": "device",
                        "camera": camera_name,
                        "frame_type": frame_type,
                        "exposure_s": float(exposure_s),
                        "image": str(image),
                        "size_bytes": stat.st_size,
                        "fits_valid": True,
                        "write_stable": True,
                        "storage": "calibration-flat",
                    }

        time.sleep(0.25)

    return {
        "status": "error",
        "mode": "device",
        "camera": camera_name,
        "frame_type": frame_type,
        "exposure_s": float(exposure_s),
        "detail": "Timeout en attente du fichier FITS flat",
    }
