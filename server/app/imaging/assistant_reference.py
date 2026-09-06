from __future__ import annotations

import os
from pathlib import Path

from app.imaging.bahtinov_focus import analyze_bahtinov
from app.imaging.quality import analyze_fits
from app.imaging.sessions import CaptureSessionService
from app.solving.service import plate_solver


REFERENCE_ROOT = Path(
    os.environ.get("STELLARPILOT_REFERENCE_ROOT", "data/reference-v2")
)


def latest_reference_root() -> Path | None:
    if not REFERENCE_ROOT.exists():
        return None

    dated = sorted(
        [item for item in REFERENCE_ROOT.iterdir() if item.is_dir()],
        key=lambda item: item.name,
        reverse=True,
    )
    return dated[0] if dated else None


def orientation_files(root: Path) -> list[Path]:
    candidates: list[Path] = []
    for relative in (
        "orientation/north",
        "orientation/east",
        "orientation/unclassified",
        "orientation/candidates",
    ):
        folder = root / relative
        if folder.exists():
            candidates.extend(sorted(folder.glob("*.fits")))
    return candidates


def bahtinov_files(root: Path, kind: str) -> list[Path]:
    normalized = kind.strip().lower()
    aliases = {
        "optimum": "optimum",
        "side_a": "side_a",
        "side_b": "side_b",
    }
    folder_name = aliases.get(normalized)
    if folder_name is None:
        return []
    folder = root / "bahtinov" / folder_name
    return sorted(folder.glob("*.fits")) if folder.exists() else []


def _pick(paths: list[Path], index: int) -> Path:
    if not paths:
        raise FileNotFoundError("Aucune image de référence disponible")
    return paths[index % len(paths)]


def catalog() -> dict:
    root = latest_reference_root()
    if root is None:
        return {
            "status": "reference_missing",
            "reference_root": None,
            "astrometry": [],
            "bahtinov": {"optimum": [], "side_a": [], "side_b": []},
        }

    astrometry = orientation_files(root)
    return {
        "status": "ok",
        "reference_root": str(root),
        "astrometry": [
            {"index": index, "name": path.name, "image": str(path)}
            for index, path in enumerate(astrometry)
        ],
        "bahtinov": {
            kind: [
                {"index": index, "name": path.name, "image": str(path)}
                for index, path in enumerate(bahtinov_files(root, kind))
            ]
            for kind in ("side_a", "optimum", "side_b")
        },
    }


def astrometry_reference(index: int = 0) -> dict:
    root = latest_reference_root()
    if root is None:
        raise FileNotFoundError("Référentiel StellarPilot introuvable")

    path = _pick(orientation_files(root), index)
    quality = analyze_fits(str(path))
    solution = plate_solver.solve_robust(str(path))

    return {
        "status": "ok",
        "mode": "reference_test",
        "simulated_actions": ["capture_4s", "mount_sync"],
        "reference_root": str(root),
        "index": index,
        "name": path.name,
        "image": str(path),
        "quality": quality,
        "solve": solution,
    }


def bahtinov_reference(kind: str, index: int = 0) -> dict:
    root = latest_reference_root()
    if root is None:
        raise FileNotFoundError("Référentiel StellarPilot introuvable")

    path = _pick(bahtinov_files(root, kind), index)
    analysis = analyze_bahtinov(str(path))
    if analysis.get("status") != "ok":
        raise ValueError(analysis.get("detail", "Analyse Bahtinov impossible"))

    return {
        "status": "ok",
        "mode": "reference_test",
        "simulated_actions": ["capture_4s"],
        "reference_root": str(root),
        "kind": kind,
        "index": index,
        "name": path.name,
        "image": str(path),
        "analysis": analysis,
    }


def preview_bytes(image: str) -> bytes:
    path = Path(image)
    root = latest_reference_root()
    if root is None:
        raise FileNotFoundError("Référentiel StellarPilot introuvable")

    resolved = path.resolve()
    root_resolved = root.resolve()
    if root_resolved not in resolved.parents:
        raise PermissionError("Image hors référentiel")

    return CaptureSessionService._fits_preview_bytes(path)
