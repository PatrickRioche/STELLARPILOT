package fr.stellarpilot.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit


data class MountHomeResult(
    val status: String,
    val detail: String?,
    val mount: String?,
    val latitude: Double?,
    val longitude: Double?,
    val trackingStopped: Boolean,
    val note: String?
)


class MountHomeApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun setCurrentPhysicalHome(
        serverBaseUrl: String,
        solvedDecDeg: Double
    ): MountHomeResult = withContext(Dispatchers.IO) {
        val url = serverBaseUrl.trimEnd('/') + "/mount/home/set"
        val payload = JSONObject()
            .put("confirmed_physical_home", true)
            .put("pole_solve_dec_deg", solvedDecDeg)

        val request = Request.Builder()
            .url(url)
            .header("Connection", "close")
            .post(
                payload.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
            )
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
                ?: error("Réponse /mount/home/set vide")
            val json = JSONObject(body)
            val status = json.optString("status", "error")
            val detail = json.optString("detail").takeIf { it.isNotBlank() }

            if (!response.isSuccessful || status != "home_set") {
                error(
                    detail
                        ?: json.optString("note")
                            .takeIf { it.isNotBlank() }
                        ?: "Initialisation HOME OnStep refusée"
                )
            }

            val location = json.optJSONObject("location")
            MountHomeResult(
                status = status,
                detail = detail,
                mount = json.optString("mount").takeIf { it.isNotBlank() },
                latitude = location?.optDouble("latitude")
                    ?.takeIf { !it.isNaN() },
                longitude = location?.optDouble("longitude")
                    ?.takeIf { !it.isNaN() },
                trackingStopped = json.optBoolean("tracking_stopped", false),
                note = json.optString("note").takeIf { it.isNotBlank() }
            )
        }
    }
}
