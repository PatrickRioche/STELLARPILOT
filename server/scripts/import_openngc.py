from __future__ import annotations

import csv
import re
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.catalog.constellations import (
    constellation_fr,
)
from app.catalog.types import (
    normalize_object_type,
)




SOURCE_DIR = (
    ROOT
    / "data"
    / "openngc"
)

DATABASE = (
    ROOT
    / "data"
    / "catalog.sqlite3"
)

SOURCE_VERSION = "OpenNGC v20260501 / 36cb178"


def clean(value: str | None) -> str | None:
    if value is None:
        return None

    value = value.strip()

    return value or None


def number(value: str | None) -> float | None:
    value = clean(value)

    if value is None:
        return None

    try:
        return float(value)
    except ValueError:
        return None


def ra_hours(value: str | None) -> float | None:
    value = clean(value)

    if value is None:
        return None

    try:
        h, m, s = [
            float(part)
            for part in value.split(":")
        ]
    except (ValueError, TypeError):
        return None

    return (
        h
        + m / 60.0
        + s / 3600.0
    )


def dec_degrees(
    value: str | None,
) -> float | None:

    value = clean(value)

    if value is None:
        return None

    sign = -1.0 if value.startswith("-") else 1.0

    raw = (
        value
        .replace("+", "")
        .replace("-", "")
    )

    try:
        d, m, s = [
            float(part)
            for part in raw.split(":")
        ]
    except (ValueError, TypeError):
        return None

    return sign * (
        d
        + m / 60.0
        + s / 3600.0
    )


def normalize_name(
    value: str | None,
) -> str | None:

    value = clean(value)

    if value is None:
        return None

    match = re.fullmatch(
        r"(NGC|IC)0*(\d+)(.*)",
        value,
        flags=re.IGNORECASE,
    )

    if match:
        catalog = match.group(1).upper()
        number_value = match.group(2)
        suffix = match.group(3).strip()

        if suffix:
            return f"{catalog} {number_value}{suffix}"

        return f"{catalog} {number_value}"

    match = re.fullmatch(
        r"M0*(\d+)",
        value,
        flags=re.IGNORECASE,
    )

    if match:
        return f"M{match.group(1)}"

    return value


def messier_name(
    value: str | None,
) -> str | None:

    value = clean(value)

    if value is None:
        return None

    value = value.upper()

    if value.startswith("M"):
        value = value[1:]

    try:
        return f"M{int(value)}"
    except ValueError:
        return value


def detect_dialect(path: Path):
    with path.open(
        "r",
        encoding="utf-8-sig",
        newline="",
    ) as handle:

        sample = handle.read(8192)

    return csv.Sniffer().sniff(
        sample,
        delimiters=",;",
    )


def rows(path: Path):
    dialect = detect_dialect(path)

    with path.open(
        "r",
        encoding="utf-8-sig",
        newline="",
    ) as handle:

        yield from csv.DictReader(
            handle,
            dialect=dialect,
        )


def magnitude(row: dict) -> tuple[float | None, str | None]:
    value = number(
        row.get("V-Mag")
    )

    if value is not None:
        return value, "V"

    value = number(
        row.get("B-Mag")
    )

    if value is not None:
        return value, "B"

    return None, None


def build_search_text(
    *values: str | None,
) -> str:

    return " ".join(
        value.strip()
        for value in values
        if value and value.strip()
    ).lower()


def main():
    DATABASE.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    if DATABASE.exists():
        DATABASE.unlink()

    connection = sqlite3.connect(
        DATABASE
    )

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

    connection.execute(
        """
        CREATE INDEX idx_objects_name
        ON objects(name)
        """
    )

    connection.execute(
        """
        CREATE INDEX idx_objects_type
        ON objects(object_type)
        """
    )

    connection.execute(
        """
        CREATE INDEX idx_objects_constellation
        ON objects(constellation_code)
        """
    )

    connection.execute(
        """
        CREATE INDEX idx_objects_magnitude
        ON objects(magnitude)
        """
    )

    imported = 0
    skipped = 0

    source_files = [
        SOURCE_DIR / "NGC.csv",
        SOURCE_DIR / "addendum.csv",
    ]

    for source_file in source_files:

        for row in rows(source_file):

            source_type = clean(
                row.get("Type")
            )

            if source_type in {
                "NonEx",
                "Dup",
            }:
                skipped += 1
                continue

            ra = ra_hours(
                row.get("RA")
            )

            dec = dec_degrees(
                row.get("Dec")
            )

            if ra is None or dec is None:
                skipped += 1
                continue

            raw_name = clean(
                row.get("Name")
            )

            name = normalize_name(
                raw_name
            )

            if name is None:
                skipped += 1
                continue

            object_type, label_fr = (
                normalize_object_type(
                    source_type
                )
            )

            constellation_code = clean(
                row.get("Const")
            )

            constellation_name_fr = (
                constellation_fr(
                    constellation_code
                )
            )

            mag, mag_band = magnitude(
                row
            )

            messier = messier_name(
                row.get("M")
            )

            ngc = normalize_name(
                row.get("NGC")
            )

            ic = normalize_name(
                row.get("IC")
            )

            common_names = clean(
                row.get("Common names")
            )

            identifiers = clean(
                row.get("Identifiers")
            )

            search_text = build_search_text(
                name,
                messier,
                ngc,
                ic,
                common_names,
                identifiers,
                constellation_name_fr,
                constellation_code,
            )

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
                    search_text
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                )
                """,
                (
                    "OpenNGC",
                    SOURCE_VERSION,
                    source_type,
                    name,
                    object_type,
                    label_fr,
                    ra,
                    dec,
                    constellation_code,
                    constellation_name_fr,
                    mag,
                    mag_band,
                    number(
                        row.get("MajAx")
                    ),
                    number(
                        row.get("MinAx")
                    ),
                    number(
                        row.get("PosAng")
                    ),
                    messier,
                    ngc,
                    ic,
                    common_names,
                    identifiers,
                    search_text,
                ),
            )

            imported += 1

    connection.commit()

    print()
    print("=== STELLARPILOT CATALOG ===")
    print(f"Database : {DATABASE}")
    print(f"Imported : {imported}")
    print(f"Skipped  : {skipped}")

    print()
    print("=== TYPES ===")

    for row in connection.execute(
        """
        SELECT
            object_type,
            COUNT(*)
        FROM objects
        GROUP BY object_type
        ORDER BY COUNT(*) DESC
        """
    ):
        print(
            f"{row[0]:24s} {row[1]}"
        )

    print()
    print("=== TEST OBJECTS ===")

    for query in (
        "M31",
        "M42",
        "M45",
    ):
        result = connection.execute(
            """
            SELECT
                name,
                messier,
                object_type_label_fr,
                constellation_fr,
                magnitude
            FROM objects
            WHERE search_text LIKE ?
            LIMIT 1
            """,
            (
                f"%{query.lower()}%",
            ),
        ).fetchone()

        print(
            query,
            "=>",
            result,
        )

    connection.close()


if __name__ == "__main__":
    main()
