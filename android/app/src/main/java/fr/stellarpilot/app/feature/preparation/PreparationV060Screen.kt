package fr.stellarpilot.app.feature.preparation

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.stellarpilot.app.domain.model.SkyStar
import fr.stellarpilot.app.feature.connection.ConnectionViewModel
import fr.stellarpilot.app.feature.sky.SkyViewModel
import fr.stellarpilot.app.ui.theme.StellarBackground
import fr.stellarpilot.app.ui.theme.StellarBorder
import fr.stellarpilot.app.ui.theme.StellarGreen
import fr.stellarpilot.app.ui.theme.StellarMuted
import fr.stellarpilot.app.ui.theme.StellarOrange
import fr.stellarpilot.app.ui.theme.StellarRed
import fr.stellarpilot.app.ui.theme.StellarSurface
import fr.stellarpilot.app.ui.theme.StellarSurfaceRaised
import fr.stellarpilot.app.ui.theme.StellarText
import java.util.Locale


private val preparationV060Steps = listOf(
    "Connexion",
    "Moteurs",
    "Astrométrie",
    "Étoile",
    "Bahtinov",
    "Prêt"
)


@Composable
fun PreparationV060Screen(
    onOpenSky: () -> Unit,
    connectionViewModel: ConnectionViewModel = viewModel()
) {
    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    var selectedStarId by rememberSaveable { mutableStateOf<String?>(null) }

    val connectionState = connectionViewModel.uiState
    val baseUrl = connectionState.serverBaseUrl
    val server = connectionState.server

    val mountViewModel: MountDiagnosticsViewModel = viewModel()
    val mountState = mountViewModel.uiState

    val astrometryViewModel: CameraPreviewViewModel = viewModel()
    val astrometryState = astrometryViewModel.uiState

    val skyViewModel: SkyViewModel = viewModel()
    val skyState = skyViewModel.uiState

    val bahtinovViewModel: BahtinovViewModel = viewModel()
    val bahtinovState = bahtinovViewModel.uiState

    LaunchedEffect(Unit) {
        connectionViewModel.connect()
    }

    LaunchedEffect(currentStep, baseUrl) {
        when (currentStep) {
            1 -> mountViewModel.refresh(baseUrl)
            3 -> skyViewModel.load(baseUrl)
        }
    }

    val selectedStar =
        skyState.sky?.stars?.firstOrNull { it.id == selectedStarId }
            ?: skyState.sky?.recommended

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = StellarBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                text = "PRÉPARATION DE L'OBSERVATION",
                style = MaterialTheme.typography.labelLarge,
                color = StellarOrange,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Assistant StellarPilot 0.6",
                style = MaterialTheme.typography.headlineMedium,
                color = StellarText,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Étape ${currentStep + 1}/${preparationV060Steps.size} • ${preparationV060Steps[currentStep]}",
                color = StellarMuted
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = (currentStep + 1).toFloat() /
                    preparationV060Steps.size.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp),
                color = StellarOrange,
                trackColor = StellarSurfaceRaised
            )
            Spacer(Modifier.height(22.dp))

            when (currentStep) {
                0 -> ConnectionV060Step(
                    connectionViewModel = connectionViewModel,
                    onContinue = { currentStep = 1 }
                )

                1 -> MotorV060Step(
                    state = mountState,
                    serverBaseUrl = baseUrl,
                    onRefresh = { mountViewModel.refresh(baseUrl) },
                    onNudgeRaPlus = {
                        mountViewModel.nudge(
                            baseUrl,
                            deltaRaHours = 0.03,
                            label = "Test moteur RA+"
                        )
                    },
                    onNudgeRaMinus = {
                        mountViewModel.nudge(
                            baseUrl,
                            deltaRaHours = -0.03,
                            label = "Test moteur RA-"
                        )
                    },
                    onNudgeDecPlus = {
                        mountViewModel.nudge(
                            baseUrl,
                            deltaDecDeg = 0.30,
                            label = "Test moteur DEC+"
                        )
                    },
                    onNudgeDecMinus = {
                        mountViewModel.nudge(
                            baseUrl,
                            deltaDecDeg = -0.30,
                            label = "Test moteur DEC-"
                        )
                    },
                    onPrevious = { currentStep = 0 },
                    onContinue = { currentStep = 2 }
                )

                2 -> AstrometryV060Step(
                    state = astrometryState,
                    onCapture = {
                        astrometryViewModel.load(
                            serverBaseUrl = baseUrl,
                            exposureSeconds = 4.0
                        )
                    },
                    onPrevious = { currentStep = 1 },
                    onContinue = { currentStep = 3 }
                )

                3 -> StarV060Step(
                    stars = skyState.sky?.stars.orEmpty(),
                    selectedStar = selectedStar,
                    isLoading = skyState.isLoading || mountState.isLoading,
                    error = skyState.error ?: mountState.error,
                    mountStatus = mountState.status?.status,
                    onSelect = { selectedStarId = it.id },
                    onRefresh = { skyViewModel.load(baseUrl) },
                    onGoto = {
                        selectedStar?.let {
                            selectedStarId = it.id
                            mountViewModel.gotoStar(baseUrl, it)
                        }
                    },
                    onPrevious = { currentStep = 2 },
                    onContinue = { currentStep = 4 }
                )

                4 -> BahtinovV060Step(
                    star = selectedStar,
                    mountState = mountState,
                    state = bahtinovState,
                    onExposure = bahtinovViewModel::setExposure,
                    onReference = { label ->
                        bahtinovViewModel.captureReference(
                            serverBaseUrl = baseUrl,
                            star = selectedStar,
                            mountRaHours = mountState.status?.raHours,
                            mountDecDeg = mountState.status?.decDeg,
                            label = label
                        )
                    },
                    onRefreshMount = { mountViewModel.refresh(baseUrl) },
                    onPrevious = { currentStep = 3 },
                    onContinue = { currentStep = 5 }
                )

                else -> ReadyV060Step(
                    bahtinovCount = bahtinovState.referenceCount,
                    onPrevious = { currentStep = 4 },
                    onOpenSky = onOpenSky
                )
            }
        }
    }
}


@Composable
private fun ConnectionV060Step(
    connectionViewModel: ConnectionViewModel,
    onContinue: () -> Unit
) {
    val state = connectionViewModel.uiState
    val server = state.server
    val ready = server != null &&
        server.devices.mount.status.lowercase() in setOf("ready", "ok", "online") &&
        server.devices.camera.status.lowercase() in setOf("ready", "ok", "online")

    V060Card(
        title = "Connexion matériel",
        subtitle = "Monture EQ et caméra doivent être disponibles"
    ) {
        V060Info("Serveur", state.serverBaseUrl)
        V060Info("Monture", server?.devices?.mount?.status ?: "Non disponible")
        V060Info("Caméra", server?.devices?.camera?.status ?: "Non disponible")

        state.error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = StellarRed)
        }

        Spacer(Modifier.height(14.dp))
        OutlinedButton(
            onClick = connectionViewModel::connect,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isConnecting
        ) {
            Text("Actualiser la connexion")
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onContinue,
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = StellarOrange,
                contentColor = StellarBackground
            )
        ) {
            Text("Tester la motorisation", fontWeight = FontWeight.Bold)
        }
    }
}


@Composable
private fun MotorV060Step(
    state: MountDiagnosticsUiState,
    serverBaseUrl: String,
    onRefresh: () -> Unit,
    onNudgeRaPlus: () -> Unit,
    onNudgeRaMinus: () -> Unit,
    onNudgeDecPlus: () -> Unit,
    onNudgeDecMinus: () -> Unit,
    onPrevious: () -> Unit,
    onContinue: () -> Unit
) {
    val mount = state.status
    val usable = mount != null &&
        mount.status.lowercase() != "error" &&
        !mount.virtualPosition

    V060Card(
        title = "Diagnostic moteurs EQ",
        subtitle = "Petits déplacements contrôlés avant la séance"
    ) {
        Text(
            "Les boutons lancent de petits GOTO de test : ±0,03 h en RA ou ±0,30° en DEC. " +
                "Après chaque ordre, StellarPilot relit les coordonnées publiées par OnStep.",
            color = StellarText
        )
        Spacer(Modifier.height(14.dp))
        V060Info("Monture", mount?.mount ?: "En attente")
        V060Info("État", mount?.status ?: "En attente")
        V060Info(
            "RA",
            mount?.raHours?.let { String.format(Locale.FRANCE, "%.5f h", it) }
                ?: "—"
        )
        V060Info(
            "DEC",
            mount?.decDeg?.let { String.format(Locale.FRANCE, "%+.4f°", it) }
                ?: "—"
        )
        V060Info("Tracking", mount?.trackingMode ?: "—")
        V060Info("INDI", mount?.indiState ?: "—")

        state.actionLabel?.let {
            Spacer(Modifier.height(8.dp))
            Text("$it…", color = StellarOrange, fontWeight = FontWeight.Bold)
        }
        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = StellarRed)
        }

        Spacer(Modifier.height(14.dp))
        OutlinedButton(
            onClick = onRefresh,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Actualiser RA / DEC")
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onNudgeRaPlus,
            enabled = usable && !state.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Tester moteur RA +") }
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = onNudgeRaMinus,
            enabled = usable && !state.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Tester moteur RA −") }
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = onNudgeDecPlus,
            enabled = usable && !state.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Tester moteur DEC +") }
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = onNudgeDecMinus,
            enabled = usable && !state.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Tester moteur DEC −") }

        if (mount?.decDeg == 90.0 || mount?.decDeg == -90.0) {
            Spacer(Modifier.height(10.dp))
            Text(
                "DEC est exactement à ${mount.decDeg}°. Après un test DEC, vérifiez impérativement qu'elle change : " +
                    "une valeur figée ne doit pas être utilisée comme hint astrométrique.",
                color = StellarOrange
            )
        }

        Spacer(Modifier.height(18.dp))
        V060Navigation(
            onPrevious = onPrevious,
            onContinue = onContinue,
            continueEnabled = usable,
            continueText = "Première astrométrie"
        )
    }
}


@Composable
private fun AstrometryV060Step(
    state: CameraPreviewUiState,
    onCapture: () -> Unit,
    onPrevious: () -> Unit,
    onContinue: () -> Unit
) {
    val solved = state.solveStatus == "solved"

    V060Card(
        title = "Première astrométrie",
        subtitle = "Référentiel réel : pose initiale 4 s"
    ) {
        Text(
            "Pour le setup Uranus-C validé le 02/09/2026, StellarPilot démarre à 4 s. " +
                "L'échelle mesurée de référence est ≈ 1,2183″/pixel.",
            color = StellarText
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onCapture,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = StellarOrange,
                contentColor = StellarBackground
            )
        ) {
            Text(
                if (state.isLoading) "Capture / résolution…" else "Capturer à 4 s et résoudre",
                fontWeight = FontWeight.Bold
            )
        }

        state.imageBytes?.let { bytes ->
            val bitmap = remember(bytes) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            if (bitmap != null) {
                Spacer(Modifier.height(12.dp))
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Capture astrométrique",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        V060Info("Qualité", state.qualityLabel ?: "—")
        V060Info("Étoiles", state.qualityStarCount?.toString() ?: "—")
        V060Info("Solve", state.solveStatus ?: "—")
        V060Info(
            "Échelle",
            state.pixelScaleArcsec?.let {
                String.format(Locale.FRANCE, "%.4f ″/px", it)
            } ?: "—"
        )
        if (solved) {
            Text("✓ Astrométrie validée", color = StellarGreen, fontWeight = FontWeight.Bold)
        }
        (state.error ?: state.solveDetail)?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = if (solved) StellarMuted else StellarOrange)
        }

        Spacer(Modifier.height(18.dp))
        V060Navigation(
            onPrevious = onPrevious,
            onContinue = onContinue,
            continueEnabled = solved,
            continueText = "Choisir l'étoile de focus"
        )
    }
}


@Composable
private fun StarV060Step(
    stars: List<SkyStar>,
    selectedStar: SkyStar?,
    isLoading: Boolean,
    error: String?,
    mountStatus: String?,
    onSelect: (SkyStar) -> Unit,
    onRefresh: () -> Unit,
    onGoto: () -> Unit,
    onPrevious: () -> Unit,
    onContinue: () -> Unit
) {
    val candidates = stars
        .filter { it.aboveHorizon && it.alignmentCandidate }
        .sortedByDescending { it.alignmentScore ?: 0.0 }
        .take(6)
    val tracking = mountStatus?.lowercase() == "tracking"

    V060Card(
        title = "Étoile de mise au point",
        subtitle = "GOTO réel + tracking sidéral"
    ) {
        candidates.forEach { star ->
            val selected = star.id == selectedStar?.id
            val text = buildString {
                append(if (selected) "★ " else "☆ ")
                append(star.name)
                append(" • mag ")
                append(String.format(Locale.FRANCE, "%.2f", star.magnitude))
                append(" • alt. ")
                append(String.format(Locale.FRANCE, "%.0f°", star.altitudeDeg))
            }
            if (selected) {
                Button(
                    onClick = { onSelect(star) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StellarOrange,
                        contentColor = StellarBackground
                    )
                ) { Text(text) }
            } else {
                OutlinedButton(
                    onClick = { onSelect(star) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(text) }
            }
            Spacer(Modifier.height(6.dp))
        }

        if (candidates.isEmpty()) {
            Text("Aucune étoile de référence disponible.", color = StellarOrange)
        }
        error?.let { Text(it, color = StellarRed) }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onRefresh,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Recalculer les étoiles") }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onGoto,
            enabled = selectedStar != null && !isLoading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = StellarOrange,
                contentColor = StellarBackground
            )
        ) {
            Text(
                selectedStar?.let { "Pointer ${it.name} et suivre" }
                    ?: "Choisir une étoile",
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (tracking) "✓ Monture en tracking" else "État monture : ${mountStatus ?: "—"}",
            color = if (tracking) StellarGreen else StellarMuted
        )

        Spacer(Modifier.height(18.dp))
        V060Navigation(
            onPrevious = onPrevious,
            onContinue = onContinue,
            continueEnabled = selectedStar != null && !isLoading,
            continueText = "Installer le Bahtinov"
        )
    }
}


@Composable
private fun BahtinovV060Step(
    star: SkyStar?,
    mountState: MountDiagnosticsUiState,
    state: BahtinovUiState,
    onExposure: (Double) -> Unit,
    onReference: (BahtinovReferenceLabel) -> Unit,
    onRefreshMount: () -> Unit,
    onPrevious: () -> Unit,
    onContinue: () -> Unit
) {
    V060Card(
        title = "Calibration Bahtinov",
        subtitle = star?.let { "${it.name} • tracking sidéral" }
            ?: "Étoile non sélectionnée"
    ) {
        Text(
            "Posez le masque de Bahtinov puis ajustez progressivement le focuser. " +
                "Chaque bouton capture une nouvelle image et l'étiquette pour constituer le référentiel.",
            color = StellarText
        )
        Spacer(Modifier.height(10.dp))
        V060Info("Monture", mountState.status?.status ?: "—")
        V060Info("Tracking", mountState.status?.trackingMode ?: "—")
        V060Info("Références", state.referenceCount.toString())

        Spacer(Modifier.height(12.dp))
        Text("Temps de pose Bahtinov", color = StellarMuted)
        Spacer(Modifier.height(6.dp))
        listOf(0.10, 0.25, 0.50, 1.0, 2.0).forEach { exposure ->
            val selected = kotlin.math.abs(state.exposureSeconds - exposure) < 0.001
            if (selected) {
                Button(
                    onClick = { onExposure(exposure) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(formatExposure(exposure))
                }
            } else {
                OutlinedButton(
                    onClick = { onExposure(exposure) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(formatExposure(exposure))
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        state.imageBytes?.let { bytes ->
            val bitmap = remember(bytes) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            if (bitmap != null) {
                Spacer(Modifier.height(10.dp))
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Référence Bahtinov",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "Étiqueter la prochaine capture",
            color = StellarOrange,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        listOf(
            BahtinovReferenceLabel.VERY_BAD,
            BahtinovReferenceLabel.BAD,
            BahtinovReferenceLabel.MEDIUM,
            BahtinovReferenceLabel.GOOD,
            BahtinovReferenceLabel.OPTIMUM,
            BahtinovReferenceLabel.BAD_OTHER_SIDE
        ).forEach { label ->
            Button(
                onClick = { onReference(label) },
                enabled = !state.isLoading && star != null,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor =
                        if (label == BahtinovReferenceLabel.OPTIMUM) StellarGreen
                        else StellarOrange,
                    contentColor = StellarBackground
                )
            ) {
                Text(
                    if (state.isLoading) "Capture…" else label.displayName,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        OutlinedButton(
            onClick = { onReference(BahtinovReferenceLabel.IGNORE) },
            enabled = !state.isLoading && star != null,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Mauvaise capture / ignorer") }

        state.message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = StellarGreen)
        }
        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = StellarRed)
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onRefreshMount,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Vérifier le tracking") }

        Spacer(Modifier.height(18.dp))
        V060Navigation(
            onPrevious = onPrevious,
            onContinue = onContinue,
            continueEnabled = state.referenceCount > 0,
            continueText = "Terminer les tests"
        )
    }
}


@Composable
private fun ReadyV060Step(
    bahtinovCount: Int,
    onPrevious: () -> Unit,
    onOpenSky: () -> Unit
) {
    V060Card(
        title = "Tests préparatoires terminés",
        subtitle = "Motorisation et Bahtinov V0.6"
    ) {
        Text(
            "✓ Motorisation testable via OnStep\n" +
                "✓ Astrométrie de référence à 4 s\n" +
                "✓ GOTO étoile + tracking\n" +
                "✓ $bahtinovCount références Bahtinov étiquetées",
            color = StellarGreen
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Les darks restent volontairement hors de cette version de test. " +
                "Le centrage deviendra ensuite une fonction automatique interne aux GOTO.",
            color = StellarMuted
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onOpenSky,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = StellarOrange,
                contentColor = StellarBackground
            )
        ) { Text("Ouvrir Ciel", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onPrevious,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Retour au Bahtinov") }
    }
}


@Composable
private fun V060Card(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = StellarSurface),
        border = BorderStroke(1.dp, StellarBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                title,
                color = StellarText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = StellarMuted)
            Spacer(Modifier.height(18.dp))
            content()
        }
    }
}


@Composable
private fun V060Info(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = StellarMuted)
        Text(value, color = StellarText, fontWeight = FontWeight.SemiBold)
    }
}


@Composable
private fun V060Navigation(
    onPrevious: () -> Unit,
    onContinue: () -> Unit,
    continueEnabled: Boolean,
    continueText: String
) {
    OutlinedButton(
        onClick = onPrevious,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Retour") }
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = onContinue,
        enabled = continueEnabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = StellarOrange,
            contentColor = StellarBackground
        )
    ) { Text(continueText, fontWeight = FontWeight.Bold) }
}


private fun formatExposure(seconds: Double): String =
    if (seconds < 1.0) {
        "${(seconds * 1000).toInt()} ms"
    } else {
        String.format(Locale.FRANCE, "%.1f s", seconds)
    }
