from __future__ import annotations

import os
import re
import sqlite3
import subprocess
from pathlib import Path
from typing import Any


DEFAULT_KSTARS_DB = Path.home() / ".local" / "share" / "kstars" / "userdb.sqlite"
_SCOPE_RE = re.compile(
    r"^(?P<name>.*?)\s+(?P<focal>\d+(?:\.\d+)?)@F/(?P<ratio>\d+(?:\.\d+)?)$",
    re.IGNORECASE,
)


def _norm(value: str | None) -> str:
    return " ".join((value or "").strip().lower().split())


def _as_float(value: Any) -> float | None:
    try:
        if value is None:
            return None
        return float(value)
    except (TypeError, ValueError):
        return None


def _plausible_aperture_mm(value: float | None) -> bool:
    return value is not None and 20.0 <= value <= 2000.0


class SetupService:
    """Read the optical setup configured in KStars/Ekos.

    INDI remains the source of truth for devices that are actually connected.
    KStars is used only for optical metadata that common INDI drivers do not
    necessarily publish (optical-train name, telescope model, focal length,
    aperture and reducer).
    """

    @property
    def database(self) -> Path:
        configured = os.environ.get("STELLARPILOT_KSTARS_DB")
        return Path(configured).expanduser() if configured else DEFAULT_KSTARS_DB

    @staticmethod
    def _kstars_running() -> bool:
        try:
            result = subprocess.run(
                ["pgrep", "-x", "kstars"],
                capture_output=True,
                text=True,
                timeout=1,
                check=False,
            )
            return result.returncode == 0 and bool(result.stdout.strip())
        except (OSError, subprocess.SubprocessError):
            return False

    @staticmethod
    def _choose_train(
        trains: list[dict[str, Any]],
        mount_name: str | None,
        camera_name: str | None,
    ) -> tuple[dict[str, Any] | None, int]:
        if not trains:
            return None, 0

        mount_norm = _norm(mount_name)
        camera_norm = _norm(camera_name)
        scored: list[tuple[int, int, dict[str, Any]]] = []

        for train in trains:
            score = 0
            train_mount = _norm(train.get("mount"))
            train_camera = _norm(train.get("camera"))

            if mount_norm and train_mount:
                if mount_norm == train_mount:
                    score += 4
                elif mount_norm in train_mount or train_mount in mount_norm:
                    score += 2

            if camera_norm and train_camera:
                if camera_norm == train_camera:
                    score += 4
                elif camera_norm in train_camera or train_camera in camera_norm:
                    score += 2

            scored.append((score, int(train.get("id") or 0), train))

        scored.sort(key=lambda item: (item[0], item[1]), reverse=True)
        best_score, _, best_train = scored[0]

        if best_score == 0 and len(trains) == 1:
            return trains[0], 0

        return best_train, best_score

    @staticmethod
    def _match_telescope(
        telescopes: list[dict[str, Any]],
        scope_name: str | None,
    ) -> dict[str, Any] | None:
        scope_norm = _norm(scope_name)
        if not scope_norm:
            return None

        candidates: list[tuple[int, dict[str, Any]]] = []
        for telescope in telescopes:
            vendor = str(telescope.get("Vendor") or "").strip()
            model = str(telescope.get("Model") or "").strip()
            full_name = " ".join(part for part in (vendor, model) if part)
            score = 0

            if full_name and _norm(full_name) in scope_norm:
                score += 4
            elif model and _norm(model) in scope_norm:
                score += 2

            if score:
                candidates.append((score, telescope))

        if not candidates:
            return None

        candidates.sort(key=lambda item: item[0], reverse=True)
        return candidates[0][1]

    def status(self, indi_snapshot: dict[str, Any] | None = None) -> dict[str, Any]:
        indi_snapshot = indi_snapshot or {}
        mount = indi_snapshot.get("mount") or {}
        camera = indi_snapshot.get("camera") or {}
        mount_name = mount.get("name")
        camera_name = camera.get("name")
        database = self.database

        base = {
            "status": "unavailable",
            "source": "kstars",
            "database": str(database),
            "database_available": database.exists(),
            "kstars_running": self._kstars_running(),
            "optical_train_id": None,
            "optical_train_name": None,
            "mount": mount_name,
            "camera": camera_name,
            "scope_config": None,
            "telescope_name": None,
            "telescope_type": None,
            "aperture_mm": None,
            "focal_length_mm": None,
            "focal_ratio": None,
            "reducer": None,
            "effective_focal_length_mm": None,
            "effective_focal_ratio": None,
            "consistency": "unverified",
            "detail": None,
        }

        if not database.exists():
            base["detail"] = "Base utilisateur KStars introuvable"
            return base

        try:
            connection = sqlite3.connect(database)
            connection.row_factory = sqlite3.Row
            trains = [
                dict(row)
                for row in connection.execute(
                    "SELECT * FROM opticaltrains ORDER BY id"
                )
            ]
            telescopes = [
                dict(row)
                for row in connection.execute(
                    "SELECT * FROM telescope ORDER BY id"
                )
            ]
        except (sqlite3.Error, OSError) as exc:
            base["detail"] = f"Lecture KStars impossible: {exc}"
            return base
        finally:
            try:
                connection.close()  # type: ignore[name-defined]
            except Exception:
                pass

        train, match_score = self._choose_train(
            trains,
            mount_name=mount_name,
            camera_name=camera_name,
        )
        if train is None:
            base["detail"] = "Aucun train optique KStars configure"
            return base

        scope_config = str(train.get("scope") or "").strip() or None
        reducer = _as_float(train.get("reducer"))
        parsed_scope = _SCOPE_RE.match(scope_config or "")
        parsed_focal = _as_float(parsed_scope.group("focal")) if parsed_scope else None
        parsed_ratio = _as_float(parsed_scope.group("ratio")) if parsed_scope else None
        telescope = self._match_telescope(telescopes, scope_config)

        telescope_name = None
        telescope_type = None
        aperture_mm = None
        focal_length_mm = None
        focal_ratio = None

        if telescope is not None:
            vendor = str(telescope.get("Vendor") or "").strip()
            model = str(telescope.get("Model") or "").strip()
            telescope_name = " ".join(
                part for part in (vendor, model) if part
            ) or None
            telescope_type = str(telescope.get("Type") or "").strip() or None
            raw_aperture = _as_float(telescope.get("Aperture"))
            raw_focal = _as_float(telescope.get("FocalLength"))

            if _plausible_aperture_mm(raw_aperture):
                aperture_mm = raw_aperture
                focal_length_mm = raw_focal
                if raw_focal and raw_aperture:
                    focal_ratio = raw_focal / raw_aperture

        if focal_length_mm is None:
            focal_length_mm = parsed_focal
        if focal_ratio is None:
            focal_ratio = parsed_ratio

        effective_focal = (
            focal_length_mm * reducer
            if focal_length_mm is not None and reducer is not None
            else focal_length_mm
        )
        effective_ratio = (
            focal_ratio * reducer
            if focal_ratio is not None and reducer is not None
            else focal_ratio
        )

        detail = None
        consistency = "ok"
        if match_score < 4:
            consistency = "warning"
            detail = "Train KStars non confirme par les peripheriques INDI"
        if scope_config and "sample " in scope_config.lower():
            consistency = "warning"
            detail = "Le train optique KStars utilise encore un telescope Sample"
        if telescope is None and scope_config:
            consistency = "warning"
            detail = detail or "Le telescope du train optique ne correspond pas a la table KStars"

        return {
            **base,
            "status": "ready",
            "optical_train_id": train.get("id"),
            "optical_train_name": train.get("name"),
            "scope_config": scope_config,
            "telescope_name": telescope_name,
            "telescope_type": telescope_type,
            "aperture_mm": aperture_mm,
            "focal_length_mm": focal_length_mm,
            "focal_ratio": round(focal_ratio, 3) if focal_ratio is not None else None,
            "reducer": reducer,
            "effective_focal_length_mm": (
                round(effective_focal, 3) if effective_focal is not None else None
            ),
            "effective_focal_ratio": (
                round(effective_ratio, 3) if effective_ratio is not None else None
            ),
            "consistency": consistency,
            "detail": detail,
        }


setup_service = SetupService()
