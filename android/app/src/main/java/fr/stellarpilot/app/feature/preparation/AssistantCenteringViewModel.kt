package fr.stellarpilot.app.feature.preparation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.stellarpilot.app.data.remote.CaptureSessionApiClient
import fr.stellarpilot.app.data.remote.CaptureSessionStatus
import fr.stellarpilot.app.data.remote.MountDiagnosticsApiClient
import fr.stellarpilot.app.data.remote.MountDiagnosticsResult
import fr.stellarpilot.app.data.remote.MountGotoCommandClient
import fr.stellarpilot.app.domain.model.SkyStar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


data class AssistantCenteringUiState(
    val isLoading: Boolean = false,
    val targetName: String? = null,
    val sessionId: String? = null,
    val imageBytes: ByteArray? = null,
    val status: String? = null,
    val errorArcsec: Double? = null,
    val attempts: Int = 0,
    val centered: Boolean = false,
    val manualRequired: Boolean = false,
    val actionLabel: String? = null,
    val message: String? = null,
    val error: String? = null
)


class AssistantCenteringViewModel : ViewModel() {

    companion object {
        const val CENTERING_EXPOSURE_SECONDS = 4.0
        private const val MAX_AUTO_CORRECTIONS = 3
        private const val MANUAL_STEP_DEG = 0.10
    }

    var uiState by mutableStateOf(AssistantCenteringUiState())
        private set

    fun reset() {
        uiState = AssistantCenteringUiState()
    }

    fun gotoAndCenter(
        serverBaseUrl: String,
        star: SkyStar
    ) {
        if (uiState.isLoading) return

        uiState = AssistantCenteringUiState(
            isLoading = true,
            targetName = star.name,
            actionLabel = "Pointage ${star.name}",
            message = "GOTO vers ${star.name}…"
        )

        viewModelScope.launch {
            try {
                val base = serverBaseUrl.trimEnd('/') + "/"
                ensureMountReady(base)

                MountGotoCommandClient(base).gotoMount(
                    raHours = star.raHours,
                    decDeg = star.decDeg,
                    trackingMode = "sidereal",
                    coordinateFrame = "j2000"
                )
                waitForMount(base)

                val captureApi = CaptureSessionApiClient(base)
                var session = captureApi.createSession(
                    targetName = star.name,
                    targetRaHours = star.raHours,
                    targetDecDeg = star.decDeg,
                    objectType = "star",
                    trackingMode = "sidereal",
                    exposureSeconds = CENTERING_EXPOSURE_SECONDS
                )

                uiState = uiState.copy(
                    sessionId = session.id,
                    actionLabel = "Recentrage automatique",
                    message = "Pose 4 s et astrométrie de centrage…"
                )

                session = autoCenterLoop(
                    baseUrl = base,
                    captureApi = captureApi,
                    initialSession = session
                )

                if (session.centering.status == "centered") {
                    uiState = uiState.copy(
                        isLoading = false,
                        centered = true,
                        manualRequired = false,
                        actionLabel = null,
                        status = session.centering.status,
                        errorArcsec = session.centering.errorArcsec,
                        attempts = session.centering.attempts,
                        message = "${star.name} centrée automatiquement ✓",
                        error = null
                    )
                } else {
                    uiState = uiState.copy(
                        isLoading = false,
                        centered = false,
                        manualRequired = true,
                        actionLabel = null,
                        status = session.centering.status,
                        errorArcsec = session.centering.errorArcsec,
                        attempts = session.centering.attempts,
                        message =
                            "Recentrage automatique non validé. Utilisez le joystick puis Vérifier.",
                        error = null
                    )
                }
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    centered = false,
                    manualRequired = true,
                    actionLabel = null,
                    message = "Le recentrage automatique s'est arrêté en sécurité.",
                    error = error.message ?: "Recentrage impossible"
                )
            }
        }
    }

    private suspend fun autoCenterLoop(
        baseUrl: String,
        captureApi: CaptureSessionApiClient,
        initialSession: CaptureSessionStatus
    ): CaptureSessionStatus {
        var session = initialSession

        for (attempt in 1..MAX_AUTO_CORRECTIONS) {
            uiState = uiState.copy(
                actionLabel = "Centrage $attempt/$MAX_AUTO_CORRECTIONS",
                message = "Pose 4 s • résolution • mesure de l'écart…"
            )

            session = captureApi.centerStep(session.id)
            val preview = runCatching {
                captureApi.getPreview(session.id, stack = false)
            }.getOrNull()

            uiState = uiState.copy(
                imageBytes = preview ?: uiState.imageBytes,
                status = session.centering.status,
                errorArcsec = session.centering.errorArcsec,
                attempts = session.centering.attempts
            )

            if (session.centering.status == "centered") {
                return session
            }

            val correctionRa = session.centering.correctionRaHours
            val correctionDec = session.centering.correctionDecDeg
            if (
                session.centering.status != "correction_required" ||
                correctionRa == null ||
                correctionDec == null
            ) {
                return session
            }

            // Server centering returns a J2000 correction. Each automatic move
            // is followed by a fresh 4 s capture and solve; never chain blind
            // corrections without measuring again.
            MountGotoCommandClient(baseUrl).gotoMount(
                raHours = correctionRa,
                decDeg = correctionDec,
                trackingMode = "sidereal",
                coordinateFrame = "j2000"
            )
            waitForMount(baseUrl)
        }

        return session
    }

    fun nudgeManual(
        serverBaseUrl: String,
        direction: String
    ) {
        if (uiState.isLoading) return

        uiState = uiState.copy(
            isLoading = true,
            actionLabel = "Joystick $direction",
            error = null
        )

        viewModelScope.launch {
            try {
                val base = serverBaseUrl.trimEnd('/') + "/"
                ensureMountReady(base)
                val diagnostics = MountDiagnosticsApiClient()
                val mount = diagnostics.status(base)
                val ra = mount.raHours
                    ?: error("RA OnStep indisponible")
                val dec = mount.decDeg
                    ?: error("DEC OnStep indisponible")

                val normalized = direction.uppercase()
                    .replace("O", "W")
                val raSign = when {
                    "E" in normalized -> 1.0
                    "W" in normalized -> -1.0
                    else -> 0.0
                }
                val decSign = when {
                    "N" in normalized -> 1.0
                    "S" in normalized -> -1.0
                    else -> 0.0
                }

                require(raSign != 0.0 || decSign != 0.0) {
                    "Direction joystick invalide"
                }

                val targetRa = (
                    ra + raSign * (MANUAL_STEP_DEG / 15.0) + 24.0
                ) % 24.0
                val targetDec = (
                    dec + decSign * MANUAL_STEP_DEG
                ).coerceIn(-90.0, 90.0)

                MountGotoCommandClient(base).gotoMount(
                    raHours = targetRa,
                    decDeg = targetDec,
                    trackingMode = "sidereal",
                    coordinateFrame = "mount"
                )
                waitForMount(base)

                uiState = uiState.copy(
                    isLoading = false,
                    centered = false,
                    manualRequired = true,
                    actionLabel = null,
                    message = "Déplacement $direction effectué • vérifier avec une pose de 4 s",
                    error = null
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    actionLabel = null,
                    error = error.message ?: "Déplacement joystick impossible"
                )
            }
        }
    }

    fun verifyManualCentering(serverBaseUrl: String) {
        val sessionId = uiState.sessionId ?: return
        if (uiState.isLoading) return

        uiState = uiState.copy(
            isLoading = true,
            actionLabel = "Vérification du centrage",
            message = "Pose 4 s et mesure…",
            error = null
        )

        viewModelScope.launch {
            try {
                val base = serverBaseUrl.trimEnd('/') + "/"
                val api = CaptureSessionApiClient(base)
                val session = api.centerStep(sessionId)
                val preview = runCatching {
                    api.getPreview(sessionId, stack = false)
                }.getOrNull()
                val centered = session.centering.status == "centered"

                uiState = uiState.copy(
                    isLoading = false,
                    imageBytes = preview ?: uiState.imageBytes,
                    status = session.centering.status,
                    errorArcsec = session.centering.errorArcsec,
                    attempts = session.centering.attempts,
                    centered = centered,
                    manualRequired = !centered,
                    actionLabel = null,
                    message =
                        if (centered) {
                            "Étoile centrée ✓"
                        } else {
                            "Encore décentrée : corrigez avec le joystick."
                        },
                    error = null
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    actionLabel = null,
                    manualRequired = true,
                    error = error.message ?: "Vérification impossible"
                )
            }
        }
    }

    private suspend fun ensureMountReady(baseUrl: String) {
        val api = MountDiagnosticsApiClient()
        val before = runCatching { api.timeVerification(baseUrl) }.getOrNull()
        if (before?.verified == true && before.controlReady) return

        api.syncTime(baseUrl)
        val after = api.timeVerification(baseUrl)
        check(after.verified && after.controlReady) {
            after.detail ?: "Synchronisation horaire OnStep non validée"
        }
    }

    private suspend fun waitForMount(baseUrl: String): MountDiagnosticsResult {
        val api = MountDiagnosticsApiClient()
        var last: MountDiagnosticsResult? = null

        repeat(35) {
            delay(700)
            last = api.status(baseUrl)
            val state = last?.status?.lowercase()
            val remaining = last?.remainingDeg
            if (
                state in setOf("tracking", "idle") &&
                (remaining == null || remaining <= 0.02)
            ) {
                return last!!
            }
        }

        return last ?: error("État monture indisponible après le GOTO")
    }
}
