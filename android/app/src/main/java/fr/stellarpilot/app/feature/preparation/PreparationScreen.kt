package fr.stellarpilot.app.feature.preparation

import fr.stellarpilot.app.feature.connection.ConnectionState

import android.graphics.BitmapFactory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.stellarpilot.app.R
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
    "Position",
    "Astrom\u00E9trie",
    "\u00C9toile",
    "Centrage",
    "Bahtinov",
    "Darks",
    "Pr\u00EAt"
)

@Composable
fun PreparationScreen(
    onOpenSky: () -> Unit,
    viewModel: ConnectionViewModel = viewModel()
) {
    var currentStep by rememberSaveable {
        mutableIntStateOf(0)
    }

    var demoMode by rememberSaveable {
        mutableStateOf(false)
    }

    var selectedReferenceStarId by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    /*
     * Temps d'exposition de la première astrométrie.
     *
     * Valeur initiale volontairement basse pour les tests
     * de jour avec la caméra réelle.
     *
     * 1 ms = 0.001 seconde.
     */
    var astrometryExposureMs by rememberSaveable {
        mutableIntStateOf(1)
    }

    val state = viewModel.uiState
    val server = state.server

    val skyViewModel: SkyViewModel =
        viewModel()

    val skyState =
        skyViewModel.uiState

    val cameraPreviewViewModel: CameraPreviewViewModel =
        viewModel()

    val cameraPreviewState =
        cameraPreviewViewModel.uiState

    LaunchedEffect(Unit) {
        viewModel.connect()
    }

    LaunchedEffect(
        currentStep,
        demoMode
    ) {
        if (currentStep == 2) {
            if (demoMode) {
                cameraPreviewViewModel.runDemoM103(
                    state.serverBaseUrl
                )
            } else {
                cameraPreviewViewModel.resetM103()
            }
        }
    }

    LaunchedEffect(
        currentStep,
        state.serverBaseUrl,
        demoMode
    ) {
        if (currentStep == 3) {
            if (demoMode) {
                skyViewModel.loadDemoSnapshot()
            } else {
                skyViewModel.load(
                    state.serverBaseUrl
                )
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
                text = "PR\u00C9PARATION DE L'OBSERVATION",
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
                text = "\u00C9tape ${currentStep + 1} sur ${stepNames.size} \u2022 ${stepNames[currentStep]}",
                style = MaterialTheme.typography.bodyMedium,
                color = StellarMuted
            )

            if (currentStep == 0 && !demoMode) {
                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        demoMode = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(
                        1.dp,
                        StellarOrange
                    )
                ) {
                    Text(
                        text = "Activer le mode d\u00E9monstration",
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
                        border = BorderStroke(
                            1.dp,
                            StellarOrange
                        )
                    ) {
                        Text(
                            text = "Mode d\u00E9monstration actif",
                            color = StellarOrange,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            if (
                                currentStep == 3 &&
                                selectedReferenceStarId == null
                            ) {
                                selectedReferenceStarId =
                                    skyState.sky
                                        ?.recommended
                                        ?.id
                                        ?: "capella"
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
                            text =
                                if (currentStep < stepNames.lastIndex)
                                    "\u00C9tape suivante en mode d\u00E9mo"
                                else
                                    "D\u00E9monstration termin\u00E9e",
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

                1 -> PositionStep(
                    demoMode = demoMode,

                    mountFamily =
                        server?.session?.mountFamily
                            ?: server?.devices?.mount?.family,

                    startupTarget =
                        server?.session?.startupTarget
                            ?: server?.devices?.mount?.startupTarget,

                    mountTypeLabel =
                        server?.devices?.mount?.typeLabel,

                    mountType =
                        server?.session?.mountType
                            ?: server?.devices?.mount?.type,

                    latitude =
                        server?.session?.latitude
                            ?: server?.devices?.gps?.latitude,

                    onPrevious = {
                        currentStep = 0
                    },

                    onNext = {
                        currentStep = 2
                    }
                )

                2 -> AstrometryStep(
                    demoMode = demoMode,

                    previewState =
                        cameraPreviewState,

                    demoM103State =
                        cameraPreviewViewModel.demoM103State,

                    cameraName =
                        server?.devices?.camera?.name,

                    exposureMs =
                        astrometryExposureMs,

                    onExposureChange = {
                        astrometryExposureMs = it
                    },

                    onRunDemoM103 = {
                        if (demoMode) {
                            cameraPreviewViewModel.runDemoM103(
                                state.serverBaseUrl
                            )
                        } else {
                            cameraPreviewViewModel.runServerM103(
                                state.serverBaseUrl
                            )
                        }
                    },

                    onRefresh = {
                        cameraPreviewViewModel.load(
                    state.serverBaseUrl,
                    astrometryExposureMs / 1000.0
                )
                    },

                    onPrevious = {
                        currentStep = 1
                    },

                    onNext = {
                        currentStep = 3
                    }
                )
                3 -> ReferenceStarStep(
                    demoMode = demoMode,
                    skyState = skyState,

                    selectedStarId =
                        selectedReferenceStarId,

                    onSelectStar = {
                        selectedReferenceStarId = it
                    },

                    onRefresh = {
                        skyViewModel.load(
                            state.serverBaseUrl
                        )
                    },

                    onOpenSky = onOpenSky,

                    onSetLocation = { latitude, longitude ->
                        if (demoMode) {
                            skyViewModel.loadDemoSnapshot()
                        } else {
                            skyViewModel.setManualLocation(
                                serverBaseUrl =
                                    state.serverBaseUrl,
                                latitude =
                                    latitude,
                                longitude =
                                    longitude
                            )
                        }
                    },

                    onPrevious = {
                        currentStep = 2
                    },

                    onNext = { starId ->
                        selectedReferenceStarId =
                            starId

                        currentStep = 4
                    }
                )

                4 -> PrototypeStep(
                    title = "Pointage et centrage",
                    description =
                        "La monture effectuera le GoTo vers l'\u00E9toile choisie. " +
                        "Une nouvelle astrom\u00E9trie permettra ensuite de corriger " +
                        "le pointage jusqu'\u00E0 placer l'\u00E9toile au centre du capteur.",
                    action = "Voir la mise au point",
                    onPrevious = { currentStep = 3 },
                    onNext = { currentStep = 5 }
                )

                5 -> BahtinovStep(
                    onPrevious = { currentStep = 4 },
                    onKeepFocus = { currentStep = 6 },
                    onDoFocus = { currentStep = 6 }
                )

                6 -> DarksStep(
                    cameraName = server?.devices?.camera?.name,
                    onPrevious = { currentStep = 5 },
                    onContinue = { currentStep = 7 }
                )

                7 -> ReadyStep(
                    onPrevious = { currentStep = 6 },
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

    val serverReachable =
        state.connectionState == ConnectionState.CONNECTED

    val canContinue =
        if (demoMode)
            true
        else
            ready

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
        title = "Connexion & contr\u00F4les",
        subtitle = "V\u00E9rification du serveur et du mat\u00E9riel essentiel"
    ) {

        OutlinedTextField(
            value = serverAddress,
            onValueChange = {
                serverAddress = it
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = {
                Text("Adresse du Raspberry Pi sous Astroberry")
            },
            supportingText = {
                Text(
                    "Ex. 192.168.1.46 ou 10.42.0.1"
                )
            }
        )

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                onChangeServer(serverAddress)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Se connecter \u00E0 cette adresse")
        }

        Spacer(Modifier.height(18.dp))

        Spacer(Modifier.height(8.dp))

        Text(
            text =
                if (demoMode)
                    "Le mode d\u00E9monstration utilise des donn\u00E9es de r\u00E9f\u00E9rence embarqu\u00E9es et ne n\u00E9cessite ni serveur ni mat\u00E9riel r\u00E9el."
                else
                    "Activez ce mode pour d\u00E9couvrir StellarPilot sans mat\u00E9riel connect\u00E9.",
            color =
                if (demoMode)
                    StellarOrange
                else
                    StellarMuted,
            style = MaterialTheme.typography.bodySmall
        )


        Spacer(Modifier.height(18.dp))

        if (server == null) {
            Text(
                text =
                    when {
                        serverReachable &&
                            state.isConnecting ->
                            "Serveur connect\u00E9 - lecture du mat\u00E9riel..."

                        serverReachable ->
                            "Serveur connecté • détails matériel indisponibles"

                        state.connectionState == ConnectionState.RECONNECTING ->
                            "Reconnexion au serveur StellarPilot..."

                        state.isConnecting ->
                            "Connexion au serveur StellarPilot..."

                        else ->
                            "Serveur non connect\u00E9"
                    },
                color =
                    if (serverReachable)
                        StellarGreen
                    else
                        StellarMuted
            )
        } else {

            PrepStatusRow(
                "Serveur",
                server.devices.server.status
            )

            PrepStatusRow(
                "Monture",
                server.devices.mount.status
            )

            PrepStatusRow(
                "Cam\u00E9ra",
                server.devices.camera.status
            )

            PrepStatusRow(
                "GPS",
                server.devices.gps.status
            )

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
                    text =
                        if (ready)
                            "Continuer la pr\u00E9paration"
                        else
                            "Mat\u00E9riel essentiel non pr\u00EAt",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (
            server != null &&
            server.devices.gps.status.lowercase() != "fix"
        ) {
            Spacer(Modifier.height(12.dp))

            Text(
                text =
                    "La position GPS n'est pas fix\u00E9e. Cela ne bloque pas la connexion, " +
                    "mais StellarPilot le prendra en compte pour l'astrom\u00E9trie.",
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
            .filter {
                it.alignmentCandidate &&
                    it.alignmentScore != null
            }
            .sortedByDescending {
                it.alignmentScore
            }
            .take(4)

    val star =
        recommendations.firstOrNull {
            it.id == selectedStarId
        }
            ?: sky?.recommended
            ?: recommendations.firstOrNull()


    var latitudeText by rememberSaveable(demoMode) {
        mutableStateOf(
            if (demoMode)
                "47.4308"
            else
                ""
        )
    }

    var longitudeText by rememberSaveable(demoMode) {
        mutableStateOf(
            if (demoMode)
                "-0.6271"
            else
                ""
        )
    }

    LaunchedEffect(
        skyState.sky?.observer?.latitude,
        skyState.sky?.observer?.longitude
    ) {
        skyState.sky
            ?.observer
            ?.latitude
            ?.let { latitude ->

                latitudeText =
                    String.format(
                        java.util.Locale.US,
                        "%.5f",
                        latitude
                    )
            }

        skyState.sky
            ?.observer
            ?.longitude
            ?.let { longitude ->

                longitudeText =
                    String.format(
                        java.util.Locale.US,
                        "%.5f",
                        longitude
                    )
            }
    }

    val locationSourceLabel =
        when (
            skyState.sky
                ?.observer
                ?.locationSource
                ?.lowercase()
        ) {

            "gps" ->
                "GPS"

            "query" ->
                "Personnalisée"

            "manual" ->
                "Manuelle"

            else ->
                "En attente"
        }
    val selectedLatitude =
        latitudeText
            .replace(',', '.')
            .toDoubleOrNull()

    val selectedLongitude =
        longitudeText
            .replace(',', '.')
            .toDoubleOrNull()

    val locationValid =
        selectedLatitude != null &&
        selectedLongitude != null &&
        selectedLatitude in -90.0..90.0 &&
        selectedLongitude in -180.0..180.0
    AssistantCard(
        title = "Étoile de référence",
        subtitle =
            "Choisis une des meilleures étoiles calculées par StellarPilot"
    ) {

        Text(
            text =
                "LOCALISATION UTILIS\u00C9E",
            color =
                StellarOrange,
            style =
                MaterialTheme
                    .typography
                    .labelLarge,
            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            Modifier.height(
                6.dp
            )
        )

        Text(
            text =
                "Source : $locationSourceLabel",
            color =
                StellarMuted,
            style =
                MaterialTheme
                    .typography
                    .bodySmall
        )

        Spacer(
            Modifier.height(
                10.dp
            )
        )

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(
                    12.dp
                )
        ) {

            OutlinedTextField(
                value =
                    latitudeText,
                onValueChange = {
                    latitudeText = it
                },
                modifier =
                    Modifier.weight(
                        1f
                    ),
                singleLine =
                    true,
                label = {
                    Text(
                        "Latitude"
                    )
                }
            )

            OutlinedTextField(
                value =
                    longitudeText,
                onValueChange = {
                    longitudeText = it
                },
                modifier =
                    Modifier.weight(
                        1f
                    ),
                singleLine =
                    true,
                label = {
                    Text(
                        "Longitude"
                    )
                }
            )
        }

        Spacer(
            Modifier.height(
                10.dp
            )
        )

        Button(
            onClick = {

                if (
                    selectedLatitude != null &&
                    selectedLongitude != null
                ) {

                    onSetLocation(
                        selectedLatitude,
                        selectedLongitude
                    )
                }
            },
            enabled =
                locationValid &&
                !skyState.isLoading,
            modifier =
                Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults
                    .buttonColors(
                        containerColor =
                            StellarOrange,
                        contentColor =
                            StellarBackground
                    )
        ) {

            Text(
                text =
                    "Utiliser cette localisation",
                fontWeight =
                    FontWeight.Bold
            )
        }

        Spacer(
            Modifier.height(
                20.dp
            )
        )
        when {

            skyState.isLoading &&
                sky == null -> {

                Text(
                    text =
                        "Calcul des meilleures étoiles...",
                    color = StellarMuted
                )
            }

            recommendations.isNotEmpty() -> {

                Text(
                    text =
                        "4 meilleures étoiles de référence",
                    color = StellarText,
                    style =
                        MaterialTheme.typography.titleMedium,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    Modifier.height(12.dp)
                )

                recommendations.forEachIndexed {
                        index,
                        candidate ->

                    val selected =
                        candidate.id == star?.id

                    val score =
                        candidate.alignmentScore
                            ?.let {
                                String.format(
                                    java.util.Locale.FRANCE,
                                    "%.0f %%",
                                    it * 100.0
                                )
                            }
                            ?: "—"

                    val label =
                        buildString {

                            append(
                                if (selected)
                                    "\u2605 "
                                else
                                    "\u2606 "
                            )

                            append(candidate.name)

                            if (index == 0) {
                                append(
                                    " \u00B7 recommandée"
                                )
                            }

                            append("\n")
                            append(
                                candidate.constellation
                            )

                            append(" \u00B7 Alt. ")

                            append(
                                String.format(
                                    java.util.Locale.FRANCE,
                                    "%.1f°",
                                    candidate.altitudeDeg
                                )
                            )

                            append(" \u00B7 mag ")

                            append(
                                String.format(
                                    java.util.Locale.FRANCE,
                                    "%.2f",
                                    candidate.magnitude
                                )
                            )

                            append(" \u00B7 score ")
                            append(score)
                        }

                    if (selected) {

                        Button(
                            onClick = {
                                onSelectStar(
                                    candidate.id
                                )
                            },
                            modifier =
                                Modifier.fillMaxWidth(),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor =
                                        StellarOrange,
                                    contentColor =
                                        StellarBackground
                                )
                        ) {
                            Text(
                                text = label,
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }

                    } else {

                        OutlinedButton(
                            onClick = {
                                onSelectStar(
                                    candidate.id
                                )
                            },
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = label
                            )
                        }
                    }

                    Spacer(
                        Modifier.height(8.dp)
                    )
                }

                star?.let { selectedStar ->

                    Spacer(
                        Modifier.height(12.dp)
                    )

                    Text(
                        text =
                            "ÉTOILE SÉLECTIONNÉE",
                        color = StellarOrange,
                        style =
                            MaterialTheme.typography.labelLarge,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Text(
                        text =
                            "\u2605 ${selectedStar.name}",
                        color = StellarGreen,
                        style =
                            MaterialTheme.typography.headlineSmall,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(4.dp)
                    )

                    Text(
                        text =
                            "Constellation : ${selectedStar.constellation}",
                        color = StellarMuted
                    )

                    Spacer(
                        Modifier.height(14.dp)
                    )

                    InfoBlock(
                        label = "Magnitude",
                        value =
                            String.format(
                                java.util.Locale.FRANCE,
                                "%.2f",
                                selectedStar.magnitude
                            )
                    )

                    InfoBlock(
                        label = "Altitude",
                        value =
                            String.format(
                                java.util.Locale.FRANCE,
                                "%.1f°",
                                selectedStar.altitudeDeg
                            )
                    )

                    InfoBlock(
                        label = "Azimut",
                        value =
                            String.format(
                                java.util.Locale.FRANCE,
                                "%.1f° %s",
                                selectedStar.azimuthDeg,
                                selectedStar.azimuthDirection
                            )
                    )

                    InfoBlock(
                        label = "Score",
                        value =
                            selectedStar.alignmentScore
                                ?.let {
                                    String.format(
                                        java.util.Locale.FRANCE,
                                        "%.0f %%",
                                        it * 100.0
                                    )
                                }
                                ?: "Non disponible"
                    )
                }
            }

            sky?.status ==
                "location_required" -> {

                Text(
                    text =
                        "Une position est nécessaire avant de choisir " +
                        "l'étoile de référence.",
                    color = StellarOrange
                )

                Spacer(
                    Modifier.height(12.dp)
                )

                Button(
                    onClick = onOpenSky,
                    modifier =
                        Modifier.fillMaxWidth(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor =
                                StellarOrange,
                            contentColor =
                                StellarBackground
                        )
                ) {
                    Text(
                        "Renseigner la position dans Ciel"
                    )
                }
            }

            else -> {

                Text(
                    text =
                        skyState.error
                            ?: "Aucune étoile de référence disponible actuellement.",
                    color = StellarOrange
                )
            }
        }

        Spacer(
            Modifier.height(18.dp)
        )

        OutlinedButton(
            onClick = onRefresh,
            enabled =
                !skyState.isLoading,
            modifier =
                Modifier.fillMaxWidth()
        ) {
            Text(
                "Recalculer les étoiles"
            )
        }

        Spacer(
            Modifier.height(8.dp)
        )


        Spacer(
            Modifier.height(16.dp)
        )

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {

            OutlinedButton(
                onClick = onPrevious,
                modifier =
                    Modifier.weight(1f)
            ) {
                Text("Retour")
            }

            Button(
                onClick = {
                    star?.let {
                        onNext(it.id)
                    }
                },
                enabled =
                    star != null,
                modifier =
                    Modifier.weight(1f),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            StellarOrange,
                        contentColor =
                            StellarBackground
                    )
            ) {
                Text(
                    text =
                        star?.let {
                            "Utiliser ${it.name}"
                        }
                            ?: "Choisir",
                    fontWeight =
                        FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PositionStep(
    demoMode: Boolean,
    mountFamily: String?,
    startupTarget: String?,
    mountTypeLabel: String?,
    mountType: String?,
    latitude: Double?,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val family =
        if (demoMode)
            "eq"
        else
            mountFamily?.lowercase()

    val isEq = family == "eq"
    val isAz = family == "az"

    val detected = isEq || isAz

    val familyTitle =
        when {
            demoMode ->
                "Monture \u00E9quatoriale de d\u00E9monstration"

            isEq ->
                "Monture \u00E9quatoriale d\u00E9tect\u00E9e"

            isAz ->
                "Monture Alt-Az d\u00E9tect\u00E9e"

            else ->
                "Type de monture non d\u00E9tect\u00E9"
        }

    val familyLabel =
        when {
            isEq ->
                "\u00C9quatoriale (EQ)"

            isAz ->
                "Alt-Az (AZ)"

            else ->
                "Inconnu"
        }

    val target =
        when {
            demoMode ->
                "P\u00F4le c\u00E9leste Nord"

            startupTarget == "zenith" || isAz ->
                "Z\u00E9nith"

            startupTarget == "celestial_pole" || isEq ->
                when {
                    latitude == null ->
                        "P\u00F4le c\u00E9leste"

                    latitude >= 0.0 ->
                        "P\u00F4le c\u00E9leste Nord"

                    else ->
                        "P\u00F4le c\u00E9leste Sud"
                }

            else ->
                "Non disponible"
        }

    val instruction =
        when {
            demoMode ->
                "En mode d\u00E9monstration, StellarPilot suppose une monture " +
                    "\u00E9quatoriale positionn\u00E9e vers le p\u00F4le c\u00E9leste Nord."

            isEq && latitude == null ->
                "Oriente la monture vers le p\u00F4le c\u00E9leste. " +
                    "StellarPilot d\u00E9terminera automatiquement Nord ou Sud " +
                    "d\u00E8s que la latitude sera disponible."

            isEq ->
                "Oriente la monture vers le $target avant de poursuivre."

            isAz ->
                "Oriente le tube vers le z\u00E9nith avant de poursuivre."

            else ->
                "StellarPilot n'a pas encore pu d\u00E9terminer automatiquement " +
                    "la famille de la monture."
        }

    AssistantCard(
        title = "Position initiale",
        subtitle =
            if (demoMode)
                "Mode d\u00E9monstration \u2022 aucun mat\u00E9riel requis"
            else
                "D\u00E9tection automatique via INDI / OnStep"
    ) {

        Text(
            text = familyTitle,
            color =
                if (detected)
                    StellarGreen
                else
                    StellarOrange,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        InfoBlock(
            label = "Famille",
            value = familyLabel
        )

        InfoBlock(
            label =
                if (demoMode)
                    "Type"
                else
                    "Type d\u00E9tect\u00E9",
            value =
                if (demoMode)
                    "Monture de d\u00E9monstration (EQ)"
                else
                    mountTypeLabel
                        ?: mountType
                        ?: "Non disponible"
        )

        InfoBlock(
            label = "Position de d\u00E9part",
            value = target
        )

        if (detected) {

            Spacer(Modifier.height(18.dp))

            Image(
                painter = painterResource(
                    id =
                        if (isEq)
                            R.drawable.ic_mount_eq
                        else
                            R.drawable.ic_mount_az
                ),
                contentDescription =
                    if (isEq)
                        "Monture \u00E9quatoriale point\u00E9e vers le p\u00F4le c\u00E9leste"
                    else
                        "Monture Alt-Az point\u00E9e vers le z\u00E9nith",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(Modifier.height(18.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor =
                    StellarSurfaceRaised
            ),
            border = BorderStroke(
                1.dp,
                if (detected)
                    StellarGreen.copy(alpha = 0.45f)
                else
                    StellarOrange.copy(alpha = 0.45f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {

                Text(
                    text = "Consigne",
                    color = StellarOrange,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = instruction,
                    color = StellarText
                )
            }
        }

        if (
            !demoMode &&
            isEq &&
            latitude == null
        ) {
            Spacer(Modifier.height(10.dp))

            Text(
                text =
                    "Latitude GPS indisponible : l'h\u00E9misph\u00E8re " +
                    "n'est pas encore d\u00E9termin\u00E9 automatiquement.",
                color = StellarMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(20.dp))

        if (!demoMode) {
            Button(
                onClick = onNext,
                enabled = detected,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StellarOrange,
                    contentColor = StellarBackground
                )
            ) {
                Text(
                    text =
                        if (detected)
                            "J'ai positionn\u00E9 la monture"
                        else
                            "Type de monture requis",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        PreviousButton(onPrevious)
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
                "Si la mise au point a d\u00E9j\u00E0 \u00E9t\u00E9 r\u00E9alis\u00E9e et n'a pas " +
                "boug\u00E9, cette \u00E9tape peut \u00EAtre saut\u00E9e.",
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
            Text(
                "Faire la mise au point Bahtinov",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = onKeepFocus,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Conserver le r\u00E9glage actuel")
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
        subtitle = cameraName ?: "Cam\u00E9ra non identifi\u00E9e"
    ) {

        Text(
            text =
                "R\u00E8gle StellarPilot : un master dark existant pourra \u00EAtre " +
                "r\u00E9utilis\u00E9 tant que la cam\u00E9ra n'a pas chang\u00E9.",
            color = StellarText
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text =
                "Si une autre cam\u00E9ra est d\u00E9tect\u00E9e, l'ancien master dark " +
                "sera invalid\u00E9 et de nouveaux darks seront obligatoires.",
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
            Text(
                "Cr\u00E9er / valider les darks",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            enabled = false
        ) {
            Text("R\u00E9utiliser le master dark compatible")
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text =
                "La r\u00E9utilisation sera activ\u00E9e d\u00E8s que StellarPilot " +
                "enregistrera les m\u00E9tadonn\u00E9es du premier master dark.",
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
        title = "Pr\u00E9paration termin\u00E9e",
        subtitle = "Le syst\u00E8me est pr\u00EAt pour l'observation"
    ) {

        Text(
            text =
                "Lorsque toutes les fonctions seront connect\u00E9es au serveur, " +
                "cet \u00E9cran r\u00E9capitulera les validations de la session.",
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
            Text(
                "Passer \u00E0 Ciel & Cible",
                fontWeight = FontWeight.Bold
            )
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
        subtitle = "Workflow pr\u00E9par\u00E9 \u2022 int\u00E9gration serveur \u00E0 venir"
    ) {

        Text(
            text = description,
            color = StellarText
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = StellarOrange,
                contentColor = StellarBackground
            )
        ) {
            Text(
                action,
                fontWeight = FontWeight.Bold
            )
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
        colors = CardDefaults.cardColors(
            containerColor = StellarSurface
        ),
        border = BorderStroke(
            1.dp,
            StellarBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
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
        color =
            if (active)
                StellarOrange.copy(alpha = 0.15f)
            else
                StellarSurfaceRaised,
        border = BorderStroke(
            1.dp,
            if (active || completed)
                StellarOrange
            else
                StellarBorder
        )
    ) {
        Text(
            text =
                if (completed)
                    "\u2713 $name"
                else
                    "$number $name",
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 7.dp
            ),
            color =
                if (active || completed)
                    StellarOrange
                else
                    StellarMuted,
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
                .background(
                    statusColor(status),
                    CircleShape
                )
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

private fun goodStatus(
    value: String
): Boolean =
    value.lowercase() in setOf(
        "online",
        "ready",
        "ok",
        "fix",
        "connected"
    )

private fun statusColor(
    value: String
): Color =
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
