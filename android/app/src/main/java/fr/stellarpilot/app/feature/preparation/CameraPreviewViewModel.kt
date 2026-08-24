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

class CameraPreviewViewModel : ViewModel() {

    var uiState by mutableStateOf(
        CameraPreviewUiState()
    )
        private set

    fun load(
        serverBaseUrl: String,
        exposureSeconds: Double
    ) {
        if (uiState.isLoading) return

        uiState = uiState.copy(
            isLoading = true,
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
