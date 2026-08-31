package fr.stellarpilot.app.feature.sky

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.stellarpilot.app.data.demo.DemoSkyObjectsDataSource
import fr.stellarpilot.app.data.remote.MountGotoCommandClient
import fr.stellarpilot.app.data.remote.SkyObjectsApiClient
import fr.stellarpilot.app.data.remote.StellarPilotApiClient
import fr.stellarpilot.app.domain.model.SkyObject
import fr.stellarpilot.app.domain.model.SkyObjectsResult
import fr.stellarpilot.app.feature.demo.DemoModeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


data class SkyTargetUiState(
    val isLoading: Boolean = false,
    val result: SkyObjectsResult? = null,
    val selected: SkyObject? = null,
    val error: String? = null,
    val isGotoLoading: Boolean = false,
    val gotoMessage: String? = null,
    val gotoError: String? = null,
    val gotoStatus: String? = null,
    val gotoProgress: Double? = null,
    val gotoCurrentRa: Double? = null,
    val gotoCurrentDec: Double? = null,
    val gotoTargetRa: Double? = null,
    val gotoTargetDec: Double? = null,
    val gotoIndiState: String? = null,
    val gotoVirtualPosition: Boolean = false
)


class SkyTargetViewModel : ViewModel() {

    private var observerLatitude: Double? = null
    private var observerLongitude: Double? = null

    private var currentSort = "magnitude"
    private var currentOrder = "asc"

    var uiState by mutableStateOf(
        SkyTargetUiState()
    )
        private set

    private fun trackingModeFor(
        target: SkyObject
    ): String =
        when (
            target.objectType.lowercase()
        ) {
            "sun" -> "solar"
            "moon" -> "lunar"
            else -> "sidereal"
        }

    private fun trackingLabelFor(
        trackingMode: String
    ): String =
        when (trackingMode) {
            "solar" -> "solar"
            "lunar" -> "lunar"
            else -> "sidéral"
        }

    private fun motionStatusLabel(
        motionStatus: String,
        trackingLabel: String
    ): String {
        val normalizedStatus =
            motionStatus
                .trim()
                .lowercase()

        return when (normalizedStatus) {
            "tracking" ->
                "tracking $trackingLabel"

            "slewing" ->
                "slewing • tracking $trackingLabel"

            "starting" ->
                "starting • tracking $trackingLabel"

            else ->
                "$normalizedStatus • tracking $trackingLabel"
        }
    }

    fun load(
        serverBaseUrl: String,
        category: String,
        query: String,
        minAltitude: Double = 15.0,
        direction: String? = null,
        constellation: String = "",
        latitude: Double? = null,
        longitude: Double? = null,
        sort: String? = null,
        order: String? = null,
        offset: Int = 0
    ) {

        if (uiState.isLoading) return

        if (
            latitude != null &&
            longitude != null
        ) {
            observerLatitude = latitude
            observerLongitude = longitude
        }

        if (
            DemoModeState.active &&
            (
                observerLatitude == null ||
                observerLongitude == null
            )
        ) {
            observerLatitude = 47.4308
            observerLongitude = -0.6271
        }

        val currentLatitude =
            observerLatitude

        val currentLongitude =
            observerLongitude

        if (
            currentLatitude == null ||
            currentLongitude == null
        ) {
            uiState =
                uiState.copy(
                    isLoading = false,
                    error =
                        "Position observateur indisponible"
                )

            return
        }

        if (sort != null) {
            currentSort = sort
        }

        if (order != null) {
            currentOrder = order
        }

        uiState =
            uiState.copy(
                isLoading = true,
                error = null
            )

        viewModelScope.launch {

            try {

                val result =
                    if (
                        DemoModeState.active
                    ) {
                        DemoSkyObjectsDataSource
                            .getObjects(
                                latitude =
                                    currentLatitude,
                                longitude =
                                    currentLongitude,
                                category =
                                    category,
                                query =
                                    query,
                                minAltitude =
                                    minAltitude,
                                direction =
                                    direction,
                                constellation =
                                    constellation,
                                sort =
                                    currentSort,
                                order =
                                    currentOrder,
                                offset =
                                    offset
                                        .coerceAtLeast(
                                            0
                                        ),
                                limit = 30
                            )
                    } else {
                        SkyObjectsApiClient(
                            serverBaseUrl
                        ).getObjects(
                            latitude =
                                currentLatitude,
                            longitude =
                                currentLongitude,
                            category =
                                category,
                            query =
                                query,
                            minAltitude =
                                minAltitude,
                            direction =
                                direction,
                            constellation =
                                constellation,
                            sort =
                                currentSort,
                            order =
                                currentOrder,
                            offset =
                                offset
                                    .coerceAtLeast(
                                        0
                                    ),
                            limit = 30
                        )
                    }

                uiState =
                    uiState.copy(
                        isLoading = false,
                        result = result,
                        error = null
                    )

            } catch (
                error: Exception
            ) {

                uiState =
                    uiState.copy(
                        isLoading = false,
                        error =
                            error.message
                                ?: "Erreur catalogue"
                    )
            }
        }
    }

    fun select(
        target: SkyObject
    ) {

        uiState =
            uiState.copy(
                selected = target,
                gotoMessage = null,
                gotoError = null
            )
    }

    fun gotoTarget(
        serverBaseUrl: String,
        target: SkyObject
    ) {

        if (uiState.isGotoLoading) return

        val trackingMode =
            trackingModeFor(target)

        val trackingLabel =
            trackingLabelFor(
                trackingMode
            )

        uiState =
            uiState.copy(
                isGotoLoading = true,
                gotoMessage =
                    "Pointage en cours...",
                gotoError = null,
                gotoStatus =
                    motionStatusLabel(
                        "starting",
                        trackingLabel
                    ),
                gotoProgress = 0.0,
                gotoCurrentRa = null,
                gotoCurrentDec = null,
                gotoTargetRa = target.raHours,
                gotoTargetDec = target.decDeg,
                gotoIndiState = null,
                gotoVirtualPosition = DemoModeState.active
            )

        if (DemoModeState.active) {
            viewModelScope.launch {
                runDemoGoto(
                    target = target,
                    trackingLabel = trackingLabel
                )
            }
            return
        }

        viewModelScope.launch {

            try {

                val api =
                    StellarPilotApiClient(
                        serverBaseUrl
                    )

                MountGotoCommandClient(
                    serverBaseUrl
                ).gotoMount(
                    raHours =
                        target.raHours,
                    decDeg =
                        target.decDeg,
                    trackingMode =
                        trackingMode
                )

                repeat(240) {

                    delay(500)

                    val motion =
                        api.getMountMotionStatus()

                    if (
                        motion.status ==
                            "error"
                    ) {
                        error(
                            motion.detail
                                ?: "Erreur de suivi monture"
                        )
                    }

                    val done =
                        motion.status == "tracking"

                    uiState =
                        uiState.copy(
                            isGotoLoading = !done,
                            gotoMessage =
                                if (done) {
                                    "Position cible atteinte"
                                } else {
                                    "Pointage en cours..."
                                },
                            gotoError = null,
                            gotoStatus =
                                motionStatusLabel(
                                    motion.status,
                                    trackingLabel
                                ),
                            gotoProgress =
                                motion.progress,
                            gotoCurrentRa =
                                motion.raHours,
                            gotoCurrentDec =
                                motion.decDeg,
                            gotoTargetRa =
                                motion.targetRaHours
                                    ?: target.raHours,
                            gotoTargetDec =
                                motion.targetDecDeg
                                    ?: target.decDeg,
                            gotoIndiState =
                                motion.indiState,
                            gotoVirtualPosition =
                                DemoModeState.active &&
                                    motion.virtualPosition
                        )

                    if (done) {
                        return@launch
                    }
                }

                uiState =
                    uiState.copy(
                        isGotoLoading = false,
                        gotoMessage =
                            "Pointage toujours en cours apres 120 s",
                        gotoError = null
                    )

            } catch (
                error: Exception
            ) {

                uiState =
                    uiState.copy(
                        isGotoLoading = false,
                        gotoMessage = null,
                        gotoError =
                            error.message
                                ?: "Erreur de pointage"
                    )
            }
        }
    }

    private suspend fun runDemoGoto(
        target: SkyObject,
        trackingLabel: String
    ) {

        val startRa =
            (
                target.raHours +
                    23.5
                ) % 24.0

        val startDec =
            (
                target.decDeg -
                    4.0
                ).coerceIn(
                    -90.0,
                    90.0
                )

        val steps = 20

        repeat(steps) { index ->

            delay(75)

            val progress =
                (
                    index + 1
                    ).toDouble() /
                    steps.toDouble()

            val currentRa =
                startRa +
                    (
                        target.raHours -
                            startRa
                        ) * progress

            val currentDec =
                startDec +
                    (
                        target.decDeg -
                            startDec
                        ) * progress

            val motionStatus =
                if (
                    progress < 1.0
                ) {
                    "slewing"
                } else {
                    "tracking"
                }

            uiState =
                uiState.copy(
                    isGotoLoading =
                        progress < 1.0,
                    gotoMessage =
                        if (
                            progress < 1.0
                        ) {
                            "Pointage Demo en cours..."
                        } else {
                            "Position cible atteinte"
                        },
                    gotoError = null,
                    gotoStatus =
                        motionStatusLabel(
                            motionStatus,
                            trackingLabel
                        ),
                    gotoProgress =
                        progress,
                    gotoCurrentRa =
                        currentRa,
                    gotoCurrentDec =
                        currentDec,
                    gotoTargetRa =
                        target.raHours,
                    gotoTargetDec =
                        target.decDeg,
                    gotoIndiState =
                        "Demo locale",
                    gotoVirtualPosition =
                        true
                )
        }
    }
}
