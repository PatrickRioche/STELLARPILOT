package fr.stellarpilot.app.feature.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel = viewModel()
) {
    val state = viewModel.uiState

    LaunchedEffect(Unit) {
        viewModel.connect()
    }

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
            text = "POC SIMULATION",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                val server = state.server

                if (server == null) {
                    Text(
                        if (state.isConnecting)
                            "Connexion au serveur..."
                        else
                            "Serveur non connecté"
                    )
                } else {
                    StatusRow(
                        label = "Serveur",
                        value = server.devices.server.status
                    )

                    StatusRow(
                        label = "Monture",
                        value = server.devices.mount.status
                    )

                    StatusRow(
                        label = "Caméra",
                        value = server.devices.camera.status
                    )

                    StatusRow(
                        label = "GPS",
                        value = server.devices.gps.status
                    )

                    Spacer(Modifier.height(16.dp))

                    server.devices.mount.name?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    server.devices.camera.name?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    val gps = server.devices.gps

                    if (gps.latitude != null && gps.longitude != null) {
                        Text(
                            text = "GPS : ${gps.latitude}, ${gps.longitude}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                state.error?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Erreur : $it",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = viewModel::connect,
            enabled = !state.isConnecting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (state.isConnecting)
                    "Connexion..."
                else
                    "Actualiser"
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "${state.backendMode} • ${state.serverBaseUrl}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = statusColor(value),
                    shape = CircleShape
                )
        )

        Text(
            text = label,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f)
        )

        Text(
            text = value.uppercase()
        )
    }
}

private fun statusColor(status: String): Color =
    when (status.lowercase()) {
        "online",
        "ready",
        "fix",
        "ok" -> Color(0xFF35D07F)

        else -> Color(0xFFFFB74D)
    }
