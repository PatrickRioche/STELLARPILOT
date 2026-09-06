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
    val storage: String?
)


class DarkCalibrationApiClient(
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
) {

    suspend fun start(
        serverBaseUrl: String,
        exposureSeconds: Double = 4.0,
        count: Int = 10
    ): DarkCalibrationStatus = withContext(Dispatchers.IO) {
        val json = JSONObject()
            .put("exposure_s", exposureSeconds)
            .put("requested_count", count)

        execute(
            serverBaseUrl = serverBaseUrl,
            path = "/calibration/darks",
            method = "POST",
            body = json
        )
    }

    suspend fun capture(
        serverBaseUrl: String,
        sessionId: String
    ): DarkCalibrationStatus = withContext(Dispatchers.IO) {
        execute(
            serverBaseUrl = serverBaseUrl,
            path = "/calibration/darks/$sessionId/capture",
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
            .url(serverBaseUrl.trimEnd('/') + path)
            .header("Connection", "close")

        val request = when (method) {
            "POST" -> {
                val requestBody = (body?.toString() ?: "")
                    .toRequestBody(
                        "application/json; charset=utf-8".toMediaType()
                    )
                builder.post(requestBody).build()
            }
            else -> builder.get().build()
        }

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "HTTP ${response.code} sur $path"
            }
            val payload = response.body?.string()
                ?: error("Réponse $path vide")
            val json = JSONObject(payload)
            return DarkCalibrationStatus(
                id = json.getString("id"),
                status = json.optString("status", "error"),
                exposureSeconds = json.optDouble("exposure_s", 4.0),
                requestedCount = json.optInt("requested_count", 10),
                capturedCount = json.optInt("captured_count", 0),
                validCount = json.optInt("valid_count", 0),
                storage = json.optString("storage")
                    .takeIf { it.isNotBlank() }
            )
        }
    }
}
