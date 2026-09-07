from __future__ import annotations

import sqlite3
from pathlib import Path
from typing import Any

from app.catalog.bright_stars import BRIGHT_STAR_SOURCE
from app.catalog.constellations import constellation_fr
from app.catalog.stellar_catalog import (
    MAX_VISUAL_MAGNITUDE,
    STELLAR_SOURCE,
    build_stellar_catalog,
    missing_sources,
    sources_available,
    stellar_source_version,
)


METADATA_COLUMNS = {
    "common_name_fr": "TEXT",
    "aliases_fr": "TEXT",
    "stellar_id": "TEXT",
    "iau_name": "TEXT",
    "hip": "TEXT",
    "hr": "TEXT",
    "hd": "TEXT",
    "bayer": "TEXT",
    "flamsteed": "TEXT",
}


def _connect(database: Path) -> sqlite3.Connection:
    connection = sqlite3.connect(database)
    connection.row_factory = sqlite3.Row
    return connection


def _table_exists(connection: sqlite3.Connection) -> bool:
    return (
        connection.execute(
            """
            SELECT 1
            FROM sqlite_master
            WHERE type = 'table'
              AND name = 'objects'
            LIMIT 1
            """
        ).fetchone()
        is not None
    )


def _ensure_columns(connection: sqlite3.Connection) -> set[str]:
    columns = {
        row["name"]
        for row in connection.execute(
            "PRAGMA table_info(objects)"
        )
    }

    for name, sql_type in METADATA_COLUMNS.items():
        if name in columns:
            continue
        connection.execute(
            f"ALTER TABLE objects ADD COLUMN {name} {sql_type}"
        )
        columns.add(name)

    return columns


def stellar_catalog_status(database: Path) -> dict[str, Any]:
    if not database.exists():
        return {
            "status": "database_unavailable",
            "source": STELLAR_SOURCE,
            "source_version": stellar_source_version(),
            "count": 0,
            "iau_named_count": 0,
            "max_visual_magnitude": MAX_VISUAL_MAGNITUDE,
            "missing_sources": missing_sources(),
        }

    with _connect(database) as connection:
        if not _table_exists(connection):
            return {
                "status": "schema_unavailable",
                "source": STELLAR_SOURCE,
                "source_version": stellar_source_version(),
                "count": 0,
                "iau_named_count": 0,
                "max_visual_magnitude": MAX_VISUAL_MAGNITUDE,
                "missing_sources": missing_sources(),
            }

        columns = {
            row["name"]
            for row in connection.execute(
                "PRAGMA table_info(objects)"
            )
        }

        if "iau_name" in columns:
            row = connection.execute(
                """
                SELECT
                    COUNT(*) AS count,
                    SUM(
                        CASE
                            WHEN iau_name IS NOT NULL
                             AND TRIM(iau_name) <> ''
                            THEN 1
                            ELSE 0
                        END
                    ) AS iau_named_count
                FROM objects
                WHERE source = ?
                  AND source_version = ?
                """,
                (
                    STELLAR_SOURCE,
                    stellar_source_version(),
                ),
            ).fetchone()
        else:
            row = connection.execute(
                """
                SELECT COUNT(*) AS count
                FROM objects
                WHERE source = ?
                  AND source_version = ?
                """,
                (
                    STELLAR_SOURCE,
                    stellar_source_version(),
                ),
            ).fetchone()

    count = int(row["count"] or 0)
    iau_named_count = (
        int(row["iau_named_count"] or 0)
        if "iau_named_count" in row.keys()
        else 0
    )

    return {
        "status": "ready" if count else "not_loaded",
        "source": STELLAR_SOURCE,
        "source_version": stellar_source_version(),
        "count": count,
        "iau_named_count": iau_named_count,
        "max_visual_magnitude": MAX_VISUAL_MAGNITUDE,
        "missing_sources": missing_sources(),
    }


def ensure_stellar_catalog(database: Path) -> dict[str, Any]:
    """Load the pinned v0.6.3 stellar catalogue into persistent SQLite.

    The external source files are packaged into the server update kit on the
    development PC / CI. Runtime on the Raspberry Pi remains fully offline.
    The migration is idempotent and never replaces catalog.sqlite3 itself.
    """

    if not database.exists():
        return stellar_catalog_status(database)

    if not sources_available():
        return stellar_catalog_status(database)

    expected_version = stellar_source_version()

    with _connect(database) as connection:
        if not _table_exists(connection):
            return stellar_catalog_status(database)

        _ensure_columns(connection)

        current = connection.execute(
            """
            SELECT COUNT(*) AS count
            FROM objects
            WHERE source = ?
              AND source_version = ?
            """,
            (
                STELLAR_SOURCE,
                expected_version,
            ),
        ).fetchone()

        if int(current["count"] or 0) >= 4000:
            return stellar_catalog_status(database)

    # Build before touching persistent rows. A parsing/source failure leaves
    # the existing catalogue completely unchanged.
    stars, stats = build_stellar_catalog()

    if stats["bright_star_count"] < 4000:
        raise RuntimeError(
            "Stellar catalogue build is unexpectedly small: "
            f"{stats['bright_star_count']} stars <= magnitude 6"
        )

    with _connect(database) as connection:
        _ensure_columns(connection)
        connection.execute("BEGIN IMMEDIATE")

        # Replace only StellarPilot-owned stellar rows. OpenNGC and all other
        # persistent catalogue data remain untouched.
        connection.execute(
            "DELETE FROM objects WHERE source = ?",
            (STELLAR_SOURCE,),
        )
        connection.execute(
            "DELETE FROM objects WHERE source = ?",
            (BRIGHT_STAR_SOURCE,),
        )

        insert_sql = """
            INSERT INTO objects (
                source,
                source_version,
                source_type,
                name,
                object_type,
                object_type_label_fr,
                ra_hours,
                dec_deg,
                constellation_code,
                constellation_fr,
                magnitude,
                magnitude_band,
                major_axis_arcmin,
                minor_axis_arcmin,
                position_angle_deg,
                messier,
                ngc,
                ic,
                common_names,
                identifiers,
                search_text,
                common_name_fr,
                aliases_fr,
                stellar_id,
                iau_name,
                hip,
                hr,
                hd,
                bayer,
                flamsteed
            )
            VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
        """

        rows = []
        for star in stars:
            constellation_code = star.get("constellation_code")
            constellation_name_fr = (
                constellation_fr(constellation_code)
                if constellation_code
                else None
            )

            rows.append(
                (
                    STELLAR_SOURCE,
                    expected_version,
                    "star",
                    star["name"],
                    "star",
                    "Étoile",
                    star["ra_hours"],
                    star["dec_deg"],
                    constellation_code,
                    constellation_name_fr,
                    star.get("magnitude"),
                    "V" if star.get("magnitude") is not None else None,
                    None,
                    None,
                    None,
                    None,
                    None,
                    None,
                    star.get("aliases"),
                    star.get("identifiers"),
                    star["search_text"],
                    star["name"],
                    star.get("aliases"),
                    star.get("stellar_id"),
                    star.get("iau_name"),
                    star.get("hip"),
                    star.get("hr"),
                    star.get("hd"),
                    star.get("bayer"),
                    star.get("flamsteed"),
                )
            )

        connection.executemany(insert_sql, rows)

        connection.execute(
            """
            CREATE INDEX IF NOT EXISTS idx_objects_stellar_id
            ON objects(stellar_id)
            """
        )
        connection.execute(
            """
            CREATE INDEX IF NOT EXISTS idx_objects_iau_name
            ON objects(iau_name)
            """
        )
        connection.execute(
            """
            CREATE INDEX IF NOT EXISTS idx_objects_hip
            ON objects(hip)
            """
        )
        connection.execute(
            """
            CREATE INDEX IF NOT EXISTS idx_objects_hr
            ON objects(hr)
            """
        )
        connection.execute(
            """
            CREATE INDEX IF NOT EXISTS idx_objects_hd
            ON objects(hd)
            """
        )

        connection.commit()

    result = stellar_catalog_status(database)
    result["build_stats"] = stats
    return result


def bright_stars_from_database(
    database: Path,
    max_magnitude: float = 2.5,
) -> list[dict[str, Any]]:
    if not database.exists():
        return []

    ensure_stellar_catalog(database)

    with _connect(database) as connection:
        if not _table_exists(connection):
            return []

        rows = connection.execute(
            """
            SELECT
                id,
                name,
                object_type,
                constellation_fr AS constellation,
                constellation_code,
                magnitude,
                ra_hours,
                dec_deg
            FROM objects
            WHERE source = ?
              AND source_version = ?
              AND object_type = 'star'
              AND magnitude IS NOT NULL
              AND magnitude <= ?
            ORDER BY magnitude ASC, name ASC
            """,
            (
                STELLAR_SOURCE,
                stellar_source_version(),
                max_magnitude,
            ),
        ).fetchall()

    return [dict(row) for row in rows]
