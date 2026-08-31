package fr.stellarpilot.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit


class MountGotoCommandClient(
    private val baseUrl: String,
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
) {
    suspend fun gotoMount(
        raHours: Double,
        decDeg: Double,
        trackingMode: String
    ): String = withContext(Dispatchers.IO) {
        val payload =
            JSONObject()
                .put("ra", raHours)
                .put("dec", decDeg)
                .put(
                    "tracking_mode",
                    trackingMode
                )

        val body =
            payload
                .toString()
                .toRequestBody(
                    "application/json; charset=utf-8"
                        .toMediaType()
                )

        val request =
            Request.Builder()
                .url(
                    baseUrl.trimEnd('/') +
                        "/mount/goto"
                )
                .post(body)
                .build()

        client.newCall(request)
            .execute()
            .use { response ->
                check(response.isSuccessful) {
                    "HTTP ${response.code} sur /mount/goto"
                }

                val responseBody =
                    response.body
                        ?.string()
                        ?: error(
                            "Reponse /mount/goto vide"
                        )

                val result =
                    JSONObject(responseBody)

                val status =
                    result.optString(
                        "status",
                        "error"
                    )

                if (
                    status !in setOf(
                        "slewing",
                        "ok"
                    )
                ) {
                    error(
                        result.optString(
                            "detail",
                            "Echec du pointage"
                        )
                    )
                }

                status
            }
    }
}
