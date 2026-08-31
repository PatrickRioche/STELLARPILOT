package fr.stellarpilot.app.feature.capture

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.stellarpilot.app.data.remote.CaptureSessionApiClient
import fr.stellarpilot.app.data.remote.CaptureSessionStatus
import fr.stellarpilot.app.data.remote.MountGotoCommandClient
import fr.stellarpilot.app.data.remote.StellarPilotApiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale


data class CaptureTarget(
    val name: String,
    val reference: String?,
    val objectType: String,
    val raHours: Double,
    val decDeg: Double
) {
    val trackingMode: String
        get() = when (objectType.lowercase(Locale.ROOT)) {
            "sun" -> "solar"
            "moon" -> "lunar"
            else -> "sidereal"
        }
}


data class CaptureUiState(
    val target: CaptureTarget? = null,
    val exposureSeconds: Double = 4.0,
    val session: CaptureSessionStatus? = null,
    val imageBytes: ByteArray? = null,
    val isBusy: Boolean = false,
    val isMonitoring: Boolean = false,
    val statusMessage: String? = null,
    val error: String? = null,
    val savedToGallery: Boolean = false
)


class CaptureViewModel(
    application: Application
) : AndroidViewModel(application) {

    var uiState by mutableStateOf(CaptureUiState())
        private set

    private var monitorJob: Job? = null
    private var autoRecenterRunning = false

    init {
        loadSelectedTarget()
    }

    fun loadSelectedTarget() {
        val context = getApplication<Application>()
        val targetPreferences =
            context.getSharedPreferences(
                "stellarpilot_target",
                0
            )
        val setupPreferences =
            context.getSharedPreferences(
                "stellarpilot_capture_setup",
                0
            )

        val name = targetPreferences.getString("name", null)
        val ra = targetPreferences
            .getString("ra_hours", null)
            ?.toDoubleOrNull()
        val dec = targetPreferences
            .getString("dec_deg", null)
            ?.toDoubleOrNull()

        val target =
            if (name != null && ra != null && dec != null) {
                CaptureTarget(
                    name = name,
                    reference =
                        targetPreferences.getString(
                            "reference",
                            null
                        ),
                    objectType =
                        targetPreferences.getString(
                            "object_type",
                            "unknown"
                        ) ?: "unknown",
                    raHours = ra,
                    decDeg = dec
                )
            } else {
                null
            }

        uiState = uiState.copy(
            target = target,
            exposureSeconds =
                setupPreferences.getFloat(
                    "exposure_seconds",
                    4.0f
                ).toDouble(),
            error = null
        )
    }

    fun changeExposure(deltaSeconds: Double) {
        if (uiState.session != null) return

        val value =
            (uiState.exposureSeconds + deltaSeconds)
                .coerceIn(0.1, 30.0)

        getApplication<Application>()
            .getSharedPreferences(
                "stellarpilot_capture_setup",
                0
            )
            .edit()
            .putFloat(
                "exposure_seconds",
                value.toFloat()
            )
            .apply()

        uiState = uiState.copy(
            exposureSeconds = value
        )
    }

    private suspend fun ensureSession(
        serverBaseUrl: String
    ): CaptureSessionStatus {
        uiState.session?.let {
            return it
        }

        val target = uiState.target
            ?: error("Aucune cible sélectionnée dans Ciel")

        val session =
            CaptureSessionApiClient(serverBaseUrl)
                .createSession(
                    targetName = target.name,
                    targetRaHours = target.raHours,
                    targetDecDeg = target.decDeg,
                    objectType = target.objectType,
                    trackingMode = target.trackingMode,
                    exposureSeconds = uiState.exposureSeconds
                )

        uiState = uiState.copy(
            session = session,
            savedToGallery = false
        )

        return session
    }

    fun centerTarget(serverBaseUrl: String) {
        if (uiState.isBusy) return

        viewModelScope.launch {
            uiState = uiState.copy(
                isBusy = true,
                statusMessage = "Capture astrométrique...",
                error = null
            )

            try {
                var session = ensureSession(serverBaseUrl)
                val target = uiState.target
                    ?: error("Aucune cible sélectionnée")

                for (attempt in 1..4) {
                    session =
                        CaptureSessionApiClient(serverBaseUrl)
                            .centerStep(session.id)

                    val preview =
                        runCatching {
                            CaptureSessionApiClient(serverBaseUrl)
                                .getPreview(
                                    session.id,
                                    stack = false
                                )
                        }.getOrNull()

                    uiState = uiState.copy(
                        session = session,
                        imageBytes = preview ?: uiState.imageBytes,
                        statusMessage =
                            if (
                                session.centering.status == "centered"
                            ) {
                                "Cible centrée — vous pouvez démarrer le stacking"
                            } else {
                                "Astrométrie ${attempt}/4 — correction du pointage"
                            }
                    )

                    if (session.centering.status == "centered") {
                        uiState = uiState.copy(
                            isBusy = false,
                            session = session,
                            error = null
                        )
                        return@launch
                    }

                    if (target.objectType.equals("sun", ignoreCase = true)) {
                        error(
                            "Le recentrage automatique du Soleil est désactivé. " +
                                "Vérifiez le filtre solaire et le cadrage manuellement."
                        )
                    }

                    val correctionRa =
                        session.centering.correctionRaHours
                            ?: error("Correction AD indisponible")
                    val correctionDec =
                        session.centering.correctionDecDeg
                            ?: error("Correction DEC indisponible")

                    uiState = uiState.copy(
                        statusMessage =
                            "Recentrage monture • tentative ${attempt}/4"
                    )

                    MountGotoCommandClient(serverBaseUrl)
                        .gotoMount(
                            raHours = correctionRa,
                            decDeg = correctionDec,
                            trackingMode = target.trackingMode
                        )

                    waitForTracking(serverBaseUrl)
                    delay(700)
                }

                error(
                    "Centrage non obtenu après 4 corrections astrométriques"
                )

            } catch (error: Exception) {
                uiState = uiState.copy(
                    isBusy = false,
                    error =
                        "${error::class.java.simpleName}: ${error.message}",
                    statusMessage = null
                )
            }
        }
    }

    fun startStacking(serverBaseUrl: String) {
        val session = uiState.session ?: return
        if (
            uiState.isBusy ||
            session.centering.status != "centered"
        ) return

        viewModelScope.launch {
            uiState = uiState.copy(
                isBusy = true,
                error = null,
                statusMessage = "Démarrage du stacking..."
            )

            try {
                val started =
                    CaptureSessionApiClient(serverBaseUrl)
                        .startStack(session.id)

                uiState = uiState.copy(
                    isBusy = false,
                    session = started,
                    statusMessage = "Stacking en cours"
                )
                startMonitor(serverBaseUrl, session.id)

            } catch (error: Exception) {
                uiState = uiState.copy(
                    isBusy = false,
                    error = error.message,
                    statusMessage = null
                )
            }
        }
    }

    fun stopStacking(serverBaseUrl: String) {
        val session = uiState.session ?: return

        viewModelScope.launch {
            try {
                val stopped =
                    CaptureSessionApiClient(serverBaseUrl)
                        .stopStack(session.id)
                uiState = uiState.copy(
                    session = stopped,
                    statusMessage =
                        "Arrêt demandé — la pose en cours se termine"
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    error = error.message
                )
            }
        }
    }

    fun finalizeSession(serverBaseUrl: String) {
        val session = uiState.session ?: return
        if (session.stacking.running) return

        viewModelScope.launch {
            uiState = uiState.copy(
                isBusy = true,
                error = null,
                statusMessage = "Enregistrement dans Galeries..."
            )
            try {
                val saved =
                    CaptureSessionApiClient(serverBaseUrl)
                        .finalizeSession(session.id)
                uiState = uiState.copy(
                    isBusy = false,
                    session = saved,
                    savedToGallery = true,
                    statusMessage = "Session enregistrée dans Galeries"
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isBusy = false,
                    error = error.message,
                    statusMessage = null
                )
            }
        }
    }

    private fun startMonitor(
        serverBaseUrl: String,
        sessionId: String
    ) {
        monitorJob?.cancel()
        monitorJob = viewModelScope.launch {
            uiState = uiState.copy(
                isMonitoring = true
            )

            var previewTick = 0

            try {
                while (isActive) {
                    val api = CaptureSessionApiClient(serverBaseUrl)
                    val session = api.getSession(sessionId)
                    previewTick += 1

                    var preview = uiState.imageBytes
                    if (
                        session.hasStackPreview &&
                        previewTick % 3 == 0
                    ) {
                        preview = runCatching {
                            api.getPreview(
                                sessionId,
                                stack = true
                            )
                        }.getOrNull() ?: preview
                    }

                    uiState = uiState.copy(
                        session = session,
                        imageBytes = preview,
                        statusMessage =
                            when {
                                session.stacking.recenterRequired ->
                                    "Dérive détectée — recentrage astrométrique"
                                session.stacking.running ->
                                    "Stacking en cours"
                                session.state == "stack_error" ->
                                    "Stacking interrompu"
                                else ->
                                    uiState.statusMessage
                            }
                    )

                    if (
                        session.stacking.recenterRequired &&
                        !autoRecenterRunning
                    ) {
                        performStackRecenter(
                            serverBaseUrl,
                            session
                        )
                    }

                    if (
                        !session.stacking.running &&
                        !session.stacking.recenterRequired &&
                        session.state !in setOf(
                            "stacking",
                            "stopping"
                        )
                    ) {
                        break
                    }

                    delay(1000)
                }
            } catch (error: Exception) {
                uiState = uiState.copy(
                    error = error.message
                )
            } finally {
                uiState = uiState.copy(
                    isMonitoring = false
                )
            }
        }
    }

    private suspend fun performStackRecenter(
        serverBaseUrl: String,
        session: CaptureSessionStatus
    ) {
        val target = uiState.target ?: return
        if (target.objectType.equals("sun", ignoreCase = true)) {
            uiState = uiState.copy(
                error =
                    "Recentrage automatique solaire suspendu. " +
                        "Intervention manuelle requise."
            )
            return
        }

        val ra = session.centering.correctionRaHours ?: return
        val dec = session.centering.correctionDecDeg ?: return

        autoRecenterRunning = true
        try {
            uiState = uiState.copy(
                statusMessage = "Stacking en pause • correction du pointage"
            )

            MountGotoCommandClient(serverBaseUrl)
                .gotoMount(
                    raHours = ra,
                    decDeg = dec,
                    trackingMode = target.trackingMode
                )
            waitForTracking(serverBaseUrl)
            delay(700)

            val api = CaptureSessionApiClient(serverBaseUrl)
            val verified = api.centerStep(session.id)
            val preview =
                runCatching {
                    api.getPreview(session.id, stack = false)
                }.getOrNull()

            uiState = uiState.copy(
                session = verified,
                imageBytes = preview ?: uiState.imageBytes
            )

            if (verified.centering.status == "centered") {
                val resumed = api.resumeStack(session.id)
                uiState = uiState.copy(
                    session = resumed,
                    statusMessage =
                        "Cible recentrée • reprise du stacking"
                )
            } else {
                uiState = uiState.copy(
                    error =
                        "La vérification astrométrique demande encore une correction. " +
                            "Utilisez RECENTRER."
                )
            }
        } catch (error: Exception) {
            uiState = uiState.copy(
                error =
                    "Recentrage stacking: ${error.message}"
            )
        } finally {
            autoRecenterRunning = false
        }
    }

    private suspend fun waitForTracking(
        serverBaseUrl: String
    ) {
        val api = StellarPilotApiClient(serverBaseUrl)
        repeat(120) {
            val status = api.getMountMotionStatus()
            if (
                status.status == "tracking" ||
                status.progressPercent?.let { it >= 99.5 } == true
            ) {
                return
            }
            if (status.status == "error") {
                error(status.detail ?: "Erreur de pointage")
            }
            delay(500)
        }
        error("Timeout en attente du recentrage de la monture")
    }

    override fun onCleared() {
        monitorJob?.cancel()
        super.onCleared()
    }
}
