package fr.stellarpilot.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit


data class ServerBuildInfo(
    val service: String,
    val version: String,
    val buildTimestamp: String?,
    val gitSha: String?,
    val branch: String?,
    val dirty: Boolean?
)


class ServerBuildApiClient {

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    suspend fun load(
        serverBaseUrl: String
    ): ServerBuildInfo =
        withContext(Dispatchers.IO) {

            val request =
                Request.Builder()
                    .url(
                        serverBaseUrl.trimEnd('/') +
                            "/build"
                    )
                    .header("Connection", "close")
                    .get()
                    .build()

            client.newCall(request)
                .execute()
                .use { response ->

                    check(response.isSuccessful) {
                        "HTTP ${response.code} sur /build"
                    }

                    val body =
                        response.body?.string()
                            ?: error(
                                "R\u00e9ponse /build vide"
                            )

                    val json = JSONObject(body)

                    fun nullableText(
                        key: String
                    ): String? {
                        if (
                            !json.has(key) ||
                            json.isNull(key)
                        ) {
                            return null
                        }

                        return json
                            .optString(key)
                            .takeIf {
                                it.isNotBlank() &&
                                    !it.equals(
                                        "null",
                                        ignoreCase = true
                                    )
                            }
                    }

                    val dirty =
                        if (
                            json.has("dirty") &&
                            !json.isNull("dirty")
                        ) {
                            json.optBoolean(
                                "dirty",
                                false
                            )
                        } else {
                            null
                        }

                    ServerBuildInfo(
                        service =
                            json.optString(
                                "service",
                                "stellarpilot-server"
                            ),
                        version =
                            json.optString(
                                "version",
                                "unknown"
                            ),
                        buildTimestamp =
                            nullableText(
                                "build_timestamp"
                            ),
                        gitSha =
                            nullableText(
                                "git_sha"
                            ),
                        branch =
                            nullableText(
                                "branch"
                            ),
                        dirty = dirty
                    )
                }
        }
}
