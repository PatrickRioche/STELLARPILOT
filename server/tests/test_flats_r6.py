from __future__ import annotations

import numpy as np

from app.imaging import calibration_r6, flats


def test_flat_compatibility_ignores_exposure_and_temperature(monkeypatch):
    light = {
        "camera": "PlayerOne CCD Uranus-C",
        "exposure_s": 4.0,
        "gain": 305.0,
        "offset": 0.0,
        "bin_x": 1,
        "bin_y": 1,
        "frame_width": 3856,
        "frame_height": 2180,
        "bits_per_pixel": 12,
        "bayer_pattern": "RGGB",
        "temperature_c": 19.8,
    }
    flat_item = {
        "id": "flat-new",
        "created_at": "2026-09-26T12:00:00+00:00",
        "compatibility": {
            "camera": "PlayerOne CCD Uranus-C",
            "gain": 305.0,
            "offset": 0.0,
            "bin_x": 1,
            "bin_y": 1,
            "frame_width": 3856,
            "frame_height": 2180,
            "bits_per_pixel": 12,
            "bayer_pattern": "RGGB",
        },
        "master_flat": {"path": "/tmp/master_flat.fits"},
    }
    monkeypatch.setattr(
        flats,
        "flat_library",
        lambda: {"status": "ready", "count": 1, "masters": [flat_item]},
    )

    selected = calibration_r6.select_compatible_flat(light)

    assert selected["status"] == "ready"
    assert selected["master"]["id"] == "flat-new"


def test_flat_selector_uses_latest_compatible(monkeypatch):
    light = {
        "camera": "Uranus-C",
        "gain": 210.0,
        "offset": 10.0,
        "bin_x": 1,
        "bin_y": 1,
        "frame_width": 100,
        "frame_height": 80,
        "bayer_pattern": "RGGB",
    }
    profile = dict(light)
    masters = [
        {
            "id": "old",
            "created_at": "2026-09-20T10:00:00+00:00",
            "compatibility": profile,
            "master_flat": {"path": "/tmp/old.fits"},
        },
        {
            "id": "new",
            "created_at": "2026-09-26T10:00:00+00:00",
            "compatibility": profile,
            "master_flat": {"path": "/tmp/new.fits"},
        },
    ]
    monkeypatch.setattr(
        flats,
        "flat_library",
        lambda: {"status": "ready", "count": 2, "masters": masters},
    )

    selected = calibration_r6.select_compatible_flat(light)

    assert selected["master"]["id"] == "new"


def test_bayer_flat_is_normalized_per_parity_plane():
    data = np.zeros((8, 8), dtype=np.float32)
    data[0::2, 0::2] = 1000.0
    data[0::2, 1::2] = 2000.0
    data[1::2, 0::2] = 2100.0
    data[1::2, 1::2] = 3000.0
    # One dust shadow remains a multiplicative defect after normalization.
    data[2, 2] = 500.0

    normalized = flats._normalize_master(data, "RGGB")

    assert np.isclose(np.median(normalized[0::2, 1::2]), 1.0)
    assert np.isclose(np.median(normalized[1::2, 0::2]), 1.0)
    assert np.isclose(np.median(normalized[1::2, 1::2]), 1.0)
    assert normalized[2, 2] < 1.0
