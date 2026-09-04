from pathlib import Path

import numpy as np
from astropy.io import fits

from app.imaging import bahtinov


def _synthetic_bahtinov(
    path: Path,
    *,
    central_offset_px: float,
    size: int = 256,
) -> None:
    yy, xx = np.mgrid[:size, :size]
    x = xx - (size - 1.0) / 2.0
    y = yy - (size - 1.0) / 2.0

    rng = np.random.default_rng(42)
    image = rng.normal(100.0, 2.5, (size, size))

    # Three Bahtinov diffraction families: a central horizontal spike and two
    # symmetric outer spikes. Moving the central spike models focus error.
    directions = (0.0, 25.0, 155.0)
    for index, direction_deg in enumerate(directions):
        normal_deg = direction_deg + 90.0
        theta = np.deg2rad(normal_deg)
        rho = central_offset_px if index == 0 else 0.0
        distance = x * np.cos(theta) + y * np.sin(theta) - rho
        radius = np.hypot(x, y)
        image += (
            3000.0
            * np.exp(-0.5 * (distance / 1.2) ** 2)
            * np.exp(-radius / 120.0)
        )

    image += 10000.0 * np.exp(-(x * x + y * y) / (2.0 * 3.0**2))
    fits.writeto(path, image.astype(np.float32), overwrite=True)


def test_bahtinov_geometry_tracks_signed_focus_error(tmp_path: Path):
    centered = tmp_path / "centered.fits"
    shifted = tmp_path / "shifted.fits"
    _synthetic_bahtinov(centered, central_offset_px=0.0)
    _synthetic_bahtinov(shifted, central_offset_px=9.0)

    centered_geometry = bahtinov._analyze_geometry(centered)
    shifted_geometry = bahtinov._analyze_geometry(shifted)

    assert abs(centered_geometry["signed_error_px"]) <= 1.5
    assert shifted_geometry["signed_error_px"] >= 6.0
    assert centered_geometry["geometry_confidence"] > 0.25
    assert shifted_geometry["geometry_confidence"] > 0.25


def test_reference_calibration_scores_both_sides(tmp_path: Path, monkeypatch):
    reference_root = tmp_path / "reference-v2"
    dated = reference_root / "2026-09-03" / "bahtinov"
    optimum_dir = dated / "optimum"
    other_dir = dated / "other_side"
    optimum_dir.mkdir(parents=True)
    other_dir.mkdir(parents=True)

    _synthetic_bahtinov(optimum_dir / "optimum_01.fits", central_offset_px=0.0)
    _synthetic_bahtinov(optimum_dir / "optimum_02.fits", central_offset_px=0.5)
    _synthetic_bahtinov(other_dir / "other_01.fits", central_offset_px=9.0)
    _synthetic_bahtinov(other_dir / "other_02.fits", central_offset_px=10.0)

    optimum_test = tmp_path / "test_optimum.fits"
    other_test = tmp_path / "test_other.fits"
    first_side_test = tmp_path / "test_first_side.fits"
    _synthetic_bahtinov(optimum_test, central_offset_px=0.25)
    _synthetic_bahtinov(other_test, central_offset_px=9.5)
    _synthetic_bahtinov(first_side_test, central_offset_px=-9.0)

    monkeypatch.setattr(bahtinov, "REFERENCE_ROOT", reference_root)

    optimum = bahtinov.analyze_bahtinov(str(optimum_test))
    other = bahtinov.analyze_bahtinov(str(other_test))
    first_side = bahtinov.analyze_bahtinov(str(first_side_test))

    assert optimum["status"] == "ok"
    assert optimum["focus_score"] >= 98
    assert optimum["focus_label"] == "Optimum"
    assert optimum["focus_side"] == "optimum"

    assert other["status"] == "ok"
    assert other["focus_score"] < optimum["focus_score"]
    assert other["focus_side"] == "other_side"

    assert first_side["status"] == "ok"
    assert first_side["focus_score"] < optimum["focus_score"]
    assert first_side["focus_side"] == "first_side"
