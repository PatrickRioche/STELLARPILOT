package fr.stellarpilot.app.feature.preparation

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import fr.stellarpilot.app.ui.theme.StellarMuted
import fr.stellarpilot.app.ui.theme.StellarOrange
import fr.stellarpilot.app.ui.theme.StellarRed
import fr.stellarpilot.app.ui.theme.StellarSurface
import fr.stellarpilot.app.ui.theme.StellarText

@Composable
fun AstrometryStep(
    previewState: CameraPreviewUiState,
    cameraName: String?,
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = StellarSurface
            )
    ) {
        Column(
            modifier =
                Modifier.padding(18.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            Text(
                text = "Premi\u00E8re astrom\u00E9trie",
                style =
                    MaterialTheme.typography.titleLarge,
                fontWeight =
                    FontWeight.Bold,
                color = StellarText
            )

            Text(
                text =
                    "Visualisation de la cam\u00E9ra via INDI",
                color = StellarMuted
            )

            Text(
                text =
                    cameraName
                        ?: "Cam\u00E9ra INDI",
                color = StellarMuted
            )

            Spacer(
                Modifier.height(4.dp)
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
                    previewBitmap != null -> {

                        Image(
                            bitmap = previewBitmap,
                            contentDescription =
                                "Aper\u00E7u cam\u00E9ra",
                            modifier =
                                Modifier.fillMaxSize(),
                            contentScale =
                                ContentScale.Fit
                        )

                        Text(
                            text = "+",
                            color = StellarOrange,
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
                            color = StellarMuted
                        )
                    }

                    else -> {

                        Text(
                            text =
                                "Aucune image cam\u00E9ra",
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

            Spacer(
                Modifier.height(4.dp)
            )

            OutlinedButton(
                onClick = onRefresh,
                enabled =
                    !previewState.isLoading,
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Text(
                    text =
                        if (previewState.isLoading)
                            "Acquisition..."
                        else
                            "Actualiser l'image"
                )
            }

            Button(
                onClick = onNext,
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
                        "Continuer vers l'\u00E9toile",
                    fontWeight =
                        FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = onPrevious,
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Text("Retour")
            }
        }
    }
}