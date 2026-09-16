package fr.stellarpilot.app.feature.preparation

import android.content.Context
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
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
import kotlin.math.abs

private const val RESTORED_DEFAULT_SERVER = "10.42.0.1"
private const val RESTORED_PREFS_NAME = "stellarpilot_connection"
private const val RESTORED_PREF_SERVER = "server_base_url"
private const val RESTORED_PREPARATION_EXPOSURE_SECONDS = 4.0
private const val RESTORED_PREPARATION_MIN_STARS = 30

@Composable
fun AssistantV069RestoredScreen(
    onOpenSky: () -> Unit,
    connectionViewModel: ConnectionViewModel = viewModel()
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var telescopeCapped by rememberSaveable { mutableStateOf(false) }
    var darkOnly by rememberSaveable { mutableStateOf(false) }
    var selectedStarId by rememberSaveable { mutableStateOf<String?>(null) }
    var maskInstalled by rememberSaveable { mutableStateOf(false) }
    var maskRemoved by rememberSaveable { mutableStateOf(false) }

    val connection = connectionViewModel.uiState
    val baseUrl = connection.serverBaseUrl

    val mountViewModel: MountDiagnosticsViewModel = viewModel()
    val mount = mountViewModel.uiState

    val astrometryViewModel: CameraPreviewViewModel = viewModel()
    val astrometry = astrometryViewModel.uiState

    val homeViewModel: MountHomeViewModel = viewModel()
    val home = homeViewModel.uiState

    val skyViewModel: SkyViewModel = viewModel()
    val sky = skyViewModel.uiState

    val centeringViewModel: AssistantCenteringViewModel = viewModel()
    val centering = centeringViewModel.uiState

    val bahtinovViewModel: BahtinovViewModel = viewModel()
    val bahtinov = bahtinovViewModel.uiState

    val darkViewModel: DarkCalibrationViewModel = viewModel()
    val dark = darkViewModel.uiState

    LaunchedEffect(step, baseUrl) {
        when (step) {
            0 -> mountViewModel.refresh(baseUrl)
            2 -> skyViewModel.load(baseUrl)
        }
    }

    val focusStars = remember(sky.sky) {
        sky.sky?.stars
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
            "ASSISTANT STELLARPILOT • V0.6.9",
            color = StellarOrange,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            when (step) {
                0 -> "1/5 • Connexion, matériel, heure & GPS"
                1 -> "2/5 • HOME monture & calibration astrométrique"
                2 -> "3/5 • Mise au point Bahtinov"
                3 -> "4/5 • Master Dark"
                else -> "5/5 • Prêt à observer"
            },
            color = StellarText,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = (step + 1) / 5f,
            modifier = Modifier.fillMaxWidth(),
            color = StellarOrange
        )
        Spacer(Modifier.height(14.dp))

        when (step) {
            0 -> RestoredConnectionStep(
                connectionViewModel = connectionViewModel,
                mountViewModel = mountViewModel,
                onContinue = {
                    darkOnly = false
                    step = 1
                },
                onDirectDarks = {
                    darkOnly = true
                    telescopeCapped = false
                    darkViewModel.reset()
                    step = 3
                }
            )

            1 -> RestoredHomeStep(
                baseUrl = baseUrl,
                astrometryViewModel = astrometryViewModel,
                astrometry = astrometry,
                homeViewModel = homeViewModel,
                home = home,
                onBack = {
                    homeViewModel.reset()
                    step = 0
                },
                onContinue = {
                    darkOnly = false
                    selectedStarId = null
                    maskInstalled = false
                    maskRemoved = false
                    centeringViewModel.reset()
                    bahtinovViewModel.resetFocus()
                    step = 2
                }
            )

            2 -> RestoredBahtinovStep(
                stars = focusStars,
                selectedStar = selectedStar,
                selectedStarId = selectedStarId,
                skyLoading = sky.isLoading,
                skyError = sky.error,
                centeringState = centering,
                bahtinovState = bahtinov,
                maskInstalled = maskInstalled,
                maskRemoved = maskRemoved,
                onSelectStar = {
                    selectedStarId = it.id
                    maskInstalled = false
                    maskRemoved = false
                    centeringViewModel.reset()
                    bahtinovViewModel.resetFocus()
                },
                onRefreshStars = { skyViewModel.load(baseUrl) },
                onGotoAndCenter = {
                    selectedStar?.let { centeringViewModel.gotoAndCenter(baseUrl, it) }
                },
                onManualNudge = { centeringViewModel.nudgeManual(baseUrl, it) },
                onVerifyCentering = { centeringViewModel.verifyManualCentering(baseUrl) },
                onMaskInstalled = {
                    maskInstalled = true
                    maskRemoved = false
                },
                onFocusCapture = { bahtinovViewModel.captureFocus(baseUrl) },
                onMaskRemoved = { maskRemoved = true },
                onBack = { step = 1 },
                onContinue = {
                    telescopeCapped = false
                    darkViewModel.reset()
                    step = 3
                },
                onSkip = {
                    telescopeCapped = false
                    darkViewModel.reset()
                    step = 3
                }
            )

            3 -> RestoredDarkStep(
                dark = dark,
                telescopeCapped = telescopeCapped,
                darkOnly = darkOnly,
                darkViewModel = darkViewModel,
                baseUrl = baseUrl,
                onCapped = { telescopeCapped = true },
                onBack = { step = if (darkOnly) 0 else 2 },
                onDone = { step = 4 }
            )

            else -> RestoredCard("PRÊT À OBSERVER") {
                Text(
                    "Retirez le bouchon. La monture est calibrée et le Master Dark est disponible pour le stacking.",
                    color = StellarGreen,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Le mode Bahtinov reste également accessible depuis l'écran Capture pour refaire la mise au point pendant la session.",
                    color = StellarText
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onOpenSky,
                    modifier = Modifier.fillMaxWidth(),
                    colors = restoredPrimaryColors()
                ) {
                    Text("OUVRIR LE CIEL", fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RestoredConnectionStep(
    connectionViewModel: ConnectionViewModel,
    mountViewModel: MountDiagnosticsViewModel,
    onContinue: () -> Unit,
    onDirectDarks: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences(RESTORED_PREFS_NAME, Context.MODE_PRIVATE)
    }
    val state = connectionViewModel.uiState
    val server = state.server
    val mount = server?.devices?.mount
    val camera = server?.devices?.camera
    val gps = server?.devices?.gps
    val mountState = mountViewModel.uiState

    var serverAddress by rememberSaveable(state.serverBaseUrl) {
        mutableStateOf(
            state.serverBaseUrl
                .removePrefix("http://")
                .removePrefix("https://")
                .removeSuffix("/")
                .removeSuffix(":8000")
        )
    }

    val serverReady = server?.status?.lowercase() in setOf("ok", "ready", "online")
    val mountReady = mount?.status?.lowercase() in setOf("ready", "ok", "online")
    val cameraReady = camera?.status?.lowercase() in setOf("ready", "ok", "online")
    val gpsReady = gps?.status?.lowercase() in setOf("fix", "available")
    val timeReady = mountState.timeSyncVerified
    val ready = serverReady && mountReady && cameraReady && gpsReady && timeReady

    fun applyServer() {
        val clean = serverAddress.trim().ifBlank { RESTORED_DEFAULT_SERVER }
        preferences.edit().putString(RESTORED_PREF_SERVER, clean).apply()
        serverAddress = clean
        connectionViewModel.setServerAddress(clean)
        connectionViewModel.connect()
        mountViewModel.refresh(connectionViewModel.uiState.serverBaseUrl)
    }

    LaunchedEffect(Unit) {
        val saved = preferences.getString(RESTORED_PREF_SERVER, null)
            ?.takeIf { it.isNotBlank() }
        if (saved != null) {
            serverAddress = saved
            connectionViewModel.setServerAddress(saved)
        }
        connectionViewModel.connect()
        mountViewModel.refresh(connectionViewModel.uiState.serverBaseUrl)
    }

    RestoredCard("CONNEXION & CONTRÔLES") {
        Text("Adresse du Raspberry Pi / serveur StellarPilot", color = StellarText)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = serverAddress,
            onValueChange = { serverAddress = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Adresse serveur") },
            supportingText = { Text("Ex. 192.168.1.46 ou 10.42.0.1") }
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = ::applyServer,
            enabled = !state.isConnecting && !mountState.isLoading,
            modifier = Modifier.fillMaxWidth(),
            colors = restoredPrimaryColors()
        ) {
            Text("UTILISER CETTE ADRESSE", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(14.dp))
        RestoredStatusLine("Serveur StellarPilot", serverReady, server?.status ?: "indisponible")
        RestoredStatusLine("Monture OnStep", mountReady, mount?.status ?: "indisponible")
        RestoredStatusLine("Caméra", cameraReady, camera?.name ?: camera?.status ?: "indisponible")
        RestoredStatusLine(
            "GPS",
            gpsReady,
            if (gpsReady) {
                "fix • ${gps?.latitude ?: "?"}, ${gps?.longitude ?: "?"}"
            } else {
                gps?.status ?: "indisponible"
            }
        )
        RestoredStatusLine(
            "Heure OnStep",
            timeReady,
            mountState.timeSyncDetail ?: "à vérifier"
        )

        state.error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = StellarRed)
        }
        mountState.error?.let {
            Spacer(Modifier.height(6.dp))
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
            Text("ACTUALISER TOUS LES CONTRÔLES")
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onContinue,
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
            colors = restoredPrimaryColors()
        ) {
            Text("SUITE • HOME MONTURE", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onDirectDarks,
            enabled = cameraReady,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("ALLER DIRECTEMENT AUX DARKS")
        }
    }
}

@Composable
private fun RestoredHomeStep(
    baseUrl: String,
    astrometryViewModel: CameraPreviewViewModel,
    astrometry: CameraPreviewUiState,
    homeViewModel: MountHomeViewModel,
    home: MountHomeUiState,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val solvedNearPole =
        astrometry.solveStatus == "solved" &&
            astrometry.dec != null &&
            abs(astrometry.dec) >= 80.0 &&
            astrometry.mountSyncStatus == "pending_zenith"
    val calibrationComplete =
        astrometry.calibrationStatus == "synced" &&
            astrometry.mountSyncStatus == "synced"

    RestoredCard("HOME MÉCANIQUE → ZONE HAUTE") {
        Text(
            "Placez la monture en HOME/CWD : contrepoids vers le bas et tube parallèle à l'axe polaire, dirigé vers le pôle céleste.",
            color = StellarText
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "StellarPilot résout d'abord le champ près du pôle sans SYNC, initialise ensuite HOME OnStep après confirmation, puis effectue une seconde astrométrie et le SYNC loin du pôle.",
            color = StellarMuted
        )
        Spacer(Modifier.height(10.dp))
        StellarImagePreview(
            imageBytes = astrometry.imageBytes,
            contentDescription = "Astrométrie HOME V0.6.9",
            loadingText = if (astrometry.isLoading) {
                astrometry.calibrationDetail ?: "Capture / astrométrie en cours…"
            } else null,
            emptyText = "Aucune image de calibration",
            showCrosshair = true
        )
        Spacer(Modifier.height(8.dp))
        RestoredValue("Astrométrie", astrometry.solveStatus ?: "non lancée")
        astrometry.dec?.let {
            RestoredValue("DEC résolue", String.format(Locale.FRANCE, "%+.5f°", it))
        }
        RestoredValue("HOME OnStep", if (home.homeSet) "initialisé ✓" else "non initialisé")
        RestoredValue("Calibration haute", astrometry.calibrationStatus ?: "en attente")

        astrometry.calibrationDetail?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = if (calibrationComplete) StellarGreen else StellarOrange)
        }
        home.message?.let { Text(it, color = StellarGreen) }
        home.error?.let { Text(it, color = StellarRed) }
        astrometry.error?.let { Text(it, color = StellarRed) }

        Spacer(Modifier.height(10.dp))
        if (astrometry.mountSyncStatus != "pending_zenith" && !calibrationComplete) {
            Button(
                onClick = {
                    astrometryViewModel.load(
                        serverBaseUrl = baseUrl,
                        exposureSeconds = RESTORED_PREPARATION_EXPOSURE_SECONDS,
                        minimumStars = RESTORED_PREPARATION_MIN_STARS
                    )
                },
                enabled = !astrometry.isLoading && !home.isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = restoredPrimaryColors()
            ) {
                Text("1 • CAPTURER & RÉSOUDRE PRÈS DU PÔLE • 4 s")
            }
        }

        if (astrometry.mountSyncStatus == "pending_zenith" && !home.homeSet) {
            Spacer(Modifier.height(8.dp))
            Text(
                if (solvedNearPole) {
                    "Champ polaire validé ✓. Confirmez uniquement si la monture est réellement en HOME mécanique."
                } else {
                    "Champ trop éloigné du pôle : initialisation HOME refusée."
                },
                color = if (solvedNearPole) StellarOrange else StellarRed,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    astrometry.dec?.let { homeViewModel.setHome(baseUrl, it) }
                },
                enabled = solvedNearPole && !home.isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = restoredPrimaryColors()
            ) {
                Text("2 • JE CONFIRME HOME MÉCANIQUE • INITIALISER ONSTEP")
            }
        }

        if (home.homeSet && !calibrationComplete) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    astrometryViewModel.calibrateNearZenith(
                        serverBaseUrl = baseUrl,
                        exposureSeconds = RESTORED_PREPARATION_EXPOSURE_SECONDS,
                        minimumStars = RESTORED_PREPARATION_MIN_STARS
                    )
                },
                enabled = !astrometry.isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = restoredPrimaryColors()
            ) {
                Text("3 • GOTO ZONE HAUTE • ASTROMÉTRIE • SYNC")
            }
        }

        if (calibrationComplete) {
            Spacer(Modifier.height(10.dp))
            Text("CALIBRATION MONTURE VALIDÉE ✓", color = StellarGreen, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                colors = restoredPrimaryColors()
            ) {
                Text("SUITE • BAHTINOV", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onBack,
            enabled = !astrometry.isLoading && !home.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("RETOUR") }
    }
}

@Composable
private fun RestoredBahtinovStep(
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
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    RestoredCard("MISE AU POINT BAHTINOV") {
        Text(
            "Choisissez une étoile brillante, centrez-la, installez le masque de Bahtinov puis ajustez le focuser jusqu'à validation de l'optimum sur deux poses consécutives.",
            color = StellarText
        )
        Spacer(Modifier.height(10.dp))

        if (skyLoading) Text("Calcul des étoiles visibles…", color = StellarMuted)

        stars.forEach { star ->
            val selected = selectedStarId == star.id
            val label = String.format(
                Locale.FRANCE,
                "%s • mag %.2f • alt. %.0f°",
                star.name,
                star.magnitude,
                star.altitudeDeg
            )
            if (selected) {
                Button(
                    onClick = { onSelectStar(star) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = restoredPrimaryColors()
                ) { Text("✓ $label") }
            } else {
                OutlinedButton(
                    onClick = { onSelectStar(star) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(label) }
            }
            Spacer(Modifier.height(4.dp))
        }

        if (stars.isEmpty() && !skyLoading) {
            Text("Aucune étoile utilisable reçue.", color = StellarRed)
        }
        skyError?.let { Text(it, color = StellarRed) }

        OutlinedButton(
            onClick = onRefreshStars,
            modifier = Modifier.fillMaxWidth(),
            enabled = !skyLoading
        ) { Text("ACTUALISER LES ÉTOILES") }

        if (selectedStar != null) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onGotoAndCenter,
                enabled = !centeringState.isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = restoredPrimaryColors()
            ) {
                Text(
                    if (centeringState.centered) "${selectedStar.name} CENTRÉE ✓"
                    else "GOTO + RECENTRAGE • ${selectedStar.name}"
                )
            }
        }

        if (centeringState.imageBytes != null || centeringState.isLoading || centeringState.manualRequired) {
            Spacer(Modifier.height(10.dp))
            StellarImagePreview(
                imageBytes = centeringState.imageBytes,
                contentDescription = "Centrage étoile Bahtinov",
                loadingText = if (centeringState.isLoading) "Pose 4 s / recentrage…" else null,
                showCrosshair = true
            )
            RestoredValue("Centrage", centeringState.status ?: "en cours")
            centeringState.errorArcsec?.let {
                RestoredValue("Erreur", String.format(Locale.FRANCE, "%.1f arcsec", it))
            }
        }

        centeringState.message?.let {
            Text(it, color = if (centeringState.centered) StellarGreen else StellarMuted)
        }
        centeringState.error?.let { Text(it, color = StellarRed) }

        if (centeringState.manualRequired && !centeringState.centered) {
            Spacer(Modifier.height(10.dp))
            Text("RECENTRAGE MANUEL", color = StellarOrange, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("N", "S", "E", "O").forEach { direction ->
                    OutlinedButton(
                        onClick = { onManualNudge(direction) },
                        enabled = !centeringState.isLoading,
                        modifier = Modifier.weight(1f)
                    ) { Text(direction) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onVerifyCentering,
                enabled = !centeringState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("VÉRIFIER LE CENTRAGE • 4 s") }
        }

        if (centeringState.centered && !maskInstalled) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Étoile centrée. Installez maintenant le masque de Bahtinov.",
                color = StellarOrange,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onMaskInstalled,
                modifier = Modifier.fillMaxWidth(),
                colors = restoredPrimaryColors()
            ) { Text("MASQUE BAHTINOV INSTALLÉ") }
        }

        if (centeringState.centered && maskInstalled && !maskRemoved) {
            Spacer(Modifier.height(12.dp))
            StellarImagePreview(
                imageBytes = bahtinovState.imageBytes,
                contentDescription = "Motif Bahtinov",
                loadingText = if (bahtinovState.isLoading) "Pose Bahtinov 4 s…" else null,
                showCrosshair = true
            )
            RestoredValue(
                "Score focus",
                bahtinovState.focusScore?.let { "$it/100 • ${bahtinovState.focusLabel ?: ""}" }
                    ?: "à mesurer"
            )
            RestoredValue(
                "Écart optimum",
                bahtinovState.focusErrorPx?.let {
                    String.format(Locale.FRANCE, "%+.2f px", it)
                } ?: "—"
            )
            RestoredValue("Confirmation optimum", "${bahtinovState.optimumStreak}/2")
            bahtinovState.focusInstruction?.let { Text(it, color = StellarOrange) }
            bahtinovState.message?.let {
                Text(
                    it,
                    color = if (bahtinovState.focusValidated) StellarGreen else StellarOrange,
                    fontWeight = FontWeight.Bold
                )
            }
            bahtinovState.error?.let { Text(it, color = StellarRed) }
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onFocusCapture,
                enabled = !bahtinovState.isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = restoredPrimaryColors()
            ) {
                Text(if (bahtinovState.imageBytes == null) "MESURER LA MISE AU POINT • 4 s" else "ENCORE • 4 s")
            }

            if (bahtinovState.focusValidated) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onMaskRemoved,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("OPTIMUM VALIDÉ ✓ • MASQUE RETIRÉ") }
            }
        }

        if (maskRemoved) {
            Spacer(Modifier.height(8.dp))
            Text("Masque retiré ✓ • prêt pour les darks", color = StellarGreen)
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                colors = restoredPrimaryColors()
            ) { Text("SUITE • 10 DARKS", fontWeight = FontWeight.Bold) }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("PASSER LE BAHTINOV POUR CETTE SESSION")
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("RETOUR")
        }
    }
}

@Composable
private fun RestoredDarkStep(
    dark: DarkCalibrationUiState,
    telescopeCapped: Boolean,
    darkOnly: Boolean,
    darkViewModel: DarkCalibrationViewModel,
    baseUrl: String,
    onCapped: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    RestoredCard("DARKS • 10 IMAGES") {
        Text(
            "Le temps de pose des darks sera repris par l'écran Capture. Le Master Dark doit avoir la même exposition que les LIGHT.",
            color = StellarText
        )
        Spacer(Modifier.height(8.dp))
        RestoredValue("Exposition", String.format(Locale.FRANCE, "%.1f s", dark.exposureSeconds))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { darkViewModel.changeExposure(-0.5) },
                enabled = dark.sessionId == null && !dark.isLoading,
                modifier = Modifier.weight(1f)
            ) { Text("− 0,5 s") }
            OutlinedButton(
                onClick = { darkViewModel.changeExposure(0.5) },
                enabled = dark.sessionId == null && !dark.isLoading,
                modifier = Modifier.weight(1f)
            ) { Text("+ 0,5 s") }
        }

        Spacer(Modifier.height(10.dp))
        if (!telescopeCapped) {
            Text("Placez le bouchon opaque sur le télescope.", color = StellarOrange, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Button(onClick = onCapped, modifier = Modifier.fillMaxWidth(), colors = restoredPrimaryColors()) {
                Text("BOUCHON POSÉ")
            }
        } else {
            RestoredValue("Darks", "${dark.capturedCount}/${dark.requestedCount}")
            RestoredValue("Valides", dark.validCount.toString())
            dark.temperatureC?.let {
                RestoredValue("Température", String.format(Locale.FRANCE, "%.1f °C", it))
            }

            if (dark.sessionId == null) {
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { darkViewModel.start(baseUrl) },
                    enabled = !dark.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = restoredPrimaryColors()
                ) { Text("DÉMARRER 10 DARKS", fontWeight = FontWeight.Bold) }
            } else if (!dark.complete) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = if (dark.requestedCount > 0) {
                        dark.capturedCount.toFloat() / dark.requestedCount.toFloat()
                    } else 0f,
                    modifier = Modifier.fillMaxWidth(),
                    color = StellarOrange
                )
            }

            dark.message?.let { Text(it, color = if (dark.complete) StellarGreen else StellarMuted) }
            dark.error?.let { Text(it, color = StellarRed) }

            if (dark.complete) {
                Spacer(Modifier.height(8.dp))
                Text("MASTER DARK CRÉÉ ✓", color = StellarGreen, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth(), colors = restoredPrimaryColors()) {
                    Text("SUITE", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, enabled = !dark.isLoading, modifier = Modifier.fillMaxWidth()) {
            Text(if (darkOnly) "RETOUR CONNEXION" else "RETOUR BAHTINOV")
        }
    }
}

@Composable
private fun RestoredCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StellarSurface),
        border = BorderStroke(1.dp, StellarBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, color = StellarOrange, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun RestoredStatusLine(label: String, ok: Boolean, detail: String? = null) {
    Text(
        buildString {
            append(if (ok) "✓ " else "✗ ")
            append(label)
            if (!detail.isNullOrBlank()) append(" • $detail")
        },
        color = if (ok) StellarGreen else StellarRed,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun RestoredValue(label: String, value: String) {
    Text("$label : $value", color = StellarMuted)
}

@Composable
private fun restoredPrimaryColors() = ButtonDefaults.buttonColors(
    containerColor = StellarOrange,
    contentColor = StellarBackground
)
