package fr.stellarpilot.app.feature.preparation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.stellarpilot.app.data.remote.DarkCalibrationApiClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


data class DarkCalibrationUiState(
    val isLoading: Boolean = false,
    val sessionId: String? = null,
    val exposureSeconds: Double = 4.0,
    val requestedCount: Int = 20,
    val capturedCount: Int = 0,
    val validCount: Int = 0,
    val complete: Boolean = false,
    val storage: String? = null,
    val cameraName: String? = null,
    val gain: Double? = null,
    val offset: Double? = null,
    val binX: Int? = null,
    val binY: Int? = null,
    val temperatureC: Double? = null,
    val bayerPattern: String? = null,
    val opticalTrainName: String? = null,
    val telescopeName: String? = null,
    val telescopeType: String? = null,
    val apertureMm: Double? = null,
    val focalLengthMm: Double? = null,
    val focalRatio: Double? = null,
    val reducer: Double? = null,
    val masterDarkPath: String? = null,
    val masterMethod: String? = null,
    val hotPixelCount: Int? = null,
    val hotPixelMapPath: String? = null,
    val message: String? = null,
    val error: String? = null
)


class DarkCalibrationViewModel : ViewModel() {

    companion object {
        const val DARK_EXPOSURE_SECONDS = 4.0
        const val DARK_COUNT = 20

        private const val BETWEEN_DARKS_DELAY_MS = 1_000L
        private const val RETRY_DELAY_MS = 1_500L
        private const val MAX_ATTEMPTS_PER_DARK = 3
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
            message = "Préparation de la série de $DARK_COUNT darks…"
        )

        viewModelScope.launch {

            try {

                val api = DarkCalibrationApiClient()

                var status = api.start(
                    serverBaseUrl = serverBaseUrl,
                    exposureSeconds = DARK_EXPOSURE_SECONDS,
                    count = DARK_COUNT
                )

                val sessionId = status.id

                fun applyStatus(
                    loading: Boolean,
                    message: String?
                ) {
                    uiState = uiState.copy(
                        isLoading = loading,
                        sessionId = sessionId,
                        exposureSeconds = status.exposureSeconds,
                        requestedCount = status.requestedCount,
                        capturedCount = status.capturedCount,
                        validCount = status.validCount,
                        complete = status.status == "complete",
                        storage = status.storage,
                        cameraName = status.cameraName,
                        gain = status.gain,
                        offset = status.offset,
                        binX = status.binX,
                        binY = status.binY,
                        temperatureC = status.temperatureC,
                        bayerPattern = status.bayerPattern,
                        opticalTrainName = status.opticalTrainName,
                        telescopeName = status.telescopeName,
                        telescopeType = status.telescopeType,
                        apertureMm = status.apertureMm,
                        focalLengthMm = status.focalLengthMm,
                        focalRatio = status.focalRatio,
                        reducer = status.reducer,
                        masterDarkPath = status.masterDarkPath,
                        masterMethod = status.masterMethod,
                        hotPixelCount = status.hotPixelCount,
                        hotPixelMapPath = status.hotPixelMapPath,
                        message = message,
                        error = null
                    )
                }

                applyStatus(
                    loading = true,
                    message =
                        "Série automatique démarrée • " +
                            "${status.capturedCount}/${status.requestedCount}"
                )

                while (
                    status.status != "complete" &&
                    status.capturedCount < status.requestedCount
                ) {

                    val next = status.capturedCount + 1
                    var captured = false
                    var lastError: Exception? = null

                    for (
                        attempt in 1..MAX_ATTEMPTS_PER_DARK
                    ) {

                        uiState = uiState.copy(
                            isLoading = true,
                            message =
                                if (attempt == 1) {
                                    "Dark $next/${status.requestedCount} • pose 4 s…"
                                } else {
                                    "Dark $next/${status.requestedCount} • tentative $attempt/$MAX_ATTEMPTS_PER_DARK…"
                                },
                            error = null
                        )

                        try {

                            status = api.capture(
                                serverBaseUrl = serverBaseUrl,
                                sessionId = sessionId
                            )

                            captured = true
                            lastError = null
                            break

                        } catch (error: Exception) {

                            lastError = error

                            if (
                                attempt <
                                MAX_ATTEMPTS_PER_DARK
                            ) {
                                uiState = uiState.copy(
                                    isLoading = true,
                                    message =
                                        "Dark $next/${status.requestedCount} • erreur transitoire • nouvelle tentative…",
                                    error = error.message
                                )

                                delay(RETRY_DELAY_MS)
                            }
                        }
                    }

                    if (!captured) {
                        throw (
                            lastError
                                ?: IllegalStateException(
                                    "Capture dark impossible"
                                )
                        )
                    }

                    val complete =
                        status.status == "complete"

                    applyStatus(
                        loading = !complete,
                        message =
                            if (complete) {
                                "Master Dark créé • ${status.validCount}/${status.requestedCount} valides • ${status.hotPixelCount ?: 0} pixels chauds détectés ✓"
                            } else {
                                "Dark ${status.capturedCount}/${status.requestedCount} enregistré ✓"
                            }
                    )

                    if (!complete) {
                        delay(
                            BETWEEN_DARKS_DELAY_MS
                        )
                    }
                }

                uiState = uiState.copy(
                    isLoading = false
                )

            } catch (error: Exception) {

                uiState = uiState.copy(
                    isLoading = false,
                    message =
                        "Série interrompue • ${uiState.capturedCount}/${uiState.requestedCount}",
                    error =
                        error.message
                            ?: "Série automatique de darks interrompue"
                )
            }
        }
    }
}
