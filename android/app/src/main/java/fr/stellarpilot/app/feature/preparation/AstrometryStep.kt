package fr.stellarpilot.app.feature.preparation

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.stellarpilot.app.ui.components.AstrometryQualityIndicator
import fr.stellarpilot.app.ui.components.StellarImagePreview
import fr.stellarpilot.app.ui.theme.StellarBackground
import fr.stellarpilot.app.ui.theme.StellarGreen
import fr.stellarpilot.app.ui.theme.StellarMuted
import fr.stellarpilot.app.ui.theme.StellarOrange
import fr.stellarpilot.app.ui.theme.StellarRed
import fr.stellarpilot.app.ui.theme.StellarSurface
import fr.stellarpilot.app.ui.theme.StellarSurfaceRaised
import fr.stellarpilot.app.ui.theme.StellarText
import kotlinx.coroutines.delay
import java.util.Locale


private val baseExposurePresetsMs =
    listOf(
        1,
        100,
        500,
        2_000,
        4_000,
        10_000
    )


private fun formatExposure(
    milliseconds: Int
): String =
    if (milliseconds < 1000) {
        "$milliseconds ms"
    } else {
        String.format(
            Locale.FRANCE,
            "%.1f s",
            milliseconds / 1000.0
        )
    }


private fun exposureStepMs(milliseconds: Int): Int =
    when {
        milliseconds < 10 -> 1
        milliseconds < 100 -> 10
        milliseconds < 1000 -> 100
        else -> 500
    }


private fun formatAstrometryNumber(
    value: Double?,
    digits: Int
): String =
    value?.let {
        String.format(
            Locale.FRANCE,
            "%.${digits}f",
            it
        )
    } ?: "—"


@Composable
fun AstrometryStep(
    demoMode: Boolean,
    previewState: CameraPreviewUiState,
    demoM103State: DemoM103UiState,
    cameraName: String?,
    exposureMs: Int,
    onExposureChange: (Int) -> Unit,
    onRunDemoM103: () -> Unit,
    onRefresh: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    var acquisitionElapsedMs by remember {
        mutableStateOf(0L)
    }

    LaunchedEffect(
        previewState.isLoading,
        previewState.imageBytes
    ) {
        if (
            previewState.isLoading &&
            previewState.imageBytes == null
        ) {
            val startedAt = SystemClock.elapsedRealtime()
            acquisitionElapsedMs = 0L

            while (
                previewState.isLoading &&
                previewState.imageBytes == null
            ) {
                acquisitionElapsedMs =
                    SystemClock.elapsedRealtime() - startedAt
                delay(100)
            }
        }
    }

    val solveStatus =
        previewState.solveStatus
            ?.lowercase(Locale.ROOT)

    val solveSucceeded =
        previewState.ra != null &&
            previewState.dec != null &&
            solveStatus !in setOf(
                "error",
                "failed",
                "timeout",
                "unsolved",
                "quality_insufficient"
            )

    val demoSolveSucceeded =
        demoM103State.solveStatus == "solved"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = StellarSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Première astrométrie",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = StellarText
            )

            if (demoMode) {
                DemoAstrometryContent(
                    state = demoM103State,
                    onRunDemoM103 = onRunDemoM103,
                    onNext = onNext,
                    canContinue = demoSolveSucceeded
                )
            } else {
                Text(
                    text =
                        "Capture réelle, contrôle qualité puis résolution avec astrometry.net.",
                    color = StellarMuted
                )

                Text(
                    text = cameraName ?: "Caméra INDI",
                    color = StellarMuted
                )

                Spacer(Modifier.height(4.dp))

                ExposureControls(
                    exposureMs = exposureMs,
                    suggestedExposureMs =
                        previewState.suggestedExposureMs,
                    enabled = !previewState.isLoading,
                    onExposureChange = onExposureChange
                )

                Spacer(Modifier.height(4.dp))

                StellarImagePreview(
                    imageBytes = previewState.imageBytes,
                    contentDescription = "Aperçu caméra",
                    loadingText =
                        if (
                            previewState.isLoading &&
                            previewState.imageBytes == null
                        ) {
                            "Acquisition de l'image… " +
                                String.format(
                                    Locale.FRANCE,
                                    "%.1f s",
                                    acquisitionElapsedMs / 1000.0
                                )
                        } else {
                            null
                        },
                    emptyText = "Aucune image caméra",
                    showCrosshair = true
                )

                if (
                    previewState.imageBytes != null ||
                    previewState.qualityScore != null
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = StellarSurfaceRaised
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp)
                        ) {
                            AstrometryQualityIndicator(
                                score = previewState.qualityScore,
                                label = previewState.qualityLabel,
                                starCount = previewState.qualityStarCount,
                                saturatedPercent =
                                    previewState.qualitySaturatedPercent,
                                classification =
                                    previewState.qualityClassification
                            )

                            Spacer(Modifier.height(6.dp))

                            Text(
                                text =
                                    "Astrometry.net est lancé automatiquement à partir de 50/100.",
                                color = StellarMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                previewState.error?.let { error ->
                    Text(
                        text = error,
                        color = StellarRed
                    )
                }

                AstrometryResultCard(
                    previewState = previewState,
                    solveStatus = solveStatus,
                    solveSucceeded = solveSucceeded
                )

                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !previewState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text =
                            when {
                                previewState.isLoading ->
                                    "Acquisition / analyse…"
                                solveStatus == "quality_insufficient" ->
                                    "Recapturer avec le nouveau réglage"
                                else ->
                                    "Capturer, analyser et résoudre"
                            }
                    )
                }

                Button(
                    onClick = onNext,
                    enabled = solveSucceeded,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StellarOrange,
                        contentColor = StellarBackground
                    )
                ) {
                    Text(
                        text =
                            if (solveSucceeded) {
                                "Continuer vers l'étoile"
                            } else {
                                "Astrométrie requise"
                            },
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            OutlinedButton(
                onClick = onPrevious,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Retour")
            }
        }
    }
}


@Composable
private fun ExposureControls(
    exposureMs: Int,
    suggestedExposureMs: List<Int>,
    enabled: Boolean,
    onExposureChange: (Int) -> Unit
) {
    val step = exposureStepMs(exposureMs)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = StellarSurfaceRaised
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Temps d'exposition",
                    color = StellarText,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = formatExposure(exposureMs),
                    color = StellarOrange,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Réglable de 1 ms à 10 s",
                color = StellarMuted,
                style = MaterialTheme.typography.bodySmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onExposureChange(
                            (exposureMs - step).coerceIn(1, 10_000)
                        )
                    },
                    enabled = enabled && exposureMs > 1,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("− ${formatExposure(step)}")
                }

                OutlinedButton(
                    onClick = {
                        onExposureChange(
                            (exposureMs + step).coerceIn(1, 10_000)
                        )
                    },
                    enabled = enabled && exposureMs < 10_000,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("+ ${formatExposure(step)}")
                }
            }

            baseExposurePresetsMs
                .chunked(3)
                .forEach { rowValues ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowValues.forEach { value ->
                            ExposurePresetButton(
                                valueMs = value,
                                selected = value == exposureMs,
                                enabled = enabled,
                                onExposureChange = onExposureChange,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

            if (suggestedExposureMs.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Réglages conseillés d'après la dernière image",
                    color = StellarMuted,
                    style = MaterialTheme.typography.bodySmall
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggestedExposureMs
                        .take(3)
                        .forEach { value ->
                            ExposurePresetButton(
                                valueMs = value,
                                selected = value == exposureMs,
                                enabled = enabled,
                                onExposureChange = onExposureChange,
                                modifier = Modifier.weight(1f)
                            )
                        }
                }
            }
        }
    }
}


@Composable
private fun ExposurePresetButton(
    valueMs: Int,
    selected: Boolean,
    enabled: Boolean,
    onExposureChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = { onExposureChange(valueMs) },
            enabled = enabled,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = StellarOrange,
                contentColor = StellarBackground
            )
        ) {
            Text(formatExposure(valueMs))
        }
    } else {
        OutlinedButton(
            onClick = { onExposureChange(valueMs) },
            enabled = enabled,
            modifier = modifier
        ) {
            Text(formatExposure(valueMs))
        }
    }
}


@Composable
private fun AstrometryResultCard(
    previewState: CameraPreviewUiState,
    solveStatus: String?,
    solveSucceeded: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = StellarSurfaceRaised
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = "Dernière astrométrie de votre caméra",
                color = StellarText,
                fontWeight = FontWeight.Bold
            )

            when {
                solveStatus == "quality_check" -> {
                    Text(
                        text = "Analyse de la qualité de l'image…",
                        color = StellarOrange
                    )
                }

                solveStatus == "solving" -> {
                    Text(
                        text =
                            "Qualité validée • résolution en cours avec astrometry.net…",
                        color = StellarOrange
                    )
                }

                solveStatus == "quality_insufficient" -> {
                    Text(
                        text =
                            "Qualité insuffisante : astrometry.net n'a pas été lancé.",
                        color = StellarRed,
                        fontWeight = FontWeight.SemiBold
                    )
                    previewState.solveDetail?.let { detail ->
                        Text(
                            text = detail,
                            color = StellarMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                solveSucceeded -> {
                    Text(
                        text = "✓ Champ céleste localisé",
                        color = StellarGreen,
                        fontWeight = FontWeight.Bold
                    )

                    AstrometryValue(
                        label = "Solveur",
                        value = previewState.solver ?: "astrometry.net"
                    )
                    AstrometryValue(
                        label = "Centre RA",
                        value = formatAstrometryNumber(previewState.ra, 6)
                    )
                    AstrometryValue(
                        label = "Centre DEC",
                        value =
                            "${formatAstrometryNumber(previewState.dec, 6)}°"
                    )
                    AstrometryValue(
                        label = "Orientation",
                        value =
                            "${formatAstrometryNumber(previewState.orientationDeg, 2)}°"
                    )
                    AstrometryValue(
                        label = "Échelle",
                        value =
                            "${formatAstrometryNumber(previewState.pixelScaleArcsec, 3)} arcsec/pixel"
                    )

                    previewState.detectedStarCount?.let { count ->
                        Text(
                            text =
                                if (count > 0) {
                                    "✓ Étoiles détectées : $count"
                                } else {
                                    "Aucune étoile détectée"
                                },
                            color =
                                if (count > 0) StellarGreen
                                else StellarMuted,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                solveStatus != null -> {
                    val message =
                        when (solveStatus) {
                            "timeout" ->
                                "Caméra : délai d'astrométrie dépassé"
                            "unsolved" ->
                                "Caméra : champ non résolu"
                            "failed" ->
                                "Caméra : échec de la résolution"
                            "error" ->
                                "Caméra : erreur d'astrométrie"
                            else ->
                                "Astrométrie caméra : ${previewState.solveStatus}"
                        }

                    Text(
                        text = message,
                        color = StellarRed,
                        fontWeight = FontWeight.SemiBold
                    )

                    previewState.solveDetail?.let { detail ->
                        Text(
                            text = detail,
                            color = StellarMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                else -> {
                    Text(
                        text = "Aucune astrométrie caméra pour le moment",
                        color = StellarMuted
                    )
                }
            }
        }
    }
}


@Composable
private fun DemoAstrometryContent(
    state: DemoM103UiState,
    onRunDemoM103: () -> Unit,
    onNext: () -> Unit,
    canContinue: Boolean
) {
    Text(
        text =
            "Mode démonstration local : aucune caméra, aucun Raspberry Pi et aucun réseau ne sont utilisés.",
        color = StellarOrange
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = StellarSurfaceRaised
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Démonstration M103",
                color = StellarText,
                fontWeight = FontWeight.Bold
            )

            Text(
                text =
                    "Image et résultat astrométrique de référence embarqués directement dans l'APK.",
                color = StellarMuted,
                style = MaterialTheme.typography.bodySmall
            )

            StellarImagePreview(
                imageBytes = state.imageBytes,
                contentDescription = "M103 - démonstration locale",
                emptyText = "Image M103 locale",
                showCrosshair = false
            )

            when {
                state.solveStatus == "solved" -> {
                    Text(
                        text = "✓ Démonstration locale réussie",
                        color = StellarGreen,
                        fontWeight = FontWeight.Bold
                    )

                    AstrometryValue(
                        label = "Centre RA",
                        value = formatAstrometryNumber(state.ra, 6)
                    )
                    AstrometryValue(
                        label = "Centre DEC",
                        value = "${formatAstrometryNumber(state.dec, 6)}°"
                    )
                    AstrometryValue(
                        label = "Orientation",
                        value =
                            "${formatAstrometryNumber(state.orientationDeg, 2)}°"
                    )
                    AstrometryValue(
                        label = "Échelle",
                        value =
                            "${formatAstrometryNumber(state.pixelScaleArcsec, 3)} arcsec/pixel"
                    )
                }

                state.solveStatus == "error" -> {
                    Text(
                        text = "Échec de la démonstration locale",
                        color = StellarRed,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                else -> {
                    Text(
                        text = "Données de démonstration prêtes.",
                        color = StellarMuted
                    )
                }
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    color = StellarRed,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            OutlinedButton(
                onClick = onRunDemoM103,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Relancer la démonstration M103")
            }
        }
    }

    Button(
        onClick = onNext,
        enabled = canContinue,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = StellarOrange,
            contentColor = StellarBackground
        )
    ) {
        Text(
            text =
                if (canContinue) {
                    "Continuer la démonstration"
                } else {
                    "Démonstration M103 requise"
                },
            fontWeight = FontWeight.Bold
        )
    }
}


@Composable
private fun AstrometryValue(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = StellarMuted
        )
        Text(
            text = value,
            color = StellarText,
            fontWeight = FontWeight.SemiBold
        )
    }
}
