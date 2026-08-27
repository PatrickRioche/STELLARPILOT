package fr.stellarpilot.app.feature.sky

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.stellarpilot.app.data.remote.StellarPilotApiClient
import fr.stellarpilot.app.domain.model.SkyObserver
import fr.stellarpilot.app.domain.model.SkyStar
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
    fun setQueryLocation(
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

    fun loadDemoSnapshot() {
        val capella = SkyStar(
            id = "capella",
            name = "Capella",
            constellation = "Cocher",
            objectType = "star",
            magnitude = 0.08,
            raHours = 5.2782,
            decDeg = 45.998,
            altitudeDeg = 55.13,
            azimuthDeg = 287.31,
            azimuthDirection = "W",
            aboveHorizon = true,
            alignmentCandidate = true,
            alignmentScore = 0.6834
        )

        val procyon = SkyStar(
            id = "procyon",
            name = "Procyon",
            constellation = "Petit Chien",
            objectType = "star",
            magnitude = 0.34,
            raHours = 7.655,
            decDeg = 5.225,
            altitudeDeg = 45.58,
            azimuthDeg = 203.29,
            azimuthDirection = "SW",
            aboveHorizon = true,
            alignmentCandidate = true,
            alignmentScore = 0.6494
        )

        val regulus = SkyStar(
            id = "regulus",
            name = "Regulus",
            constellation = "Lion",
            objectType = "star",
            magnitude = 1.35,
            raHours = 10.1395,
            decDeg = 11.9672,
            altitudeDeg = 50.35,
            azimuthDeg = 146.45,
            azimuthDirection = "SE",
            aboveHorizon = true,
            alignmentCandidate = true,
            alignmentScore = 0.5328
        )

        val betelgeuse = SkyStar(
            id = "betelgeuse",
            name = "Betelgeuse",
            constellation = "Orion",
            objectType = "star",
            magnitude = 0.5,
            raHours = 5.9195,
            decDeg = 7.4071,
            altitudeDeg = 36.31,
            azimuthDeg = 235.70,
            azimuthDirection = "SW",
            aboveHorizon = true,
            alignmentCandidate = true,
            alignmentScore = 0.5153
        )

        overrideLatitude = 47.4308
        overrideLongitude = -0.6271

        uiState = SkyUiState(
            isLoading = false,
            sky = SkyStatus(
                status = "ok",
                observer = SkyObserver(
                    latitude = 47.4308,
                    longitude = -0.6271,
                    timestampUtc = "2026-08-26T10:27:35.800145+00:00",
                    locationSource = "demo"
                ),
                catalogCount = 4,
                aboveHorizonCount = 4,
                alignmentCandidateCount = 4,
                recommended = capella,
                stars = listOf(
                    capella,
                    procyon,
                    regulus,
                    betelgeuse
                )
            ),
            error = null
        )
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