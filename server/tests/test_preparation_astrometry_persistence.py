from pathlib import Path

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
    assert persisted.exists()
    assert persisted.read_bytes() == source.read_bytes()
    assert "assistant-3" in persisted.parts
    assert persisted.name == "initial.fits"


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
