package fr.stellarpilot.app.feature.preparation

import fr.stellarpilot.app.feature.connection.ConnectionState

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.stellarpilot.app.feature.connection.ConnectionViewModel
import fr.stellarpilot.app.feature.sky.SkyUiState
import fr.stellarpilot.app.feature.sky.SkyViewModel
import fr.stellarpilot.app.ui.format.statusDisplay
import fr.stellarpilot.app.ui.theme.StellarBackground
import fr.stellarpilot.app.ui.theme.StellarBorder
import fr.stellarpilot.app.ui.theme.StellarGreen
import fr.stellarpilot.app.ui.theme.StellarMuted
import fr.stellarpilot.app.ui.theme.StellarOrange
import fr.stellarpilot.app.ui.theme.StellarRed
import fr.stellarpilot.app.ui.theme.StellarSurface
import fr.stellarpilot.app.ui.theme.StellarSurfaceRaised
import fr.stellarpilot.app.ui.theme.StellarText

private val stepNames = listOf(
    "Connexion",
    "Astrométrie",
    "Étoile",
    "Centrage",
    "Bahtinov",
    "Darks",
    "Prêt"
)

@Composable
fun PreparationScreen(
    onOpenSky: () -> Unit,
    viewModel: ConnectionViewModel = viewModel()
) {
    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    var demoMode by rememberSaveable { mutableStateOf(false) }
    var selectedReferenceStarId by rememberSaveable { mutableStateOf<String?>(null) }
    var astrometryExposureMs by rememberSaveable { mutableIntStateOf(1) }

    LaunchedEffect(demoMode) {
        fr.stellarpilot.app.feature.demo.DemoModeState.active = demoMode
    }

    val state = viewModel.uiState
    val server = state.server

    val skyViewModel: SkyViewModel = viewModel()
    val skyState = skyViewModel.uiState

    val cameraPreviewViewModel: CameraPreviewViewModel = viewModel()
    val cameraPreviewState = cameraPreviewViewModel.uiState

    LaunchedEffect(Unit) {
        viewModel.connect()
    }

    LaunchedEffect(currentStep, demoMode) {
        if (currentStep == 1) {
            if (demoMode) {
                cameraPreviewViewModel.runDemoM103(state.serverBaseUrl)
            } else {
                cameraPreviewViewModel.resetM103()
            }
        }
    }

    LaunchedEffect(currentStep, state.serverBaseUrl, demoMode) {
        if (currentStep == 2) {
            if (demoMode) {
                skyViewModel.loadDemoSnapshot()
            } else {
                skyViewModel.load(state.serverBaseUrl)
            }
        }
    }

    val essentialReady = remember(server) {
        server != null &&
            goodStatus(server.devices.server.status) &&
            goodStatus(server.devices.mount.status) &&
            goodStatus(server.devices.camera.status)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = StellarBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Text(
                text = "PRÉPARATION DE L'OBSERVATION",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = StellarOrange
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Assistant StellarPilot",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = StellarText
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Étape ${currentStep + 1} sur ${stepNames.size} • ${stepNames[currentStep]}",
                style = MaterialTheme.typography.bodyMedium,
                color = StellarMuted
            )

            if (currentStep == 0 && !demoMode) {
                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { demoMode = true },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, StellarOrange)
                ) {
                    Text(
                        text = "Activer le mode démonstration",
                        color = StellarOrange,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (demoMode) {
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            demoMode = false
                            currentStep = 0
                            selectedReferenceStarId = null
                        },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, StellarOrange)
                    ) {
                        Text(
                            text = "Mode démonstration actif",
                            color = StellarOrange,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            if (currentStep == 2 && selectedReferenceStarId == null) {
                                selectedReferenceStarId =
                                    skyState.sky?.recommended?.id ?: "capella"
                            }

                            if (currentStep < stepNames.lastIndex) {
                                currentStep += 1
                            }
                        },
                        enabled = currentStep < stepNames.lastIndex,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StellarOrange,
                            contentColor = StellarBackground
                        )
                    ) {
                        Text(
                            text = if (currentStep < stepNames.lastIndex) {
                                "Étape suivante en mode démo"
                            } else {
                                "Démonstration terminée"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = (currentStep + 1).toFloat() / stepNames.size.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp),
                color = StellarOrange,
                trackColor = StellarSurfaceRaised
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                stepNames.forEachIndexed { index, name ->
                    StepBadge(
                        number = index + 1,
                        name = name,
                        active = index == currentStep,
                        completed = index < currentStep
                    )
                }
            }

            Spacer(Modifier.height(26.dp))

            when (currentStep) {
                0 -> ConnectionStep(
                    state = state,
                    ready = essentialReady,
                    demoMode = demoMode,
                    onRefresh = viewModel::connect,
                    onChangeServer = viewModel::setServerAddress,
                    onContinue = { currentStep = 1 }
                )

                1 -> AstrometryStep(
                    demoMode = demoMode,
                    previewState = cameraPreviewState,
                    demoM103State = cameraPreviewViewModel.demoM103State,
                    cameraName = server?.devices?.camera?.name,
                    exposureMs = astrometryExposureMs,
                    onExposureChange = { astrometryExposureMs = it },
                    onRunDemoM103 = {
                        cameraPreviewViewModel.runDemoM103(state.serverBaseUrl)
                    },
                    onRefresh = {
                        cameraPreviewViewModel.load(
                            state.serverBaseUrl,
                            astrometryExposureMs / 1000.0
                        )
                    },
                    onPrevious = { currentStep = 0 },
                    onNext = { currentStep = 2 }
                )

                2 -> ReferenceStarStep(
                    demoMode = demoMode,
                    skyState = skyState,
                    selectedStarId = selectedReferenceStarId,
                    onSelectStar = { selectedReferenceStarId = it },
                    onRefresh = { skyViewModel.load(state.serverBaseUrl) },
                    onOpenSky = onOpenSky,
                    onSetLocation = { latitude, longitude ->
                        if (demoMode) {
                            skyViewModel.loadDemoSnapshot()
                        } else {
                            skyViewModel.setManualLocation(
                                serverBaseUrl = state.serverBaseUrl,
                                latitude = latitude,
                                longitude = longitude
                            )
                        }
                    },
                    onPrevious = { currentStep = 1 },
                    onNext = { starId ->
                        selectedReferenceStarId = starId
                        currentStep = 3
                    }
                )

                3 -> PrototypeStep(
                    title = "Pointage et centrage",
                    description =
                        "La monture effectuera le GoTo vers l'étoile choisie. " +
                            "Une nouvelle astrométrie permettra ensuite de corriger " +
                            "le pointage jusqu'à placer l'étoile au centre du capteur.",
                    action = "Voir la mise au point",
                    onPrevious = { currentStep = 2 },
                    onNext = { currentStep = 4 }
                )

                4 -> BahtinovStep(
                    onPrevious = { currentStep = 3 },
                    onKeepFocus = { currentStep = 5 },
                    onDoFocus = { currentStep = 5 }
                )

                5 -> DarksStep(
                    cameraName = server?.devices?.camera?.name,
                    onPrevious = { currentStep = 4 },
                    onContinue = { currentStep = 6 }
                )

                6 -> ReadyStep(
                    onPrevious = { currentStep = 5 },
                    onOpenSky = onOpenSky
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConnectionStep(
    state: fr.stellarpilot.app.feature.connection.ConnectionUiState,
    ready: Boolean,
    demoMode: Boolean,
    onRefresh: () -> Unit,
    onChangeServer: (String) -> Unit,
    onContinue: () -> Unit
) {
    val server = state.server
    val serverReachable = state.connectionState == ConnectionState.CONNECTED
    val canContinue = demoMode || ready

    var serverAddress by rememberSaveable(state.serverBaseUrl) {
        mutableStateOf(
            state.serverBaseUrl
                .removePrefix("http://")
                .removePrefix("https://")
                .removeSuffix("/")
                .removeSuffix(":8000")
        )
    }

    AssistantCard(
        title = "Connexion & contrôles",
        subtitle = "Vérification du serveur et du matériel essentiel"
    ) {
        OutlinedTextField(
            value = serverAddress,
            onValueChange = { serverAddress = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Adresse du Raspberry Pi sous Astroberry") },
            supportingText = { Text("Ex. 192.168.1.46 ou 10.42.0.1") }
        )

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = { onChangeServer(serverAddress) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Se connecter à cette adresse")
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = if (demoMode) {
                "Le mode démonstration utilise des données de référence embarquées et ne nécessite ni serveur ni matériel réel."
            } else {
                "Activez ce mode pour découvrir StellarPilot sans matériel connecté."
            },
            color = if (demoMode) StellarOrange else StellarMuted,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(18.dp))

        if (server == null) {
            Text(
                text = when {
                    serverReachable && state.isConnecting ->
                        "Serveur connecté - lecture du matériel..."
                    serverReachable ->
                        "Serveur connecté • détails matériel indisponibles"
                    state.connectionState == ConnectionState.RECONNECTING ->
                        "Reconnexion au serveur StellarPilot..."
                    state.isConnecting ->
                        "Connexion au serveur StellarPilot..."
                    else ->
                        "Serveur non connecté"
                },
                color = if (serverReachable) StellarGreen else StellarMuted
            )
        } else {
            PrepStatusRow("Serveur", server.devices.server.status)
            PrepStatusRow("Monture", server.devices.mount.status)
            PrepStatusRow("Caméra", server.devices.camera.status)
            PrepStatusRow("GPS", server.devices.gps.status)

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Mode serveur : ${server.mode.uppercase()}",
                color = StellarMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(18.dp))

        OutlinedButton(
            onClick = onRefresh,
            enabled = !state.isConnecting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Actualiser")
        }

        Spacer(Modifier.height(10.dp))

        if (!demoMode) {
            Button(
                onClick = onContinue,
                enabled = canContinue,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StellarOrange,
                    contentColor = StellarBackground
                )
            ) {
                Text(
                    text = if (ready) {
                        "Continuer vers l'astrométrie"
                    } else {
                        "Matériel essentiel non prêt"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (server != null && server.devices.gps.status.lowercase() != "fix") {
            Spacer(Modifier.height(12.dp))

            Text(
                text =
                    "La position GPS n'est pas fixée. Cela ne bloque pas la première astrométrie ; " +
                        "StellarPilot utilise les informations disponibles via INDI et conserve un solve blind en repli.",
                color = StellarOrange,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ReferenceStarStep(
    demoMode: Boolean,
    skyState: SkyUiState,
    selectedStarId: String?,
    onSelectStar: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenSky: () -> Unit,
    onSetLocation: (Double, Double) -> Unit,
    onPrevious: () -> Unit,
    onNext: (String) -> Unit
) {
    val sky = skyState.sky

    val recommendations =
        sky?.stars
            .orEmpty()
            .filter { it.alignmentCandidate && it.alignmentScore != null }
            .sortedByDescending { it.alignmentScore }
            .take(4)

    val star =
        recommendations.firstOrNull { it.id == selectedStarId }
            ?: sky?.recommended
            ?: recommendations.firstOrNull()

    var latitudeText by rememberSaveable(demoMode) {
        mutableStateOf(if (demoMode) "47.4308" else "")
    }

    var longitudeText by rememberSaveable(demoMode) {
        mutableStateOf(if (demoMode) "-0.6271" else "")
    }

    LaunchedEffect(
        skyState.sky?.observer?.latitude,
        skyState.sky?.observer?.longitude
    ) {
        skyState.sky?.observer?.latitude?.let { latitude ->
            latitudeText = String.format(java.util.Locale.US, "%.5f", latitude)
        }

        skyState.sky?.observer?.longitude?.let { longitude ->
            longitudeText = String.format(java.util.Locale.US, "%.5f", longitude)
        }
    }

    val locationSourceLabel =
        when (skyState.sky?.observer?.locationSource?.lowercase()) {
            "gps" -> "GPS"
            "query" -> "Personnalisée"
            "manual" -> "Manuelle"
            else -> "En attente"
        }

    val selectedLatitude = latitudeText.replace(',', '.').toDoubleOrNull()
    val selectedLongitude = longitudeText.replace(',', '.').toDoubleOrNull()

    val locationValid =
        selectedLatitude != null &&
            selectedLongitude != null &&
            selectedLatitude in -90.0..90.0 &&
            selectedLongitude in -180.0..180.0

    AssistantCard(
        title = "Étoile de référence",
        subtitle = "Choisis une des meilleures étoiles calculées par StellarPilot"
    ) {
        Text(
            text = "LOCALISATION UTILISÉE",
            color = StellarOrange,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Source : $locationSourceLabel",
            color = StellarMuted,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = latitudeText,
                onValueChange = { latitudeText = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Latitude") }
            )

            OutlinedTextField(
                value = longitudeText,
                onValueChange = { longitudeText = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Longitude") }
            )
        }

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = {
                if (selectedLatitude != null && selectedLongitude != null) {
                    onSetLocation(selectedLatitude, selectedLongitude)
                }
            },
            enabled = locationValid && !skyState.isLoading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = StellarOrange,
                contentColor = StellarBackground
            )
        ) {
            Text("Utiliser cette localisation", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(20.dp))

        when {
            skyState.isLoading && sky == null -> {
                Text("Calcul des meilleures étoiles...", color = StellarMuted)
            }

            recommendations.isNotEmpty() -> {
                Text(
                    text = "4 meilleures étoiles de référence",
                    color = StellarText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(12.dp))

                recommendations.forEachIndexed { index, candidate ->
                    val selected = candidate.id == star?.id
                    val score =
                        candidate.alignmentScore?.let {
                            String.format(java.util.Locale.FRANCE, "%.0f %%", it * 100.0)
                        } ?: "—"

                    val label = buildString {
                        append(if (selected) "★ " else "☆ ")
                        append(candidate.name)
                        if (index == 0) append(" · recommandée")
                        append("\n")
                        append(candidate.constellation)
                        append(" · Alt. ")
                        append(
                            String.format(
                                java.util.Locale.FRANCE,
                                "%.1f°",
                                candidate.altitudeDeg
                            )
                        )
                        append(" · mag ")
                        append(
                            String.format(
                                java.util.Locale.FRANCE,
                                "%.2f",
                                candidate.magnitude
                            )
                        )
                        append(" · score ")
                        append(score)
                    }

                    if (selected) {
                        Button(
                            onClick = { onSelectStar(candidate.id) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StellarOrange,
                                contentColor = StellarBackground
                            )
                        ) {
                            Text(label, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSelectStar(candidate.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label)
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                }

                star?.let { selectedStar ->
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "ÉTOILE SÉLECTIONNÉE",
                        color = StellarOrange,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "★ ${selectedStar.name}",
                        color = StellarGreen,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "Constellation : ${selectedStar.constellation}",
                        color = StellarMuted
                    )

                    Spacer(Modifier.height(14.dp))

                    InfoBlock(
                        label = "Magnitude",
                        value = String.format(
                            java.util.Locale.FRANCE,
                            "%.2f",
                            selectedStar.magnitude
                        )
                    )

                    InfoBlock(
                        label = "Altitude",
                        value = String.format(
                            java.util.Locale.FRANCE,
                            "%.1f°",
                            selectedStar.altitudeDeg
                        )
                    )

                    InfoBlock(
                        label = "Azimut",
                        value = String.format(
                            java.util.Locale.FRANCE,
                            "%.1f° %s",
                            selectedStar.azimuthDeg,
                            selectedStar.azimuthDirection
                        )
                    )

                    InfoBlock(
                        label = "Score",
                        value = selectedStar.alignmentScore?.let {
                            String.format(java.util.Locale.FRANCE, "%.0f %%", it * 100.0)
                        } ?: "Non disponible"
                    )
                }
            }

            sky?.status == "location_required" -> {
                Text(
                    text = "Une position est nécessaire avant de choisir l'étoile de référence.",
                    color = StellarOrange
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = onOpenSky,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StellarOrange,
                        contentColor = StellarBackground
                    )
                ) {
                    Text("Renseigner la position dans Ciel")
                }
            }

            else -> {
                Text(
                    text = skyState.error
                        ?: "Aucune étoile de référence disponible actuellement.",
                    color = StellarOrange
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        OutlinedButton(
            onClick = onRefresh,
            enabled = !skyState.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Recalculer les étoiles")
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onPrevious,
                modifier = Modifier.weight(1f)
            ) {
                Text("Retour")
            }

            Button(
                onClick = { star?.let { onNext(it.id) } },
                enabled = star != null,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StellarOrange,
                    contentColor = StellarBackground
                )
            ) {
                Text(
                    text = star?.let { "Utiliser ${it.name}" } ?: "Choisir",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun InfoBlock(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = StellarMuted,
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = value,
            modifier = Modifier.weight(1f),
            color = StellarText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun BahtinovStep(
    onPrevious: () -> Unit,
    onKeepFocus: () -> Unit,
    onDoFocus: () -> Unit
) {
    AssistantCard(
        title = "Mise au point",
        subtitle = "Masque de Bahtinov"
    ) {
        Text(
            text =
                "Si la mise au point a déjà été réalisée et n'a pas bougé, " +
                    "cette étape peut être sautée.",
            color = StellarText
        )

        Spacer(Modifier.height(18.dp))

        Button(
            onClick = onDoFocus,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = StellarOrange,
                contentColor = StellarBackground
            )
        ) {
            Text("Faire la mise au point Bahtinov", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = onKeepFocus,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Conserver le réglage actuel")
        }

        Spacer(Modifier.height(14.dp))

        PreviousButton(onPrevious)
    }
}

@Composable
private fun DarksStep(
    cameraName: String?,
    onPrevious: () -> Unit,
    onContinue: () -> Unit
) {
    AssistantCard(
        title = "Darks",
        subtitle = cameraName ?: "Caméra non identifiée"
    ) {
        Text(
            text =
                "Règle StellarPilot : un master dark existant pourra être réutilisé " +
                    "tant que la caméra n'a pas changé.",
            color = StellarText
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text =
                "Si une autre caméra est détectée, l'ancien master dark sera invalidé " +
                    "et de nouveaux darks seront obligatoires.",
            color = StellarMuted,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(18.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = StellarOrange,
                contentColor = StellarBackground
            )
        ) {
            Text("Créer / valider les darks", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            enabled = false
        ) {
            Text("Réutiliser le master dark compatible")
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text =
                "La réutilisation sera activée dès que StellarPilot enregistrera " +
                    "les métadonnées du premier master dark.",
            color = StellarMuted,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(14.dp))

        PreviousButton(onPrevious)
    }
}

@Composable
private fun ReadyStep(
    onPrevious: () -> Unit,
    onOpenSky: () -> Unit
) {
    AssistantCard(
        title = "Préparation terminée",
        subtitle = "Le système est prêt pour l'observation"
    ) {
        Text(
            text =
                "Lorsque toutes les fonctions seront connectées au serveur, " +
                    "cet écran récapitulera les validations de la session.",
            color = StellarMuted
        )

        Spacer(Modifier.height(18.dp))

        Button(
            onClick = onOpenSky,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = StellarOrange,
                contentColor = StellarBackground
            )
        ) {
            Text("Passer à Ciel & Cible", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))

        PreviousButton(onPrevious)
    }
}

@Composable
private fun PrototypeStep(
    title: String,
    description: String,
    action: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    AssistantCard(
        title = title,
        subtitle = "Workflow préparé • intégration serveur à venir"
    ) {
        Text(text = description, color = StellarText)

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = StellarOrange,
                contentColor = StellarBackground
            )
        ) {
            Text(action, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(10.dp))

        PreviousButton(onPrevious)
    }
}

@Composable
private fun AssistantCard(
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
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = StellarText
            )

            Spacer(Modifier.height(5.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = StellarMuted
            )

            Spacer(Modifier.height(20.dp))

            content()
        }
    }
}

@Composable
private fun StepBadge(
    number: Int,
    name: String,
    active: Boolean,
    completed: Boolean
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (active) {
            StellarOrange.copy(alpha = 0.15f)
        } else {
            StellarSurfaceRaised
        },
        border = BorderStroke(
            1.dp,
            if (active || completed) StellarOrange else StellarBorder
        )
    ) {
        Text(
            text = if (completed) "✓ $name" else "$number $name",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = if (active || completed) StellarOrange else StellarMuted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PrepStatusRow(
    label: String,
    status: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .background(statusColor(status), CircleShape)
                .padding(5.dp)
        )

        Text(
            text = label,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
            color = StellarText
        )

        Text(
            text = statusDisplay(status),
            color = statusColor(status),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PreviousButton(
    onPrevious: () -> Unit
) {
    OutlinedButton(
        onClick = onPrevious,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Retour")
    }
}

private fun goodStatus(value: String): Boolean =
    value.lowercase() in setOf(
        "online",
        "ready",
        "ok",
        "fix",
        "connected"
    )

private fun statusColor(value: String): Color =
    when (value.lowercase()) {
        "online",
        "ready",
        "ok",
        "fix",
        "connected" -> StellarGreen

        "error",
        "offline",
        "failed",
        "disconnected" -> StellarRed

        else -> StellarOrange
    }
