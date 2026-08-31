import json
from pathlib import Path

import numpy as np
from astropy.io import fits

from app.imaging.centering_capture import (
    capture_centering_frame,
    solve_centering_frame,
)
from app.imaging.sessions import CaptureSessionService


class FakeIndi:
    def __init__(self):
        self.calls = 0

    def capture(
        self,
        exposure_s,
        output_dir=None,
        prefix=None,
    ):
        self.calls += 1
        output = Path(output_dir)
        output.mkdir(parents=True, exist_ok=True)
        image = output / f"{prefix}.fits"
        data = (
            np.arange(64 * 64, dtype=np.uint16).reshape(64, 64)
            + (self.calls - 1) * 100
        )
        header = fits.Header()
        header["BAYERPAT"] = "RGGB"
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


def _service(tmp_path, solver=None):
    return CaptureSessionService(
        runtime_root=tmp_path / "tmp",
        galleries_root=tmp_path / "galleries",
        indi=FakeIndi(),
        solver=solver or CountingSolver(),
    )


def _session(service):
    return service.create_session(
        target_name="M42",
        target_ra_hours=5.0,
        target_dec_deg=20.0,
        exposure_s=4.0,
    )


def test_preview_is_available_before_astrometry(tmp_path):
    solver = CountingSolver()
    service = _service(tmp_path, solver=solver)
    session = _session(service)

    captured = capture_centering_frame(
        session["id"],
        service=service,
        archive_root=tmp_path / "astrometry",
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


def test_first_astrometry_capture_is_persisted_before_solve(tmp_path):
    solver = CountingSolver()
    service = _service(tmp_path, solver=solver)
    session = _session(service)
    archive_root = tmp_path / "astrometry"

    captured = capture_centering_frame(
        session["id"],
        service=service,
        archive_root=archive_root,
    )

    assert captured["status"] == "captured"
    assert solver.calls == 0

    archive = captured["session"]["initial_astrometry_archive"]
    fits_path = Path(archive["fits"])
    preview_path = Path(archive["preview"])
    metadata_path = Path(archive["metadata"])

    assert archive_root in fits_path.parents
    assert fits_path.name == "initial.fits"
    assert preview_path.name == "initial.jpg"
    assert metadata_path.name == "metadata.json"
    assert fits_path.exists()
    assert preview_path.exists()
    assert metadata_path.exists()

    archived = json.loads(metadata_path.read_text(encoding="utf-8"))
    assert archived["target"]["name"] == "M42"
    assert archived["exposure_s"] == 4.0
    assert archived["camera"] == "Fake CCD"
    assert archived["solver"]["status"] is None

    solved = solve_centering_frame(
        session["id"],
        service=service,
    )

    archived = json.loads(metadata_path.read_text(encoding="utf-8"))
    assert solved["status"] == "centered"
    assert archived["solver"]["status"] == "solved"
    assert archived["solver"]["solver"] == "fake"
    assert archived["solver"]["solve_ra_deg"] == 75.0
    assert archived["solver"]["solve_dec_deg"] == 20.0
    assert archived["solver"]["centering_error_arcsec"] == 0.0


def test_corrective_capture_never_overwrites_initial_archive(tmp_path):
    service = _service(tmp_path)
    session = _session(service)
    archive_root = tmp_path / "astrometry"

    first = capture_centering_frame(
        session["id"],
        service=service,
        archive_root=archive_root,
    )
    initial_fits = Path(
        first["session"]["initial_astrometry_archive"]["fits"]
    )
    first_bytes = initial_fits.read_bytes()

    solve_centering_frame(session["id"], service=service)

    second = capture_centering_frame(
        session["id"],
        service=service,
        archive_root=archive_root,
    )

    assert second["session"]["centering"]["attempts"] == 2
    assert initial_fits.read_bytes() == first_bytes
    assert (
        second["session"]["initial_astrometry_archive"]["fits"]
        == str(initial_fits)
    )
