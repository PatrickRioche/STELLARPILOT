OPENNGC_TYPES = {
    "*": (
        "star",
        "Étoile",
    ),
    "**": (
        "star_double",
        "Étoile double",
    ),
    "*Ass": (
        "cluster_open",
        "Association stellaire",
    ),
    "OCl": (
        "cluster_open",
        "Amas ouvert",
    ),
    "GCl": (
        "cluster_globular",
        "Amas globulaire",
    ),
    "Cl+N": (
        "nebula_diffuse",
        "Amas et nébuleuse",
    ),
    "G": (
        "galaxy",
        "Galaxie",
    ),
    "GPair": (
        "galaxy_pair",
        "Paire de galaxies",
    ),
    "GTrpl": (
        "galaxy_group",
        "Triplet de galaxies",
    ),
    "GGroup": (
        "galaxy_group",
        "Groupe de galaxies",
    ),
    "PN": (
        "nebula_planetary",
        "Nébuleuse planétaire",
    ),
    "HII": (
        "nebula_diffuse",
        "Région H II",
    ),
    "DrkN": (
        "nebula_dark",
        "Nébuleuse obscure",
    ),
    "EmN": (
        "nebula_diffuse",
        "Nébuleuse en émission",
    ),
    "Neb": (
        "nebula_diffuse",
        "Nébuleuse",
    ),
    "RfN": (
        "nebula_diffuse",
        "Nébuleuse par réflexion",
    ),
    "SNR": (
        "supernova_remnant",
        "Rémanent de supernova",
    ),
    "Nova": (
        "star",
        "Nova",
    ),
    "Other": (
        "unknown",
        "Autre objet",
    ),
}


def normalize_object_type(
    source_type: str | None,
) -> tuple[str, str]:

    value = (source_type or "").strip()

    return OPENNGC_TYPES.get(
        value,
        (
            "unknown",
            value or "Objet céleste",
        ),
    )


STELLARPILOT_TYPE_LABELS_FR = {
    "star": "Étoiles",
    "star_double": "Étoiles doubles",
    "cluster_open": "Amas ouverts",
    "cluster_globular": "Amas globulaires",
    "nebula_diffuse": "Nébuleuses diffuses",
    "nebula_planetary": "Nébuleuses planétaires",
    "nebula_dark": "Nébuleuses obscures",
    "supernova_remnant": "Rémanents de supernova",
    "galaxy": "Galaxies",
    "galaxy_pair": "Paires de galaxies",
    "galaxy_group": "Groupes de galaxies",
    "planet": "Planètes",
    "moon": "Lunes",
    "comet": "Comètes",
    "asterism": "Astérismes",
    "unknown": "Autres objets",
}


def object_type_label_fr(
    object_type: str | None,
) -> str:
    value = (object_type or "unknown").strip()

    return STELLARPILOT_TYPE_LABELS_FR.get(
        value,
        value or "Autres objets",
    )
