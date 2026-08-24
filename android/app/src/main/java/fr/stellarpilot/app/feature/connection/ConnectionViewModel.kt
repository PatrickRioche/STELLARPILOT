package fr.stellarpilot.app.feature.connection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import fr.stellarpilot.app.BuildConfig
import fr.stellarpilot.app.data.remote.StellarPilotApiClient
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class ConnectionViewModel : ViewModel() {
    private var api = StellarPilotApiClient(BuildConfig.SERVER_BASE_URL)
    private var webSocket: WebSocket? = null

    var uiState by mutableStateOf(
        ConnectionUiState(
            backendMode = BuildConfig.BACKEND_MODE,
            serverBaseUrl = BuildConfig.SERVER_BASE_URL
        )
    )
        private set

    fun setServerAddress(address: String) {
        val baseUrl = normalizeServerUrl(address)

        if (baseUrl == null) {
            uiState = uiState.copy(
                error = "Adresse serveur invalide"
            )
            return
        }

        webSocket?.close(
            1000,
            "Server address changed"
        )
        webSocket = null

        api = StellarPilotApiClient(baseUrl)

        uiState = uiState.copy(
            serverBaseUrl = baseUrl,
            server = null,
            restStatus = "Non connectÃ©",
            webSocketStatus = "Non connectÃ©",
            error = null
        )

        connect()
    }

    private fun normalizeServerUrl(address: String): String? {
        var value = address.trim()

        if (value.isBlank()) {
            return null
        }

        value = value.trimEnd('/')

        if (!value.startsWith("http://", ignoreCase = true) &&
            !value.startsWith("https://", ignoreCase = true)
        ) {
            value = "http://$value"
        }

        return try {
            val uri = java.net.URI(value)

            val scheme = uri.scheme?.lowercase() ?: "http"
            val host = uri.host ?: return null

            if (scheme != "http" && scheme != "https") {
                return null
            }

            val port = if (uri.port == -1) 8000 else uri.port

            "$scheme://$host:$port/"
        } catch (_: Exception) {
            null
        }
    }

    fun connect() {
        if (uiState.isConnecting) return

        uiState = uiState.copy(
            isConnecting = true,
            restStatus = "Connexion...",
            error = null
        )

        viewModelScope.launch {

            // ------------------------------------------------
            // 1. Présence du serveur
            // ------------------------------------------------

            try {
                api.checkHealth()

                uiState = uiState.copy(
                    restStatus = "OK",
                    error = null
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isConnecting = false,
                    server = null,
                    restStatus = "Erreur",
                    error =
                        error.message
                            ?: "Serveur StellarPilot inaccessible"
                )

                return@launch
            }

            // ------------------------------------------------
            // 2. État détaillé du matériel
            // ------------------------------------------------

            try {
                val status = api.getStatus()

                uiState = uiState.copy(
                    isConnecting = false,
                    server = status,
                    restStatus = "OK",
                    error = null
                )

                if (webSocket == null) {
                    connectWebSocket()
                }
            } catch (error: Exception) {
                Log.e(
                    "StellarPilotStatus",
                    "Erreur pendant getStatus()/parseStatus()",
                    error
                )

                uiState = uiState.copy(
                    isConnecting = false,
                    restStatus = "OK",
                    error =
                        "Serveur connecté, mais lecture du matériel impossible : " +
                            (
                                error.message
                                    ?: "erreur inconnue"
                            )
                )
            }
        }
    }

    private fun connectWebSocket() {
        uiState = uiState.copy(webSocketStatus = "Connexion...")

        webSocket = api.openEvents(object : WebSocketListener() {
            override fun onOpen(
                webSocket: WebSocket,
                response: Response
            ) {
                viewModelScope.launch {
                    uiState = uiState.copy(
                        webSocketStatus = "Ouvert"
                    )
                }
            }

            override fun onMessage(
                webSocket: WebSocket,
                text: String
            ) {
                viewModelScope.launch {
                    uiState = uiState.copy(
                        webSocketStatus = text
                    )
                }
            }

            override fun onClosed(
                webSocket: WebSocket,
                code: Int,
                reason: String
            ) {
                this@ConnectionViewModel.webSocket = null

                viewModelScope.launch {
                    uiState = uiState.copy(
                        webSocketStatus = "FermÃƒÂ©"
                    )
                }
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?
            ) {
                this@ConnectionViewModel.webSocket = null

                viewModelScope.launch {
                    uiState = uiState.copy(
                        webSocketStatus = "Erreur",
                        error = t.message ?: "Erreur WebSocket"
                    )
                }
            }
        })
    }

    override fun onCleared() {
        webSocket?.close(1000, "ViewModel cleared")
        webSocket = null
        super.onCleared()
    }
}