package fr.stellarpilot.app.feature.connection

import fr.stellarpilot.app.domain.model.CatalogStatus
import fr.stellarpilot.app.domain.model.ServerStatus

/**
 * État technique de la liaison entre l'application Android
 * et StellarPilot Server.
 *
 * Cet état est indépendant de l'état des périphériques INDI,
 * du GPS ou de la caméra. Un périphérique indisponible ne doit
 * jamais être interprété comme une perte du serveur.
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
    val restStatus: String = "Non connecté",
    val webSocketStatus: String = "Non connecté",
    val reconnectAttempt: Int = 0,
    val reconnectDelaySeconds: Long? = null,
    val error: String? = null
)
