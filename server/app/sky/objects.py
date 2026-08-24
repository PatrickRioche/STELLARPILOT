from __future__ import annotations

import sqlite3
from pathlib import Path

from app.sky.service import sky_service


ROOT = Path(__file__).resolve().parents[2]
DATABASE = ROOT / "data" / "catalog.sqlite3"


CATEGORY_TYPES = {
    "star": (
        "star",
        "star_double",
    ),
    "galaxy": (
        "galaxy",
        "galaxy_pair",
        "galaxy_group",
    ),
    "nebula": (
        "nebula_diffuse",
        "nebula_planetary",
        "nebula_dark",
        "supernova_remnant",
    ),
    "cluster": (
        "cluster_open",
        "cluster_globular",
    ),
}


CATEGORY_LABELS_FR = {
    "all": "Tous",
    "star": "Étoiles",
    "galaxy": "Galaxies",
    "nebula": "Nébuleuses",
    "cluster": "Amas",
}


DIRECTIONS = {
    "N",
    "NE",
    "E",
    "SE",
    "S",
    "SW",
    "W",
    "NW",
}


class SkyObjectsService:

    def _connect(self):
        connection = sqlite3.connect(DATABASE)
        connection.row_factory = sqlite3.Row
        return connection

    def objects(
        self,
        latitude: float,
        longitude: float,
        at: str | None = None,
        category: str = "all",
        query: str | None = None,
        min_altitude: float = 15.0,
        direction: str | None = None,
        constellation: str | None = None,
        limit: int = 100,
        location_source: str | None = None,
    ) -> dict:

        category = (
            category.strip().lower()
            if category
            else "all"
        )

        if category not in {
            "all",
            *CATEGORY_TYPES.keys(),
        }:
            raise ValueError(
                f"Unknown category: {category}"
            )

        normalized_direction = (
            direction.strip().upper()
            if direction
            else None
        )

        if (
            normalized_direction
            and normalized_direction
            not in DIRECTIONS
        ):
            raise ValueError(
                f"Unknown direction: {direction}"
            )

        normalized_constellation = (
            constellation.strip().casefold()
            if constellation
            else None
        )

        min_altitude = max(
            0.0,
            min(90.0, min_altitude),
        )

        limit = max(
            1,
            min(300, limit),
        )

        dt = sky_service._parse_datetime(at)

        sql = """
            SELECT
                id,
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

                common_name_fr,
                aliases_fr

            FROM objects

            WHERE
                ra_hours IS NOT NULL
                AND dec_deg IS NOT NULL
        """

        parameters = []

        if category != "all":

            types = CATEGORY_TYPES[category]

            placeholders = ",".join(
                "?"
                for _ in types
            )

            sql += (
                f" AND object_type "
                f"IN ({placeholders})"
            )

            parameters.extend(types)

        normalized_query = (
            query.strip().lower()
            if query
            else ""
        )

        if normalized_query:

            sql += """
                AND search_text LIKE ?
            """

            parameters.append(
                f"%{normalized_query}%"
            )

        with self._connect() as connection:

            rows = connection.execute(
                sql,
                parameters,
            ).fetchall()

        visible = []

        for row in rows:

            if normalized_constellation:

                row_constellation = (
                    row["constellation_fr"]
                    or ""
                )

                if (
                    row_constellation
                    .casefold()
                    != normalized_constellation
                ):
                    continue

            altitude_deg, azimuth_deg = (
                sky_service
                ._equatorial_to_horizontal(
                    ra_hours=row["ra_hours"],
                    dec_deg=row["dec_deg"],
                    latitude_deg=latitude,
                    longitude_deg=longitude,
                    dt=dt,
                )
            )

            if altitude_deg < min_altitude:
                continue

            azimuth_direction = (
                sky_service
                ._azimuth_direction(
                    azimuth_deg
                )
            )

            if (
                normalized_direction
                and azimuth_direction
                != normalized_direction
            ):
                continue

            display_name = (
                row["common_name_fr"]
                or row["messier"]
                or row["name"]
            )

            catalog_reference = (
                row["messier"]
                or row["ngc"]
                or row["ic"]
                or row["name"]
            )

            visible.append(
                {
                    "id": row["id"],

                    "name": display_name,

                    "catalog_name":
                        row["name"],

                    "reference":
                        catalog_reference,

                    "messier":
                        row["messier"],

                    "ngc":
                        row["ngc"],

                    "ic":
                        row["ic"],

                    "object_type":
                        row["object_type"],

                    "object_type_label_fr":
                        row[
                            "object_type_label_fr"
                        ],

                    "constellation":
                        row["constellation_fr"],

                    "ra_hours":
                        row["ra_hours"],

                    "dec_deg":
                        row["dec_deg"],

                    "magnitude":
                        row["magnitude"],

                    "magnitude_band":
                        row["magnitude_band"],

                    "major_axis_arcmin":
                        row["major_axis_arcmin"],

                    "minor_axis_arcmin":
                        row["minor_axis_arcmin"],

                    "aliases_fr":
                        row["aliases_fr"],

                    "altitude_deg":
                        round(
                            altitude_deg,
                            2,
                        ),

                    "azimuth_deg":
                        round(
                            azimuth_deg,
                            2,
                        ),

                    "azimuth_direction":
                        azimuth_direction,

                    "visible": True,
                }
            )

        visible.sort(
            key=lambda obj: (
                obj["magnitude"] is None,
                (
                    obj["magnitude"]
                    if obj["magnitude"]
                    is not None
                    else 99.0
                ),
                -obj["altitude_deg"],
                obj["name"],
            )
        )

        returned = visible[:limit]

        return {
            "status": "ok",

            "observer": {
                "latitude": latitude,
                "longitude": longitude,
                "timestamp_utc":
                    dt.isoformat(),
                "location_source":
                    location_source,
            },

            "category": category,

            "category_label_fr":
                CATEGORY_LABELS_FR[
                    category
                ],

            "query": query,

            "min_altitude_deg":
                min_altitude,

            "direction":
                normalized_direction,

            "constellation":
                constellation,

            "visible_count":
                len(visible),

            "returned_count":
                len(returned),

            "limit":
                limit,

            "categories": [
                {
                    "id": key,
                    "label_fr": label,
                }
                for key, label
                in CATEGORY_LABELS_FR.items()
            ],

            "objects": returned,
        }


sky_objects_service = SkyObjectsService()
