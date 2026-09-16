package fr.stellarpilot.app.feature.preparation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.stellarpilot.app.data.remote.MountHomeApiClient
import kotlinx.coroutines.launch


data class MountHomeUiState(
    val isLoading: Boolean = false,
    val homeSet: Boolean = false,
    val message: String? = null,
    val error: String? = null
)


class MountHomeViewModel : ViewModel() {

    var uiState by mutableStateOf(MountHomeUiState())
        private set

    fun reset() {
        uiState = MountHomeUiState()
    }

    fun setHome(
        serverBaseUrl: String,
        solvedDecDeg: Double
    ) {
        if (uiState.isLoading || uiState.homeSet) return

        uiState = uiState.copy(
            isLoading = true,
            message = "Vérification heure/GPS puis initialisation HOME OnStep…",
            error = null
        )

        viewModelScope.launch {
            try {
                val result = MountHomeApiClient()
                    .setCurrentPhysicalHome(
                        serverBaseUrl = serverBaseUrl,
                        solvedDecDeg = solvedDecDeg
                    )

                uiState = uiState.copy(
                    isLoading = false,
                    homeSet = result.status == "home_set",
                    message = if (result.status == "home_set") {
                        "HOME OnStep initialisé ✓ • position GPS synchronisée • aucun SYNC effectué près du pôle"
                    } else {
                        result.detail ?: result.note
                    },
                    error = null
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    homeSet = false,
                    message = null,
                    error = error.message ?: "Initialisation HOME impossible"
                )
            }
        }
    }
}
