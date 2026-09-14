package fr.stellarpilot.app.feature.preparation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.stellarpilot.app.feature.connection.ConnectionViewModel
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
    "Bahtinov via Capture",
    "Darks",
    "Bilan"
)

@Composable
fun AssistantFinalScreen(
    onOpenSky: () -> Unit,
    connectionViewModel: ConnectionViewModel = viewModel()
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var telescopeCapped by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var darkEnteredDirectly by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }

    val connectionState = connectionViewModel.uiState
    val baseUrl = connectionState.serverBaseUrl

    val mountViewModel: MountDiagnosticsViewModel = viewModel()
    val mountState = mountViewModel.uiState

    val astrometryViewModel: CameraPreviewViewModel = viewModel()
    val astrometryState = astrometryViewModel.uiState

    val darkViewModel: DarkCalibrationViewModel = viewModel()
    val darkState = darkViewModel.uiState

    LaunchedEffect(Unit) {
        connectionViewModel.connect()
        mountViewModel.refresh(baseUrl)
    }

    LaunchedEffect(step, baseUrl) {
        if (step == 0) {
            mountViewModel.refresh(baseUrl)
        }
    }

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

        if (step < 3) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    darkEnteredDirectly = true
                    telescopeCapped = false
                    darkViewModel.reset()
                    step = 3
                },
                enabled = !darkState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ALLER DIRECTEMENT AUX DARKS")
            }
            Text(
                "Mode calibration : les étapes d'astrométrie et de mise au point sont ignorées.",
                color = StellarMuted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(16.dp))

        when (step) {
            0 -> AssistantConnectionStep(
                connectionViewModel = connectionViewModel,
                mountViewModel = mountViewModel,
                onContinue = { step = 1 }
            )

            1 -> AssistantAstrometryStep(
                state = astrometryState,
                mountState = mountState,
                onCapture = {
                    astrometryViewModel.load(
                        serverBaseUrl = baseUrl,
                        exposureSeconds = 4.0
                    )
                },
                onPrevious = { step = 0 },
                onContinue = { step = 2 }
            )

            2 -> AssistantBahtinovRedirectStep(
                onPrevious = { step = 1 },
                onOpenSky = onOpenSky,
                onContinue = {
                    darkEnteredDirectly = false
                    telescopeCapped = false
                    darkViewModel.reset()
                    step = 3
                }
            )

            3 -> AssistantDarkStep(
                state = darkState,
                telescopeCapped = telescopeCapped,
                directMode = darkEnteredDirectly,
                onCapped = { telescopeCapped = true },
                onStart = { darkViewModel.start(baseUrl) },
                onPrevious = {
                    step = if (darkEnteredDirectly) 0 else 2
                },
                onContinue = {
                    if (darkEnteredDirectly) {
                        darkEnteredDirectly = false
                        step = 0
                    } else {
                        step = 4
                    }
                }
            )

            else -> AssistantSummaryStep(
                connectionViewModel = connectionViewModel,
                mountState = mountState,
                astrometryState = astrometryState,
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
    val gpsReady = gps?.status?.lowercase() in setOf("fix", "available")
    val timeReady = mountState.timeSyncVerified

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
            enabled = true,
            modifier = Modifier.fillMaxWidth(),
            colors = assistantPrimaryButtonColors()
        ) {
            Text("Continuer vers l'astrométrie", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AssistantAstrometryStep(
    state: CameraPreviewUiState,
    mountState: MountDiagnosticsUiState,
    onCapture: () -> Unit,
    onPrevious: () -> Unit,
    onContinue: () -> Unit
) {
    val solved = state.solveStatus == "solved"
    val synced = state.mountSyncStatus == "synced"
    val validated = solved && synced

    AssistantCard("Astrométrie de préparation") {
        Text(
            "Cette vérification de préparation reste disponible ici. Dans l'onglet Capture, l'astrométrie V0.6.7 est désormais lancée uniquement par un bouton dédié.",
            color = StellarText
        )
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
            continueText = "Suite • Bahtinov via Capture"
        )
    }
}

@Composable
private fun AssistantBahtinovRedirectStep(
    onPrevious: () -> Unit,
    onOpenSky: () -> Unit,
    onContinue: () -> Unit
) {
    AssistantCard("Mise au point Bahtinov déplacée dans Ciel + Capture") {
        Text(
            "Le choix automatique des 10 étoiles a été supprimé de l'assistant.",
            color = StellarGreen,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Choisissez maintenant votre étoile dans Ciel, effectuez le GOTO, puis ouvrez Capture. Vous pourrez y déclarer le masque Bahtinov installé et lancer autant de poses de 4 s que nécessaire.",
            color = StellarText
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Cette étape n'effectue plus aucun GOTO ni recentrage automatique.",
            color = StellarOrange,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onOpenSky,
            modifier = Modifier.fillMaxWidth(),
            colors = assistantPrimaryButtonColors()
        ) {
            Text("OUVRIR CIEL", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))
        NavigationButtons(
            onPrevious = onPrevious,
            onContinue = onContinue,
            continueEnabled = true,
            continueText = "Continuer vers les darks"
        )
    }
}

@Composable
private fun AssistantDarkStep(
    state: DarkCalibrationUiState,
    telescopeCapped: Boolean,
    directMode: Boolean,
    onCapped: () -> Unit,
    onStart: () -> Unit,
    onPrevious: () -> Unit,
    onContinue: () -> Unit
) {
    AssistantCard("Prise de darks") {
        if (directMode) {
            Text(
                "Mode darks directs • aucune validation d'astrométrie ou de mise au point n'est nécessaire.",
                color = StellarGreen,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
        }

        if (!telescopeCapped) {
            Text(
                "Retirez tout masque de mise au point puis placez le bouchon opaque sur le télescope.",
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
            StatusValue("Exposition", "${String.format(Locale.FRANCE, "%.1f", state.exposureSeconds)} s")
            StatusValue("Darks", "${state.capturedCount}/${state.requestedCount}")
            StatusValue("Darks valides", state.validCount.toString())

            if (state.sessionId != null) {
                Spacer(Modifier.height(10.dp))
                Text("PROFIL ARCHIVÉ", color = StellarOrange, fontWeight = FontWeight.Bold)
                StatusValue("Caméra", state.cameraName ?: "Non disponible")
                StatusValue(
                    "Gain / offset",
                    "${state.gain?.let { numberText(it) } ?: "?"} / ${state.offset?.let { numberText(it) } ?: "?"}"
                )
                StatusValue(
                    "Binning",
                    if (state.binX != null && state.binY != null) "${state.binX}×${state.binY}" else "Non disponible"
                )
                StatusValue(
                    "Température",
                    state.temperatureC?.let { String.format(Locale.FRANCE, "%.1f °C", it) } ?: "Non disponible"
                )
                StatusValue("Bayer", state.bayerPattern ?: "À lire dans le premier FITS")
                StatusValue("Train KStars", state.opticalTrainName ?: "Non disponible")
                StatusValue("Tube", state.telescopeName ?: "Non disponible")
                StatusValue("Type", state.telescopeType ?: "Non disponible")
                StatusValue(
                    "Diamètre",
                    state.apertureMm?.let { String.format(Locale.FRANCE, "%.1f mm", it) } ?: "Non disponible"
                )
                StatusValue(
                    "Focale",
                    state.focalLengthMm?.let { String.format(Locale.FRANCE, "%.1f mm", it) } ?: "Non disponible"
                )
                StatusValue(
                    "F/D",
                    state.focalRatio?.let { String.format(Locale.FRANCE, "f/%.2f", it) } ?: "Non disponible"
                )
            }

            Spacer(Modifier.height(10.dp))
            if (state.sessionId == null) {
                Button(
                    onClick = onStart,
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = assistantPrimaryButtonColors()
                ) {
                    Text("DÉMARRER 20 DARKS")
                }
            } else if (!state.complete) {
                val progress = if (state.requestedCount > 0) {
                    state.capturedCount.toFloat() / state.requestedCount.toFloat()
                } else {
                    0f
                }

                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = StellarOrange
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    "Acquisition automatique • ${state.capturedCount}/${state.requestedCount}",
                    color = StellarOrange,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    state.message ?: "Acquisition en cours…",
                    color = StellarMuted
                )
            }

            if (state.complete && state.masterDarkPath != null) {
                Spacer(Modifier.height(12.dp))
                Text("CALIBRATION CRÉÉE ✓", color = StellarGreen, fontWeight = FontWeight.Bold)
                StatusValue("Master Dark", state.masterMethod ?: "Créé")
                StatusValue("Pixels chauds", (state.hotPixelCount ?: 0).toString())
                StatusValue("Carte pixels chauds", if (state.hotPixelMapPath != null) "Créée" else "Non disponible")
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
            continueEnabled = state.complete && state.masterDarkPath != null,
            continueText = if (directMode) "TERMINER LES DARKS" else "Voir le bilan"
        )
    }
}

@Composable
private fun AssistantSummaryStep(
    connectionViewModel: ConnectionViewModel,
    mountState: MountDiagnosticsUiState,
    astrometryState: CameraPreviewUiState,
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
    val darkOk = darkState.complete && darkState.masterDarkPath != null
    val ready = connectionOk && gpsOk && mountState.timeSyncVerified && astrometryOk && darkOk

    AssistantCard("Bilan de préparation") {
        StatusLine("Connexion matériel", connectionOk)
        StatusLine("GPS", gpsOk)
        StatusLine("Heure OnStep", mountState.timeSyncVerified)
        StatusLine("Astrométrie + SYNC de préparation", astrometryOk)
        StatusLine(
            "Bahtinov",
            true,
            "à effectuer dans Ciel → Capture sur l'étoile choisie"
        )
        StatusLine(
            "Master Dark",
            darkOk,
            if (darkOk) "${darkState.validCount} darks • ${darkState.hotPixelCount ?: 0} pixels chauds" else "non créé"
        )

        Spacer(Modifier.height(14.dp))
        if (ready) {
            Text(
                "Préparation terminée ✓ • choisissez ensuite votre cible dans Ciel.",
                color = StellarGreen,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text(
                "Une ou plusieurs validations de préparation sont encore manquantes.",
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

private fun numberText(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString()
    else String.format(Locale.FRANCE, "%.2f", value)

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
