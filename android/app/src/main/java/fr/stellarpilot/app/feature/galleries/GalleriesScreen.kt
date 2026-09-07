package fr.stellarpilot.app.feature.galleries

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.stellarpilot.app.R
import fr.stellarpilot.app.data.remote.GallerySession
import fr.stellarpilot.app.ui.components.StellarImagePreview
import fr.stellarpilot.app.ui.theme.StellarBackground
import fr.stellarpilot.app.ui.theme.StellarBorder
import fr.stellarpilot.app.ui.theme.StellarMuted
import fr.stellarpilot.app.ui.theme.StellarOrange
import fr.stellarpilot.app.ui.theme.StellarRed
import fr.stellarpilot.app.ui.theme.StellarSurface
import fr.stellarpilot.app.ui.theme.StellarText
import java.time.Year
import java.util.Locale


private val GalleryOverlayBackground = Color(0xA806111F)


@Composable
fun GalleriesScreen(
    serverBaseUrl: String,
    viewModel: GalleriesViewModel = viewModel()
) {
    val state = viewModel.uiState
    var fullScreen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(serverBaseUrl) {
        viewModel.load(serverBaseUrl)
    }

    BackHandler(enabled = fullScreen) {
        fullScreen = false
    }

    val selectedSession = state.sessions.firstOrNull {
        it.id == state.selectedSessionId
    }

    if (
        fullScreen &&
        state.previewBytes != null &&
        selectedSession != null
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = StellarBackground
        ) {
            BrandedGalleryViewer(
                session = selectedSession,
                imageBytes = state.previewBytes,
                fullScreen = true,
                onTap = { fullScreen = false }
            )
        }
        return
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
                        horizontal = 12.dp,
                        vertical = 16.dp
                    )
        ) {
            Text(
                text = "GALERIES",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = StellarOrange
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Sessions d'observation",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = StellarText
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text =
                    "Stacks finalisés, zoomables et exportables dans la galerie de la tablette ou du téléphone.",
                color = StellarMuted
            )

            Spacer(Modifier.height(12.dp))

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

            Spacer(Modifier.height(12.dp))

            if (state.isLoading) {
                CircularProgressIndicator(
                    color = StellarOrange
                )
                Spacer(Modifier.height(10.dp))
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
                        color = StellarOrange
                    )

                    Spacer(Modifier.height(3.dp))

                    Text(
                        text =
                            "${session.acceptedFrames} poses × ${format(session.exposureSeconds, 1)} s • ${formatDuration(session.integrationSeconds)}",
                        color = StellarText
                    )

                    Text(
                        text =
                            "${session.capturedFrames} capturées • ${session.acceptedFrames} stackées • ${session.rejectedFrames} rejetées",
                        color = StellarMuted
                    )

                    Spacer(Modifier.height(8.dp))

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
                        state.previewBytes != null
                    ) {
                        Spacer(Modifier.height(10.dp))

                        BrandedGalleryViewer(
                            session = session,
                            imageBytes = state.previewBytes,
                            fullScreen = false,
                            onTap = { fullScreen = true }
                        )

                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = {
                                viewModel.exportSelected(session.id)
                            },
                            enabled = !state.isExporting,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StellarOrange,
                                contentColor = StellarBackground
                            )
                        ) {
                            Text(
                                if (state.isExporting) {
                                    "ENREGISTREMENT…"
                                } else {
                                    "ENREGISTRER DANS LA GALERIE"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }

                        state.exportMessage?.let { message ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = message,
                                color = StellarOrange,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
            }

            state.error?.let {
                Text(
                    text = it,
                    color = StellarRed
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}


@Composable
private fun BrandedGalleryViewer(
    session: GallerySession,
    imageBytes: ByteArray,
    fullScreen: Boolean,
    onTap: () -> Unit
) {
    BoxWithConstraints(
        modifier =
            if (fullScreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier.fillMaxWidth()
            }
    ) {
        val compact = maxWidth < 520.dp
        val borderInset = if (fullScreen) 8.dp else 5.dp
        val logoSize = if (compact) 34.dp else 42.dp
        val chipPaddingHorizontal = if (compact) 7.dp else 10.dp
        val chipPaddingVertical = if (compact) 4.dp else 6.dp

        StellarImagePreview(
            imageBytes = imageBytes,
            contentDescription = "Stack ${session.targetName}",
            modifier = if (fullScreen) Modifier.fillMaxSize() else Modifier,
            fullScreen = fullScreen,
            onTap = onTap
        )

        // Le liseré est volontairement en retrait : l'image continue derrière
        // lui afin de maximiser la surface utile et de créer la signature
        // visuelle StellarPilot.
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(borderInset)
                .border(
                    width = 1.dp,
                    color = StellarOrange,
                    shape = RoundedCornerShape(10.dp)
                )
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                .background(GalleryOverlayBackground)
                .padding(
                    horizontal = chipPaddingHorizontal,
                    vertical = 3.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = "Logo StellarPilot",
                modifier = Modifier.size(logoSize),
                contentScale = ContentScale.Fit
            )

            Text(
                text = "${stellarPilotVersionLabel()} © ${copyrightYear(session)}",
                color = StellarOrange,
                style =
                    if (compact) {
                        MaterialTheme.typography.bodySmall
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .widthIn(max = if (compact) 210.dp else 320.dp)
                .clip(RoundedCornerShape(topEnd = 10.dp))
                .background(GalleryOverlayBackground)
                .padding(
                    horizontal = chipPaddingHorizontal,
                    vertical = chipPaddingVertical
                )
        ) {
            Text(
                text = "Objet : ${session.targetName}",
                color = StellarOrange,
                fontWeight = FontWeight.Bold,
                style =
                    if (compact) {
                        MaterialTheme.typography.bodyMedium
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text =
                    "${session.capturedFrames} capturées • ${session.acceptedFrames} stackées",
                color = StellarOrange,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .widthIn(max = if (compact) 220.dp else 340.dp)
                .clip(RoundedCornerShape(topStart = 10.dp))
                .background(GalleryOverlayBackground)
                .padding(
                    horizontal = chipPaddingHorizontal,
                    vertical = chipPaddingVertical
                ),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = formatGalleryDate(session.createdAt),
                color = StellarOrange,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                style =
                    if (compact) {
                        MaterialTheme.typography.bodySmall
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                maxLines = 1
            )
            Text(
                text = formatGalleryLocation(session),
                color = StellarOrange,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


@Composable
private fun GalleryCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = StellarSurface
        ),
        border = BorderStroke(1.dp, StellarBorder)
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
        ) {
            content()
        }
    }
}


private fun copyrightYear(session: GallerySession): Int {
    return session.createdAt
        .take(4)
        .toIntOrNull()
        ?: Year.now().value
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
