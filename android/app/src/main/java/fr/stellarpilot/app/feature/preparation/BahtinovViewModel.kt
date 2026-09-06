package fr.stellarpilot.app.feature.preparation

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.stellarpilot.app.data.remote.AssistantReferenceApiClient
import fr.stellarpilot.app.data.remote.BahtinovApiClient
import fr.stellarpilot.app.data.remote.CameraPreviewApiClient
import fr.stellarpilot.app.domain.model.SkyStar
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate


enum class BahtinovReferenceLabel(
    val storageValue: String,
    val displayName: String
) {
    VERY_BAD("very_bad", "Très mauvais"),
    BAD("bad", "Mauvais"),
    MEDIUM("medium", "Moyen"),
    GOOD("good", "Bon"),
    OPTIMUM("optimum", "Optimum"),
    BAD_OTHER_SIDE("bad_other_side", "Mauvais autre côté"),
    IGNORE("ignore", "Ignorer")
}


data class BahtinovUiState(
    val isLoading: Boolean = false,
    val imageBytes: ByteArray? = null,
    val capturePath: String? = null,
    val exposureSeconds: Double = 4.0,
    val lastLabel: BahtinovReferenceLabel? = null,
    val referenceCount: Int = 0,
    val journalPath: String? = null,
    val focusScore: Int? = null,
    val focusLabel: String? = null,
    val focusReady: Boolean = false,
    val focusValidated: Boolean = false,
    val focusSide: String? = null,
    val focusErrorPx: Double? = null,
    val focusConfidence: Double? = null,
    val focusInstruction: String? = null,
    val optimumStreak: Int = 0,
    val isReferenceTest: Boolean = false,
    val referenceName: String? = null,
    val message: String? = null,
    val error: String? = null
)


class BahtinovViewModel(
    application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "StellarBahtinov"
        const val FOCUS_EXPOSURE_SECONDS = 4.0
    }

    var uiState by mutableStateOf(BahtinovUiState())
        private set

    fun setExposure(seconds: Double) {
        uiState = uiState.copy(
            exposureSeconds = seconds.coerceIn(0.01, 10.0)
        )
    }

    fun resetFocus() {
        uiState = BahtinovUiState()
    }

    fun loadReference(
        serverBaseUrl: String,
        kind: String
    ) {
        if (uiState.isLoading) return

        uiState = uiState.copy(
            isLoading = true,
            exposureSeconds = FOCUS_EXPOSURE_SECONDS,
            isReferenceTest = true,
            message = "Lecture du référentiel Bahtinov…",
            error = null
        )

        viewModelScope.launch {
            try {
                val result = AssistantReferenceApiClient().bahtinov(
                    serverBaseUrl = serverBaseUrl,
                    kind = kind
                )
                val nextStreak = if (result.focusReady) uiState.optimumStreak + 1 else 0
                val validated = nextStreak >= 2
                val message = when {
                    validated ->
                        "Optimum référentiel validé sur 2 lectures consécutives."
                    result.focusReady ->
                        "Optimum détecté. Rejouez Optimum une seconde fois pour confirmer la chaîne."
                    kind == "side_a" ->
                        "Côté A détecté • ajuster vers l'optimum."
                    kind == "side_b" ->
                        "Côté B détecté • revenir vers l'optimum."
                    else -> result.instruction ?: "Référence analysée"
                }

                uiState = uiState.copy(
                    isLoading = false,
                    imageBytes = result.previewBytes,
                    capturePath = result.imagePath,
                    exposureSeconds = FOCUS_EXPOSURE_SECONDS,
                    focusScore = result.focusScore,
                    focusLabel = result.focusLabel,
                    focusReady = result.focusReady,
                    focusValidated = validated,
                    focusSide = result.focusSide,
                    focusErrorPx = result.focusErrorPx,
                    focusConfidence = result.geometryConfidence,
                    focusInstruction = result.instruction,
                    optimumStreak = nextStreak,
                    isReferenceTest = true,
                    referenceName = result.name,
                    message = message,
                    error = null
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    focusReady = false,
                    focusValidated = false,
                    optimumStreak = 0,
                    error = error.message ?: "Lecture Bahtinov du référentiel impossible"
                )
            }
        }
    }

    /**
     * Normal Assistant focus capture. Exposure is deliberately fixed at 4 s
     * so scores stay comparable from one adjustment to the next.
     */
    fun captureFocus(serverBaseUrl: String) {
        if (uiState.isLoading) return

        uiState = uiState.copy(
            isLoading = true,
            exposureSeconds = FOCUS_EXPOSURE_SECONDS,
            isReferenceTest = false,
            referenceName = null,
            message = "Pose Bahtinov 4 s…",
            error = null
        )

        viewModelScope.launch {
            try {
                val cameraApi = CameraPreviewApiClient()
                val capture = cameraApi.capture(
                    serverBaseUrl = serverBaseUrl,
                    exposureSeconds = FOCUS_EXPOSURE_SECONDS
                )
                val preview = cameraApi.getPreview(serverBaseUrl)
                val focus = BahtinovApiClient().analyze(
                    serverBaseUrl = serverBaseUrl,
                    imagePath = capture.imagePath
                )

                val nextStreak = if (focus.focusReady) uiState.optimumStreak + 1 else 0
                val validated = nextStreak >= 2

                val message = when {
                    validated ->
                        "Mise au point optimale validée sur 2 poses consécutives."
                    focus.focusReady ->
                        "Optimum détecté. Faites une seconde pose de 4 s pour confirmer."
                    else ->
                        focus.instruction ?: "Ajustez la mise au point puis recommencez."
                }

                Log.i(
                    TAG,
                    "FOCUS capture=${capture.imagePath} score=${focus.focusScore} " +
                        "label=${focus.focusLabel} side=${focus.focusSide} " +
                        "error_px=${focus.errorFromOptimumPx} streak=$nextStreak"
                )

                uiState = uiState.copy(
                    isLoading = false,
                    imageBytes = preview,
                    capturePath = capture.imagePath,
                    exposureSeconds = capture.exposureSeconds,
                    focusScore = focus.focusScore,
                    focusLabel = focus.focusLabel,
                    focusReady = focus.focusReady,
                    focusValidated = validated,
                    focusSide = focus.focusSide,
                    focusErrorPx = focus.errorFromOptimumPx,
                    focusConfidence = focus.geometryConfidence,
                    focusInstruction = focus.instruction,
                    optimumStreak = nextStreak,
                    isReferenceTest = false,
                    referenceName = null,
                    message = message,
                    error = null
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    focusReady = false,
                    focusValidated = false,
                    optimumStreak = 0,
                    error = error.message ?: "Analyse Bahtinov impossible"
                )
            }
        }
    }

    /**
     * Calibration/reference capture retained for building future libraries.
     * Unlike normal focusing, this mode can keep its chosen exposure.
     */
    fun captureReference(
        serverBaseUrl: String,
        star: SkyStar?,
        mountRaHours: Double?,
        mountDecDeg: Double?,
        label: BahtinovReferenceLabel
    ) {
        if (uiState.isLoading) return

        uiState = uiState.copy(
            isLoading = true,
            message = null,
            error = null
        )

        viewModelScope.launch {
            try {
                val api = CameraPreviewApiClient()
                val exposure = uiState.exposureSeconds
                val capture = api.capture(
                    serverBaseUrl = serverBaseUrl,
                    exposureSeconds = exposure
                )
                val preview = api.getPreview(serverBaseUrl)
                val quality = runCatching {
                    api.analyzeQuality(
                        serverBaseUrl = serverBaseUrl,
                        imagePath = capture.imagePath
                    )
                }.getOrNull()

                val timestamp = Instant.now().toString()
                val record = JSONObject()
                    .put("timestamp_utc", timestamp)
                    .put("capture_path", capture.imagePath)
                    .put("exposure_s", capture.exposureSeconds)
                    .put("camera", capture.camera)
                    .put("label", label.storageValue)
                    .put("label_fr", label.displayName)
                    .put("target_id", star?.id)
                    .put("target_name", star?.name)
                    .put("target_ra_hours", star?.raHours)
                    .put("target_dec_deg", star?.decDeg)
                    .put("mount_ra_hours", mountRaHours)
                    .put("mount_dec_deg", mountDecDeg)
                    .put("tracking_mode", "sidereal")
                    .put("quality_status", quality?.status)
                    .put("quality_classification", quality?.classification)
                    .put("quality_score", quality?.score)
                    .put("quality_label", quality?.qualityLabel)
                    .put("star_count", quality?.starCount)
                    .put("saturated_percent", quality?.saturatedPercent)
                    .put("recommended_exposure_factor", quality?.recommendedExposureFactor)

                val journal = persistReference(record)

                Log.i(
                    TAG,
                    "REFERENCE label=${label.storageValue} " +
                        "capture=${capture.imagePath} exposure=$exposure " +
                        "star=${star?.name ?: "unknown"} journal=${journal.absolutePath}"
                )

                uiState = uiState.copy(
                    isLoading = false,
                    imageBytes = preview,
                    capturePath = capture.imagePath,
                    lastLabel = label,
                    referenceCount = uiState.referenceCount +
                        if (label == BahtinovReferenceLabel.IGNORE) 0 else 1,
                    journalPath = journal.absolutePath,
                    message = if (label == BahtinovReferenceLabel.IGNORE) {
                        "Capture marquée à ignorer"
                    } else {
                        "Référence « ${label.displayName} » enregistrée"
                    },
                    error = null
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    error = error.message ?: "Capture Bahtinov impossible"
                )
            }
        }
    }

    private fun persistReference(record: JSONObject): File {
        val application = getApplication<Application>()
        val rootParent = application.getExternalFilesDir(null) ?: application.filesDir
        val root = File(rootParent, "bahtinov-references")
        root.mkdirs()

        val file = File(root, "${LocalDate.now()}_references.jsonl")
        file.appendText(record.toString() + "\n", Charsets.UTF_8)
        return file
    }
}
