package fr.stellarpilot.app.feature.preparation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.stellarpilot.app.data.remote.CameraPreviewApiClient
import fr.stellarpilot.app.data.remote.DetectedStar
import kotlinx.coroutines.launch

data class CameraPreviewUiState(
    val isLoading: Boolean = false,
    val imageBytes: ByteArray? = null,
    val capturePath: String? = null,
    val exposureSeconds: Double? = null,
    val solveStatus: String? = null,
    val solver: String? = null,
    val ra: Double? = null,
    val dec: Double? = null,
    val orientationDeg: Double? = null,
    val pixelScaleArcsec: Double? = null,
    val detectedStarCount: Int? = null,
    val detectedStars: List<DetectedStar> = emptyList(),
    val solveDetail: String? = null,
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


class CameraPreviewViewModel : ViewModel() {

    var uiState by mutableStateOf(
        CameraPreviewUiState()
    )
        private set

    var demoM103State by mutableStateOf(
        DemoM103UiState()
    )
        private set

    fun resetM103() {
        demoM103State = DemoM103UiState()
    }

    fun runDemoM103(
        serverBaseUrl: String
    ) {
        demoM103State =
            DemoM103UiState(
                isLoading = false,
                isDisplayed = true,
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

    fun runServerM103(
        serverBaseUrl: String
    ) {
        if (demoM103State.isLoading) return

        demoM103State =
            DemoM103UiState(
                isLoading = true,
                isDisplayed = true,
                solveStatus = "loading"
            )

        viewModelScope.launch {
            try {
                val api = CameraPreviewApiClient()

                val image =
                    api.getDemoM103Preview(
                        serverBaseUrl
                    )

                demoM103State =
                    demoM103State.copy(
                        imageBytes = image,
                        isDisplayed = true,
                        solveStatus = "solving",
                        solver = "astrometry.net",
                        error = null
                    )

                val solution =
                    api.solveDemoM103(
                        serverBaseUrl
                    )

                demoM103State =
                    demoM103State.copy(
                        isLoading = false,
                        isDisplayed = true,
                        solveStatus = solution.status,
                        solver = solution.solver,
                        ra = solution.ra,
                        dec = solution.dec,
                        orientationDeg = solution.orientationDeg,
                        pixelScaleArcsec = solution.pixelScaleArcsec,
                        solveDurationMs = solution.solveDurationMs,
                        error = null
                    )

            } catch (error: Exception) {
                demoM103State =
                    demoM103State.copy(
                        isLoading = false,
                        isDisplayed = true,
                        solveStatus = "error",
                        error =
                            "${error::class.java.simpleName}: ${error.message}"
                    )
            }
        }
    }

    fun load(
        serverBaseUrl: String,
        exposureSeconds: Double
    ) {
        if (uiState.isLoading) return

        demoM103State =
            demoM103State.copy(
                isDisplayed = false
            )

        uiState = uiState.copy(
            isLoading = true,
            imageBytes = null,
            capturePath = null,
            exposureSeconds = exposureSeconds,
            solveStatus = null,
            solver = null,
            ra = null,
            dec = null,
            orientationDeg = null,
            pixelScaleArcsec = null,
            detectedStarCount = null,
            detectedStars = emptyList(),
            solveDetail = null,
            error = null
        )

        viewModelScope.launch {
            try {
                val api = CameraPreviewApiClient()

                val capture =
                    api.capture(
                        serverBaseUrl,
                        exposureSeconds
                    )

                val image =
                    api.getPreview(
                        serverBaseUrl
                    )

                uiState = uiState.copy(
                    imageBytes = image,
                    capturePath = capture.imagePath,
                    exposureSeconds = capture.exposureSeconds,
                    solveStatus = "solving",
                    solver = "astrometry.net",
                    error = null
                )

                /*
                 * Laisser Compose afficher la capture avant
                 * de lancer la résolution astrométrique longue.
                 */
                kotlinx.coroutines.yield()
                kotlinx.coroutines.delay(100)

                val solution =
                    api.solve(
                        serverBaseUrl,
                        capture.imagePath
                    )

                uiState = uiState.copy(
                    isLoading = false,
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

            } catch (error: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    solveStatus = "error",
                    error =
                        "${error::class.java.simpleName}: ${error.message}"
                )
            }
        }
    }
}
