from pathlib import Path

import numpy as np
from astropy.io import fits

from app.imaging import bahtinov
from app.imaging import bahtinov_focus


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


def test_two_focus_sides_are_scored_symmetrically(tmp_path: Path, monkeypatch):
    root = tmp_path / "reference-v2"
    dated = root / "2026-09-03" / "bahtinov"
    optimum_dir = dated / "optimum"
    side_a_dir = dated / "side_a"
    side_b_dir = dated / "side_b"
    optimum_dir.mkdir(parents=True)
    side_a_dir.mkdir(parents=True)
    side_b_dir.mkdir(parents=True)

    _synthetic_bahtinov(optimum_dir / "optimum_01.fits", central_offset_px=0.0)
    _synthetic_bahtinov(optimum_dir / "optimum_02.fits", central_offset_px=0.5)
    _synthetic_bahtinov(side_a_dir / "side_a_01.fits", central_offset_px=9.0)
    _synthetic_bahtinov(side_b_dir / "side_b_01.fits", central_offset_px=-9.0)

    monkeypatch.setattr(bahtinov, "REFERENCE_ROOT", root)
    monkeypatch.setattr(bahtinov_focus, "REFERENCE_ROOT", root)

    optimum_test = tmp_path / "optimum_test.fits"
    side_a_test = tmp_path / "side_a_test.fits"
    side_b_test = tmp_path / "side_b_test.fits"
    _synthetic_bahtinov(optimum_test, central_offset_px=0.25)
    _synthetic_bahtinov(side_a_test, central_offset_px=9.0)
    _synthetic_bahtinov(side_b_test, central_offset_px=-9.0)

    optimum = bahtinov_focus.analyze_bahtinov(str(optimum_test))
    side_a = bahtinov_focus.analyze_bahtinov(str(side_a_test))
    side_b = bahtinov_focus.analyze_bahtinov(str(side_b_test))

    assert optimum["focus_score"] >= 98
    assert optimum["focus_side"] == "optimum"
    assert side_a["focus_side"] == "side_a"
    assert side_b["focus_side"] == "side_b"
    assert abs(side_a["focus_score"] - side_b["focus_score"]) <= 2
    assert 40 <= side_a["focus_score"] <= 50
    assert 40 <= side_b["focus_score"] <= 50
