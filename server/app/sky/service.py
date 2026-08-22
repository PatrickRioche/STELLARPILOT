from datetime import datetime, timezone
from math import (
    asin,
    atan2,
    cos,
    degrees,
    pi,
    radians,
    sin,
    tan,
)

from app.sky.catalog import BRIGHT_STARS


class SkyService:
    MIN_ALIGNMENT_ALTITUDE_DEG = 25.0
    MAX_ALIGNMENT_ALTITUDE_DEG = 80.0
    MAX_ALIGNMENT_MAGNITUDE = 2.5
    IDEAL_ALIGNMENT_ALTITUDE_DEG = 50.0

    @staticmethod
    def _parse_datetime(value: str | None) -> datetime:
        if not value:
            return datetime.now(timezone.utc)

        normalized = value.strip()

        if normalized.endswith("Z"):
            normalized = normalized[:-1] + "+00:00"

        dt = datetime.fromisoformat(normalized)

        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)

        return dt.astimezone(timezone.utc)

    @staticmethod
    def _julian_date(dt: datetime) -> float:
        return (
            dt.timestamp() / 86400.0
            + 2440587.5
        )

    @classmethod
    def _local_sidereal_degrees(
        cls,
        dt: datetime,
        longitude_deg: float,
    ) -> float:
        jd = cls._julian_date(dt)

        t = (
            jd - 2451545.0
        ) / 36525.0

        gmst = (
            280.46061837
            + 360.98564736629
            * (jd - 2451545.0)
            + 0.000387933 * t * t
            - (t * t * t) / 38710000.0
        )

        return (
            gmst + longitude_deg
        ) % 360.0

    @classmethod
    def _equatorial_to_horizontal(
        cls,
        ra_hours: float,
        dec_deg: float,
        latitude_deg: float,
        longitude_deg: float,
        dt: datetime,
    ) -> tuple[float, float]:
        lst_deg = cls._local_sidereal_degrees(
            dt,
            longitude_deg,
        )

        ra_deg = ra_hours * 15.0

        hour_angle_deg = (
            lst_deg - ra_deg + 180.0
        ) % 360.0 - 180.0

        ha = radians(hour_angle_deg)
        dec = radians(dec_deg)
        lat = radians(latitude_deg)

        sin_alt = (
            sin(dec) * sin(lat)
            + cos(dec) * cos(lat) * cos(ha)
        )

        sin_alt = max(
            -1.0,
            min(1.0, sin_alt),
        )

        altitude = asin(sin_alt)

        azimuth = atan2(
            -sin(ha),
            tan(dec) * cos(lat)
            - sin(lat) * cos(ha),
        )

        altitude_deg = degrees(altitude)
        azimuth_deg = (
            degrees(azimuth) + 360.0
        ) % 360.0

        return (
            altitude_deg,
            azimuth_deg,
        )

    @classmethod
    def _alignment_score(
        cls,
        magnitude: float,
        altitude_deg: float,
    ) -> float | None:
        if (
            altitude_deg
            < cls.MIN_ALIGNMENT_ALTITUDE_DEG
            or altitude_deg
            > cls.MAX_ALIGNMENT_ALTITUDE_DEG
            or magnitude
            > cls.MAX_ALIGNMENT_MAGNITUDE
        ):
            return None

        brightness_score = (
            cls.MAX_ALIGNMENT_MAGNITUDE
            - magnitude
        ) / 4.0

        brightness_score = max(
            0.0,
            min(1.0, brightness_score),
        )

        altitude_score = (
            1.0
            - abs(
                altitude_deg
                - cls.IDEAL_ALIGNMENT_ALTITUDE_DEG
            ) / 30.0
        )

        altitude_score = max(
            0.0,
            min(1.0, altitude_score),
        )

        score = (
            0.65 * brightness_score
            + 0.35 * altitude_score
        )

        return round(score, 4)

    @staticmethod
    def _azimuth_direction(
        azimuth_deg: float,
    ) -> str:
        directions = [
            "N",
            "NE",
            "E",
            "SE",
            "S",
            "SW",
            "W",
            "NW",
        ]

        index = int(
            (azimuth_deg + 22.5) // 45
        ) % 8

        return directions[index]

    def bright_stars(
        self,
        latitude: float,
        longitude: float,
        at: str | None = None,
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

        dt = self._parse_datetime(at)

        stars = []

        for star in BRIGHT_STARS:
            altitude_deg, azimuth_deg = (
                self._equatorial_to_horizontal(
                    ra_hours=star["ra_hours"],
                    dec_deg=star["dec_deg"],
                    latitude_deg=latitude,
                    longitude_deg=longitude,
                    dt=dt,
                )
            )

            score = self._alignment_score(
                magnitude=star["magnitude"],
                altitude_deg=altitude_deg,
            )

            stars.append(
                {
                    **star,
                    "altitude_deg": round(
                        altitude_deg,
                        2,
                    ),
                    "azimuth_deg": round(
                        azimuth_deg,
                        2,
                    ),
                    "azimuth_direction":
                        self._azimuth_direction(
                            azimuth_deg
                        ),
                    "above_horizon":
                        altitude_deg > 0.0,
                    "alignment_candidate":
                        score is not None,
                    "alignment_score": score,
                }
            )

        visible = [
            star
            for star in stars
            if star["above_horizon"]
        ]

        visible.sort(
            key=lambda star: (
                star["magnitude"],
                -star["altitude_deg"],
            )
        )

        candidates = [
            star
            for star in stars
            if star["alignment_candidate"]
        ]

        candidates.sort(
            key=lambda star:
                star["alignment_score"],
            reverse=True,
        )

        recommended = (
            candidates[0]
            if candidates
            else None
        )

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
            "criteria": {
                "min_altitude_deg":
                    self.MIN_ALIGNMENT_ALTITUDE_DEG,
                "max_altitude_deg":
                    self.MAX_ALIGNMENT_ALTITUDE_DEG,
                "max_magnitude":
                    self.MAX_ALIGNMENT_MAGNITUDE,
                "ideal_altitude_deg":
                    self.IDEAL_ALIGNMENT_ALTITUDE_DEG,
            },
            "catalog_count": len(
                BRIGHT_STARS
            ),
            "above_horizon_count": len(
                visible
            ),
            "alignment_candidate_count": len(
                candidates
            ),
            "recommended": recommended,
            "stars": visible,
        }


sky_service = SkyService()
