from __future__ import annotations

import sqlite3
from pathlib import Path

import pytest

from app.catalog.stellar_catalog import (
    MAX_VISUAL_MAGNITUDE,
    build_stellar_catalog,
    fold_text,
    sources_available,
)
from app.sky.objects import SkyObjectsService
from app.sky.service import sky_service


def test_full_stellar_catalog_contains_naked_eye_stars_and_iau_names():
    if not sources_available():
        pytest.skip("Pinned stellar sources are not packaged in this test run")

    stars, stats = build_stellar_catalog()

    assert stats["bright_star_count"] >= 4000
    assert stats["iau_named_count"] >= 400
    assert stats["max_visual_magnitude"] == MAX_VISUAL_MAGNITUDE

    by_name = {
        fold_text(star["name"]): star
        for star in stars
    }

    for expected in (
        "Vega",
        "Arcturus",
        "Sirius",
        "Deneb",
        "Altair",
        "Polaris",
    ):
        assert fold_text(expected) in by_name

    vega = by_name["vega"]
    assert vega["magnitude"] == pytest.approx(0.03, abs=0.1)
    assert "alpha lyr" in fold_text(vega["search_text"])
    assert "hip" in fold_text(vega["search_text"])


def _create_sky_test_database(path: Path) -> None:
    connection = sqlite3.connect(path)
    connection.execute(
        """
        CREATE TABLE objects (
            id INTEGER PRIMARY KEY,
            name TEXT NOT NULL,
            messier TEXT,
            ngc TEXT,
            ic TEXT,
            object_type TEXT NOT NULL,
            object_type_label_fr TEXT NOT NULL,
            constellation_code TEXT,
            constellation_fr TEXT,
            ra_hours REAL,
            dec_deg REAL,
            magnitude REAL,
            magnitude_band TEXT,
            major_axis_arcmin REAL,
            minor_axis_arcmin REAL,
            common_name_fr TEXT,
            aliases_fr TEXT,
            search_text TEXT NOT NULL
        )
        """
    )
    connection.execute(
        """
        INSERT INTO objects (
            id,
            name,
            object_type,
            object_type_label_fr,
            constellation_code,
            constellation_fr,
            ra_hours,
            dec_deg,
            magnitude,
            magnitude_band,
            common_name_fr,
            aliases_fr,
            search_text
        )
        VALUES (
            1,
            'Vega',
            'star',
            'Étoile',
            'Lyr',
            'Lyre',
            18.6156,
            38.7837,
            0.03,
            'V',
            'Vega',
            'Véga; Alpha Lyr',
            'vega véga alpha lyr hip 91262'
        )
        """
    )
    connection.commit()
    connection.close()


def test_explicit_search_keeps_object_below_altitude_filter(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
):
    database = tmp_path / "catalog.sqlite3"
    _create_sky_test_database(database)

    monkeypatch.setattr(
        sky_service,
        "_equatorial_to_horizontal",
        lambda **kwargs: (6.0, 90.0),
    )

    service = SkyObjectsService(database=database)

    explicit = service.objects(
        latitude=47.45,
        longitude=-0.62,
        category="star",
        query="Vega",
        min_altitude=15.0,
    )

    assert explicit["matched_count"] == 1
    assert explicit["visible_count"] == 0
    assert explicit["objects"][0]["name"] == "Vega"
    assert explicit["objects"][0]["visible"] is False
    assert explicit["objects"][0]["above_horizon"] is True

    browse = service.objects(
        latitude=47.45,
        longitude=-0.62,
        category="star",
        query="",
        min_altitude=15.0,
    )

    assert browse["matched_count"] == 0
    assert browse["objects"] == []
