package fr.stellarpilot.app.feature.preparation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.stellarpilot.app.domain.model.SkyStar
import fr.stellarpilot.app.feature.connection.ConnectionViewModel
import fr.stellarpilot.app.feature.sky.SkyViewModel
import fr.stellarpilot.app.ui.components.StellarImagePreview
import fr.stellarpilot.app.ui.theme.StellarBackground
import fr.stellarpilot.app.ui.theme.StellarBorder
import fr.stellarpilot.app.ui.theme.StellarGreen
import fr.stellarpilot.app.ui.theme.StellarMuted
import fr.stellarpilot.app.ui.theme.StellarOrange
import fr.stellarpilot.app.ui.theme.StellarRed
import fr.stellarpilot.app.ui.theme.StellarSurface
import fr.stellarpilot.app.ui.theme.StellarText
import java.util.Locale


private val assistantSteps = listOf(
    "Connexion",
    "Astrométrie",
    "Bahtinov",
    "Darks",
    "Bilan"
)

private val assistantDirections = listOf(
    "N", "NE", "E", "SE", "S", "SO", "O", "NO"
)


@Composable
fun AssistantFinalScreen(
    onOpenSky: () -> Unit,
    connectionViewModel: ConnectionViewModel = viewModel()
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var selectedOrientation by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedStarId by rememberSaveable { mutableStateOf<String?>(null) }
    var maskInstalled by rememberSaveable { mutableStateOf(false) }
    var maskRemoved by rememberSaveable { mutableStateOf(false) }
    var telescopeCapped by rememberSaveable { mutableStateOf(false) }

    val connectionState = connectionViewModel.uiState
    val baseUrl = connectionState.serverBaseUrl

    val mountViewModel: MountDiagnosticsViewModel = viewModel()
    val mountState = mountViewModel.uiState

    val astrometryViewModel: CameraPreviewViewModel = viewModel()
    val astrometryState = astrometryViewModel.uiState

    val skyViewModel: SkyViewModel = viewModel()
    val skyState = skyViewModel.uiState

    val centeringViewModel: AssistantCenteringViewModel = viewModel()
    val centeringState = centeringViewModel.uiState

    val bahtinovViewModel: BahtinovViewModel = viewModel()
    val bahtinovState = bahtinovViewModel.uiState

    val darkViewModel: DarkCalibrationViewModel = viewModel()
    val darkState = darkViewModel.uiState

    LaunchedEffect(Unit) {
        connectionViewModel.connect()
        mountViewModel.refresh(baseUrl)
    }

    LaunchedEffect(step, baseUrl) {
        when (step) {
            0 -> mountViewModel.refresh(baseUrl)
            2 -> skyViewModel.load(baseUrl)
        }
    }

    val focusStars = remember(skyState.sky) {
        skyState.sky?.stars
            .orEmpty()
            .filter { it.aboveHorizon && it.altitudeDeg >= 20.0 }
            .sortedWith(
                compareBy<SkyStar> { it.magnitude }
                    .thenByDescending { it.altitudeDeg }
            )
            .take(10)
    }

    val selectedStar = focusStars.firstOrNull { it.id == selectedStarId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "ASSISTANT STELLARPILOT",
            color = StellarOrange,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Étape ${step + 1}/${assistantSteps.size} • ${assistantSteps[step]}",
            color = StellarText,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = (step + 1).toFloat() / assistantSteps.size.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp),
            color = StellarOrange
        )
        Spacer(Modifier.height(16.dp))

        when (step) {
            0 -> AssistantConnectionStep(
                connectionViewModel = connectionViewModel,
                mountViewModel = mountViewModel,
                onContinue = { step = 1 }
            )

            1 -> AssistantAstrometryStep(
                selectedOrientation = selectedOrientation,
                onOrientation = { selectedOrientation = it },
                state = astrometryState,
                mountState = mountState,
                onCapture = {
                    astrometryViewModel.load(
                        serverBaseUrl = baseUrl,
                        exposureSeconds = 4.0
                    )
                },
                onNudge = { direction ->
                    nudgeDirection(
                        viewModel = mountViewModel,
                        serverBaseUrl = baseUrl,
                        direction = direction
                    )
                },
                onPrevious = { step = 0 },
                onContinue = { step = 2 }
            )

            2 -> AssistantBahtinovStep(
                stars = focusStars,
                selectedStar = selectedStar,
                selectedStarId = selectedStarId,
                skyLoading = skyState.isLoading,
                skyError = skyState.error,
                centeringState = centeringState,
                bahtinovState = bahtinovState,
                maskInstalled = maskInstalled,
                maskRemoved = maskRemoved,
                onSelectStar = {
                    selectedStarId = it.id
                    maskInstalled = false
                    maskRemoved = false
                    centeringViewModel.reset()
                },
                onRefreshStars = { skyViewModel.load(baseUrl) },
                onGotoAndCenter = {
                    selectedStar?.let {
                        centeringViewModel.gotoAndCenter(baseUrl, it)
                    }
                },
                onManualNudge = {
                    centeringViewModel.nudgeManual(baseUrl, it)
                },
                onVerifyCentering = {
                    centeringViewModel.verifyManualCentering(baseUrl)
                },
                onMaskInstalled = {
                    maskInstalled = true
                    maskRemoved = false
                },
                onFocusCapture = {
                    bahtinovViewModel.captureFocus(baseUrl)
                },
                onMaskRemoved = { maskRemoved = true },
                onPrevious = { step = 1 },
                onContinue = {
                    telescopeCapped = false
                    darkViewModel.reset()
                    step = 3
                }
            )

            3 -> AssistantDarkStep(
                state = darkState,
                telescopeCapped = telescopeCapped,
                onCapped = { telescopeCapped = true },
                onStart = { darkViewModel.start(baseUrl) },
                onCapture = { darkViewModel.captureNext(baseUrl) },
                onPrevious = { step = 2 },
                onContinue = { step = 4 }
            )

            else -> AssistantSummaryStep(
                connectionViewModel = connectionViewModel,
                mountState = mountState,
                orientation = selectedOrientation,
                astrometryState = astrometryState,
                selectedStar = selectedStar,
                centeringState = centeringState,
                bahtinovState = bahtinovState,
                darkState = darkState,
                onPrevious = { step = 3 },
                onOpenSky = onOpenSky
            )
        }
    }
}


@Composable
private fun AssistantConnectionStep(
    connectionViewModel: ConnectionViewModel,
    mountViewModel: MountDiagnosticsViewModel,
    onContinue: () -> Unit
) {
    val state = connectionViewModel.uiState
    val server = state.server
    val mount = server?.devices?.mount
    val camera = server?.devices?.camera
    val gps = server?.devices?.gps
    val mountState = mountViewModel.uiState

    val mountReady = mount?.status?.lowercase() in setOf("ready", "ok", "online")
    val cameraReady = camera?.status?.lowercase() in setOf("ready", "ok", "online")
    val gpsReady = gps?.status?.lowercase() == "fix"
    val timeReady = mountState.timeSyncVerified
    val ready = server != null && mountReady && cameraReady && gpsReady && timeReady

    AssistantCard("Connexion et contrôle du setup") {
        StatusLine("Serveur StellarPilot", server?.status == "ok" || server?.status == "ready")
        StatusLine("Monture OnStep", mountReady, mount?.status ?: "indisponible")
        StatusLine("Caméra", cameraReady, camera?.name ?: camera?.status ?: "indisponible")
        StatusLine(
            "GPS",
            gpsReady,
            if (gpsReady) {
                "fix • ${gps?.latitude ?: "?"}, ${gps?.longitude ?: "?"}"
            } else {
                gps?.status ?: "indisponible"
            }
        )
        StatusLine(
            "Heure OnStep",
            timeReady,
            mountState.timeSyncDetail ?: "à vérifier"
        )

        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = StellarRed)
        }
        mountState.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = StellarRed)
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                connectionViewModel.connect()
                mountViewModel.refresh(state.serverBaseUrl)
            },
            enabled = !state.isConnecting && !mountState.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Actualiser tous les contrôles")
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onContinue,
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
            colors = assistantPrimaryButtonColors()
        ) {
            Text("Continuer vers l'astrométrie", fontWeight = FontWeight.Bold)
        }
    }
}


@Composable
private fun AssistantAstrometryStep(
    selectedOrientation: String?,
    onOrientation: (String) -> Unit,
    state: CameraPreviewUiState,
    mountState: MountDiagnosticsUiState,
    onCapture: () -> Unit,
    onNudge: (String) -> Unit,
    onPrevious: () -> Unit,
    onContinue: () -> Unit
) {
    val solved = state.solveStatus == "solved"
    val synced = state.mountSyncStatus == "synced"
    val validated = solved && synced && selectedOrientation != null

    AssistantCard("Astrométrie et orientation") {
        Text(
            "Indiquez la direction approximative du tube puis faites une pose de 4 s. " +
                "StellarPilot résout le champ et synchronise OnStep.",
            color = StellarText
        )
        Spacer(Modifier.height(10.dp))
        DirectionSelector(selectedOrientation, onOrientation)
        Spacer(Modifier.height(12.dp))

        StellarImagePreview(
            imageBytes = state.imageBytes,
            contentDescription = "Capture astrométrique",
            loadingText = if (state.isLoading) "Pose 4 s / analyse…" else null,
            showCrosshair = true
        )
        Spacer(Modifier.height(8.dp))

        StatusValue("Pose", "4,0 s")
        StatusValue("Qualité", state.qualityScore?.let { "$it/100 • ${state.qualityLabel ?: ""}" } ?: "—")
        StatusValue("Étoiles", state.qualityStarCount?.toString() ?: "—")
        StatusValue("Astrométrie", state.solveStatus ?: "à lancer")
        StatusValue("SYNC OnStep", state.mountSyncStatus ?: "—")
        state.ra?.let { StatusValue("RA résolue", String.format(Locale.FRANCE, "%.5f°", it)) }
        state.dec?.let { StatusValue("DEC résolue", String.format(Locale.FRANCE, "%+.5f°", it)) }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onCapture,
            enabled = !state.isLoading && !mountState.isLoading,
            modifier = Modifier.fillMaxWidth(),
            colors = assistantPrimaryButtonColors()
        ) {
            Text(if (state.imageBytes == null) "CAPTURER • 4 s" else "ENCORE • 4 s")
        }

        Spacer(Modifier.height(14.dp))
        Text("Joystick monture", color = StellarText, fontWeight = FontWeight.Bold)
        Text(
            "Petits déplacements de 0,10° pour corriger manuellement le cadrage.",
            color = StellarMuted
        )
        Spacer(Modifier.height(8.dp))
        DirectionJoystick(
            enabled = !state.isLoading && !mountState.isLoading,
            onDirection = onNudge
        )

        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = StellarRed)
        }
        mountState.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = StellarRed)
        }

        Spacer(Modifier.height(14.dp))
        NavigationButtons(
            onPrevious = onPrevious,
            onContinue = onContinue,
            continueEnabled = validated,
            continueText = "Suite • mise au point"
        )
    }
}


@Composable
private fun AssistantBahtinovStep(
    stars: List<SkyStar>,
    selectedStar: SkyStar?,
    selectedStarId: String?,
    skyLoading: Boolean,
    skyError: String?,
    centeringState: AssistantCenteringUiState,
    bahtinovState: BahtinovUiState,
    maskInstalled: Boolean,
    maskRemoved: Boolean,
    onSelectStar: (SkyStar) -> Unit,
    onRefreshStars: () -> Unit,
    onGotoAndCenter: () -> Unit,
    onManualNudge: (String) -> Unit,
    onVerifyCentering: () -> Unit,
    onMaskInstalled: () -> Unit,
    onFocusCapture: () -> Unit,
    onMaskRemoved: () -> Unit,
    onPrevious: () -> Unit,
    onContinue: () -> Unit
) {
    AssistantCard("Mise au point Bahtinov") {
        Text(
            "Choisissez une étoile brillante. StellarPilot fait le GOTO puis tente de la centrer automatiquement.",
            color = StellarText
        )
        Spacer(Modifier.height(10.dp))

        if (skyLoading) {
            Text("Calcul des étoiles visibles…", color = StellarMuted)
        }

        stars.forEach { star ->
            val selected = selectedStarId == star.id
            val direction = frenchDirection(star.azimuthDirection)
            val label = String.format(
                Locale.FRANCE,
                "%s • mag %.2f • %s • %s • h %.0f°",
                star.name,
                star.magnitude,
                star.constellation,
                direction,
                star.altitudeDeg
            )

            if (selected) {
                Button(
                    onClick = { onSelectStar(star) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = assistantPrimaryButtonColors()
                ) {
                    Text("✓ $label")
                }
            } else {
                OutlinedButton(
                    onClick = { onSelectStar(star) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(label)
                }
            }
            Spacer(Modifier.height(5.dp))
        }

        if (stars.isEmpty() && !skyLoading) {
            Text("Aucune étoile utilisable reçue.", color = StellarRed)
        }
        skyError?.let { Text(it, color = StellarRed) }

        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = onRefreshStars,
            modifier = Modifier.fillMaxWidth(),
            enabled = !skyLoading
        ) {
            Text("Actualiser les 10 étoiles")
        }

        if (selectedStar != null) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onGotoAndCenter,
                enabled = !centeringState.isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = assistantPrimaryButtonColors()
            ) {
                Text(
                    if (centeringState.centered) {
                        "${selectedStar.name} centrée ✓"
                    } else {
                        "GOTO + RECENTRAGE • ${selectedStar.name}"
                    }
                )
            }
        }

        if (
            centeringState.imageBytes != null ||
            centeringState.isLoading ||
            centeringState.manualRequired
        ) {
            Spacer(Modifier.height(12.dp))
            StellarImagePreview(
                imageBytes = centeringState.imageBytes,
                contentDescription = "Centrage étoile",
                loadingText = if (centeringState.isLoading) "Pose 4 s / recentrage…" else null,
                showCrosshair = true
            )
            Spacer(Modifier.height(6.dp))
            StatusValue("Centrage", centeringState.status ?: "en cours")
            StatusValue(
                "Erreur",
                centeringState.errorArcsec?.let {
                    String.format(Locale.FRANCE, "%.1f arcsec", it)
                } ?: "—"
            )
            StatusValue("Tentatives", centeringState.attempts.toString())
        }

        centeringState.message?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = if (centeringState.centered) StellarGreen else StellarMuted)
        }
        centeringState.error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = StellarRed)
        }

        if (centeringState.manualRequired && !centeringState.centered) {
            Spacer(Modifier.height(12.dp))
            Text("Recentrage manuel", color = StellarOrange, fontWeight = FontWeight.Bold)
            DirectionJoystick(
                enabled = !centeringState.isLoading,
                onDirection = onManualNudge
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onVerifyCentering,
                enabled = !centeringState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("VÉRIFIER LE CENTRAGE • pose 4 s")
            }
        }

        if (centeringState.centered && !maskInstalled) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Étoile centrée. Posez maintenant le masque de Bahtinov devant l'objectif.",
                color = StellarOrange,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onMaskInstalled,
                modifier = Modifier.fillMaxWidth(),
                colors = assistantPrimaryButtonColors()
            ) {
                Text("MASQUE BAHTINOV INSTALLÉ")
            }
        }

        if (centeringState.centered && maskInstalled && !maskRemoved) {
            Spacer(Modifier.height(14.dp))
            StellarImagePreview(
                imageBytes = bahtinovState.imageBytes,
                contentDescription = "Motif Bahtinov",
                loadingText = if (bahtinovState.isLoading) "Pose Bahtinov 4 s…" else null,
                showCrosshair = true
            )
            Spacer(Modifier.height(8.dp))
            StatusValue("Pose", "4,0 s")
            StatusValue(
                "Score focus",
                bahtinovState.focusScore?.let {
                    "$it/100 • ${bahtinovState.focusLabel ?: ""}"
                } ?: "à mesurer"
            )
            StatusValue(
                "Écart optimum",
                bahtinovState.focusErrorPx?.let {
                    String.format(Locale.FRANCE, "%+.2f px", it)
                } ?: "—"
            )
            StatusValue("Confirmation optimum", "${bahtinovState.optimumStreak}/2")

            bahtinovState.message?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    color = if (bahtinovState.focusValidated) StellarGreen else StellarOrange,
                    fontWeight = FontWeight.Bold
                )
            }
            bahtinovState.error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = StellarRed)
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onFocusCapture,
                enabled = !bahtinovState.isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = assistantPrimaryButtonColors()
            ) {
                Text(
                    if (bahtinovState.imageBytes == null) {
                        "MESURER LA MISE AU POINT • 4 s"
                    } else {
                        "ENCORE • 4 s"
                    }
                )
            }

            if (bahtinovState.focusValidated) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "OPTIMUM VALIDÉ ✓ • retirez maintenant le masque de Bahtinov.",
                    color = StellarGreen,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onMaskRemoved,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("MASQUE RETIRÉ")
                }
            }
        }

        if (maskRemoved) {
            Spacer(Modifier.height(12.dp))
            Text("Masque retiré ✓ • prêt pour les darks", color = StellarGreen)
        }

        Spacer(Modifier.height(14.dp))
        NavigationButtons(
            onPrevious = onPrevious,
            onContinue = onContinue,
            continueEnabled = bahtinovState.focusValidated && maskRemoved,
            continueText = "Suite • Darks"
        )
    }
}


@Composable
private fun AssistantDarkStep(
    state: DarkCalibrationUiState,
    telescopeCapped: Boolean,
    onCapped: () -> Unit,
    onStart: () -> Unit,
    onCapture: () -> Unit,
    onPrevious: () -> Unit,
    onContinue: () -> Unit
) {
    AssistantCard("Prise de darks") {
        if (!telescopeCapped) {
            Text(
                "Retirez le masque Bahtinov puis placez le bouchon opaque sur le télescope.",
                color = StellarOrange,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onCapped,
                modifier = Modifier.fillMaxWidth(),
                colors = assistantPrimaryButtonColors()
            ) {
                Text("BOUCHON POSÉ")
            }
        } else {
            StatusLine("Bouchon opaque", true)
            StatusValue("Exposition", "4,0 s")
            StatusValue("Darks", "${state.capturedCount}/${state.requestedCount}")
            StatusValue("Darks valides", state.validCount.toString())

            Spacer(Modifier.height(10.dp))
            if (state.sessionId == null) {
                Button(
                    onClick = onStart,
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = assistantPrimaryButtonColors()
                ) {
                    Text("DÉMARRER 10 DARKS")
                }
            } else if (!state.complete) {
                Button(
                    onClick = onCapture,
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = assistantPrimaryButtonColors()
                ) {
                    Text(
                        if (state.isLoading) {
                            "POSE 4 s…"
                        } else {
                            "DARK ${state.capturedCount + 1}/${state.requestedCount} • 4 s"
                        }
                    )
                }
            }

            state.message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = if (state.complete) StellarGreen else StellarMuted)
            }
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = StellarRed)
            }
        }

        Spacer(Modifier.height(14.dp))
        NavigationButtons(
            onPrevious = onPrevious,
            onContinue = onContinue,
            continueEnabled = state.complete && state.validCount == state.requestedCount,
            continueText = "Voir le bilan"
        )
    }
}


@Composable
private fun AssistantSummaryStep(
    connectionViewModel: ConnectionViewModel,
    mountState: MountDiagnosticsUiState,
    orientation: String?,
    astrometryState: CameraPreviewUiState,
    selectedStar: SkyStar?,
    centeringState: AssistantCenteringUiState,
    bahtinovState: BahtinovUiState,
    darkState: DarkCalibrationUiState,
    onPrevious: () -> Unit,
    onOpenSky: () -> Unit
) {
    val server = connectionViewModel.uiState.server
    val connectionOk = server != null &&
        server.devices.mount.status.lowercase() in setOf("ready", "ok", "online") &&
        server.devices.camera.status.lowercase() in setOf("ready", "ok", "online")
    val gpsOk = server?.devices?.gps?.status?.lowercase() == "fix"
    val astrometryOk = astrometryState.solveStatus == "solved" &&
        astrometryState.mountSyncStatus == "synced"
    val ready = connectionOk && gpsOk && mountState.timeSyncVerified &&
        astrometryOk && centeringState.centered && bahtinovState.focusValidated &&
        darkState.complete && darkState.validCount == darkState.requestedCount

    AssistantCard("Bilan de préparation") {
        StatusLine("Connexion matériel", connectionOk)
        StatusLine("GPS", gpsOk)
        StatusLine("Heure OnStep", mountState.timeSyncVerified)
        StatusLine("Astrométrie + SYNC", astrometryOk)
        StatusLine("Orientation", orientation != null, orientation ?: "—")
        StatusLine(
            "Étoile de focus",
            selectedStar != null && centeringState.centered,
            selectedStar?.name ?: "—"
        )
        StatusLine(
            "Mise au point Bahtinov",
            bahtinovState.focusValidated,
            bahtinovState.focusScore?.let { "$it/100" } ?: "—"
        )
        StatusLine(
            "Darks",
            darkState.complete && darkState.validCount == darkState.requestedCount,
            "${darkState.validCount}/${darkState.requestedCount}"
        )

        Spacer(Modifier.height(14.dp))
        if (ready) {
            Text(
                "Préparation terminée ✓",
                color = StellarGreen,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text(
                "Une ou plusieurs validations sont encore manquantes.",
                color = StellarRed
            )
        }

        Spacer(Modifier.height(12.dp))
        NavigationButtons(
            onPrevious = onPrevious,
            onContinue = onOpenSky,
            continueEnabled = ready,
            continueText = "ALLER À CIEL"
        )
    }
}


private fun nudgeDirection(
    viewModel: MountDiagnosticsViewModel,
    serverBaseUrl: String,
    direction: String
) {
    val normalized = direction.uppercase()
    val raDelta = when {
        "E" in normalized -> 0.10 / 15.0
        "O" in normalized -> -0.10 / 15.0
        else -> 0.0
    }
    val decDelta = when {
        "N" in normalized -> 0.10
        "S" in normalized -> -0.10
        else -> 0.0
    }

    viewModel.nudge(
        serverBaseUrl = serverBaseUrl,
        deltaRaHours = raDelta,
        deltaDecDeg = decDelta,
        label = "Joystick $direction"
    )
}


private fun frenchDirection(value: String): String = when (value.uppercase()) {
    "SW" -> "SO"
    "W" -> "O"
    "NW" -> "NO"
    else -> value.uppercase()
}


@Composable
private fun DirectionSelector(
    selected: String?,
    onSelect: (String) -> Unit
) {
    listOf(
        listOf("NO", "N", "NE"),
        listOf("O", "E"),
        listOf("SO", "S", "SE")
    ).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            row.forEach { direction ->
                if (selected == direction) {
                    Button(
                        onClick = { onSelect(direction) },
                        colors = assistantPrimaryButtonColors()
                    ) {
                        Text("✓ $direction")
                    }
                } else {
                    OutlinedButton(onClick = { onSelect(direction) }) {
                        Text(direction)
                    }
                }
            }
        }
        Spacer(Modifier.height(5.dp))
    }
}


@Composable
private fun DirectionJoystick(
    enabled: Boolean,
    onDirection: (String) -> Unit
) {
    listOf(
        listOf("NO", "N", "NE"),
        listOf("O", "E"),
        listOf("SO", "S", "SE")
    ).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            row.forEach { direction ->
                OutlinedButton(
                    onClick = { onDirection(direction) },
                    enabled = enabled
                ) {
                    Text(direction, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}


@Composable
private fun AssistantCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StellarSurface),
        border = BorderStroke(1.dp, StellarBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                title,
                color = StellarText,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}


@Composable
private fun StatusLine(
    label: String,
    ok: Boolean,
    detail: String? = null
) {
    Text(
        buildString {
            append(if (ok) "✓ " else "✗ ")
            append(label)
            if (!detail.isNullOrBlank()) append(" • $detail")
        },
        color = if (ok) StellarGreen else StellarRed
    )
}


@Composable
private fun StatusValue(label: String, value: String) {
    Text("$label : $value", color = StellarMuted)
}


@Composable
private fun NavigationButtons(
    onPrevious: () -> Unit,
    onContinue: () -> Unit,
    continueEnabled: Boolean,
    continueText: String
) {
    OutlinedButton(
        onClick = onPrevious,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Retour")
    }
    Spacer(Modifier.height(6.dp))
    Button(
        onClick = onContinue,
        enabled = continueEnabled,
        modifier = Modifier.fillMaxWidth(),
        colors = assistantPrimaryButtonColors()
    ) {
        Text(continueText, fontWeight = FontWeight.Bold)
    }
}


@Composable
private fun assistantPrimaryButtonColors() =
    ButtonDefaults.buttonColors(
        containerColor = StellarOrange,
        contentColor = StellarBackground
    )
