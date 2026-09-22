package fr.stellarpilot.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit


/**
 * Explicit field-test stacking entry point.
 *
 * This endpoint is used only when the target is visible but centering has not
 * been validated. The server keeps dark calibration, quality filtering,
 * registration and stacking active while disabling periodic astrometric
 * recentering, so this path can never command an automatic mount GOTO.
 */
class CaptureStackTestApiClient(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
) {
    suspend fun start(sessionId: String): CaptureSessionStatus =
        withContext(Dispatchers.IO) {
            val path = "capture/sessions/$sessionId/stack/start-test"
            val request = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/" + path)
                .header("Connection", "close")
                .post(
                    JSONObject()
                        .toString()
                        .toRequestBody(
                            "application/json; charset=utf-8".toMediaType()
                        )
                )
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                    ?: error("Réponse /$path vide")

                check(response.isSuccessful) {
                    "HTTP ${response.code} sur /$path"
                }

                val json = JSONObject(body)
                val status = json.optString("status", "error")
                if (status in setOf("error", "finalized")) {
                    error(
                        json.optString(
                            "detail",
                            "Démarrage du stacking test impossible"
                        )
                    )
                }
            }

            // Re-read through the canonical parser so Capture keeps exactly
            // the same session model in normal and field-test modes.
            CaptureSessionApiClient(baseUrl).getSession(sessionId)
        }
}
