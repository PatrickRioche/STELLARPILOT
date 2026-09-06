from app.solving.service import PlateSolverService


def test_assistant3_uses_current_mount_position_as_astrometry_hint(monkeypatch):
    service = PlateSolverService()

    monkeypatch.setattr(
        service,
        "_current_mount_position_hint",
        lambda: {
            "source": "indi_mount_readback",
            "mount": "LX200 OnStep",
            "coordinate_property": "EQUATORIAL_EOD_COORD",
            "ra_hours": 5.5,
            "ra_deg": 82.5,
            "dec_deg": 22.0,
            "mount_status": "tracking",
            "indi_state": "Ok",
        },
    )

    calls = []

    def fake_solve(**kwargs):
        calls.append(kwargs)
        return {
            "status": "solved",
            "solver": "astrometry.net",
            "image": kwargs["image"],
            "ra": 82.6,
            "dec": 22.1,
        }

    monkeypatch.setattr(service, "solve", fake_solve)

    result = service.solve_robust("assistant3.fits")

    assert result["status"] == "solved"
    assert result["strategy"] == "scale_narrow"
    assert result["position_hint"]["source"] == "indi_mount_readback"
    assert result["position_hint"]["ra_hours"] == 5.5
    assert result["position_hint"]["ra_deg"] == 82.5

    assert len(calls) == 1
    assert calls[0]["ra_hint"] == 82.5
    assert calls[0]["dec_hint"] == 22.0
    assert calls[0]["radius_deg"] == 8.0
    assert calls[0]["scale_low_arcsec"] == 1.22 * 0.70
    assert calls[0]["scale_high_arcsec"] == 1.22 * 1.40

    attempt = result["attempts"][0]
    assert attempt["position_hint_used"] is True
    assert attempt["ra_hint_deg"] == 82.5
    assert attempt["dec_hint_deg"] == 22.0
    assert attempt["radius_deg"] == 8.0


def test_assistant3_falls_back_when_mount_hint_is_unavailable(monkeypatch):
    service = PlateSolverService()

    monkeypatch.setattr(
        service,
        "_current_mount_position_hint",
        lambda: None,
    )

    calls = []

    def fake_solve(**kwargs):
        calls.append(kwargs)
        return {
            "status": "solved",
            "solver": "astrometry.net",
            "image": kwargs["image"],
            "ra": 10.0,
            "dec": 20.0,
        }

    monkeypatch.setattr(service, "solve", fake_solve)

    result = service.solve_robust("assistant3.fits")

    assert result["status"] == "solved"
    assert result["position_hint"] is None
    assert calls[0]["ra_hint"] is None
    assert calls[0]["dec_hint"] is None
    assert calls[0]["radius_deg"] is None
    assert result["attempts"][0]["position_hint_used"] is False


def test_final_strategy_is_blind_even_with_mount_hint(monkeypatch):
    service = PlateSolverService()

    monkeypatch.setattr(
        service,
        "_current_mount_position_hint",
        lambda: {
            "source": "indi_mount_readback",
            "mount": "LX200 OnStep",
            "coordinate_property": "EQUATORIAL_EOD_COORD",
            "ra_hours": 12.0,
            "ra_deg": 180.0,
            "dec_deg": -5.0,
            "mount_status": "idle",
            "indi_state": "Ok",
        },
    )

    calls = []

    def fake_solve(**kwargs):
        calls.append(kwargs)
        if len(calls) < 3:
            return {
                "status": "unsolved",
                "solver": "astrometry.net",
                "image": kwargs["image"],
            }
        return {
            "status": "solved",
            "solver": "astrometry.net",
            "image": kwargs["image"],
            "ra": 180.1,
            "dec": -5.1,
        }

    monkeypatch.setattr(service, "solve", fake_solve)

    result = service.solve_robust("assistant3.fits")

    assert result["status"] == "solved"
    assert result["strategy"] == "scale_broad"
    assert len(calls) == 3

    assert calls[0]["ra_hint"] == 180.0
    assert calls[0]["radius_deg"] == 8.0
    assert calls[1]["ra_hint"] == 180.0
    assert calls[1]["radius_deg"] == 20.0
    assert calls[2]["ra_hint"] is None
    assert calls[2]["dec_hint"] is None
    assert calls[2]["radius_deg"] is None
    assert result["attempts"][2]["position_hint_used"] is False
