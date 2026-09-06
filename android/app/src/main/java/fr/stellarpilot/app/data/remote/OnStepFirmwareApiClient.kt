package fr.stellarpilot.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit


data class OnStepFirmwareInfo(
    val status: String,
    val mount: String?,
    val number: String?,
    val name: String?,
    val date: String?,
    val time: String?,
    val detail: String?
)


class OnStepFirmwareApiClient(
    private val baseUrl: String,
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
) {

    suspend fun getStatus(): OnStepFirmwareInfo =
        withContext(Dispatchers.IO) {
            val path = "/mount/firmware"
            val request =
                Request.Builder()
                    .url(
                        baseUrl.trimEnd('/') +
                            path +
                            "?t=" +
                            System.currentTimeMillis()
                    )
                    .header("Connection", "close")
                    .get()
                    .build()

            client.newCall(request)
                .execute()
                .use { response ->
                    check(response.isSuccessful) {
                        "HTTP ${response.code} sur $path"
                    }

                    val body = response.body?.string()
                        ?: error("Réponse $path vide")
                    val root = JSONObject(body)

                    OnStepFirmwareInfo(
                        status = root.optString(
                            "status",
                            "unavailable"
                        ),
                        mount = root.nullableFirmwareText("mount"),
                        number = root.nullableFirmwareText("number"),
                        name = root.nullableFirmwareText("name"),
                        date = root.nullableFirmwareText("date"),
                        time = root.nullableFirmwareText("time"),
                        detail = root.nullableFirmwareText("detail")
                    )
                }
        }
}


private fun JSONObject.nullableFirmwareText(
    key: String
): String? =
    if (!has(key) || isNull(key)) {
        null
    } else {
        optString(key)
            .takeIf { it.isNotBlank() }
    }
