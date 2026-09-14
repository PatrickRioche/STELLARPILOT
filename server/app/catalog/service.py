from __future__ import annotations

import sqlite3
from pathlib import Path
from typing import Any

from app.catalog.bright_stars import (
    BRIGHT_STARS,
    BRIGHT_STAR_SOURCE,
    BRIGHT_STAR_SOURCE_VERSION,
)
from app.catalog.constellations import constellation_fr
from app.catalog.types import object_type_label_fr


ROOT = Path(__file__).resolve().parents[2]

DATABASE = (
    ROOT
    / "data"
    / "catalog.sqlite3"
)


class CatalogService:

    def __init__(
        self,
        database: Path = DATABASE,
    ) -> None:
        self.database = database

    def _connect(self) -> sqlite3.Connection:
        connection = sqlite3.connect(
            self.database
        )

        connection.row_factory = sqlite3.Row

        return connection

    @staticmethod
    def _row_to_dict(
        row: sqlite3.Row | None,
    ) -> dict[str, Any] | None:

        if row is None:
            return None

        return dict(row)

    def ensure_builtin_bright_stars(self) -> int:
        """Add StellarPilot's named bright stars to an existing catalogue.

        The catalogue SQLite file lives in persistent data on the Pi and is
        deliberately preserved by deployments. This idempotent migration lets
        a server update enrich that existing database without replacing it.
        """

        if not self.database.exists():
            return 0

        with self._connect() as connection:
            table = connection.execute(
                """
                SELECT name
                FROM sqlite_master
                WHERE type = 'table'
                  AND name = 'objects'
                LIMIT 1
                """
            ).fetchone()

            if table is None:
                return 0

            columns = {
                row["name"]
                for row in connection.execute(
                    "PRAGMA table_info(objects)"
                )
            }

            if "common_name_fr" not in columns:
                connection.execute(
                    """
                    ALTER TABLE objects
                    ADD COLUMN common_name_fr TEXT
                    """
                )
                columns.add("common_name_fr")

            if "aliases_fr" not in columns:
                connection.execute(
                    """
                    ALTER TABLE objects
                    ADD COLUMN aliases_fr TEXT
                    """
                )
                columns.add("aliases_fr")

            required_columns = {
                "source",
                "source_version",
                "source_type",
                "name",
                "object_type",
                "object_type_label_fr",
                "ra_hours",
                "dec_deg",
                "constellation_code",
                "constellation_fr",
                "magnitude",
                "magnitude_band",
                "major_axis_arcmin",
                "minor_axis_arcmin",
                "position_angle_deg",
                "messier",
                "ngc",
                "ic",
                "common_names",
                "identifiers",
                "search_text",
                "common_name_fr",
                "aliases_fr",
            }

            if not required_columns.issubset(columns):
                return 0

            inserted = 0

            for star in BRIGHT_STARS:
                existing = connection.execute(
                    """
                    SELECT id
                    FROM objects
                    WHERE lower(name) = lower(?)
                    LIMIT 1
                    """,
                    (star["name"],),
                ).fetchone()

                if existing is not None:
                    continue

                aliases = tuple(
                    star.get("aliases") or ()
                )
                identifiers = tuple(
                    star.get("identifiers") or ()
                )

                aliases_text = (
                    "; ".join(aliases)
                    if aliases
                    else None
                )
                identifiers_text = (
                    "; ".join(identifiers)
                    if identifiers
                    else None
                )

                constellation_code = star.get(
                    "constellation_code"
                )
                constellation_name_fr = (
                    constellation_fr(
                        constellation_code
                    )
                    or star.get("constellation")
                )

                search_text = " ".join(
                    str(value).strip()
                    for value in (
                        star["name"],
                        aliases_text,
                        identifiers_text,
                        constellation_code,
                        constellation_name_fr,
                        star.get("constellation"),
                    )
                    if value
                ).lower()

                connection.execute(
                    """
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
                        aliases_fr
                    )
                    VALUES (
                        ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                    )
                    """,
                    (
                        BRIGHT_STAR_SOURCE,
                        BRIGHT_STAR_SOURCE_VERSION,
                        "*",
                        star["name"],
                        "star",
                        "Étoile",
                        star["ra_hours"],
                        star["dec_deg"],
                        constellation_code,
                        constellation_name_fr,
                        star["magnitude"],
                        "V",
                        None,
                        None,
                        None,
                        None,
                        None,
                        None,
                        aliases_text,
                        identifiers_text,
                        search_text,
                        star["name"],
                        aliases_text,
                    ),
                )
                inserted += 1

            connection.commit()

        return inserted

    def bright_stars(
        self,
        max_magnitude: float = 2.5,
    ) -> list[dict[str, Any]]:
        self.ensure_builtin_bright_stars()

        if not self.database.exists():
            return []

        with self._connect() as connection:
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
                    BRIGHT_STAR_SOURCE,
                    BRIGHT_STAR_SOURCE_VERSION,
                    max_magnitude,
                ),
            ).fetchall()

        return [
            dict(row)
            for row in rows
        ]

    def status(self) -> dict[str, Any]:

        if not self.database.exists():
            return {
                "status": "unavailable",
                "database": str(self.database),
                "database_name": self.database.name,
                "database_size_bytes": None,
                "source": None,
                "source_version": None,
                "language": "fr",
                "offline": True,
                "object_count": 0,
                "constellation_count": 88,
                "constellation_codes_in_catalog": 0,
                "french_name_count": 0,
                "french_alias_count": 0,
                "types": {},
                "type_details": [],
            }

        self.ensure_builtin_bright_stars()

        try:
            database_size_bytes = self.database.stat().st_size
        except OSError:
            database_size_bytes = None

        with self._connect() as connection:

            summary = connection.execute(
                """
                SELECT
                    COUNT(*) AS object_count,

                    COUNT(
                        DISTINCT constellation_code
                    ) AS constellation_codes,

                    SUM(
                        CASE
                            WHEN common_name_fr IS NOT NULL
                            AND TRIM(common_name_fr) <> ''
                            THEN 1
                            ELSE 0
                        END
                    ) AS french_name_count,

                    SUM(
                        CASE
                            WHEN aliases_fr IS NOT NULL
                            AND TRIM(aliases_fr) <> ''
                            THEN 1
                            ELSE 0
                        END
                    ) AS french_alias_count

                FROM objects
                """
            ).fetchone()

            type_rows = connection.execute(
                """
                SELECT
                    object_type,
                    COUNT(*) AS count
                FROM objects
                GROUP BY object_type
                ORDER BY count DESC
                """
            ).fetchall()

            source_row = connection.execute(
                """
                SELECT
                    source,
                    source_version
                FROM objects
                WHERE source <> ?
                LIMIT 1
                """,
                (BRIGHT_STAR_SOURCE,),
            ).fetchone()

            if source_row is None:
                source_row = connection.execute(
                    """
                    SELECT
                        source,
                        source_version
                    FROM objects
                    LIMIT 1
                    """
                ).fetchone()

        types = {
            row["object_type"]: row["count"]
            for row in type_rows
        }

        type_details = [
            {
                "type": row["object_type"],
                "label_fr": object_type_label_fr(
                    row["object_type"]
                ),
                "count": row["count"],
            }
            for row in type_rows
        ]

        return {
            "status": "ready",
            "database": str(self.database),
            "database_name": self.database.name,
            "database_size_bytes": database_size_bytes,

            "source": (
                source_row["source"]
                if source_row
                else None
            ),

            "source_version": (
                source_row["source_version"]
                if source_row
                else None
            ),

            "language": "fr",
            "offline": True,

            "object_count":
                summary["object_count"],

            "constellation_count": 88,

            "constellation_codes_in_catalog":
                summary["constellation_codes"],

            "french_name_count":
                summary["french_name_count"] or 0,

            "french_alias_count":
                summary["french_alias_count"] or 0,

            "types": types,
            "type_details": type_details,
        }

    def search(
        self,
        query: str,
        limit: int = 20,
        object_type: str | None = None,
    ) -> dict[str, Any]:

        query = query.strip()

        if not query:
            return {
                "status": "ok",
                "query": query,
                "count": 0,
                "objects": [],
            }

        self.ensure_builtin_bright_stars()

        limit = max(
            1,
            min(limit, 100),
        )

        sql = """
            SELECT
                id,
                source,
                source_version,

                name,
                messier,
                ngc,
                ic,

                object_type,
                object_type_label_fr,

                constellation_code,
                constellation_fr,

                ra_hours,
                dec_deg,

                magnitude,
                magnitude_band,

                major_axis_arcmin,
                minor_axis_arcmin,
                position_angle_deg,

                common_names,
                common_name_fr,
                aliases_fr,
                identifiers

            FROM objects

            WHERE search_text LIKE ?
        """

        parameters: list[Any] = [
            f"%{query.lower()}%"
        ]

        if object_type:
            sql += """
                AND object_type = ?
            """

            parameters.append(
                object_type
            )

        sql += """
            ORDER BY
                CASE
                    WHEN lower(name) = lower(?)
                        THEN 0
                    WHEN lower(messier) = lower(?)
                        THEN 1
                    WHEN lower(ngc) = lower(?)
                        THEN 2
                    WHEN lower(ic) = lower(?)
                        THEN 3
                    ELSE 4
                END,
                magnitude IS NULL,
                magnitude ASC,
                name ASC

            LIMIT ?
        """

        parameters.extend(
            [
                query,
                query,
                query,
                query,
                limit,
            ]
        )

        with self._connect() as connection:

            rows = connection.execute(
                sql,
                parameters,
            ).fetchall()

        return {
            "status": "ok",
            "query": query,
            "count": len(rows),
            "objects": [
                self._row_to_dict(row)
                for row in rows
            ],
        }

    def get(
        self,
        object_id: int,
    ) -> dict[str, Any] | None:

        self.ensure_builtin_bright_stars()

        with self._connect() as connection:

            row = connection.execute(
                """
                SELECT *
                FROM objects
                WHERE id = ?
                LIMIT 1
                """,
                (
                    object_id,
                ),
            ).fetchone()

        return self._row_to_dict(
            row
        )


catalog_service = CatalogService()
catalog_service.ensure_builtin_bright_stars()
