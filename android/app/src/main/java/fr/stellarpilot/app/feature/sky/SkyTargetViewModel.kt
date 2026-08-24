package fr.stellarpilot.app.feature.sky

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.stellarpilot.app.data.remote.SkyObjectsApiClient
import fr.stellarpilot.app.domain.model.SkyObject
import fr.stellarpilot.app.domain.model.SkyObjectsResult
import kotlinx.coroutines.launch

data class SkyTargetUiState(
    val isLoading: Boolean = false,
    val result: SkyObjectsResult? = null,
    val selected: SkyObject? = null,
    val error: String? = null
)

class SkyTargetViewModel : ViewModel() {

    private var observerLatitude: Double? = null
    private var observerLongitude: Double? = null

    var uiState by mutableStateOf(
        SkyTargetUiState()
    )
        private set

    fun load(
        serverBaseUrl: String,
        category: String,
        query: String,
        minAltitude: Double = 15.0,
        direction: String? = null,
        constellation: String = "",
        latitude: Double? = null,
        longitude: Double? = null
    ) {

        if (uiState.isLoading) return

        if (
            latitude != null &&
            longitude != null
        ) {
            observerLatitude = latitude
            observerLongitude = longitude
        }

        val currentLatitude =
            observerLatitude

        val currentLongitude =
            observerLongitude

        if (
            currentLatitude == null ||
            currentLongitude == null
        ) {
            uiState =
                uiState.copy(
                    isLoading = false,
                    error =
                        "Position observateur indisponible"
                )

            return
        }

        uiState =
            uiState.copy(
                isLoading = true,
                error = null
            )

        viewModelScope.launch {

            try {

                val result =
                    SkyObjectsApiClient(
                        serverBaseUrl
                    ).getObjects(
                        latitude =
                            currentLatitude,
                        longitude =
                            currentLongitude,
                        category = category,
                        query = query,
                        minAltitude = minAltitude,
                        direction = direction,
                        constellation = constellation,
                        limit = 30
                    )

                uiState =
                    uiState.copy(
                        isLoading = false,
                        result = result,
                        error = null
                    )

            } catch (
                error: Exception
            ) {

                uiState =
                    uiState.copy(
                        isLoading = false,
                        error =
                            error.message
                                ?: "Erreur catalogue"
                    )
            }
        }
    }

    fun select(
        target: SkyObject
    ) {

        uiState =
            uiState.copy(
                selected = target
            )
    }
}