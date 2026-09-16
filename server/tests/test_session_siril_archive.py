import json
from pathlib import Path

from app.imaging.session_archive_patch import _archive_siril_assets


class FakeService:
    def __init__(self, session_dir: Path):
        self.session_dir = session_dir

    def _session_dir(self, _session_id: str) -> Path:
        return self.session_dir


def test_siril_archive_keeps_accepted_rejected_and_master(tmp_path):
    session_dir = tmp_path / "runtime" / "session-001"
    accepted = session_dir / "accepted"
    rejected = session_dir / "rejected"
    accepted.mkdir(parents=True)
    rejected.mkdir(parents=True)

    (accepted / "light_000001.fits").write_bytes(b"accepted")
    (accepted / "light_000002.fits").write_bytes(b"accepted2")
    (rejected / "light_000003.fits").write_bytes(b"rejected")

    master_dir = tmp_path / "darks" / "products"
    master_dir.mkdir(parents=True)
    master = master_dir / "master_dark.fits"
    hot = master_dir / "hot_pixels.fits"
    master.write_bytes(b"master")
    hot.write_bytes(b"hot")

    gallery = tmp_path / "gallery" / "session-001"
    gallery.mkdir(parents=True)

    metadata = {
        "gallery_path": str(gallery),
        "target": {"name": "M16"},
        "setup": {"exposure_s": 4.0},
        "counts": {
            "captured": 3,
            "accepted": 2,
            "rejected": 1,
            "rejected_by_reason": {"trailed": 1},
        },
        "integration_seconds": 8.0,
        "calibration": {"master_dark": str(master)},
    }

    result = _archive_siril_assets(
        FakeService(session_dir),
        "session-001",
        metadata,
    )

    assert result["status"] == "ready"
    assert result["accepted_raw_fits"] == 2
    assert result["rejected_raw_fits"] == 1
    assert (gallery / "lights" / "accepted" / "light_000001.fits").exists()
    assert (gallery / "lights" / "accepted" / "light_000002.fits").exists()
    assert (gallery / "lights" / "rejected" / "light_000003.fits").exists()
    assert (gallery / "calibration" / "master_dark.fits").read_bytes() == b"master"
    assert (gallery / "calibration" / "hot_pixels.fits").read_bytes() == b"hot"

    manifest = json.loads((gallery / "siril_manifest.json").read_text(encoding="utf-8"))
    assert manifest["format"] == "stellarpilot-siril-v1"
    assert manifest["rejected_by_reason"] == {"trailed": 1}
