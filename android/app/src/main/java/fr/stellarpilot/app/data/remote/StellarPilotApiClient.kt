package fr.stellarpilot.app.data.remote

import fr.stellarpilot.app.domain.model.CameraCapture
import fr.stellarpilot.app.domain.model.CameraSensor
import fr.stellarpilot.app.domain.model.CameraStatus
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

class StellarPilotApiClient(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun getStatus(): ServerStatus = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint("status"))
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "HTTP ${response.code} sur /status"
            }

            val body = response.body?.string()
                ?: error("R\u00E9ponse /status vide")

            parseStatus(body)
        }
    }

    suspend fun getBrightStars(): SkyStatus = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint("sky/bright-stars"))
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

        return client.newWebSocket(request, listener)
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
