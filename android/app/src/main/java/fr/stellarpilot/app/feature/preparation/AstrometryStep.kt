package fr.stellarpilot.app.feature.preparation

import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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

/* Uranus-C : 3856 x 2180, soit environ 1,769:1. */
private const val ASTROMETRY_PREVIEW_ASPECT = 3856f / 2180f
private const val ASTROMETRY_MAX_ZOOM = 8f

/*
 * Plage d'exposition :
 * 1 à 99 ms       : pas de 1 ms
 * 100 à 990 ms    : pas de 10 ms
 * 1 à 10 secondes : pas de 100 ms
 */
private val astrometryExposureOptionsMs =
    buildList {
        for (value in 1..99) add(value)
        for (value in 100..990 step 10) add(value)
        for (value in 1000..10000 step 100) add(value)
    }

private fun formatExposure(milliseconds: Int): String =
    if (milliseconds < 1000) {
        "$milliseconds ms"
    } else {
        String.format(Locale.US, "%.1f s", milliseconds / 1000.0)
    }

private fun formatAstrometryNumber(value: Double?, digits: Int): String =
    value?.let {
        String.format(Locale.US, "%.${digits}f", it)
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
                /*
                 * Sur tablette on conserve davantage de définition qu'avant.
                 * Le zoom tactile a ainsi une vraie utilité tout en restant
                 * raisonnable en mémoire pour un aperçu JPEG.
                 */
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 2
                }

                BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size,
                    options
                )?.asImageBitmap()
            }
        }

    val demoM103Bitmap =
        remember(demoM103State.imageBytes) {
            demoM103State.imageBytes?.let { bytes ->
                BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size
                )?.asImageBitmap()
            }
        }

    var acquisitionElapsedMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(
        previewState.isLoading,
        previewState.imageBytes
    ) {
        if (previewState.isLoading && previewState.imageBytes == null) {
            val startedAt = SystemClock.elapsedRealtime()
            acquisitionElapsedMs = 0L

            while (previewState.isLoading && previewState.imageBytes == null) {
                acquisitionElapsedMs =
                    SystemClock.elapsedRealtime() - startedAt
                delay(100)
            }
        }
    }

    val exposureIndex =
        remember(exposureMs) {
            astrometryExposureOptionsMs.indices.minByOrNull { index ->
                abs(astrometryExposureOptionsMs[index] - exposureMs)
            } ?: 0
        }

    val solveStatus = previewState.solveStatus?.lowercase(Locale.ROOT)

    val solveSucceeded =
        previewState.ra != null &&
            previewState.dec != null &&
            solveStatus !in setOf("error", "failed", "timeout", "unsolved")

    val demoSolveSucceeded = demoM103State.solveStatus == "solved"
    val canContinue = if (demoMode) demoSolveSucceeded else solveSucceeded

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = StellarSurface)
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

            Text(
                text = "Capture et résolution du champ avec astrometry.net",
                color = StellarMuted
            )

            Text(
                text = cameraName ?: "Caméra INDI",
                color = StellarMuted
            )

            Text(
                text =
                    "La monture peut être positionnée avec OnStep ou une autre console. " +
                        "StellarPilot utilise la position AD/DEC disponible via INDI comme indice, " +
                        "puis le plate solving détermine le champ réel.",
                color = StellarMuted,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(6.dp))

            DemoM103Card(
                demoMode = demoMode,
                demoM103State = demoM103State,
                demoM103Bitmap = demoM103Bitmap,
                previewBusy = previewState.isLoading,
                onRunDemoM103 = onRunDemoM103
            )

            if (!demoMode) {
                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Votre capture caméra",
                    color = StellarText,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text =
                        "Réglez l'exposition puis réalisez votre propre capture. " +
                            "Seule cette astrométrie permet de continuer.",
                    color = StellarMuted,
                    style = MaterialTheme.typography.bodySmall
                )

                ExposureCard(
                    exposureMs = exposureMs,
                    exposureIndex = exposureIndex,
                    loading = previewState.isLoading,
                    onExposureChange = onExposureChange
                )

                Spacer(Modifier.height(4.dp))

                when {
                    previewBitmap != null -> {
                        ZoomableAstrometryPreview(
                            bitmap = previewBitmap,
                            contentDescription = "Aperçu caméra",
                            showReticle = true
                        )

                        Text(
                            text =
                                "Pincez avec deux doigts pour zoomer jusqu'à 8×. " +
                                    "Déplacez l'image lorsqu'elle est agrandie ; " +
                                    "le réticule reste fixe au centre.",
                            color = StellarMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    previewState.isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(ASTROMETRY_PREVIEW_ASPECT)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text =
                                    "Acquisition de l'image... ${
                                        String.format(
                                            Locale.FRANCE,
                                            "%.1f s",
                                            acquisitionElapsedMs / 1000.0
                                        )
                                    }",
                                color = StellarMuted
                            )
                        }
                    }

                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(ASTROMETRY_PREVIEW_ASPECT)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Aucune image caméra", color = StellarMuted)
                        }
                    }
                }

                previewState.error?.let { error ->
                    Text(text = error, color = StellarRed)
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
                        if (previewState.isLoading) {
                            "Acquisition / astrométrie..."
                        } else {
                            "Nouvelle capture et astrométrie"
                        }
                    )
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
                        if (canContinue) {
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
private fun DemoM103Card(
    demoMode: Boolean,
    demoM103State: DemoM103UiState,
    demoM103Bitmap: ImageBitmap?,
    previewBusy: Boolean,
    onRunDemoM103: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = StellarSurfaceRaised),
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
                    "Vérifie le fonctionnement du solveur astrometry.net " +
                        "avec une image FITS astronomique de référence.",
                color = StellarMuted,
                style = MaterialTheme.typography.bodySmall
            )

            when {
                demoM103Bitmap != null -> {
                    ZoomableAstrometryPreview(
                        bitmap = demoM103Bitmap,
                        contentDescription = "M103 - image de démonstration",
                        showReticle = false
                    )
                }

                demoMode -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(ASTROMETRY_PREVIEW_ASPECT)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.m103_preview),
                            contentDescription = "M103 - image locale de démonstration",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(ASTROMETRY_PREVIEW_ASPECT)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "L'image M103 sera chargée depuis le serveur lors du test.",
                            color = StellarMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (!demoMode) {
                OutlinedButton(
                    onClick = onRunDemoM103,
                    enabled = !demoM103State.isLoading && !previewBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            demoM103State.isLoading -> "Résolution M103 en cours..."
                            demoM103State.solveStatus != null -> "↻ Relancer le test serveur M103"
                            else -> "▶ Tester l'astrométrie serveur avec M103"
                        }
                    )
                }
            }

            when {
                demoM103State.solveStatus == "solving" ||
                    demoM103State.solveStatus == "loading" -> {
                    Text(
                        "Analyse du champ M103 avec astrometry.net...",
                        color = StellarOrange
                    )
                }

                demoM103State.solveStatus == "solved" -> {
                    Text(
                        "✓ Démonstration réussie",
                        color = StellarGreen,
                        fontWeight = FontWeight.Bold
                    )

                    AstrometryValue(
                        label = "Centre RA",
                        value = formatAstrometryNumber(demoM103State.ra, 6)
                    )

                    AstrometryValue(
                        label = "Centre DEC",
                        value = "${formatAstrometryNumber(demoM103State.dec, 6)}°"
                    )

                    AstrometryValue(
                        label = "Échelle",
                        value =
                            "${formatAstrometryNumber(demoM103State.pixelScaleArcsec, 3)} arcsec/pixel"
                    )

                    demoM103State.solveDurationMs?.let { duration ->
                        AstrometryValue(
                            label = "Durée",
                            value = String.format(
                                Locale.US,
                                "%.2f s",
                                duration / 1000.0
                            )
                        )
                    }
                }

                demoM103State.solveStatus == "error" -> {
                    Text(
                        "Échec de la démonstration M103",
                        color = StellarRed,
                        fontWeight = FontWeight.SemiBold
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
        }
    }
}

@Composable
private fun ExposureCard(
    exposureMs: Int,
    exposureIndex: Int,
    loading: Boolean,
    onExposureChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = StellarSurfaceRaised),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                    val index = value
                        .roundToInt()
                        .coerceIn(0, astrometryExposureOptionsMs.lastIndex)

                    onExposureChange(astrometryExposureOptionsMs[index])
                },
                valueRange = 0f..astrometryExposureOptionsMs.lastIndex.toFloat(),
                enabled = !loading
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
}

@Composable
private fun ZoomableAstrometryPreview(
    bitmap: ImageBitmap,
    contentDescription: String,
    showReticle: Boolean
) {
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offsetX by remember(bitmap) { mutableFloatStateOf(0f) }
    var offsetY by remember(bitmap) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ASTROMETRY_PREVIEW_ASPECT)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .pointerInput(bitmap) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = (scale * zoom).coerceIn(1f, ASTROMETRY_MAX_ZOOM)

                    if (nextScale <= 1.01f) {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    } else {
                        scale = nextScale
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
            contentScale = ContentScale.Fit
        )

        if (showReticle) {
            Text(
                text = "+",
                color = StellarOrange,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
        }

        if (scale > 1.01f) {
            Text(
                text = String.format(Locale.FRANCE, "%.1f×", scale),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(
                        StellarBackground.copy(alpha = 0.70f),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                color = StellarText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
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
        colors = CardDefaults.cardColors(containerColor = StellarSurfaceRaised),
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
                            "Résolution en cours avec ${previewState.solver ?: "astrometry.net"}...",
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
                        value = "${formatAstrometryNumber(previewState.dec, 6)}°"
                    )

                    AstrometryValue(
                        label = "Échelle",
                        value =
                            "${formatAstrometryNumber(previewState.pixelScaleArcsec, 3)} arcsec/pixel"
                    )

                    when {
                        previewState.detectedStarCount != null -> {
                            val count = previewState.detectedStarCount ?: 0

                            Text(
                                text = if (count > 0) {
                                    "✓ Étoiles détectées : $count"
                                } else {
                                    "Aucune étoile détectée"
                                },
                                color = if (count > 0) StellarGreen else StellarMuted,
                                fontWeight = FontWeight.SemiBold
                            )

                            previewState.detectedStars.take(10).forEachIndexed { index, star ->
                                val celestial =
                                    if (star.ra != null && star.dec != null) {
                                        " | RA ${formatAstrometryNumber(star.ra, 4)}" +
                                            " DEC ${formatAstrometryNumber(star.dec, 4)}°"
                                    } else {
                                        ""
                                    }

                                Text(
                                    text =
                                        "Étoile ${index + 1}  " +
                                            "X=${formatAstrometryNumber(star.x, 1)}  " +
                                            "Y=${formatAstrometryNumber(star.y, 1)}" +
                                            celestial,
                                    color = StellarMuted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            if (previewState.detectedStars.size > 10) {
                                Text(
                                    text =
                                        "… ${previewState.detectedStars.size - 10} autres étoiles",
                                    color = StellarMuted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        else -> {
                            Text(
                                text =
                                    "Champ localisé. Les coordonnées individuelles des étoiles " +
                                        "ne sont pas encore fournies par le serveur.",
                                color = StellarMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                solveStatus != null -> {
                    val message =
                        when (solveStatus) {
                            "timeout" -> "Caméra : délai d'astrométrie dépassé"
                            "unsolved" -> "Caméra : champ non résolu"
                            "failed" -> "Caméra : échec de la résolution"
                            "error" -> "Caméra : erreur d'astrométrie"
                            else -> "Astrométrie caméra : ${previewState.solveStatus}"
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
private fun AstrometryValue(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = StellarMuted)
        Text(
            text = value,
            color = StellarText,
            fontWeight = FontWeight.SemiBold
        )
    }
}
