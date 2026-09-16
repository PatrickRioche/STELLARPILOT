import app.main as main_module

from fastapi.testclient import TestClient

from app.indi import home_routes


client = TestClient(main_module.app)


def test_home_reset_requires_explicit_physical_confirmation():
    response = client.post(
        "/mount/home/set",
        json={
            "confirmed_physical_home": False,
            "pole_solve_dec_deg": 89.4,
        },
    )
    body = response.json()
    assert body["status"] == "error"
    assert "Confirmation physique obligatoire" in body["detail"]


def test_home_reset_rejects_field_away_from_pole(monkeypatch):
    monkeypatch.setattr(home_routes, "_connected_mount", lambda: "LX200 OnStep")
    monkeypatch.setattr(
        home_routes,
        "_home_properties",
        lambda _mount: {
            "home_set_supported": True,
            "home_go_supported": True,
            "parked": False,
            "tracking": False,
            "home_state": "Idle",
            "raw": "",
        },
    )
    monkeypatch.setattr(
        home_routes,
        "_trusted_mount_clock",
        lambda: ({"status": "available"}, "gps", None),
    )
    monkeypatch.setattr(
        home_routes,
        "_sync_location_from_gps",
        lambda _mount: {"status": "synced", "latitude": 47.4},
    )

    response = client.post(
        "/mount/home/set",
        json={
            "confirmed_physical_home": True,
            "pole_solve_dec_deg": 72.0,
        },
    )
    body = response.json()
    assert body["status"] == "error"
    assert "pas assez proche" in body["detail"]


def test_home_reset_uses_indi_standard_property(monkeypatch):
    monkeypatch.setattr(home_routes, "_connected_mount", lambda: "LX200 OnStep")

    reads = iter(
        [
            {
                "home_set_supported": True,
                "home_go_supported": True,
                "parked": False,
                "tracking": True,
                "home_state": "Idle",
                "raw": "before",
            },
            {
                "home_set_supported": True,
                "home_go_supported": True,
                "parked": False,
                "tracking": False,
                "home_state": "Ok",
                "raw": "after",
            },
        ]
    )
    monkeypatch.setattr(home_routes, "_home_properties", lambda _mount: next(reads))
    monkeypatch.setattr(
        home_routes,
        "_trusted_mount_clock",
        lambda: ({"status": "available", "time_sync_verification": {"verified": True}}, "gps", None),
    )
    monkeypatch.setattr(
        home_routes,
        "_sync_location_from_gps",
        lambda _mount: {
            "status": "synced",
            "source": "gps",
            "latitude": 47.4118,
            "longitude": -0.6407,
        },
    )
    monkeypatch.setattr(home_routes.time, "sleep", lambda _seconds: None)
    monkeypatch.setattr(
        main_module._core.indi_service,
        "mount_status",
        lambda: {"status": "idle", "ra": 12.0, "dec": 90.0},
    )

    writes = []

    def fake_set(value):
        writes.append(value)
        return {"status": "sent", "property": value.split("=", 1)[0]}

    monkeypatch.setattr(home_routes, "_indi_set", fake_set)

    response = client.post(
        "/mount/home/set",
        json={
            "confirmed_physical_home": True,
            "pole_solve_dec_deg": 89.6,
        },
    )
    body = response.json()

    assert body["status"] == "home_set"
    assert body["pole_verification"]["status"] == "verified"
    assert writes == [
        "LX200 OnStep.TELESCOPE_TRACK_STATE.TRACK_OFF=On",
        "LX200 OnStep.TELESCOPE_HOME.SET=On",
    ]
    assert body["tracking_stopped"] is True
    assert ":hF#" in body["note"]
