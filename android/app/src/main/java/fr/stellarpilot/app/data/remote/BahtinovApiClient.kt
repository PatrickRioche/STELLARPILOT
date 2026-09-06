package fr.stellarpilot.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit


data class BahtinovQualityResult(
    val status: String,
    val focusScore: Int?,
    val focusLabel: String?,
    val focusReady: Boolean,
    val focusSide: String?,
    val instruction: String?,
    val errorFromOptimumPx: Double?,
    val geometryConfidence: Double?
)


class BahtinovApiClient {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(150, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

    suspend fun analyze(
        serverBaseUrl: String,
        imagePath: String
    ): BahtinovQualityResult = withContext(Dispatchers.IO) {
        val payload =
            JSONObject()
                .put("image", imagePath)
                .toString()
                .toRequestBody(
                    "application/json; charset=utf-8".toMediaType()
                )

        val request =
            Request.Builder()
                .url(serverBaseUrl.trimEnd('/') + "/bahtinov/quality")
                .header("Connection", "close")
                .post(payload)
                .build()

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "HTTP ${response.code} sur /bahtinov/quality"
            }

            val body = response.body?.string()
                ?: error("Réponse /bahtinov/quality vide")
            val json = JSONObject(body)

            fun nullableDouble(key: String): Double? {
                if (!json.has(key) || json.isNull(key)) return null
                return json.optDouble(key, Double.NaN)
                    .takeUnless { it.isNaN() }
            }

            BahtinovQualityResult(
                status = json.optString("status", "error"),
                focusScore =
                    if (json.has("focus_score") && !json.isNull("focus_score")) {
                        json.optInt("focus_score")
                    } else {
                        null
                    },
                focusLabel =
                    json.optString("focus_label")
                        .takeIf { it.isNotBlank() },
                focusReady = json.optBoolean("focus_ready", false),
                focusSide =
                    json.optString("focus_side")
                        .takeIf { it.isNotBlank() },
                instruction =
                    json.optString("instruction")
                        .takeIf { it.isNotBlank() },
                errorFromOptimumPx = nullableDouble("error_from_optimum_px"),
                geometryConfidence = nullableDouble("geometry_confidence")
            )
        }
    }
}
