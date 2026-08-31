from __future__ import annotations

from astropy import units as u
from astropy.coordinates import (
    AltAz,
    EarthLocation,
    get_body,
    solar_system_ephemeris,
)
from astropy.time import Time
from astropy.utils import iers

from app.sky.service import sky_service


# StellarPilot is designed to work without Internet access in the field.
# Astropy must therefore use its bundled Earth-orientation data instead of
# attempting an IERS download while an observation session is in progress.
iers.conf.auto_download = False
iers.conf.auto_max_age = None


SOLAR_SYSTEM_BODIES = (
    {
        "id": -101,
        "key": "sun",
        "name": "Soleil",
        "object_type": "sun",
        "object_type_label_fr": "Soleil",
        "symbol": "☉",
        "solar_warning": True,
    },
    {
        "id": -102,
        "key": "moon",
        "name": "Lune",
        "object_type": "moon",
        "object_type_label_fr": "Lune",
        "symbol": "☾",
        "solar_warning": False,
    },
    {
        "id": -103,
        "key": "mercury",
        "name": "Mercure",
        "object_type": "planet",
        "object_type_label_fr": "Planète",
        "symbol": "☿",
        "solar_warning": False,
    },
    {
        "id": -104,
        "key": "venus",
        "name": "Vénus",
        "object_type": "planet",
        "object_type_label_fr": "Planète",
        "symbol": "♀",
        "solar_warning": False,
    },
    {
        "id": -105,
        "key": "mars",
        "name": "Mars",
        "object_type": "planet",
        "object_type_label_fr": "Planète",
        "symbol": "♂",
        "solar_warning": False,
    },
    {
        "id": -106,
        "key": "jupiter",
        "name": "Jupiter",
        "object_type": "planet",
        "object_type_label_fr": "Planète",
        "symbol": "♃",
        "solar_warning": False,
    },
    {
        "id": -107,
        "key": "saturn",
        "name": "Saturne",
        "object_type": "planet",
        "object_type_label_fr": "Planète",
        "symbol": "♄",
        "solar_warning": False,
    },
    {
        "id": -108,
        "key": "uranus",
        "name": "Uranus",
        "object_type": "planet",
        "object_type_label_fr": "Planète",
        "symbol": "♅",
        "solar_warning": False,
    },
    {
        "id": -109,
        "key": "neptune",
        "name": "Neptune",
        "object_type": "planet",
        "object_type_label_fr": "Planète",
        "symbol": "♆",
        "solar_warning": False,
    },
)


class SolarSystemService:

    def objects(
        self,
        latitude: float,
        longitude: float,
        at: str | None = None,
        query: str | None = None,
        direction: str | None = None,
        sort: str = "altitude",
        order: str = "desc",
        offset: int = 0,
        limit: int = 100,
        location_source: str | None = None,
    ) -> dict:
        if not -90.0 <= latitude <= 90.0:
            raise ValueError(
                "latitude must be between -90 and 90"
            )

        if not -180.0 <= longitude <= 180.0:
            raise ValueError(
                "longitude must be between -180 and 180"
            )

        dt = sky_service._parse_datetime(at)
        observation_time = Time(dt)
        observer = EarthLocation.from_geodetic(
            lon=longitude * u.deg,
            lat=latitude * u.deg,
            height=0.0 * u.m,
        )

        normalized_query = (
            query.strip().casefold()
            if query
            else ""
        )

        normalized_direction = (
            direction.strip().upper()
            if direction
            else None
        )

        objects = []

        # "builtin" uses the ERFA ephemerides shipped with Astropy and never
        # requires a network connection, which is essential on the telescope.
        with solar_system_ephemeris.set("builtin"):
            for body in SOLAR_SYSTEM_BODIES:
                coordinates = get_body(
                    body["key"],
                    observation_time,
                    observer,
                )

                horizontal = coordinates.transform_to(
                    AltAz(
                        obstime=observation_time,
                        location=observer,
                    )
                )

                altitude_deg = float(
                    horizontal.alt.deg
                )
                azimuth_deg = float(
                    horizontal.az.deg
                )
                azimuth_direction = (
                    sky_service._azimuth_direction(
                        azimuth_deg
                    )
                )
                above_horizon = altitude_deg > 0.0

                if (
                    normalized_query
                    and normalized_query
                    not in body["name"].casefold()
                    and normalized_query
                    not in body["key"].casefold()
                ):
                    continue

                if (
                    normalized_direction
                    and azimuth_direction
                    != normalized_direction
                ):
                    continue

                objects.append(
                    {
                        "id": body["id"],
                        "name": body["name"],
                        "catalog_name": body["key"],
                        "reference": body["name"],
                        "object_type": body[
                            "object_type"
                        ],
                        "object_type_label_fr": body[
                            "object_type_label_fr"
                        ],
                        "constellation": None,
                        "ra_hours": round(
                            float(coordinates.ra.hour),
                            8,
                        ),
                        "dec_deg": round(
                            float(coordinates.dec.deg),
                            8,
                        ),
                        "magnitude": None,
                        "magnitude_band": None,
                        "major_axis_arcmin": None,
                        "minor_axis_arcmin": None,
                        "aliases_fr": None,
                        "altitude_deg": round(
                            altitude_deg,
                            2,
                        ),
                        "azimuth_deg": round(
                            azimuth_deg,
                            2,
                        ),
                        "azimuth_direction":
                            azimuth_direction,
                        "visible": above_horizon,
                        "above_horizon":
                            above_horizon,
                        "solar_warning": body[
                            "solar_warning"
                        ],
                        "symbol": body["symbol"],
                    }
                )

        normalized_sort = (
            sort.strip().lower()
            if sort
            else "altitude"
        )
        normalized_order = (
            order.strip().lower()
            if order
            else "desc"
        )

        reverse = normalized_order == "desc"

        if normalized_sort == "name":
            objects.sort(
                key=lambda item:
                    item["name"].casefold(),
                reverse=reverse,
            )
        elif normalized_sort == "altitude":
            objects.sort(
                key=lambda item:
                    item["altitude_deg"],
                reverse=reverse,
            )

        safe_offset = max(0, offset)
        safe_limit = max(
            1,
            min(100, limit),
        )
        returned = objects[
            safe_offset:safe_offset + safe_limit
        ]
        visible_count = sum(
            1
            for item in objects
            if item["above_horizon"]
        )

        return {
            "status": "ok",
            "observer": {
                "latitude": latitude,
                "longitude": longitude,
                "timestamp_utc": dt.isoformat(),
                "location_source":
                    location_source,
            },
            "category": "solar_system",
            "category_label_fr":
                "Système solaire",
            "query": query,
            # Solar-system mode intentionally keeps bodies below the horizon
            # in the result so the UI can show why GOTO is unavailable.
            "min_altitude_deg": 0.0,
            "direction": normalized_direction,
            "constellation": None,
            "visible_count": visible_count,
            "returned_count": len(returned),
            "limit": safe_limit,
            "offset": safe_offset,
            "sort": normalized_sort,
            "order": normalized_order,
            "has_previous": safe_offset > 0,
            "has_next": (
                safe_offset + len(returned)
                < len(objects)
            ),
            "ephemeris": "astropy_builtin",
            "objects": returned,
        }


solar_system_service = SolarSystemService()
