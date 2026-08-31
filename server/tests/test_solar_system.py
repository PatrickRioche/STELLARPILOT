from app.sky.solar_system import solar_system_service


EXPECTED_NAMES = {
    "Soleil",
    "Lune",
    "Mercure",
    "Vénus",
    "Mars",
    "Jupiter",
    "Saturne",
    "Uranus",
    "Neptune",
}


def test_solar_system_returns_all_major_bodies():
    result = solar_system_service.objects(
        latitude=47.47,
        longitude=-0.55,
        at="2026-08-31T06:55:00+00:00",
        sort="altitude",
        order="desc",
    )

    assert result["status"] == "ok"
    assert result["category"] == "solar_system"
    assert result["returned_count"] == 9
    assert {
        item["name"]
        for item in result["objects"]
    } == EXPECTED_NAMES

    for item in result["objects"]:
        assert 0.0 <= item["ra_hours"] < 24.0
        assert -90.0 <= item["dec_deg"] <= 90.0
        assert -90.0 <= item["altitude_deg"] <= 90.0
        assert 0.0 <= item["azimuth_deg"] < 360.0
        assert isinstance(
            item["above_horizon"],
            bool,
        )


def test_sun_is_flagged_for_solar_safety():
    result = solar_system_service.objects(
        latitude=47.47,
        longitude=-0.55,
        at="2026-08-31T12:00:00+00:00",
    )

    sun = next(
        item
        for item in result["objects"]
        if item["name"] == "Soleil"
    )

    assert sun["object_type"] == "sun"
    assert sun["solar_warning"] is True
    assert sun["symbol"] == "☉"
