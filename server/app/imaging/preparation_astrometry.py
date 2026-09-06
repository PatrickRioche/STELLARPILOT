from __future__ import annotations

import json
import logging
import os
import shutil
import threading
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from uuid import uuid4


SERVER_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_ARCHIVE_ROOT = (
    SERVER_ROOT / "data" / "astrometry" / "assistant-3"
)
logger = logging.getLogger("uvicorn.error")


class PreparationAstrometryArchive:
    """Persistent archive for Preparation assistant step 3 astrometry.

    The Preparation screen still acquires its live FITS through the legacy
    /camera/capture endpoint, whose working file is stored below the OS /tmp.
    This service copies every successful Assistant 3 acquisition immediately
    to application data, before preview generation and before plate solving.

    A capture is not considered archived until the persistent FITS has been
    verified on disk. Any verification failure is propagated to the API so the
    Android client cannot silently continue with a non-persistent capture.
    """

    def __init__(self, root: Path | str | None = None):
        self.root = Path(
            root
            or os.environ.get(
                "STELLARPILOT_PREPARATION_ASTROMETRY_ROOT"
            )
            or DEFAULT_ARCHIVE_ROOT
        )
        self._lock = threading.RLock()
        self._by_source: dict[str, Path] = {}
        self._by_name: dict[str, Path] = {}

    @staticmethod
    def _write_json_atomic(
        path: Path,
        payload: dict[str, Any],
    ) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        temporary = path.with_suffix(path.suffix + ".tmp")
        temporary.write_text(
            json.dumps(
                payload,
                ensure_ascii=False,
                indent=2,
            ),
            encoding="utf-8",
        )
        temporary.replace(path)

    @staticmethod
    def _copy_atomic(source: Path, destination: Path) -> None:
        destination.parent.mkdir(parents=True, exist_ok=True)
        temporary = destination.with_suffix(
            destination.suffix + ".tmp"
        )
        shutil.copy2(source, temporary)
        temporary.replace(destination)

    @staticmethod
    def _write_bytes_atomic(
        destination: Path,
        content: bytes,
    ) -> None:
        destination.parent.mkdir(parents=True, exist_ok=True)
        temporary = destination.with_suffix(
            destination.suffix + ".tmp"
        )
        temporary.write_bytes(content)
        temporary.replace(destination)

    @staticmethod
    def _verify_persisted_fits(
        source: Path,
        destination: Path,
    ) -> dict[str, int | bool]:
        if not destination.is_file():
            raise RuntimeError(
                f"FITS persistant absent après copie: {destination}"
            )

        source_size = source.stat().st_size
        destination_size = destination.stat().st_size

        if source_size <= 0:
            raise RuntimeError("FITS source vide")

        if destination_size != source_size:
            raise RuntimeError(
                "Taille FITS persistante incohérente: "
                f"source={source_size}, archive={destination_size}"
            )

        with destination.open("rb") as handle:
            header = handle.read(9)

        if header != b"SIMPLE  =":
            raise RuntimeError(
                "FITS persistant invalide: en-tête SIMPLE absent"
            )

        return {
            "verified": True,
            "source_size_bytes": source_size,
            "fits_size_bytes": destination_size,
        }

    def archive_capture(
        self,
        result: dict[str, Any],
    ) -> dict[str, Any]:
        if result.get("status") != "captured":
            raise ValueError("La capture caméra n'est pas valide")

        image_value = result.get("image")
        if not image_value:
            raise ValueError("Chemin FITS de capture absent")

        source = Path(str(image_value))
        if not source.is_file():
            raise FileNotFoundError(source)

        captured_at = datetime.now(timezone.utc)
        capture_id = (
            captured_at.strftime("%Y%m%dT%H%M%S%fZ")
            + "_"
            + uuid4().hex[:8]
        )
        archive_dir = (
            self.root
            / captured_at.strftime("%Y")
            / captured_at.strftime("%Y-%m-%d")
            / capture_id
        )

        fits_path = archive_dir / "initial.fits"
        preview_path = archive_dir / "initial.jpg"
        metadata_path = archive_dir / "metadata.json"
        solve_path = archive_dir / "solve.json"

        # The scientific FITS is secured first. Nothing else in the Assistant
        # 3 workflow is allowed to start before this copy has completed and the
        # persistent file has been verified byte-for-byte by size and FITS magic.
        self._copy_atomic(source, fits_path)
        verification = self._verify_persisted_fits(
            source,
            fits_path,
        )

        metadata = {
            "workflow": "preparation_assistant_3_first_astrometry",
            "capture_id": capture_id,
            "capture_time_utc": captured_at.isoformat(
                timespec="milliseconds"
            ),
            "source_image": str(source),
            "source_name": source.name,
            "exposure_s": result.get("exposure_s"),
            "camera": result.get("camera"),
            "archive": verification,
            "files": {
                "fits": "initial.fits",
                "preview": None,
                "solve": None,
            },
            "preview": {
                "status": None,
                "bayer": None,
                "background": None,
                "noise": None,
                "contrast": None,
            },
            "solver": {
                "status": None,
                "solver": None,
                "detail": None,
                "ra_deg": None,
                "dec_deg": None,
                "orientation_deg": None,
                "pixel_scale_arcsec": None,
                "stars_detected": None,
            },
        }
        self._write_json_atomic(metadata_path, metadata)

        with self._lock:
            self._by_source[str(source)] = archive_dir
            self._by_name[source.name] = archive_dir

        logger.info(
            "ASSISTANT3 ARCHIVED capture_id=%s size=%s fits=%s",
            capture_id,
            verification["fits_size_bytes"],
            fits_path,
        )

        return {
            "directory": str(archive_dir),
            "fits": str(fits_path),
            "preview": str(preview_path),
            "metadata": str(metadata_path),
            "solve": str(solve_path),
            "capture_id": capture_id,
            **verification,
        }

    def _find_archive(self, source: str) -> Path | None:
        source_path = Path(source)

        with self._lock:
            direct = self._by_source.get(source)
            if direct is not None:
                return direct

            by_name = self._by_name.get(source_path.name)
            if by_name is not None:
                return by_name

        if not self.root.exists():
            return None

        # Fallback after a server restart: metadata.json retains the original
        # /tmp source path and lets a later /solve update the right archive.
        candidates = sorted(
            self.root.glob("*/*/*/metadata.json"),
            key=lambda item: item.stat().st_mtime,
            reverse=True,
        )
        for metadata_path in candidates:
            try:
                payload = json.loads(
                    metadata_path.read_text(encoding="utf-8")
                )
            except (OSError, json.JSONDecodeError):
                continue

            if (
                payload.get("source_image") == source
                or payload.get("source_name") == source_path.name
            ):
                archive_dir = metadata_path.parent
                with self._lock:
                    self._by_source[source] = archive_dir
                    self._by_name[source_path.name] = archive_dir
                return archive_dir

        return None

    def record_preview(
        self,
        source: str,
        content: bytes,
        headers: dict[str, str] | None = None,
    ) -> None:
        archive_dir = self._find_archive(source)
        if archive_dir is None:
            return

        preview_path = archive_dir / "initial.jpg"
        metadata_path = archive_dir / "metadata.json"

        if not preview_path.exists():
            self._write_bytes_atomic(preview_path, content)

        try:
            metadata = json.loads(
                metadata_path.read_text(encoding="utf-8")
            )
        except (OSError, json.JSONDecodeError):
            return

        metadata.setdefault("files", {})["preview"] = "initial.jpg"

        headers = headers or {}
        metadata["preview"] = {
            "status": headers.get("X-StellarPilot-Preview-Status"),
            "bayer": headers.get("X-StellarPilot-Bayer"),
            "background": headers.get(
                "X-StellarPilot-Preview-Background"
            ),
            "noise": headers.get("X-StellarPilot-Preview-Noise"),
            "contrast": headers.get(
                "X-StellarPilot-Preview-Contrast"
            ),
        }
        self._write_json_atomic(metadata_path, metadata)

    def record_solve(
        self,
        source: str,
        solution: dict[str, Any],
    ) -> None:
        archive_dir = self._find_archive(source)
        if archive_dir is None:
            return

        metadata_path = archive_dir / "metadata.json"
        solve_path = archive_dir / "solve.json"
        try:
            metadata = json.loads(
                metadata_path.read_text(encoding="utf-8")
            )
        except (OSError, json.JSONDecodeError):
            return

        # Keep the complete solver response for post-session diagnostics. This
        # includes robust-solver attempts, position hint, radii and timings.
        self._write_json_atomic(solve_path, solution)
        metadata.setdefault("files", {})["solve"] = "solve.json"
        metadata["solver"] = {
            "status": solution.get("status"),
            "solver": solution.get("solver"),
            "detail": solution.get("detail"),
            "ra_deg": solution.get("ra"),
            "dec_deg": solution.get("dec"),
            "orientation_deg": solution.get("orientation_deg"),
            "pixel_scale_arcsec": solution.get("pixel_scale_arcsec"),
            "stars_detected": solution.get("stars_detected"),
            "strategy": solution.get("strategy"),
            "total_duration_s": solution.get("total_duration_s"),
            "position_hint": solution.get("position_hint"),
            "attempts": solution.get("attempts"),
        }
        self._write_json_atomic(metadata_path, metadata)

        logger.info(
            "ASSISTANT3 SOLVE capture=%s status=%s strategy=%s duration_s=%s",
            archive_dir.name,
            solution.get("status"),
            solution.get("strategy"),
            solution.get("total_duration_s"),
        )


preparation_astrometry_archive = PreparationAstrometryArchive()
