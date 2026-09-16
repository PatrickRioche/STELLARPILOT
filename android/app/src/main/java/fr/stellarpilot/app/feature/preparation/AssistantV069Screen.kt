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
import kotlin.math.abs


private const val PREPARATION_EXPOSURE_SECONDS = 4.0
private const val PREPARATION_MIN_STARS = 30


@Composable
fun AssistantV069Screen(
    onOpenSky: () -> Unit,
    connectionViewModel: ConnectionViewModel = viewModel()
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var telescopeCapped by rememberSaveable { mutableStateOf(false) }
    var darkOnly by rememberSaveable { mutableStateOf(false) }

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
                0 -> "1/4 • Connexion, heure & GPS"
                1 -> "2/4 • HOME monture & calibration astrométrique"
                2 -> "3/4 • Master Dark"
                else -> "4/4 • Prêt à observer"
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
            0 -> V069Card("CONNEXION") {
                Text(
                    "StellarPilot vérifie la monture puis synchronise TIME_UTC OnStep avant tout mouvement.",
                    color = StellarText
                )
                Spacer(Modifier.height(10.dp))
                V069Value("Serveur", baseUrl)
                V069Value(
                    "Heure OnStep",
                    if (mount.timeSyncVerified) "vérifiée ✓" else "à vérifier"
                )
                mount.timeSyncDetail?.let { V069Value("Détail", it) }

                mount.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = StellarRed)
                }

                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        connectionViewModel.connect()
                        mountViewModel.refresh(baseUrl)
                    },
                    enabled = !mount.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ACTUALISER & SYNCHRONISER L'HEURE")
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        darkOnly = false
                        step = 1
                    },
                    enabled = mount.timeSyncVerified,
                    modifier = Modifier.fillMaxWidth(),
                    colors = v069PrimaryColors()
                ) {
                    Text("SUITE • HOME MONTURE", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        darkOnly = true
                        telescopeCapped = false
                        darkViewModel.reset()
                        step = 2
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ALLER DIRECTEMENT AUX DARKS")
                }
            }

            1 -> V069HomeStep(
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
                    telescopeCapped = false
                    darkViewModel.reset()
                    step = 2
                }
            )

            2 -> V069Card("DARKS • 10 IMAGES") {
                Text(
                    "Choisissez le temps de pose qui sera aussi repris par l'écran Capture. Le Master Dark doit avoir exactement la même exposition que les LIGHT.",
                    color = StellarText
                )
                Spacer(Modifier.height(10.dp))
                V069Value(
                    "Exposition",
                    String.format(Locale.FRANCE, "%.1f s", dark.exposureSeconds)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { darkViewModel.changeExposure(-0.5) },
                        enabled = dark.sessionId == null && !dark.isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("− 0,5 s")
                    }
                    OutlinedButton(
                        onClick = { darkViewModel.changeExposure(0.5) },
                        enabled = dark.sessionId == null && !dark.isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+ 0,5 s")
                    }
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
                        onClick = { telescopeCapped = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = v069PrimaryColors()
                    ) {
                        Text("BOUCHON POSÉ")
                    }
                } else {
                    V069Value("Darks", "${dark.capturedCount}/${dark.requestedCount}")
                    V069Value("Valides", dark.validCount.toString())
                    dark.temperatureC?.let {
                        V069Value(
                            "Température",
                            String.format(Locale.FRANCE, "%.1f °C", it)
                        )
                    }

                    if (dark.sessionId == null) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { darkViewModel.start(baseUrl) },
                            enabled = !dark.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                            colors = v069PrimaryColors()
                        ) {
                            Text("DÉMARRER 10 DARKS", fontWeight = FontWeight.Bold)
                        }
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
                            "MASTER DARK CRÉÉ ✓ • retirez le bouchon avant de poursuivre.",
                            color = StellarGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { step = 3 },
                            modifier = Modifier.fillMaxWidth(),
                            colors = v069PrimaryColors()
                        ) {
                            Text("SUITE", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { step = if (darkOnly) 0 else 1 },
                    enabled = !dark.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("RETOUR")
                }
            }

            else -> V069Card("PRÊT À OBSERVER") {
                Text(
                    "Retirez le bouchon. La monture est calibrée et le Master Dark est disponible pour le stacking.",
                    color = StellarGreen,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Dans Capture : exposition réglable par pas de 0,5 s avant création de la session, stacking continu, puis bouton d'enregistrement dans Galeries après l'arrêt.",
                    color = StellarText
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onOpenSky,
                    modifier = Modifier.fillMaxWidth(),
                    colors = v069PrimaryColors()
                ) {
                    Text("OUVRIR LE CIEL", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}


@Composable
private fun V069HomeStep(
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

    V069Card("HOME MÉCANIQUE → ZONE HAUTE") {
        Text(
            "1. Placez la monture en HOME/CWD : contrepoids vers le bas, tube parallèle à l'axe polaire et dirigé vers le pôle céleste. Polaris doit seulement être dans le champ ; elle n'a pas besoin d'être centrée.",
            color = StellarText
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "2. StellarPilot fait une première astrométrie près du pôle sans envoyer de SYNC. Après votre confirmation, il réinitialise HOME OnStep. Ensuite il pointe automatiquement une zone haute sûre, refait une astrométrie et effectue le SYNC loin du pôle.",
            color = StellarMuted
        )
        Spacer(Modifier.height(12.dp))

        StellarImagePreview(
            imageBytes = astrometry.imageBytes,
            contentDescription = "Astrométrie de préparation V0.6.9",
            loadingText = if (astrometry.isLoading) {
                astrometry.calibrationDetail ?: "Capture / astrométrie en cours…"
            } else null,
            emptyText = "Aucune image de calibration",
            showCrosshair = true
        )

        Spacer(Modifier.height(10.dp))
        V069Value("Astrométrie", astrometry.solveStatus ?: "non lancée")
        astrometry.dec?.let {
            V069Value("DEC résolue", String.format(Locale.FRANCE, "%+.5f°", it))
        }
        V069Value("HOME OnStep", if (home.homeSet) "initialisé ✓" else "non initialisé")
        V069Value("Calibration haute", astrometry.calibrationStatus ?: "en attente")
        astrometry.calibrationTargetAltitudeDeg?.let {
            V069Value(
                "Altitude cible calibration",
                String.format(Locale.FRANCE, "%.1f°", it)
            )
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
                        exposureSeconds = PREPARATION_EXPOSURE_SECONDS,
                        minimumStars = PREPARATION_MIN_STARS
                    )
                },
                enabled = !astrometry.isLoading && !home.isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = v069PrimaryColors()
            ) {
                Text("1 • CAPTURER & RÉSOUDRE PRÈS DU PÔLE • 4 s")
            }
        }

        if (astrometry.mountSyncStatus == "pending_zenith" && !home.homeSet) {
            Spacer(Modifier.height(8.dp))
            Text(
                if (solvedNearPole) {
                    "Champ polaire validé ✓. Vérifiez une dernière fois la position mécanique HOME avant de confirmer."
                } else {
                    "Le champ n'est pas suffisamment proche du pôle : HOME ne sera pas réinitialisé."
                },
                color = if (solvedNearPole) StellarOrange else StellarRed,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    astrometry.dec?.let { solvedDec ->
                        homeViewModel.setHome(baseUrl, solvedDec)
                    }
                },
                enabled = solvedNearPole && !home.isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = v069PrimaryColors()
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
                        exposureSeconds = PREPARATION_EXPOSURE_SECONDS,
                        minimumStars = PREPARATION_MIN_STARS
                    )
                },
                enabled = !astrometry.isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = v069PrimaryColors()
            ) {
                Text("3 • GOTO ZONE HAUTE • ASTROMÉTRIE • SYNC")
            }
        }

        if (calibrationComplete) {
            Spacer(Modifier.height(10.dp))
            Text(
                "CALIBRATION MONTURE VALIDÉE ✓",
                color = StellarGreen,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                colors = v069PrimaryColors()
            ) {
                Text("SUITE • 10 DARKS", fontWeight = FontWeight.Bold)
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
private fun V069Card(
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
            Text(title, color = StellarOrange, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}


@Composable
private fun V069Value(label: String, value: String) {
    Text("$label : $value", color = StellarMuted)
}


@Composable
private fun v069PrimaryColors() =
    ButtonDefaults.buttonColors(
        containerColor = StellarOrange,
        contentColor = StellarBackground
    )
