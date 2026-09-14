package fr.stellarpilot.app.feature.preparation

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.stellarpilot.app.feature.connection.ConnectionViewModel
import fr.stellarpilot.app.ui.theme.StellarBackground
import fr.stellarpilot.app.ui.theme.StellarBorder
import fr.stellarpilot.app.ui.theme.StellarMuted
import fr.stellarpilot.app.ui.theme.StellarOrange
import fr.stellarpilot.app.ui.theme.StellarSurface
import fr.stellarpilot.app.ui.theme.StellarText

private const val DEFAULT_SERVER = "10.42.0.1"
private const val PREFS_NAME = "stellarpilot_connection"
private const val PREF_SERVER = "server_base_url"

@Composable
fun PreparationV060HostScreen(
    onOpenSky: () -> Unit,
    connectionViewModel: ConnectionViewModel
) {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var serverAddress by rememberSaveable { mutableStateOf(DEFAULT_SERVER) }

    val state = connectionViewModel.uiState
    val activeAddress = state.serverBaseUrl
        .removePrefix("http://")
        .removePrefix("https://")
        .removeSuffix("/")
        .removeSuffix(":8000")

    fun applyServer(address: String) {
        val clean = address.trim().ifBlank { DEFAULT_SERVER }

        preferences.edit()
            .putString(PREF_SERVER, clean)
            .apply()

        serverAddress = clean
        connectionViewModel.setServerAddress(clean)
    }

    LaunchedEffect(Unit) {
        val saved = preferences.getString(PREF_SERVER, null)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_SERVER

        serverAddress = saved
        connectionViewModel.setServerAddress(saved)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = StellarSurface),
            border = BorderStroke(1.dp, StellarBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Serveur StellarPilot",
                    color = StellarText,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "Adresse active • $activeAddress",
                    color = StellarMuted
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = serverAddress,
                    onValueChange = { serverAddress = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Autre adresse") },
                    supportingText = {
                        Text("Par défaut : 10.42.0.1 • IP ou URL complète acceptée")
                    }
                )

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { applyServer(serverAddress) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StellarOrange,
                        contentColor = StellarBackground
                    )
                ) {
                    Text(
                        "UTILISER CETTE ADRESSE",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AssistantFinalScreen(
                onOpenSky = onOpenSky,
                connectionViewModel = connectionViewModel
            )
        }
    }
}
