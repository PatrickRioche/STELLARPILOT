package fr.stellarpilot.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
                            .takeIf { it.isNotBlank() }
                    )
                }
        }
}
