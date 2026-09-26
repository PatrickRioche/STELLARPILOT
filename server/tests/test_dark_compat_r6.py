from __future__ import annotations

from app.imaging.calibration_r6 import fixed_dark_compatibility_profile


def test_dark_profile_prefers_fits_gain_offset_and_exposure():
    metadata = {
        "exposure_s": 4.0,
        "setup_profile": {
            "camera": {
                "name": "PlayerOne CCD Uranus-C",
                "sensor": {
                    "width": 3856,
                    "height": 2180,
                    "bits_per_pixel": 12,
                },
                "capture": {
                    "gain": None,
                    "offset": None,
                    "bin_x": 1,
                    "bin_y": 1,
                    "frame_width": 3856,
                    "frame_height": 2180,
                },
                "temperature_c": 21.0,
            }
        },
        "fits_profile": {
            "width": 3856,
            "height": 2180,
            "bayer_pattern": "RGGB",
            "exposure_s": 4.0,
            "gain": 305.0,
            "offset": 0.0,
            "temperature_c": 20.0,
        },
        "frames": [
            {"valid": True, "temperature_c": 19.8},
            {"valid": True, "temperature_c": 20.2},
        ],
    }

    profile = fixed_dark_compatibility_profile(metadata)

    assert profile["exposure_s"] == 4.0
    assert profile["gain"] == 305.0
    assert profile["offset"] == 0.0
    assert profile["temperature_c"] == 20.0
    assert profile["frame_width"] == 3856
    assert profile["frame_height"] == 2180
