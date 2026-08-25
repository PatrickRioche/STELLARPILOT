package fr.stellarpilot.app.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CameraCaptureResult(
    val imagePath: String,
    val exposureSeconds: Double,
    val camera: String?
)

data class DetectedStar(
    val x: Double,
    val y: Double,
    val ra: Double? = null,
    val dec: Double? = null
)

data class PlateSolveResult(
    val status: String,
    val solver: String?,
    val ra: Double?,
    val dec: Double?,
    val orientationDeg: Double?,
    val pixelScaleArcsec: Double?,
    val detectedStarCount: Int?,
    val detectedStars: List<DetectedStar>,
    val detail: String?,
    val solveDurationMs: Int? = null
)

class CameraPreviewApiClient {

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    suspend fun capture(
        serverBaseUrl: String,
        exposureSeconds: Double
    ): CameraCaptureResult =
        withContext(Dispatchers.IO) {

            val url =
                serverBaseUrl.trimEnd('/') +
                    "/camera/capture"

            val payload =
                JSONObject()
                    .put(
                        "exposure_s",
                        exposureSeconds
                    )
                    .toString()
                    .toRequestBody(
                        "application/json; charset=utf-8"
                            .toMediaType()
                    )

            val request =
                Request.Builder()
                    .url(url)
                    .post(payload)
                    .build()

            Log.i(
                "StellarPreview",
                "CAPTURE $exposureSeconds s"
            )

            client.newCall(request)
                .execute()
                .use { response ->

                    check(response.isSuccessful) {
                        "HTTP ${response.code} sur /camera/capture"
                    }

                    val body =
                        response.body?.string()
                            ?: error(
                                "Réponse /camera/capture vide"
                            )

                    val json = JSONObject(body)

                    val status =
                        json.optString("status")

                    if (status != "captured") {
                        error(
                            json.optString(
                                "detail",
                                "Capture caméra impossible"
                            )
                        )
                    }

                    CameraCaptureResult(
                        imagePath =
                            json.getString("image"),

                        exposureSeconds =
                            json.optDouble(
                                "exposure_s",
                                exposureSeconds
                            ),

                        camera =
                            json.optString(
                                "camera"
                            ).takeIf {
                                it.isNotBlank()
                            }
                    )
                }
        }

    suspend fun getPreview(
        serverBaseUrl: String
    ): ByteArray =
        withContext(Dispatchers.IO) {

            val url =
                serverBaseUrl.trimEnd('/') +
                    "/camera/preview.jpg?t=" +
                    System.currentTimeMillis()

            val request =
                Request.Builder()
                    .url(url)
                    .get()
                    .build()

            Log.i(
                "StellarPreview",
                "PREVIEW $url"
            )

            client.newCall(request)
                .execute()
                .use { response ->

                    Log.i(
                        "StellarPreview",
                        "PREVIEW HTTP ${response.code}"
                    )

                    check(response.isSuccessful) {
                        "HTTP ${response.code} sur /camera/preview.jpg"
                    }

                    response.body?.bytes()
                        ?: error(
                            "Image caméra vide"
                        )
                }
        }

    suspend fun getDemoM103Preview(
        serverBaseUrl: String
    ): ByteArray =
        withContext(Dispatchers.IO) {

            val url =
                serverBaseUrl.trimEnd('/') +
                    "/demo/m103/preview.jpg?t=" +
                    System.currentTimeMillis()

            val request =
                Request.Builder()
                    .url(url)
                    .get()
                    .build()

            client.newCall(request)
                .execute()
                .use { response ->

                    check(response.isSuccessful) {
                        "HTTP ${response.code} sur /demo/m103/preview.jpg"
                    }

                    response.body?.bytes()
                        ?: error("Aperçu M103 vide")
                }
        }

    suspend fun solveDemoM103(
        serverBaseUrl: String
    ): PlateSolveResult =
        withContext(Dispatchers.IO) {

            val url =
                serverBaseUrl.trimEnd('/') +
                    "/demo/m103/solve"

            val request =
                Request.Builder()
                    .url(url)
                    .post(
                        ByteArray(0)
                            .toRequestBody(null)
                    )
                    .build()

            client.newCall(request)
                .execute()
                .use { response ->

                    check(response.isSuccessful) {
                        "HTTP ${response.code} sur /demo/m103/solve"
                    }

                    val body =
                        response.body?.string()
                            ?: error("Réponse démo M103 vide")

                    val json = JSONObject(body)

                    fun nullableDouble(key: String): Double? {
                        if (!json.has(key) || json.isNull(key)) {
                            return null
                        }

                        return json
                            .optDouble(key)
                            .takeUnless { it.isNaN() }
                    }

                    PlateSolveResult(
                        status =
                            json.optString(
                                "status",
                                "error"
                            ),

                        solver =
                            json.optString("solver")
                                .takeIf {
                                    it.isNotBlank()
                                },

                        ra =
                            nullableDouble("ra"),

                        dec =
                            nullableDouble("dec"),

                        orientationDeg =
                            nullableDouble(
                                "orientation_deg"
                            ),

                        pixelScaleArcsec =
                            nullableDouble(
                                "pixel_scale_arcsec"
                            ),

                        detectedStarCount = null,

                        detectedStars =
                            emptyList(),

                        detail =
                            json.optString("detail")
                                .takeIf {
                                    it.isNotBlank()
                                },

                        solveDurationMs =
                            if (
                                json.has("solve_duration_ms") &&
                                !json.isNull("solve_duration_ms")
                            ) {
                                json.optInt("solve_duration_ms")
                            } else {
                                null
                            }
                    )
                }
        }
    suspend fun solve(
        serverBaseUrl: String,
        imagePath: String
    ): PlateSolveResult =
        withContext(Dispatchers.IO) {

            val url =
                serverBaseUrl.trimEnd('/') +
                    "/solve"

            val payload =
                JSONObject()
                    .put(
                        "image",
                        imagePath
                    )
                    .toString()
                    .toRequestBody(
                        "application/json; charset=utf-8"
                            .toMediaType()
                    )

            val request =
                Request.Builder()
                    .url(url)
                    .post(payload)
                    .build()

            Log.i(
                "StellarPreview",
                "SOLVE $imagePath"
            )

            client.newCall(request)
                .execute()
                .use { response ->

                    check(response.isSuccessful) {
                        "HTTP ${response.code} sur /solve"
                    }

                    val body =
                        response.body?.string()
                            ?: error(
                                "Réponse /solve vide"
                            )

                    val json =
                        JSONObject(body)

                    fun nullableDouble(
                        key: String
                    ): Double? {

                        if (
                            !json.has(key) ||
                            json.isNull(key)
                        ) {
                            return null
                        }

                        return json
                            .optDouble(key)
                            .takeUnless {
                                it.isNaN()
                            }
                    }

                    val starsArray =
                        json.optJSONArray(
                            "detected_stars"
                        )

                    val detectedStars =
                        buildList {

                            if (starsArray != null) {

                                for (
                                    index in
                                    0 until starsArray.length()
                                ) {

                                    val star =
                                        starsArray
                                            .optJSONObject(index)
                                            ?: continue

                                    val x =
                                        star.optDouble(
                                            "x",
                                            Double.NaN
                                        )

                                    val y =
                                        star.optDouble(
                                            "y",
                                            Double.NaN
                                        )

                                    if (
                                        !x.isNaN() &&
                                        !y.isNaN()
                                    ) {

                                        val ra =
                                            if (
                                                star.has("ra") &&
                                                !star.isNull("ra")
                                            ) {
                                                star
                                                    .optDouble("ra")
                                                    .takeUnless {
                                                        it.isNaN()
                                                    }
                                            } else {
                                                null
                                            }

                                        val dec =
                                            if (
                                                star.has("dec") &&
                                                !star.isNull("dec")
                                            ) {
                                                star
                                                    .optDouble("dec")
                                                    .takeUnless {
                                                        it.isNaN()
                                                    }
                                            } else {
                                                null
                                            }

                                        add(
                                            DetectedStar(
                                                x = x,
                                                y = y,
                                                ra = ra,
                                                dec = dec
                                            )
                                        )
                                    }
                                }
                            }
                        }

                    val detectedStarCount =
                        when {

                            json.has(
                                "stars_detected"
                            ) &&
                                !json.isNull(
                                    "stars_detected"
                                ) -> {

                                json.optInt(
                                    "stars_detected"
                                )
                            }

                            starsArray != null -> {

                                detectedStars.size
                            }

                            else -> null
                        }

                    PlateSolveResult(
                        status =
                            json.optString(
                                "status",
                                "error"
                            ),

                        solver =
                            json.optString(
                                "solver"
                            ).takeIf {
                                it.isNotBlank()
                            },

                        ra =
                            nullableDouble(
                                "ra"
                            ),

                        dec =
                            nullableDouble(
                                "dec"
                            ),

                        orientationDeg =
                            nullableDouble(
                                "orientation_deg"
                            ),

                        pixelScaleArcsec =
                            nullableDouble(
                                "pixel_scale_arcsec"
                            ),

                        detectedStarCount =
                            detectedStarCount,

                        detectedStars =
                            detectedStars,

                        detail =
                            json.optString(
                                "detail"
                            ).takeIf {
                                it.isNotBlank()
                            }
                    )
                }
        }
}
