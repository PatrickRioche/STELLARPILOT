package fr.stellarpilot.app.data.remote

import fr.stellarpilot.app.domain.model.CameraCapture
import fr.stellarpilot.app.domain.model.CameraSensor
import fr.stellarpilot.app.domain.model.CameraStatus
import fr.stellarpilot.app.domain.model.CatalogStatus
import fr.stellarpilot.app.domain.model.CatalogTypeDetail
import fr.stellarpilot.app.domain.model.DeviceStatus
import fr.stellarpilot.app.domain.model.GpsStatus
import fr.stellarpilot.app.domain.model.MountStatus
import fr.stellarpilot.app.domain.model.ServerSession
import fr.stellarpilot.app.domain.model.ServerStatus
import fr.stellarpilot.app.domain.model.SkyObserver
import fr.stellarpilot.app.domain.model.SkyStar
import fr.stellarpilot.app.domain.model.SkyStatus
import fr.stellarpilot.app.domain.model.SystemDevices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class StellarPilotApiClient(
    private val baseUrl: String,
    private val client: OkHttpClient = createHttpClient()
) {

    companion object {

        /*
         * Configuration r?seau commune ? StellarPilot.
         *
         * Les d?lais sont volontairement courts car le serveur
         * StellarPilot fonctionne normalement sur le m?me r?seau local
         * que la tablette Android.
         *
         * Le ping WebSocket natif d'OkHttp sert de heartbeat transport.
         * Si le serveur cesse de r?pondre, OkHttp d?clenche automatiquement
         * l'?chec du WebSocket afin de permettre une reconnexion rapide.
         */
        private fun createHttpClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()

        /*
         * Client d?di? ? /status.
         *
         * Il ne partage ni dispatcher ni pool de connexions avec
         * le WebSocket. Une t?l?m?trie lente ne peut donc pas
         * perturber le heartbeat temps r?el.
         */
        private fun createStatusClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .callTimeout(25, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()

        /*
         * Client exclusivement r?serv? au WebSocket StellarPilot.
         *
         * Le ping/pong natif OkHttp constitue le heartbeat r?seau.
         */
        private fun createWebSocketClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .pingInterval(10, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
    }

    private val statusClient: OkHttpClient =
        createStatusClient()

    private val webSocketClient: OkHttpClient =
        createWebSocketClient()

    suspend fun checkHealth() = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint("health"))
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "HTTP ${response.code} sur /health"
            }
        }
    }

    suspend fun getStatus(): ServerStatus = withContext(Dispatchers.IO) {

        /*
         * /status interroge r?ellement INDI et plusieurs p?riph?riques.
         * Cette requ?te dispose donc d'une marge sup?rieure ? /health.
         *
         * Les d?lais courts du client principal restent inchang?s pour
         * d?tecter rapidement une v?ritable perte du serveur.
         */
        val request = Request.Builder()
            .url(endpoint("status"))
            .get()
            .build()

        statusClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "HTTP ${response.code} sur /status"
            }

            val body = response.body?.string()
                ?: error("R\u00E9ponse /status vide")

            parseStatus(body)
        }
    }

    suspend fun getCatalogStatus(): CatalogStatus =
        withContext(Dispatchers.IO) {

            val request = Request.Builder()
                .url(endpoint("catalog/status"))
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) {
                    "HTTP ${response.code} sur /catalog/status"
                }

                val body = response.body?.string()
                    ?: error("Réponse /catalog/status vide")

                parseCatalogStatus(body)
            }
        }

    suspend fun getBrightStars(
        latitude: Double? = null,
        longitude: Double? = null
    ): SkyStatus = withContext(Dispatchers.IO) {

        val url =
            if (
                latitude != null &&
                longitude != null
            ) {
                endpoint("sky/bright-stars") +
                    "?latitude=$latitude&longitude=$longitude"
            } else {
                endpoint("sky/bright-stars")
            }
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "HTTP ${response.code} sur /sky/bright-stars"
            }

            val body = response.body?.string()
                ?: error("R\u00E9ponse /sky/bright-stars vide")

            parseSkyStatus(body)
        }
    }

    suspend fun setManualLocation(
        latitude: Double,
        longitude: Double
    ) = withContext(Dispatchers.IO) {

        val payload = JSONObject()
            .put("latitude", latitude)
            .put("longitude", longitude)
            .put("altitude", JSONObject.NULL)
            .put(
                "timestamp",
                System.currentTimeMillis().toString()
            )

        val body = payload
            .toString()
            .toRequestBody(
                "application/json; charset=utf-8"
                    .toMediaType()
            )

        val request = Request.Builder()
            .url(endpoint("system/location"))
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "HTTP ${response.code} sur /system/location"
            }
        }
    }
    fun openEvents(listener: WebSocketListener): WebSocket {
        val request = Request.Builder()
            .url(webSocketEndpoint())
            .build()

        return webSocketClient.newWebSocket(
            request,
            listener
        )
    }

    private fun endpoint(path: String): String =
        baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    private fun webSocketEndpoint(): String {
        val httpUrl = endpoint("ws")

        return when {
            httpUrl.startsWith("https://") ->
                "wss://" + httpUrl.removePrefix("https://")

            httpUrl.startsWith("http://") ->
                "ws://" + httpUrl.removePrefix("http://")

            else ->
                error("Sch\u00E9ma serveur non support\u00E9: $baseUrl")
        }
    }

    private fun parseCatalogStatus(
        json: String
    ): CatalogStatus {

        val root = JSONObject(json)

        val detailsJson =
            root.optJSONArray("type_details")

        val details = buildList {

            if (detailsJson != null) {

                for (
                    index in 0 until detailsJson.length()
                ) {
                    val item =
                        detailsJson.optJSONObject(index)

                    if (item != null) {
                        add(
                            CatalogTypeDetail(
                                type = item.optString(
                                    "type",
                                    "unknown"
                                ),
                                labelFr = item.optString(
                                    "label_fr",
                                    "Autres objets"
                                ),
                                count = item.optInt(
                                    "count",
                                    0
                                )
                            )
                        )
                    }
                }
            }
        }

        return CatalogStatus(
            status = root.optString(
                "status",
                "unknown"
            ),
            databaseName =
                root.optNullableString(
                    "database_name"
                ),
            source =
                root.optNullableString(
                    "source"
                ),
            sourceVersion =
                root.optNullableString(
                    "source_version"
                ),
            language =
                root.optNullableString(
                    "language"
                ),
            offline = root.optBoolean(
                "offline",
                true
            ),
            objectCount = root.optInt(
                "object_count",
                0
            ),
            constellationCount = root.optInt(
                "constellation_count",
                0
            ),
            frenchNameCount = root.optInt(
                "french_name_count",
                0
            ),
            frenchAliasCount = root.optInt(
                "french_alias_count",
                0
            ),
            typeDetails = details
        )
    }

    private fun parseSkyStatus(json: String): SkyStatus {
        val root = JSONObject(json)

        val status = root.optString(
            "status",
            "ok"
        )

        if (status == "location_required") {
            return SkyStatus(
                status = "location_required",
                observer = null,
                catalogCount = 0,
                aboveHorizonCount = 0,
                alignmentCandidateCount = 0,
                recommended = null,
                stars = emptyList()
            )
        }

        val observerJson =
            root.optJSONObject("observer")

        val recommendedJson =
            root.optJSONObject("recommended")

        val starsJson =
            root.optJSONArray("stars")

        fun parseStar(star: JSONObject): SkyStar =
            SkyStar(
                id = star.optString("id", ""),
                name = star.optString("name", ""),
                constellation = star.optString(
                    "constellation",
                    ""
                ),
                objectType = star.optString(
                    "object_type",
                    "star"
                ),
                magnitude = star.optDouble(
                    "magnitude",
                    0.0
                ),
                raHours = star.optDouble(
                    "ra_hours",
                    0.0
                ),
                decDeg = star.optDouble(
                    "dec_deg",
                    0.0
                ),
                altitudeDeg = star.optDouble(
                    "altitude_deg",
                    0.0
                ),
                azimuthDeg = star.optDouble(
                    "azimuth_deg",
                    0.0
                ),
                azimuthDirection = star.optString(
                    "azimuth_direction",
                    ""
                ),
                aboveHorizon = star.optBoolean(
                    "above_horizon",
                    false
                ),
                alignmentCandidate = star.optBoolean(
                    "alignment_candidate",
                    false
                ),
                alignmentScore =
                    star.optNullableDouble(
                        "alignment_score"
                    )
            )

        val stars = buildList {
            if (starsJson != null) {
                for (index in 0 until starsJson.length()) {
                    val star =
                        starsJson.optJSONObject(index)

                    if (star != null) {
                        add(parseStar(star))
                    }
                }
            }
        }

        return SkyStatus(
            status = root.optString(
                "status",
                "unknown"
            ),

            observer =
                observerJson?.let {
                    SkyObserver(
                        latitude =
                            it.optNullableDouble(
                                "latitude"
                            ),

                        longitude =
                            it.optNullableDouble(
                                "longitude"
                            ),

                        timestampUtc =
                            it.optNullableString(
                                "timestamp_utc"
                            ),

                        locationSource =
                            it.optNullableString(
                                "location_source"
                            )
                    )
                },

            catalogCount = root.optInt(
                "catalog_count",
                0
            ),

            aboveHorizonCount = root.optInt(
                "above_horizon_count",
                0
            ),

            alignmentCandidateCount = root.optInt(
                "alignment_candidate_count",
                0
            ),

            recommended =
                recommendedJson?.let {
                    parseStar(it)
                },

            stars = stars
        )
    }

    private fun parseStatus(json: String): ServerStatus {
        val root = JSONObject(json)

        val session =
            root.optJSONObject("session")
                ?: JSONObject()

        val devices =
            root.optJSONObject("devices")
                ?: JSONObject()

        val server =
            devices.optJSONObject("server")
                ?: JSONObject()

        val mount =
            devices.optJSONObject("mount")
                ?: JSONObject()

        val camera =
            devices.optJSONObject("camera")
                ?: JSONObject()

        val gps =
            devices.optJSONObject("gps")
                ?: JSONObject()

        val cameraSensor =
            camera.optJSONObject("sensor")
                ?: JSONObject()

        val cameraCapture =
            camera.optJSONObject("capture")
                ?: JSONObject()

        return ServerStatus(
            service = root.optString(
                "service",
                "unknown"
            ),

            status = root.optString(
                "status",
                "unknown"
            ),

            poc = root.optBoolean(
                "poc",
                false
            ),

            mode = root.optString(
                "mode",
                "unknown"
            ),

            devices = SystemDevices(
                server = DeviceStatus(
                    status = server.optString(
                        "status",
                        "unknown"
                    )
                ),

                mount = MountStatus(
                    status = mount.optString(
                        "status",
                        "unknown"
                    ),

                    name = mount.optNullableString(
                        "name"
                    ),

                    type = mount.optNullableString(
                        "type"
                    ),

                    typeLabel = mount.optNullableString(
                        "type_label"
                    ),

                    family = mount.optNullableString(
                        "family"
                    ),

                    familyLabel = mount.optNullableString(
                        "family_label"
                    ),

                    startupTarget = mount.optNullableString(
                        "startup_target"
                    )
                ),

                camera = CameraStatus(
                    status = camera.optString(
                        "status",
                        "unknown"
                    ),

                    name = camera.optNullableString(
                        "name"
                    ),

                    sensor = CameraSensor(
                        width = cameraSensor.optNullableInt(
                            "width"
                        ),

                        height = cameraSensor.optNullableInt(
                            "height"
                        ),

                        pixelSizeUm =
                            cameraSensor.optNullableDouble(
                                "pixel_size_um"
                            ),

                        pixelSizeXUm =
                            cameraSensor.optNullableDouble(
                                "pixel_size_x_um"
                            ),

                        pixelSizeYUm =
                            cameraSensor.optNullableDouble(
                                "pixel_size_y_um"
                            ),

                        bitsPerPixel =
                            cameraSensor.optNullableInt(
                                "bits_per_pixel"
                            )
                    ),

                    capture = CameraCapture(
                        exposureS =
                            cameraCapture.optNullableDouble(
                                "exposure_s"
                            ),

                        gain =
                            cameraCapture.optNullableDouble(
                                "gain"
                            ),

                        offset =
                            cameraCapture.optNullableDouble(
                                "offset"
                            ),

                        binX =
                            cameraCapture.optNullableInt(
                                "bin_x"
                            ),

                        binY =
                            cameraCapture.optNullableInt(
                                "bin_y"
                            ),

                        frameWidth =
                            cameraCapture.optNullableInt(
                                "frame_width"
                            ),

                        frameHeight =
                            cameraCapture.optNullableInt(
                                "frame_height"
                            ),

                        frameType =
                            cameraCapture.optNullableString(
                                "frame_type"
                            )
                    ),

                    temperatureC =
                        camera.optNullableDouble(
                            "temperature_c"
                        )
                ),

                gps = GpsStatus(
                    status = gps.optString(
                        "status",
                        "unknown"
                    ),

                    latitude =
                        gps.optNullableDouble(
                            "latitude"
                        ),

                    longitude =
                        gps.optNullableDouble(
                            "longitude"
                        )
                )
            ),

            session = ServerSession(
                latitude =
                    session.optNullableDouble(
                        "latitude"
                    ),

                longitude =
                    session.optNullableDouble(
                        "longitude"
                    ),

                altitude =
                    session.optNullableDouble(
                        "altitude"
                    ),

                timestamp =
                    session.optNullableString(
                        "timestamp"
                    ),

                mountType =
                    session.optNullableString(
                        "mount_type"
                    ),

                mountTypeSource =
                    session.optNullableString(
                        "mount_type_source"
                    ),

                mountFamily =
                    session.optNullableString(
                        "mount_family"
                    ),

                mountFamilyLabel =
                    session.optNullableString(
                        "mount_family_label"
                    ),

                startupTarget =
                    session.optNullableString(
                        "startup_target"
                    )
            )
        )
    }
}

private fun JSONObject.optNullableDouble(
    name: String
): Double? =
    if (!has(name) || isNull(name)) {
        null
    } else {
        optDouble(name)
    }

private fun JSONObject.optNullableInt(
    name: String
): Int? =
    if (!has(name) || isNull(name)) {
        null
    } else {
        optInt(name)
    }

private fun JSONObject.optNullableString(
    name: String
): String? =
    if (!has(name) || isNull(name)) {
        null
    } else {
        optString(name)
    }
