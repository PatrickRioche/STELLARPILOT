package fr.stellarpilot.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit


data class AssistantReferenceAstrometryResult(
    val name: String,
    val imagePath: String,
    val previewBytes: ByteArray,
    val qualityScore: Int?,
    val qualityLabel: String?,
    val qualityClassification: String?,
    val starCount: Int?,
    val saturatedPercent: Double?,
    val solveStatus: String?,
    val solver: String?,
    val ra: Double?,
    val dec: Double?,
    val orientationDeg: Double?,
    val pixelScaleArcsec: Double?,
    val solveDetail: String?
)


data class AssistantReferenceBahtinovResult(
    val kind: String,
    val name: String,
    val imagePath: String,
    val previewBytes: ByteArray,
    val focusScore: Int?,
    val focusLabel: String?,
    val focusReady: Boolean,
    val focusSide: String?,
    val focusErrorPx: Double?,
    val geometryConfidence: Double?,
    val instruction: String?
)


class AssistantReferenceApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(130, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun astrometry(
        serverBaseUrl: String,
        index: Int = 0
    ): AssistantReferenceAstrometryResult = withContext(Dispatchers.IO) {
        val base = serverBaseUrl.trimEnd('/')
        val url = "$base/assistant/test/reference/astrometry?index=$index"
        val json = getJson(url)
        val imagePath = json.getString("image")
        val quality = json.optJSONObject("quality") ?: JSONObject()
        val solve = json.optJSONObject("solve") ?: JSONObject()
        val preview = getPreview(base, imagePath)

        AssistantReferenceAstrometryResult(
            name = json.optString("name", imagePath.substringAfterLast('/')),
            imagePath = imagePath,
            previewBytes = preview,
            qualityScore = nullableInt(quality, "astrometry_score"),
            qualityLabel = quality.optString("quality_label").takeIf { it.isNotBlank() },
            qualityClassification = quality.optString("classification").takeIf { it.isNotBlank() },
            starCount = nullableInt(quality, "star_count"),
            saturatedPercent = nullableDouble(quality, "saturated_percent"),
            solveStatus = solve.optString("status").takeIf { it.isNotBlank() },
            solver = solve.optString("solver").takeIf { it.isNotBlank() },
            ra = nullableDouble(solve, "ra"),
            dec = nullableDouble(solve, "dec"),
            orientationDeg = nullableDouble(solve, "orientation_deg"),
            pixelScaleArcsec = nullableDouble(solve, "pixel_scale_arcsec"),
            solveDetail = solve.optString("detail").takeIf { it.isNotBlank() }
        )
    }

    suspend fun bahtinov(
        serverBaseUrl: String,
        kind: String,
        index: Int = 0
    ): AssistantReferenceBahtinovResult = withContext(Dispatchers.IO) {
        val base = serverBaseUrl.trimEnd('/')
        val url = "$base/assistant/test/reference/bahtinov/$kind?index=$index"
        val json = getJson(url)
        val imagePath = json.getString("image")
        val analysis = json.optJSONObject("analysis") ?: JSONObject()
        val preview = getPreview(base, imagePath)

        AssistantReferenceBahtinovResult(
            kind = json.optString("kind", kind),
            name = json.optString("name", imagePath.substringAfterLast('/')),
            imagePath = imagePath,
            previewBytes = preview,
            focusScore = nullableInt(analysis, "focus_score"),
            focusLabel = analysis.optString("focus_label").takeIf { it.isNotBlank() },
            focusReady = analysis.optBoolean("focus_ready", false),
            focusSide = analysis.optString("focus_side").takeIf { it.isNotBlank() },
            focusErrorPx = nullableDouble(analysis, "error_from_optimum_px"),
            geometryConfidence = nullableDouble(analysis, "geometry_confidence"),
            instruction = analysis.optString("instruction").takeIf { it.isNotBlank() }
        )
    }

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("Connection", "close")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "HTTP ${response.code} sur ${request.url.encodedPath}"
            }
            val body = response.body?.string() ?: error("Réponse vide")
            return JSONObject(body)
        }
    }

    private fun getPreview(base: String, imagePath: String): ByteArray {
        val url = "$base/assistant/test/reference/preview.jpg"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("image", imagePath)
            .addQueryParameter("t", System.currentTimeMillis().toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Connection", "close")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "HTTP ${response.code} sur /assistant/test/reference/preview.jpg"
            }
            return response.body?.bytes() ?: error("Aperçu référentiel vide")
        }
    }

    private fun nullableDouble(json: JSONObject, key: String): Double? {
        if (!json.has(key) || json.isNull(key)) return null
        return json.optDouble(key, Double.NaN).takeUnless { it.isNaN() }
    }

    private fun nullableInt(json: JSONObject, key: String): Int? {
        if (!json.has(key) || json.isNull(key)) return null
        return json.optInt(key)
    }
}
