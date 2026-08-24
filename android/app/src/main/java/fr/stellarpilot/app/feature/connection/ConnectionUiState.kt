package fr.stellarpilot.app.feature.connection

import fr.stellarpilot.app.domain.model.CatalogStatus
import fr.stellarpilot.app.domain.model.ServerStatus

data class ConnectionUiState(
    val backendMode: String,
    val serverBaseUrl: String,
    val isConnecting: Boolean = false,
    val server: ServerStatus? = null,
    val catalog: CatalogStatus? = null,
    val restStatus: String = "Non connecté",
    val webSocketStatus: String = "Non connecté",
    val error: String? = null
)
