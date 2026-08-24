package fr.stellarpilot.app.feature.sky

import fr.stellarpilot.app.R
import java.util.Locale

internal fun targetObjectIconResource(
    objectType: String
): Int =
    when (
        objectType.lowercase(Locale.ROOT)
    ) {
        "star" ->
            R.drawable.ic_object_star

        "star_double" ->
            R.drawable.ic_object_star_double

        "star_triple" ->
            R.drawable.ic_object_star_triple

        "star_multiple" ->
            R.drawable.ic_object_star_multiple

        "cluster_open" ->
            R.drawable.ic_object_cluster_open

        "cluster_globular" ->
            R.drawable.ic_object_cluster_globular

        "nebula_diffuse" ->
            R.drawable.ic_object_nebula_diffuse

        "nebula_planetary" ->
            R.drawable.ic_object_nebula_planetary

        "nebula_dark" ->
            R.drawable.ic_object_nebula_dark

        "supernova_remnant" ->
            R.drawable.ic_object_supernova_remnant

        "galaxy" ->
            R.drawable.ic_object_galaxy

        "galaxy_pair" ->
            R.drawable.ic_object_galaxy_pair

        "galaxy_group" ->
            R.drawable.ic_object_galaxy_group

        "planet" ->
            R.drawable.ic_object_planet

        "moon" ->
            R.drawable.ic_object_moon

        "comet" ->
            R.drawable.ic_object_comet

        "asterism" ->
            R.drawable.ic_object_asterism

        else ->
            R.drawable.ic_object_asterism
    }

internal fun targetCategoryIconResource(
    category: String
): Int =
    when (category) {
        "star" ->
            R.drawable.ic_object_star
        "galaxy" ->
            R.drawable.ic_object_galaxy
        "nebula" ->
            R.drawable.ic_object_nebula_diffuse
        "cluster" ->
            R.drawable.ic_object_cluster_open
        else ->
            R.drawable.ic_object_asterism
    }

internal val targetConstellationsFr =
    listOf(
        "Aigle",
        "Andromède",
        "Autel",
        "Balance",
        "Baleine",
        "Bélier",
        "Boussole",
        "Bouvier",
        "Burin",
        "Caméléon",
        "Cancer",
        "Capricorne",
        "Carène",
        "Cassiopée",
        "Centaure",
        "Céphée",
        "Chevelure de Bérénice",
        "Chiens de chasse",
        "Cocher",
        "Colombe",
        "Compas",
        "Corbeau",
        "Coupe",
        "Couronne australe",
        "Couronne boréale",
        "Croix du Sud",
        "Cygne",
        "Dauphin",
        "Dorade",
        "Dragon",
        "Écu de Sobieski",
        "Éridan",
        "Flèche",
        "Fourneau",
        "Gémeaux",
        "Girafe",
        "Grand Chien",
        "Grande Ourse",
        "Grue",
        "Hercule",
        "Horloge",
        "Hydre",
        "Hydre mâle",
        "Indien",
        "Lézard",
        "Licorne",
        "Lièvre",
        "Lion",
        "Loup",
        "Lynx",
        "Lyre",
        "Machine pneumatique",
        "Microscope",
        "Mouche",
        "Octant",
        "Oiseau de paradis",
        "Ophiuchus",
        "Orion",
        "Paon",
        "Pégase",
        "Peintre",
        "Persée",
        "Petit Cheval",
        "Petit Chien",
        "Petit Lion",
        "Petit Renard",
        "Petite Ourse",
        "Phénix",
        "Poisson austral",
        "Poisson volant",
        "Poissons",
        "Poupe",
        "Règle",
        "Réticule",
        "Sagittaire",
        "Scorpion",
        "Sculpteur",
        "Serpent",
        "Serpent (Queue)",
        "Serpent (Tête)",
        "Sextant",
        "Table",
        "Taureau",
        "Télescope",
        "Toucan",
        "Triangle",
        "Triangle austral",
        "Verseau",
        "Vierge",
        "Voiles",
    )
