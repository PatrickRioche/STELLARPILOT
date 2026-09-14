import sqlite3

from app.setup.service import SetupService


def test_setup_service_matches_real_indi_train(tmp_path, monkeypatch):
    database = tmp_path / "userdb.sqlite"
    connection = sqlite3.connect(database)
    connection.execute(
        """
        CREATE TABLE opticaltrains (
            id INTEGER PRIMARY KEY,
            profile INTEGER,
            name TEXT,
            mount TEXT,
            dustcap TEXT,
            lightbox TEXT,
            scope TEXT,
            reducer REAL,
            rotator TEXT,
            focuser TEXT,
            filterwheel TEXT,
            camera TEXT,
            guider TEXT
        )
        """
    )
    connection.execute(
        """
        CREATE TABLE telescope (
            id INTEGER PRIMARY KEY,
            Vendor TEXT,
            Aperture REAL,
            Model TEXT,
            Type TEXT,
            FocalLength REAL
        )
        """
    )
    connection.execute(
        """
        INSERT INTO telescope
            (id, Vendor, Aperture, Model, Type, FocalLength)
        VALUES
            (3, 'ASKAR', 71.0, '71F', 'Réfracteur', 490.0)
        """
    )
    connection.execute(
        """
        INSERT INTO opticaltrains
            (id, profile, name, mount, scope, reducer, camera)
        VALUES
            (2, 2, 'Imageur', 'LX200 OnStep', 'ASKAR 71F 490@F/6.9', 1.0,
             'PlayerOne CCD Uranus-C')
        """
    )
    connection.commit()
    connection.close()

    monkeypatch.setenv("STELLARPILOT_KSTARS_DB", str(database))
    monkeypatch.setattr(SetupService, "_kstars_running", staticmethod(lambda: True))

    result = SetupService().status(
        {
            "mount": {"status": "ready", "name": "LX200 OnStep"},
            "camera": {"status": "ready", "name": "PlayerOne CCD Uranus-C"},
        }
    )

    assert result["status"] == "ready"
    assert result["optical_train_name"] == "Imageur"
    assert result["telescope_name"] == "ASKAR 71F"
    assert result["aperture_mm"] == 71.0
    assert result["focal_length_mm"] == 490.0
    assert abs(result["focal_ratio"] - (490.0 / 71.0)) < 0.001
    assert result["effective_focal_length_mm"] == 490.0
    assert result["consistency"] == "ok"
    assert result["kstars_running"] is True


def test_setup_service_flags_sample_scope(tmp_path, monkeypatch):
    database = tmp_path / "userdb.sqlite"
    connection = sqlite3.connect(database)
    connection.execute(
        """
        CREATE TABLE opticaltrains (
            id INTEGER PRIMARY KEY,
            profile INTEGER,
            name TEXT,
            mount TEXT,
            scope TEXT,
            reducer REAL,
            camera TEXT
        )
        """
    )
    connection.execute(
        """
        CREATE TABLE telescope (
            id INTEGER PRIMARY KEY,
            Vendor TEXT,
            Aperture REAL,
            Model TEXT,
            Type TEXT,
            FocalLength REAL
        )
        """
    )
    connection.execute(
        """
        INSERT INTO telescope
            (id, Vendor, Aperture, Model, Type, FocalLength)
        VALUES
            (3, 'ASKAR', 6.9, '71F', 'Réfracteur', 490.0)
        """
    )
    connection.execute(
        """
        INSERT INTO opticaltrains
            (id, profile, name, mount, scope, reducer, camera)
        VALUES
            (2, 2, 'Imageur', 'LX200 OnStep', 'Sample Primary 700@F/5.8', 1.0,
             'PlayerOne CCD Uranus-C')
        """
    )
    connection.commit()
    connection.close()

    monkeypatch.setenv("STELLARPILOT_KSTARS_DB", str(database))
    monkeypatch.setattr(SetupService, "_kstars_running", staticmethod(lambda: False))

    result = SetupService().status(
        {
            "mount": {"status": "ready", "name": "LX200 OnStep"},
            "camera": {"status": "ready", "name": "PlayerOne CCD Uranus-C"},
        }
    )

    assert result["status"] == "ready"
    assert result["scope_config"] == "Sample Primary 700@F/5.8"
    assert result["focal_length_mm"] == 700.0
    assert result["focal_ratio"] == 5.8
    assert result["aperture_mm"] is None
    assert result["consistency"] == "warning"
    assert "Sample" in result["detail"]
