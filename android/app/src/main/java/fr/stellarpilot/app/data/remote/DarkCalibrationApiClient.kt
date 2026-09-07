package fr.stellarpilot.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit


data class DarkCalibrationStatus(
    val id: String,
    val status: String,
    val exposureSeconds: Double,
    val requestedCount: Int,
    val capturedCount: Int,
    val validCount: Int,
    val storage: String?,
    val cameraName: String?,
    val gain: Double?,
    val offset: Double?,
    val binX: Int?,
    val binY: Int?,
    val temperatureC: Double?,
    val bayerPattern: String?,
    val opticalTrainName: String?,
    val telescopeName: String?,
    val telescopeType: String?,
    val apertureMm: Double?,
    val focalLengthMm: Double?,
    val focalRatio: Double?,
    val reducer: Double?,
    val masterDarkPath: String?,
    val masterMethod: String?,
    val hotPixelCount: Int?,
    val hotPixelMapPath: String?
)


class DarkCalibrationApiClient(
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
        exposureSeconds: Double = 4.0,
        count: Int = 20
    ): DarkCalibrationStatus =
        withContext(Dispatchers.IO) {

            val json = JSONObject()
                .put(
                    "exposure_s",
                    exposureSeconds
                )
                .put(
                    "requested_count",
                    count
                )

            execute(
                serverBaseUrl =
                    serverBaseUrl,
                path =
                    "/calibration/darks",
                method = "POST",
                body = json
            )
        }


    suspend fun capture(
        serverBaseUrl: String,
        sessionId: String
    ): DarkCalibrationStatus =
        withContext(Dispatchers.IO) {

            execute(
                serverBaseUrl =
                    serverBaseUrl,
                path =
                    "/calibration/darks/$sessionId/capture",
                method = "POST",
                body = null
            )
        }


    private fun execute(
        serverBaseUrl: String,
        path: String,
        method: String,
        body: JSONObject?
    ): DarkCalibrationStatus {

        val builder = Request.Builder()
            .url(
                serverBaseUrl
                    .trimEnd('/') +
                    path
            )
            .header(
                "Connection",
                "close"
            )

        val request =
            when (method) {

                "POST" -> {

                    val requestBody =
                        (
                            body?.toString()
                                ?: ""
                        ).toRequestBody(
                            "application/json; charset=utf-8"
                                .toMediaType()
                        )

                    builder
                        .post(requestBody)
                        .build()
                }

                else ->
                    builder
                        .get()
                        .build()
            }

        client
            .newCall(request)
            .execute()
            .use { response ->

                val payload =
                    response.body
                        ?.string()
                        .orEmpty()

                if (!response.isSuccessful) {

                    val detail =
                        runCatching {
                            JSONObject(payload)
                                .optString(
                                    "detail",
                                    ""
                                )
                        }
                            .getOrNull()
                            ?.takeIf {
                                it.isNotBlank()
                            }

                    error(
                        buildString {
                            append(
                                "HTTP ${response.code}"
                            )

                            if (detail != null) {
                                append(" • ")
                                append(detail)
                            }

                            append(" • ")
                            append(path)
                        }
                    )
                }

                if (payload.isBlank()) {
                    error(
                        "Réponse $path vide"
                    )
                }

                val json = JSONObject(payload)
                val setup = json.optJSONObject("setup_profile") ?: JSONObject()
                val camera = setup.optJSONObject("camera") ?: JSONObject()
                val capture = camera.optJSONObject("capture") ?: JSONObject()
                val optical = setup.optJSONObject("optical") ?: JSONObject()
                val fits = json.optJSONObject("fits_profile") ?: JSONObject()
                val master = json.optJSONObject("master_dark") ?: JSONObject()
                val hotPixels = json.optJSONObject("hot_pixels") ?: JSONObject()

                return DarkCalibrationStatus(
                    id = json.getString("id"),
                    status = json.optString(
                        "status",
                        "error"
                    ),
                    exposureSeconds = json.optDouble(
                        "exposure_s",
                        4.0
                    ),
                    requestedCount = json.optInt(
                        "requested_count",
                        20
                    ),
                    capturedCount = json.optInt(
                        "captured_count",
                        0
                    ),
                    validCount = json.optInt(
                        "valid_count",
                        0
                    ),
                    storage = json.optNullableString("storage"),
                    cameraName = camera.optNullableString("name"),
                    gain = capture.optNullableDouble("gain"),
                    offset = capture.optNullableDouble("offset"),
                    binX = capture.optNullableInt("bin_x"),
                    binY = capture.optNullableInt("bin_y"),
                    temperatureC = camera.optNullableDouble("temperature_c"),
                    bayerPattern = fits.optNullableString("bayer_pattern"),
                    opticalTrainName = optical.optNullableString("optical_train_name"),
                    telescopeName = optical.optNullableString("telescope_name"),
                    telescopeType = optical.optNullableString("telescope_type"),
                    apertureMm = optical.optNullableDouble("aperture_mm"),
                    focalLengthMm = optical.optNullableDouble("focal_length_mm"),
                    focalRatio = optical.optNullableDouble("focal_ratio"),
                    reducer = optical.optNullableDouble("reducer"),
                    masterDarkPath = master.optNullableString("path"),
                    masterMethod = master.optNullableString("method"),
                    hotPixelCount = hotPixels.optNullableInt("count"),
                    hotPixelMapPath = hotPixels.optNullableString("mask_fits")
                )
            }
    }
}


private fun JSONObject.optNullableString(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

private fun JSONObject.optNullableDouble(name: String): Double? =
    if (!has(name) || isNull(name)) null else optDouble(name, Double.NaN).takeUnless { it.isNaN() }

private fun JSONObject.optNullableInt(name: String): Int? =
    if (!has(name) || isNull(name)) null else optInt(name)
