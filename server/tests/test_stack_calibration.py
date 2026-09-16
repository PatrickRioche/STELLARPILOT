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


def _write_light(path: Path, value: int = 100, temperature_c: float | None = None):
    data = np.full((8, 8), value, dtype=np.uint16)
    data[2, 2] = 1000
    header = fits.Header()
    header["BAYERPAT"] = "RGGB"
    header["EXPTIME"] = 4.0
    header["GAIN"] = 305.0
    header["OFFSET"] = 0.0
    if temperature_c is not None:
        header["CCD-TEMP"] = temperature_c
    fits.PrimaryHDU(data=data, header=header).writeto(path, overwrite=True)


def _master_item(
    master_path: Path,
    *,
    master_id: str,
    temperature_c: float,
    created_at: str,
    hot_path: Path | None = None,
):
    return {
        "id": master_id,
        "created_at": created_at,
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
            "temperature_c": temperature_c,
            # Historical masters can still carry the old 2 °C value. Runtime
            # compatibility now follows StellarPilot's current 5 °C policy.
            "temperature_tolerance_c": 2.0,
        },
        "master_dark": {"path": str(master_path)},
        "hot_pixels": (
            {"count": 1, "mask_fits": str(hot_path)}
            if hot_path is not None
            else {}
        ),
    }


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
            _master_item(
                master,
                master_id="dark-001",
                temperature_c=10.0,
                created_at="2026-09-07T18:00:00+00:00",
                hot_path=hot,
            )
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
    assert selection["temperature_tolerance_c"] == 5.0

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


def test_temperature_tolerance_is_five_degrees(tmp_path, monkeypatch):
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
            _master_item(
                master,
                master_id="dark-001",
                temperature_c=10.0,
                created_at="2026-09-07T18:00:00+00:00",
            )
        ],
    }
    monkeypatch.setattr(stack_calibration.darks, "dark_library", lambda: library)

    accepted = stack_calibration.select_compatible_master(
        light,
        exposure_s=4.0,
        snapshot=_snapshot(temperature_c=15.0),
    )
    assert accepted["status"] == "ready"
    assert accepted["temperature_delta_c"] == 5.0

    rejected = stack_calibration.select_compatible_master(
        light,
        exposure_s=4.0,
        snapshot=_snapshot(temperature_c=15.1),
    )
    assert rejected["status"] == "unavailable"
    assert "15.1 °C" in rejected["detail"]
    assert "10.0 °C" in rejected["detail"]
    assert "5.1 °C" in rejected["detail"]
    assert "±5.0 °C" in rejected["detail"]
    assert "Refaites les darks" in rejected["detail"]


def test_fits_temperature_has_priority_over_live_camera_temperature(tmp_path, monkeypatch):
    light = tmp_path / "light.fits"
    master = tmp_path / "master_dark.fits"
    _write_light(light, temperature_c=20.0)
    fits.PrimaryHDU(data=np.full((8, 8), 10.0, dtype=np.float32)).writeto(
        master,
        overwrite=True,
    )

    library = {
        "status": "ready",
        "count": 1,
        "masters": [
            _master_item(
                master,
                master_id="dark-020",
                temperature_c=20.0,
                created_at="2026-09-15T20:00:00+00:00",
            )
        ],
    }
    monkeypatch.setattr(stack_calibration.darks, "dark_library", lambda: library)

    selection = stack_calibration.select_compatible_master(
        light,
        exposure_s=4.0,
        snapshot=_snapshot(temperature_c=30.0),
    )

    assert selection["status"] == "ready"
    assert selection["profile"]["temperature_c"] == 20.0
    assert selection["temperature_delta_c"] == 0.0


def test_incompatible_preferred_master_switches_to_another_compatible_master(
    tmp_path,
    monkeypatch,
):
    light = tmp_path / "light.fits"
    old_master = tmp_path / "old_master.fits"
    better_master = tmp_path / "better_master.fits"
    _write_light(light)
    for path in (old_master, better_master):
        fits.PrimaryHDU(data=np.full((8, 8), 10.0, dtype=np.float32)).writeto(
            path,
            overwrite=True,
        )

    library = {
        "status": "ready",
        "count": 2,
        "masters": [
            _master_item(
                old_master,
                master_id="dark-old",
                temperature_c=25.0,
                created_at="2026-09-15T18:00:00+00:00",
            ),
            _master_item(
                better_master,
                master_id="dark-new",
                temperature_c=14.0,
                created_at="2026-09-15T21:00:00+00:00",
            ),
        ],
    }
    monkeypatch.setattr(stack_calibration.darks, "dark_library", lambda: library)

    selection = stack_calibration.select_compatible_master(
        light,
        exposure_s=4.0,
        snapshot=_snapshot(temperature_c=13.0),
        preferred_master_id="dark-old",
    )

    assert selection["status"] == "ready"
    assert selection["master"]["id"] == "dark-new"
    assert selection["switched_master"] is True
    assert selection["previous_master_id"] == "dark-old"
    assert selection["temperature_delta_c"] == 1.0


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
