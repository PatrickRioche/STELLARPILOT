package fr.stellarpilot.app.feature.preparation

import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.stellarpilot.app.R
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
import kotlin.math.abs
import kotlin.math.roundToInt


private val astrometryExposureOptionsMs =
    buildList {
        for (value in 1..99) add(value)
        for (value in 100..990 step 10) add(value)
        for (value in 1000..10000 step 100) add(value)
    }


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
    val previewBitmap =
        remember(previewState.imageBytes) {
            previewState.imageBytes?.let { bytes ->
                val options =
                    BitmapFactory.Options().apply {
                        inSampleSize = 4
                    }
                BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size,
                    options
                )?.asImageBitmap()
            }
        }

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

    val exposureIndex =
        remember(exposureMs) {
            astrometryExposureOptionsMs
                .indices
                .minByOrNull { index ->
                    abs(
                        astrometryExposureOptionsMs[index] -
                            exposureMs
                    )
                }
                ?: 0
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
                "unsolved"
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
                Text(
                    text =
                        "Mode démonstration local : aucune caméra, aucun Raspberry Pi et aucun réseau ne sont utilisés.",
                    color = StellarOrange
                )

                Spacer(Modifier.height(6.dp))

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

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .background(
                                        Color.Black,
                                        RoundedCornerShape(12.dp)
                                    ),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(
                                    id = R.drawable.m103_preview
                                ),
                                contentDescription =
                                    "M103 - démonstration locale",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }

                        when {
                            demoM103State.solveStatus == "solved" -> {
                                Text(
                                    text = "✓ Démonstration locale réussie",
                                    color = StellarGreen,
                                    fontWeight = FontWeight.Bold
                                )

                                AstrometryValue(
                                    label = "Centre RA",
                                    value =
                                        formatAstrometryNumber(
                                            demoM103State.ra,
                                            6
                                        )
                                )

                                AstrometryValue(
                                    label = "Centre DEC",
                                    value =
                                        "${formatAstrometryNumber(demoM103State.dec, 6)}°"
                                )

                                AstrometryValue(
                                    label = "Orientation",
                                    value =
                                        "${formatAstrometryNumber(demoM103State.orientationDeg, 2)}°"
                                )

                                AstrometryValue(
                                    label = "Échelle",
                                    value =
                                        "${formatAstrometryNumber(demoM103State.pixelScaleArcsec, 3)} arcsec/pixel"
                                )

                                demoM103State.solveDurationMs?.let { duration ->
                                    AstrometryValue(
                                        label = "Durée simulée",
                                        value =
                                            String.format(
                                                Locale.FRANCE,
                                                "%.2f s",
                                                duration / 1000.0
                                            )
                                    )
                                }
                            }

                            demoM103State.solveStatus == "error" -> {
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

                        demoM103State.error?.let { error ->
                            Text(
                                text = error,
                                color = StellarRed,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        OutlinedButton(
                            onClick = onRunDemoM103,
                            enabled = !demoM103State.isLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Relancer la démonstration M103")
                        }
                    }
                }

                Button(
                    onClick = onNext,
                    enabled = demoSolveSucceeded,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StellarOrange,
                        contentColor = StellarBackground
                    )
                ) {
                    Text(
                        text =
                            if (demoSolveSucceeded) {
                                "Continuer la démonstration"
                            } else {
                                "Démonstration M103 requise"
                            },
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    text =
                        "Capture et résolution du champ réel avec astrometry.net",
                    color = StellarMuted
                )

                Text(
                    text = cameraName ?: "Caméra INDI",
                    color = StellarMuted
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Votre capture caméra",
                    color = StellarText,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text =
                        "Réglez l'exposition puis réalisez votre propre capture. Seule cette astrométrie permet de continuer.",
                    color = StellarMuted,
                    style = MaterialTheme.typography.bodySmall
                )

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

                        Slider(
                            value = exposureIndex.toFloat(),
                            onValueChange = { value ->
                                val index =
                                    value
                                        .roundToInt()
                                        .coerceIn(
                                            0,
                                            astrometryExposureOptionsMs.lastIndex
                                        )
                                onExposureChange(
                                    astrometryExposureOptionsMs[index]
                                )
                            },
                            valueRange =
                                0f..astrometryExposureOptionsMs.lastIndex.toFloat(),
                            enabled = !previewState.isLoading
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "1 ms",
                                color = StellarMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "10 s",
                                color = StellarMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .background(
                                Color.Black,
                                RoundedCornerShape(12.dp)
                            ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        previewBitmap != null -> {
                            Image(
                                bitmap = previewBitmap,
                                contentDescription = "Aperçu caméra",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )

                            Text(
                                text = "+",
                                color = StellarOrange,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        previewState.isLoading -> {
                            Text(
                                text =
                                    "Acquisition de l'image… ${String.format(Locale.FRANCE, "%.1f s", acquisitionElapsedMs / 1000.0)}",
                                color = StellarMuted
                            )
                        }

                        else -> {
                            Text(
                                text = "Aucune image caméra",
                                color = StellarMuted
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
                            solveStatus == "solving" -> {
                                Text(
                                    text =
                                        "Résolution en cours avec ${previewState.solver ?: "astrometry.net"}…",
                                    color = StellarOrange
                                )
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
                                    text =
                                        "Aucune astrométrie caméra pour le moment",
                                    color = StellarMuted
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !previewState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text =
                            if (previewState.isLoading) {
                                "Acquisition / astrométrie…"
                            } else {
                                "Nouvelle capture et astrométrie"
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
