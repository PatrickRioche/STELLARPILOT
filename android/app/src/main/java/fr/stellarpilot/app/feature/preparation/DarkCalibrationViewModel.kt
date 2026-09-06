package fr.stellarpilot.app.feature.preparation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.stellarpilot.app.data.remote.DarkCalibrationApiClient
import kotlinx.coroutines.launch


data class DarkCalibrationUiState(
    val isLoading: Boolean = false,
    val sessionId: String? = null,
    val exposureSeconds: Double = 4.0,
    val requestedCount: Int = 10,
    val capturedCount: Int = 0,
    val validCount: Int = 0,
    val complete: Boolean = false,
    val storage: String? = null,
    val message: String? = null,
    val error: String? = null
)


class DarkCalibrationViewModel : ViewModel() {
    companion object {
        const val DARK_EXPOSURE_SECONDS = 4.0
        const val DARK_COUNT = 10
    }

    var uiState by mutableStateOf(DarkCalibrationUiState())
        private set

    fun reset() {
        uiState = DarkCalibrationUiState()
    }

    fun start(serverBaseUrl: String) {
        if (uiState.isLoading) return
        uiState = DarkCalibrationUiState(
            isLoading = true,
            message = "Préparation de la série de darks…"
        )

        viewModelScope.launch {
            try {
                val status = DarkCalibrationApiClient().start(
                    serverBaseUrl = serverBaseUrl,
                    exposureSeconds = DARK_EXPOSURE_SECONDS,
                    count = DARK_COUNT
                )
                uiState = uiState.copy(
                    isLoading = false,
                    sessionId = status.id,
                    exposureSeconds = status.exposureSeconds,
                    requestedCount = status.requestedCount,
                    capturedCount = status.capturedCount,
                    validCount = status.validCount,
                    complete = status.status == "complete",
                    storage = status.storage,
                    message = "Série prête • bouchon posé • lancer la première pose de 4 s",
                    error = null
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    error = error.message ?: "Création de la série de darks impossible"
                )
            }
        }
    }

    fun captureNext(serverBaseUrl: String) {
        val sessionId = uiState.sessionId ?: return
        if (uiState.isLoading || uiState.complete) return

        val next = uiState.capturedCount + 1
        uiState = uiState.copy(
            isLoading = true,
            message = "Dark $next/${uiState.requestedCount} • pose 4 s…",
            error = null
        )

        viewModelScope.launch {
            try {
                val status = DarkCalibrationApiClient().capture(
                    serverBaseUrl = serverBaseUrl,
                    sessionId = sessionId
                )
                val complete = status.status == "complete"
                uiState = uiState.copy(
                    isLoading = false,
                    exposureSeconds = status.exposureSeconds,
                    requestedCount = status.requestedCount,
                    capturedCount = status.capturedCount,
                    validCount = status.validCount,
                    complete = complete,
                    storage = status.storage,
                    message =
                        if (complete) {
                            "Darks terminés • ${status.validCount}/${status.requestedCount} valides ✓"
                        } else {
                            "Dark ${status.capturedCount}/${status.requestedCount} enregistré • continuer"
                        },
                    error = null
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    error = error.message ?: "Capture dark impossible"
                )
            }
        }
    }
}
