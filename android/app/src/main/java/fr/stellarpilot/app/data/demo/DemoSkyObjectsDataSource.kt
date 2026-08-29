package fr.stellarpilot.app.data.demo

import fr.stellarpilot.app.domain.model.SkyObject
import fr.stellarpilot.app.domain.model.SkyObjectsResult

/**
 * Petit catalogue embarque utilise uniquement en mode Demo.
 *
 * Aucun acces reseau.
 */
object DemoSkyObjectsDataSource {

    private fun item(
        id: Int,
        name: String,
        catalogName: String,
        type: String,
        typeLabel: String,
        constellation: String?,
        ra: Double,
        dec: Double,
        magnitude: Double?,
        altitude: Double,
        azimuth: Double,
        direction: String,
        aliases: String? = null,
        major: Double? = null,
        minor: Double? = null
    ) =
        SkyObject(
            id = id,
            name = name,
            catalogName = catalogName,
            reference = catalogName,
            objectType = type,
            objectTypeLabelFr = typeLabel,
            constellation = constellation,
            raHours = ra,
            decDeg = dec,
            magnitude = magnitude,
            magnitudeBand = "V",
            majorAxisArcmin = major,
            minorAxisArcmin = minor,
            aliasesFr = aliases,
            altitudeDeg = altitude,
            azimuthDeg = azimuth,
            azimuthDirection = direction
        )

    private val catalog =
        listOf(
            item(
                1, "Capella", "Alpha Aur",
                "star", "Etoile", "Cocher",
                5.2782, 45.998, 0.08,
                55.1, 287.3, "W"
            ),
            item(
                2, "Procyon", "Alpha CMi",
                "star", "Etoile", "Petit Chien",
                7.6550, 5.225, 0.34,
                45.6, 203.3, "SW"
            ),
            item(
                3, "Betelgeuse", "Alpha Ori",
                "star", "Etoile", "Orion",
                5.9195, 7.407, 0.50,
                36.3, 235.7, "SW"
            ),
            item(
                4, "Regulus", "Alpha Leo",
                "star", "Etoile", "Lion",
                10.1395, 11.967, 1.35,
                50.4, 146.5, "SE"
            ),
            item(
                5, "Vega", "Alpha Lyr",
                "star", "Etoile", "Lyre",
                18.6156, 38.784, 0.03,
                62.0, 72.0, "NE"
            ),
            item(
                6, "Deneb", "Alpha Cyg",
                "star", "Etoile", "Cygne",
                20.6905, 45.280, 1.25,
                58.0, 54.0, "NE"
            ),
            item(
                7, "Altair", "Alpha Aql",
                "star", "Etoile", "Aigle",
                19.8464, 8.868, 0.77,
                41.0, 93.0, "E"
            ),
            item(
                8, "Polaris", "Alpha UMi",
                "star", "Etoile", "Petite Ourse",
                2.5303, 89.264, 1.98,
                47.4, 0.5, "N"
            ),

            item(
                20, "M31", "M31",
                "galaxy", "Galaxie", "Andromede",
                0.7123, 41.269, 3.44,
                61.0, 48.0, "NE",
                "Galaxie d'Andromede",
                190.0, 60.0
            ),
            item(
                21, "M51", "M51",
                "galaxy", "Galaxie", "Chiens de chasse",
                13.4979, 47.195, 8.40,
                52.0, 315.0, "NW",
                "Galaxie du Tourbillon",
                11.2, 6.9
            ),
            item(
                22, "M81", "M81",
                "galaxy", "Galaxie", "Grande Ourse",
                9.9259, 69.065, 6.94,
                56.0, 338.0, "NW",
                "Galaxie de Bode",
                26.9, 14.1
            ),
            item(
                23, "M82", "M82",
                "galaxy", "Galaxie", "Grande Ourse",
                9.9313, 69.679, 8.41,
                55.0, 340.0, "NW",
                "Galaxie du Cigare",
                11.2, 4.3
            ),

            item(
                30, "M42", "M42",
                "nebula", "Nebuleuse", "Orion",
                5.5881, -5.391, 4.00,
                30.0, 220.0, "SW",
                "Nebuleuse d'Orion",
                85.0, 60.0
            ),
            item(
                31, "M27", "M27",
                "nebula", "Nebuleuse", "Petit Renard",
                19.9934, 22.721, 7.50,
                48.0, 78.0, "NE",
                "Nebuleuse de l'Haltere",
                8.0, 5.7
            ),
            item(
                32, "M57", "M57",
                "nebula", "Nebuleuse", "Lyre",
                18.8931, 33.029, 8.80,
                60.0, 70.0, "NE",
                "Nebuleuse de la Lyre",
                1.4, 1.0
            ),

            item(
                40, "M45", "M45",
                "cluster", "Amas", "Taureau",
                3.7900, 24.117, 1.60,
                44.0, 262.0, "W",
                "Pleiades",
                110.0, 110.0
            ),
            item(
                41, "M13", "M13",
                "cluster", "Amas", "Hercule",
                16.6949, 36.461, 5.80,
                57.0, 64.0, "NE",
                "Grand amas d'Hercule",
                20.0, 20.0
            ),
            item(
                42, "M44", "M44",
                "cluster", "Amas", "Cancer",
                8.6728, 19.667, 3.10,
                38.0, 175.0, "S",
                "Amas de la Creche",
                95.0, 95.0
            ),
            item(
                43, "M103", "M103",
                "cluster", "Amas", "Cassiopee",
                1.5564, 60.650, 7.40,
                64.0, 18.0, "N",
                null,
                6.0, 6.0
            )
        )

    fun getObjects(
        latitude: Double,
        longitude: Double,
        category: String,
        query: String,
        minAltitude: Double,
        direction: String?,
        constellation: String,
        sort: String = "magnitude",
        order: String = "asc",
        offset: Int = 0,
        limit: Int = 30
    ): SkyObjectsResult {

        /*
         * Latitude/longitude font volontairement partie
         * de la meme interface que la source distante.
         * Le snapshot Demo utilise des alt/az fixes.
         */
        @Suppress("UNUSED_VARIABLE")
        val observer = latitude to longitude

        val normalizedQuery =
            query.trim().lowercase()

        val filtered =
            catalog
                .asSequence()
                .filter {
                    category == "all" ||
                        it.objectType.equals(
                            category,
                            ignoreCase = true
                        )
                }
                .filter {
                    it.altitudeDeg >= minAltitude
                }
                .filter {
                    direction.isNullOrBlank() ||
                        it.azimuthDirection.equals(
                            direction,
                            ignoreCase = true
                        )
                }
                .filter {
                    constellation.isBlank() ||
                        it.constellation
                            ?.equals(
                                constellation.trim(),
                                ignoreCase = true
                            ) == true
                }
                .filter { item ->
                    if (normalizedQuery.isBlank()) {
                        true
                    } else {
                        listOfNotNull(
                            item.name,
                            item.catalogName,
                            item.reference,
                            item.aliasesFr,
                            item.constellation
                        ).any {
                            it.lowercase()
                                .contains(
                                    normalizedQuery
                                )
                        }
                    }
                }
                .toList()

        val normalizedSort =
            when (sort) {
                "name",
                "altitude",
                "magnitude" -> sort

                else -> "magnitude"
            }

        val normalizedOrder =
            if (order == "desc") {
                "desc"
            } else {
                "asc"
            }

        val comparator =
            when (normalizedSort) {

                "name" ->
                    Comparator<SkyObject> { a, b ->
                        val result =
                            a.name.compareTo(
                                b.name,
                                ignoreCase = true
                            )

                        if (
                            normalizedOrder == "desc"
                        ) {
                            -result
                        } else {
                            result
                        }
                    }

                "altitude" ->
                    Comparator<SkyObject> { a, b ->
                        if (
                            normalizedOrder == "desc"
                        ) {
                            b.altitudeDeg
                                .compareTo(
                                    a.altitudeDeg
                                )
                        } else {
                            a.altitudeDeg
                                .compareTo(
                                    b.altitudeDeg
                                )
                        }
                    }

                else ->
                    Comparator<SkyObject> { a, b ->

                        when {
                            a.magnitude == null &&
                                b.magnitude == null ->
                                0

                            a.magnitude == null ->
                                1

                            b.magnitude == null ->
                                -1

                            normalizedOrder ==
                                "desc" ->
                                b.magnitude
                                    .compareTo(
                                        a.magnitude
                                    )

                            else ->
                                a.magnitude
                                    .compareTo(
                                        b.magnitude
                                    )
                        }
                    }
            }

        val sorted =
            filtered.sortedWith(
                comparator
            )

        val safeLimit =
            limit.coerceIn(
                1,
                100
            )

        val safeOffset =
            offset
                .coerceAtLeast(0)
                .coerceAtMost(
                    sorted.size
                )

        val page =
            sorted.drop(
                safeOffset
            ).take(
                safeLimit
            )

        val categoryLabel =
            when (category) {
                "star" -> "Etoiles"
                "galaxy" -> "Galaxies"
                "nebula" -> "Nebuleuses"
                "cluster" -> "Amas"
                else -> "Tous"
            }

        return SkyObjectsResult(
            status = "ok",
            category = category,
            categoryLabelFr = categoryLabel,
            query =
                query.takeIf {
                    it.isNotBlank()
                },
            minAltitudeDeg = minAltitude,
            visibleCount = sorted.size,
            returnedCount = page.size,
            sort = normalizedSort,
            order = normalizedOrder,
            offset = safeOffset,
            limit = safeLimit,
            objects = page
        )
    }
}
