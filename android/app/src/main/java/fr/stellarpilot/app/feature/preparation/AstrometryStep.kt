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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    previewState: CameraPreviewUiState,
    cameraName: String?,
    exposureMs: Int,
    onExposureChange: (Int) -> Unit,
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

                    previewBitmap != null -> {

                        Image(
                            bitmap =
                                previewBitmap,

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
                            "Résultat astrométrique",

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
                                        "Échec : délai d'astrométrie dépassé"

                                    "unsolved" ->
                                        "Champ non résolu"

                                    "failed" ->
                                        "Échec de la résolution"

                                    "error" ->
                                        "Erreur d'astrométrie"

                                    else ->
                                        "Astrométrie : ${previewState.solveStatus}"
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
                                    "Aucun résultat pour le moment",

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

            Button(
                onClick =
                    onNext,

                enabled =
                    solveSucceeded,

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
                        if (solveSucceeded) {
                            "Continuer vers l'étoile"
                        } else {
                            "Astrométrie requise"
                        },

                    fontWeight =
                        FontWeight.Bold
                )
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
