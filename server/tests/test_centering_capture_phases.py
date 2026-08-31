from pathlib import Path

import numpy as np
from astropy.io import fits

from app.imaging.centering_capture import (
    capture_centering_frame,
    solve_centering_frame,
)
from app.imaging.sessions import CaptureSessionService


class FakeIndi:
    def capture(
        self,
        exposure_s,
        output_dir=None,
        prefix=None,
    ):
        output = Path(output_dir)
        output.mkdir(parents=True, exist_ok=True)
        image = output / f"{prefix}.fits"
        data = np.arange(64 * 64, dtype=np.uint16).reshape(64, 64)
        fits.PrimaryHDU(data=data).writeto(
            image,
            overwrite=True,
        )
        return {
            "status": "captured",
            "image": str(image),
            "exposure_s": exposure_s,
        }


class CountingSolver:
    def __init__(self):
        self.calls = 0

    def solve_robust(self, image, ra_hint=None, dec_hint=None):
        self.calls += 1
        return {
            "status": "solved",
            "solver": "fake",
            "image": image,
            "ra": 75.0,
            "dec": 20.0,
            "pixel_scale_arcsec": 1.2,
        }


def test_preview_is_available_before_astrometry(tmp_path):
    solver = CountingSolver()
    service = CaptureSessionService(
        runtime_root=tmp_path / "tmp",
        galleries_root=tmp_path / "galleries",
        indi=FakeIndi(),
        solver=solver,
    )

    session = service.create_session(
        target_name="M42",
        target_ra_hours=5.0,
        target_dec_deg=20.0,
        exposure_s=4.0,
    )

    captured = capture_centering_frame(
        session["id"],
        service=service,
    )

    assert captured["status"] == "captured"
    assert captured["session"]["centering"]["status"] == "captured"
    assert captured["session"]["preview"] is not None
    assert Path(captured["session"]["preview"]).exists()
    assert solver.calls == 0

    solved = solve_centering_frame(
        session["id"],
        service=service,
    )

    assert solver.calls == 1
    assert solved["status"] == "centered"
    assert solved["session"]["state"] == "centered"
