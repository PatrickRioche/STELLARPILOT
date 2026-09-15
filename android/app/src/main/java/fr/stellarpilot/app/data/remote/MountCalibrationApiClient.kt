package fr.stellarpilot.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit


data class MountCalibrationTarget(
    val status: String,
    val strategy: String?,
    val raJ2000Hours: Double,
    val decJ2000Deg: Double,
    val altitudeDeg: Double,
    val azimuthDeg: Double,
    val hourAngleHours: Double,
    val locationSource: String?,
    val timeSource: String?
)


class MountCalibrationApiClient {

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

    suspend fun target(
        serverBaseUrl: String
    ): MountCalibrationTarget =
        withContext(Dispatchers.IO) {
            val url =
                serverBaseUrl.trimEnd('/') +
                    "/mount/calibration-target"

            val request =
                Request.Builder()
                    .url(url)
                    .header("Connection", "close")
                    .get()
                    .build()

            client.newCall(request)
                .execute()
                .use { response ->
                    val body = response.body?.string()
                        ?: error("Réponse /mount/calibration-target vide")

                    if (!response.isSuccessful) {
                        val detail = try {
                            JSONObject(body).optString("detail")
                        } catch (_: Exception) {
                            body
                        }
                        error(
                            detail.ifBlank {
                                "HTTP ${response.code} sur /mount/calibration-target"
                            }
                        )
                    }

                    val json = JSONObject(body)
                    val status = json.optString("status", "error")
                    if (status != "ready") {
                        error(
                            json.optString(
                                "detail",
                                "Cible de calibration indisponible"
                            )
                        )
                    }

                    MountCalibrationTarget(
                        status = status,
                        strategy =
                            json.optString("strategy")
                                .takeIf { it.isNotBlank() },
                        raJ2000Hours = json.getDouble("ra_j2000_hours"),
                        decJ2000Deg = json.getDouble("dec_j2000_deg"),
                        altitudeDeg = json.getDouble("altitude_deg"),
                        azimuthDeg = json.getDouble("azimuth_deg"),
                        hourAngleHours = json.getDouble("hour_angle_hours"),
                        locationSource =
                            json.optString("location_source")
                                .takeIf { it.isNotBlank() },
                        timeSource =
                            json.optString("time_source")
                                .takeIf { it.isNotBlank() }
                    )
                }
        }
}
