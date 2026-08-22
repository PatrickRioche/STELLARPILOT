from __future__ import annotations

import sqlite3
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]

if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))


from app.catalog.names_fr import french_object_name


DATABASE = ROOT / "data" / "catalog.sqlite3"


def main() -> None:
    connection = sqlite3.connect(DATABASE)
    connection.row_factory = sqlite3.Row

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

    if "aliases_fr" not in columns:
        connection.execute(
            """
            ALTER TABLE objects
            ADD COLUMN aliases_fr TEXT
            """
        )

    rows = connection.execute(
        """
        SELECT
            id,
            name,
            messier,
            ngc,
            ic,
            common_names,
            identifiers,
            constellation_code,
            constellation_fr
        FROM objects
        """
    ).fetchall()

    enriched = 0

    for row in rows:
        common_name_fr, aliases = french_object_name(
            row["messier"],
            row["name"],
            row["ngc"],
            row["ic"],
        )

        aliases_fr = (
            "; ".join(aliases)
            if aliases
            else None
        )

        if common_name_fr:
            enriched += 1

        # Recréation du texte de recherche avec accents
        # normalisés par Python.
        search_parts = [
            row["name"],
            row["messier"],
            row["ngc"],
            row["ic"],
            row["common_names"],
            row["identifiers"],
            row["constellation_code"],
            row["constellation_fr"],
            common_name_fr,
            aliases_fr,
        ]

        search_text = " ".join(
            str(value).strip()
            for value in search_parts
            if value
        ).lower()

        connection.execute(
            """
            UPDATE objects
            SET
                common_name_fr = ?,
                aliases_fr = ?,
                search_text = ?
            WHERE id = ?
            """,
            (
                common_name_fr,
                aliases_fr,
                search_text,
                row["id"],
            ),
        )

    connection.commit()

    print()
    print("=== CATALOGUE FR ===")
    print(f"Objets enrichis : {enriched}")

    print()
    print("=== TESTS ===")

    for query in (
        "Tourbillon",
        "Trognon de pomme",
        "Pléiades",
        "Sombrero",
        "Dentelle",
    ):
        results = connection.execute(
            """
            SELECT
                name,
                messier,
                common_name_fr,
                aliases_fr,
                object_type,
                constellation_fr
            FROM objects
            WHERE search_text LIKE ?
            LIMIT 5
            """,
            (
                f"%{query.lower()}%",
            ),
        ).fetchall()

        print()
        print(query)

        for result in results:
            print(dict(result))

    connection.close()


if __name__ == "__main__":
    main()
