package fr.stellarpilot.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import fr.stellarpilot.app.ui.theme.StellarMuted
import fr.stellarpilot.app.ui.theme.StellarOrange


private const val URANUS_C_ASPECT_RATIO = 3856f / 2180f
private const val MAX_PREVIEW_ZOOM = 8f


@Composable
fun StellarImagePreview(
    imageBytes: ByteArray?,
    contentDescription: String,
    loadingText: String? = null,
    emptyText: String = "Aucune image",
    showCrosshair: Boolean = false
) {
    val bitmap = remember(imageBytes) {
        imageBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size
            )?.asImageBitmap()
        }
    }

    var scale by remember(imageBytes) { mutableFloatStateOf(1f) }
    var offset by remember(imageBytes) { mutableStateOf(Offset.Zero) }
    var viewportWidth by remember { mutableFloatStateOf(0f) }
    var viewportHeight by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(URANUS_C_ASPECT_RATIO)
            .background(
                Color.Black,
                RoundedCornerShape(12.dp)
            )
            .onSizeChanged { size ->
                viewportWidth = size.width.toFloat()
                viewportHeight = size.height.toFloat()
            },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(imageBytes) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextScale =
                                (scale * zoom)
                                    .coerceIn(1f, MAX_PREVIEW_ZOOM)

                            if (nextScale <= 1.001f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                val maxX =
                                    viewportWidth * (nextScale - 1f) / 2f
                                val maxY =
                                    viewportHeight * (nextScale - 1f) / 2f

                                offset = Offset(
                                    x = (offset.x + pan.x)
                                        .coerceIn(-maxX, maxX),
                                    y = (offset.y + pan.y)
                                        .coerceIn(-maxY, maxY)
                                )
                                scale = nextScale
                            }
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentScale = ContentScale.Fit
            )

            // Le réticule reste lié au centre de l'écran et ne suit pas
            // le zoom / déplacement de l'image.
            if (showCrosshair) {
                Text(
                    text = "+",
                    color = StellarOrange,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Text(
                text = loadingText ?: emptyText,
                color = StellarMuted
            )
        }
    }
}
