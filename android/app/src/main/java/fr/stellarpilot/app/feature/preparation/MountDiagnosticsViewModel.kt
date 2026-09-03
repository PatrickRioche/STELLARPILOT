package fr.stellarpilot.app.feature.preparation

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.stellarpilot.app.data.remote.MountDiagnosticsApiClient
import fr.stellarpilot.app.data.remote.MountDiagnosticsResult
import fr.stellarpilot.app.data.remote.MountGotoCommandClient
import fr.stellarpilot.app.domain.model.SkyStar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs


enum class MovementAxis {
    RA,
    DEC
}


data class MovementTestResult(
    val axis: MovementAxis,
    val label: String,
    val requestedDeltaDeg: Double,
    val measuredDeltaDeg: Double,
    val startRaHours: Double,
    val startDecDeg: Double,
    val endRaHours: Double?,
    val endDecDeg: Double?,
    val passed: Boolean,
    val detail: String
)


data class MountDiagnosticsUiState(
    val isLoading: Boolean = false,
    val status: MountDiagnosticsResult? = null,
    val actionLabel: String? = null,
    val lastMovementTest: MovementTestResult? = null,
    val raValidated: Boolean = false,
    val decValidated: Boolean = false,
    val timeSyncVerified: Boolean = false,
    val timeSyncDetail: String? = null,
    val error: String? = null
)


class MountDiagnosticsViewModel : ViewModel() {

    companion object {
        private const val TAG = "StellarMountTest"
    }

    var uiState by mutableStateOf(MountDiagnosticsUiState())
        private set

    fun refresh(serverBaseUrl: String) {
        if (uiState.isLoading) return

        uiState = uiState.copy(
            isLoading = true,
            error = null
        )

        viewModelScope.launch {
            try {
                val api = MountDiagnosticsApiClient()
                val status = api.status(serverBaseUrl)
                val timeVerification = try {
                    api.timeVerification(serverBaseUrl)
                } catch (_: Exception) {
                    null
                }

                uiState = uiState.copy(
                    isLoading = false,
                    status = status,
                    timeSyncVerified =
                        timeVerification?.verified == true &&
                            timeVerification.controlReady,
                    timeSyncDetail = timeVerification?.detail,
                    error = null
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    error = error.message ?: "État monture indisponible"
                )
            }
        }
    }

    fun nudge(
        serverBaseUrl: String,
        deltaRaHours: Double = 0.0,
        deltaDecDeg: Double = 0.0,
        label: String
    ) {
        val current = uiState.status
        val ra = current?.raHours
        val dec = current?.decDeg

        if (ra == null || dec == null) {
            uiState = uiState.copy(
                error = "Actualisez d'abord la position de la monture."
            )
            return
        }

        val axis =
            if (abs(deltaRaHours) > 0.0) MovementAxis.RA
            else MovementAxis.DEC

        runGoto(
            serverBaseUrl = serverBaseUrl,
            raHours = (ra + deltaRaHours + 24.0) % 24.0,
            decDeg = (dec + deltaDecDeg).coerceIn(-90.0, 90.0),
            trackingMode = "sidereal",
            coordinateFrame = "mount",
            label = label,
            movementTest = MovementTestRequest(
                axis = axis,
                startRaHours = ra,
                startDecDeg = dec,
                requestedDeltaDeg =
                    if (axis == MovementAxis.RA) deltaRaHours * 15.0
                    else deltaDecDeg
            )
        )
    }

    fun gotoStar(
        serverBaseUrl: String,
        star: SkyStar
    ) {
        runGoto(
            serverBaseUrl = serverBaseUrl,
            raHours = star.raHours,
            decDeg = star.decDeg,
            trackingMode = "sidereal",
            coordinateFrame = "j2000",
            label = "Pointage ${star.name}",
            movementTest = null
        )
    }

    private data class MovementTestRequest(
        val axis: MovementAxis,
        val startRaHours: Double,
        val startDecDeg: Double,
        val requestedDeltaDeg: Double
    )

    private suspend fun ensureTimeSynchronization(
        serverBaseUrl: String,
        api: MountDiagnosticsApiClient
    ) {
        val base = serverBaseUrl.trimEnd('/') + "/"

        val before = try {
            api.timeVerification(base)
        } catch (_: Exception) {
            null
        }

        if (before?.verified == true && before.controlReady) {
            uiState = uiState.copy(
                timeSyncVerified = true,
                timeSyncDetail = before.detail
            )
            return
        }

        uiState = uiState.copy(
            actionLabel = "Synchronisation horaire OnStep",
            timeSyncVerified = false,
            error = null
        )

        api.syncTime(base)

        val after = api.timeVerification(base)
        val ready = after.verified && after.controlReady

        uiState = uiState.copy(
            timeSyncVerified = ready,
            timeSyncDetail = after.detail
        )

        if (!ready) {
            throw IllegalStateException(
                after.detail
                    ?: "Synchronisation horaire OnStep non validée"
            )
        }
    }

    private fun runGoto(
        serverBaseUrl: String,
        raHours: Double,
        decDeg: Double,
        trackingMode: String,
        coordinateFrame: String,
        label: String,
        movementTest: MovementTestRequest?
    ) {
        if (uiState.isLoading) return

        uiState = uiState.copy(
            isLoading = true,
            actionLabel = label,
            lastMovementTest = if (movementTest != null) null else uiState.lastMovementTest,
            error = null
        )

        viewModelScope.launch {
            try {
                val base = serverBaseUrl.trimEnd('/') + "/"
                val diagnosticsApi = MountDiagnosticsApiClient()

                ensureTimeSynchronization(
                    serverBaseUrl = base,
                    api = diagnosticsApi
                )

                uiState = uiState.copy(
                    actionLabel = label
                )

                MountGotoCommandClient(base).gotoMount(
                    raHours = raHours,
                    decDeg = decDeg,
                    trackingMode = trackingMode,
                    coordinateFrame = coordinateFrame
                )

                var lastStatus: MountDiagnosticsResult? = null

                for (attempt in 0 until 25) {
                    delay(700)
                    lastStatus = diagnosticsApi.status(base)

                    val state = lastStatus?.status?.lowercase()
                    if (state == "tracking" || state == "idle") {
                        break
                    }
                }

                val testResult = movementTest?.let {
                    evaluateMovement(
                        request = it,
                        label = label,
                        end = lastStatus
                    )
                }

                testResult?.let { test ->
                    Log.i(
                        TAG,
                        "axis=${test.axis} label=${test.label} " +
                            "passed=${test.passed} requested_deg=${test.requestedDeltaDeg} " +
                            "measured_deg=${test.measuredDeltaDeg} " +
                            "start_ra_h=${test.startRaHours} start_dec_deg=${test.startDecDeg} " +
                            "end_ra_h=${test.endRaHours} end_dec_deg=${test.endDecDeg}"
                    )
                }

                uiState = uiState.copy(
                    isLoading = false,
                    status = lastStatus,
                    actionLabel = null,
                    lastMovementTest = testResult ?: uiState.lastMovementTest,
                    raValidated = uiState.raValidated ||
                        (testResult?.axis == MovementAxis.RA && testResult.passed),
                    decValidated = uiState.decValidated ||
                        (testResult?.axis == MovementAxis.DEC && testResult.passed),
                    error = null
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    actionLabel = null,
                    error = error.message ?: "Commande monture impossible"
                )
            }
        }
    }

    private fun evaluateMovement(
        request: MovementTestRequest,
        label: String,
        end: MountDiagnosticsResult?
    ): MovementTestResult {
        val endRa = end?.raHours
        val endDec = end?.decDeg

        val measured = when (request.axis) {
            MovementAxis.RA -> {
                if (endRa == null) 0.0
                else signedHourDelta(request.startRaHours, endRa) * 15.0
            }
            MovementAxis.DEC -> {
                if (endDec == null) 0.0
                else endDec - request.startDecDeg
            }
        }

        val threshold = maxOf(
            0.03,
            abs(request.requestedDeltaDeg) * 0.25
        )
        val directionOk =
            measured == 0.0 || request.requestedDeltaDeg == 0.0 ||
                measured * request.requestedDeltaDeg > 0.0
        val passed =
            endRa != null && endDec != null &&
                abs(measured) >= threshold && directionOk

        val detail = if (passed) {
            "PASS • déplacement mesuré ${formatDelta(measured)}°"
        } else {
            "FAIL • demandé ${formatDelta(request.requestedDeltaDeg)}° ; " +
                "mesuré ${formatDelta(measured)}°"
        }

        return MovementTestResult(
            axis = request.axis,
            label = label,
            requestedDeltaDeg = request.requestedDeltaDeg,
            measuredDeltaDeg = measured,
            startRaHours = request.startRaHours,
            startDecDeg = request.startDecDeg,
            endRaHours = endRa,
            endDecDeg = endDec,
            passed = passed,
            detail = detail
        )
    }

    private fun signedHourDelta(startHours: Double, endHours: Double): Double {
        var delta = (endHours - startHours) % 24.0
        if (delta > 12.0) delta -= 24.0
        if (delta < -12.0) delta += 24.0
        return delta
    }

    private fun formatDelta(value: Double): String =
        if (value >= 0.0) "+%.3f".format(value) else "%.3f".format(value)
}
