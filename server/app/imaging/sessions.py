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

from app.imaging.quality import analyze_fits
from app.indi.service import indi_service
from app.solving.service import plate_solver


SERVER_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_RUNTIME_ROOT = SERVER_ROOT / "tmp"
DEFAULT_GALLERIES_ROOT = SERVER_ROOT / "data" / "galleries"


class CaptureSessionService:
    """Persistent capture workspace and first live-stacking pipeline.

    Runtime files intentionally live below ``stellarpilot-server/tmp`` and
    never below the operating-system ``/tmp``.  A session keeps every stage
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
    def _safe_slug(value: str) -> str:
        cleaned = "".join(
            char if char.isalnum() else "-"
            for char in (value or "target").strip()
        )
        cleaned = "-".join(part for part in cleaned.split("-") if part)
        return cleaned[:48] or "target"

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
            },
            "integration_seconds": 0.0,
            "centering": {
                "status": "not_checked",
                "attempts": 0,
                "error_arcsec": None,
                "solve_ra_deg": None,
                "solve_dec_deg": None,
                "correction_ra_hours": None,
                "correction_dec_deg": None,
                "image": None,
            },
            "stacking": {
                "running": False,
                "stop_requested": False,
                "recenter_required": False,
                "recenter_reason": None,
                "last_registration_dx_px": None,
                "last_registration_dy_px": None,
                "last_registration_distance_px": None,
                "last_astrometry_frame": 0,
                "registration_mode": "translation-even-pixel-v1",
            },
            "last_frame": None,
            "preview": None,
            "stack_fits": None,
            "stack_preview": None,
            "gallery_path": None,
        }
        return self._write(metadata)

    def get_session(self, session_id: str) -> dict:
        with self._lock:
            metadata = self._read(session_id)
            thread = self._threads.get(session_id)
            if thread is not None and not thread.is_alive():
                metadata["stacking"]["running"] = False
                self._threads.pop(session_id, None)
                self._write(metadata)
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
                sessions.append(
                    json.loads(metadata_path.read_text(encoding="utf-8"))
                )
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
        accepted_dir = session_dir / "accepted"
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
        fits.PrimaryHDU(data=stacked, header=header).writeto(
            stack_fits,
            overwrite=True,
        )
        stack_preview = stack_dir / "current.jpg"
        stack_preview.write_bytes(self._fits_preview_bytes(stack_fits))

        accepted_path = accepted_dir / image_path.name
        if image_path.exists():
            image_path.replace(accepted_path)

        return stack_fits, stack_preview, {
            "dx_px": dx,
            "dy_px": dy,
            "distance_px": round(distance, 3),
            "registered_image": str(registered_path),
            "accepted_image": str(accepted_path),
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

    def _stack_worker(self, session_id: str, stop_event: threading.Event) -> None:
        try:
            while not stop_event.is_set():
                with self._lock:
                    metadata = self._read(session_id)
                    if metadata["stacking"].get("recenter_required"):
                        break
                    frame_number = int(metadata["counts"]["captured"]) + 1

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

                image_path = Path(capture["image"])
                quality = analyze_fits(str(image_path))
                with self._lock:
                    metadata["counts"]["captured"] += 1

                if (
                    quality.get("status") != "ok"
                    or quality.get("classification") == "overexposed"
                ):
                    rejected_path = (
                        self._session_dir(session_id)
                        / "rejected"
                        / image_path.name
                    )
                    if image_path.exists():
                        image_path.replace(rejected_path)
                    with self._lock:
                        metadata["counts"]["rejected"] += 1
                        metadata["last_frame"] = str(rejected_path)
                        metadata["last_quality"] = quality
                        self._write(metadata)
                    continue

                try:
                    stack_fits, stack_preview, registration = self._update_stack(
                        session_id,
                        image_path,
                    )
                except Exception as exc:
                    rejected_path = (
                        self._session_dir(session_id)
                        / "rejected"
                        / image_path.name
                    )
                    if image_path.exists():
                        image_path.replace(rejected_path)
                    with self._lock:
                        metadata["counts"]["rejected"] += 1
                        metadata["last_frame"] = str(rejected_path)
                        metadata["last_quality"] = quality
                        metadata["stacking"]["last_registration_error"] = str(exc)
                        self._write(metadata)
                    continue

                with self._lock:
                    metadata["counts"]["accepted"] += 1
                    metadata["integration_seconds"] = round(
                        metadata["counts"]["accepted"]
                        * metadata["setup"]["exposure_s"],
                        3,
                    )
                    metadata["stack_fits"] = str(stack_fits)
                    metadata["stack_preview"] = str(stack_preview)
                    metadata["preview"] = str(stack_preview)
                    metadata["last_frame"] = registration["accepted_image"]
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
                    accepted = int(metadata["counts"]["accepted"])
                    interval = max(
                        1,
                        int(metadata["setup"]["astrometry_interval_frames"]),
                    )
                    registration_trigger = (
                        registration["distance_px"]
                        >= metadata["setup"]["registration_recenter_pixels"]
                    )
                    astrometry_due = accepted % interval == 0
                    self._write(metadata)

                if astrometry_due or registration_trigger:
                    drift = self._check_astrometry_drift(
                        metadata,
                        registration["accepted_image"],
                    )
                    with self._lock:
                        metadata = self._read(session_id)
                        metadata["stacking"]["last_astrometry_frame"] = accepted
                        metadata["stacking"]["last_astrometry"] = drift
                        if drift and drift["status"] == "correction_required":
                            metadata["stacking"]["recenter_required"] = True
                            metadata["stacking"]["recenter_reason"] = (
                                "registration_drift"
                                if registration_trigger
                                else "periodic_astrometry"
                            )
                            metadata["stacking"]["running"] = False
                            metadata["state"] = "paused_recenter"
                            metadata["centering"].update(drift)
                            self._write(metadata)
                            break
                        self._write(metadata)

        finally:
            with self._lock:
                try:
                    metadata = self._read(session_id)
                    metadata["stacking"]["running"] = False
                    if metadata["state"] == "stacking":
                        metadata["state"] = "stopped"
                    self._write(metadata)
                except KeyError:
                    pass
                self._threads.pop(session_id, None)

    def start_stack(self, session_id: str) -> dict:
        with self._lock:
            metadata = self._read(session_id)
            if metadata["centering"].get("status") != "centered":
                return {
                    "status": "centering_required",
                    "detail": "Validate astrometric centering before stacking",
                    "session": metadata,
                }
            thread = self._threads.get(session_id)
            if thread is not None and thread.is_alive():
                return {"status": "already_running", "session": metadata}

            stop_event = threading.Event()
            self._stop_events[session_id] = stop_event
            metadata["stacking"]["running"] = True
            metadata["stacking"]["stop_requested"] = False
            metadata["stacking"]["recenter_required"] = False
            metadata["stacking"]["recenter_reason"] = None
            metadata["state"] = "stacking"
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

    def resume_stack(self, session_id: str) -> dict:
        with self._lock:
            metadata = self._read(session_id)
            if metadata["centering"].get("status") != "centered":
                return {
                    "status": "centering_required",
                    "detail": "Re-centering must be verified before resuming",
                    "session": metadata,
                }
            metadata["stacking"]["recenter_required"] = False
            metadata["stacking"]["recenter_reason"] = None
            self._write(metadata)
        return self.start_stack(session_id)

    def stop_stack(self, session_id: str) -> dict:
        with self._lock:
            metadata = self._read(session_id)
            event = self._stop_events.get(session_id)
            if event is not None:
                event.set()
            metadata["stacking"]["stop_requested"] = True
            metadata["state"] = "stopping" if metadata["stacking"]["running"] else "stopped"
            self._write(metadata)
            return {"status": metadata["state"], "session": metadata}

    def finalize(self, session_id: str) -> dict:
        with self._lock:
            metadata = self._read(session_id)
            thread = self._threads.get(session_id)
            if thread is not None and thread.is_alive():
                return {
                    "status": "stacking_running",
                    "detail": "Stop stacking before finalizing",
                    "session": metadata,
                }

            created = datetime.fromisoformat(metadata["created_at"])
            gallery_dir = (
                self.galleries_root
                / f"{created.year:04d}"
                / f"{created.date().isoformat()}"
                / session_id
            )
            gallery_dir.mkdir(parents=True, exist_ok=True)

            if metadata.get("stack_fits") and Path(metadata["stack_fits"]).exists():
                shutil.copy2(metadata["stack_fits"], gallery_dir / "final.fits")
            if metadata.get("stack_preview") and Path(metadata["stack_preview"]).exists():
                shutil.copy2(metadata["stack_preview"], gallery_dir / "final.jpg")
                shutil.copy2(metadata["stack_preview"], gallery_dir / "thumbnail.jpg")

            metadata["state"] = "completed"
            metadata["gallery_path"] = str(gallery_dir)
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
