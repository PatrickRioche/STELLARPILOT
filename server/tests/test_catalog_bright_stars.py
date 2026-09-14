from __future__ import annotations

import sqlite3

from app.catalog.bright_stars import BRIGHT_STARS
from app.catalog.service import CatalogService


def _create_catalog(path) -> None:
    with sqlite3.connect(path) as connection:
        connection.execute(
            """
            CREATE TABLE objects (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source TEXT NOT NULL,
                source_version TEXT NOT NULL,
                source_type TEXT,
                name TEXT NOT NULL,
                object_type TEXT NOT NULL,
                object_type_label_fr TEXT NOT NULL,
                ra_hours REAL NOT NULL,
                dec_deg REAL NOT NULL,
                constellation_code TEXT,
                constellation_fr TEXT,
                magnitude REAL,
                magnitude_band TEXT,
                major_axis_arcmin REAL,
                minor_axis_arcmin REAL,
                position_angle_deg REAL,
                messier TEXT,
                ngc TEXT,
                ic TEXT,
                common_names TEXT,
                identifiers TEXT,
                search_text TEXT NOT NULL
            )
            """
        )


def test_named_bright_stars_are_seeded_and_searchable(tmp_path):
    database = tmp_path / "catalog.sqlite3"
    _create_catalog(database)
    service = CatalogService(database)

    inserted = service.ensure_builtin_bright_stars()

    assert inserted == len(BRIGHT_STARS)
    assert service.ensure_builtin_bright_stars() == 0

    expected = {
        "Vega": "Vega",
        "Véga": "Vega",
        "Arcturus": "Arcturus",
        "Sirius": "Sirius",
        "Deneb": "Deneb",
        "Altair": "Altair",
        "Étoile polaire": "Polaris",
        "Alpha UMi": "Polaris",
    }

    for query, name in expected.items():
        result = service.search(query, limit=5)
        assert result["objects"]
        assert result["objects"][0]["name"] == name
        assert result["objects"][0]["object_type"] == "star"


def test_bright_star_catalog_contains_named_alignment_stars(tmp_path):
    database = tmp_path / "catalog.sqlite3"
    _create_catalog(database)
    service = CatalogService(database)

    stars = service.bright_stars(max_magnitude=2.5)
    names = {star["name"] for star in stars}

    assert {
        "Vega",
        "Arcturus",
        "Polaris",
        "Sirius",
        "Deneb",
        "Altair",
    }.issubset(names)
