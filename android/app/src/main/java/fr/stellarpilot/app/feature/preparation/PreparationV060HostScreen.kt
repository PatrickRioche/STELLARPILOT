package fr.stellarpilot.app.feature.preparation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.weight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.stellarpilot.app.feature.connection.ConnectionViewModel

@Composable
fun PreparationV060HostScreen(
    onOpenSky: () -> Unit,
    connectionViewModel: ConnectionViewModel
) {
    Column(modifier = Modifier.fillMaxSize()) {
        FlatCalibrationPanel(
            serverBaseUrl = connectionViewModel.uiState.serverBaseUrl
        )

        Box(modifier = Modifier.weight(1f)) {
            AssistantV069FourStepScreen(
                onOpenSky = onOpenSky,
                connectionViewModel = connectionViewModel
            )
        }
    }
}
