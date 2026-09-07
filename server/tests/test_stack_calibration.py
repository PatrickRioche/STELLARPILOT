from pathlib import Path

import numpy as np
from astropy.io import fits

from app.imaging import stack_calibration
from app.imaging.robust_stack import build_sigma_clipped_stack


def _snapshot(temperature_c: float = 10.5):
    return {
        "camera": {
            "status": "ready",
            "name": "PlayerOne CCD Uranus-C",
            "sensor": {
                "width": 8,
                "height": 8,
                "bits_per_pixel": 16,
            },
            "capture": {
                "gain": 305.0,
                "offset": 0.0,
                "bin_x": 1,
                "bin_y": 1,
                "frame_width": 8,
                "frame_height": 8,
            },
            "temperature_c": temperature_c,
        }
    }


def _write_light(path: Path, value: int = 100):
    data = np.full((8, 8), value, dtype=np.uint16)
    data[2, 2] = 1000
    header = fits.Header()
    header["BAYERPAT"] = "RGGB"
    header["EXPTIME"] = 4.0
    header["GAIN"] = 305.0
    header["OFFSET"] = 0.0
    fits.PrimaryHDU(data=data, header=header).writeto(path, overwrite=True)


def test_select_and_apply_compatible_master_dark(tmp_path, monkeypatch):
    light = tmp_path / "light.fits"
    master = tmp_path / "master_dark.fits"
    hot = tmp_path / "hot_pixels.fits"
    calibrated = tmp_path / "calibrated.fits"

    _write_light(light)
    fits.PrimaryHDU(data=np.full((8, 8), 10.0, dtype=np.float32)).writeto(
        master,
        overwrite=True,
    )
    mask = np.zeros((8, 8), dtype=np.uint8)
    mask[2, 2] = 1
    fits.PrimaryHDU(data=mask).writeto(hot, overwrite=True)

    library = {
        "status": "ready",
        "count": 1,
        "masters": [
            {
                "id": "dark-001",
                "created_at": "2026-09-07T18:00:00+00:00",
                "compatibility": {
                    "camera": "PlayerOne CCD Uranus-C",
                    "exposure_s": 4.0,
                    "gain": 305.0,
                    "offset": 0.0,
                    "bin_x": 1,
                    "bin_y": 1,
                    "frame_width": 8,
                    "frame_height": 8,
                    "bits_per_pixel": 16,
                    "bayer_pattern": "RGGB",
                    "temperature_c": 10.0,
                    "temperature_tolerance_c": 2.0,
                },
                "master_dark": {"path": str(master)},
                "hot_pixels": {
                    "count": 1,
                    "mask_fits": str(hot),
                },
            }
        ],
    }
    monkeypatch.setattr(stack_calibration.darks, "dark_library", lambda: library)

    selection = stack_calibration.select_compatible_master(
        light,
        exposure_s=4.0,
        snapshot=_snapshot(),
    )

    assert selection["status"] == "ready"
    assert selection["master"]["id"] == "dark-001"
    assert selection["temperature_delta_c"] == 0.5

    result = stack_calibration.calibrate_light(
        light,
        selection,
        calibrated,
    )
    assert result["status"] == "calibrated"
    assert result["master_id"] == "dark-001"
    assert result["hot_pixels_repaired"] == 1

    with fits.open(calibrated, memmap=False) as hdul:
        data = np.asarray(hdul[0].data)
        header = hdul[0].header

    assert np.isclose(data[0, 0], 90.0)
    assert np.isclose(data[2, 2], 90.0)
    assert header["SPCALIB"] is True
    assert header["SPDARKID"] == "dark-001"


def test_preferred_master_is_rejected_when_temperature_drifts(tmp_path, monkeypatch):
    light = tmp_path / "light.fits"
    master = tmp_path / "master_dark.fits"
    _write_light(light)
    fits.PrimaryHDU(data=np.full((8, 8), 10.0, dtype=np.float32)).writeto(
        master,
        overwrite=True,
    )

    library = {
        "status": "ready",
        "count": 1,
        "masters": [
            {
                "id": "dark-001",
                "created_at": "2026-09-07T18:00:00+00:00",
                "compatibility": {
                    "camera": "PlayerOne CCD Uranus-C",
                    "exposure_s": 4.0,
                    "gain": 305.0,
                    "offset": 0.0,
                    "bin_x": 1,
                    "bin_y": 1,
                    "frame_width": 8,
                    "frame_height": 8,
                    "bits_per_pixel": 16,
                    "bayer_pattern": "RGGB",
                    "temperature_c": 10.0,
                    "temperature_tolerance_c": 2.0,
                },
                "master_dark": {"path": str(master)},
                "hot_pixels": {},
            }
        ],
    }
    monkeypatch.setattr(stack_calibration.darks, "dark_library", lambda: library)

    selection = stack_calibration.select_compatible_master(
        light,
        exposure_s=4.0,
        snapshot=_snapshot(temperature_c=13.0),
        preferred_master_id="dark-001",
    )

    assert selection["status"] == "unavailable"
    assert "plus compatible" in selection["detail"]


def test_final_stack_sigma_clips_one_outlier(tmp_path):
    paths = []
    for index, value in enumerate((100.0, 100.0, 100.0, 100.0, 100.0)):
        data = np.full((8, 8), value, dtype=np.float32)
        if index == 4:
            data[3, 3] = 10000.0
        path = tmp_path / f"registered_{index}.fits"
        fits.PrimaryHDU(data=data).writeto(path, overwrite=True)
        paths.append(path)

    destination = tmp_path / "final.fits"
    result = build_sigma_clipped_stack(paths, destination)

    assert result["status"] == "ready"
    assert result["frames"] == 5
    with fits.open(destination, memmap=False) as hdul:
        data = np.asarray(hdul[0].data)
        header = hdul[0].header

    assert data[3, 3] < 500.0
    assert header["SPMETH"] == "sigma-clipped-mean-v1"
