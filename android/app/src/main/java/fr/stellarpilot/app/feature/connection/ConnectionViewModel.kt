package fr.stellarpilot.app.feature.connection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.stellarpilot.app.BuildConfig
import fr.stellarpilot.app.data.remote.StellarPilotApiClient
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class ConnectionViewModel : ViewModel() {
    private val api = StellarPilotApiClient(BuildConfig.SERVER_BASE_URL)
    private var webSocket: WebSocket? = null

    var uiState by mutableStateOf(
        ConnectionUiState(
            backendMode = BuildConfig.BACKEND_MODE,
            serverBaseUrl = BuildConfig.SERVER_BASE_URL
        )
    )
        private set

    fun connect() {
        if (uiState.isConnecting) return

        uiState = uiState.copy(
            isConnecting = true,
            restStatus = "Connexion...",
            error = null
        )

        viewModelScope.launch {
            try {
                val status = api.getStatus()

                uiState = uiState.copy(
                    isConnecting = false,
                    server = status,
                    restStatus = "OK"
                )

                if (webSocket == null) {
                    connectWebSocket()
                }
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isConnecting = false,
                    restStatus = "Erreur",
                    error = error.message ?: "Erreur de connexion inconnue"
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
                        webSocketStatus = "Fermé"
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