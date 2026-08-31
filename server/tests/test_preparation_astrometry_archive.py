import json
from pathlib import Path

from app.imaging.preparation_astrometry import PreparationAstrometryArchive


def _capture_result(image: Path, exposure_s: float = 2.5):
    return {
        "status": "captured",
        "image": str(image),
        "exposure_s": exposure_s,
        "camera": "Fake CCD",
    }


def test_assistant_3_fits_is_persisted_before_preview_and_solve(tmp_path):
    source = tmp_path / "stellarpilot-captures" / "capture.fits"
    source.parent.mkdir(parents=True)
    source.write_bytes(b"FITS-FIRST-ASTROMETRY")

    archive = PreparationAstrometryArchive(
        root=tmp_path / "server" / "data" / "astrometry" / "assistant-3"
    )

    saved = archive.archive_capture(_capture_result(source))

    fits_path = Path(saved["fits"])
    metadata_path = Path(saved["metadata"])

    assert fits_path.exists()
    assert fits_path.read_bytes() == b"FITS-FIRST-ASTROMETRY"
    assert fits_path.name == "initial.fits"
    assert metadata_path.exists()
    assert not Path(saved["preview"]).exists()

    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    assert metadata["workflow"] == "preparation_assistant_3_first_astrometry"
    assert metadata["exposure_s"] == 2.5
    assert metadata["camera"] == "Fake CCD"
    assert metadata["files"]["preview"] is None
    assert metadata["solver"]["status"] is None


def test_assistant_3_preview_and_solve_enrich_same_archive(tmp_path):
    source = tmp_path / "stellarpilot-captures" / "capture.fits"
    source.parent.mkdir(parents=True)
    source.write_bytes(b"FITS")

    archive = PreparationAstrometryArchive(
        root=tmp_path / "astrometry" / "assistant-3"
    )
    saved = archive.archive_capture(_capture_result(source))

    archive.record_preview(
        source.name,
        b"JPEG-PREVIEW",
        headers={
            "X-StellarPilot-Preview-Status": "structured",
            "X-StellarPilot-Bayer": "RGGB",
            "X-StellarPilot-Preview-Background": "100.0",
            "X-StellarPilot-Preview-Noise": "5.0",
            "X-StellarPilot-Preview-Contrast": "12.0",
        },
    )

    archive.record_solve(
        str(source),
        {
            "status": "solved",
            "solver": "astrometry.net",
            "ra": 23.287695,
            "dec": 60.600901,
            "orientation_deg": -67.55,
            "pixel_scale_arcsec": 1.3227,
            "stars_detected": 42,
            "detail": None,
        },
    )

    preview_path = Path(saved["preview"])
    metadata_path = Path(saved["metadata"])

    assert preview_path.read_bytes() == b"JPEG-PREVIEW"

    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    assert metadata["files"]["preview"] == "initial.jpg"
    assert metadata["preview"]["status"] == "structured"
    assert metadata["solver"]["status"] == "solved"
    assert metadata["solver"]["ra_deg"] == 23.287695
    assert metadata["solver"]["dec_deg"] == 60.600901
    assert metadata["solver"]["stars_detected"] == 42


def test_each_assistant_3_capture_gets_its_own_persistent_directory(tmp_path):
    source1 = tmp_path / "c1.fits"
    source2 = tmp_path / "c2.fits"
    source1.write_bytes(b"FIRST")
    source2.write_bytes(b"SECOND")

    archive = PreparationAstrometryArchive(root=tmp_path / "archive")

    first = archive.archive_capture(_capture_result(source1))
    second = archive.archive_capture(_capture_result(source2))

    assert first["directory"] != second["directory"]
    assert Path(first["fits"]).read_bytes() == b"FIRST"
    assert Path(second["fits"]).read_bytes() == b"SECOND"
