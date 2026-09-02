from pathlib import Path

import numpy as np
from astropy.io import fits

# Importing app.main installs the unified renderer on CaptureSessionService.
from app import main as _main  # noqa: F401
from app.imaging.preview import render_fits_preview
from app.imaging.quality import analyze_fits
from app.imaging.sessions import CaptureSessionService


def test_assistant_and_capture_use_identical_preview_renderer(tmp_path):
    image = tmp_path / "bayer.fits"
    data = np.full((64, 96), 1200, dtype=np.uint16)
    data[12, 20] = 12000
    data[30, 50] = 18000
    data[44, 70] = 15000

    header = fits.Header()
    header["BAYERPAT"] = "RGGB"
    header["XBAYROFF"] = 1
    header["YBAYROFF"] = 1
    fits.PrimaryHDU(data=data, header=header).writeto(image)

    expected, diagnostics = render_fits_preview(image)
    capture_bytes = CaptureSessionService._fits_preview_bytes(Path(image))

    assert expected.startswith(b"\xff\xd8")
    assert capture_bytes == expected
    assert diagnostics["renderer"] == "stellar-unified-v1"
    assert diagnostics["bayer"] == "RGGB"
    assert diagnostics["x_bayer_offset"] == 1
    assert diagnostics["y_bayer_offset"] == 1


def test_overexposed_frame_gets_low_astrometry_score(tmp_path):
    image = tmp_path / "overexposed.fits"
    data = np.full((64, 64), 65535, dtype=np.uint16)
    fits.PrimaryHDU(data=data).writeto(image)

    quality = analyze_fits(str(image))

    assert quality["status"] == "ok"
    assert quality["classification"] == "overexposed"
    assert 0 <= quality["astrometry_score"] <= 24
    assert quality["quality_label"] == "Insuffisante"
    assert quality["recommended_exposure_factor"] == 0.5
    assert quality["astrometry_ready"] is False
