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
    val solverDetail: String?,
    val verifiedAt: String? = null
)


data class CaptureQualityStatus(
    val score: Int?,
    val label: String?,
    val classification: String?,
    val starCount: Int?,
    val saturatedPercent: Double?,
    val recommendedExposureFactor: Double?
)


data class CaptureLightQualityStatus(
    val status: String,
    val accepted: Boolean,
    val score: Int?,
    val starCount: Int?,
    val backgroundSigma: Double?,
    val saturatedPercent: Double?,
    val fwhmPx: Double?,
    val ellipticity: Double?,
    val starSignal: Double?,
    val reasons: List<String>
)


data class CaptureCalibrationStatus(
    val required: Boolean,
    val status: String,
    val masterId: String?,
    val masterDark: String?,
    val temperatureDeltaC: Double?,
    val hotPixels: Int?,
    val calibratedFrames: Int,
    val detail: String?
)


data class CaptureStackingStatus(
    val running: Boolean,
    val recenterRequired: Boolean,
    val recenterReason: String?,
    val lastRegistrationDxPx: Double?,
    val lastRegistrationDyPx: Double?,
    val lastRegistrationDistancePx: Double?,
    val mode: String = "continuous",
    val runCount: Int = 0,
    val liveStackMethod: String? = null,
    val finalStackMethod: String? = null,
    val resumeVerificationAfter: String? = null
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
    val centeringQuality: CaptureQualityStatus? = null,
    val stacking: CaptureStackingStatus,
    val hasPreview: Boolean,
    val hasStackPreview: Boolean,
    val galleryPath: String?,
    val acquisitionSeconds: Double = 0.0,
    val rejectedByReason: Map<String, Int> = emptyMap(),
    val calibration: CaptureCalibrationStatus? = null,
    val lastLightQuality: CaptureLightQualityStatus? = null
)


data class GallerySession(
    val id: String,
    val createdAt: String,
    val targetName: String,
    val exposureSeconds: Double,
    val capturedFrames: Int,
    val acceptedFrames: Int,
    val rejectedFrames: Int,
    val integrationSeconds: Double,
    val latitude: Double?,
    val longitude: Double?,
    val altitudeM: Double?,
    val locationSource: String?,
    val placeName: String?,
    val acquisitionSeconds: Double = 0.0
)


class CaptureSessionApiClient(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
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
        val payload = JSONObject()
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
        parseSession(executeJson("capture/sessions", "POST", payload))
    }

    suspend fun getSession(sessionId: String): CaptureSessionStatus =
        withContext(Dispatchers.IO) {
            parseSession(executeJson("capture/sessions/$sessionId", "GET"))
        }

    suspend fun centerStep(sessionId: String): CaptureSessionStatus =
        withContext(Dispatchers.IO) {
            centerAction("capture/sessions/$sessionId/center")
        }

    suspend fun captureCenterFrame(sessionId: String): CaptureSessionStatus =
        withContext(Dispatchers.IO) {
            centerAction("capture/sessions/$sessionId/center/capture")
        }

    suspend fun solveCenterFrame(sessionId: String): CaptureSessionStatus =
        withContext(Dispatchers.IO) {
            centerAction("capture/sessions/$sessionId/center/solve")
        }

    private fun centerAction(path: String): CaptureSessionStatus {
        val root = executeJson(path, "POST")
        if (root.optString("status") == "error") {
            error(root.optString("detail", "Action de centrage impossible"))
        }
        return parseSession(
            root.optJSONObject("session")
                ?: error("Réponse de centrage sans session")
        )
    }

    suspend fun startStack(sessionId: String): CaptureSessionStatus =
        withContext(Dispatchers.IO) {
            sessionFromAction("capture/sessions/$sessionId/stack/start")
        }

    suspend fun resumeStack(sessionId: String): CaptureSessionStatus =
        withContext(Dispatchers.IO) {
            sessionFromAction("capture/sessions/$sessionId/stack/resume")
        }

    suspend fun stopStack(sessionId: String): CaptureSessionStatus =
        withContext(Dispatchers.IO) {
            sessionFromAction("capture/sessions/$sessionId/stack/stop")
        }

    suspend fun finalizeSession(sessionId: String): CaptureSessionStatus =
        withContext(Dispatchers.IO) {
            sessionFromAction("capture/sessions/$sessionId/finalize")
        }

    private fun sessionFromAction(path: String): CaptureSessionStatus {
        val root = executeJson(path, "POST")
        val status = root.optString("status")
        if (
            status in setOf(
                "centering_required",
                "resume_required",
                "stacking_running",
                "finalized",
                "error"
            )
        ) {
            error(root.optString("detail", "Action capture impossible"))
        }
        return parseSession(
            root.optJSONObject("session")
                ?: error("Réponse capture sans session")
        )
    }

    suspend fun getPreview(
        sessionId: String,
        stack: Boolean
    ): ByteArray = withContext(Dispatchers.IO) {
        val suffix = if (stack) "stack/preview.jpg" else "preview.jpg"
        executeBytes("capture/sessions/$sessionId/$suffix")
    }

    suspend fun listGalleries(): List<GallerySession> =
        withContext(Dispatchers.IO) {
            val array = executeJson("galleries/sessions", "GET")
                .optJSONArray("sessions")
                ?: return@withContext emptyList()

            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val target = item.optJSONObject("target")
                    val setup = item.optJSONObject("setup")
                    val counts = item.optJSONObject("counts")
                    val observation = item.optJSONObject("observation")
                    add(
                        GallerySession(
                            id = item.optString("id"),
                            createdAt = item.optString("created_at"),
                            targetName = target?.optString("name")
                                ?.takeIf { it.isNotBlank() } ?: "Cible",
                            exposureSeconds = setup?.optDouble("exposure_s", 4.0) ?: 4.0,
                            capturedFrames = counts?.optInt("captured", 0) ?: 0,
                            acceptedFrames = counts?.optInt("accepted", 0) ?: 0,
                            rejectedFrames = counts?.optInt("rejected", 0) ?: 0,
                            integrationSeconds = item.optDouble("integration_seconds", 0.0),
                            latitude = observation?.let { nullableDouble(it, "latitude") },
                            longitude = observation?.let { nullableDouble(it, "longitude") },
                            altitudeM = observation?.let { nullableDouble(it, "altitude_m") },
                            locationSource = observation?.let { nullableString(it, "location_source") },
                            placeName = observation?.let { nullableString(it, "place_name") },
                            acquisitionSeconds = item.optDouble("acquisition_seconds", 0.0)
                        )
                    )
                }
            }
        }

    suspend fun getGalleryPreview(sessionId: String): ByteArray =
        withContext(Dispatchers.IO) {
            executeBytes("galleries/sessions/$sessionId/preview.jpg")
        }

    private fun executeJson(
        path: String,
        method: String,
        payload: JSONObject? = null
    ): JSONObject {
        val builder = Request.Builder()
            .url(endpoint(path))
            .header("Connection", "close")

        if (method == "POST") {
            val body = (payload ?: JSONObject())
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            builder.post(body)
        } else {
            builder.get()
        }

        client.newCall(builder.build()).execute().use { response ->
            check(response.isSuccessful) {
                "HTTP ${response.code} sur /$path"
            }
            return JSONObject(
                response.body?.string()
                    ?: error("Réponse /$path vide")
            )
        }
    }

    private fun executeBytes(path: String): ByteArray {
        val request = Request.Builder()
            .url(endpoint(path) + "?t=" + System.currentTimeMillis())
            .header("Connection", "close")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
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

        val centeringQuality = root.optJSONObject("centering_quality")
            ?.takeIf { it.optString("status") == "ok" }
            ?.let {
                CaptureQualityStatus(
                    score = nullableInt(it, "astrometry_score"),
                    label = nullableString(it, "quality_label"),
                    classification = nullableString(it, "classification"),
                    starCount = nullableInt(it, "star_count"),
                    saturatedPercent = nullableDouble(it, "saturated_percent"),
                    recommendedExposureFactor = nullableDouble(
                        it,
                        "recommended_exposure_factor"
                    )
                )
            }

        val calibration = root.optJSONObject("calibration")?.let {
            CaptureCalibrationStatus(
                required = it.optBoolean("required", true),
                status = it.optString("status", "pending"),
                masterId = nullableString(it, "master_id"),
                masterDark = nullableString(it, "master_dark"),
                temperatureDeltaC = nullableDouble(it, "temperature_delta_c"),
                hotPixels = nullableInt(it, "hot_pixels"),
                calibratedFrames = it.optInt("calibrated_frames", 0),
                detail = nullableString(it, "detail")
            )
        }

        val lastLightQuality = root.optJSONObject("last_quality")?.let { quality ->
            val reasonsArray = quality.optJSONArray("reasons")
            val reasons = buildList {
                if (reasonsArray != null) {
                    for (index in 0 until reasonsArray.length()) {
                        reasonsArray.optString(index)
                            .takeIf { it.isNotBlank() }
                            ?.let { add(it) }
                    }
                }
            }
            CaptureLightQualityStatus(
                status = quality.optString("status", "unknown"),
                accepted = quality.optBoolean("accepted", false),
                score = nullableInt(quality, "score"),
                starCount = nullableInt(quality, "star_count"),
                backgroundSigma = nullableDouble(quality, "background_sigma"),
                saturatedPercent = nullableDouble(quality, "saturated_percent"),
                fwhmPx = nullableDouble(quality, "fwhm_px"),
                ellipticity = nullableDouble(quality, "ellipticity"),
                starSignal = nullableDouble(quality, "star_signal"),
                reasons = reasons
            )
        }

        val rejectedByReason = buildMap {
            val reasons = counts.optJSONObject("rejected_by_reason")
            if (reasons != null) {
                val keys = reasons.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, reasons.optInt(key, 0))
                }
            }
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
            integrationSeconds = root.optDouble("integration_seconds", 0.0),
            centering = CaptureCenteringStatus(
                status = centering.optString("status", "not_checked"),
                errorArcsec = nullableDouble(centering, "error_arcsec"),
                solveRaDeg = nullableDouble(centering, "solve_ra_deg"),
                solveDecDeg = nullableDouble(centering, "solve_dec_deg"),
                correctionRaHours = nullableDouble(centering, "correction_ra_hours"),
                correctionDecDeg = nullableDouble(centering, "correction_dec_deg"),
                attempts = centering.optInt("attempts", 0),
                solverStatus = nullableString(centering, "solver_status"),
                solverDetail = nullableString(centering, "solver_detail"),
                verifiedAt = nullableString(centering, "verified_at")
            ),
            centeringQuality = centeringQuality,
            stacking = CaptureStackingStatus(
                running = stacking.optBoolean("running", false),
                recenterRequired = stacking.optBoolean("recenter_required", false),
                recenterReason = nullableString(stacking, "recenter_reason"),
                lastRegistrationDxPx = nullableDouble(stacking, "last_registration_dx_px"),
                lastRegistrationDyPx = nullableDouble(stacking, "last_registration_dy_px"),
                lastRegistrationDistancePx = nullableDouble(
                    stacking,
                    "last_registration_distance_px"
                ),
                mode = stacking.optString("mode", "continuous"),
                runCount = stacking.optInt("run_count", 0),
                liveStackMethod = nullableString(stacking, "live_stack_method"),
                finalStackMethod = nullableString(stacking, "final_stack_method"),
                resumeVerificationAfter = nullableString(
                    stacking,
                    "resume_verification_after"
                )
            ),
            hasPreview = root.has("preview") && !root.isNull("preview"),
            hasStackPreview = root.has("stack_preview") && !root.isNull("stack_preview"),
            galleryPath = nullableString(root, "gallery_path"),
            acquisitionSeconds = root.optDouble("acquisition_seconds", 0.0),
            rejectedByReason = rejectedByReason,
            calibration = calibration,
            lastLightQuality = lastLightQuality
        )
    }

    private fun nullableDouble(json: JSONObject, key: String): Double? {
        if (!json.has(key) || json.isNull(key)) return null
        return json.optDouble(key, Double.NaN).takeUnless { it.isNaN() }
    }

    private fun nullableInt(json: JSONObject, key: String): Int? {
        if (!json.has(key) || json.isNull(key)) return null
        return json.optInt(key)
    }

    private fun nullableString(json: JSONObject, key: String): String? {
        if (!json.has(key) || json.isNull(key)) return null
        return json.optString(key).takeIf { it.isNotBlank() }
    }

    private fun endpoint(path: String): String =
        baseUrl.trimEnd('/') + "/" + path.trimStart('/')
}
