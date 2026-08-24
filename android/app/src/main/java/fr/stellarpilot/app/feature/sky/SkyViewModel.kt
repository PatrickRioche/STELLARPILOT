package fr.stellarpilot.app.feature.sky

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.stellarpilot.app.data.remote.StellarPilotApiClient
import fr.stellarpilot.app.domain.model.SkyStatus
import kotlinx.coroutines.launch

data class SkyUiState(
    val isLoading: Boolean = false,
    val sky: SkyStatus? = null,
    val error: String? = null
)

class SkyViewModel : ViewModel() {

    private var overrideLatitude: Double? =
        null

    private var overrideLongitude: Double? =
        null

    var uiState by mutableStateOf(
        SkyUiState()
    )
        private set

    fun setManualLocation(
        serverBaseUrl: String,
        latitude: Double,
        longitude: Double
    ) {
        overrideLatitude =
            latitude

        overrideLongitude =
            longitude

        if (uiState.isLoading) return

        uiState = uiState.copy(
            isLoading = true,
            error = null
        )

        viewModelScope.launch {
            try {
                val api =
                    StellarPilotApiClient(
                        serverBaseUrl
                    )

                api.setManualLocation(
                    latitude = latitude,
                    longitude = longitude
                )

                val sky =
                    api.getBrightStars(
                        latitude =
                            latitude,
                        longitude =
                            longitude
                    )

                uiState = uiState.copy(
                    isLoading = false,
                    sky = sky,
                    error = null
                )

            } catch (error: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    error =
                        error.message
                            ?: "Erreur de position"
                )
            }
        }
    }
    fun load(serverBaseUrl: String) {
        if (uiState.isLoading) return

        uiState = uiState.copy(
            isLoading = true,
            error = null
        )

        viewModelScope.launch {
            try {
                val api =
                    StellarPilotApiClient(
                        serverBaseUrl
                    )

                val sky =
                    api.getBrightStars(
                        latitude =
                            overrideLatitude,
                        longitude =
                            overrideLongitude
                    )

                uiState = uiState.copy(
                    isLoading = false,
                    sky = sky,
                    error = null
                )

            } catch (error: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    error =
                        error.message
                            ?: "Erreur Ciel inconnue"
                )
            }
        }
    }
}