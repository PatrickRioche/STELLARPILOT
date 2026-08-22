from __future__ import annotations

import sqlite3
from pathlib import Path
from typing import Any


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

    def status(self) -> dict[str, Any]:

        if not self.database.exists():
            return {
                "status": "unavailable",
                "database": str(
                    self.database
                ),
                "object_count": 0,
                "types": {},
            }

        with self._connect() as connection:

            object_count = connection.execute(
                """
                SELECT COUNT(*)
                FROM objects
                """
            ).fetchone()[0]

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

        return {
            "status": "ready",
            "database": str(
                self.database
            ),
            "object_count": object_count,
            "types": {
                row["object_type"]:
                    row["count"]
                for row in type_rows
            },
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
