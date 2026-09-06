package fr.stellarpilot.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit


data class MountSyncResult(
    val status: String,
    val coordinateProperty: String?,
    val targetFrame: String?,
    val mountRaHours: Double?,
    val mountDecDeg: Double?,
    val detail: String?
)


class MountSyncApiClient {

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

    suspend fun sync(
        serverBaseUrl: String,
        raDeg: Double,
        decDeg: Double
    ): MountSyncResult =
        withContext(Dispatchers.IO) {
            val url =
                serverBaseUrl.trimEnd('/') + "/mount/sync"

            val payload =
                JSONObject()
                    .put("ra_deg", raDeg)
                    .put("dec_deg", decDeg)
                    .toString()
                    .toRequestBody(
                        "application/json; charset=utf-8".toMediaType()
                    )

            val request =
                Request.Builder()
                    .url(url)
                    .header("Connection", "close")
                    .post(payload)
                    .build()

            client.newCall(request)
                .execute()
                .use { response ->
                    check(response.isSuccessful) {
                        "HTTP ${response.code} sur /mount/sync"
                    }

                    val body = response.body?.string()
                        ?: error("Réponse /mount/sync vide")
                    val json = JSONObject(body)

                    fun nullableDouble(key: String): Double? {
                        if (!json.has(key) || json.isNull(key)) return null
                        return json.optDouble(key, Double.NaN)
                            .takeUnless { it.isNaN() }
                    }

                    MountSyncResult(
                        status = json.optString("status", "error"),
                        coordinateProperty =
                            json.optString("coordinate_property")
                                .takeIf { it.isNotBlank() },
                        targetFrame =
                            json.optString("target_frame")
                                .takeIf { it.isNotBlank() },
                        mountRaHours = nullableDouble("mount_ra_hours"),
                        mountDecDeg = nullableDouble("mount_dec_deg"),
                        detail =
                            json.optString("detail")
                                .takeIf { it.isNotBlank() }
                    )
                }
        }
}
