package fr.stellarpilot.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit


data class MountClockStatus(
    val status: String,
    val mount: String?,
    val utc: String?,
    val offsetHours: Double?,
    val referenceUtc: String?,
    val referenceSource: String?,
    val driftSeconds: Double?,
    val synchronized: Boolean?,
    val synchronization: String?,
    val detail: String?
)


class MountTimeApiClient(
    private val baseUrl: String,
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
) {

    suspend fun getStatus(): MountClockStatus =
        withContext(Dispatchers.IO) {
            val request =
                Request.Builder()
                    .url(
                        baseUrl.trimEnd('/') +
                            "/mount/time?t=" +
                            System.currentTimeMillis()
                    )
                    .header("Connection", "close")
                    .get()
                    .build()

            client.newCall(request)
                .execute()
                .use { response ->
                    check(response.isSuccessful) {
                        "HTTP ${response.code} sur /mount/time"
                    }

                    val body = response.body?.string()
                        ?: error("Réponse /mount/time vide")
                    val root = JSONObject(body)

                    fun nullableString(key: String): String? =
                        if (!root.has(key) || root.isNull(key)) {
                            null
                        } else {
                            root.optString(key)
                                .takeIf { it.isNotBlank() }
                        }

                    fun nullableDouble(key: String): Double? =
                        if (!root.has(key) || root.isNull(key)) {
                            null
                        } else {
                            root.optDouble(key, Double.NaN)
                                .takeUnless { it.isNaN() }
                        }

                    fun nullableBoolean(key: String): Boolean? =
                        if (!root.has(key) || root.isNull(key)) {
                            null
                        } else {
                            root.optBoolean(key)
                        }

                    MountClockStatus(
                        status = root.optString(
                            "status",
                            "unavailable"
                        ),
                        mount = nullableString("mount"),
                        utc = nullableString("utc"),
                        offsetHours = nullableDouble("offset_hours"),
                        referenceUtc = nullableString("reference_utc"),
                        referenceSource = nullableString("reference_source"),
                        driftSeconds = nullableDouble("drift_seconds"),
                        synchronized = nullableBoolean("synchronized"),
                        synchronization = nullableString("synchronization"),
                        detail = nullableString("detail")
                    )
                }
        }
}
