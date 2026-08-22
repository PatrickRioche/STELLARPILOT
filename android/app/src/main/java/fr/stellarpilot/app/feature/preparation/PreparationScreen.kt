package fr.stellarpilot.app.feature.preparation

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

    val state = viewModel.uiState
    val server = state.server

    val skyViewModel: SkyViewModel =
        viewModel()

    val skyState =
        skyViewModel.uiState

    LaunchedEffect(Unit) {
        viewModel.connect()
    }

    LaunchedEffect(
        currentStep,
        state.serverBaseUrl
    ) {
        if (currentStep == 3) {
            skyViewModel.load(
                state.serverBaseUrl
            )
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
                    onRefresh = viewModel::connect,
                    onChangeServer = viewModel::setServerAddress,
                    onContinue = { currentStep = 1 }
                )

                1 -> PositionStep(
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

                2 -> PrototypeStep(
                    title = "Premi\u00E8re astrom\u00E9trie",
                    description =
                        if (server?.devices?.gps?.status?.lowercase() == "fix") {
                            "Position GPS fix\u00E9e. StellarPilot pourra capturer une image " +
                            "et tenter la premi\u00E8re r\u00E9solution astrom\u00E9trique."
                        } else {
                            "La position GPS n'est pas encore fix\u00E9e. L'assistant devra " +
                            "signaler cette situation avant la premi\u00E8re astrom\u00E9trie."
                        },
                    action = "Voir l'\u00E9tape suivante",
                    onPrevious = { currentStep = 1 },
                    onNext = { currentStep = 3 }
                )

                3 -> ReferenceStarStep(
                    skyState = skyState,

                    onRefresh = {
                        skyViewModel.load(
                            state.serverBaseUrl
                        )
                    },

                    onOpenSky = onOpenSky,

                    onPrevious = {
                        currentStep = 2
                    },

                    onNext = {
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
    onRefresh: () -> Unit,
    onChangeServer: (String) -> Unit,
    onContinue: () -> Unit
) {
    val server = state.server

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

        if (server == null) {
            Text(
                text =
                    if (state.isConnecting)
                        "Connexion au serveur StellarPilot..."
                    else
                        "Serveur non connect\u00E9",
                color = StellarMuted
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

        Button(
            onClick = onContinue,
            enabled = ready,
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
    skyState: SkyUiState,
    onRefresh: () -> Unit,
    onOpenSky: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val sky = skyState.sky
    val star = sky?.recommended

    AssistantCard(
        title = "\u00C9toile de r\u00E9f\u00E9rence",
        subtitle =
            "S\u00E9lection automatique par le moteur Ciel"
    ) {

        when {

            skyState.isLoading &&
                sky == null -> {

                Text(
                    text =
                        "Calcul de la meilleure \u00E9toile...",
                    color = StellarMuted
                )
            }

            star != null -> {

                Text(
                    text =
                        "\u2605 ${star.name}",
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
                        star.constellation,
                    color = StellarMuted
                )

                Spacer(
                    Modifier.height(16.dp)
                )

                InfoBlock(
                    label = "Magnitude",
                    value =
                        String.format(
                            java.util.Locale.US,
                            "%.2f",
                            star.magnitude
                        )
                )

                InfoBlock(
                    label = "Altitude",
                    value =
                        String.format(
                            java.util.Locale.US,
                            "%.1f\u00B0",
                            star.altitudeDeg
                        )
                )

                InfoBlock(
                    label = "Azimut",
                    value =
                        String.format(
                            java.util.Locale.US,
                            "%.1f\u00B0 %s",
                            star.azimuthDeg,
                            star.azimuthDirection
                        )
                )

                InfoBlock(
                    label = "Ascension droite",
                    value =
                        String.format(
                            java.util.Locale.US,
                            "%.4f h",
                            star.raHours
                        )
                )

                InfoBlock(
                    label = "D\u00E9clinaison",
                    value =
                        String.format(
                            java.util.Locale.US,
                            "%.4f\u00B0",
                            star.decDeg
                        )
                )

                InfoBlock(
                    label = "Score",
                    value =
                        star.alignmentScore
                            ?.let {
                                String.format(
                                    java.util.Locale.US,
                                    "%.0f %%",
                                    it * 100.0
                                )
                            }
                            ?: "Non disponible"
                )

                Spacer(
                    Modifier.height(14.dp)
                )

                Text(
                    text =
                        "StellarPilot recommande cette \u00E9toile car elle est " +
                        "brillante et bien plac\u00E9e dans le ciel.",
                    color = StellarMuted,
                    style =
                        MaterialTheme.typography.bodySmall
                )
            }

            sky?.status ==
                "location_required" -> {

                Text(
                    text =
                        "Une position est n\u00E9cessaire avant de choisir " +
                        "l'\u00E9toile de r\u00E9f\u00E9rence.",
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
                            ?: "Aucune \u00E9toile recommand\u00E9e actuellement.",
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
                "Actualiser la recommandation"
            )
        }

        Spacer(
            Modifier.height(8.dp)
        )

        OutlinedButton(
            onClick = onOpenSky,
            modifier =
                Modifier.fillMaxWidth()
        ) {
            Text(
                "Voir toutes les \u00E9toiles dans Ciel"
            )
        }

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
                Text("Pr\u00E9c\u00E9dent")
            }

            Button(
                onClick = onNext,
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
                            ?: "Choisir une \u00E9toile",
                    fontWeight =
                        FontWeight.Bold
                )
            }
        }
    }
}


@Composable
private fun PositionStep(
    mountFamily: String?,
    startupTarget: String?,
    mountTypeLabel: String?,
    mountType: String?,
    latitude: Double?,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val family = mountFamily?.lowercase()

    val isEq = family == "eq"
    val isAz = family == "az"

    val detected = isEq || isAz

    val familyTitle =
        when {
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
        subtitle = "D\u00E9tection automatique via INDI / OnStep"
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
            label = "Type d\u00E9tect\u00E9",
            value =
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