package fr.stellarpilot.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val StellarBackground = Color(0xFF06111F)
val StellarSurface = Color(0xFF0C1B2E)
val StellarSurfaceRaised = Color(0xFF10243D)
val StellarBorder = Color(0xFF203B59)

val StellarOrange = Color(0xFFFF8A3D)
val StellarBlue = Color(0xFF5EA8FF)
val StellarGreen = Color(0xFF35D07F)
val StellarRed = Color(0xFFFF6470)

val StellarText = Color(0xFFF4F7FB)
val StellarMuted = Color(0xFF91A5BC)

private val StellarDarkColors = darkColorScheme(
    primary = StellarOrange,
    onPrimary = StellarBackground,

    secondary = StellarBlue,
    onSecondary = StellarBackground,

    background = StellarBackground,
    onBackground = StellarText,

    surface = StellarSurface,
    onSurface = StellarText,

    surfaceVariant = StellarSurfaceRaised,
    onSurfaceVariant = StellarMuted,

    error = StellarRed,
    onError = StellarText
)

@Composable
fun StellarPilotTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StellarDarkColors,
        content = content
    )
}