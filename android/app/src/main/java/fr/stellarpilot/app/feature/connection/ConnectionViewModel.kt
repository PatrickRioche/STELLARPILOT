package fr.stellarpilot.app.feature.connection

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.stellarpilot.app.BuildConfig
import fr.stellarpilot.app.data.remote.StellarPilotApiClient
import fr.stellarpilot.app.feature.demo.DemoModeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject


class ConnectionViewModel : ViewModel() {

    companion object {
        private const val TAG = "StellarPilotConnection"
        private val RECONNECT_DELAYS_SECONDS =
            longArrayOf(1L, 2L, 3L, 5L, 8L, 10L)
    }

    private var api =
        StellarPilotApiClient(
            BuildConfig.SERVER_BASE_URL
        )

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var handshakeTimeoutJob: Job? = null
    private var webSocketGeneration = 0L
    private var stopped = false
    private var destroyed = false

    var uiState by mutableStateOf(
        ConnectionUiState(
            backendMode = BuildConfig.BACKEND_MODE,
            serverBaseUrl = BuildConfig.SERVER_BASE_URL
        )
    )
        private set

    private val demoModeListener: (Boolean) -> Unit = { active ->
        viewModelScope.launch {
            if (active) {
                enterDemoMode()
            } else {
                leaveDemoMode()
            }
        }
    }

    init {
        DemoModeState.addListener(demoModeListener)

        if (DemoModeState.active) {
            enterDemoMode()
        }
    }

    fun setServerAddress(address: String) {
        val baseUrl = normalizeServerUrl(address)

        if (baseUrl == null) {
            uiState = uiState.copy(
                error = "Adresse serveur invalide"
            )
            return
        }

        reconnectJob?.cancel()
        reconnectJob = null
        handshakeTimeoutJob?.cancel()
        handshakeTimeoutJob = null

        invalidateCurrentWebSocket(
            reason = "Adresse serveur modifiée"
        )

        api = StellarPilotApiClient(baseUrl)

        uiState = uiState.copy(
            serverBaseUrl = baseUrl,
            connectionState =
                if (DemoModeState.active) {
                    ConnectionState.STOPPED
                } else {
                    ConnectionState.DISCONNECTED
                },
            isConnecting = false,
            server = null,
            catalog = null,
            restStatus =
                if (DemoModeState.active) {
                    "Mode démonstration local"
                } else {
                    "Non connecté"
                },
            webSocketStatus =
                if (DemoModeState.active) {
                    "Désactivé en mode démo"
                } else {
                    "Non connecté"
                },
            reconnectAttempt = 0,
            reconnectDelaySeconds = null,
            error = null
        )

        if (!DemoModeState.active) {
            stopped = false
            connect()
        }
    }

    private fun normalizeServerUrl(
        address: String
    ): String? {
        var value = address.trim()

        if (value.isBlank()) return null

        value = value.trimEnd('/')

        if (
            !value.startsWith("http://", ignoreCase = true) &&
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

            val port =
                if (uri.port == -1) 8000 else uri.port

            "$scheme://$host:$port/"
        } catch (_: Exception) {
            null
        }
    }

    fun connect() {
        if (destroyed) return

        if (DemoModeState.active) {
            enterDemoMode()
            return
        }

        stopped = false
        reconnectJob?.cancel()
        reconnectJob = null

        startConnection(
            reconnecting = false
        )
    }

    private fun startConnection(
        reconnecting: Boolean
    ) {
        if (
            destroyed ||
            stopped ||
            DemoModeState.active ||
            uiState.isConnecting
        ) {
            return
        }

        uiState = uiState.copy(
            connectionState =
                if (reconnecting) {
                    ConnectionState.RECONNECTING
                } else {
                    ConnectionState.CONNECTING
                },
            isConnecting = true,
            restStatus = "Connexion...",
            error = null
        )

        viewModelScope.launch {
            try {
                api.checkHealth()

                if (networkDisabled()) {
                    return@launch
                }

                try {
                    api.syncClientTime()
                } catch (error: Exception) {
                    if (!networkDisabled()) {
                        Log.w(
                            TAG,
                            "Synchronisation heure Android impossible",
                            error
                        )
                    }
                }

                if (networkDisabled()) {
                    return@launch
                }

                uiState = uiState.copy(
                    isConnecting = false,
                    restStatus = "OK"
                )

                connectWebSocket()

                viewModelScope.launch {
                    try {
                        val status = api.getStatus()

                        if (networkDisabled()) {
                            return@launch
                        }

                        uiState = uiState.copy(
                            server = status,
                            restStatus = "OK"
                        )
                    } catch (error: Exception) {
                        if (networkDisabled()) {
                            return@launch
                        }

                        Log.e(
                            TAG,
                            "Connexion établie mais /status indisponible",
                            error
                        )

                        uiState = uiState.copy(
                            restStatus =
                                "OK - télémétrie indisponible"
                        )
                    }
                }

            } catch (error: Exception) {
                if (networkDisabled()) {
                    return@launch
                }

                Log.w(
                    TAG,
                    "Serveur StellarPilot inaccessible",
                    error
                )

                uiState = uiState.copy(
                    connectionState = ConnectionState.DISCONNECTED,
                    isConnecting = false,
                    server = null,
                    restStatus = "Erreur",
                    webSocketStatus = "Serveur inaccessible",
                    error =
                        error.message
                            ?: "Serveur StellarPilot inaccessible"
                )

                scheduleReconnect(
                    reason = "REST inaccessible"
                )
            }
        }
    }

    private fun connectWebSocket() {
        if (networkDisabled()) return
        if (webSocket != null) return

        uiState = uiState.copy(
            webSocketStatus = "Connexion..."
        )

        val generation = ++webSocketGeneration

        val listener =
            object : WebSocketListener() {

                override fun onOpen(
                    socket: WebSocket,
                    response: Response
                ) {
                    viewModelScope.launch {
                        if (
                            generation != webSocketGeneration ||
                            networkDisabled()
                        ) {
                            socket.close(
                                1000,
                                "Connexion désactivée"
                            )
                            return@launch
                        }

                        uiState = uiState.copy(
                            isConnecting = true,
                            webSocketStatus = "Handshake...",
                            error = null
                        )

                        val hello =
                            JSONObject()
                                .put("type", "hello")
                                .put("client", "android")
                                .put(
                                    "app_version",
                                    BuildConfig.VERSION_NAME
                                )
                                .put("protocol", "proto-1")

                        if (!socket.send(hello.toString())) {
                            socket.cancel()
                            return@launch
                        }

                        handshakeTimeoutJob?.cancel()
                        handshakeTimeoutJob =
                            viewModelScope.launch {
                                delay(5_000L)

                                if (
                                    generation == webSocketGeneration &&
                                    !networkDisabled() &&
                                    uiState.connectionState !=
                                    ConnectionState.CONNECTED
                                ) {
                                    socket.close(
                                        1002,
                                        "Timeout handshake"
                                    )
                                }
                            }
                    }
                }

                override fun onMessage(
                    socket: WebSocket,
                    text: String
                ) {
                    viewModelScope.launch {
                        if (
                            generation != webSocketGeneration ||
                            networkDisabled()
                        ) {
                            return@launch
                        }

                        handleWebSocketMessage(
                            text,
                            socket
                        )
                    }
                }

                override fun onClosed(
                    socket: WebSocket,
                    code: Int,
                    reason: String
                ) {
                    viewModelScope.launch {
                        if (
                            generation != webSocketGeneration ||
                            networkDisabled()
                        ) {
                            return@launch
                        }

                        webSocket = null

                        uiState = uiState.copy(
                            connectionState =
                                ConnectionState.DISCONNECTED,
                            webSocketStatus = "Fermé"
                        )

                        scheduleReconnect(
                            reason = "WebSocket fermé"
                        )
                    }
                }

                override fun onFailure(
                    socket: WebSocket,
                    throwable: Throwable,
                    response: Response?
                ) {
                    viewModelScope.launch {
                        if (
                            generation != webSocketGeneration ||
                            networkDisabled()
                        ) {
                            return@launch
                        }

                        Log.w(
                            TAG,
                            "Échec du WebSocket StellarPilot",
                            throwable
                        )

                        webSocket = null

                        uiState = uiState.copy(
                            connectionState =
                                ConnectionState.DISCONNECTED,
                            isConnecting = false,
                            webSocketStatus = "Erreur",
                            error =
                                throwable.message
                                    ?: "Erreur WebSocket"
                        )

                        scheduleReconnect(
                            reason = "Échec WebSocket"
                        )
                    }
                }
            }

        webSocket = api.openEvents(listener)
    }

    private fun handleWebSocketMessage(
        text: String,
        socket: WebSocket
    ) {
        if (networkDisabled()) return

        try {
            val payload = JSONObject(text)

            when (payload.optString("event", "")) {
                "connected" -> {
                    uiState = uiState.copy(
                        webSocketStatus = "Handshake..."
                    )
                }

                "welcome" -> {
                    val protocol =
                        payload.optString(
                            "protocol",
                            ""
                        )

                    if (protocol != "proto-1") {
                        uiState = uiState.copy(
                            connectionState =
                                ConnectionState.DISCONNECTED,
                            isConnecting = false,
                            webSocketStatus =
                                "Protocole incompatible",
                            error =
                                "Protocole serveur incompatible : $protocol"
                        )

                        handshakeTimeoutJob?.cancel()
                        handshakeTimeoutJob = null

                        socket.close(
                            1002,
                            "Protocole incompatible"
                        )
                        return
                    }

                    handshakeTimeoutJob?.cancel()
                    handshakeTimeoutJob = null

                    uiState = uiState.copy(
                        connectionState = ConnectionState.CONNECTED,
                        isConnecting = false,
                        webSocketStatus = "Connecté ($protocol)",
                        reconnectAttempt = 0,
                        reconnectDelaySeconds = null,
                        error = null
                    )
                }
            }
        } catch (error: Exception) {
            Log.d(
                TAG,
                "Message WebSocket non JSON : $text",
                error
            )
        }
    }

    private fun scheduleReconnect(
        reason: String
    ) {
        if (
            networkDisabled() ||
            reconnectJob?.isActive == true
        ) {
            return
        }

        val attempt =
            uiState.reconnectAttempt + 1

        val delayIndex =
            (attempt - 1)
                .coerceAtMost(
                    RECONNECT_DELAYS_SECONDS.lastIndex
                )

        val delaySeconds =
            RECONNECT_DELAYS_SECONDS[delayIndex]

        Log.i(
            TAG,
            "Reconnexion #$attempt dans ${delaySeconds}s : $reason"
        )

        uiState = uiState.copy(
            connectionState = ConnectionState.RECONNECTING,
            isConnecting = false,
            webSocketStatus =
                "Reconnexion dans ${delaySeconds}s",
            reconnectAttempt = attempt,
            reconnectDelaySeconds = delaySeconds
        )

        reconnectJob =
            viewModelScope.launch {
                delay(delaySeconds * 1000L)
                reconnectJob = null

                if (!networkDisabled()) {
                    startConnection(
                        reconnecting = true
                    )
                }
            }
    }

    private fun enterDemoMode() {
        if (destroyed) return

        stopped = true

        reconnectJob?.cancel()
        reconnectJob = null

        handshakeTimeoutJob?.cancel()
        handshakeTimeoutJob = null

        invalidateCurrentWebSocket(
            reason = "Mode démonstration local"
        )

        uiState = uiState.copy(
            connectionState = ConnectionState.STOPPED,
            isConnecting = false,
            server = null,
            catalog = null,
            restStatus = "Mode démonstration local",
            webSocketStatus = "Désactivé en mode démo",
            reconnectAttempt = 0,
            reconnectDelaySeconds = null,
            error = null
        )
    }

    private fun leaveDemoMode() {
        if (destroyed) return

        stopped = false

        uiState = uiState.copy(
            connectionState = ConnectionState.DISCONNECTED,
            isConnecting = false,
            server = null,
            catalog = null,
            restStatus = "Non connecté",
            webSocketStatus = "Non connecté",
            reconnectAttempt = 0,
            reconnectDelaySeconds = null,
            error = null
        )

        connect()
    }

    private fun networkDisabled(): Boolean =
        destroyed ||
            stopped ||
            DemoModeState.active

    private fun invalidateCurrentWebSocket(
        reason: String
    ) {
        webSocketGeneration++

        val socket = webSocket
        webSocket = null

        socket?.close(
            1000,
            reason
        )
    }

    override fun onCleared() {
        destroyed = true
        stopped = true

        DemoModeState.removeListener(
            demoModeListener
        )

        reconnectJob?.cancel()
        reconnectJob = null

        handshakeTimeoutJob?.cancel()
        handshakeTimeoutJob = null

        invalidateCurrentWebSocket(
            reason = "ViewModel détruit"
        )

        uiState = uiState.copy(
            connectionState = ConnectionState.STOPPED,
            isConnecting = false,
            reconnectDelaySeconds = null
        )

        super.onCleared()
    }
}
