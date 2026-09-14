package fr.stellarpilot.app.feature.preparation

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.stellarpilot.app.R
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
import kotlin.math.roundToInt

private const val DEFAULT_SERVER = "10.42.0.1"
private const val PREFS_NAME = "stellarpilot_connection"
private const val PREF_SERVER = "server_base_url"
private const val ASTROMETRY_PREFS_NAME = "stellarpilot_astrometry"
private const val PREF_ASTROMETRY_MIN_STARS = "minimum_stars"
private const val DEFAULT_ASTROMETRY_MIN_STARS = 80
private const val MIN_ASTROMETRY_STARS = 10
private const val MAX_ASTROMETRY_STARS = 200
private const val ASTROMETRY_STAR_STEP = 5

private val assistantSteps = listOf(
    "Connexion",
    "Pointage",
    "Astrométrie",
    "Darks"
)

@Composable
fun AssistantFinalScreen(
    onOpenSky: () -> Unit,
    connectionViewModel: ConnectionViewModel = viewModel()
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var telescopeCapped by rememberSaveable { mutableStateOf(false) }
    var darkEnteredDirectly by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val astrometryPreferences = remember {
        context.getSharedPreferences(ASTROMETRY_PREFS_NAME, Context.MODE_PRIVATE)
    }
    var astrometryMinimumStars by rememberSaveable {
        mutableIntStateOf(
            astrometryPreferences
                .getInt(PREF_ASTROMETRY_MIN_STARS, DEFAULT_ASTROMETRY_MIN_STARS)
                .coerceIn(MIN_ASTROMETRY_STARS, MAX_ASTROMETRY_STARS)
        )
    }

    val connectionState = connectionViewModel.uiState
    val baseUrl = connectionState.serverBaseUrl
    val server = connectionState.server

    val mountViewModel: MountDiagnosticsViewModel = viewModel()
    val mountState = mountViewModel.uiState

    val astrometryViewModel: CameraPreviewViewModel = viewModel()
    val astrometryState = astrometryViewModel.uiState

    val darkViewModel: DarkCalibrationViewModel = viewModel()
    val darkState = darkViewModel.uiState

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
                "Mode calibration : les étapes de pointage et d'astrométrie sont ignorées.",
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

            1 -> AssistantPointingStep(
                mountFamily = server?.session?.mountFamily
                    ?: server?.devices?.mount?.family,
                startupTarget = server?.session?.startupTarget
                    ?: server?.devices?.mount?.startupTarget,
                mountTypeLabel = server?.devices?.mount?.typeLabel,
                mountType = server?.session?.mountType
                    ?: server?.devices?.mount?.type,
                latitude = server?.session?.latitude
                    ?: server?.devices?.gps?.latitude,
                onPrevious = { step = 0 },
                onContinue = { step = 2 }
            )

            2 -> AssistantAstrometryStep(
                state = astrometryState,
                mountState = mountState,
                minimumStars = astrometryMinimumStars,
                onMinimumStarsChange = { value ->
                    astrometryMinimumStars = value
                    astrometryPreferences.edit()
                        .putInt(PREF_ASTROMETRY_MIN_STARS, value)
                        .apply()
                },
                onCapture = {
                    astrometryViewModel.load(
                        serverBaseUrl = baseUrl,
                        exposureSeconds = 4.0,
                        minimumStars = astrometryMinimumStars
                    )
                },
                onPrevious = { step = 1 },
                onContinue = {
                    darkEnteredDirectly = false
                    telescopeCapped = false
                    darkViewModel.reset()
                    step = 3
                }
            )

            else -> AssistantDarkStep(
                state = darkState,
                telescopeCapped = telescopeCapped,
                directMode = darkEnteredDirectly,
                onCapped = { telescopeCapped = true },
                onStart = { darkViewModel.start(baseUrl) },
                onPrevious = { step = if (darkEnteredDirectly) 0 else 2 },
                onContinue = onOpenSky
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
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    var serverAddress by rememberSaveable { mutableStateOf(DEFAULT_SERVER) }

    val state = connectionViewModel.uiState
    val server = state.server
    val mount = server?.devices?.mount
    val camera = server?.devices?.camera
    val gps = server?.devices?.gps
    val mountState = mountViewModel.uiState

    val activeAddress = state.serverBaseUrl
        .removePrefix("http://")
        .removePrefix("https://")
        .removeSuffix("/")
        .removeSuffix(":8000")

    val mountReady = mount?.status?.lowercase() in setOf("ready", "ok", "online")
    val cameraReady = camera?.status?.lowercase() in setOf("ready", "ok", "online")
    val gpsReady = gps?.status?.lowercase() in setOf("fix", "available")
    val timeReady = mountState.timeSyncVerified

    fun applyServer(address: String) {
        val clean = address.trim().ifBlank { DEFAULT_SERVER }
        preferences.edit().putString(PREF_SERVER, clean).apply()
        serverAddress = clean
        connectionViewModel.setServerAddress(clean)
        connectionViewModel.connect()
        mountViewModel.refresh(connectionViewModel.uiState.serverBaseUrl)
    }

    LaunchedEffect(Unit) {
        val saved = preferences.getString(PREF_SERVER, null)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_SERVER
        serverAddress = saved
        connectionViewModel.setServerAddress(saved)
        connectionViewModel.connect()
        mountViewModel.refresh(connectionViewModel.uiState.serverBaseUrl)
    }

    AssistantCard("Connexion et contrôle du setup") {
        Text("Serveur StellarPilot", color = StellarText, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text("Adresse active • $activeAddress", color = StellarMuted)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = serverAddress,
            onValueChange = { serverAddress = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Autre adresse") },
            supportingText = { Text("Par défaut : 10.42.0.1") }
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { applyServer(serverAddress) },
            modifier = Modifier.fillMaxWidth(),
            colors = assistantPrimaryButtonColors()
        ) {
            Text("UTILISER CETTE ADRESSE", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))
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
            Text("SUITE • POINTAGE", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AssistantPointingStep(
    mountFamily: String?,
    startupTarget: String?,
    mountTypeLabel: String?,
    mountType: String?,
    latitude: Double?,
    onPrevious: () -> Unit,
    onContinue: () -> Unit
) {
    val family = mountFamily?.lowercase()
    val isEq = family == "eq"
    val isAz = family == "az"

    val familyLabel = when {
        isEq -> "Équatoriale (EQ)"
        isAz -> "Alt-Az (AZ)"
        else -> "Inconnue"
    }

    val target = when {
        startupTarget == "zenith" || isAz -> "Zénith"
        startupTarget == "celestial_pole" || isEq -> when {
            latitude == null -> "Pôle céleste"
            latitude >= 0.0 -> "Pôle céleste Nord"
            else -> "Pôle céleste Sud"
        }
        else -> "À déterminer selon la monture"
    }

    val instruction = when {
        isEq && latitude == null ->
            "Orientez la monture vers le pôle céleste. Nord ou Sud sera déterminé dès que la latitude sera disponible."
        isEq -> "Orientez la monture vers le $target avant de poursuivre."
        isAz -> "Orientez le tube vers le zénith avant de poursuivre."
        else ->
            "Le type de monture n'est pas encore déterminé. Vérifiez la position de départ adaptée à votre monture avant de poursuivre."
    }

    AssistantCard("Pointage initial de la monture") {
        Text(
            when {
                isEq -> "Monture équatoriale détectée"
                isAz -> "Monture Alt-Az détectée"
                else -> "Type de monture non détecté"
            },
            color = if (isEq || isAz) StellarGreen else StellarOrange,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        StatusValue("Famille", familyLabel)
        StatusValue("Type", mountTypeLabel ?: mountType ?: "Non disponible")
        StatusValue("Position de départ", target)
        latitude?.let {
            StatusValue("Latitude", String.format(Locale.FRANCE, "%+.5f°", it))
        }

        if (isEq || isAz) {
            Spacer(Modifier.height(14.dp))
            Image(
                painter = painterResource(
                    id = if (isEq) R.drawable.ic_mount_eq else R.drawable.ic_mount_az
                ),
                contentDescription = if (isEq) {
                    "Monture équatoriale pointée vers le pôle céleste"
                } else {
                    "Monture Alt-Az pointée vers le zénith"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                contentScale = ContentScale.Fit
            )
        }

        Spacer(Modifier.height(12.dp))
        Text("Consigne", color = StellarOrange, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(instruction, color = StellarText)

        Spacer(Modifier.height(14.dp))
        NavigationButtons(
            onPrevious = onPrevious,
            onContinue = onContinue,
            continueEnabled = true,
            continueText = "SUITE • ASTROMÉTRIE"
        )
    }
}

@Composable
private fun AssistantAstrometryStep(
    state: CameraPreviewUiState,
    mountState: MountDiagnosticsUiState,
    minimumStars: Int,
    onMinimumStarsChange: (Int) -> Unit,
    onCapture: () -> Unit,
    onPrevious: () -> Unit,
    onContinue: () -> Unit
) {
    val detectedStars = state.qualityStarCount
    val thresholdReached = detectedStars != null && detectedStars >= minimumStars

    AssistantCard("Astrométrie de préparation") {
        Text(
            "Le nombre d'étoiles détectées décide maintenant si astrometry.net est lancé. Le score qualité reste seulement informatif.",
            color = StellarText
        )
        Spacer(Modifier.height(12.dp))

        Text(
            "Seuil astrométrie : $minimumStars étoiles",
            color = StellarText,
            fontWeight = FontWeight.Bold
        )
        Slider(
            value = minimumStars.toFloat(),
            onValueChange = { raw ->
                val snapped = (
                    ((raw - MIN_ASTROMETRY_STARS) / ASTROMETRY_STAR_STEP)
                        .roundToInt() * ASTROMETRY_STAR_STEP + MIN_ASTROMETRY_STARS
                    ).coerceIn(MIN_ASTROMETRY_STARS, MAX_ASTROMETRY_STARS)
                onMinimumStarsChange(snapped)
            },
            valueRange = MIN_ASTROMETRY_STARS.toFloat()..MAX_ASTROMETRY_STARS.toFloat(),
            steps = ((MAX_ASTROMETRY_STARS - MIN_ASTROMETRY_STARS) / ASTROMETRY_STAR_STEP) - 1,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Minimum 10 • Maximum 200 • pas de 5",
            color = StellarMuted,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                detectedStars == null -> "Étoiles détectées : —"
                thresholdReached -> "Étoiles détectées : $detectedStars • astrométrie autorisée"
                else -> "Étoiles détectées : $detectedStars • seuil non atteint"
            },
            color = when {
                detectedStars == null -> StellarMuted
                thresholdReached -> StellarGreen
                else -> StellarOrange
            },
            fontWeight = if (detectedStars != null) FontWeight.SemiBold else FontWeight.Normal
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

        state.solveDetail?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = if (thresholdReached) StellarGreen else StellarOrange)
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
            continueEnabled = true,
            continueText = "SUITE • DARKS"
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
                "Mode darks directs • aucune validation de pointage ou d'astrométrie n'est nécessaire.",
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
            continueEnabled = true,
            continueText = "TERMINER • ALLER À CIEL"
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