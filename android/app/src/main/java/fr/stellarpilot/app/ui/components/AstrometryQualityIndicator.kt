package fr.stellarpilot.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.stellarpilot.app.ui.theme.StellarGreen
import fr.stellarpilot.app.ui.theme.StellarMuted
import fr.stellarpilot.app.ui.theme.StellarOrange
import fr.stellarpilot.app.ui.theme.StellarRed
import fr.stellarpilot.app.ui.theme.StellarSurfaceRaised
import fr.stellarpilot.app.ui.theme.StellarText
import java.util.Locale


@Composable
fun AstrometryQualityIndicator(
    score: Int?,
    label: String?,
    starCount: Int?,
    saturatedPercent: Double?,
    classification: String?
) {
    val effectiveScore = score?.coerceIn(0, 100)
    val accent: Color =
        when {
            effectiveScore == null -> StellarMuted
            effectiveScore >= 75 -> StellarGreen
            effectiveScore >= 25 -> StellarOrange
            else -> StellarRed
        }

    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Qualité astrométrique",
                color = StellarText,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text =
                    if (effectiveScore != null) "$effectiveScore / 100"
                    else "— / 100",
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }

        LinearProgressIndicator(
            progress = (effectiveScore ?: 0) / 100f,
            modifier = Modifier.fillMaxWidth(),
            color = accent,
            trackColor = StellarSurfaceRaised
        )

        Text(
            text =
                when {
                    label != null -> "● ${label.uppercase(Locale.FRANCE)}"
                    effectiveScore != null -> "● ANALYSE TERMINÉE"
                    else -> "Analyse en attente"
                },
            color = accent,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Sources : ${starCount ?: 0}",
                color = StellarMuted,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text =
                    saturatedPercent?.let {
                        String.format(
                            Locale.FRANCE,
                            "Saturation : %.3f %%",
                            it
                        )
                    } ?: "Saturation : —",
                color = StellarMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }

        val guidance =
            when (classification) {
                "astrometry_ready" ->
                    "✓ Image adaptée à l'astrométrie"
                "overexposed" ->
                    "Réduire l'exposition avant l'astrométrie"
                "insufficient_stars" ->
                    "Augmenter l'exposition pour détecter davantage de sources"
                else -> null
            }

        guidance?.let {
            Spacer(Modifier.height(1.dp))
            Text(
                text = it,
                color = accent,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
