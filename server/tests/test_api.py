from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)


def test_status():
    response = client.get("/status")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_location():
    response = client.post(
        "/system/location",
        json={
            "latitude": 47.47,
            "longitude": -0.55,
            "altitude": 50,
            "timestamp": "2026-08-11T13:52:00+02:00",
        },
    )
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_mount_type():
    response = client.post("/system/mount-type", json={"mount_type": "EQ"})
    assert response.status_code == 200
    assert response.json()["mount_type"] == "EQ"
