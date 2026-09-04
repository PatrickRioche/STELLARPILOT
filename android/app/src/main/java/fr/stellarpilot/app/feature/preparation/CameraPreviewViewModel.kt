package fr.stellarpilot.app.feature.preparation

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.stellarpilot.app.R
import fr.stellarpilot.app.data.remote.AssistantReferenceApiClient
import fr.stellarpilot.app.data.remote.CameraPreviewApiClient
import fr.stellarpilot.app.data.remote.CameraQualityResult
import fr.stellarpilot.app.data.remote.DetectedStar
import fr.stellarpilot.app.data.remote.MountSyncApiClient
import kotlinx.coroutines.launch
import kotlin.math.roundToInt


data class CameraPreviewUiState(
    val isLoading: Boolean = false,
    val imageBytes: ByteArray? = null,
    val capturePath: String? = null,
    val exposureSeconds: Double? = null,
    val qualityScore: Int? = null,
    val qualityLabel: String? = null,
    val qualityClassification: String? = null,
    val qualityStarCount: Int? = null,
    val qualitySaturatedPercent: Double? = null,
    val recommendedExposureFactor: Double? = null,
    val suggestedExposureMs: List<Int> = emptyList(),
    val solveStatus: String? = null,
    val solver: String? = null,
    val ra: Double? = null,
    val dec: Double? = null,
    val orientationDeg: Double? = null,
    val pixelScaleArcsec: Double? = null,
    val detectedStarCount: Int? = null,
    val detectedStars: List<DetectedStar> = emptyList(),
    val solveDetail: String? = null,
    val mountSyncStatus: String? = null,
    val mountSyncDetail: String? = null,
    val isReferenceTest: Boolean = false,
    val referenceName: String? = null,
    val error: String? = null
)


data class DemoM103UiState(
    val isLoading: Boolean = false,
    val isDisplayed: Boolean = false,
    val imageBytes: ByteArray? = null,
    val solveStatus: String? = null,
    val solver: String? = null,
    val ra: Double? = null,
    val dec: Double? = null,
    val orientationDeg: Double? = null,
    val pixelScaleArcsec: Double? = null,
    val solveDurationMs: Int? = null,
    val error: String? = null
)


class CameraPreviewViewModel(
    application: Application
) : AndroidViewModel(application) {

    var uiState by mutableStateOf(CameraPreviewUiState())
        private set

    var demoM103State by mutableStateOf(DemoM103UiState())
        private set

    fun resetM103() {
        demoM103State = DemoM103UiState()
    }

    @Suppress("UNUSED_PARAMETER")
    fun runDemoM103(serverBaseUrl: String) {
        val imageBytes = try {
            getApplication<Application>()
                .resources
                .openRawResource(R.drawable.m103_preview)
                .use { input -> input.readBytes() }
        } catch (error: Exception) {
            demoM103State = DemoM103UiState(
                isLoading = false,
                isDisplayed = true,
                solveStatus = "error",
                error = "Image M103 locale indisponible: ${error.message}"
            )
            return
        }

        demoM103State = DemoM103UiState(
            isLoading = false,
            isDisplayed = true,
            imageBytes = imageBytes,
            solveStatus = "solved",
            solver = "astrometry.net - resultat de reference",
            ra = 23.287695,
            dec = 60.600901,
            orientationDeg = -67.5544,
            pixelScaleArcsec = 1.3227,
            solveDurationMs = 1980,
            error = null
        )
    }

    fun loadReference(
        serverBaseUrl: String,
        index: Int = 0
    ) {
        if (uiState.isLoading) return

        uiState = CameraPreviewUiState(
            isLoading = true,
            exposureSeconds = 4.0,
            solveStatus = "reference_loading",
            mountSyncStatus = "simulated",
            mountSyncDetail = "Aucun mouvement ni SYNC envoyé à OnStep en mode référentiel",
            isReferenceTest = true
        )

        viewModelScope.launch {
            try {
                val result = AssistantReferenceApiClient().astrometry(
                    serverBaseUrl = serverBaseUrl,
                    index = index
                )

                uiState = uiState.copy(
                    isLoading = false,
                    imageBytes = result.previewBytes,
                    capturePath = result.imagePath,
                    exposureSeconds = 4.0,
                    qualityScore = result.qualityScore,
                    qualityLabel = result.qualityLabel,
                    qualityClassification = result.qualityClassification,
                    qualityStarCount = result.starCount,
                    qualitySaturatedPercent = result.saturatedPercent,
                    solveStatus = result.solveStatus,
                    solver = result.solver,
                    ra = result.ra,
                    dec = result.dec,
                    orientationDeg = result.orientationDeg,
                    pixelScaleArcsec = result.pixelScaleArcsec,
                    solveDetail = result.solveDetail,
                    mountSyncStatus = if (result.solveStatus == "solved") "simulated" else null,
                    mountSyncDetail = if (result.solveStatus == "solved") {
                        "Plate solving réel sur FITS sauvegardé • SYNC OnStep simulé"
                    } else {
                        "Le FITS de référence n'a pas été résolu"
                    },
                    isReferenceTest = true,
                    referenceName = result.name,
                    error = null
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    solveStatus = "error",
                    error = error.message ?: "Lecture du référentiel impossible"
                )
            }
        }
    }

    fun load(
        serverBaseUrl: String,
        exposureSeconds: Double
    ) {
        if (uiState.isLoading) return

        demoM103State = demoM103State.copy(isDisplayed = false)

        uiState = uiState.copy(
            isLoading = true,
            imageBytes = null,
            capturePath = null,
            exposureSeconds = exposureSeconds,
            qualityScore = null,
            qualityLabel = null,
            qualityClassification = null,
            qualityStarCount = null,
            qualitySaturatedPercent = null,
            recommendedExposureFactor = null,
            suggestedExposureMs = emptyList(),
            solveStatus = null,
            solver = null,
            ra = null,
            dec = null,
            orientationDeg = null,
            pixelScaleArcsec = null,
            detectedStarCount = null,
            detectedStars = emptyList(),
            solveDetail = null,
            mountSyncStatus = null,
            mountSyncDetail = null,
            isReferenceTest = false,
            referenceName = null,
            error = null
        )

        viewModelScope.launch {
            try {
                val api = CameraPreviewApiClient()
                val capture = api.capture(serverBaseUrl, exposureSeconds)
                val image = api.getPreview(serverBaseUrl)

                uiState = uiState.copy(
                    imageBytes = image,
                    capturePath = capture.imagePath,
                    exposureSeconds = capture.exposureSeconds,
                    solveStatus = "quality_check",
                    error = null
                )

                kotlinx.coroutines.yield()
                kotlinx.coroutines.delay(100)

                val quality = api.analyzeQuality(serverBaseUrl, capture.imagePath)
                val suggestions = suggestedExposures(
                    currentExposureSeconds = capture.exposureSeconds,
                    quality = quality
                )

                uiState = uiState.copy(
                    qualityScore = quality.score,
                    qualityLabel = quality.qualityLabel,
                    qualityClassification = quality.classification,
                    qualityStarCount = quality.starCount,
                    qualitySaturatedPercent = quality.saturatedPercent,
                    recommendedExposureFactor = quality.recommendedExposureFactor,
                    suggestedExposureMs = suggestions,
                    solveStatus = if (quality.astrometryReady) "solving" else "quality_insufficient",
                    solveDetail = if (quality.astrometryReady) null else
                        "Qualité insuffisante pour lancer automatiquement astrometry.net"
                )

                if (!quality.astrometryReady) {
                    uiState = uiState.copy(isLoading = false, solver = null, error = null)
                    return@launch
                }

                kotlinx.coroutines.yield()
                kotlinx.coroutines.delay(100)

                val solution = api.solve(serverBaseUrl, capture.imagePath)

                uiState = uiState.copy(
                    solveStatus = solution.status,
                    solver = solution.solver,
                    ra = solution.ra,
                    dec = solution.dec,
                    orientationDeg = solution.orientationDeg,
                    pixelScaleArcsec = solution.pixelScaleArcsec,
                    detectedStarCount = solution.detectedStarCount,
                    detectedStars = solution.detectedStars,
                    solveDetail = solution.detail,
                    error = null
                )

                if (
                    solution.status == "solved" &&
                    solution.ra != null &&
                    solution.dec != null
                ) {
                    uiState = uiState.copy(
                        solveStatus = "syncing_mount",
                        mountSyncStatus = "syncing",
                        mountSyncDetail = "Synchronisation OnStep sur le centre astrométrique…"
                    )

                    val sync = MountSyncApiClient().sync(
                        serverBaseUrl = serverBaseUrl,
                        raDeg = solution.ra,
                        decDeg = solution.dec
                    )

                    if (sync.status == "synced") {
                        val frame = sync.targetFrame ?: "monture"
                        val property = sync.coordinateProperty ?: "INDI"
                        uiState = uiState.copy(
                            isLoading = false,
                            solveStatus = "solved",
                            mountSyncStatus = "synced",
                            mountSyncDetail = "Monture synchronisée • $property • $frame",
                            error = null
                        )
                    } else {
                        uiState = uiState.copy(
                            isLoading = false,
                            solveStatus = "sync_error",
                            mountSyncStatus = "error",
                            mountSyncDetail = sync.detail ?: "Synchronisation OnStep refusée",
                            error = null
                        )
                    }
                } else {
                    uiState = uiState.copy(isLoading = false)
                }
            } catch (error: Exception) {
                val solvedCoordinatesAvailable =
                    uiState.ra != null && uiState.dec != null && uiState.solver != null

                uiState = uiState.copy(
                    isLoading = false,
                    solveStatus = if (solvedCoordinatesAvailable) "sync_error" else "error",
                    mountSyncStatus = if (solvedCoordinatesAvailable) "error" else uiState.mountSyncStatus,
                    mountSyncDetail = if (solvedCoordinatesAvailable) {
                        "SYNC OnStep impossible : ${error.message}"
                    } else uiState.mountSyncDetail,
                    error = if (solvedCoordinatesAvailable) null else
                        "${error::class.java.simpleName}: ${error.message}"
                )
            }
        }
    }

    private fun suggestedExposures(
        currentExposureSeconds: Double,
        quality: CameraQualityResult
    ): List<Int> {
        val currentMs = (currentExposureSeconds * 1000.0)
            .roundToInt()
            .coerceIn(1, 10_000)

        val factor = quality.recommendedExposureFactor
            ?.coerceIn(0.1, 8.0)
            ?: 1.0

        val targetMs = (currentMs * factor)
            .roundToInt()
            .coerceIn(1, 10_000)

        val candidates = if (factor < 1.0) {
            listOf((targetMs * 0.5).roundToInt(), targetMs, currentMs)
        } else if (factor > 1.0) {
            listOf(currentMs, targetMs, (targetMs * 2.0).roundToInt())
        } else {
            listOf((currentMs * 0.5).roundToInt(), currentMs, (currentMs * 2.0).roundToInt())
        }

        return candidates
            .map { it.coerceIn(1, 10_000) }
            .distinct()
            .sorted()
    }
}
