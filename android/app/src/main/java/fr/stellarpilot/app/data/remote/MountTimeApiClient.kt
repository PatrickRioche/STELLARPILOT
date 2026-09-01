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
    val indiState: String?,
    val detail: String?
)


data class TimeSourceStatus(
    val available: Boolean,
    val status: String,
    val utc: String?,
    val driftSeconds: Double?,
    val synchronized: Boolean?,
    val trustedReference: Boolean,
    val timezoneOffsetMinutes: Int? = null,
    val offsetHours: Double? = null,
    val indiState: String? = null,
    val synchronization: String? = null,
    val readbackKind: String? = null,
    val detail: String? = null
)


data class TimeSynchronizationStatus(
    val status: String,
    val toleranceSeconds: Double,
    val referenceSource: String?,
    val referenceUtc: String?,
    val gps: TimeSourceStatus,
    val android: TimeSourceStatus,
    val raspberryPi: TimeSourceStatus,
    val onStep: TimeSourceStatus,
    val note: String?
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
            val root = getJson("/mount/time")

            MountClockStatus(
                status = root.optString(
                    "status",
                    "unavailable"
                ),
                mount = root.nullableString("mount"),
                utc = root.nullableString("utc"),
                offsetHours = root.nullableDouble("offset_hours"),
                referenceUtc = root.nullableString("reference_utc"),
                referenceSource = root.nullableString("reference_source"),
                driftSeconds = root.nullableDouble("drift_seconds"),
                synchronized = root.nullableBoolean("synchronized"),
                synchronization = root.nullableString("synchronization"),
                indiState = root.nullableString("indi_state"),
                detail = root.nullableString("detail")
            )
        }

    suspend fun getSynchronizationStatus(): TimeSynchronizationStatus =
        withContext(Dispatchers.IO) {
            val root = getJson("/time/synchronization")
            val sources = root.optJSONObject("sources")
                ?: JSONObject()

            fun parseSource(name: String): TimeSourceStatus {
                val source = sources.optJSONObject(name)
                    ?: JSONObject()

                return TimeSourceStatus(
                    available = source.optBoolean(
                        "available",
                        false
                    ),
                    status = source.optString(
                        "status",
                        "unavailable"
                    ),
                    utc = source.nullableString("utc"),
                    driftSeconds = source.nullableDouble(
                        "drift_seconds"
                    ),
                    synchronized = source.nullableBoolean(
                        "synchronized"
                    ),
                    trustedReference = source.optBoolean(
                        "trusted_reference",
                        false
                    ),
                    timezoneOffsetMinutes = source.nullableInt(
                        "timezone_offset_minutes"
                    ),
                    offsetHours = source.nullableDouble(
                        "offset_hours"
                    ),
                    indiState = source.nullableString(
                        "indi_state"
                    ),
                    synchronization = source.nullableString(
                        "synchronization"
                    ),
                    readbackKind = source.nullableString(
                        "readback_kind"
                    ),
                    detail = source.nullableString("detail")
                )
            }

            TimeSynchronizationStatus(
                status = root.optString(
                    "status",
                    "unverified"
                ),
                toleranceSeconds = root.optDouble(
                    "tolerance_seconds",
                    10.0
                ),
                referenceSource = root.nullableString(
                    "reference_source"
                ),
                referenceUtc = root.nullableString(
                    "reference_utc"
                ),
                gps = parseSource("gps"),
                android = parseSource("android"),
                raspberryPi = parseSource("raspberry_pi"),
                onStep = parseSource("onstep"),
                note = root.nullableString("note")
            )
        }

    private fun getJson(path: String): JSONObject {
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

                return JSONObject(body)
            }
    }
}


private fun JSONObject.nullableString(key: String): String? =
    if (!has(key) || isNull(key)) {
        null
    } else {
        optString(key)
            .takeIf { it.isNotBlank() }
    }


private fun JSONObject.nullableDouble(key: String): Double? =
    if (!has(key) || isNull(key)) {
        null
    } else {
        optDouble(key, Double.NaN)
            .takeUnless { it.isNaN() }
    }


private fun JSONObject.nullableInt(key: String): Int? =
    if (!has(key) || isNull(key)) {
        null
    } else {
        optInt(key)
    }


private fun JSONObject.nullableBoolean(key: String): Boolean? =
    if (!has(key) || isNull(key)) {
        null
    } else {
        optBoolean(key)
    }
