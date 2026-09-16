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
import kotlin.math.abs

private const val FOUR_DEFAULT_SERVER = "10.42.0.1"
private const val FOUR_PREFS_NAME = "stellarpilot_connection"
private const val FOUR_PREF_SERVER = "server_base_url"
private const val FOUR_ASTROMETRY_EXPOSURE_S = 4.0
private const val FOUR_ASTROMETRY_MIN_STARS = 30

@Composable
fun AssistantV069FourStepScreen(
    onOpenSky: () -> Unit,
    connectionViewModel: ConnectionViewModel = viewModel()
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var telescopeCapped by rememberSaveable { mutableStateOf(false) }
    var directDarks by rememberSaveable { mutableStateOf(false) }

    val connection = connectionViewModel.uiState
    val baseUrl = connection.serverBaseUrl
    val mountViewModel: MountDiagnosticsViewModel = viewModel()
    val mount = mountViewModel.uiState
    val astrometryViewModel: CameraPreviewViewModel = viewModel()
    val astrometry = astrometryViewModel.uiState
    val homeViewModel: MountHomeViewModel = viewModel()
    val home = homeViewModel.uiState
    val darkViewModel: DarkCalibrationViewModel = viewModel()
    val dark = darkViewModel.uiState

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
        Text(
            "ASSISTANT STELLARPILOT • V0.6.9",
            color = StellarOrange,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            when (step) {
                0 -> "1/4 • Connexion"
                1 -> "2/4 • Pointage"
                2 -> "3/4 • Astrométrie"
                else -> "4/4 • Darks"
            },
            color = StellarText,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = (step + 1) / 4f,
            modifier = Modifier.fillMaxWidth(),
            color = StellarOrange
        )
        Spacer(Modifier.height(14.dp))

        when (step) {
            0 -> FourConnectionStep(
                connectionViewModel = connectionViewModel,
                mountViewModel = mountViewModel,
                onContinue = {
                    directDarks = false
                    step = 1
                },
                onDirectDarks = {
                    directDarks = true
                    telescopeCapped = false
                    darkViewModel.reset()
                    step = 3
                }
            )

            1 -> FourPointingStep(
                connectionViewModel = connectionViewModel,
                onBack = { step = 0 },
                onContinue = { step = 2 }
            )

            2 -> FourAstrometryStep(
                baseUrl = baseUrl,
                astrometryViewModel = astrometryViewModel,
                astrometry = astrometry,
                homeViewModel = homeViewModel,
                home = home,
                onBack = {
                    homeViewModel.reset()
                    step = 1
                },
                onContinue = {
                    directDarks = false
                    telescopeCapped = false
                    darkViewModel.reset()
                    step = 3
                }
            )

            else -> FourDarkStep(
                dark = dark,
                telescopeCapped = telescopeCapped,
                darkViewModel = darkViewModel,
                baseUrl = baseUrl,
                onCapped = { telescopeCapped = true },
                onBack = { step = if (directDarks) 0 else 2 },
                onOpenSky = onOpenSky
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FourConnectionStep(
    connectionViewModel: ConnectionViewModel,
    mountViewModel: MountDiagnosticsViewModel,
    onContinue: () -> Unit,
    onDirectDarks: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences(FOUR_PREFS_NAME, Context.MODE_PRIVATE)
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
    val mountReady = mount?.status?.lowercase() in setOf("ready", "ok", "online", "tracking", "idle")
    val cameraReady = camera?.status?.lowercase() in setOf("ready", "ok", "online")
    val gpsReady = gps?.status?.lowercase() in setOf("fix", "available")
    val timeReady = mountState.timeSyncVerified
    val ready = serverReady && mountReady && cameraReady && gpsReady && timeReady

    fun applyServer() {
        val clean = serverAddress.trim().ifBlank { FOUR_DEFAULT_SERVER }
        preferences.edit().putString(FOUR_PREF_SERVER, clean).apply()
        serverAddress = clean
        connectionViewModel.setServerAddress(clean)
        connectionViewModel.connect()
        mountViewModel.refresh(connectionViewModel.uiState.serverBaseUrl)
    }

    LaunchedEffect(Unit) {
        val saved = preferences.getString(FOUR_PREF_SERVER, null)?.takeIf { it.isNotBlank() }
        if (saved != null) {
            serverAddress = saved
            connectionViewModel.setServerAddress(saved)
        }
        connectionViewModel.connect()
        mountViewModel.refresh(connectionViewModel.uiState.serverBaseUrl)
    }

    FourCard("CONNEXION & CONTRÔLES") {
        OutlinedTextField(
            value = serverAddress,
            onValueChange = { serverAddress = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Adresse du Raspberry Pi") },
            supportingText = { Text("Ex. 192.168.1.46 ou 10.42.0.1") }
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = ::applyServer,
            enabled = !state.isConnecting && !mountState.isLoading,
            modifier = Modifier.fillMaxWidth(),
            colors = fourPrimaryColors()
        ) {
            Text("UTILISER CETTE ADRESSE", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(14.dp))
        FourStatus("Serveur StellarPilot", serverReady, server?.status ?: "indisponible")
        FourStatus("Monture OnStep", mountReady, mount?.status ?: "indisponible")
        FourStatus("Caméra", cameraReady, camera?.name ?: camera?.status ?: "indisponible")
        FourStatus(
            "GPS",
            gpsReady,
            if (gpsReady) "fix • ${gps?.latitude ?: "?"}, ${gps?.longitude ?: "?"}"
            else gps?.status ?: "indisponible"
        )
        FourStatus("Heure OnStep", timeReady, mountState.timeSyncDetail ?: "à vérifier")

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
            colors = fourPrimaryColors()
        ) {
            Text("SUITE • POINTAGE", fontWeight = FontWeight.Bold)
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
private fun FourPointingStep(
    connectionViewModel: ConnectionViewModel,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val server = connectionViewModel.uiState.server
    val latitude = server?.session?.latitude ?: server?.devices?.gps?.latitude
    val mountFamily = server?.session?.mountFamily ?: server?.devices?.mount?.family

    FourCard("POINTAGE INITIAL") {
        Text(
            "Placez manuellement la monture en position HOME/CWD : contrepoids vers le bas, tube parallèle à l'axe polaire et dirigé vers le pôle céleste.",
            color = StellarText
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Polaris doit seulement être dans le champ : ne cherchez pas à la centrer. L'étape suivante utilisera l'astrométrie pour initialiser HOME OnStep puis calibrer le pointage loin du pôle.",
            color = StellarMuted
        )
        Spacer(Modifier.height(10.dp))
        FourValue("Type", mountFamily?.uppercase() ?: "EQ")
        latitude?.let { FourValue("Latitude GPS", String.format(Locale.FRANCE, "%+.5f°", it)) }

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            colors = fourPrimaryColors()
        ) {
            Text("POSITION HOME CONFIRMÉE • SUITE ASTROMÉTRIE", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("RETOUR")
        }
    }
}

@Composable
private fun FourAstrometryStep(
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

    FourCard("ASTROMÉTRIE & CALIBRATION MONTURE") {
        Text(
            "1. Résolution près du pôle sans SYNC. 2. Confirmation et initialisation HOME OnStep. 3. GOTO vers une zone haute sûre, seconde résolution et SYNC loin du pôle.",
            color = StellarText
        )
        Spacer(Modifier.height(12.dp))

        StellarImagePreview(
            imageBytes = astrometry.imageBytes,
            contentDescription = "Astrométrie de préparation V0.6.9",
            loadingText = if (astrometry.isLoading) {
                astrometry.calibrationDetail ?: "Capture / astrométrie en cours…"
            } else null,
            emptyText = "Aucune image astrométrique",
            showCrosshair = true
        )

        Spacer(Modifier.height(10.dp))
        FourValue("Solve", astrometry.solveStatus ?: "non lancé")
        astrometry.dec?.let {
            FourValue("DEC résolue", String.format(Locale.FRANCE, "%+.5f°", it))
        }
        FourValue("HOME OnStep", if (home.homeSet) "initialisé ✓" else "en attente")
        FourValue("Calibration haute", astrometry.calibrationStatus ?: "en attente")
        astrometry.calibrationTargetAltitudeDeg?.let {
            FourValue("Altitude cible", String.format(Locale.FRANCE, "%.1f°", it))
        }

        astrometry.solveDetail?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = StellarMuted)
        }
        astrometry.calibrationDetail?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = if (calibrationComplete) StellarGreen else StellarOrange)
        }
        home.message?.let { Text(it, color = StellarGreen) }
        home.error?.let { Text(it, color = StellarRed) }
        astrometry.error?.let { Text(it, color = StellarRed) }

        Spacer(Modifier.height(12.dp))
        if (astrometry.mountSyncStatus != "pending_zenith" && !calibrationComplete) {
            Button(
                onClick = {
                    astrometryViewModel.load(
                        serverBaseUrl = baseUrl,
                        exposureSeconds = FOUR_ASTROMETRY_EXPOSURE_S,
                        minimumStars = FOUR_ASTROMETRY_MIN_STARS
                    )
                },
                enabled = !astrometry.isLoading && !home.isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = fourPrimaryColors()
            ) {
                Text("1 • CAPTURER & RÉSOUDRE PRÈS DU PÔLE • 4 s")
            }
        }

        if (astrometry.mountSyncStatus == "pending_zenith" && !home.homeSet) {
            Spacer(Modifier.height(8.dp))
            Text(
                if (solvedNearPole) "Champ polaire validé ✓ • confirmez HOME mécanique."
                else "Champ insuffisamment proche du pôle : HOME reste bloqué.",
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
                colors = fourPrimaryColors()
            ) {
                Text("2 • INITIALISER HOME ONSTEP")
            }
        }

        if (home.homeSet && !calibrationComplete) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    astrometryViewModel.calibrateNearZenith(
                        serverBaseUrl = baseUrl,
                        exposureSeconds = FOUR_ASTROMETRY_EXPOSURE_S,
                        minimumStars = FOUR_ASTROMETRY_MIN_STARS
                    )
                },
                enabled = !astrometry.isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = fourPrimaryColors()
            ) {
                Text("3 • ZONE HAUTE • ASTROMÉTRIE • SYNC")
            }
        }

        if (calibrationComplete) {
            Spacer(Modifier.height(10.dp))
            Text("ASTROMÉTRIE & SYNC VALIDÉS ✓", color = StellarGreen, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                colors = fourPrimaryColors()
            ) {
                Text("SUITE • DARKS", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onBack,
            enabled = !astrometry.isLoading && !home.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("RETOUR")
        }
    }
}

@Composable
private fun FourDarkStep(
    dark: DarkCalibrationUiState,
    telescopeCapped: Boolean,
    darkViewModel: DarkCalibrationViewModel,
    baseUrl: String,
    onCapped: () -> Unit,
    onBack: () -> Unit,
    onOpenSky: () -> Unit
) {
    FourCard("DARKS • 10 IMAGES") {
        Text(
            "Le temps de pose choisi ici est repris par Capture. Le Master Dark doit avoir exactement la même exposition que les LIGHT.",
            color = StellarText
        )
        Spacer(Modifier.height(10.dp))
        FourValue("Exposition", String.format(Locale.FRANCE, "%.1f s", dark.exposureSeconds))
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

        Spacer(Modifier.height(12.dp))
        if (!telescopeCapped) {
            Text(
                "Placez le bouchon opaque sur le télescope avant de lancer la série.",
                color = StellarOrange,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onCapped,
                modifier = Modifier.fillMaxWidth(),
                colors = fourPrimaryColors()
            ) { Text("BOUCHON POSÉ") }
        } else {
            FourValue("Darks", "${dark.capturedCount}/${dark.requestedCount}")
            FourValue("Valides", dark.validCount.toString())
            dark.temperatureC?.let {
                FourValue("Température", String.format(Locale.FRANCE, "%.1f °C", it))
            }

            if (dark.sessionId == null) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { darkViewModel.start(baseUrl) },
                    enabled = !dark.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fourPrimaryColors()
                ) { Text("DÉMARRER 10 DARKS", fontWeight = FontWeight.Bold) }
            } else if (!dark.complete) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = if (dark.requestedCount > 0) {
                        dark.capturedCount.toFloat() / dark.requestedCount.toFloat()
                    } else 0f,
                    modifier = Modifier.fillMaxWidth(),
                    color = StellarOrange
                )
            }

            dark.message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = if (dark.complete) StellarGreen else StellarMuted)
            }
            dark.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = StellarRed)
            }

            if (dark.complete) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "MASTER DARK CRÉÉ ✓ • retirez le bouchon.",
                    color = StellarGreen,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onOpenSky,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fourPrimaryColors()
                ) {
                    Text("TERMINER • OUVRIR LE CIEL", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onBack,
            enabled = !dark.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("RETOUR") }
    }
}

@Composable
private fun FourCard(title: String, content: @Composable () -> Unit) {
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
private fun FourStatus(label: String, ok: Boolean, detail: String) {
    Text(
        text = (if (ok) "● " else "● ") + "$label • $detail",
        color = if (ok) StellarGreen else StellarRed,
        fontWeight = if (ok) FontWeight.SemiBold else FontWeight.Normal
    )
}

@Composable
private fun FourValue(label: String, value: String) {
    Text("$label : $value", color = StellarMuted)
}

@Composable
private fun fourPrimaryColors() = ButtonDefaults.buttonColors(
    containerColor = StellarOrange,
    contentColor = StellarBackground
)
