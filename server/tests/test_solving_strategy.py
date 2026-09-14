from app.solving.service import PlateSolverService


def _indi_hint():
    return {
        "source": "indi_mount_readback",
        "mount": "LX200 OnStep",
        "coordinate_property": "EQUATORIAL_EOD_COORD",
        "ra_hours": 2.5,
        "ra_deg": 37.5,
        "dec_deg": 89.0,
        "mount_status": "tracking",
        "indi_state": "Ok",
    }


def test_robust_solve_uses_indi_position_as_optional_hint(monkeypatch):
    solver = PlateSolverService()
    monkeypatch.setattr(solver, "_current_mount_position_hint", _indi_hint)

    calls = []

    def fake_solve(**kwargs):
        calls.append(kwargs)
        return {
            "status": "solved",
            "solver": "astrometry.net",
            "ra": 40.0,
            "dec": 88.9,
            "pixel_scale_arcsec": 1.218,
        }

    monkeypatch.setattr(solver, "solve", fake_solve)

    result = solver.solve_robust("capture.fits")

    assert result["status"] == "solved"
    assert result["strategy"] == "scale_narrow_position"
    assert result["position_hint"]["source"] == "indi_mount_readback"
    assert len(calls) == 1
    assert calls[0]["ra_hint"] == 37.5
    assert calls[0]["dec_hint"] == 89.0
    assert calls[0]["radius_deg"] == 8.0
    assert 0.89 < calls[0]["scale_low_arcsec"] < 0.92
    assert 1.50 < calls[0]["scale_high_arcsec"] < 1.52
    assert calls[0]["timeout_s"] == 20


def test_robust_solve_falls_back_to_blind_after_bad_hint(monkeypatch):
    solver = PlateSolverService()
    monkeypatch.setattr(solver, "_current_mount_position_hint", _indi_hint)

    calls = []

    def fake_solve(**kwargs):
        calls.append(kwargs)
        if len(calls) == 1:
            return {"status": "timeout", "solver": "astrometry.net"}
        return {
            "status": "solved",
            "solver": "astrometry.net",
            "ra": 40.0,
            "dec": 88.9,
            "pixel_scale_arcsec": 1.218,
        }

    monkeypatch.setattr(solver, "solve", fake_solve)

    result = solver.solve_robust("capture.fits")

    assert result["status"] == "solved"
    assert result["strategy"] == "scale_narrow_blind"
    assert len(calls) == 2
    assert calls[0]["ra_hint"] == 37.5
    assert calls[0]["dec_hint"] == 89.0
    assert calls[1]["ra_hint"] is None
    assert calls[1]["dec_hint"] is None
    assert calls[1]["radius_deg"] is None
    assert calls[1]["timeout_s"] == 90


def test_robust_solve_starts_blind_when_no_indi_hint(monkeypatch):
    solver = PlateSolverService()
    monkeypatch.setattr(solver, "_current_mount_position_hint", lambda: None)

    calls = []

    def fake_solve(**kwargs):
        calls.append(kwargs)
        return {
            "status": "solved",
            "solver": "astrometry.net",
            "ra": 40.0,
            "dec": 88.9,
            "pixel_scale_arcsec": 1.218,
        }

    monkeypatch.setattr(solver, "solve", fake_solve)

    result = solver.solve_robust("capture.fits")

    assert result["status"] == "solved"
    assert result["strategy"] == "scale_narrow_blind"
    assert result["position_hint"] is None
    assert len(calls) == 1
    assert calls[0]["ra_hint"] is None
    assert calls[0]["dec_hint"] is None
    assert calls[0]["radius_deg"] is None


def test_robust_solve_uses_wide_blind_as_last_resort(monkeypatch):
    solver = PlateSolverService()
    monkeypatch.setattr(solver, "_current_mount_position_hint", lambda: None)

    calls = []

    def fake_solve(**kwargs):
        calls.append(kwargs)
        if len(calls) == 1:
            return {"status": "timeout", "solver": "astrometry.net"}
        return {
            "status": "solved",
            "solver": "astrometry.net",
            "ra": 40.0,
            "dec": 88.9,
            "pixel_scale_arcsec": 1.218,
        }

    monkeypatch.setattr(solver, "solve", fake_solve)

    result = solver.solve_robust("capture.fits")

    assert result["strategy"] == "scale_wide_blind"
    assert calls[1]["scale_low_arcsec"] == 0.50
    assert calls[1]["scale_high_arcsec"] == 2.50
    assert calls[1]["timeout_s"] == 120
