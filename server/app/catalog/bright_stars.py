from __future__ import annotations


BRIGHT_STAR_SOURCE = "StellarPilot Bright Stars"
BRIGHT_STAR_SOURCE_VERSION = "2026.09"


# Bright named stars used by the sky catalogue and alignment assistant.
# Coordinates are J2000 and magnitudes are approximate visual magnitudes.
BRIGHT_STARS = [
    {"id": "sirius", "name": "Sirius", "constellation": "Canis Major", "constellation_code": "CMa", "magnitude": -1.46, "ra_hours": 6.7525, "dec_deg": -16.7161, "aliases": (), "identifiers": ("Alpha CMa",)},
    {"id": "canopus", "name": "Canopus", "constellation": "Carina", "constellation_code": "Car", "magnitude": -0.74, "ra_hours": 6.3992, "dec_deg": -52.6957, "aliases": (), "identifiers": ("Alpha Car",)},
    {"id": "arcturus", "name": "Arcturus", "constellation": "Boötes", "constellation_code": "Boo", "magnitude": -0.05, "ra_hours": 14.2610, "dec_deg": 19.1824, "aliases": (), "identifiers": ("Alpha Boo",)},
    {"id": "vega", "name": "Vega", "constellation": "Lyra", "constellation_code": "Lyr", "magnitude": 0.03, "ra_hours": 18.6156, "dec_deg": 38.7837, "aliases": ("Véga",), "identifiers": ("Alpha Lyr",)},
    {"id": "capella", "name": "Capella", "constellation": "Auriga", "constellation_code": "Aur", "magnitude": 0.08, "ra_hours": 5.2782, "dec_deg": 45.9980, "aliases": (), "identifiers": ("Alpha Aur",)},
    {"id": "rigel", "name": "Rigel", "constellation": "Orion", "constellation_code": "Ori", "magnitude": 0.13, "ra_hours": 5.2423, "dec_deg": -8.2016, "aliases": (), "identifiers": ("Beta Ori",)},
    {"id": "procyon", "name": "Procyon", "constellation": "Canis Minor", "constellation_code": "CMi", "magnitude": 0.34, "ra_hours": 7.6550, "dec_deg": 5.2250, "aliases": (), "identifiers": ("Alpha CMi",)},
    {"id": "achernar", "name": "Achernar", "constellation": "Eridanus", "constellation_code": "Eri", "magnitude": 0.46, "ra_hours": 1.6286, "dec_deg": -57.2368, "aliases": (), "identifiers": ("Alpha Eri",)},
    {"id": "betelgeuse", "name": "Betelgeuse", "constellation": "Orion", "constellation_code": "Ori", "magnitude": 0.50, "ra_hours": 5.9195, "dec_deg": 7.4071, "aliases": ("Bételgeuse",), "identifiers": ("Alpha Ori",)},
    {"id": "hadar", "name": "Hadar", "constellation": "Centaurus", "constellation_code": "Cen", "magnitude": 0.61, "ra_hours": 14.0637, "dec_deg": -60.3730, "aliases": ("Agena",), "identifiers": ("Beta Cen",)},
    {"id": "acrux", "name": "Acrux", "constellation": "Crux", "constellation_code": "Cru", "magnitude": 0.76, "ra_hours": 12.4433, "dec_deg": -63.0991, "aliases": (), "identifiers": ("Alpha Cru",)},
    {"id": "altair", "name": "Altair", "constellation": "Aquila", "constellation_code": "Aql", "magnitude": 0.77, "ra_hours": 19.8464, "dec_deg": 8.8683, "aliases": ("Altaïr",), "identifiers": ("Alpha Aql",)},
    {"id": "aldebaran", "name": "Aldebaran", "constellation": "Taurus", "constellation_code": "Tau", "magnitude": 0.85, "ra_hours": 4.5987, "dec_deg": 16.5093, "aliases": ("Aldébaran",), "identifiers": ("Alpha Tau",)},
    {"id": "antares", "name": "Antares", "constellation": "Scorpius", "constellation_code": "Sco", "magnitude": 0.96, "ra_hours": 16.4901, "dec_deg": -26.4320, "aliases": ("Antarès",), "identifiers": ("Alpha Sco",)},
    {"id": "spica", "name": "Spica", "constellation": "Virgo", "constellation_code": "Vir", "magnitude": 0.98, "ra_hours": 13.4199, "dec_deg": -11.1613, "aliases": ("Épi",), "identifiers": ("Alpha Vir",)},
    {"id": "pollux", "name": "Pollux", "constellation": "Gemini", "constellation_code": "Gem", "magnitude": 1.14, "ra_hours": 7.7553, "dec_deg": 28.0262, "aliases": (), "identifiers": ("Beta Gem",)},
    {"id": "fomalhaut", "name": "Fomalhaut", "constellation": "Piscis Austrinus", "constellation_code": "PsA", "magnitude": 1.16, "ra_hours": 22.9608, "dec_deg": -29.6222, "aliases": (), "identifiers": ("Alpha PsA",)},
    {"id": "deneb", "name": "Deneb", "constellation": "Cygnus", "constellation_code": "Cyg", "magnitude": 1.25, "ra_hours": 20.6905, "dec_deg": 45.2803, "aliases": ("Deneb Cygni",), "identifiers": ("Alpha Cyg",)},
    {"id": "regulus", "name": "Regulus", "constellation": "Leo", "constellation_code": "Leo", "magnitude": 1.35, "ra_hours": 10.1395, "dec_deg": 11.9672, "aliases": ("Régulus",), "identifiers": ("Alpha Leo",)},
    {"id": "castor", "name": "Castor", "constellation": "Gemini", "constellation_code": "Gem", "magnitude": 1.58, "ra_hours": 7.5767, "dec_deg": 31.8883, "aliases": (), "identifiers": ("Alpha Gem",)},
    {"id": "bellatrix", "name": "Bellatrix", "constellation": "Orion", "constellation_code": "Ori", "magnitude": 1.64, "ra_hours": 5.4189, "dec_deg": 6.3497, "aliases": (), "identifiers": ("Gamma Ori",)},
    {"id": "elnath", "name": "Elnath", "constellation": "Taurus", "constellation_code": "Tau", "magnitude": 1.65, "ra_hours": 5.4382, "dec_deg": 28.6075, "aliases": ("Nath",), "identifiers": ("Beta Tau",)},
    {"id": "alnilam", "name": "Alnilam", "constellation": "Orion", "constellation_code": "Ori", "magnitude": 1.69, "ra_hours": 5.6036, "dec_deg": -1.2019, "aliases": (), "identifiers": ("Epsilon Ori",)},
    {"id": "alnair", "name": "Alnair", "constellation": "Grus", "constellation_code": "Gru", "magnitude": 1.74, "ra_hours": 22.1372, "dec_deg": -46.9610, "aliases": (), "identifiers": ("Alpha Gru",)},
    {"id": "alioth", "name": "Alioth", "constellation": "Ursa Major", "constellation_code": "UMa", "magnitude": 1.76, "ra_hours": 12.9005, "dec_deg": 55.9598, "aliases": (), "identifiers": ("Epsilon UMa",)},
    {"id": "mirfak", "name": "Mirfak", "constellation": "Perseus", "constellation_code": "Per", "magnitude": 1.79, "ra_hours": 3.4054, "dec_deg": 49.8612, "aliases": ("Algenib de Persée",), "identifiers": ("Alpha Per",)},
    {"id": "dubhe", "name": "Dubhe", "constellation": "Ursa Major", "constellation_code": "UMa", "magnitude": 1.79, "ra_hours": 11.0621, "dec_deg": 61.7508, "aliases": (), "identifiers": ("Alpha UMa",)},
    {"id": "alkaid", "name": "Alkaid", "constellation": "Ursa Major", "constellation_code": "UMa", "magnitude": 1.86, "ra_hours": 13.7923, "dec_deg": 49.3133, "aliases": ("Benetnasch",), "identifiers": ("Eta UMa",)},
    {"id": "sargas", "name": "Sargas", "constellation": "Scorpius", "constellation_code": "Sco", "magnitude": 1.86, "ra_hours": 17.6219, "dec_deg": -42.9978, "aliases": (), "identifiers": ("Theta Sco",)},
    {"id": "polaris", "name": "Polaris", "constellation": "Ursa Minor", "constellation_code": "UMi", "magnitude": 1.98, "ra_hours": 2.5303, "dec_deg": 89.2641, "aliases": ("Étoile polaire", "Polaris A"), "identifiers": ("Alpha UMi",)},
    {"id": "kochab", "name": "Kochab", "constellation": "Ursa Minor", "constellation_code": "UMi", "magnitude": 2.08, "ra_hours": 14.8451, "dec_deg": 74.1555, "aliases": (), "identifiers": ("Beta UMi",)},
]
