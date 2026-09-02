import json
from pathlib import Path

import pytest

from app.imaging.preparation_astrometry import PreparationAstrometryArchive
from app.indi._service_core import IndiService as CoreIndiService
from app.indi import service as service_module


def test_legacy_camera_capture_is_persisted_before_return(
    tmp_path,
    monkeypatch,
):
    source_dir = tmp_path / "os-tmp"
    source_dir.mkdir()
    source = source_dir / "camera.fits"
    source.write_bytes(b"SIMPLE  = T / fake fits for persistence test")

    def fake_core_capture(self, exposure_s):
        return {
            "status": "captured",
            "image": str(source),
            "exposure_s": exposure_s,
            "camera": "Fake CCD",
        }

    monkeypatch.setattr(
        CoreIndiService,
        "capture",
        fake_core_capture,
    )
    monkeypatch.setattr(
        service_module,
        "preparation_astrometry_archive",
        PreparationAstrometryArchive(
            tmp_path / "persistent" / "assistant-3"
        ),
    )

    service = service_module.IndiService()
    result = service.capture(4.0)

    assert result["status"] == "captured"
    assert result["storage"] == "assistant-3-persistent"

    archive = result["persistent_astrometry"]
    persisted = Path(archive["fits"])
    assert archive["verified"] is True
    assert archive["source_size_bytes"] == source.stat().st_size
    assert archive["fits_size_bytes"] == source.stat().st_size
    assert persisted.exists()
    assert persisted.read_bytes() == source.read_bytes()
    assert "assistant-3" in persisted.parts
    assert persisted.name == "initial.fits"

    metadata = json.loads(
        Path(archive["metadata"]).read_text(encoding="utf-8")
    )
    assert metadata["archive"]["verified"] is True
    assert metadata["archive"]["fits_size_bytes"] == source.stat().st_size


def test_archive_rejects_non_fits_payload(tmp_path):
    source = tmp_path / "broken.fits"
    source.write_bytes(b"not a fits file")
    archive = PreparationAstrometryArchive(tmp_path / "archive")

    with pytest.raises(RuntimeError, match="SIMPLE"):
        archive.archive_capture(
            {
                "status": "captured",
                "image": str(source),
                "exposure_s": 1.0,
            }
        )


def test_complete_solver_response_is_persisted(tmp_path):
    source = tmp_path / "camera.fits"
    source.write_bytes(b"SIMPLE  = T / fake fits for solve archive")
    archive = PreparationAstrometryArchive(tmp_path / "archive")
    capture = archive.archive_capture(
        {
            "status": "captured",
            "image": str(source),
            "exposure_s": 4.0,
        }
    )

    solution = {
        "status": "unsolved",
        "solver": "astrometry.net",
        "strategy": "exhausted",
        "total_duration_s": 12.5,
        "detail": "all attempts failed",
        "position_hint": {"ra_deg": 10.0, "dec_deg": 20.0},
        "attempts": [
            {
                "strategy": "scale_narrow",
                "status": "unsolved",
                "duration_s": 2.5,
                "radius_deg": 8.0,
            }
        ],
    }

    archive.record_solve(str(source), solution)

    solve_path = Path(capture["solve"])
    assert solve_path.exists()
    assert json.loads(solve_path.read_text(encoding="utf-8")) == solution

    metadata = json.loads(
        Path(capture["metadata"]).read_text(encoding="utf-8")
    )
    assert metadata["files"]["solve"] == "solve.json"
    assert metadata["solver"]["strategy"] == "exhausted"
    assert metadata["solver"]["attempts"] == solution["attempts"]


def test_session_capture_keeps_runtime_move_without_assistant_archive(
    tmp_path,
    monkeypatch,
):
    source_dir = tmp_path / "os-tmp"
    source_dir.mkdir()
    source = source_dir / "camera.fits"
    source.write_bytes(b"session frame")

    def fake_core_capture(self, exposure_s):
        return {
            "status": "captured",
            "image": str(source),
            "exposure_s": exposure_s,
        }

    monkeypatch.setattr(
        CoreIndiService,
        "capture",
        fake_core_capture,
    )

    archive_root = tmp_path / "persistent" / "assistant-3"
    monkeypatch.setattr(
        service_module,
        "preparation_astrometry_archive",
        PreparationAstrometryArchive(archive_root),
    )

    service = service_module.IndiService()
    session_dir = tmp_path / "runtime-session"
    result = service.capture(
        4.0,
        output_dir=session_dir,
        prefix="center_001",
    )

    assert result["status"] == "captured"
    assert result["storage"] == "session"
    assert Path(result["image"]).exists()
    assert not archive_root.exists()
