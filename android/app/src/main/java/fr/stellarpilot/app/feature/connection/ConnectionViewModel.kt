package fr.stellarpilot.app.feature.connection

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.stellarpilot.app.BuildConfig
import fr.stellarpilot.app.data.remote.StellarPilotApiClient
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

        /*
         * D?lais de reconnexion successifs.
         *
         * Apr?s 30 secondes, les tentatives suivantes restent
         * espac?es de 30 secondes afin de ne pas saturer le r?seau
         * ni le Raspberry Pi lorsqu'il est r?ellement indisponible.
         */
        private val RECONNECT_DELAYS_SECONDS =
            longArrayOf(1L, 2L, 3L, 5L, 8L, 10L)
    }

    private var api =
        StellarPilotApiClient(
            BuildConfig.SERVER_BASE_URL
        )

    private var webSocket: WebSocket? = null

    /*
     * Travail de reconnexion diff?r?e actuellement programm?.
     * Une seule reconnexion peut ?tre planifi?e ? la fois.
     */
    private var reconnectJob: Job? = null


    private var handshakeTimeoutJob: Job? = null
/*
     * Num?ro de g?n?ration du WebSocket.
     *
     * Il permet d'ignorer les callbacks tardifs provenant d'une
     * ancienne socket apr?s un changement d'adresse serveur ou
     * apr?s l'ouverture d'une nouvelle connexion.
     */
    private var webSocketGeneration = 0L

    /*
     * Devient vrai lorsque le ViewModel est d?truit.
     * Aucune reconnexion ne doit alors ?tre programm?e.
     */
    private var stopped = false

    var uiState by mutableStateOf(
        ConnectionUiState(
            backendMode = BuildConfig.BACKEND_MODE,
            serverBaseUrl = BuildConfig.SERVER_BASE_URL
        )
    )
        private set

    /**
     * Change l'adresse du serveur StellarPilot.
     *
     * La connexion pr?c?dente est invalid?e avant d'ouvrir la
     * nouvelle afin qu'un callback tardif de l'ancien serveur ne
     * puisse pas d?clencher une reconnexion parasite.
     */
    fun setServerAddress(address: String) {
        val baseUrl =
            normalizeServerUrl(address)

        if (baseUrl == null) {
            uiState = uiState.copy(
                error = "Adresse serveur invalide"
            )
            return
        }

        stopped = false

        reconnectJob?.cancel()
        reconnectJob = null

        invalidateCurrentWebSocket(
            reason = "Adresse serveur modifi?e"
        )

        api = StellarPilotApiClient(baseUrl)

        uiState = uiState.copy(
            serverBaseUrl = baseUrl,
            connectionState =
                ConnectionState.DISCONNECTED,
            isConnecting = false,
            server = null,
            restStatus = "Non connect?",
            webSocketStatus = "Non connect?",
            reconnectAttempt = 0,
            reconnectDelaySeconds = null,
            error = null
        )

        connect()
    }

    /**
     * Normalise une adresse saisie par l'utilisateur.
     *
     * Si aucun port n'est fourni, le port historique 8000 du
     * serveur StellarPilot est utilis?.
     */
    private fun normalizeServerUrl(
        address: String
    ): String? {
        var value = address.trim()

        if (value.isBlank()) {
            return null
        }

        value = value.trimEnd('/')

        if (
            !value.startsWith(
                "http://",
                ignoreCase = true
            ) &&
            !value.startsWith(
                "https://",
                ignoreCase = true
            )
        ) {
            value = "http://$value"
        }

        return try {
            val uri = java.net.URI(value)

            val scheme =
                uri.scheme?.lowercase()
                    ?: "http"

            val host =
                uri.host
                    ?: return null

            if (
                scheme != "http" &&
                scheme != "https"
            ) {
                return null
            }

            val port =
                if (uri.port == -1) {
                    8000
                } else {
                    uri.port
                }

            "$scheme://$host:$port/"
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Demande une connexion imm?diate.
     *
     * Une action manuelle annule l'attente ?ventuelle du backoff :
     * l'utilisateur n'a donc jamais besoin d'attendre la prochaine
     * tentative automatique.
     */
    fun connect() {
        if (stopped) {
            stopped = false
        }

        reconnectJob?.cancel()
        reconnectJob = null

        startConnection(
            reconnecting = false
        )
    }

    /**
     * V?rifie d'abord la disponibilit? REST du serveur avant
     * d'ouvrir le canal WebSocket.
     *
     * Cette s?paration permet de distinguer :
     * - serveur totalement inaccessible ;
     * - serveur accessible mais ?tat mat?riel illisible ;
     * - canal WebSocket indisponible.
     */
    private fun startConnection(
        reconnecting: Boolean
    ) {
        if (stopped || uiState.isConnecting) {
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
                // /health d?termine la disponibilit? du serveur.
                api.checkHealth()

                uiState = uiState.copy(
                    isConnecting = false,
                    restStatus = "OK"
                )

                /*
                 * Le WebSocket est ouvert imm?diatement apr?s /health.
                 *
                 * La lecture du mat?riel via /status ne doit jamais
                 * retarder la connexion temps r?el.
                 */
                connectWebSocket()

                /*
                 * La t?l?m?trie mat?rielle est r?cup?r?e s?par?ment.
                 * Son ?chec ne remet pas en cause la connexion serveur.
                 */
                viewModelScope.launch {
                    try {
                        val status = api.getStatus()

                        uiState = uiState.copy(
                            server = status,
                            restStatus = "OK"
                        )
                    } catch (error: Exception) {
                        Log.e(
                            TAG,
                            "Connexion ?tablie mais /status indisponible",
                            error
                        )

                        uiState = uiState.copy(
                            restStatus =
                                "OK - t?l?m?trie indisponible"
                        )
                    }
                }

            } catch (error: Exception) {
                Log.w(
                    TAG,
                    "Serveur StellarPilot inaccessible",
                    error
                )

                uiState = uiState.copy(
                    connectionState =
                        ConnectionState.DISCONNECTED,
                    isConnecting = false,
                    server = null,
                    restStatus = "Erreur",
                    webSocketStatus =
                        "Serveur inaccessible",
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

    /**
     * Ouvre le WebSocket du prototype.
     *
     * Le heartbeat r?seau est assur? automatiquement par OkHttp.
     * ? l'ouverture, Android envoie ?galement un message hello afin
     * de n?gocier le protocole applicatif avec StellarPilot Server.
     */
    private fun connectWebSocket() {
        if (stopped) {
            return
        }

        if (webSocket != null) {
            return
        }

        uiState = uiState.copy(
            webSocketStatus = "Connexion..."
        )

        val generation =
            ++webSocketGeneration

        val listener =
            object : WebSocketListener() {

                override fun onOpen(
                    socket: WebSocket,
                    response: Response
                ) {
                    viewModelScope.launch {
                        if (
                            generation !=
                            webSocketGeneration
                        ) {
                            socket.close(
                                1000,
                                "Connexion obsolete"
                            )
                            return@launch
                        }

                        Log.i(
                            TAG,
                            "WebSocket StellarPilot ouvert - handshake"
                        )

                        /*
                         * Le WebSocket TCP est ouvert mais le serveur
                         * StellarPilot n'est pas encore valide.
                         *
                         * CONNECTED sera positionne uniquement apres
                         * reception de welcome avec proto-1.
                         */
                        uiState = uiState.copy(
                            isConnecting = true,
                            webSocketStatus =
                                "Handshake...",
                            error = null
                        )

                        val hello =
                            JSONObject()
                                .put(
                                    "type",
                                    "hello"
                                )
                                .put(
                                    "client",
                                    "android"
                                )
                                .put(
                                    "app_version",
                                    BuildConfig.VERSION_NAME
                                )
                                .put(
                                    "protocol",
                                    "proto-1"
                                )

                        val sent =
                            socket.send(
                                hello.toString()
                            )

                        if (!sent) {
                            Log.w(
                                TAG,
                                "Echec envoi HELLO StellarPilot"
                            )

                            socket.cancel()
                            return@launch
                        }

                        Log.i(
                            TAG,
                            "HELLO StellarPilot envoye (proto-1)"
                        )

                        handshakeTimeoutJob?.cancel()

                        handshakeTimeoutJob =
                            viewModelScope.launch {
                                delay(5_000L)

                                if (
                                    generation ==
                                    webSocketGeneration &&
                                    uiState.connectionState !=
                                    ConnectionState.CONNECTED
                                ) {
                                    Log.w(
                                        TAG,
                                        "Timeout handshake StellarPilot"
                                    )

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
                            generation !=
                            webSocketGeneration
                        ) {
                            return@launch
                        }

                        handleWebSocketMessage(text, socket)
                    }
                }

                override fun onClosed(
                    socket: WebSocket,
                    code: Int,
                    reason: String
                ) {
                    viewModelScope.launch {
                        if (
                            generation !=
                            webSocketGeneration
                        ) {
                            return@launch
                        }

                        Log.i(
                            TAG,
                            "WebSocket ferm? : code=$code, raison=$reason"
                        )

                        webSocket = null

                        uiState = uiState.copy(
                            connectionState =
                                ConnectionState.DISCONNECTED,
                            webSocketStatus = "Ferm?"
                        )

                        scheduleReconnect(
                            reason =
                                "WebSocket ferm?"
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
                            generation !=
                            webSocketGeneration
                        ) {
                            return@launch
                        }

                        Log.w(
                            TAG,
                            "?chec du WebSocket StellarPilot",
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
                            reason =
                                "?chec WebSocket"
                        )
                    }
                }
            }

        webSocket =
            api.openEvents(listener)
    }

    /**
     * Interpr?te les messages de contr?le du serveur sans afficher
     * le JSON brut dans l'interface.
     */
    private fun handleWebSocketMessage(
        text: String,
        socket: WebSocket
    ) {
        try {
            val payload =
                JSONObject(text)

            when (
                payload.optString(
                    "event",
                    ""
                )
            ) {
                "connected" -> {
                    /*
                     * Le serveur annonce l'ouverture du canal.
                     * Ce message ne valide pas encore le protocole.
                     */
                    uiState = uiState.copy(
                        webSocketStatus =
                            "Handshake..."
                    )
                }

                "welcome" -> {
                    val protocol =
                        payload.optString(
                            "protocol",
                            ""
                        )

                    if (protocol != "proto-1") {
                        Log.w(
                            TAG,
                            "Protocole StellarPilot incompatible : $protocol"
                        )

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
                        connectionState =
                            ConnectionState.CONNECTED,
                        isConnecting = false,
                        webSocketStatus =
                            "Connecte ($protocol)",
                        reconnectAttempt = 0,
                        reconnectDelaySeconds = null,
                        error = null
                    )

                    Log.i(
                        TAG,
                        "WELCOME StellarPilot recu - proto-1 valide"
                    )
                }

                else -> {
                    Log.d(
                        TAG,
                        "Message WebSocket re?u : $text"
                    )
                }
            }
        } catch (error: Exception) {
            /*
             * Une trame inconnue ne coupe jamais la connexion.
             * Elle est seulement journalis?e pour diagnostic.
             */
            Log.d(
                TAG,
                "Message WebSocket non JSON : $text",
                error
            )
        }
    }

    /**
     * Programme une reconnexion avec backoff progressif.
     *
     * S?quence :
     * 1 s -> 2 s -> 4 s -> 8 s -> 15 s -> 30 s.
     *
     * Les tentatives suivantes restent ensuite ? 30 secondes.
     */
    private fun scheduleReconnect(
        reason: String
    ) {
        if (
            stopped ||
            reconnectJob?.isActive == true
        ) {
            return
        }

        val attempt =
            uiState.reconnectAttempt + 1

        val delayIndex =
            (attempt - 1)
                .coerceAtMost(
                    RECONNECT_DELAYS_SECONDS
                        .lastIndex
                )

        val delaySeconds =
            RECONNECT_DELAYS_SECONDS[
                delayIndex
            ]

        Log.i(
            TAG,
            "Reconnexion #$attempt dans ${delaySeconds}s : $reason"
        )

        uiState = uiState.copy(
            connectionState =
                ConnectionState.RECONNECTING,
            isConnecting = false,
            webSocketStatus =
                "Reconnexion dans ${delaySeconds}s",
            reconnectAttempt = attempt,
            reconnectDelaySeconds =
                delaySeconds
        )

        reconnectJob =
            viewModelScope.launch {
                delay(
                    delaySeconds * 1000L
                )

                reconnectJob = null

                if (!stopped) {
                    startConnection(
                        reconnecting = true
                    )
                }
            }
    }

    /**
     * Invalide la socket active avant de la fermer.
     *
     * L'incr?ment de g?n?ration garantit que les callbacks produits
     * par cette fermeture volontaire seront ignor?s.
     */
    private fun invalidateCurrentWebSocket(
        reason: String
    ) {
        webSocketGeneration++

        val socket =
            webSocket

        webSocket = null

        socket?.close(
            1000,
            reason
        )
    }

    /**
     * Arr?t d?finitif du gestionnaire lorsque l'?cran associ?
     * dispara?t r?ellement.
     */
    override fun onCleared() {
        stopped = true

        reconnectJob?.cancel()
        reconnectJob = null

        invalidateCurrentWebSocket(
            reason = "ViewModel d?truit"
        )

        uiState = uiState.copy(
            connectionState =
                ConnectionState.STOPPED,
            isConnecting = false,
            reconnectDelaySeconds = null
        )

        super.onCleared()
    }
}
