package fr.stellarpilot.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import fr.stellarpilot.app.ui.theme.StellarMuted
import fr.stellarpilot.app.ui.theme.StellarOrange


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

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    Color.Black,
                    RoundedCornerShape(12.dp)
                ),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

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
