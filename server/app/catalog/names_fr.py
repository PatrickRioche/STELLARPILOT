FRENCH_OBJECT_NAMES = {
    # ------------------------------------------------------------
    # Nébuleuses / rémanents
    # ------------------------------------------------------------
    "M1": (
        "Nébuleuse du Crabe",
        (),
    ),
    "M8": (
        "Nébuleuse de la Lagune",
        (),
    ),
    "M16": (
        "Nébuleuse de l'Aigle",
        (),
    ),
    "M17": (
        "Nébuleuse Oméga",
        (
            "Nébuleuse du Cygne",
        ),
    ),
    "M20": (
        "Nébuleuse Trifide",
        (),
    ),
    "M27": (
        "Nébuleuse de l'Haltère",
        (
            "Nébuleuse du Trognon de pomme",
            "Trognon de pomme",
        ),
    ),
    "M42": (
        "Nébuleuse d'Orion",
        (
            "Grande nébuleuse d'Orion",
        ),
    ),
    "M43": (
        "Nébuleuse de Mairan",
        (),
    ),
    "M57": (
        "Nébuleuse de la Lyre",
        (
            "Nébuleuse de l'Anneau",
        ),
    ),
    "M76": (
        "Petite Nébuleuse de l'Haltère",
        (
            "Petite Haltère",
        ),
    ),
    "M97": (
        "Nébuleuse du Hibou",
        (),
    ),

    # ------------------------------------------------------------
    # Galaxies
    # ------------------------------------------------------------
    "M31": (
        "Galaxie d'Andromède",
        (
            "Grande galaxie d'Andromède",
        ),
    ),
    "M33": (
        "Galaxie du Triangle",
        (),
    ),
    "M51": (
        "Galaxie du Tourbillon",
        (
            "Tourbillon",
        ),
    ),
    "M63": (
        "Galaxie du Tournesol",
        (),
    ),
    "M64": (
        "Galaxie de l'Œil noir",
        (),
    ),
    "M65": (
        "Galaxie M65",
        (),
    ),
    "M66": (
        "Galaxie M66",
        (),
    ),
    "M74": (
        "Galaxie Fantôme",
        (),
    ),
    "M77": (
        "Galaxie de la Baleine",
        (),
    ),
    "M81": (
        "Galaxie de Bode",
        (),
    ),
    "M82": (
        "Galaxie du Cigare",
        (),
    ),
    "M83": (
        "Galaxie du Moulinet austral",
        (),
    ),
    "M87": (
        "Galaxie Virgo A",
        (),
    ),
    "M94": (
        "Galaxie de l'Œil de crocodile",
        (),
    ),
    "M101": (
        "Galaxie du Moulinet",
        (),
    ),
    "M104": (
        "Galaxie du Sombrero",
        (),
    ),

    # ------------------------------------------------------------
    # Amas
    # ------------------------------------------------------------
    "M3": (
        "Amas globulaire M3",
        (),
    ),
    "M4": (
        "Amas globulaire M4",
        (),
    ),
    "M6": (
        "Amas du Papillon",
        (),
    ),
    "M7": (
        "Amas de Ptolémée",
        (),
    ),
    "M11": (
        "Amas du Canard sauvage",
        (),
    ),
    "M13": (
        "Grand Amas d'Hercule",
        (
            "Amas d'Hercule",
        ),
    ),
    "M22": (
        "Grand Amas du Sagittaire",
        (),
    ),
    "M35": (
        "Amas M35",
        (),
    ),
    "M36": (
        "Amas du Moulinet",
        (),
    ),
    "M37": (
        "Amas M37",
        (),
    ),
    "M38": (
        "Amas de l'Étoile de mer",
        (),
    ),
    "M44": (
        "Amas de la Crèche",
        (
            "La Ruche",
            "Praesepe",
        ),
    ),
    "M45": (
        "Les Pléiades",
        (
            "Pléiades",
            "Les Sept Sœurs",
        ),
    ),

    # ------------------------------------------------------------
    # Objets NGC célèbres hors Messier
    # ------------------------------------------------------------
    "NGC 1499": (
        "Nébuleuse Californie",
        (
            "Nébuleuse de Californie",
        ),
    ),
    "NGC 2237": (
        "Nébuleuse de la Rosette",
        (
            "Rosette",
        ),
    ),
    "NGC 2392": (
        "Nébuleuse du Clown",
        (),
    ),
    "NGC 6543": (
        "Nébuleuse de l'Œil de Chat",
        (),
    ),
    "NGC 6888": (
        "Nébuleuse du Croissant",
        (),
    ),
    "NGC 6960": (
        "Petite Dentelle du Cygne",
        (
            "Dentelles du Cygne",
        ),
    ),
    "NGC 6992": (
        "Grande Dentelle du Cygne",
        (
            "Dentelles du Cygne",
        ),
    ),
    "NGC 7000": (
        "Nébuleuse de l'Amérique du Nord",
        (
            "Nébuleuse North America",
        ),
    ),
    "NGC 7293": (
        "Nébuleuse de l'Hélice",
        (
            "Hélice",
        ),
    ),
    "NGC 869": (
        "Double Amas de Persée",
        (
            "h de Persée",
        ),
    ),
    "NGC 884": (
        "Double Amas de Persée",
        (
            "χ de Persée",
        ),
    ),
}


def french_object_name(
    *identifiers: str | None,
) -> tuple[str | None, tuple[str, ...]]:

    for identifier in identifiers:
        if not identifier:
            continue

        key = identifier.strip()

        if key in FRENCH_OBJECT_NAMES:
            return FRENCH_OBJECT_NAMES[key]

    return None, ()
