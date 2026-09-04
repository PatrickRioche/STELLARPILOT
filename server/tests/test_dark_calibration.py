from pathlib import Path

import numpy as np
from astropy.io import fits

from app.imaging import darks


class FakeIndi:
    def capture(self, exposure_s, output_dir=None, prefix=None):
        output = Path(output_dir)
        output.mkdir(parents=True, exist_ok=True)
        path = output / f"{prefix}.fits"
        image = np.full((64, 64), 120, dtype=np.uint16)
        image[2, 2] = 500
        fits.writeto(path, image, overwrite=True)
        return {
            "status": "captured",
            "image": str(path),
            "exposure_s": exposure_s,
        }


def test_dark_session_persists_each_frame(tmp_path, monkeypatch):
    monkeypatch.setattr(darks, "DARK_ROOT", tmp_path / "darks")
    monkeypatch.setattr(darks, "indi_service", FakeIndi())

    metadata = darks.start_dark_session(exposure_s=4.0, requested_count=3)
    assert metadata["captured_count"] == 0

    first = darks.capture_dark(metadata["id"])
    assert first["captured_count"] == 1
    assert Path(first["frames"][0]["image"]).exists()
    assert first["frames"][0]["valid"] is True

    second = darks.capture_dark(metadata["id"])
    third = darks.capture_dark(metadata["id"])

    assert second["captured_count"] == 2
    assert third["status"] == "complete"
    assert third["captured_count"] == 3
    assert third["valid_count"] == 3
    assert len(third["frames"]) == 3
