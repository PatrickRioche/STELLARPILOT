from __future__ import annotations

import json
import shutil
from pathlib import Path
from typing import Any

from app.imaging.sessions import CaptureSessionService


_ORIGINAL_FINALIZE = CaptureSessionService.finalize


def _copy_fits_tree(source: Path, destination: Path) -> int:
    destination.mkdir(parents=True, exist_ok=True)
    count = 0
    if not source.exists():
        return count

    for path in sorted(source.glob("*.fits")):
        if not path.is_file():
            continue
        shutil.copy2(path, destination / path.name)
        count += 1
    return count


def _archive_siril_assets(
    service: CaptureSessionService,
    session_id: str,
    metadata: dict[str, Any],
) -> dict[str, Any]:
    session_dir = service._session_dir(session_id)
    gallery_value = metadata.get("gallery_path")
    if not gallery_value:
        raise RuntimeError("Galerie absente après finalisation")

    gallery_dir = Path(gallery_value)
    lights_dir = gallery_dir / "lights"
    accepted_count = _copy_fits_tree(
        session_dir / "accepted",
        lights_dir / "accepted",
    )
    rejected_count = _copy_fits_tree(
        session_dir / "rejected",
        lights_dir / "rejected",
    )

    calibration_dir = gallery_dir / "calibration"
    calibration_dir.mkdir(parents=True, exist_ok=True)

    calibration = metadata.get("calibration") or {}
    master_source_value = calibration.get("master_dark")
    master_archived = None
    hot_pixels_archived = None

    if master_source_value:
        master_source = Path(master_source_value)
        if master_source.exists():
            master_destination = calibration_dir / "master_dark.fits"
            shutil.copy2(master_source, master_destination)
            master_archived = str(master_destination)

            hot_source = master_source.parent / "hot_pixels.fits"
            if hot_source.exists():
                hot_destination = calibration_dir / "hot_pixels.fits"
                shutil.copy2(hot_source, hot_destination)
                hot_pixels_archived = str(hot_destination)

    manifest = {
        "format": "stellarpilot-siril-v1",
        "session_id": session_id,
        "target": metadata.get("target"),
        "setup": metadata.get("setup"),
        "counts": metadata.get("counts"),
        "integration_seconds": metadata.get("integration_seconds"),
        "accepted_raw_fits": accepted_count,
        "rejected_raw_fits": rejected_count,
        "rejected_by_reason": (metadata.get("counts") or {}).get(
            "rejected_by_reason",
            {},
        ),
        "master_dark": master_archived,
        "hot_pixel_map": hot_pixels_archived,
        "note": (
            "Les LIGHT de lights/accepted et lights/rejected sont les FITS "
            "bruts d'origine, conservés pour un retraitement externe (Siril)."
        ),
    }
    manifest_path = gallery_dir / "siril_manifest.json"
    manifest_path.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    return {
        "status": "ready",
        "format": manifest["format"],
        "accepted_raw_fits": accepted_count,
        "rejected_raw_fits": rejected_count,
        "master_dark_archived": master_archived is not None,
        "hot_pixel_map_archived": hot_pixels_archived is not None,
        "manifest": str(manifest_path),
        "lights_accepted": str(lights_dir / "accepted"),
        "lights_rejected": str(lights_dir / "rejected"),
    }


def _finalize_with_raw_archive(
    self: CaptureSessionService,
    session_id: str,
) -> dict[str, Any]:
    result = _ORIGINAL_FINALIZE(self, session_id)
    if result.get("status") != "completed":
        return result

    metadata = result.get("session") or self._read(session_id)

    try:
        archive = _archive_siril_assets(self, session_id, metadata)
    except Exception as exc:
        archive = {
            "status": "warning",
            "detail": f"Archivage RAW/Siril incomplet : {exc}",
        }

    metadata["siril_archive"] = archive
    self._write(metadata)

    gallery_value = metadata.get("gallery_path")
    if gallery_value:
        gallery_metadata = Path(gallery_value) / "session.json"
        gallery_metadata.write_text(
            json.dumps(metadata, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )

    result["session"] = metadata
    result["siril_archive"] = archive
    return result


# Loaded once by app.indi.coordinates during app.main startup. Keep the patch
# idempotent for test imports/reloads.
if CaptureSessionService.finalize is not _finalize_with_raw_archive:
    CaptureSessionService.finalize = _finalize_with_raw_archive
