package fr.stellarpilot.app.feature.preparation

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    val exposureSeconds: Double = 1.0,
    val lastLabel: BahtinovReferenceLabel? = null,
    val referenceCount: Int = 0,
    val journalPath: String? = null,
    val message: String? = null,
    val error: String? = null
)


class BahtinovViewModel(
    application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "StellarBahtinov"
    }

    var uiState by mutableStateOf(BahtinovUiState())
        private set

    fun setExposure(seconds: Double) {
        uiState = uiState.copy(
            exposureSeconds = seconds.coerceIn(0.01, 10.0)
        )
    }

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
                    .put(
                        "recommended_exposure_factor",
                        quality?.recommendedExposureFactor
                    )

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
                    message =
                        if (label == BahtinovReferenceLabel.IGNORE) {
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
        val rootParent = application.getExternalFilesDir(null)
            ?: application.filesDir
        val root = File(
            rootParent,
            "bahtinov-references"
        )
        root.mkdirs()

        val file = File(
            root,
            "${LocalDate.now()}_references.jsonl"
        )

        file.appendText(
            record.toString() + "\n",
            Charsets.UTF_8
        )

        return file
    }
}
