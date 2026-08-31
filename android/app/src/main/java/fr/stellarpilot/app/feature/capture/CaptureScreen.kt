package fr.stellarpilot.app.feature.capture

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.stellarpilot.app.feature.demo.DemoModeState
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


@Composable
fun CaptureScreen(
    serverBaseUrl: String,
    viewModel: CaptureViewModel = viewModel()
) {
    val state = viewModel.uiState
    val target = state.target
    val session = state.session
    val demoMode = DemoModeState.active

    val bitmap = remember(state.imageBytes) {
        state.imageBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size
            )?.asImageBitmap()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = StellarBackground
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = 24.dp,
                        vertical = 24.dp
                    )
        ) {
            Text(
                text = "CAPTURE",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = StellarOrange
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Cadrage & stacking",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = StellarText
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text =
                    "Astrométrie de centrage, validation visuelle, puis stacking avec contrôle périodique du pointage.",
                color = StellarMuted
            )

            if (demoMode) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = StellarSurfaceRaised,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, StellarOrange)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "MODE DÉMONSTRATION • 100 % LOCAL",
                            color = StellarOrange,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text =
                                "Capture, astrométrie et stacking sont simulés dans l'application. Aucun ordre n'est envoyé au Raspberry Pi.",
                            color = StellarMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            CaptureCard {
                Text(
                    text = "CIBLE ACTIVE",
                    color = StellarOrange,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                if (target == null) {
                    Text(
                        text =
                            "Aucune cible sélectionnée. Choisissez d'abord un objet dans Ciel.",
                        color = StellarRed
                    )

                    Spacer(Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.loadSelectedTarget()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Relire la cible")
                    }
                } else {
                    Text(
                        text = target.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = StellarText
                    )

                    target.reference
                        ?.takeIf { it.isNotBlank() }
                        ?.let {
                            Text(
                                text = it,
                                color = StellarMuted
                            )
                        }

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text =
                            "AD ${format(target.raHours, 4)} h • DEC ${formatSigned(target.decDeg, 4)}°",
                        color = StellarMuted
                    )

                    Text(
                        text =
                            "TRACKING ${target.trackingMode.uppercase(Locale.ROOT)}",
                        color = StellarGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            CaptureCard {
                Text(
                    text = "ACQUISITION",
                    color = StellarOrange,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Exposition",
                        color = StellarText,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "${format(state.exposureSeconds, 1)} s",
                        color = StellarOrange,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.changeExposure(-0.5)
                        },
                        enabled = session == null && !state.isBusy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("− 0,5 s")
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.changeExposure(0.5)
                        },
                        enabled = session == null && !state.isBusy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+ 0,5 s")
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text =
                        "Valeur initiale : 4 s. Elle sera ensuite ajustée au profil du setup et à la qualité réelle du suivi.",
                    color = StellarMuted,
                    style = MaterialTheme.typography.bodySmall
                )

                if (state.operationPhase != null) {
                    Spacer(Modifier.height(14.dp))

                    val elapsedSeconds =
                        state.operationElapsedMs / 1000.0
                    val expectedSeconds =
                        state.operationExpectedMs / 1000.0
                    val exposureProgress =
                        if (state.operationExpectedMs > 0L) {
                            (
                                state.operationElapsedMs.toFloat() /
                                    state.operationExpectedMs.toFloat()
                                ).coerceIn(0f, 1f)
                        } else {
                            0f
                        }

                    Text(
                        text =
                            if (state.operationPhase == "capture") {
                                "Acquisition de l'image… ${format(elapsedSeconds, 1)} / ${format(expectedSeconds, 1)} s"
                            } else {
                                "Pose ${format(expectedSeconds, 1)} s terminée • astrométrie en cours… ${format(elapsedSeconds, 1)} s"
                            },
                        color = StellarOrange,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = exposureProgress,
                        modifier = Modifier.fillMaxWidth(),
                        color = StellarOrange,
                        trackColor = StellarSurfaceRaised
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            CaptureCard {
                Text(
                    text = "POINTAGE & ASTROMÉTRIE",
                    color = StellarOrange,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                val centering = session?.centering

                Text(
                    text = when (centering?.status) {
                        "centered" -> "CENTRÉ ✓"
                        "correction_required" -> "CORRECTION NÉCESSAIRE"
                        "unsolved" -> "ASTROMÉTRIE NON RÉSOLUE"
                        else -> "À CONTRÔLER"
                    },
                    color =
                        when (centering?.status) {
                            "centered" -> StellarGreen
                            "unsolved" -> StellarRed
                            else -> StellarMuted
                        },
                    fontWeight = FontWeight.Bold
                )

                centering?.errorArcsec?.let { error ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text =
                            "Erreur de centrage : ${format(error, 1)}″",
                        color = StellarText
                    )
                }

                centering?.solveRaDeg?.let { ra ->
                    Text(
                        text =
                            "Centre astrométrique : AD ${format(ra / 15.0, 4)} h • DEC ${centering.solveDecDeg?.let { formatSigned(it, 4) } ?: "—"}°",
                        color = StellarMuted
                    )
                }

                if (centering?.status == "unsolved") {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text =
                            "Aucune correction AD/DEC n'est envoyée tant que le champ n'est pas résolu.",
                        color = StellarMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    centering.solverDetail
                        ?.takeIf { it.isNotBlank() }
                        ?.let { detail ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = detail,
                                color = StellarMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        viewModel.centerTarget(serverBaseUrl)
                    },
                    enabled =
                        target != null &&
                            !state.isBusy &&
                            session?.stacking?.running != true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StellarOrange,
                        contentColor = StellarBackground
                    )
                ) {
                    Text(
                        text =
                            if (centering?.status == "centered") {
                                "RECENTRER"
                            } else {
                                "CAPTURER & CENTRER"
                            },
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            CaptureCard {
                Text(
                    text = "IMAGE / STACK",
                    color = StellarOrange,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(10.dp))

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Capture astronomique",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth
                    )
                } else {
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                        color = StellarSurfaceRaised,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Aucune image",
                                color = StellarMuted
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            CaptureCard {
                Text(
                    text = "STACKING",
                    color = StellarOrange,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Metric(
                        label = "Capturées",
                        value = "${session?.capturedFrames ?: 0}"
                    )
                    Metric(
                        label = "Acceptées",
                        value = "${session?.acceptedFrames ?: 0}"
                    )
                    Metric(
                        label = "Rejetées",
                        value = "${session?.rejectedFrames ?: 0}"
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text =
                        "Intégration : ${formatDuration(session?.integrationSeconds ?: 0.0)}",
                    color = StellarText,
                    fontWeight = FontWeight.SemiBold
                )

                session?.stacking?.lastRegistrationDistancePx?.let {
                    Text(
                        text =
                            "Décalage registration : ${format(it, 1)} px",
                        color = StellarMuted
                    )
                }

                if (session?.stacking?.recenterRequired == true) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text =
                            "RECENTRAGE ASTROMÉTRIQUE REQUIS — stacking suspendu",
                        color = StellarRed,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(14.dp))

                if (session?.stacking?.running == true) {
                    Button(
                        onClick = {
                            viewModel.stopStacking(serverBaseUrl)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StellarRed,
                            contentColor = StellarText
                        )
                    ) {
                        Text(
                            text = "ARRÊTER LE STACKING",
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            viewModel.startStacking(serverBaseUrl)
                        },
                        enabled =
                            session?.centering?.status == "centered" &&
                                !state.isBusy &&
                                !state.savedToGallery,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StellarOrange,
                            contentColor = StellarBackground
                        )
                    ) {
                        Text(
                            text = "DÉMARRER LE STACKING",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (
                    session != null &&
                    !session.stacking.running &&
                    session.acceptedFrames > 0
                ) {
                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.finalizeSession(serverBaseUrl)
                        },
                        enabled = !state.isBusy && !state.savedToGallery,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("TERMINER & ENREGISTRER DANS GALERIES")
                    }
                }
            }

            if (state.isBusy) {
                Spacer(Modifier.height(14.dp))
                Row {
                    CircularProgressIndicator(
                        color = StellarOrange,
                        modifier = Modifier.width(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = state.statusMessage ?: "Traitement...",
                        color = StellarMuted
                    )
                }
            } else {
                state.statusMessage?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = it,
                        color = StellarGreen
                    )
                }
            }

            state.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = it,
                    color = StellarRed
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}


@Composable
private fun CaptureCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = StellarSurface
        ),
        border = BorderStroke(1.dp, StellarBorder)
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
        ) {
            content()
        }
    }
}


@Composable
private fun Metric(
    label: String,
    value: String
) {
    Column {
        Text(
            text = value,
            color = StellarText,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = label,
            color = StellarMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}


private fun format(
    value: Double,
    decimals: Int
): String =
    String.format(
        Locale.FRANCE,
        "%.${decimals}f",
        value
    )


private fun formatSigned(
    value: Double,
    decimals: Int
): String =
    String.format(
        Locale.FRANCE,
        "%+.${decimals}f",
        value
    )


private fun formatDuration(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val minutes = total / 60
    val remaining = total % 60
    return if (minutes > 0) {
        "${minutes} min ${remaining} s"
    } else {
        "${remaining} s"
    }
}
