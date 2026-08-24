package fr.stellarpilot.app.feature.connection

import fr.stellarpilot.app.domain.model.CatalogStatus
import fr.stellarpilot.app.domain.model.ServerStatus

/**
 * ?tat technique de la liaison entre l'application Android
 * et StellarPilot Server.
 *
 * Cet ?tat est ind?pendant de l'?tat des p?riph?riques INDI,
 * du GPS ou de la cam?ra. Un p?riph?rique indisponible ne doit
 * jamais ?tre interpr?t? comme une perte du serveur.
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    STOPPED
}

data class ConnectionUiState(
    val backendMode: String,
    val serverBaseUrl: String,
    val connectionState: ConnectionState =
        ConnectionState.DISCONNECTED,
    val isConnecting: Boolean = false,
    val server: ServerStatus? = null,
    val catalog: CatalogStatus? = null,
    val restStatus: String = "Non connect?",
    val webSocketStatus: String = "Non connect?",
    val reconnectAttempt: Int = 0,
    val reconnectDelaySeconds: Long? = null,
    val error: String? = null
)
