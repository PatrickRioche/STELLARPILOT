from __future__ import annotations

import json
import math
import os
import shutil
import threading
from datetime import datetime, timezone
from io import BytesIO
from pathlib import Path
from typing import Any

import numpy as np
from astropy.io import fits
from PIL import Image
from scipy.ndimage import gaussian_filter, shift as nd_shift

from app.gps.service import gps_service
from app.imaging.light_quality import analyze_light_quality
from app.imaging.robust_stack import FINAL_STACK_METHOD, build_sigma_clipped_stack
from app.imaging.stack_calibration import calibrate_light, select_compatible_master
from app.indi.service import indi_service
from app.solving.service import plate_solver


SERVER_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_RUNTIME_ROOT = SERVER_ROOT / "tmp"
DEFAULT_GALLERIES_ROOT = SERVER_ROOT / "data" / "galleries"
QUALITY_BASELINE_LIMIT = 20


class CaptureSessionService:
    """Persistent capture workspace and resumable calibrated live stack.

    Runtime files intentionally live below ``stellarpilot-server/tmp`` and
    never below the operating-system ``/tmp``. A session keeps every stage
    separated so calibration/registration algorithms can evolve without
    changing the public API.
    """

    def __init__(
        self,
        runtime_root: Path | str | None = None,
        galleries_root: Path | str | None = None,
        indi: Any = None,
        solver: Any = None,
    ) -> None:
        self.runtime_root = Path(
            runtime_root
            or os.environ.get("STELLARPILOT_RUNTIME_ROOT")
            or DEFAULT_RUNTIME_ROOT
        )
        self.galleries_root = Path(
            galleries_root
            or os.environ.get("STELLARPILOT_GALLERIES_ROOT")
            or DEFAULT_GALLERIES_ROOT
        )
        self.sessions_root = self.runtime_root / "capture" / "sessions"
        self.indi = indi or indi_service
        self.solver = solver or plate_solver
        self._lock = threading.RLock()
        self._stop_events: dict[str, threading.Event] = {}
        self._threads: dict[str, threading.Thread] = {}

    @staticmethod
    def _utc_now() -> str:
        return datetime.now(timezone.utc).isoformat(timespec="seconds")

    @staticmethod
    def _parse_utc(value: str | None) -> datetime | None:
        if not value:
            return None
        try:
            instant = datetime.fromisoformat(value)
        except ValueError:
            return None
        if instant.tzinfo is None:
            instant = instant.replace(tzinfo=timezone.utc)
        return instant.astimezone(timezone.utc)

    @classmethod
    def _acquisition_seconds(cls, metadata: dict) -> float:
        stacking = metadata.get("stacking") or {}
        accumulated = float(stacking.get("accumulated_active_seconds") or 0.0)
        active_started_at = cls._parse_utc(stacking.get("active_started_at"))
        if active_started_at is not None:
            accumulated += max(
                0.0,
                (datetime.now(timezone.utc) - active_started_at).total_seconds(),
            )
        return round(accumulated, 3)

    def _start_acquisition_segment(self, metadata: dict) -> None:
        stacking = metadata["stacking"]
        now = self._utc_now()
        if not stacking.get("started_at"):
            stacking["started_at"] = now
        stacking["active_started_at"] = now
        stacking["stopped_at"] = None
        stacking["run_count"] = int(stacking.get("run_count") or 0) + 1
        metadata["acquisition_seconds"] = self._acquisition_seconds(metadata)

    def _close_acquisition_segment(self, metadata: dict) -> None:
        stacking = metadata["stacking"]
        active_started_at = self._parse_utc(stacking.get("active_started_at"))
        accumulated = float(stacking.get("accumulated_active_seconds") or 0.0)
        now = datetime.now(timezone.utc)
        if active_started_at is not None:
            accumulated += max(0.0, (now - active_started_at).total_seconds())
        stacking["accumulated_active_seconds"] = round(accumulated, 3)
        stacking["active_started_at"] = None
        stacking["stopped_at"] = now.isoformat(timespec="seconds")
        metadata["acquisition_seconds"] = round(accumulated, 3)

    @staticmethod
    def _safe_slug(value: str) -> str:
        cleaned = "".join(
            char if char.isalnum() else "-"
            for char in (value or "target").strip()
        )
        cleaned = "-".join(part for part in cleaned.split("-") if part)
        return cleaned[:48] or "target"

    def _observation_snapshot(self) -> dict:
        """Freeze the observing site when a session starts."""
        try:
            gps = gps_service.status()
        except Exception:
            gps = {}

        if (
            gps.get("status") == "fix"
            and gps.get("latitude") is not None
            and gps.get("longitude") is not None
        ):
            return {
                "latitude": gps.get("latitude"),
                "longitude": gps.get("longitude"),
                "altitude_m": gps.get("altitude"),
                "location_source": "gps",
                "place_name": gps.get("place_name"),
            }

        try:
            mount_location = self.indi.mount_location()
        except Exception:
            mount_location = {}

        if (
            mount_location.get("status") == "available"
            and mount_location.get("latitude") is not None
            and mount_location.get("longitude") is not None
        ):
            return {
                "latitude": mount_location.get("latitude"),
                "longitude": mount_location.get("longitude"),
                "altitude_m": mount_location.get("altitude"),
                "location_source": "onstep",
                "place_name": None,
            }

        return {
            "latitude": None,
            "longitude": None,
            "altitude_m": None,
            "location_source": None,
            "place_name": None,
        }

    def _session_dir(self, session_id: str) -> Path:
        return self.sessions_root / session_id

    def _metadata_path(self, session_id: str) -> Path:
        return self._session_dir(session_id) / "session.json"

    def _ensure_tree(self, session_dir: Path) -> None:
        for name in (
            "raw",
            "accepted",
            "rejected",
            "calibrated",
            "registered",
            "stack",
            "previews",
        ):
            (session_dir / name).mkdir(parents=True, exist_ok=True)

    def _write(self, metadata: dict) -> dict:
        metadata["updated_at"] = self._utc_now()
        metadata["acquisition_seconds"] = self._acquisition_seconds(metadata)
        path = self._metadata_path(metadata["id"])
        path.parent.mkdir(parents=True, exist_ok=True)
        temporary = path.with_suffix(".json.tmp")
        temporary.write_text(
            json.dumps(metadata, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        temporary.replace(path)
        return metadata

    def _read(self, session_id: str) -> dict:
        path = self._metadata_path(session_id)
        if not path.exists():
            raise KeyError(session_id)
        return json.loads(path.read_text(encoding="utf-8"))

    def create_session(
        self,
        *,
        target_name: str,
        target_ra_hours: float,
        target_dec_deg: float,
        object_type: str = "unknown",
        tracking_mode: str = "sidereal",
        exposure_s: float = 4.0,
        centering_tolerance_arcsec: float = 30.0,
        recenter_tolerance_arcsec: float = 30.0,
        astrometry_interval_frames: int = 8,
        registration_recenter_pixels: float = 12.0,
    ) -> dict:
        now = datetime.now(timezone.utc)
        stamp = now.strftime("%Y%m%dT%H%M%SZ")
        session_id = (
            f"{stamp}_{now.microsecond:06d}_"
            f"{self._safe_slug(target_name)}"
        )
        session_dir = self._session_dir(session_id)
        self._ensure_tree(session_dir)

        metadata = {
            "id": session_id,
            "created_at": now.isoformat(timespec="seconds"),
            "updated_at": now.isoformat(timespec="seconds"),
            "state": "framing",
            "target": {
                "name": target_name,
                "ra_hours": float(target_ra_hours) % 24.0,
                "dec_deg": float(target_dec_deg),
                "object_type": object_type,
                "tracking_mode": tracking_mode,
            },
            "observation": self._observation_snapshot(),
            "setup": {
                "exposure_s": float(exposure_s),
                "centering_tolerance_arcsec": float(
                    centering_tolerance_arcsec
                ),
                "recenter_tolerance_arcsec": float(
                    recenter_tolerance_arcsec
                ),
                "astrometry_interval_frames": int(
                    astrometry_interval_frames
                ),
                "registration_recenter_pixels": float(
                    registration_recenter_pixels
                ),
            },
            "counts": {
                "captured": 0,
                "accepted": 0,
                "rejected": 0,
                "rejected_by_reason": {},
            },
            "integration_seconds": 0.0,
            "acquisition_seconds": 0.0,
            "centering": {
                "status": "not_checked",
                "attempts": 0,
                "error_arcsec": None,
                "solve_ra_deg": None,
                "solve_dec_deg": None,
                "correction_ra_hours": None,
                "correction_dec_deg": None,
                "image": None,
                "verified_at": None,
            },
            "calibration": {
                "required": True,
                "status": "pending",
                "master_id": None,
                "master_dark": None,
                "temperature_delta_c": None,
                "hot_pixels": None,
                "calibrated_frames": 0,
                "detail": None,
            },
            "stacking": {
                "running": False,
                "mode": "continuous",
                "target_frames": None,
                "max_captured_frames": None,
                "astrometry_required": True,
                "stop_requested": False,
                "recenter_required": False,
                "recenter_reason": None,
                "last_registration_dx_px": None,
                "last_registration_dy_px": None,
                "last_registration_distance_px": None,
                "last_astrometry_frame": 0,
                "registration_mode": "translation-even-pixel-v1",
                "live_stack_method": "mean-v1",
                "final_stack_method": FINAL_STACK_METHOD,
                "quality_samples": [],
                "run_count": 0,
                "started_at": None,
                "active_started_at": None,
                "accumulated_active_seconds": 0.0,
                "stopped_at": None,
                "resume_verification_after": None,
            },
            "last_frame": None,
            "last_quality": None,
            "preview": None,
            "stack_fits": None,
            "stack_preview": None,
            "final_stack": None,
            "gallery_path": None,
        }
        return self._write(metadata)

    def get_session(self, session_id: str) -> dict:
        with self._lock:
            metadata = self._read(session_id)
            thread = self._threads.get(session_id)
            if thread is not None and not thread.is_alive():
                if metadata["stacking"].get("active_started_at"):
                    self._close_acquisition_segment(metadata)
                metadata["stacking"]["running"] = False
                if metadata.get("state") in {"stacking", "stopping"}:
                    metadata["state"] = "stopped"
                self._threads.pop(session_id, None)
                self._write(metadata)
            else:
                metadata["acquisition_seconds"] = self._acquisition_seconds(metadata)
            return metadata

    def list_sessions(self) -> list[dict]:
        if not self.sessions_root.exists():
            return []
        sessions = []
        for path in self.sessions_root.iterdir():
            metadata_path = path / "session.json"
            if not metadata_path.exists():
                continue
            try:
                item = json.loads(metadata_path.read_text(encoding="utf-8"))
                item["acquisition_seconds"] = self._acquisition_seconds(item)
                sessions.append(item)
            except (OSError, json.JSONDecodeError):
                continue
        sessions.sort(key=lambda item: item.get("created_at", ""), reverse=True)
        return sessions

    @staticmethod
    def _wrap_degrees(value: float) -> float:
        return (value + 180.0) % 360.0 - 180.0

    @classmethod
    def _centering_result(
        cls,
        target_ra_hours: float,
        target_dec_deg: float,
        solve_ra_deg: float,
        solve_dec_deg: float,
        tolerance_arcsec: float,
    ) -> dict:
        target_ra_deg = (target_ra_hours * 15.0) % 360.0
        delta_ra_deg = cls._wrap_degrees(target_ra_deg - solve_ra_deg)
        delta_dec_deg = target_dec_deg - solve_dec_deg

        target_dec_rad = math.radians(target_dec_deg)
        solve_dec_rad = math.radians(solve_dec_deg)
        delta_ra_rad = math.radians(delta_ra_deg)
        cosine = (
            math.sin(target_dec_rad) * math.sin(solve_dec_rad)
            + math.cos(target_dec_rad)
            * math.cos(solve_dec_rad)
            * math.cos(delta_ra_rad)
        )
        cosine = min(1.0, max(-1.0, cosine))
        error_arcsec = math.degrees(math.acos(cosine)) * 3600.0

        correction_ra_deg = (target_ra_deg + delta_ra_deg) % 360.0
        correction_dec_deg = max(
            -90.0,
            min(90.0, target_dec_deg + delta_dec_deg),
        )

        return {
            "status": "centered"
            if error_arcsec <= tolerance_arcsec
            else "correction_required",
            "error_arcsec": round(error_arcsec, 3),
            "solve_ra_deg": solve_ra_deg,
            "solve_dec_deg": solve_dec_deg,
            "correction_ra_hours": correction_ra_deg / 15.0,
            "correction_dec_deg": correction_dec_deg,
        }

    def _capture_into(
        self,
        metadata: dict,
        prefix: str,
    ) -> dict:
        raw_dir = self._session_dir(metadata["id"]) / "raw"
        result = self.indi.capture(
            metadata["setup"]["exposure_s"],
            output_dir=raw_dir,
            prefix=prefix,
        )
        if result.get("status") != "captured":
            return result
        metadata["last_frame"] = result.get("image")
        return result

    def center_step(self, session_id: str) -> dict:
        with self._lock:
            metadata = self._read(session_id)
            attempt = int(metadata["centering"].get("attempts", 0)) + 1

        result = self._capture_into(
            metadata,
            f"center_{attempt:03d}",
        )
        if result.get("status") != "captured":
            return {
                "status": "error",
                "detail": result.get("detail", "Capture impossible"),
                "session": metadata,
            }

        image = result["image"]
        target = metadata["target"]
        solution = self.solver.solve_robust(
            image,
            ra_hint=target["ra_hours"] * 15.0,
            dec_hint=target["dec_deg"],
        )

        preview_path = (
            self._session_dir(session_id) / "previews" / "latest.jpg"
        )
        try:
            preview_path.write_bytes(self._fits_preview_bytes(Path(image)))
            metadata["preview"] = str(preview_path)
        except Exception:
            metadata["preview"] = None

        centering = {
            "status": "unsolved",
            "attempts": attempt,
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
            "verified_at": None,
        }

        if (
            solution.get("status") == "solved"
            and solution.get("ra") is not None
            and solution.get("dec") is not None
        ):
            centering.update(
                self._centering_result(
                    target["ra_hours"],
                    target["dec_deg"],
                    float(solution["ra"]),
                    float(solution["dec"]),
                    metadata["setup"]["centering_tolerance_arcsec"],
                )
            )
            if centering["status"] == "centered":
                centering["verified_at"] = self._utc_now()

        metadata["centering"] = centering
        metadata["state"] = (
            "centered" if centering["status"] == "centered" else "framing"
        )
        self._write(metadata)
        return {
            "status": centering["status"],
            "centering": centering,
            "session": metadata,
        }

    @staticmethod
    def _load_fits(path: Path) -> tuple[np.ndarray, fits.Header]:
        with fits.open(path, memmap=False) as hdul:
            data = np.asarray(hdul[0].data)
            header = hdul[0].header.copy()
        data = np.squeeze(data)
        if data.ndim != 2:
            raise ValueError(f"Unsupported FITS shape: {data.shape}")
        return data.astype(np.float32, copy=False), header

    @staticmethod
    def _registration_shift(
        reference: np.ndarray,
        current: np.ndarray,
    ) -> tuple[int, int, float]:
        if reference.shape != current.shape:
            raise ValueError("Image dimensions changed during stacking")

        max_dimension = max(reference.shape)
        sample_step = max(1, int(math.ceil(max_dimension / 1024.0)))
        ref = reference[::sample_step, ::sample_step]
        cur = current[::sample_step, ::sample_step]
        ref = gaussian_filter(ref - np.nanmedian(ref), sigma=1.0)
        cur = gaussian_filter(cur - np.nanmedian(cur), sigma=1.0)

        ref_fft = np.fft.fft2(np.nan_to_num(ref, nan=0.0))
        cur_fft = np.fft.fft2(np.nan_to_num(cur, nan=0.0))
        cross = ref_fft * np.conj(cur_fft)
        magnitude = np.abs(cross)
        cross /= np.where(magnitude == 0.0, 1.0, magnitude)
        correlation = np.abs(np.fft.ifft2(cross))
        peak = np.unravel_index(np.argmax(correlation), correlation.shape)

        dy = int(peak[0])
        dx = int(peak[1])
        if dy > ref.shape[0] // 2:
            dy -= ref.shape[0]
        if dx > ref.shape[1] // 2:
            dx -= ref.shape[1]
        dy *= sample_step
        dx *= sample_step

        # Keep the Bayer phase unchanged: only even integer translations.
        dy = int(round(dy / 2.0) * 2)
        dx = int(round(dx / 2.0) * 2)
        distance = math.hypot(dx, dy)
        return dx, dy, distance

    def _update_stack(
        self,
        session_id: str,
        image_path: Path,
    ) -> tuple[Path, Path, dict]:
        session_dir = self._session_dir(session_id)
        stack_dir = session_dir / "stack"
        registered_dir = session_dir / "registered"
        stack_dir.mkdir(parents=True, exist_ok=True)

        data, header = self._load_fits(image_path)
        reference_path = stack_dir / "reference.fits"
        sum_path = stack_dir / "sum.npy"
        count_path = stack_dir / "count.npy"

        if not reference_path.exists():
            fits.PrimaryHDU(data=data, header=header).writeto(
                reference_path,
                overwrite=True,
            )
            registered = data
            dx = dy = 0
            distance = 0.0
        else:
            reference, _ = self._load_fits(reference_path)
            dx, dy, distance = self._registration_shift(reference, data)
            if distance > 250.0:
                raise ValueError(
                    f"Registration shift too large: {distance:.1f} px"
                )
            registered = nd_shift(
                data,
                shift=(dy, dx),
                order=0,
                mode="constant",
                cval=np.nan,
                prefilter=False,
            )

        registered_path = registered_dir / image_path.name
        fits.PrimaryHDU(data=registered, header=header).writeto(
            registered_path,
            overwrite=True,
        )

        if sum_path.exists() and count_path.exists():
            stack_sum = np.load(sum_path)
            stack_count = np.load(count_path)
        else:
            stack_sum = np.zeros_like(registered, dtype=np.float64)
            stack_count = np.zeros_like(registered, dtype=np.uint32)

        finite = np.isfinite(registered)
        stack_sum[finite] += registered[finite]
        stack_count[finite] += 1
        np.save(sum_path, stack_sum)
        np.save(count_path, stack_count)

        stacked = np.divide(
            stack_sum,
            stack_count,
            out=np.zeros_like(stack_sum, dtype=np.float64),
            where=stack_count > 0,
        ).astype(np.float32)

        stack_fits = stack_dir / "current.fits"
        header["SPSTACK"] = (True, "StellarPilot live stack")
        header["SPMETH"] = ("mean-v1", "Live preview combination method")
        fits.PrimaryHDU(data=stacked, header=header).writeto(
            stack_fits,
            overwrite=True,
        )
        stack_preview = stack_dir / "current.jpg"
        stack_preview.write_bytes(self._fits_preview_bytes(stack_fits))

        return stack_fits, stack_preview, {
            "dx_px": dx,
            "dy_px": dy,
            "distance_px": round(distance, 3),
            "registered_image": str(registered_path),
            "calibrated_image": str(image_path),
        }

    @staticmethod
    def _fits_preview_bytes(path: Path) -> bytes:
        data, header = CaptureSessionService._load_fits(path)
        height = data.shape[0] - data.shape[0] % 2
        width = data.shape[1] - data.shape[1] % 2
        data = data[:height, :width]
        finite = data[np.isfinite(data)]
        if finite.size == 0:
            raise ValueError("No finite pixels")

        background = float(np.median(finite))
        mad = float(np.median(np.abs(finite - background)))
        noise = max(1.4826 * mad, 1.0)
        black = background - 0.5 * noise
        white = max(
            float(np.percentile(finite, 99.8)),
            background + 8.0 * noise,
        )
        if white <= black:
            white = black + 1.0

        def stretch(channel: np.ndarray) -> np.ndarray:
            value = np.clip((channel - black) / (white - black), 0.0, 1.0)
            return np.arcsinh(value * 8.0) / np.arcsinh(8.0)

        pattern = str(header.get("BAYERPAT", "")).strip().upper()
        matrices = {
            "RGGB": (("R", "G"), ("G", "B")),
            "BGGR": (("B", "G"), ("G", "R")),
            "GRBG": (("G", "R"), ("B", "G")),
            "GBRG": (("G", "B"), ("R", "G")),
        }
        matrix = matrices.get(pattern)

        if matrix is None:
            mono = stretch(data)
            rgb = np.stack((mono, mono, mono), axis=-1)
        else:
            channels: dict[str, list[np.ndarray]] = {"R": [], "G": [], "B": []}
            for row in range(2):
                for col in range(2):
                    channels[matrix[row][col]].append(data[row::2, col::2])
            red = channels["R"][0]
            green = sum(channels["G"]) / len(channels["G"])
            blue = channels["B"][0]
            rgb = np.stack(
                (stretch(red), stretch(green), stretch(blue)),
                axis=-1,
            )

        rgb = np.clip(rgb, 0.0, 1.0)
        image = Image.fromarray((rgb * 255.0).astype(np.uint8))
        if image.width > 1600:
            new_height = round(image.height * 1600 / image.width)
            image = image.resize((1600, new_height), Image.Resampling.LANCZOS)
        buffer = BytesIO()
        image.save(buffer, format="JPEG", quality=90, optimize=True)
        return buffer.getvalue()

    def _check_astrometry_drift(
        self,
        metadata: dict,
        image: str,
    ) -> dict | None:
        target = metadata["target"]
        solution = self.solver.solve_robust(
            image,
            ra_hint=target["ra_hours"] * 15.0,
            dec_hint=target["dec_deg"],
        )
        if (
            solution.get("status") != "solved"
            or solution.get("ra") is None
            or solution.get("dec") is None
        ):
            return None
        return self._centering_result(
            target["ra_hours"],
            target["dec_deg"],
            float(solution["ra"]),
            float(solution["dec"]),
            metadata["setup"]["recenter_tolerance_arcsec"],
        )

    @staticmethod
    def _move_if_exists(source: Path, destination: Path) -> Path:
        destination.parent.mkdir(parents=True, exist_ok=True)
        if source.exists():
            source.replace(destination)
        return destination

    @staticmethod
    def _record_rejection(metadata: dict, reasons: list[str]) -> None:
        metadata["counts"]["rejected"] = int(metadata["counts"].get("rejected") or 0) + 1
        counters = metadata["counts"].setdefault("rejected_by_reason", {})
        for reason in reasons or ["unknown"]:
            counters[reason] = int(counters.get(reason) or 0) + 1

    def _stack_worker(self, session_id: str, stop_event: threading.Event) -> None:
        try:
            while not stop_event.is_set():
                with self._lock:
                    metadata = self._read(session_id)
                    if metadata["stacking"].get("recenter_required"):
                        break
                    frame_number = int(metadata["counts"].get("captured") or 0) + 1

                capture = self._capture_into(
                    metadata,
                    f"light_{frame_number:06d}",
                )
                if capture.get("status") != "captured":
                    with self._lock:
                        metadata["state"] = "stack_error"
                        metadata["stacking"]["running"] = False
                        metadata["stacking"]["error"] = capture.get("detail")
                        self._write(metadata)
                    return

                raw_path = Path(capture["image"])
                with self._lock:
                    metadata = self._read(session_id)
                    metadata["counts"]["captured"] = int(metadata["counts"].get("captured") or 0) + 1
                    self._write(metadata)

                try:
                    snapshot = self.indi.status_snapshot()
                except Exception:
                    snapshot = None

                preferred_master_id = metadata.get("calibration", {}).get("master_id")
                try:
                    selection = select_compatible_master(
                        raw_path,
                        exposure_s=float(metadata["setup"]["exposure_s"]),
                        snapshot=snapshot,
                        preferred_master_id=preferred_master_id,
                    )
                except Exception as exc:
                    selection = {
                        "status": "unavailable",
                        "detail": f"Sélection Master Dark impossible: {exc}",
                        "master": None,
                    }

                if selection.get("status") != "ready":
                    rejected_path = self._move_if_exists(
                        raw_path,
                        self._session_dir(session_id) / "rejected" / raw_path.name,
                    )
                    with self._lock:
                        metadata = self._read(session_id)
                        self._record_rejection(metadata, ["dark_incompatible"])
                        metadata["last_frame"] = str(rejected_path)
                        metadata["calibration"].update(
                            {
                                "status": "unavailable",
                                "detail": selection.get("detail") or "Aucun Master Dark compatible",
                            }
                        )
                        metadata["stacking"]["running"] = False
                        metadata["state"] = "paused_calibration"
                        metadata["stacking"]["error"] = metadata["calibration"]["detail"]
                        self._write(metadata)
                    break

                master_item = selection["master"]
                hot_pixels = master_item.get("hot_pixels") or {}
                calibrated_path = (
                    self._session_dir(session_id)
                    / "calibrated"
                    / raw_path.name
                )
                try:
                    calibration = calibrate_light(
                        raw_path,
                        selection,
                        calibrated_path,
                    )
                except Exception as exc:
                    rejected_path = self._move_if_exists(
                        raw_path,
                        self._session_dir(session_id) / "rejected" / raw_path.name,
                    )
                    with self._lock:
                        metadata = self._read(session_id)
                        self._record_rejection(metadata, ["calibration_error"])
                        metadata["last_frame"] = str(rejected_path)
                        metadata["calibration"]["status"] = "error"
                        metadata["calibration"]["detail"] = str(exc)
                        metadata["stacking"]["running"] = False
                        metadata["state"] = "stack_error"
                        self._write(metadata)
                    break

                with self._lock:
                    metadata = self._read(session_id)
                    metadata["calibration"].update(
                        {
                            "status": "ready",
                            "master_id": master_item.get("id"),
                            "master_dark": (master_item.get("master_dark") or {}).get("path"),
                            "temperature_delta_c": selection.get("temperature_delta_c"),
                            "hot_pixels": hot_pixels.get("count"),
                            "detail": None,
                        }
                    )
                    baseline = list(metadata["stacking"].get("quality_samples") or [])

                quality = analyze_light_quality(
                    calibrated_path,
                    baseline=baseline,
                )

                if quality.get("status") != "ok" or not quality.get("accepted"):
                    reasons = list(quality.get("reasons") or ["quality_rejected"])
                    rejected_path = self._move_if_exists(
                        raw_path,
                        self._session_dir(session_id) / "rejected" / raw_path.name,
                    )
                    with self._lock:
                        metadata = self._read(session_id)
                        self._record_rejection(metadata, reasons)
                        metadata["last_frame"] = str(rejected_path)
                        metadata["last_quality"] = quality
                        self._write(metadata)
                    continue

                try:
                    stack_fits, stack_preview, registration = self._update_stack(
                        session_id,
                        calibrated_path,
                    )
                except Exception as exc:
                    rejected_path = self._move_if_exists(
                        raw_path,
                        self._session_dir(session_id) / "rejected" / raw_path.name,
                    )
                    with self._lock:
                        metadata = self._read(session_id)
                        self._record_rejection(metadata, ["registration_error"])
                        metadata["last_frame"] = str(rejected_path)
                        metadata["last_quality"] = quality
                        metadata["stacking"]["last_registration_error"] = str(exc)
                        self._write(metadata)
                    continue

                accepted_path = self._move_if_exists(
                    raw_path,
                    self._session_dir(session_id) / "accepted" / raw_path.name,
                )

                with self._lock:
                    metadata = self._read(session_id)
                    metadata["counts"]["accepted"] = int(metadata["counts"].get("accepted") or 0) + 1
                    metadata["integration_seconds"] = round(
                        metadata["counts"]["accepted"]
                        * metadata["setup"]["exposure_s"],
                        3,
                    )
                    metadata["calibration"]["calibrated_frames"] = int(
                        metadata["calibration"].get("calibrated_frames") or 0
                    ) + 1
                    metadata["stack_fits"] = str(stack_fits)
                    metadata["stack_preview"] = str(stack_preview)
                    metadata["preview"] = str(stack_preview)
                    metadata["last_frame"] = str(accepted_path)
                    metadata["last_quality"] = quality
                    metadata["stacking"]["last_registration_dx_px"] = registration[
                        "dx_px"
                    ]
                    metadata["stacking"]["last_registration_dy_px"] = registration[
                        "dy_px"
                    ]
                    metadata["stacking"]["last_registration_distance_px"] = registration[
                        "distance_px"
                    ]
                    samples = list(metadata["stacking"].get("quality_samples") or [])
                    samples.append(
                        {
                            key: quality.get(key)
                            for key in (
                                "score",
                                "star_count",
                                "background_sigma",
                                "fwhm_px",
                                "ellipticity",
                                "star_signal",
                            )
                        }
                    )
                    metadata["stacking"]["quality_samples"] = samples[-QUALITY_BASELINE_LIMIT:]
                    accepted = int(metadata["counts"]["accepted"])

                    astrometry_required = bool(
                        metadata["stacking"].get("astrometry_required", True)
                    )
                    interval = max(
                        1,
                        int(metadata["setup"]["astrometry_interval_frames"]),
                    )
                    registration_trigger = (
                        astrometry_required
                        and registration["distance_px"]
                        >= metadata["setup"]["registration_recenter_pixels"]
                    )
                    astrometry_due = (
                        astrometry_required
                        and accepted % interval == 0
                    )
                    self._write(metadata)

                if astrometry_due or registration_trigger:
                    drift = self._check_astrometry_drift(
                        metadata,
                        registration["calibrated_image"],
                    )
                    with self._lock:
                        metadata = self._read(session_id)
                        metadata["stacking"]["last_astrometry_frame"] = accepted
                        metadata["stacking"]["last_astrometry"] = drift
                        if drift and drift["status"] == "correction_required":
                            checkpoint = self._utc_now()
                            metadata["stacking"]["recenter_required"] = True
                            metadata["stacking"]["recenter_reason"] = (
                                "registration_drift"
                                if registration_trigger
                                else "periodic_astrometry"
                            )
                            metadata["stacking"]["resume_verification_after"] = checkpoint
                            metadata["stacking"]["running"] = False
                            metadata["state"] = "paused_recenter"
                            metadata["centering"].update(drift)
                            metadata["centering"]["verified_at"] = None
                            self._write(metadata)
                            break
                        self._write(metadata)

        finally:
            with self._lock:
                try:
                    metadata = self._read(session_id)
                    if metadata["stacking"].get("active_started_at"):
                        self._close_acquisition_segment(metadata)
                    metadata["stacking"]["running"] = False
                    if metadata["state"] in {"stacking", "stopping"}:
                        metadata["state"] = "stopped"
                        metadata["stacking"]["resume_verification_after"] = (
                            metadata["stacking"].get("stopped_at")
                        )
                    self._write(metadata)
                except KeyError:
                    pass
                self._threads.pop(session_id, None)

    def _launch_stack_locked(self, session_id: str, metadata: dict) -> dict:
        stop_event = threading.Event()
        self._stop_events[session_id] = stop_event
        metadata["stacking"]["running"] = True
        metadata["stacking"]["stop_requested"] = False
        metadata["stacking"].pop("error", None)
        metadata["state"] = "stacking"
        self._start_acquisition_segment(metadata)
        self._write(metadata)

        thread = threading.Thread(
            target=self._stack_worker,
            args=(session_id, stop_event),
            name=f"stellarpilot-stack-{session_id}",
            daemon=True,
        )
        self._threads[session_id] = thread
        thread.start()
        return {"status": "stacking", "session": metadata}

    def start_stack(self, session_id: str) -> dict:
        with self._lock:
            metadata = self._read(session_id)
            thread = self._threads.get(session_id)
            if thread is not None and thread.is_alive():
                return {"status": "already_running", "session": metadata}
            if metadata.get("gallery_path") or metadata.get("state") == "completed":
                return {
                    "status": "finalized",
                    "detail": "Cette session est déjà enregistrée dans la galerie",
                    "session": metadata,
                }
            if int(metadata["counts"].get("accepted") or 0) > 0 and metadata.get("state") == "stopped":
                return {
                    "status": "resume_required",
                    "detail": "Utilisez la reprise après une vérification de centrage",
                    "session": metadata,
                }
            if metadata["centering"].get("status") != "centered":
                return {
                    "status": "centering_required",
                    "detail": "Le centrage doit être vérifié avant le stacking",
                    "session": metadata,
                }
            if metadata["stacking"].get("recenter_required"):
                return {
                    "status": "centering_required",
                    "detail": "Un recentrage doit être vérifié avant le stacking",
                    "session": metadata,
                }
            metadata["stacking"]["start_centering_status"] = "centered"
            return self._launch_stack_locked(session_id, metadata)

    def resume_stack(self, session_id: str) -> dict:
        with self._lock:
            metadata = self._read(session_id)
            thread = self._threads.get(session_id)
            if thread is not None and thread.is_alive():
                return {"status": "already_running", "session": metadata}
            if metadata.get("gallery_path") or metadata.get("state") == "completed":
                return {
                    "status": "finalized",
                    "detail": "Cette session est déjà finalisée",
                    "session": metadata,
                }
            if metadata["centering"].get("status") != "centered":
                return {
                    "status": "centering_required",
                    "detail": "Recentrage obligatoire avant la reprise",
                    "session": metadata,
                }

            checkpoint = self._parse_utc(
                metadata["stacking"].get("resume_verification_after")
            )
            verified = self._parse_utc(
                metadata["centering"].get("verified_at")
            )
            if checkpoint is not None and (verified is None or verified <= checkpoint):
                return {
                    "status": "centering_required",
                    "detail": "Une nouvelle pose astrométrique doit valider le centrage avant la reprise",
                    "session": metadata,
                }

            metadata["stacking"]["recenter_required"] = False
            metadata["stacking"]["recenter_reason"] = None
            metadata["stacking"]["resume_verification_after"] = None
            return self._launch_stack_locked(session_id, metadata)

    def stop_stack(self, session_id: str) -> dict:
        with self._lock:
            metadata = self._read(session_id)
            event = self._stop_events.get(session_id)
            if event is not None:
                event.set()
            metadata["stacking"]["stop_requested"] = True
            metadata["state"] = (
                "stopping"
                if metadata["stacking"].get("running")
                else "stopped"
            )
            self._write(metadata)
            return {"status": metadata["state"], "session": metadata}

    def _build_final_stack(self, session_id: str) -> tuple[dict, Path]:
        registered_dir = self._session_dir(session_id) / "registered"
        paths = sorted(registered_dir.glob("*.fits"))
        if not paths:
            raise ValueError("Aucune image acceptée à finaliser")
        final_path = self._session_dir(session_id) / "stack" / "final.fits"
        result = build_sigma_clipped_stack(paths, final_path)
        preview_path = self._session_dir(session_id) / "stack" / "final.jpg"
        preview_path.write_bytes(self._fits_preview_bytes(final_path))
        result["preview"] = str(preview_path)
        return result, preview_path

    def finalize(self, session_id: str) -> dict:
        with self._lock:
            metadata = self._read(session_id)
            thread = self._threads.get(session_id)
            if thread is not None and thread.is_alive():
                return {
                    "status": "stacking_running",
                    "detail": "Arrêtez le stacking avant l'enregistrement",
                    "session": metadata,
                }
            if int(metadata["counts"].get("accepted") or 0) < 1:
                return {
                    "status": "error",
                    "detail": "Aucune image acceptée à enregistrer",
                    "session": metadata,
                }
            metadata["state"] = "finalizing"
            self._write(metadata)

        try:
            final_stack, final_preview = self._build_final_stack(session_id)
        except Exception as exc:
            with self._lock:
                metadata = self._read(session_id)
                metadata["state"] = "finalize_error"
                metadata["stacking"]["error"] = str(exc)
                self._write(metadata)
                return {
                    "status": "error",
                    "detail": f"Stack final impossible: {exc}",
                    "session": metadata,
                }

        with self._lock:
            metadata = self._read(session_id)
            final_fits = Path(final_stack["path"])
            metadata["final_stack"] = final_stack
            metadata["stack_fits"] = str(final_fits)
            metadata["stack_preview"] = str(final_preview)
            metadata["preview"] = str(final_preview)

            created = datetime.fromisoformat(metadata["created_at"])
            gallery_dir = (
                self.galleries_root
                / f"{created.year:04d}"
                / f"{created.date().isoformat()}"
                / session_id
            )
            gallery_dir.mkdir(parents=True, exist_ok=True)

            shutil.copy2(final_fits, gallery_dir / "final.fits")
            shutil.copy2(final_preview, gallery_dir / "final.jpg")
            shutil.copy2(final_preview, gallery_dir / "thumbnail.jpg")

            metadata["state"] = "completed"
            metadata["gallery_path"] = str(gallery_dir)
            metadata["stacking"]["finalized_at"] = self._utc_now()
            self._write(metadata)
            (gallery_dir / "session.json").write_text(
                json.dumps(metadata, ensure_ascii=False, indent=2),
                encoding="utf-8",
            )
            return {"status": "completed", "session": metadata}

    def list_galleries(self) -> list[dict]:
        if not self.galleries_root.exists():
            return []
        items = []
        for metadata_path in self.galleries_root.rglob("session.json"):
            try:
                item = json.loads(metadata_path.read_text(encoding="utf-8"))
                item["gallery_preview"] = str(metadata_path.parent / "final.jpg")
                items.append(item)
            except (OSError, json.JSONDecodeError):
                continue
        items.sort(key=lambda item: item.get("created_at", ""), reverse=True)
        return items

    def preview_bytes(self, session_id: str, stack: bool = False) -> bytes:
        metadata = self._read(session_id)
        candidate = metadata.get("stack_preview") if stack else metadata.get("preview")
        if candidate and Path(candidate).exists():
            return Path(candidate).read_bytes()
        source = metadata.get("stack_fits") if stack else metadata.get("last_frame")
        if source and Path(source).exists():
            return self._fits_preview_bytes(Path(source))
        raise FileNotFoundError(session_id)

    def gallery_preview_bytes(self, session_id: str) -> bytes:
        for item in self.list_galleries():
            if item.get("id") != session_id:
                continue
            path = Path(item["gallery_preview"])
            if path.exists():
                return path.read_bytes()
        raise FileNotFoundError(session_id)


capture_session_service = CaptureSessionService()
