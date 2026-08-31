package fr.stellarpilot.app.feature.capture

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.stellarpilot.app.R
import fr.stellarpilot.app.data.remote.CaptureCenteringStatus
import fr.stellarpilot.app.data.remote.CaptureSessionApiClient
import fr.stellarpilot.app.data.remote.CaptureSessionStatus
import fr.stellarpilot.app.data.remote.CaptureStackingStatus
import fr.stellarpilot.app.data.remote.MountGotoCommandClient
import fr.stellarpilot.app.data.remote.StellarPilotApiClient
import fr.stellarpilot.app.feature.demo.DemoModeState
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
    val savedToGallery: Boolean = false,
    val operationElapsedMs: Long = 0L,
    val operationExpectedMs: Long = 0L,
    val operationPhase: String? = null
)


class CaptureViewModel(
    application: Application
) : AndroidViewModel(application) {

    var uiState by mutableStateOf(CaptureUiState())
        private set

    private var monitorJob: Job? = null
    private var operationTimerJob: Job? = null
    private var demoStackJob: Job? = null
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
            ?: throw IllegalArgumentException(
                "Aucune cible sélectionnée dans Ciel"
            )

        if (DemoModeState.active) {
            val session = demoSession(
                target = target,
                centered = false
            )
            uiState = uiState.copy(
                session = session,
                savedToGallery = false
            )
            return session
        }

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

        if (DemoModeState.active) {
            runDemoCenter()
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(
                isBusy = true,
                statusMessage = "Préparation de la capture astrométrique...",
                error = null
            )

            try {
                var session = ensureSession(serverBaseUrl)
                val target = uiState.target
                    ?: throw IllegalArgumentException(
                        "Aucune cible sélectionnée"
                    )

                for (attempt in 1..4) {
                    uiState = uiState.copy(
                        statusMessage =
                            "Acquisition image • tentative $attempt/4"
                    )
                    startOperationTimer(
                        expectedSeconds = session.exposureSeconds
                    )

                    session =
                        CaptureSessionApiClient(serverBaseUrl)
                            .centerStep(session.id)

                    stopOperationTimer()

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
                        imageBytes = preview ?: uiState.imageBytes
                    )

                    when (session.centering.status) {
                        "centered" -> {
                            uiState = uiState.copy(
                                isBusy = false,
                                session = session,
                                error = null,
                                statusMessage =
                                    "Cible centrée — vous pouvez démarrer le stacking"
                            )
                            return@launch
                        }

                        "unsolved" -> {
                            val detail =
                                session.centering.solverDetail
                                    ?.takeIf { it.isNotBlank() }
                            uiState = uiState.copy(
                                isBusy = false,
                                session = session,
                                statusMessage =
                                    "Astrométrie non résolue — aucune correction de monture envoyée",
                                error =
                                    buildString {
                                        append(
                                            "Champ non résolu. Réessayez la capture"
                                        )
                                        if (detail != null) {
                                            append(" • ")
                                            append(detail)
                                        }
                                    }
                            )
                            return@launch
                        }
                    }

                    if (target.objectType.equals("sun", ignoreCase = true)) {
                        uiState = uiState.copy(
                            isBusy = false,
                            session = session,
                            statusMessage = null,
                            error =
                                "Le recentrage automatique du Soleil est désactivé. " +
                                    "Vérifiez le filtre solaire et le cadrage manuellement."
                        )
                        return@launch
                    }

                    val correctionRa =
                        session.centering.correctionRaHours
                    val correctionDec =
                        session.centering.correctionDecDeg

                    if (
                        correctionRa == null ||
                        correctionDec == null
                    ) {
                        uiState = uiState.copy(
                            isBusy = false,
                            session = session,
                            statusMessage = null,
                            error =
                                "Astrométrie exploitable mais correction AD/DEC indisponible. " +
                                    "Aucun mouvement de monture n'a été envoyé."
                        )
                        return@launch
                    }

                    uiState = uiState.copy(
                        statusMessage =
                            "Recentrage monture • tentative $attempt/4"
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

                uiState = uiState.copy(
                    isBusy = false,
                    statusMessage = null,
                    error =
                        "Centrage non obtenu après 4 corrections astrométriques"
                )

            } catch (error: Exception) {
                stopOperationTimer()
                uiState = uiState.copy(
                    isBusy = false,
                    error =
                        error.message
                            ?: "Erreur de capture / centrage",
                    statusMessage = null
                )
            }
        }
    }

    private fun startOperationTimer(
        expectedSeconds: Double
    ) {
        operationTimerJob?.cancel()

        val expectedMs =
            (expectedSeconds * 1000.0)
                .toLong()
                .coerceAtLeast(1L)
        val startedAt =
            SystemClock.elapsedRealtime()

        uiState = uiState.copy(
            operationElapsedMs = 0L,
            operationExpectedMs = expectedMs,
            operationPhase = "capture"
        )

        operationTimerJob = viewModelScope.launch {
            while (isActive && uiState.isBusy) {
                val elapsed =
                    SystemClock.elapsedRealtime() - startedAt
                uiState = uiState.copy(
                    operationElapsedMs = elapsed,
                    operationPhase =
                        if (elapsed < expectedMs) {
                            "capture"
                        } else {
                            "astrometry"
                        }
                )
                delay(100)
            }
        }
    }

    private fun stopOperationTimer() {
        operationTimerJob?.cancel()
        operationTimerJob = null
        uiState = uiState.copy(
            operationPhase = null
        )
    }

    fun startStacking(serverBaseUrl: String) {
        val session = uiState.session ?: return
        if (
            uiState.isBusy ||
            session.centering.status != "centered"
        ) return

        if (DemoModeState.active) {
            startDemoStacking(session)
            return
        }

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

        if (DemoModeState.active) {
            demoStackJob?.cancel()
            demoStackJob = null
            uiState = uiState.copy(
                session = session.copy(
                    state = "stopped",
                    stacking = session.stacking.copy(
                        running = false
                    )
                ),
                statusMessage = "Stacking démo arrêté"
            )
            return
        }

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

        if (DemoModeState.active) {
            uiState = uiState.copy(
                session = session.copy(
                    state = "finalized",
                    galleryPath = "demo-local"
                ),
                savedToGallery = true,
                statusMessage =
                    "Session de démonstration enregistrée localement dans la démo",
                error = null
            )
            return
        }

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
                throw IllegalStateException(
                    status.detail ?: "Erreur de pointage"
                )
            }
            delay(500)
        }
        throw IllegalStateException(
            "Timeout en attente du recentrage de la monture"
        )
    }

    private fun runDemoCenter() {
        if (uiState.isBusy) return
        val target = uiState.target
        if (target == null) {
            uiState = uiState.copy(
                error = "Aucune cible sélectionnée dans Ciel"
            )
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(
                isBusy = true,
                error = null,
                statusMessage =
                    "MODE DÉMO LOCAL • acquisition fictive"
            )

            ensureSession("demo://local")
            startOperationTimer(uiState.exposureSeconds)

            delay(
                (uiState.exposureSeconds * 1000.0)
                    .toLong()
                    .coerceIn(100L, 4_000L)
            )

            uiState = uiState.copy(
                operationPhase = "astrometry",
                statusMessage =
                    "MODE DÉMO LOCAL • astrométrie fictive"
            )
            delay(650)
            stopOperationTimer()

            val image =
                runCatching {
                    getApplication<Application>()
                        .resources
                        .openRawResource(R.drawable.m103_preview)
                        .use { it.readBytes() }
                }.getOrNull()

            val centered = demoSession(
                target = target,
                centered = true,
                existing = uiState.session
            )

            uiState = uiState.copy(
                isBusy = false,
                session = centered,
                imageBytes = image ?: uiState.imageBytes,
                statusMessage =
                    "MODE DÉMO LOCAL • cible centrée — aucun appel au Raspberry Pi",
                error = null
            )
        }
    }

    private fun demoSession(
        target: CaptureTarget,
        centered: Boolean,
        existing: CaptureSessionStatus? = null
    ): CaptureSessionStatus {
        val base = existing
        val centering =
            CaptureCenteringStatus(
                status = if (centered) "centered" else "not_checked",
                errorArcsec = if (centered) 4.2 else null,
                solveRaDeg = if (centered) target.raHours * 15.0 else null,
                solveDecDeg = if (centered) target.decDeg else null,
                correctionRaHours = if (centered) target.raHours else null,
                correctionDecDeg = if (centered) target.decDeg else null,
                attempts = if (centered) 1 else 0,
                solverStatus = if (centered) "solved" else null,
                solverDetail = if (centered) "Résultat fictif embarqué" else null
            )
        return CaptureSessionStatus(
            id = base?.id ?: "demo-${System.currentTimeMillis()}",
            state = if (centered) "centered" else "framing",
            targetName = target.name,
            targetRaHours = target.raHours,
            targetDecDeg = target.decDeg,
            objectType = target.objectType,
            trackingMode = target.trackingMode,
            exposureSeconds = uiState.exposureSeconds,
            capturedFrames = base?.capturedFrames ?: 0,
            acceptedFrames = base?.acceptedFrames ?: 0,
            rejectedFrames = base?.rejectedFrames ?: 0,
            integrationSeconds = base?.integrationSeconds ?: 0.0,
            centering = centering,
            stacking = base?.stacking
                ?: CaptureStackingStatus(
                    running = false,
                    recenterRequired = false,
                    recenterReason = null,
                    lastRegistrationDxPx = null,
                    lastRegistrationDyPx = null,
                    lastRegistrationDistancePx = null
                ),
            hasPreview = centered,
            hasStackPreview = base?.hasStackPreview ?: false,
            galleryPath = base?.galleryPath
        )
    }

    private fun startDemoStacking(
        session: CaptureSessionStatus
    ) {
        demoStackJob?.cancel()
        uiState = uiState.copy(
            session = session.copy(
                state = "stacking",
                stacking = session.stacking.copy(
                    running = true,
                    recenterRequired = false,
                    recenterReason = null
                )
            ),
            statusMessage =
                "MODE DÉMO LOCAL • stacking fictif en cours",
            error = null
        )

        demoStackJob = viewModelScope.launch {
            while (isActive) {
                delay(900)
                val current = uiState.session ?: break
                if (!current.stacking.running) break
                val nextAccepted = current.acceptedFrames + 1
                val distance =
                    ((nextAccepted % 5) * 0.35) + 0.4
                uiState = uiState.copy(
                    session = current.copy(
                        capturedFrames = current.capturedFrames + 1,
                        acceptedFrames = nextAccepted,
                        integrationSeconds =
                            current.integrationSeconds +
                                current.exposureSeconds,
                        hasStackPreview = true,
                        stacking = current.stacking.copy(
                            lastRegistrationDxPx = distance,
                            lastRegistrationDyPx = distance / 2.0,
                            lastRegistrationDistancePx = distance
                        )
                    ),
                    statusMessage =
                        "MODE DÉMO LOCAL • stacking fictif en cours"
                )
            }
        }
    }

    override fun onCleared() {
        monitorJob?.cancel()
        operationTimerJob?.cancel()
        demoStackJob?.cancel()
        super.onCleared()
    }
}
