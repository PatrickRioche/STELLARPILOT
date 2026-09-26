package fr.stellarpilot.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit


data class FlatCalibrationStatus(
    val id: String,
    val status: String,
    val exposureSeconds: Double,
    val requestedCount: Int,
    val capturedCount: Int,
    val validCount: Int,
    val cameraName: String?,
    val gain: Double?,
    val offset: Double?,
    val binX: Int?,
    val binY: Int?,
    val bayerPattern: String?,
    val masterFlatPath: String?,
    val masterMethod: String?,
    val detail: String?
)


class FlatCalibrationApiClient(
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
) {

    suspend fun start(
        serverBaseUrl: String,
        exposureSeconds: Double,
        count: Int = 20
    ): FlatCalibrationStatus = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("exposure_s", exposureSeconds)
            .put("requested_count", count)
        execute(serverBaseUrl, "/calibration/flats", "POST", body)
    }

    suspend fun capture(
        serverBaseUrl: String,
        sessionId: String
    ): FlatCalibrationStatus = withContext(Dispatchers.IO) {
        execute(
            serverBaseUrl,
            "/calibration/flats/$sessionId/capture",
            "POST",
            null
        )
    }

    private fun execute(
        serverBaseUrl: String,
        path: String,
        method: String,
        body: JSONObject?
    ): FlatCalibrationStatus {
        val builder = Request.Builder()
            .url(serverBaseUrl.trimEnd('/') + path)
            .header("Connection", "close")

        val request = if (method == "POST") {
            builder.post(
                (body?.toString() ?: "")
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
            ).build()
        } else {
            builder.get().build()
        }

        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching {
                    JSONObject(payload).optString("detail", "")
                }.getOrNull()?.takeIf { it.isNotBlank() }
                error("HTTP ${response.code}${detail?.let { " • $it" } ?: ""} • $path")
            }
            if (payload.isBlank()) error("Réponse $path vide")

            val json = JSONObject(payload)
            val setup = json.optJSONObject("setup_profile") ?: JSONObject()
            val camera = setup.optJSONObject("camera") ?: JSONObject()
            val capture = camera.optJSONObject("capture") ?: JSONObject()
            val fits = json.optJSONObject("fits_profile") ?: JSONObject()
            val master = json.optJSONObject("master_flat") ?: JSONObject()

            return FlatCalibrationStatus(
                id = json.getString("id"),
                status = json.optString("status", "error"),
                exposureSeconds = json.optDouble("exposure_s", 0.2),
                requestedCount = json.optInt("requested_count", 20),
                capturedCount = json.optInt("captured_count", 0),
                validCount = json.optInt("valid_count", 0),
                cameraName = camera.optString("name").takeIf { it.isNotBlank() },
                gain = capture.optNullableFlatDouble("gain")
                    ?: fits.optNullableFlatDouble("gain"),
                offset = capture.optNullableFlatDouble("offset")
                    ?: fits.optNullableFlatDouble("offset"),
                binX = capture.optNullableFlatInt("bin_x"),
                binY = capture.optNullableFlatInt("bin_y"),
                bayerPattern = fits.optString("bayer_pattern").takeIf { it.isNotBlank() },
                masterFlatPath = master.optString("path").takeIf { it.isNotBlank() },
                masterMethod = master.optString("method").takeIf { it.isNotBlank() },
                detail = json.optString("detail").takeIf { it.isNotBlank() }
            )
        }
    }
}


private fun JSONObject.optNullableFlatDouble(name: String): Double? =
    if (!has(name) || isNull(name)) null
    else optDouble(name, Double.NaN).takeUnless { it.isNaN() }

private fun JSONObject.optNullableFlatInt(name: String): Int? =
    if (!has(name) || isNull(name)) null else optInt(name)
