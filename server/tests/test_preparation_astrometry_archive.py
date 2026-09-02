import json
from pathlib import Path

from app.imaging.preparation_astrometry import PreparationAstrometryArchive


FITS_HEADER = b"SIMPLE  ="


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
    payload = FITS_HEADER + b" FITS-FIRST-ASTROMETRY"
    source.write_bytes(payload)

    archive = PreparationAstrometryArchive(
        root=tmp_path / "server" / "data" / "astrometry" / "assistant-3"
    )

    saved = archive.archive_capture(_capture_result(source))

    fits_path = Path(saved["fits"])
    metadata_path = Path(saved["metadata"])

    assert fits_path.exists()
    assert fits_path.read_bytes() == payload
    assert fits_path.name == "initial.fits"
    assert metadata_path.exists()
    assert not Path(saved["preview"]).exists()
    assert saved["verified"] is True
    assert saved["source_size_bytes"] == len(payload)
    assert saved["fits_size_bytes"] == len(payload)

    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    assert metadata["workflow"] == "preparation_assistant_3_first_astrometry"
    assert metadata["exposure_s"] == 2.5
    assert metadata["camera"] == "Fake CCD"
    assert metadata["files"]["preview"] is None
    assert metadata["solver"]["status"] is None
    assert metadata["archive_verification"]["verified"] is True


def test_assistant_3_preview_and_solve_enrich_same_archive(tmp_path):
    source = tmp_path / "stellarpilot-captures" / "capture.fits"
    source.parent.mkdir(parents=True)
    source.write_bytes(FITS_HEADER + b" TEST")

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

    solve_payload = {
        "status": "solved",
        "solver": "astrometry.net",
        "ra": 23.287695,
        "dec": 60.600901,
        "orientation_deg": -67.55,
        "pixel_scale_arcsec": 1.3227,
        "stars_detected": 42,
        "detail": None,
        "strategy": "scale_narrow",
        "attempts": [
            {
                "strategy": "scale_narrow",
                "status": "solved",
                "duration_s": 1.2,
            }
        ],
    }
    archive.record_solve(str(source), solve_payload)

    preview_path = Path(saved["preview"])
    metadata_path = Path(saved["metadata"])
    solve_path = Path(saved["solve"])

    assert preview_path.read_bytes() == b"JPEG-PREVIEW"
    assert solve_path.exists()
    assert json.loads(solve_path.read_text(encoding="utf-8")) == solve_payload

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
    payload1 = FITS_HEADER + b" FIRST"
    payload2 = FITS_HEADER + b" SECOND"
    source1.write_bytes(payload1)
    source2.write_bytes(payload2)

    archive = PreparationAstrometryArchive(root=tmp_path / "archive")

    first = archive.archive_capture(_capture_result(source1))
    second = archive.archive_capture(_capture_result(source2))

    assert first["directory"] != second["directory"]
    assert Path(first["fits"]).read_bytes() == payload1
    assert Path(second["fits"]).read_bytes() == payload2
