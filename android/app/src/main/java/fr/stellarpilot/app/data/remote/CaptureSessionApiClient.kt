package fr.stellarpilot.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit


data class CaptureCenteringStatus(
    val status: String,
    val errorArcsec: Double?,
    val solveRaDeg: Double?,
    val solveDecDeg: Double?,
    val correctionRaHours: Double?,
    val correctionDecDeg: Double?,
    val attempts: Int,
    val solverStatus: String?,
    val solverDetail: String?
)


data class CaptureStackingStatus(
    val running: Boolean,
    val recenterRequired: Boolean,
    val recenterReason: String?,
    val lastRegistrationDxPx: Double?,
    val lastRegistrationDyPx: Double?,
    val lastRegistrationDistancePx: Double?
)


data class CaptureSessionStatus(
    val id: String,
    val state: String,
    val targetName: String,
    val targetRaHours: Double,
    val targetDecDeg: Double,
    val objectType: String,
    val trackingMode: String,
    val exposureSeconds: Double,
    val capturedFrames: Int,
    val acceptedFrames: Int,
    val rejectedFrames: Int,
    val integrationSeconds: Double,
    val centering: CaptureCenteringStatus,
    val stacking: CaptureStackingStatus,
    val hasPreview: Boolean,
    val hasStackPreview: Boolean,
    val galleryPath: String?
)


data class GallerySession(
    val id: String,
    val createdAt: String,
    val targetName: String,
    val exposureSeconds: Double,
    val acceptedFrames: Int,
    val rejectedFrames: Int,
    val integrationSeconds: Double
)


class CaptureSessionApiClient(
    private val baseUrl: String,
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(150, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
) {

    suspend fun createSession(
        targetName: String,
        targetRaHours: Double,
        targetDecDeg: Double,
        objectType: String,
        trackingMode: String,
        exposureSeconds: Double
    ): CaptureSessionStatus = withContext(Dispatchers.IO) {
        val payload =
            JSONObject()
                .put("target_name", targetName)
                .put("target_ra_hours", targetRaHours)
                .put("target_dec_deg", targetDecDeg)
                .put("object_type", objectType)
                .put("tracking_mode", trackingMode)
                .put("exposure_s", exposureSeconds)
                .put("centering_tolerance_arcsec", 30.0)
                .put("recenter_tolerance_arcsec", 30.0)
                .put("astrometry_interval_frames", 8)
                .put("registration_recenter_pixels", 12.0)

        parseSession(
            executeJson(
                path = "capture/sessions",
                method = "POST",
                payload = payload
            )
        )
    }

    suspend fun getSession(
        sessionId: String
    ): CaptureSessionStatus = withContext(Dispatchers.IO) {
        parseSession(
            executeJson(
                path = "capture/sessions/$sessionId",
                method = "GET"
            )
        )
    }

    suspend fun centerStep(
        sessionId: String
    ): CaptureSessionStatus = withContext(Dispatchers.IO) {
        val root = executeJson(
            path = "capture/sessions/$sessionId/center",
            method = "POST"
        )
        val session = root.optJSONObject("session")
            ?: error("Réponse de centrage sans session")
        parseSession(session)
    }

    suspend fun startStack(
        sessionId: String
    ): CaptureSessionStatus = withContext(Dispatchers.IO) {
        sessionFromAction(
            "capture/sessions/$sessionId/stack/start"
        )
    }

    suspend fun resumeStack(
        sessionId: String
    ): CaptureSessionStatus = withContext(Dispatchers.IO) {
        sessionFromAction(
            "capture/sessions/$sessionId/stack/resume"
        )
    }

    suspend fun stopStack(
        sessionId: String
    ): CaptureSessionStatus = withContext(Dispatchers.IO) {
        sessionFromAction(
            "capture/sessions/$sessionId/stack/stop"
        )
    }

    suspend fun finalizeSession(
        sessionId: String
    ): CaptureSessionStatus = withContext(Dispatchers.IO) {
        sessionFromAction(
            "capture/sessions/$sessionId/finalize"
        )
    }

    private fun sessionFromAction(path: String): CaptureSessionStatus {
        val root = executeJson(
            path = path,
            method = "POST"
        )
        val status = root.optString("status")
        if (
            status in setOf(
                "centering_required",
                "stacking_running",
                "error"
            )
        ) {
            error(
                root.optString(
                    "detail",
                    "Action capture impossible"
                )
            )
        }
        val session = root.optJSONObject("session")
            ?: error("Réponse capture sans session")
        return parseSession(session)
    }

    suspend fun getPreview(
        sessionId: String,
        stack: Boolean
    ): ByteArray = withContext(Dispatchers.IO) {
        val suffix =
            if (stack) "stack/preview.jpg"
            else "preview.jpg"
        executeBytes(
            "capture/sessions/$sessionId/$suffix"
        )
    }

    suspend fun listGalleries(): List<GallerySession> =
        withContext(Dispatchers.IO) {
            val root = executeJson(
                path = "galleries/sessions",
                method = "GET"
            )
            val array = root.optJSONArray("sessions")
                ?: return@withContext emptyList()

            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index)
                        ?: continue
                    val target = item.optJSONObject("target")
                    val setup = item.optJSONObject("setup")
                    val counts = item.optJSONObject("counts")
                    add(
                        GallerySession(
                            id = item.optString("id"),
                            createdAt = item.optString("created_at"),
                            targetName =
                                target?.optString("name")
                                    ?.takeIf { it.isNotBlank() }
                                    ?: "Cible",
                            exposureSeconds =
                                setup?.optDouble("exposure_s", 4.0)
                                    ?: 4.0,
                            acceptedFrames =
                                counts?.optInt("accepted", 0)
                                    ?: 0,
                            rejectedFrames =
                                counts?.optInt("rejected", 0)
                                    ?: 0,
                            integrationSeconds =
                                item.optDouble(
                                    "integration_seconds",
                                    0.0
                                )
                        )
                    )
                }
            }
        }

    suspend fun getGalleryPreview(
        sessionId: String
    ): ByteArray = withContext(Dispatchers.IO) {
        executeBytes(
            "galleries/sessions/$sessionId/preview.jpg"
        )
    }

    private fun executeJson(
        path: String,
        method: String,
        payload: JSONObject? = null
    ): JSONObject {
        val requestBuilder =
            Request.Builder()
                .url(endpoint(path))
                .header("Connection", "close")

        if (method == "POST") {
            val body =
                (payload ?: JSONObject())
                    .toString()
                    .toRequestBody(
                        "application/json; charset=utf-8"
                            .toMediaType()
                    )
            requestBuilder.post(body)
        } else {
            requestBuilder.get()
        }

        client.newCall(requestBuilder.build())
            .execute()
            .use { response ->
                check(response.isSuccessful) {
                    "HTTP ${response.code} sur /$path"
                }
                val body = response.body?.string()
                    ?: error("Réponse /$path vide")
                return JSONObject(body)
            }
    }

    private fun executeBytes(path: String): ByteArray {
        val request =
            Request.Builder()
                .url(endpoint(path) + "?t=" + System.currentTimeMillis())
                .header("Connection", "close")
                .get()
                .build()

        client.newCall(request)
            .execute()
            .use { response ->
                check(response.isSuccessful) {
                    "HTTP ${response.code} sur /$path"
                }
                return response.body?.bytes()
                    ?: error("Image /$path vide")
            }
    }

    private fun parseSession(root: JSONObject): CaptureSessionStatus {
        val target = root.optJSONObject("target") ?: JSONObject()
        val setup = root.optJSONObject("setup") ?: JSONObject()
        val counts = root.optJSONObject("counts") ?: JSONObject()
        val centering = root.optJSONObject("centering") ?: JSONObject()
        val stacking = root.optJSONObject("stacking") ?: JSONObject()

        fun nullableDouble(
            json: JSONObject,
            key: String
        ): Double? {
            if (!json.has(key) || json.isNull(key)) return null
            return json.optDouble(key, Double.NaN)
                .takeUnless { it.isNaN() }
        }

        fun nullableString(
            json: JSONObject,
            key: String
        ): String? {
            if (!json.has(key) || json.isNull(key)) return null
            return json.optString(key)
                .takeIf { it.isNotBlank() }
        }

        return CaptureSessionStatus(
            id = root.optString("id"),
            state = root.optString("state", "unknown"),
            targetName = target.optString("name", "Cible"),
            targetRaHours = target.optDouble("ra_hours", 0.0),
            targetDecDeg = target.optDouble("dec_deg", 0.0),
            objectType = target.optString("object_type", "unknown"),
            trackingMode = target.optString("tracking_mode", "sidereal"),
            exposureSeconds = setup.optDouble("exposure_s", 4.0),
            capturedFrames = counts.optInt("captured", 0),
            acceptedFrames = counts.optInt("accepted", 0),
            rejectedFrames = counts.optInt("rejected", 0),
            integrationSeconds =
                root.optDouble("integration_seconds", 0.0),
            centering = CaptureCenteringStatus(
                status = centering.optString("status", "not_checked"),
                errorArcsec = nullableDouble(centering, "error_arcsec"),
                solveRaDeg = nullableDouble(centering, "solve_ra_deg"),
                solveDecDeg = nullableDouble(centering, "solve_dec_deg"),
                correctionRaHours =
                    nullableDouble(centering, "correction_ra_hours"),
                correctionDecDeg =
                    nullableDouble(centering, "correction_dec_deg"),
                attempts = centering.optInt("attempts", 0),
                solverStatus = nullableString(centering, "solver_status"),
                solverDetail = nullableString(centering, "solver_detail")
            ),
            stacking = CaptureStackingStatus(
                running = stacking.optBoolean("running", false),
                recenterRequired =
                    stacking.optBoolean("recenter_required", false),
                recenterReason =
                    nullableString(stacking, "recenter_reason"),
                lastRegistrationDxPx =
                    nullableDouble(stacking, "last_registration_dx_px"),
                lastRegistrationDyPx =
                    nullableDouble(stacking, "last_registration_dy_px"),
                lastRegistrationDistancePx =
                    nullableDouble(
                        stacking,
                        "last_registration_distance_px"
                    )
            ),
            hasPreview = !root.isNull("preview") && root.has("preview"),
            hasStackPreview =
                !root.isNull("stack_preview") && root.has("stack_preview"),
            galleryPath = nullableString(root, "gallery_path")
        )
    }

    private fun endpoint(path: String): String =
        baseUrl.trimEnd('/') + "/" + path.trimStart('/')
}
