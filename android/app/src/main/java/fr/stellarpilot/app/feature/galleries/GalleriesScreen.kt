package fr.stellarpilot.app.feature.galleries

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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


private val GalleryTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.88f),
    offset = Offset(1.2f, 1.2f),
    blurRadius = 4f
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
            modifier = Modifier
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
                onClick = { viewModel.load(serverBaseUrl) },
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
                CircularProgressIndicator(color = StellarOrange)
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
                            onClick = { viewModel.exportSelected(session.id) },
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

        // Le liseré est volontairement assez loin du bord. Les informations
        // sont placées entre le bord de l'image et ce liseré pour ne jamais
        // réduire la surface de l'astrophotographie.
        val borderInset = when {
            fullScreen && compact -> 34.dp
            fullScreen -> 42.dp
            compact -> 32.dp
            else -> 38.dp
        }
        val edgeInset = if (compact) 5.dp else 7.dp
        val headerTopInset = if (compact) 3.dp else 5.dp
        val logoSize = if (compact) 20.dp else 24.dp
        val headerGap = if (compact) 4.dp else 5.dp

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
                    top = headerTopInset,
                    start = edgeInset,
                    end = edgeInset
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.stellarpilot_gallery_logo),
                contentDescription = "Logo StellarPilot",
                modifier = Modifier.size(logoSize),
                contentScale = ContentScale.Fit
            )

            Spacer(Modifier.size(headerGap))

            Text(
                text = "${stellarPilotVersionLabel()} © ${copyrightYear(session)}",
                color = StellarOrange,
                style = galleryTextStyle(
                    MaterialTheme.typography.bodySmall.copy(
                        fontSize = if (compact) 10.sp else 12.sp,
                        lineHeight = if (compact) 11.sp else 13.sp
                    )
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
                    start = edgeInset,
                    bottom = edgeInset
                )
                .widthIn(max = if (compact) 205.dp else 315.dp)
        ) {
            Text(
                text = "Objet : ${session.targetName}",
                color = StellarOrange,
                fontWeight = FontWeight.Bold,
                style = galleryTextStyle(
                    MaterialTheme.typography.bodySmall.copy(
                        fontSize = if (compact) 11.sp else 13.sp,
                        lineHeight = if (compact) 13.sp else 15.sp
                    )
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text =
                    "${session.capturedFrames} captures • ${session.acceptedFrames} stackées",
                color = StellarOrange,
                style = galleryTextStyle(
                    MaterialTheme.typography.bodySmall.copy(
                        fontSize = if (compact) 8.sp else 10.sp,
                        lineHeight = if (compact) 10.sp else 12.sp
                    )
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = edgeInset,
                    bottom = edgeInset
                )
                .widthIn(max = if (compact) 210.dp else 325.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = formatGalleryDate(session.createdAt),
                color = StellarOrange,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                style = galleryTextStyle(
                    MaterialTheme.typography.bodySmall.copy(
                        fontSize = if (compact) 9.sp else 11.sp,
                        lineHeight = if (compact) 11.sp else 13.sp
                    )
                ),
                maxLines = 1
            )
            Text(
                text = formatGalleryLocation(session),
                color = StellarOrange,
                style = galleryTextStyle(
                    MaterialTheme.typography.bodySmall.copy(
                        fontSize = if (compact) 8.sp else 10.sp,
                        lineHeight = if (compact) 10.sp else 12.sp
                    )
                ),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
            modifier = Modifier
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
