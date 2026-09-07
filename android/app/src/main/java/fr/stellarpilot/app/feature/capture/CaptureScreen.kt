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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.stellarpilot.app.feature.demo.DemoModeState
import fr.stellarpilot.app.ui.components.AstrometryQualityIndicator
import fr.stellarpilot.app.ui.components.StellarImagePreview
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
                    "Centrage astrométrique en boucle fermée, calibration Dark, sélection des poses et stacking continu reprenable.",
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
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "MODE DÉMONSTRATION • 100 % LOCAL",
                            color = StellarOrange,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text =
                                "Capture, centrage et stacking sont simulés. Aucun ordre n'est envoyé au Raspberry Pi.",
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
                        text = "Aucune cible sélectionnée. Choisissez d'abord un objet dans Ciel.",
                        color = StellarRed
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = viewModel::loadSelectedTarget,
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
                        ?.let { Text(text = it, color = StellarMuted) }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text =
                            "AD ${format(target.raHours, 4)} h • DEC ${formatSigned(target.decDeg, 4)}°",
                        color = StellarMuted
                    )
                    Text(
                        text = "TRACKING ${target.trackingMode.uppercase(Locale.ROOT)}",
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
                        onClick = { viewModel.changeExposure(-0.5) },
                        enabled = session == null && !state.isBusy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("− 0,5 s")
                    }
                    OutlinedButton(
                        onClick = { viewModel.changeExposure(0.5) },
                        enabled = session == null && !state.isBusy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+ 0,5 s")
                    }
                }

                if (state.operationPhase != null) {
                    Spacer(Modifier.height(14.dp))
                    val elapsedSeconds = state.operationElapsedMs / 1000.0
                    val expectedSeconds = state.operationExpectedMs / 1000.0
                    val progress =
                        if (state.operationExpectedMs > 0L) {
                            (state.operationElapsedMs.toFloat() /
                                state.operationExpectedMs.toFloat())
                                .coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                    Text(
                        text =
                            if (state.operationPhase == "capture") {
                                "Acquisition… ${format(elapsedSeconds, 1)} / ${format(expectedSeconds, 1)} s"
                            } else {
                                "Pose terminée • astrométrie en cours…"
                            },
                        color = StellarOrange,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = progress,
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
                    color = when (centering?.status) {
                        "centered" -> StellarGreen
                        "unsolved" -> StellarRed
                        "correction_required" -> StellarOrange
                        else -> StellarMuted
                    },
                    fontWeight = FontWeight.Bold
                )

                session?.centeringQuality?.let { quality ->
                    Spacer(Modifier.height(10.dp))
                    AstrometryQualityIndicator(
                        score = quality.score,
                        label = quality.label,
                        starCount = quality.starCount,
                        saturatedPercent = quality.saturatedPercent,
                        classification = quality.classification
                    )
                }

                centering?.errorArcsec?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Erreur de centrage : ${format(it, 1)}″",
                        color = StellarText
                    )
                }
                centering?.verifiedAt?.let {
                    Text(
                        text = "Contrôle astrométrique validé",
                        color = StellarGreen,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (centering?.status == "correction_required") {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text =
                            "Sécurité : une seule correction automatique est autorisée, suivie obligatoirement d'une nouvelle pose de contrôle.",
                        color = StellarMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.centerTarget(serverBaseUrl) },
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
                            if (centering?.status == "centered") "RECENTRER"
                            else "CAPTURER & CENTRER",
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
                StellarImagePreview(
                    imageBytes = state.imageBytes,
                    contentDescription = "Capture astronomique",
                    loadingText = if (state.isBusy) state.statusMessage else null,
                    emptyText = "Aucune image",
                    showCrosshair = false
                )
            }

            Spacer(Modifier.height(14.dp))

            CaptureCard {
                Text(
                    text = "STACKING CONTINU",
                    color = StellarOrange,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text =
                        "Chaque LIGHT est calibré par le Master Dark compatible avant contrôle qualité et registration.",
                    color = StellarMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Metric("Capturées", "${session?.capturedFrames ?: 0}")
                    Metric("Acceptées", "${session?.acceptedFrames ?: 0}")
                    Metric("Rejetées", "${session?.rejectedFrames ?: 0}")
                }

                Spacer(Modifier.height(14.dp))
                InfoText(
                    "Temps d'acquisition",
                    formatDuration(session?.acquisitionSeconds ?: 0.0)
                )
                InfoText(
                    "Intégration utile",
                    formatDuration(session?.integrationSeconds ?: 0.0)
                )
                InfoText(
                    "Séquences",
                    "${session?.stacking?.runCount ?: 0}"
                )

                session?.calibration?.let { calibration ->
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "CALIBRATION DARK",
                        color = StellarOrange,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                    InfoText(
                        "État",
                        when (calibration.status) {
                            "ready" -> "Master Dark appliqué ✓"
                            "unavailable" -> "Incompatible / indisponible"
                            "error" -> "Erreur"
                            else -> "En attente"
                        }
                    )
                    InfoText("Master", calibration.masterId ?: "Non sélectionné")
                    calibration.temperatureDeltaC?.let {
                        InfoText("Écart température", "${format(it, 1)} °C")
                    }
                    calibration.hotPixels?.let {
                        InfoText("Pixels chauds corrigés", "$it")
                    }
                    InfoText("LIGHT calibrés", "${calibration.calibratedFrames}")
                    calibration.detail?.let {
                        Text(
                            text = it,
                            color = StellarRed,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                session?.lastLightQuality?.let { quality ->
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "QUALITÉ DERNIÈRE POSE",
                        color = StellarOrange,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                    quality.score?.let { InfoText("Score", "$it / 100") }
                    quality.starCount?.let { InfoText("Étoiles", "$it") }
                    quality.fwhmPx?.let { InfoText("FWHM", "${format(it, 1)} px") }
                    quality.ellipticity?.let {
                        InfoText("Ellipticité", format(it, 2))
                    }
                    if (quality.reasons.isNotEmpty()) {
                        Text(
                            text = "Rejet : ${quality.reasons.joinToString { rejectionLabel(it) }}",
                            color = StellarRed,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (session?.rejectedByReason?.isNotEmpty() == true) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "REJETS",
                        color = StellarOrange,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                    session.rejectedByReason
                        .toList()
                        .sortedByDescending { it.second }
                        .forEach { (reason, count) ->
                            InfoText(rejectionLabel(reason), "$count")
                        }
                }

                session?.stacking?.lastRegistrationDistancePx?.let {
                    Spacer(Modifier.height(8.dp))
                    InfoText("Décalage registration", "${format(it, 1)} px")
                }

                if (session?.stacking?.recenterRequired == true) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "RECENTRAGE REQUIS — STACKING SUSPENDU",
                        color = StellarRed,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (session?.state == "paused_calibration") {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "MASTER DARK COMPATIBLE REQUIS — STACKING SUSPENDU",
                        color = StellarRed,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(14.dp))

                when {
                    session?.stacking?.running == true -> {
                        Button(
                            onClick = { viewModel.stopStacking(serverBaseUrl) },
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
                    }

                    session != null &&
                        session.state in setOf(
                            "stopped",
                            "paused_recenter",
                            "paused_calibration"
                        ) &&
                        !state.savedToGallery -> {
                        Button(
                            onClick = { viewModel.resumeStacking(serverBaseUrl) },
                            enabled = !state.isBusy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StellarOrange,
                                contentColor = StellarBackground
                            )
                        ) {
                            Text(
                                text = "REPRENDRE LE STACKING",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    !state.savedToGallery -> {
                        Button(
                            onClick = { viewModel.startStacking(serverBaseUrl) },
                            enabled =
                                target != null &&
                                    !state.isBusy &&
                                    session?.centering?.status == "centered",
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
                }

                if (
                    session != null &&
                    !session.stacking.running &&
                    session.acceptedFrames > 0 &&
                    !state.savedToGallery
                ) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.finalizeSession(serverBaseUrl) },
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ARRÊT FINAL • ENREGISTRER DANS GALERIES")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text =
                            "L'arrêt final reconstruit le FITS avec une moyenne sigma-clippée robuste avant la sauvegarde.",
                        color = StellarMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
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
                        text = state.statusMessage ?: "Traitement…",
                        color = StellarMuted
                    )
                }
            } else {
                state.statusMessage?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(text = it, color = StellarGreen)
                }
            }

            state.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(text = it, color = StellarRed)
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
        colors = CardDefaults.cardColors(containerColor = StellarSurface),
        border = BorderStroke(1.dp, StellarBorder)
    ) {
        Column(
            modifier = Modifier
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


@Composable
private fun InfoText(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = StellarMuted,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = StellarText,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
    }
}


private fun rejectionLabel(value: String): String =
    when (value) {
        "overexposed" -> "Surexposition"
        "too_few_stars" -> "Trop peu d'étoiles"
        "blurred" -> "Flou"
        "trailed" -> "Filé"
        "transparency_drop" -> "Transparence dégradée"
        "signal_drop" -> "Signal dégradé"
        "focus_or_seeing_degraded" -> "Focale / seeing dégradé"
        "tracking_degraded" -> "Suivi dégradé"
        "background_degraded" -> "Fond de ciel dégradé"
        "registration_error" -> "Registration impossible"
        "dark_incompatible" -> "Master Dark incompatible"
        "calibration_error" -> "Erreur calibration"
        else -> value
    }


private fun format(
    value: Double,
    decimals: Int
): String =
    String.format(Locale.FRANCE, "%.${decimals}f", value)


private fun formatSigned(
    value: Double,
    decimals: Int
): String =
    String.format(Locale.FRANCE, "%+.${decimals}f", value)


private fun formatDuration(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val remaining = total % 60
    return when {
        hours > 0 -> "${hours} h ${minutes} min ${remaining} s"
        minutes > 0 -> "${minutes} min ${remaining} s"
        else -> "${remaining} s"
    }
}
