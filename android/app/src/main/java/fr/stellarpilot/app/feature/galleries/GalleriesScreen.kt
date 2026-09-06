package fr.stellarpilot.app.feature.galleries

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.stellarpilot.app.ui.theme.StellarBackground
import fr.stellarpilot.app.ui.theme.StellarBorder
import fr.stellarpilot.app.ui.theme.StellarMuted
import fr.stellarpilot.app.ui.theme.StellarOrange
import fr.stellarpilot.app.ui.theme.StellarRed
import fr.stellarpilot.app.ui.theme.StellarSurface
import fr.stellarpilot.app.ui.theme.StellarText
import java.util.Locale


@Composable
fun GalleriesScreen(
    serverBaseUrl: String,
    viewModel: GalleriesViewModel = viewModel()
) {
    val state = viewModel.uiState

    LaunchedEffect(serverBaseUrl) {
        viewModel.load(serverBaseUrl)
    }

    val bitmap = remember(state.previewBytes) {
        state.previewBytes?.let { bytes ->
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
                text = "GALERIES",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = StellarOrange
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Sessions d'observation",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = StellarText
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text =
                    "Stacks finalisés, temps d'intégration et paramètres d'acquisition.",
                color = StellarMuted
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    viewModel.load(serverBaseUrl)
                },
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StellarOrange,
                    contentColor = StellarBackground
                )
            ) {
                Text(
                    text = "ACTUALISER",
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))

            if (state.isLoading) {
                CircularProgressIndicator(
                    color = StellarOrange
                )
                Spacer(Modifier.height(12.dp))
            }

            if (!state.isLoading && state.sessions.isEmpty()) {
                GalleryCard {
                    Text(
                        text = "Aucune session finalisée",
                        color = StellarMuted
                    )
                }
            }

            state.sessions.forEach { session ->
                GalleryCard {
                    Text(
                        text = session.targetName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = StellarText
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = session.createdAt,
                        color = StellarMuted,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text =
                            "${session.acceptedFrames} poses × ${format(session.exposureSeconds, 1)} s • ${formatDuration(session.integrationSeconds)}",
                        color = StellarText
                    )

                    Text(
                        text =
                            "${session.acceptedFrames} acceptées • ${session.rejectedFrames} rejetées",
                        color = StellarMuted
                    )

                    Spacer(Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.open(
                                serverBaseUrl,
                                session.id
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("VOIR LE STACK")
                    }

                    if (
                        state.selectedSessionId == session.id &&
                        bitmap != null
                    ) {
                        Spacer(Modifier.height(12.dp))
                        Image(
                            bitmap = bitmap,
                            contentDescription =
                                "Stack ${session.targetName}",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.FillWidth
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
            }

            state.error?.let {
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
private fun GalleryCard(
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


private fun format(
    value: Double,
    decimals: Int
): String =
    String.format(
        Locale.FRANCE,
        "%.${decimals}f",
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
