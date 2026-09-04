package fr.stellarpilot.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit


data class MountDiagnosticsResult(
    val status: String,
    val mount: String?,
    val raHours: Double?,
    val decDeg: Double?,
    val targetRaHours: Double?,
    val targetDecDeg: Double?,
    val progressPercent: Double?,
    val remainingDeg: Double?,
    val indiState: String?,
    val coordinateProperty: String?,
    val trackingMode: String?,
    val virtualPosition: Boolean,
    val detail: String?
)


data class MountTimeVerificationResult(
    val status: String,
    val verified: Boolean,
    val controlReady: Boolean,
    val detail: String?
)


class MountDiagnosticsApiClient {

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    suspend fun status(
        serverBaseUrl: String
    ): MountDiagnosticsResult =
        withContext(Dispatchers.IO) {
            val request =
                Request.Builder()
                    .url(
                        serverBaseUrl.trimEnd('/') +
                            "/mount/status"
                    )
                    .header("Connection", "close")
                    .get()
                    .build()

            client.newCall(request)
                .execute()
                .use { response ->
                    check(response.isSuccessful) {
                        "HTTP ${response.code} sur /mount/status"
                    }

                    val body = response.body?.string()
                        ?: error("Réponse /mount/status vide")
                    val json = JSONObject(body)

                    fun nullableDouble(key: String): Double? {
                        if (!json.has(key) || json.isNull(key)) return null
                        return json.optDouble(key, Double.NaN)
                            .takeUnless { it.isNaN() }
                    }

                    MountDiagnosticsResult(
                        status = json.optString("status", "error"),
                        mount = json.optString("mount")
                            .takeIf { it.isNotBlank() },
                        raHours = nullableDouble("ra"),
                        decDeg = nullableDouble("dec"),
                        targetRaHours = nullableDouble("target_ra"),
                        targetDecDeg = nullableDouble("target_dec"),
                        progressPercent = nullableDouble("progress_percent"),
                        remainingDeg = nullableDouble("remaining_deg"),
                        indiState = json.optString("indi_state")
                            .takeIf { it.isNotBlank() },
                        coordinateProperty =
                            json.optString("coordinate_property")
                                .takeIf { it.isNotBlank() },
                        trackingMode = json.optString("tracking_mode")
                            .takeIf { it.isNotBlank() },
                        virtualPosition =
                            json.optBoolean("virtual_position", false),
                        detail = json.optString("detail")
                            .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                    )
                }
        }

    suspend fun timeVerification(
        serverBaseUrl: String
    ): MountTimeVerificationResult =
        withContext(Dispatchers.IO) {
            val request =
                Request.Builder()
                    .url(
                        serverBaseUrl.trimEnd('/') +
                            "/mount/time/verification"
                    )
                    .header("Connection", "close")
                    .get()
                    .build()

            client.newCall(request)
                .execute()
                .use { response ->
                    check(response.isSuccessful) {
                        "HTTP ${response.code} sur /mount/time/verification"
                    }

                    val body = response.body?.string()
                        ?: error("Réponse /mount/time/verification vide")

                    parseTimeVerification(JSONObject(body))
                }
        }

    suspend fun syncTime(
        serverBaseUrl: String
    ): MountTimeVerificationResult =
        withContext(Dispatchers.IO) {
            val request =
                Request.Builder()
                    .url(
                        serverBaseUrl.trimEnd('/') +
                            "/mount/time/sync"
                    )
                    .header("Connection", "close")
                    .post(ByteArray(0).toRequestBody(null))
                    .build()

            client.newCall(request)
                .execute()
                .use { response ->
                    check(response.isSuccessful) {
                        "HTTP ${response.code} sur /mount/time/sync"
                    }

                    val body = response.body?.string()
                        ?: error("Réponse /mount/time/sync vide")

                    parseTimeVerification(JSONObject(body))
                }
        }

    private fun parseTimeVerification(
        json: JSONObject
    ): MountTimeVerificationResult {
        val verification = json.optJSONObject("verification")
        val status = json.optString("status", "unknown")

        val verified = when {
            json.has("verified") -> json.optBoolean("verified", false)
            verification?.has("verified") == true ->
                verification.optBoolean("verified", false)
            status.equals("verified", ignoreCase = true) -> true
            status.equals("synced", ignoreCase = true) -> true
            else -> false
        }

        val controlReady = when {
            json.has("control_ready") ->
                json.optBoolean("control_ready", false)
            verification?.has("control_ready") == true ->
                verification.optBoolean("control_ready", false)
            else -> verified
        }

        fun nullableText(source: JSONObject?, key: String): String? {
            if (source == null || !source.has(key) || source.isNull(key)) return null
            return source.optString(key)
                .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        }

        val detail =
            nullableText(json, "detail")
                ?: nullableText(verification, "detail")

        return MountTimeVerificationResult(
            status = status,
            verified = verified,
            controlReady = controlReady,
            detail = detail
        )
    }
}
