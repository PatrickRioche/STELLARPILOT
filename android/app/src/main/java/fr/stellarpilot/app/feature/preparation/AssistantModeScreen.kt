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


@Composable
fun AssistantModeScreen(
    onOpenSky: () -> Unit,
    connectionViewModel: ConnectionViewModel = viewModel()
) {
    var referenceTestMode by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = StellarSurface),
            border = BorderStroke(1.dp, StellarBorder),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(Modifier.padding(10.dp)) {
                Text("Mode Assistant", color = StellarText, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { referenceTestMode = false },
                        modifier = Modifier.weight(1f),
                        colors = if (!referenceTestMode) testPrimaryColors() else ButtonDefaults.buttonColors()
                    ) {
                        Text("Mode réel")
                    }
                    Button(
                        onClick = { referenceTestMode = true },
                        modifier = Modifier.weight(1f),
                        colors = if (referenceTestMode) testPrimaryColors() else ButtonDefaults.buttonColors()
                    ) {
                        Text("Test référentiel")
                    }
                }
                if (referenceTestMode) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "TEST JOUR • FITS sauvegardés pour astrométrie/Bahtinov • aucun GOTO/SYNC/joystick réel",
                        color = StellarOrange
                    )
                }
            }
        }

        if (referenceTestMode) {
            AssistantReferenceTestScreen(
                onOpenSky = onOpenSky,
                connectionViewModel = connectionViewModel
            )
        } else {
            AssistantFinalScreen(
                onOpenSky = onOpenSky,
                connectionViewModel = connectionViewModel
            )
        }
    }
}


@Composable
private fun AssistantReferenceTestScreen(
    onOpenSky: () -> Unit,
    connectionViewModel: ConnectionViewModel
) {
    val steps = listOf("Connexion", "Astrométrie", "Bahtinov", "Darks", "Bilan")
    var step by rememberSaveable { mutableIntStateOf(0) }
    var orientation by rememberSaveable { mutableStateOf<String?>(null) }
    var referenceIndex by rememberSaveable { mutableIntStateOf(0) }
    var centeringPhase by rememberSaveable { mutableStateOf("idle") }
    var lastJoystick by rememberSaveable { mutableStateOf<String?>(null) }
    var maskInstalled by rememberSaveable { mutableStateOf(false) }
    var maskRemoved by rememberSaveable { mutableStateOf(false) }
    var telescopeCapped by rememberSaveable { mutableStateOf(false) }

    val baseUrl = connectionViewModel.uiState.serverBaseUrl
    val mountViewModel: MountDiagnosticsViewModel = viewModel()
    val mountState = mountViewModel.uiState
    val astrometryViewModel: CameraPreviewViewModel = viewModel()
    val astrometryState = astrometryViewModel.uiState
    val bahtinovViewModel: BahtinovViewModel = viewModel()
    val bahtinovState = bahtinovViewModel.uiState
    val darkViewModel: DarkCalibrationViewModel = viewModel()
    val darkState = darkViewModel.uiState

    LaunchedEffect(Unit) {
        connectionViewModel.connect()
        mountViewModel.refresh(baseUrl)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("ASSISTANT • TEST RÉFÉRENTIEL", color = StellarOrange, fontWeight = FontWeight.Bold)
        Text("Étape ${step + 1}/${steps.size} • ${steps[step]}", color = StellarText)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = (step + 1).toFloat() / steps.size,
            modifier = Modifier.fillMaxWidth().height(5.dp),
            color = StellarOrange
        )
        Spacer(Modifier.height(12.dp))

        when (step) {
            0 -> {
                val server = connectionViewModel.uiState.server
                val mount = server?.devices?.mount
                val camera = server?.devices?.camera
                val gps = server?.devices?.gps

                TestCard("Connexion • contrôle réel du setup") {
                    TestStatus("Serveur StellarPilot", server != null, server?.status ?: "indisponible")
                    TestStatus(
                        "Monture OnStep",
                        mount?.status?.lowercase() in setOf("ready", "ok", "online"),
                        mount?.status ?: "indisponible"
                    )
                    TestStatus(
                        "Caméra",
                        camera?.status?.lowercase() in setOf("ready", "ok", "online"),
                        camera?.name ?: camera?.status ?: "indisponible"
                    )
                    TestStatus(
                        "GPS",
                        gps?.status?.lowercase() == "fix",
                        gps?.status ?: "indisponible"
                    )
                    TestStatus("Heure OnStep", mountState.timeSyncVerified, mountState.timeSyncDetail ?: "non vérifiée")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "En mode test, les défauts GPS/heure restent visibles mais ne bloquent pas la validation de l'interface.",
                        color = StellarMuted
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            connectionViewModel.connect()
                            mountViewModel.refresh(baseUrl)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Actualiser le setup")
                    }
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = { step = 1 },
                        enabled = server != null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = testPrimaryColors()
                    ) {
                        Text("Suite • Astrométrie référentiel")
                    }
                }
            }

            1 -> TestCard("Astrométrie • vraie analyse d'un FITS sauvegardé") {
                Text(
                    "La première 'pose 4 s' recharge une vraie capture du référentiel. Le plate solving est réel ; le SYNC OnStep est simulé.",
                    color = StellarText
                )
                Spacer(Modifier.height(8.dp))
                TestDirectionSelector(orientation) { orientation = it }
                Spacer(Modifier.height(8.dp))
                StellarImagePreview(
                    imageBytes = astrometryState.imageBytes,
                    contentDescription = "Référence astrométrique",
                    loadingText = if (astrometryState.isLoading) "Lecture FITS + plate solving…" else null,
                    showCrosshair = true
                )
                Spacer(Modifier.height(6.dp))
                TestValue("Source", "Référentiel")
                TestValue("Fichier", astrometryState.referenceName ?: "—")
                TestValue("Pose simulée", "4,0 s")
                TestValue(
                    "Qualité",
                    astrometryState.qualityScore?.let { "$it/100 • ${astrometryState.qualityLabel ?: ""}" } ?: "—"
                )
                TestValue("Étoiles", astrometryState.qualityStarCount?.toString() ?: "—")
                TestValue("Astrométrie", astrometryState.solveStatus ?: "à lancer")
                TestValue("SYNC OnStep", if (astrometryState.solveStatus == "solved") "SIMULÉ • aucun ordre envoyé" else "—")
                astrometryState.ra?.let { TestValue("RA", String.format(Locale.FRANCE, "%.5f°", it)) }
                astrometryState.dec?.let { TestValue("DEC", String.format(Locale.FRANCE, "%+.5f°", it)) }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        astrometryViewModel.loadReference(baseUrl, referenceIndex)
                        referenceIndex += 1
                    },
                    enabled = !astrometryState.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = testPrimaryColors()
                ) {
                    Text(if (astrometryState.imageBytes == null) "CAPTURE TEST • 4 s" else "ENCORE • référence suivante")
                }
                Spacer(Modifier.height(12.dp))
                Text("Joystick • simulation sans mouvement", color = StellarText, fontWeight = FontWeight.Bold)
                TestJoystick(enabled = true) { lastJoystick = it }
                lastJoystick?.let {
                    Text("Commande joystick simulée : $it ✓", color = StellarGreen)
                }
                astrometryState.error?.let { Text(it, color = StellarRed) }
                Spacer(Modifier.height(10.dp))
                TestNav(
                    onBack = { step = 0 },
                    onNext = { step = 2 },
                    enabled = astrometryState.solveStatus == "solved" && orientation != null,
                    text = "Suite • Bahtinov"
                )
            }

            2 -> TestCard("Bahtinov • chaîne GOTO / centrage / focus simulée") {
                Text("Étoile de test : Vega • Lyre • référentiel du 03/09/2026", color = StellarText)
                Spacer(Modifier.height(8.dp))

                if (centeringPhase == "idle") {
                    Button(
                        onClick = { centeringPhase = "centered" },
                        modifier = Modifier.fillMaxWidth(),
                        colors = testPrimaryColors()
                    ) {
                        Text("SIMULER GOTO + RECENTRAGE AUTO RÉUSSI")
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { centeringPhase = "manual" },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("SIMULER ÉCHEC AUTO → JOYSTICK")
                    }
                }

                if (centeringPhase == "manual") {
                    Text("Recentrage automatique en échec • joystick manuel requis", color = StellarOrange)
                    Spacer(Modifier.height(6.dp))
                    TestJoystick(enabled = true) { lastJoystick = it }
                    lastJoystick?.let { Text("Déplacement simulé : $it", color = StellarMuted) }
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = { centeringPhase = "centered" },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("VÉRIFIER LE CENTRAGE • pose 4 s simulée")
                    }
                }

                if (centeringPhase == "centered") {
                    TestStatus("Vega centrée", true, "simulation validée")
                }

                if (centeringPhase == "centered" && !maskInstalled) {
                    Spacer(Modifier.height(10.dp))
                    Text("Posez le masque de Bahtinov.", color = StellarOrange, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = { maskInstalled = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = testPrimaryColors()
                    ) {
                        Text("MASQUE INSTALLÉ")
                    }
                }

                if (centeringPhase == "centered" && maskInstalled && !maskRemoved) {
                    Spacer(Modifier.height(10.dp))
                    StellarImagePreview(
                        imageBytes = bahtinovState.imageBytes,
                        contentDescription = "Référence Bahtinov",
                        loadingText = if (bahtinovState.isLoading) "Analyse du FITS Bahtinov…" else null,
                        showCrosshair = true
                    )
                    Spacer(Modifier.height(6.dp))
                    TestValue("Source", "Référentiel Bahtinov")
                    TestValue("Fichier", bahtinovState.referenceName ?: "—")
                    TestValue(
                        "Score focus",
                        bahtinovState.focusScore?.let { "$it/100 • ${bahtinovState.focusLabel ?: ""}" } ?: "—"
                    )
                    TestValue("Côté", bahtinovState.focusSide ?: "—")
                    TestValue("Confirmation optimum", "${bahtinovState.optimumStreak}/2")
                    bahtinovState.message?.let {
                        Text(it, color = if (bahtinovState.focusValidated) StellarGreen else StellarOrange)
                    }
                    bahtinovState.error?.let { Text(it, color = StellarRed) }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { bahtinovViewModel.loadReference(baseUrl, "side_a") },
                            modifier = Modifier.weight(1f),
                            enabled = !bahtinovState.isLoading
                        ) { Text("Côté A") }
                        Button(
                            onClick = { bahtinovViewModel.loadReference(baseUrl, "optimum") },
                            modifier = Modifier.weight(1f),
                            enabled = !bahtinovState.isLoading,
                            colors = testPrimaryColors()
                        ) { Text("Optimum") }
                        OutlinedButton(
                            onClick = { bahtinovViewModel.loadReference(baseUrl, "side_b") },
                            modifier = Modifier.weight(1f),
                            enabled = !bahtinovState.isLoading
                        ) { Text("Côté B") }
                    }

                    if (bahtinovState.focusValidated) {
                        Spacer(Modifier.height(10.dp))
                        Text("OPTIMUM VALIDÉ ✓ • retirez le masque", color = StellarGreen, fontWeight = FontWeight.Bold)
                        Button(
                            onClick = { maskRemoved = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("MASQUE RETIRÉ")
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                TestNav(
                    onBack = { step = 1 },
                    onNext = {
                        telescopeCapped = false
                        darkViewModel.reset()
                        step = 3
                    },
                    enabled = bahtinovState.focusValidated && maskRemoved,
                    text = "Suite • Darks réels"
                )
            }

            3 -> TestCard("Darks • captures caméra réelles") {
                Text(
                    "À partir d'ici le test redevient réel : bouchon opaque puis 10 darks de 4 s sauvegardés sur le Pi.",
                    color = StellarText
                )
                Spacer(Modifier.height(8.dp))
                if (!telescopeCapped) {
                    Button(
                        onClick = { telescopeCapped = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = testPrimaryColors()
                    ) {
                        Text("BOUCHON POSÉ")
                    }
                } else {
                    TestStatus("Bouchon opaque", true)
                    TestValue("Darks", "${darkState.capturedCount}/${darkState.requestedCount}")
                    TestValue("Valides", darkState.validCount.toString())
                    Spacer(Modifier.height(8.dp))
                    if (darkState.sessionId == null) {
                        Button(
                            onClick = { darkViewModel.start(baseUrl) },
                            enabled = !darkState.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                            colors = testPrimaryColors()
                        ) { Text("DÉMARRER 10 DARKS") }
                    } else if (!darkState.complete) {
                        Button(
                            onClick = { darkViewModel.captureNext(baseUrl) },
                            enabled = !darkState.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                            colors = testPrimaryColors()
                        ) {
                            Text("DARK ${darkState.capturedCount + 1}/${darkState.requestedCount} • 4 s")
                        }
                    }
                    darkState.message?.let { Text(it, color = StellarMuted) }
                    darkState.error?.let { Text(it, color = StellarRed) }
                }
                Spacer(Modifier.height(10.dp))
                TestNav(
                    onBack = { step = 2 },
                    onNext = { step = 4 },
                    enabled = darkState.complete && darkState.validCount == darkState.requestedCount,
                    text = "Voir le bilan"
                )
            }

            else -> TestCard("Bilan • chaîne de test") {
                val serverOk = connectionViewModel.uiState.server != null
                val astroOk = astrometryState.solveStatus == "solved"
                val centerOk = centeringPhase == "centered"
                val focusOk = bahtinovState.focusValidated
                val darkOk = darkState.complete && darkState.validCount == darkState.requestedCount
                val ready = serverOk && astroOk && centerOk && focusOk && darkOk

                TestStatus("Connexion serveur", serverOk)
                TestStatus("Astrométrie référentiel", astroOk, astrometryState.referenceName ?: "—")
                TestStatus("Orientation", orientation != null, orientation ?: "—")
                TestStatus("GOTO + centrage", centerOk, "simulé")
                TestStatus(
                    "Bahtinov",
                    focusOk,
                    bahtinovState.focusScore?.let { "$it/100 • test référentiel" } ?: "—"
                )
                TestStatus("Darks réels", darkOk, "${darkState.validCount}/${darkState.requestedCount}")
                Spacer(Modifier.height(10.dp))
                Text(
                    if (ready) "CHAÎNE DE TEST VALIDÉE ✓" else "Validation incomplète",
                    color = if (ready) StellarGreen else StellarRed,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                TestNav(
                    onBack = { step = 3 },
                    onNext = onOpenSky,
                    enabled = ready,
                    text = "ALLER À CIEL"
                )
            }
        }
    }
}


@Composable
private fun TestDirectionSelector(
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
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            row.forEach { direction ->
                if (selected == direction) {
                    Button(onClick = { onSelect(direction) }, colors = testPrimaryColors()) {
                        Text("✓ $direction")
                    }
                } else {
                    OutlinedButton(onClick = { onSelect(direction) }) { Text(direction) }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}


@Composable
private fun TestJoystick(
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
private fun TestCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StellarSurface),
        border = BorderStroke(1.dp, StellarBorder)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = StellarText, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}


@Composable
private fun TestStatus(label: String, ok: Boolean, detail: String? = null) {
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
private fun TestValue(label: String, value: String) {
    Text("$label : $value", color = StellarMuted)
}


@Composable
private fun TestNav(
    onBack: () -> Unit,
    onNext: () -> Unit,
    enabled: Boolean,
    text: String
) {
    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text("Retour")
    }
    Spacer(Modifier.height(5.dp))
    Button(
        onClick = onNext,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = testPrimaryColors()
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}


@Composable
private fun testPrimaryColors() = ButtonDefaults.buttonColors(
    containerColor = StellarOrange,
    contentColor = StellarBackground
)
