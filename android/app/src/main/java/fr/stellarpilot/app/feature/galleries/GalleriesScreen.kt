package fr.stellarpilot.app.feature.galleries

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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


private val GalleryTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.92f),
    offset = Offset(1.5f, 1.5f),
    blurRadius = 5f
)


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
        val borderInset = when {
            fullScreen && compact -> 16.dp
            fullScreen -> 22.dp
            compact -> 10.dp
            else -> 14.dp
        }
        val contentInset = borderInset + if (compact) 7.dp else 10.dp
        val logoSize = if (compact) 28.dp else 36.dp

        StellarImagePreview(
            imageBytes = imageBytes,
            contentDescription = "Stack ${session.targetName}",
            modifier = if (fullScreen) Modifier.fillMaxSize() else Modifier,
            fullScreen = fullScreen,
            onTap = onTap
        )

        Canvas(
            modifier = Modifier.matchParentSize()
        ) {
            val insetPx = borderInset.toPx()
            val radiusPx = (if (compact) 10.dp else 14.dp).toPx()
            val strokePx = (if (compact) 1.dp else 1.25.dp).toPx()
            drawRoundRect(
                color = StellarOrange,
                topLeft = Offset(insetPx, insetPx),
                size = Size(
                    width = (size.width - 2f * insetPx).coerceAtLeast(1f),
                    height = (size.height - 2f * insetPx).coerceAtLeast(1f)
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                    radiusPx,
                    radiusPx
                ),
                style = Stroke(width = strokePx)
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(
                    top = contentInset,
                    start = contentInset,
                    end = contentInset
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StellarPilotOrangeMark(
                modifier = Modifier.size(logoSize)
            )

            Spacer(Modifier.size(if (compact) 5.dp else 7.dp))

            Text(
                text = "${stellarPilotVersionLabel()} © ${copyrightYear(session)}",
                color = StellarOrange,
                style = galleryTextStyle(
                    if (compact) {
                        MaterialTheme.typography.bodySmall
                    } else {
                        MaterialTheme.typography.bodyMedium
                    }
                ),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = contentInset,
                    bottom = contentInset
                )
                .widthIn(max = if (compact) 220.dp else 330.dp)
        ) {
            Text(
                text = "Objet : ${session.targetName}",
                color = StellarOrange,
                fontWeight = FontWeight.Bold,
                style = galleryTextStyle(
                    if (compact) {
                        MaterialTheme.typography.bodyMedium
                    } else {
                        MaterialTheme.typography.titleMedium
                    }
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text =
                    "${session.capturedFrames} captures • ${session.acceptedFrames} stackées",
                color = StellarOrange,
                style = galleryTextStyle(MaterialTheme.typography.bodySmall),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = contentInset,
                    bottom = contentInset
                )
                .widthIn(max = if (compact) 230.dp else 350.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = formatGalleryDate(session.createdAt),
                color = StellarOrange,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                style = galleryTextStyle(
                    if (compact) {
                        MaterialTheme.typography.bodySmall
                    } else {
                        MaterialTheme.typography.bodyMedium
                    }
                ),
                maxLines = 1
            )
            Text(
                text = formatGalleryLocation(session),
                color = StellarOrange,
                style = galleryTextStyle(MaterialTheme.typography.bodySmall),
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


@Composable
private fun StellarPilotOrangeMark(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val shortEdge = minOf(size.width, size.height)
        val center = Offset(
            x = size.width * 0.46f,
            y = size.height * 0.56f
        )
        val stroke = Stroke(width = shortEdge * 0.085f)

        rotate(
            degrees = -18f,
            pivot = center
        ) {
            drawOval(
                color = StellarOrange,
                topLeft = Offset(
                    x = size.width * 0.08f,
                    y = size.height * 0.38f
                ),
                size = Size(
                    width = size.width * 0.76f,
                    height = size.height * 0.34f
                ),
                style = stroke
            )
        }

        drawCircle(
            color = StellarOrange,
            radius = shortEdge * 0.18f,
            center = center
        )

        drawCircle(
            color = StellarOrange,
            radius = shortEdge * 0.055f,
            center = Offset(
                x = size.width * 0.80f,
                y = size.height * 0.17f
            )
        )
    }
}


private fun galleryTextStyle(base: TextStyle): TextStyle {
    return base.copy(shadow = GalleryTextShadow)
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
