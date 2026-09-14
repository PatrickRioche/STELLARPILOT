from pathlib import Path

import numpy as np
from astropy.io import fits

from app.imaging import darks


class FakeIndi:
    def status_snapshot(self):
        return {
            "mount": {
                "status": "ready",
                "name": "LX200 OnStep",
            },
            "camera": {
                "status": "ready",
                "name": "PlayerOne CCD Uranus-C",
                "sensor": {
                    "width": 64,
                    "height": 64,
                    "pixel_size_um": 2.9,
                    "pixel_size_x_um": 2.9,
                    "pixel_size_y_um": 2.9,
                    "bits_per_pixel": 16,
                },
                "capture": {
                    "gain": 200.0,
                    "offset": 20.0,
                    "bin_x": 1,
                    "bin_y": 1,
                    "frame_width": 64,
                    "frame_height": 64,
                    "frame_type": "dark",
                },
                "temperature_c": 12.5,
            },
            "location": {"status": "unavailable"},
        }


class FakeSetup:
    def status(self, indi_snapshot):
        assert indi_snapshot["camera"]["name"] == "PlayerOne CCD Uranus-C"
        return {
            "status": "ready",
            "source": "kstars",
            "optical_train_name": "Imageur",
            "telescope_name": "ASKAR 71F",
            "telescope_type": "Réfracteur",
            "aperture_mm": 71.0,
            "focal_length_mm": 490.0,
            "focal_ratio": 6.901,
            "reducer": 1.0,
            "effective_focal_length_mm": 490.0,
            "effective_focal_ratio": 6.901,
            "consistency": "ok",
        }


def _write_dark(path: Path, image: np.ndarray, exposure_s: float) -> None:
    header = fits.Header()
    header["BAYERPAT"] = "RGGB"
    header["EXPTIME"] = exposure_s
    header["GAIN"] = 200
    header["OFFSET"] = 20
    header["CCD-TEMP"] = 12.5
    fits.writeto(path, image, header=header, overwrite=True)


def test_dark_session_builds_master_and_hot_pixel_map(tmp_path, monkeypatch):
    monkeypatch.setattr(darks, "DARK_ROOT", tmp_path / "darks")
    monkeypatch.setattr(darks, "indi_service", FakeIndi())
    monkeypatch.setattr(darks, "setup_service", FakeSetup())

    counter = {"value": 0}

    def fake_capture_dark(exposure_s, *, output_dir, prefix):
        counter["value"] += 1
        output = Path(output_dir)
        output.mkdir(parents=True, exist_ok=True)
        path = output / f"{prefix}.fits"
        image = np.full((64, 64), 120, dtype=np.uint16)
        image[2, 2] = 1200
        image[10, 10] = 200 + counter["value"]
        _write_dark(path, image, exposure_s)
        return {
            "status": "captured",
            "image": str(path),
            "exposure_s": exposure_s,
            "frame_type": "dark",
        }

    monkeypatch.setattr(darks, "capture_dark_frame", fake_capture_dark)

    metadata = darks.start_dark_session(exposure_s=4.0, requested_count=3)
    assert metadata["captured_count"] == 0
    assert metadata["setup_profile"]["optical"]["telescope_name"] == "ASKAR 71F"

    first = darks.capture_dark(metadata["id"])
    assert first["captured_count"] == 1
    assert Path(first["frames"][0]["image"]).exists()
    assert first["frames"][0]["valid"] is True
    assert first["frames"][0]["frame_type"] == "dark"

    second = darks.capture_dark(metadata["id"])
    third = darks.capture_dark(metadata["id"])

    assert second["captured_count"] == 2
    assert third["status"] == "complete"
    assert third["captured_count"] == 3
    assert third["valid_count"] == 3
    assert len(third["frames"]) == 3
    assert third["series_quality"]["status"] == "ok"
    assert third["series_quality"]["rejected_count"] == 0

    master = third["master_dark"]
    hot_pixels = third["hot_pixels"]
    assert Path(master["path"]).exists()
    assert Path(master["profile"]).exists()
    assert master["method"] == darks.MASTER_METHOD
    assert master["rejected_frames"] == 0
    assert hot_pixels["count"] >= 1
    assert Path(hot_pixels["mask_fits"]).exists()
    assert Path(hot_pixels["coordinates_csv"]).exists()
    assert Path(hot_pixels["summary_json"]).exists()

    compatibility = third["compatibility"]
    assert compatibility["camera"] == "PlayerOne CCD Uranus-C"
    assert compatibility["exposure_s"] == 4.0
    assert compatibility["gain"] == 200.0
    assert compatibility["offset"] == 20.0
    assert compatibility["bayer_pattern"] == "RGGB"

    library = darks.dark_library()
    assert library["count"] == 1
    assert library["masters"][0]["id"] == metadata["id"]


def test_dark_series_rejects_level_outlier_before_master(tmp_path, monkeypatch):
    monkeypatch.setattr(darks, "DARK_ROOT", tmp_path / "darks")
    monkeypatch.setattr(darks, "indi_service", FakeIndi())
    monkeypatch.setattr(darks, "setup_service", FakeSetup())

    levels = [120, 121, 240, 119, 120]
    counter = {"value": 0}

    def fake_capture_dark(exposure_s, *, output_dir, prefix):
        index = counter["value"]
        counter["value"] += 1
        output = Path(output_dir)
        output.mkdir(parents=True, exist_ok=True)
        path = output / f"{prefix}.fits"
        image = np.full((64, 64), levels[index], dtype=np.uint16)
        image[2, 2] = 1200
        _write_dark(path, image, exposure_s)
        return {
            "status": "captured",
            "image": str(path),
            "exposure_s": exposure_s,
            "frame_type": "dark",
        }

    monkeypatch.setattr(darks, "capture_dark_frame", fake_capture_dark)

    metadata = darks.start_dark_session(exposure_s=4.0, requested_count=5)
    result = metadata
    for _ in range(5):
        result = darks.capture_dark(metadata["id"])

    assert result["status"] == "complete"
    assert result["captured_count"] == 5
    assert result["valid_count"] == 4
    assert result["series_quality"]["status"] == "ok"
    assert result["series_quality"]["rejected_count"] == 1

    rejected = [frame for frame in result["frames"] if not frame["valid"]]
    assert len(rejected) == 1
    assert rejected[0]["index"] == 3
    assert rejected[0]["capture_valid"] is True
    assert "median_outlier" in rejected[0]["rejection_reasons"]
    assert result["master_dark"]["valid_frames"] == 4
    assert result["master_dark"]["rejected_frames"] == 1
