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
