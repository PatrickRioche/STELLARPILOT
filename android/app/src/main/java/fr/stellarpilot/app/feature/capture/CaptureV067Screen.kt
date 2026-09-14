package fr.stellarpilot.app.feature.capture

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.stellarpilot.app.ui.components.AstrometryQualityIndicator
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
fun CaptureV067Screen(
    serverBaseUrl: String,
    viewModel: CaptureV067ViewModel = viewModel()
) {
    val state = viewModel.uiState
    val target = state.target
    val session = state.session
    val centering = session?.centering

    LaunchedEffect(Unit) {
        viewModel.loadSelectedTarget()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = StellarBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Text(
                "CAPTURE • V0.6.7",
                color = StellarOrange,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "Capture et astrométrie manuelles",
                color = StellarText,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "Aucune astrométrie n'est lancée automatiquement après une capture.",
                color = StellarMuted
            )

            Spacer(Modifier.height(14.dp))
            V067Card("CIBLE CHOISIE DANS CIEL") {
                if (target == null) {
                    Text(
                        "Aucune cible sélectionnée. Choisissez d'abord un objet dans Ciel.",
                        color = StellarRed
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = viewModel::loadSelectedTarget,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("RELIRE LA CIBLE")
                    }
                } else {
                    Text(
                        target.name,
                        color = StellarText,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    target.reference
                        ?.takeIf { it.isNotBlank() }
                        ?.let { Text(it, color = StellarMuted) }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        String.format(
                            Locale.FRANCE,
                            "AD %.5f h • DEC %+.5f°",
                            target.raHours,
                            target.decDeg
                        ),
                        color = StellarMuted
                    )
                    Text(
                        "Suivi ${target.trackingMode}",
                        color = StellarGreen
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            V067Card("1 • CAPTURE DE CONTRÔLE") {
                Text(
                    "Pose fixe : 4,0 s",
                    color = StellarText,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "La capture affiche l'image et sa qualité. Elle ne lance pas astrometry.net.",
                    color = StellarMuted
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { viewModel.captureFourSeconds(serverBaseUrl) },
                    enabled = target != null && !state.isCapturing && !state.isSolving && !state.bahtinovIsLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = v067PrimaryColors()
                ) {
                    Text(
                        if (state.imageBytes == null) "CAPTURER • 4 s" else "NOUVELLE CAPTURE • 4 s",
                        fontWeight = FontWeight.Bold
                    )
                }

                if (state.isCapturing) {
                    Spacer(Modifier.height(10.dp))
                    CircularProgressIndicator(color = StellarOrange)
                    Text("Capture en cours…", color = StellarOrange)
                }

                session?.centeringQuality?.let { quality ->
                    Spacer(Modifier.height(12.dp))
                    AstrometryQualityIndicator(
                        score = quality.score,
                        label = quality.label,
                        starCount = quality.starCount,
                        saturatedPercent = quality.saturatedPercent,
                        classification = quality.classification
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            V067Card("2 • ASTROMÉTRIE À LA DEMANDE") {
                val statusText = when (centering?.status) {
                    "captured" -> "Image prête • astrométrie non lancée"
                    "centered" -> "Cible centrée ✓"
                    "correction_required" -> "Correction nécessaire"
                    "unsolved" -> "Astrométrie non résolue"
                    "cancelled" -> "Astrométrie arrêtée • image conservée"
                    else -> "Faites d'abord une capture de 4 s"
                }
                Text(
                    statusText,
                    color = when (centering?.status) {
                        "centered" -> StellarGreen
                        "unsolved" -> StellarRed
                        "correction_required", "captured", "cancelled" -> StellarOrange
                        else -> StellarMuted
                    },
                    fontWeight = FontWeight.Bold
                )

                centering?.errorArcsec?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        String.format(Locale.FRANCE, "Erreur de centrage : %.1f″", it),
                        color = StellarText
                    )
                }

                if (centering?.status == "correction_required") {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "V0.6.7 : aucune correction automatique. Repositionnez si nécessaire puis refaites une capture de 4 s.",
                        color = StellarMuted
                    )
                }

                Spacer(Modifier.height(10.dp))
                if (state.isSolving) {
                    Button(
                        onClick = { viewModel.stopAstrometry(serverBaseUrl) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StellarRed,
                            contentColor = StellarText
                        )
                    ) {
                        Text("ARRÊTER L'ASTROMÉTRIE", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { viewModel.launchAstrometry(serverBaseUrl) },
                        enabled = centering?.status in setOf("captured", "unsolved", "cancelled") && !state.isCapturing,
                        modifier = Modifier.fillMaxWidth(),
                        colors = v067PrimaryColors()
                    ) {
                        Text("LANCER L'ASTROMÉTRIE", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            V067Card("3 • MISE AU POINT BAHTINOV") {
                Text(
                    "Pointez une étoile depuis Ciel. Placez le masque seulement quand vous êtes prêt à contrôler la mise au point.",
                    color = StellarMuted
                )
                Spacer(Modifier.height(10.dp))

                if (!state.bahtinovMaskInstalled) {
                    Button(
                        onClick = viewModel::installBahtinovMask,
                        enabled = target != null && !state.isCapturing && !state.isSolving,
                        modifier = Modifier.fillMaxWidth(),
                        colors = v067PrimaryColors()
                    ) {
                        Text("MASQUE BAHTINOV INSTALLÉ", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        "Masque installé ✓",
                        color = StellarGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.captureBahtinov(serverBaseUrl) },
                        enabled = !state.bahtinovIsLoading && !state.isCapturing && !state.isSolving,
                        modifier = Modifier.fillMaxWidth(),
                        colors = v067PrimaryColors()
                    ) {
                        Text(
                            if (state.bahtinovImageBytes == null) {
                                "MESURER LA MISE AU POINT • 4 s"
                            } else {
                                "ENCORE • 4 s"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (state.bahtinovIsLoading) {
                        Spacer(Modifier.height(8.dp))
                        CircularProgressIndicator(color = StellarOrange)
                    }

                    state.bahtinovFocusScore?.let {
                        Spacer(Modifier.height(10.dp))
                        V067Value("Score focus", "$it / 100")
                    }
                    state.bahtinovFocusLabel?.let {
                        V067Value("État", it)
                    }
                    state.bahtinovFocusErrorPx?.let {
                        V067Value(
                            "Écart optimum",
                            String.format(Locale.FRANCE, "%+.2f px", it)
                        )
                    }
                    V067Value(
                        "Confirmation optimum",
                        "${state.bahtinovOptimumStreak}/2"
                    )
                    state.bahtinovFocusInstruction?.let {
                        Spacer(Modifier.height(5.dp))
                        Text(it, color = StellarOrange)
                    }

                    if (state.bahtinovValidated) {
                        Spacer(Modifier.height(7.dp))
                        Text(
                            "OPTIMUM VALIDÉ ✓",
                            color = StellarGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = viewModel::removeBahtinovMask,
                        enabled = !state.bahtinovIsLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("MASQUE RETIRÉ")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            V067Card("IMAGE") {
                StellarImagePreview(
                    imageBytes = state.bahtinovImageBytes ?: state.imageBytes,
                    contentDescription = "Capture V0.6.7",
                    loadingText = when {
                        state.isCapturing -> "Pose 4 s…"
                        state.isSolving -> "Astrométrie en cours…"
                        state.bahtinovIsLoading -> "Pose Bahtinov 4 s…"
                        else -> null
                    },
                    emptyText = "Aucune image",
                    showCrosshair = false
                )
            }

            if (centering?.status == "centered") {
                Spacer(Modifier.height(12.dp))
                V067Card("STACKING") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        V067Value("Capturées", "${session.capturedFrames}")
                        V067Value("Acceptées", "${session.acceptedFrames}")
                        V067Value("Rejetées", "${session.rejectedFrames}")
                    }
                    Spacer(Modifier.height(8.dp))
                    if (session.stacking.running) {
                        OutlinedButton(
                            onClick = { viewModel.stopStacking(serverBaseUrl) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("ARRÊTER LE STACKING")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.startStacking(serverBaseUrl) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = v067PrimaryColors()
                        ) {
                            Text("DÉMARRER LE STACKING", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            state.statusMessage?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = StellarOrange, fontWeight = FontWeight.SemiBold)
            }
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text("Erreur : $it", color = StellarRed)
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}


@Composable
private fun V067Card(
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
                color = StellarOrange,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(9.dp))
            content()
        }
    }
}


@Composable
private fun V067Value(label: String, value: String) {
    Text("$label : $value", color = StellarMuted)
}


@Composable
private fun v067PrimaryColors() =
    ButtonDefaults.buttonColors(
        containerColor = StellarOrange,
        contentColor = StellarBackground
    )
