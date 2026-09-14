from __future__ import annotations

import csv
import json
import math
import re
import unicodedata
from collections import defaultdict
from pathlib import Path
from typing import Any, Iterable


ROOT = Path(__file__).resolve().parents[2]
SOURCE_DIR = ROOT / "catalog_sources"
MANIFEST = SOURCE_DIR / "SOURCE_MANIFEST.json"

STELLAR_SOURCE = "StellarPilot Stellar Catalog"
DEFAULT_STELLAR_SOURCE_VERSION = "2026.09-stellar-v1"
MAX_VISUAL_MAGNITUDE = 6.0

REQUIRED_SOURCE_FILES = (
    "skymap_stars.csv",
    "bsc5_extract.txt",
    "iau_proper_stars.csv",
    "iau_stars_with_data.csv",
)

GREEK_LONG = {
    "Alp": "Alpha",
    "Bet": "Beta",
    "Gam": "Gamma",
    "Del": "Delta",
    "Eps": "Epsilon",
    "Zet": "Zeta",
    "Eta": "Eta",
    "The": "Theta",
    "Iot": "Iota",
    "Kap": "Kappa",
    "Lam": "Lambda",
    "Mu ": "Mu",
    "Nu ": "Nu",
    "Xi ": "Xi",
    "Omi": "Omicron",
    "Pi ": "Pi",
    "Rho": "Rho",
    "Sig": "Sigma",
    "Tau": "Tau",
    "Ups": "Upsilon",
    "Phi": "Phi",
    "Chi": "Chi",
    "Psi": "Psi",
    "Ome": "Omega",
}

GREEK_SYMBOL = {
    "Alpha": "α",
    "Beta": "β",
    "Gamma": "γ",
    "Delta": "δ",
    "Epsilon": "ε",
    "Zeta": "ζ",
    "Eta": "η",
    "Theta": "θ",
    "Iota": "ι",
    "Kappa": "κ",
    "Lambda": "λ",
    "Mu": "μ",
    "Nu": "ν",
    "Xi": "ξ",
    "Omicron": "ο",
    "Pi": "π",
    "Rho": "ρ",
    "Sigma": "σ",
    "Tau": "τ",
    "Upsilon": "υ",
    "Phi": "φ",
    "Chi": "χ",
    "Psi": "ψ",
    "Omega": "ω",
}


def fold_text(value: str | None) -> str:
    if not value:
        return ""

    normalized = unicodedata.normalize("NFKD", value)
    return "".join(
        char
        for char in normalized
        if not unicodedata.combining(char)
    ).casefold().strip()


def _clean(value: str | None) -> str | None:
    if value is None:
        return None
    value = value.strip()
    return value or None


def _float(value: str | None) -> float | None:
    value = _clean(value)
    if value is None:
        return None
    try:
        return float(value)
    except ValueError:
        return None


def _manifest() -> dict[str, Any]:
    if not MANIFEST.exists():
        return {}
    try:
        return json.loads(MANIFEST.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}


def stellar_source_version() -> str:
    return str(
        _manifest().get(
            "catalog_version",
            DEFAULT_STELLAR_SOURCE_VERSION,
        )
    )


def sources_available() -> bool:
    return all(
        (SOURCE_DIR / filename).is_file()
        for filename in REQUIRED_SOURCE_FILES
    )


def missing_sources() -> list[str]:
    return [
        filename
        for filename in REQUIRED_SOURCE_FILES
        if not (SOURCE_DIR / filename).is_file()
    ]


def _angular_distance_deg(
    ra1_deg: float,
    dec1_deg: float,
    ra2_deg: float,
    dec2_deg: float,
) -> float:
    ra1 = math.radians(ra1_deg)
    dec1 = math.radians(dec1_deg)
    ra2 = math.radians(ra2_deg)
    dec2 = math.radians(dec2_deg)

    cosine = (
        math.sin(dec1) * math.sin(dec2)
        + math.cos(dec1)
        * math.cos(dec2)
        * math.cos(ra1 - ra2)
    )
    cosine = max(-1.0, min(1.0, cosine))
    return math.degrees(math.acos(cosine))


def _bsc_position(raw: str) -> tuple[float, float] | None:
    match = re.fullmatch(
        r"(\d{2})(\d{2})(\d{2}\.\d)([+-])(\d{2})(\d{2})(\d{2})",
        raw.strip(),
    )
    if not match:
        return None

    hour = int(match.group(1))
    minute = int(match.group(2))
    second = float(match.group(3))
    ra_hours = hour + minute / 60.0 + second / 3600.0

    sign = -1.0 if match.group(4) == "-" else 1.0
    degree = int(match.group(5))
    arcminute = int(match.group(6))
    arcsecond = int(match.group(7))
    dec_deg = sign * (
        degree + arcminute / 60.0 + arcsecond / 3600.0
    )

    return ra_hours, dec_deg


def _bayer_names(
    bayer_abbrev: str | None,
    superscript: str | None,
    constellation: str | None,
) -> tuple[str | None, tuple[str, ...]]:
    abbreviation = (bayer_abbrev or "").ljust(3)[:3]
    constellation = _clean(constellation)

    long_name = GREEK_LONG.get(abbreviation)
    if long_name is None or constellation is None:
        return None, ()

    suffix = (superscript or "").strip()
    compact_suffix = suffix if suffix else ""
    readable_suffix = suffix if suffix else ""

    long_designation = (
        f"{long_name}{readable_suffix} {constellation}"
    )
    symbol = GREEK_SYMBOL.get(long_name)

    aliases = [long_designation]
    if symbol:
        aliases.append(
            f"{symbol}{compact_suffix} {constellation}"
        )

    return long_designation, tuple(aliases)


def _parse_bsc() -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    path = SOURCE_DIR / "bsc5_extract.txt"

    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            if len(line.rstrip("\n")) < 40:
                continue

            line = line.rstrip("\n")
            position = _bsc_position(line[20:35])
            if position is None:
                continue

            magnitude = _float(line[35:40])
            if magnitude is None:
                continue

            hr = _clean(line[0:4])
            flamsteed = _clean(line[4:7])
            bayer_abbrev = line[7:10]
            superscript = line[10:11]
            constellation = _clean(line[11:14])
            hd = _clean(line[14:20])

            bayer, bayer_aliases = _bayer_names(
                bayer_abbrev,
                superscript,
                constellation,
            )

            aliases = set(bayer_aliases)
            flamsteed_designation = None
            if flamsteed and constellation:
                flamsteed_designation = (
                    f"{flamsteed} {constellation}"
                )
                aliases.add(flamsteed_designation)

            identifiers: set[str] = set()
            if hr:
                identifiers.add(f"HR {int(hr)}")
            if hd:
                identifiers.add(f"HD {int(hd)}")

            records.append(
                {
                    "ra_hours": position[0],
                    "dec_deg": position[1],
                    "magnitude": magnitude,
                    "constellation_code": constellation,
                    "hr": str(int(hr)) if hr else None,
                    "hd": str(int(hd)) if hd else None,
                    "bayer": bayer,
                    "flamsteed": flamsteed_designation,
                    "aliases": aliases,
                    "identifiers": identifiers,
                }
            )

    return records


def _spatial_index(
    records: Iterable[dict[str, Any]],
    bucket_size: float = 1.0,
) -> dict[tuple[int, int], list[dict[str, Any]]]:
    index: dict[
        tuple[int, int],
        list[dict[str, Any]],
    ] = defaultdict(list)

    ra_bucket_count = round(360.0 / bucket_size)

    for record in records:
        ra_deg = (record["ra_hours"] * 15.0) % 360.0
        dec_deg = record["dec_deg"]
        key = (
            int(ra_deg // bucket_size) % ra_bucket_count,
            int((dec_deg + 90.0) // bucket_size),
        )
        index[key].append(record)

    return index


def _nearest(
    index: dict[tuple[int, int], list[dict[str, Any]]],
    ra_hours: float,
    dec_deg: float,
    max_distance_deg: float = 0.18,
) -> dict[str, Any] | None:
    bucket_size = 1.0
    ra_deg = (ra_hours * 15.0) % 360.0
    ra_bucket_count = 360
    center_ra = int(ra_deg // bucket_size) % ra_bucket_count
    center_dec = int((dec_deg + 90.0) // bucket_size)

    best: dict[str, Any] | None = None
    best_distance = max_distance_deg

    for ra_delta in (-1, 0, 1):
        for dec_delta in (-1, 0, 1):
            key = (
                (center_ra + ra_delta) % ra_bucket_count,
                center_dec + dec_delta,
            )
            for candidate in index.get(key, []):
                distance = _angular_distance_deg(
                    ra_deg,
                    dec_deg,
                    candidate["ra_hours"] * 15.0,
                    candidate["dec_deg"],
                )
                if distance < best_distance:
                    best_distance = distance
                    best = candidate

    return best


def _compact_ra(value: str | None) -> float | None:
    value = _clean(value)
    if not value:
        return None

    parts = value.split(".", 2)
    if len(parts) != 3:
        return None

    try:
        hour = int(parts[0])
        minute = int(parts[1])
        raw_second = parts[2]

        if len(raw_second) > 2:
            second = float(
                raw_second[:2]
                + "."
                + raw_second[2:]
            )
        else:
            second = float(raw_second)
    except ValueError:
        return None

    return hour + minute / 60.0 + second / 3600.0


def _compact_dec(value: str | None) -> float | None:
    value = _clean(value)
    if not value:
        return None

    sign = -1.0 if value.startswith("-") else 1.0
    raw = value.lstrip("+-")
    parts = raw.split(".", 1)
    if len(parts) != 2:
        return None

    try:
        degree = int(parts[0])
        tail = parts[1]
        if len(tail) < 2:
            return None
        minute = int(tail[:2])
        raw_second = tail[2:]
        if raw_second:
            if len(raw_second) > 2:
                second = float(
                    raw_second[:2]
                    + "."
                    + raw_second[2:]
                )
            else:
                second = float(raw_second)
        else:
            second = 0.0
    except ValueError:
        return None

    return sign * (
        degree + minute / 60.0 + second / 3600.0
    )


def _identifier_fields(
    values: Iterable[str],
) -> dict[str, str | None]:
    result: dict[str, str | None] = {
        "hip": None,
        "hr": None,
        "hd": None,
    }

    for value in values:
        for kind in ("HIP", "HR", "HD"):
            if result[kind.lower()] is not None:
                continue
            match = re.search(
                rf"\b{kind}\s*[- ]?(\d+)\b",
                value,
                flags=re.IGNORECASE,
            )
            if match:
                result[kind.lower()] = str(
                    int(match.group(1))
                )

    return result


def _parse_iau_proper() -> dict[str, dict[str, Any]]:
    path = SOURCE_DIR / "iau_proper_stars.csv"
    by_name: dict[str, dict[str, Any]] = {}

    with path.open(
        "r",
        encoding="utf-8-sig",
        newline="",
    ) as handle:
        for row in csv.DictReader(handle):
            proper_name = _clean(row.get("Proper Names"))
            if proper_name is None:
                continue

            by_name[fold_text(proper_name)] = {
                "proper_name": proper_name,
                "designation": _clean(row.get("Designation")),
                "hip": _clean(row.get("HIP")),
                "bayer": _clean(row.get("Bayer ID")),
                "constellation_code": _clean(
                    row.get("Constellation")
                ),
                "date_of_adoption": _clean(
                    row.get("Date of Adoption")
                ),
            }

    return by_name


def _parse_iau_data() -> list[dict[str, Any]]:
    path = SOURCE_DIR / "iau_stars_with_data.csv"
    records: list[dict[str, Any]] = []

    with path.open(
        "r",
        encoding="utf-8-sig",
        newline="",
    ) as handle:
        for row in csv.DictReader(handle):
            name = _clean(row.get("Common Name"))
            ra_hours = _compact_ra(
                row.get("Right Ascension (HH.MM.SS)")
            )
            dec_deg = _compact_dec(
                row.get("Declination (DD.SS)")
            )

            if name is None or ra_hours is None or dec_deg is None:
                continue

            aliases = {
                alias.strip()
                for alias in (
                    row.get("Alternative Names") or ""
                ).split(",")
                if alias.strip()
            }

            identifiers = _identifier_fields(aliases)

            records.append(
                {
                    "name": name,
                    "ra_hours": ra_hours,
                    "dec_deg": dec_deg,
                    "magnitude": _float(
                        row.get("Magnitude (V, Visual)")
                    ),
                    "aliases": aliases,
                    **identifiers,
                }
            )

    return records


def _load_skymap_stars() -> list[dict[str, Any]]:
    path = SOURCE_DIR / "skymap_stars.csv"
    stars: list[dict[str, Any]] = []

    with path.open(
        "r",
        encoding="utf-8-sig",
        newline="",
    ) as handle:
        for row in csv.DictReader(handle):
            magnitude = _float(row.get("magnitude"))
            ra_deg = _float(row.get("ra_deg"))
            dec_deg = _float(row.get("dec_deg"))

            if (
                magnitude is None
                or magnitude > MAX_VISUAL_MAGNITUDE
                or ra_deg is None
                or dec_deg is None
            ):
                continue

            names = [
                name.strip()
                for name in (row.get("names") or "").split("|")
                if name.strip()
            ]

            stars.append(
                {
                    "stellar_id": _clean(row.get("id")),
                    "ra_hours": ra_deg / 15.0,
                    "dec_deg": dec_deg,
                    "magnitude": magnitude,
                    "names": names,
                    "aliases": set(names[1:]),
                    "identifiers": set(),
                    "iau_name": None,
                    "hip": None,
                    "hr": None,
                    "hd": None,
                    "bayer": None,
                    "flamsteed": None,
                    "constellation_code": None,
                }
            )

    return stars


def _set_constellations(stars: list[dict[str, Any]]) -> None:
    missing = [
        star
        for star in stars
        if not star.get("constellation_code")
    ]
    if not missing:
        return

    try:
        import astropy.units as u
        from astropy.coordinates import SkyCoord, get_constellation
    except ImportError:
        return

    coordinates = SkyCoord(
        ra=[star["ra_hours"] * 15.0 for star in missing] * u.deg,
        dec=[star["dec_deg"] for star in missing] * u.deg,
        frame="icrs",
    )

    codes = get_constellation(
        coordinates,
        short_name=True,
    )

    for star, code in zip(missing, codes):
        star["constellation_code"] = str(code)


def _display_name(star: dict[str, Any]) -> str:
    if star.get("iau_name"):
        return str(star["iau_name"])

    names = star.get("names") or []
    if names:
        return str(names[0])

    for key in (
        "bayer",
        "flamsteed",
    ):
        if star.get(key):
            return str(star[key])

    if star.get("hr"):
        return f"HR {star['hr']}"
    if star.get("hd"):
        return f"HD {star['hd']}"

    stellar_id = str(
        star.get("stellar_id") or "star/unknown"
    )
    return stellar_id.removeprefix("star/")


def build_stellar_catalog() -> tuple[list[dict[str, Any]], dict[str, Any]]:
    if not sources_available():
        raise FileNotFoundError(
            "Missing stellar sources: "
            + ", ".join(missing_sources())
        )

    stars = _load_skymap_stars()
    bsc_records = _parse_bsc()
    bsc_index = _spatial_index(bsc_records)

    for star in stars:
        bsc = _nearest(
            bsc_index,
            star["ra_hours"],
            star["dec_deg"],
        )
        if bsc is None:
            continue

        for key in (
            "hr",
            "hd",
            "bayer",
            "flamsteed",
            "constellation_code",
        ):
            if bsc.get(key):
                star[key] = bsc[key]

        star["aliases"].update(bsc["aliases"])
        star["identifiers"].update(bsc["identifiers"])

    iau_proper = _parse_iau_proper()
    iau_data = _parse_iau_data()

    name_index: dict[str, dict[str, Any]] = {}
    for star in stars:
        for name in star.get("names") or []:
            name_index.setdefault(fold_text(name), star)
        for alias in star["aliases"]:
            name_index.setdefault(fold_text(alias), star)

    star_index = _spatial_index(stars)
    resolved_iau_names: set[str] = set()
    added_named_faint = 0

    for iau in iau_data:
        proper = iau_proper.get(fold_text(iau["name"]))
        official_name = (
            proper["proper_name"]
            if proper
            else iau["name"]
        )

        target = name_index.get(
            fold_text(official_name)
        )
        if target is None:
            target = _nearest(
                star_index,
                iau["ra_hours"],
                iau["dec_deg"],
                max_distance_deg=0.12,
            )

        if target is None:
            target = {
                "stellar_id": (
                    "iau/" + re.sub(
                        r"[^a-z0-9]+",
                        "-",
                        fold_text(official_name),
                    ).strip("-")
                ),
                "ra_hours": iau["ra_hours"],
                "dec_deg": iau["dec_deg"],
                "magnitude": iau.get("magnitude"),
                "names": [official_name],
                "aliases": set(),
                "identifiers": set(),
                "iau_name": official_name,
                "hip": None,
                "hr": None,
                "hd": None,
                "bayer": None,
                "flamsteed": None,
                "constellation_code": None,
            }
            stars.append(target)
            name_index[fold_text(official_name)] = target
            added_named_faint += 1

        target["iau_name"] = official_name
        target["aliases"].update(iau["aliases"])
        target["aliases"].add(iau["name"])

        for kind in ("hip", "hr", "hd"):
            value = iau.get(kind)
            if value:
                target[kind] = value
                target["identifiers"].add(
                    f"{kind.upper()} {value}"
                )

        if proper:
            if proper.get("hip"):
                hip = re.sub(r"\D", "", proper["hip"])
                if hip:
                    target["hip"] = str(int(hip))
                    target["identifiers"].add(
                        f"HIP {int(hip)}"
                    )

            if proper.get("bayer"):
                target["bayer"] = proper["bayer"]
                target["aliases"].add(proper["bayer"])

            if proper.get("designation"):
                target["aliases"].add(
                    proper["designation"]
                )

            if proper.get("constellation_code"):
                target["constellation_code"] = (
                    proper["constellation_code"]
                )

        resolved_iau_names.add(fold_text(official_name))

    _set_constellations(stars)

    output: list[dict[str, Any]] = []

    for star in stars:
        name = _display_name(star)
        aliases = {
            alias.strip()
            for alias in star["aliases"]
            if alias and alias.strip() and alias.strip() != name
        }

        identifiers = {
            identifier.strip()
            for identifier in star["identifiers"]
            if identifier and identifier.strip()
        }

        for kind in ("hip", "hr", "hd"):
            if star.get(kind):
                identifiers.add(
                    f"{kind.upper()} {star[kind]}"
                )

        search_values = [
            name,
            star.get("stellar_id"),
            star.get("iau_name"),
            star.get("bayer"),
            star.get("flamsteed"),
            star.get("constellation_code"),
            *sorted(aliases),
            *sorted(identifiers),
        ]

        folded_values = [
            fold_text(str(value))
            for value in search_values
            if value
        ]

        search_text = " ".join(
            dict.fromkeys(
                str(value).strip().casefold()
                for value in search_values
                if value and str(value).strip()
            )
        )
        folded_search = " ".join(
            dict.fromkeys(
                value
                for value in folded_values
                if value
            )
        )

        if folded_search and folded_search not in search_text:
            search_text += " " + folded_search

        output.append(
            {
                "stellar_id": star.get("stellar_id"),
                "name": name,
                "iau_name": star.get("iau_name"),
                "ra_hours": star["ra_hours"],
                "dec_deg": star["dec_deg"],
                "magnitude": star.get("magnitude"),
                "constellation_code": star.get(
                    "constellation_code"
                ),
                "aliases": "; ".join(sorted(aliases)) or None,
                "identifiers": "; ".join(
                    sorted(identifiers)
                ) or None,
                "hip": star.get("hip"),
                "hr": star.get("hr"),
                "hd": star.get("hd"),
                "bayer": star.get("bayer"),
                "flamsteed": star.get("flamsteed"),
                "search_text": search_text.strip(),
            }
        )

    output.sort(
        key=lambda star: (
            star["magnitude"] is None,
            star["magnitude"]
            if star["magnitude"] is not None
            else 99.0,
            fold_text(star["name"]),
        )
    )

    stats = {
        "source_version": stellar_source_version(),
        "bright_star_count": sum(
            1
            for star in output
            if star["magnitude"] is not None
            and star["magnitude"] <= MAX_VISUAL_MAGNITUDE
        ),
        "object_count": len(output),
        "iau_named_count": sum(
            1 for star in output if star["iau_name"]
        ),
        "iau_inventory_count": len(iau_proper),
        "iau_unresolved_count": max(
            0,
            len(iau_proper) - len(resolved_iau_names),
        ),
        "named_faint_added_count": added_named_faint,
        "max_visual_magnitude": MAX_VISUAL_MAGNITUDE,
    }

    return output, stats
