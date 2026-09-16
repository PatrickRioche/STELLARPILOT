package fr.stellarpilot.app.feature.preparation

import androidx.compose.runtime.Composable
import fr.stellarpilot.app.feature.connection.ConnectionViewModel

@Composable
fun PreparationV060HostScreen(
    onOpenSky: () -> Unit,
    connectionViewModel: ConnectionViewModel
) {
    AssistantV069FourStepScreen(
        onOpenSky = onOpenSky,
        connectionViewModel = connectionViewModel
    )
}
