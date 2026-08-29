package fr.stellarpilot.app.data.remote

import fr.stellarpilot.app.domain.model.SkyObject
import fr.stellarpilot.app.domain.model.SkyObjectsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class SkyObjectsApiClient(
    private val baseUrl: String,
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(
                60,
                TimeUnit.SECONDS
            )
            .readTimeout(
                60,
                TimeUnit.SECONDS
            )
            .callTimeout(
                60,
                TimeUnit.SECONDS
            )
            .build()
) {

    suspend fun getObjects(
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
    ): SkyObjectsResult =
        withContext(Dispatchers.IO) {

            val parameters =
                mutableListOf(
                    "latitude=$latitude",
                    "longitude=$longitude",
                    "category=${encode(category)}",
                    "min_altitude=$minAltitude",
                    "sort=${encode(sort)}",
                    "order=${encode(order)}",
                    "offset=$offset",
                    "limit=$limit"
                )

            if (query.isNotBlank()) {
                parameters +=
                    "q=${encode(query.trim())}"
            }

            if (!direction.isNullOrBlank()) {
                parameters +=
                    "direction=${encode(direction)}"
            }

            if (constellation.isNotBlank()) {
                parameters +=
                    "constellation=${
                        encode(
                            constellation.trim()
                        )
                    }"
            }

            val request =
                Request.Builder()
                    .url(
                        endpoint(
                            "sky/objects"
                        ) +
                            "?" +
                            parameters
                                .joinToString("&")
                    )
                    .get()
                    .build()

            client.newCall(request)
                .execute()
                .use { response ->

                    check(
                        response.isSuccessful
                    ) {
                        "HTTP ${response.code} sur /sky/objects"
                    }

                    val body =
                        response.body
                            ?.string()
                            ?: error(
                                "Reponse /sky/objects vide"
                            )

                    parse(body)
                }
        }

    private fun parse(
        json: String
    ): SkyObjectsResult {

        val root =
            JSONObject(json)

        val array =
            root.optJSONArray(
                "objects"
            )

        val objects =
            buildList {

                if (array != null) {

                    for (
                        index in
                        0 until array.length()
                    ) {

                        val item =
                            array.optJSONObject(
                                index
                            )
                                ?: continue

                        add(
                            SkyObject(
                                id =
                                    item.optInt(
                                        "id",
                                        0
                                    ),

                                name =
                                    item.optString(
                                        "name",
                                        ""
                                    ),

                                catalogName =
                                    item.optString(
                                        "catalog_name",
                                        ""
                                    ),

                                reference =
                                    item.nullableString(
                                        "reference"
                                    ),

                                objectType =
                                    item.optString(
                                        "object_type",
                                        "unknown"
                                    ),

                                objectTypeLabelFr =
                                    item.optString(
                                        "object_type_label_fr",
                                        "Objet"
                                    ),

                                constellation =
                                    item.nullableString(
                                        "constellation"
                                    ),

                                raHours =
                                    item.optDouble(
                                        "ra_hours",
                                        0.0
                                    ),

                                decDeg =
                                    item.optDouble(
                                        "dec_deg",
                                        0.0
                                    ),

                                magnitude =
                                    item.nullableDouble(
                                        "magnitude"
                                    ),

                                magnitudeBand =
                                    item.nullableString(
                                        "magnitude_band"
                                    ),

                                majorAxisArcmin =
                                    item.nullableDouble(
                                        "major_axis_arcmin"
                                    ),

                                minorAxisArcmin =
                                    item.nullableDouble(
                                        "minor_axis_arcmin"
                                    ),

                                aliasesFr =
                                    item.nullableString(
                                        "aliases_fr"
                                    ),

                                altitudeDeg =
                                    item.optDouble(
                                        "altitude_deg",
                                        0.0
                                    ),

                                azimuthDeg =
                                    item.optDouble(
                                        "azimuth_deg",
                                        0.0
                                    ),

                                azimuthDirection =
                                    item.optString(
                                        "azimuth_direction",
                                        ""
                                    )
                            )
                        )
                    }
                }
            }

        return SkyObjectsResult(
            status =
                root.optString(
                    "status",
                    "unknown"
                ),

            category =
                root.optString(
                    "category",
                    "all"
                ),

            categoryLabelFr =
                root.optString(
                    "category_label_fr",
                    "Tous"
                ),

            query =
                root.nullableString(
                    "query"
                ),

            minAltitudeDeg =
                root.optDouble(
                    "min_altitude_deg",
                    15.0
                ),

            visibleCount =
                root.optInt(
                    "visible_count",
                    0
                ),

            returnedCount =
                root.optInt(
                    "returned_count",
                    0
                ),

            sort =
                root.optString(
                    "sort",
                    "magnitude"
                ),

            order =
                root.optString(
                    "order",
                    "asc"
                ),

            offset =
                root.optInt(
                    "offset",
                    0
                ),

            limit =
                root.optInt(
                    "limit",
                    30
                ),

            objects =
                objects
        )
    }

    private fun endpoint(
        path: String
    ): String =
        baseUrl.trimEnd('/') +
            "/" +
            path.trimStart('/')

    private fun encode(
        value: String
    ): String =
        URLEncoder.encode(
            value,
            Charsets.UTF_8.name()
        )

    private fun JSONObject.nullableString(
        key: String
    ): String? {

        if (
            !has(key) ||
            isNull(key)
        ) return null

        return optString(key)
            .takeIf {
                it.isNotBlank()
            }
    }

    private fun JSONObject.nullableDouble(
        key: String
    ): Double? {

        if (
            !has(key) ||
            isNull(key)
        ) return null

        return optDouble(
            key,
            Double.NaN
        ).takeUnless {
            it.isNaN()
        }
    }
}