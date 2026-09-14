package fr.stellarpilot.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit


class MountSessionApiClient(
    private val baseUrl: String
) {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    suspend fun prepare() = withContext(Dispatchers.IO) {
        val body =
            "{}".toRequestBody(
                "application/json; charset=utf-8".toMediaType()
            )

        val request =
            Request.Builder()
                .url(endpoint("mount/session/prepare"))
                .post(body)
                .build()

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "HTTP ${response.code} sur /mount/session/prepare"
            }

            val responseBody =
                response.body?.string()
                    ?: error(
                        "Réponse /mount/session/prepare vide"
                    )

            val root = JSONObject(responseBody)
            val status = root.optString("status", "error")

            if (status != "ready") {
                error(
                    root.optString(
                        "detail",
                        "Préparation de la monture impossible"
                    )
                )
            }
        }
    }

    private fun endpoint(path: String): String =
        baseUrl.trimEnd('/') + "/" + path.trimStart('/')
}
