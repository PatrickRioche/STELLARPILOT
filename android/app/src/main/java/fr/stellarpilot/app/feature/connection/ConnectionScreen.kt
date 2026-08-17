package fr.stellarpilot.app.feature.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel = viewModel()
) {
    val state = viewModel.uiState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "StellarPilot",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "POC Android → StellarPilot Server",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Backend : ${state.backendMode}")
                Text("Serveur : ${state.serverBaseUrl}")
                Text("REST /status : ${state.restStatus}")
                Text("WebSocket /ws : ${state.webSocketStatus}")

                state.server?.let { server ->
                    Spacer(Modifier.height(12.dp))
                    Text("Service : ${server.service}")
                    Text("État : ${server.status}")
                    Text("POC : ${server.poc}")
                    Text("Monture : ${server.session.mountType ?: "non définie"}")
                }

                state.error?.let { error ->
                    Spacer(Modifier.height(12.dp))
                    Text("Erreur : $error")
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = viewModel::connect,
            enabled = !state.isConnecting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.isConnecting) "Connexion…" else "Tester StellarPilot Server")
        }
    }
}
