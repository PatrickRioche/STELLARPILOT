package fr.stellarpilot.app.feature.preparation

import android.graphics.BitmapFactory
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
import androidx.compose.runtime.remember
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
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Plage d'exposition :
 *
 * 1 à 99 ms       : pas de 1 ms
 * 100 à 990 ms    : pas de 10 ms
 * 1 à 10 secondes : pas de 100 ms
 */
private val astrometryExposureOptionsMs =
    buildList {
        for (value in 1..99) {
            add(value)
        }

        for (value in 100..990 step 10) {
            add(value)
        }

        for (value in 1000..10000 step 100) {
            add(value)
        }
    }

private fun formatExposure(
    milliseconds: Int
): String =
    if (milliseconds < 1000) {
        "$milliseconds ms"
    } else {
        String.format(
            Locale.US,
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
            Locale.US,
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

    val demoM103Bitmap =
        remember(demoM103State.imageBytes) {

            demoM103State.imageBytes?.let { bytes ->

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

    val displayedBitmap = previewBitmap

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

    val canContinue =
        if (demoMode)
            demoSolveSucceeded
        else
            solveSucceeded

    Card(
        modifier =
            Modifier.fillMaxWidth(),

        shape =
            RoundedCornerShape(18.dp),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    StellarSurface
            )
    ) {

        Column(
            modifier =
                Modifier.padding(18.dp),

            verticalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            Text(
                text =
                    "Première astrométrie",

                style =
                    MaterialTheme
                        .typography
                        .titleLarge,

                fontWeight =
                    FontWeight.Bold,

                color =
                    StellarText
            )

            Text(
                text =
                    "Capture et résolution du champ avec astrometry.net",

                color =
                    StellarMuted
            )

            Text(
                text =
                    cameraName ?: "Caméra INDI",

                color =
                    StellarMuted
            )

            Spacer(
                Modifier.height(6.dp)
            )

            /*
             * Démonstration astrométrique M103.
             *
             * Cette démonstration est indépendante de la
             * validation de l'astrométrie utilisateur.
             */
            Card(
                modifier =
                    Modifier.fillMaxWidth(),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            StellarSurfaceRaised
                    ),

                shape =
                    RoundedCornerShape(12.dp)
            ) {

                Column(
                    modifier =
                        Modifier.padding(14.dp),

                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    Text(
                        text =
                            "Démonstration M103",

                        color =
                            StellarText,

                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        text =
                            "Vérifie le fonctionnement du solveur " +
                                "astrometry.net avec une image FITS " +
                                "astronomique de référence.",

                        color =
                            StellarMuted,

                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
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
                        contentAlignment =
                            Alignment.Center
                    ) {
                        when {
                            demoMode -> {
                                Image(
                                    painter = painterResource(
                                        id = R.drawable.m103_preview
                                    ),
                                    contentDescription =
                                        "M103 - image locale de demonstration",
                                    modifier =
                                        Modifier.fillMaxSize(),
                                    contentScale =
                                        ContentScale.Fit
                                )
                            }

                            demoM103Bitmap != null -> {
                                Image(
                                    bitmap = demoM103Bitmap,
                                    contentDescription =
                                        "M103 - image recue du serveur",
                                    modifier =
                                        Modifier.fillMaxSize(),
                                    contentScale =
                                        ContentScale.Fit
                                )
                            }

                            else -> {
                                Text(
                                    text =
                                        "L'image M103 sera chargee depuis le serveur lors du test.",
                                    color =
                                        StellarMuted,
                                    style =
                                        MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    Spacer(
                        Modifier.height(4.dp)
                    )

                    if (!demoMode) {
                    OutlinedButton(
                        onClick =
                            onRunDemoM103,

                        enabled =
                            !demoM103State.isLoading &&
                                !previewState.isLoading,

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Text(
                            text =
                                when {
                                    demoM103State.isLoading ->
                                        "Résolution M103 en cours..."

                                    demoM103State.solveStatus != null ->
                                        "↻ Relancer le test serveur M103"

                                    else ->
                                        "▶ Tester l'astrométrie serveur avec M103"
                                }
                        )
                    }
                    }

                    when {

                        demoM103State.solveStatus == "solving" ||
                            demoM103State.solveStatus == "loading" -> {

                            Text(
                                text =
                                    "Analyse du champ M103 avec astrometry.net...",

                                color =
                                    StellarOrange
                            )
                        }

                        demoM103State.solveStatus == "solved" -> {

                            Text(
                                text =
                                    "✓ Démonstration réussie",

                                color =
                                    StellarGreen,

                                fontWeight =
                                    FontWeight.Bold
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
                                    "${
                                        formatAstrometryNumber(
                                            demoM103State.dec,
                                            6
                                        )
                                    }°"
                            )

                            AstrometryValue(
                                label = "Orientation",
                                value =
                                    "${
                                        formatAstrometryNumber(
                                            demoM103State.orientationDeg,
                                            2
                                        )
                                    }°"
                            )

                            AstrometryValue(
                                label = "Échelle",
                                value =
                                    "${
                                        formatAstrometryNumber(
                                            demoM103State.pixelScaleArcsec,
                                            3
                                        )
                                    } arcsec/pixel"
                            )

                            demoM103State
                                .solveDurationMs
                                ?.let { duration ->

                                    AstrometryValue(
                                        label = "Durée",
                                        value =
                                            String.format(
                                                Locale.US,
                                                "%.2f s",
                                                duration / 1000.0
                                            )
                                    )
                                }
                        }

                        demoM103State.solveStatus == "error" -> {

                            Text(
                                text =
                                    "Échec de la démonstration M103",

                                color =
                                    StellarRed,

                                fontWeight =
                                    FontWeight.SemiBold
                            )
                        }
                    }


                    demoM103State.error?.let { error ->

                        Text(
                            text = error,
                            color = StellarRed,
                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall
                        )
                    }
                }
            }


            if (!demoMode) {
                Spacer(
                    Modifier.height(8.dp)
                )

                Text(
                    text =
                        "Votre capture caméra",

                color =
                    StellarText,

                fontWeight =
                    FontWeight.Bold
            )

            Text(
                text =
                    "Réglez l'exposition puis réalisez votre propre " +
                        "capture. Seule cette astrométrie permet de continuer.",

                color =
                    StellarMuted,

                style =
                    MaterialTheme
                        .typography
                        .bodySmall
            )

            Spacer(
                Modifier.height(4.dp)
            )

            /*
             * Réglage du temps d'exposition.
             */
            Card(
                modifier =
                    Modifier.fillMaxWidth(),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            StellarSurfaceRaised
                    ),

                shape =
                    RoundedCornerShape(12.dp)
            ) {

                Column(
                    modifier =
                        Modifier.padding(14.dp)
                ) {

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.SpaceBetween,

                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Text(
                            text =
                                "Temps d'exposition",

                            color =
                                StellarText,

                            fontWeight =
                                FontWeight.SemiBold
                        )

                        Text(
                            text =
                                formatExposure(
                                    exposureMs
                                ),

                            color =
                                StellarOrange,

                            fontWeight =
                                FontWeight.Bold
                        )
                    }

                    Slider(
                        value =
                            exposureIndex.toFloat(),

                        onValueChange = { value ->

                            val index =
                                value
                                    .roundToInt()
                                    .coerceIn(
                                        0,
                                        astrometryExposureOptionsMs
                                            .lastIndex
                                    )

                            onExposureChange(
                                astrometryExposureOptionsMs[
                                    index
                                ]
                            )
                        },

                        valueRange =
                            0f..
                                astrometryExposureOptionsMs
                                    .lastIndex
                                    .toFloat(),

                        enabled =
                            !previewState.isLoading
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {

                        Text(
                            text = "1 ms",
                            color = StellarMuted,
                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall
                        )

                        Text(
                            text = "10 s",
                            color = StellarMuted,
                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall
                        )
                    }
                }
            }

            Spacer(
                Modifier.height(6.dp)
            )

            /*
             * Aperçu caméra.
             */
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(
                            Color.Black,
                            RoundedCornerShape(12.dp)
                        ),

                contentAlignment =
                    Alignment.Center
            ) {

                when {

                    demoMode -> {
                        Image(
                            painter = painterResource(
                                id = R.drawable.m103_preview
                            ),
                            contentDescription =
                                "M103 - image de demonstration",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    displayedBitmap != null -> {

                        Image(
                            bitmap =
                                displayedBitmap,

                            contentDescription =
                                "Aperçu caméra",

                            modifier =
                                Modifier.fillMaxSize(),

                            contentScale =
                                ContentScale.Fit
                        )

                        /*
                         * Réticule central.
                         */
                        Text(
                            text = "+",

                            color =
                                StellarOrange,

                            style =
                                MaterialTheme
                                    .typography
                                    .headlineLarge,

                            fontWeight =
                                FontWeight.Bold
                        )
                    }

                    previewState.isLoading -> {

                        Text(
                            text =
                                "Acquisition de l'image...",

                            color =
                                StellarMuted
                        )
                    }

                    else -> {

                        Text(
                            text =
                                "Aucune image caméra",

                            color =
                                StellarMuted
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

            Spacer(
                Modifier.height(6.dp)
            )

            /*
             * Résultat ASTROMÉTRIE.
             */
            Card(
                modifier =
                    Modifier.fillMaxWidth(),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            StellarSurfaceRaised
                    ),

                shape =
                    RoundedCornerShape(12.dp)
            ) {

                Column(
                    modifier =
                        Modifier.padding(14.dp),

                    verticalArrangement =
                        Arrangement.spacedBy(7.dp)
                ) {

                    Text(
                        text =
                            "Dernière astrométrie de votre caméra",

                        color =
                            StellarText,

                        fontWeight =
                            FontWeight.Bold
                    )

                    when {

                        solveStatus == "solving" -> {

                            Text(
                                text =
                                    "Résolution en cours avec ${
                                        previewState.solver
                                            ?: "astrometry.net"
                                    }...",

                                color =
                                    StellarOrange
                            )
                        }

                        solveSucceeded -> {

                            Text(
                                text =
                                    "✓ Champ céleste localisé",

                                color =
                                    StellarGreen,

                                fontWeight =
                                    FontWeight.Bold
                            )

                            AstrometryValue(
                                label =
                                    "Solveur",

                                value =
                                    previewState.solver
                                        ?: "astrometry.net"
                            )

                            AstrometryValue(
                                label =
                                    "Centre RA",

                                value =
                                    formatAstrometryNumber(
                                        previewState.ra,
                                        6
                                    )
                            )

                            AstrometryValue(
                                label =
                                    "Centre DEC",

                                value =
                                    "${
                                        formatAstrometryNumber(
                                            previewState.dec,
                                            6
                                        )
                                    }°"
                            )

                            AstrometryValue(
                                label =
                                    "Orientation",

                                value =
                                    "${
                                        formatAstrometryNumber(
                                            previewState.orientationDeg,
                                            2
                                        )
                                    }°"
                            )

                            AstrometryValue(
                                label =
                                    "Échelle",

                                value =
                                    "${
                                        formatAstrometryNumber(
                                            previewState.pixelScaleArcsec,
                                            3
                                        )
                                    } arcsec/pixel"
                            )

                            Spacer(
                                Modifier.height(4.dp)
                            )

                            /*
                             * Étoiles détectées.
                             */
                            when {

                                previewState
                                    .detectedStarCount != null -> {

                                    val count =
                                        previewState
                                            .detectedStarCount
                                            ?: 0

                                    Text(
                                        text =
                                            if (count > 0) {
                                                "✓ Étoiles détectées : $count"
                                            } else {
                                                "Aucune étoile détectée"
                                            },

                                        color =
                                            if (count > 0) {
                                                StellarGreen
                                            } else {
                                                StellarMuted
                                            },

                                        fontWeight =
                                            FontWeight.SemiBold
                                    )

                                    /*
                                     * On limite volontairement
                                     * l'affichage aux 10 premières.
                                     */
                                    previewState
                                        .detectedStars
                                        .take(10)
                                        .forEachIndexed {
                                                index,
                                                star ->

                                            val celestial =
                                                if (
                                                    star.ra != null &&
                                                    star.dec != null
                                                ) {

                                                    " | RA ${
                                                        formatAstrometryNumber(
                                                            star.ra,
                                                            4
                                                        )
                                                    }" +
                                                        " DEC ${
                                                            formatAstrometryNumber(
                                                                star.dec,
                                                                4
                                                            )
                                                        }°"

                                                } else {
                                                    ""
                                                }

                                            Text(
                                                text =
                                                    "Étoile ${index + 1}  " +
                                                        "X=${
                                                            formatAstrometryNumber(
                                                                star.x,
                                                                1
                                                            )
                                                        }  " +
                                                        "Y=${
                                                            formatAstrometryNumber(
                                                                star.y,
                                                                1
                                                            )
                                                        }" +
                                                        celestial,

                                                color =
                                                    StellarMuted,

                                                style =
                                                    MaterialTheme
                                                        .typography
                                                        .bodySmall
                                            )
                                        }

                                    if (
                                        previewState
                                            .detectedStars
                                            .size > 10
                                    ) {

                                        Text(
                                            text =
                                                "… ${
                                                    previewState
                                                        .detectedStars
                                                        .size - 10
                                                } autres étoiles",

                                            color =
                                                StellarMuted,

                                            style =
                                                MaterialTheme
                                                    .typography
                                                    .bodySmall
                                        )
                                    }
                                }

                                else -> {

                                    Text(
                                        text =
                                            "Champ localisé. " +
                                                "Les coordonnées individuelles " +
                                                "des étoiles ne sont pas encore " +
                                                "fournies par le serveur.",

                                        color =
                                            StellarMuted,

                                        style =
                                            MaterialTheme
                                                .typography
                                                .bodySmall
                                    )
                                }
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
                                text =
                                    message,

                                color =
                                    StellarRed,

                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            previewState
                                .solveDetail
                                ?.let { detail ->

                                    Text(
                                        text =
                                            detail,

                                        color =
                                            StellarMuted,

                                        style =
                                            MaterialTheme
                                                .typography
                                                .bodySmall
                                    )
                                }
                        }

                        else -> {

                            Text(
                                text =
                                    "Aucune astrométrie caméra pour le moment",

                                color =
                                    StellarMuted
                            )
                        }
                    }
                }
            }

            Spacer(
                Modifier.height(4.dp)
            )

            OutlinedButton(
                onClick =
                    onRefresh,

                enabled =
                    !demoMode &&
                        !previewState.isLoading,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        if (
                            previewState.isLoading
                        ) {
                            "Acquisition / astrométrie..."
                        } else {
                            "Nouvelle capture et astrométrie"
                        }
                )
            }

            }

            if (!demoMode) {
                Button(
                    onClick =
                        onNext,

                    enabled =
                        canContinue,

                modifier =
                    Modifier.fillMaxWidth(),

                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            StellarOrange,

                        contentColor =
                            StellarBackground
                    )
            ) {

                Text(
                    text =
                        if (canContinue) {
                            if (demoMode)
                                "Continuer la d\u00E9monstration"
                            else
                                "Continuer vers l'\u00E9toile"
                        } else {
                            if (demoMode)
                                "R\u00E9soudre M103 pour continuer"
                            else
                                "Astrom\u00E9trie requise"
                        },

                    fontWeight =
                        FontWeight.Bold
                )
                }
            }

            OutlinedButton(
                onClick =
                    onPrevious,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text = "Retour"
                )
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
        modifier =
            Modifier.fillMaxWidth(),

        horizontalArrangement =
            Arrangement.SpaceBetween
    ) {

        Text(
            text =
                label,

            color =
                StellarMuted
        )

        Text(
            text =
                value,

            color =
                StellarText,

            fontWeight =
                FontWeight.SemiBold
        )
    }
}
