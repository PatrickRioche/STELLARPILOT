from pathlib import Path

import numpy as np
from astropy.io import fits

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
        header = fits.Header()
        header["BAYERPAT"] = "RGGB"
        data = np.arange(64 * 64, dtype=np.uint16).reshape(64, 64)
        fits.PrimaryHDU(data=data, header=header).writeto(
            image,
            overwrite=True,
        )
        return {
            "status": "captured",
            "image": str(image),
            "exposure_s": exposure_s,
            "camera": "Fake CCD",
        }


class FakeSolver:
    def __init__(self, ra_deg, dec_deg):
        self.ra_deg = ra_deg
        self.dec_deg = dec_deg

    def solve_robust(self, image, ra_hint=None, dec_hint=None):
        return {
            "status": "solved",
            "solver": "fake",
            "image": image,
            "ra": self.ra_deg,
            "dec": self.dec_deg,
            "pixel_scale_arcsec": 1.2,
        }


def test_session_tree_is_below_server_runtime_root(tmp_path):
    runtime = tmp_path / "stellarpilot-server" / "tmp"
    galleries = tmp_path / "stellarpilot-server" / "data" / "galleries"
    service = CaptureSessionService(
        runtime_root=runtime,
        galleries_root=galleries,
        indi=FakeIndi(),
        solver=FakeSolver(75.0, 20.0),
    )

    session = service.create_session(
        target_name="M42",
        target_ra_hours=5.0,
        target_dec_deg=20.0,
        exposure_s=4.0,
    )

    session_dir = runtime / "capture" / "sessions" / session["id"]
    assert session_dir.exists()
    assert (session_dir / "raw").exists()
    assert (session_dir / "accepted").exists()
    assert (session_dir / "rejected").exists()
    assert (session_dir / "registered").exists()
    assert (session_dir / "stack").exists()
    assert (session_dir / "previews").exists()
    assert (session_dir / "session.json").exists()


def test_center_step_marks_exact_target_centered(tmp_path):
    service = CaptureSessionService(
        runtime_root=tmp_path / "tmp",
        galleries_root=tmp_path / "galleries",
        indi=FakeIndi(),
        solver=FakeSolver(75.0, 20.0),
    )

    session = service.create_session(
        target_name="M42",
        target_ra_hours=5.0,
        target_dec_deg=20.0,
        exposure_s=4.0,
        centering_tolerance_arcsec=30.0,
    )

    result = service.center_step(session["id"])

    assert result["status"] == "centered"
    assert result["session"]["state"] == "centered"
    assert result["centering"]["error_arcsec"] == 0.0
    assert result["centering"]["correction_ra_hours"] == 5.0
    assert result["centering"]["correction_dec_deg"] == 20.0


def test_center_step_returns_closed_loop_correction(tmp_path):
    service = CaptureSessionService(
        runtime_root=tmp_path / "tmp",
        galleries_root=tmp_path / "galleries",
        indi=FakeIndi(),
        solver=FakeSolver(74.9, 19.9),
    )

    session = service.create_session(
        target_name="M42",
        target_ra_hours=5.0,
        target_dec_deg=20.0,
        centering_tolerance_arcsec=30.0,
    )

    result = service.center_step(session["id"])
    centering = result["centering"]

    assert result["status"] == "correction_required"
    assert centering["error_arcsec"] > 30.0
    assert centering["correction_ra_hours"] > 5.0
    assert centering["correction_dec_deg"] > 20.0
