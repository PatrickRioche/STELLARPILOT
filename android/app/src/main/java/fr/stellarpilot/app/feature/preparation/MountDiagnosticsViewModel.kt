package fr.stellarpilot.app.feature.preparation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.stellarpilot.app.data.remote.MountDiagnosticsApiClient
import fr.stellarpilot.app.data.remote.MountDiagnosticsResult
import fr.stellarpilot.app.data.remote.MountGotoCommandClient
import fr.stellarpilot.app.domain.model.SkyStar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


data class MountDiagnosticsUiState(
    val isLoading: Boolean = false,
    val status: MountDiagnosticsResult? = null,
    val actionLabel: String? = null,
    val error: String? = null
)


class MountDiagnosticsViewModel : ViewModel() {

    var uiState by mutableStateOf(MountDiagnosticsUiState())
        private set

    fun refresh(serverBaseUrl: String) {
        if (uiState.isLoading) return

        uiState = uiState.copy(
            isLoading = true,
            error = null
        )

        viewModelScope.launch {
            try {
                val status =
                    MountDiagnosticsApiClient().status(serverBaseUrl)

                uiState = uiState.copy(
                    isLoading = false,
                    status = status,
                    error = null
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    error = error.message ?: "État monture indisponible"
                )
            }
        }
    }

    fun nudge(
        serverBaseUrl: String,
        deltaRaHours: Double = 0.0,
        deltaDecDeg: Double = 0.0,
        label: String
    ) {
        val current = uiState.status
        val ra = current?.raHours
        val dec = current?.decDeg

        if (ra == null || dec == null) {
            uiState = uiState.copy(
                error = "Actualisez d'abord la position de la monture."
            )
            return
        }

        runGoto(
            serverBaseUrl = serverBaseUrl,
            raHours = (ra + deltaRaHours + 24.0) % 24.0,
            decDeg = (dec + deltaDecDeg).coerceIn(-89.5, 89.5),
            trackingMode = "sidereal",
            label = label
        )
    }

    fun gotoStar(
        serverBaseUrl: String,
        star: SkyStar
    ) {
        runGoto(
            serverBaseUrl = serverBaseUrl,
            raHours = star.raHours,
            decDeg = star.decDeg,
            trackingMode = "sidereal",
            label = "Pointage ${star.name}"
        )
    }

    private fun runGoto(
        serverBaseUrl: String,
        raHours: Double,
        decDeg: Double,
        trackingMode: String,
        label: String
    ) {
        if (uiState.isLoading) return

        uiState = uiState.copy(
            isLoading = true,
            actionLabel = label,
            error = null
        )

        viewModelScope.launch {
            try {
                val base = serverBaseUrl.trimEnd('/') + "/"
                MountGotoCommandClient(base).gotoMount(
                    raHours = raHours,
                    decDeg = decDeg,
                    trackingMode = trackingMode
                )

                var lastStatus: MountDiagnosticsResult? = null

                repeat(15) {
                    delay(700)
                    lastStatus = MountDiagnosticsApiClient().status(base)

                    val state = lastStatus?.status?.lowercase()
                    if (state == "tracking" || state == "idle") {
                        return@repeat
                    }
                }

                uiState = uiState.copy(
                    isLoading = false,
                    status = lastStatus,
                    actionLabel = null,
                    error = null
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    actionLabel = null,
                    error = error.message ?: "Commande monture impossible"
                )
            }
        }
    }
}
