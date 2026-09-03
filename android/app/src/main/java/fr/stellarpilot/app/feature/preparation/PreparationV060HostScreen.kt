package fr.stellarpilot.app.feature.preparation

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedButton
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

private const val HOME_SERVER = "192.168.1.46"
private const val FIELD_SERVER = "10.42.0.1"
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

    var showCustomAddress by rememberSaveable { mutableStateOf(false) }
    var customAddress by rememberSaveable { mutableStateOf("") }

    val state = connectionViewModel.uiState
    val activeAddress = state.serverBaseUrl
        .removePrefix("http://")
        .removePrefix("https://")
        .removeSuffix("/")
        .removeSuffix(":8000")

    val profileLabel = when (activeAddress) {
        HOME_SERVER -> "Maison LAN"
        FIELD_SERVER -> "Terrain / campagne"
        else -> "Personnalisé"
    }

    fun applyServer(address: String) {
        val clean = address.trim()
        if (clean.isBlank()) return

        preferences.edit()
            .putString(PREF_SERVER, clean)
            .apply()

        connectionViewModel.setServerAddress(clean)
        customAddress = clean
        showCustomAddress = false
    }

    LaunchedEffect(Unit) {
        val saved = preferences.getString(PREF_SERVER, null)
        if (!saved.isNullOrBlank()) {
            customAddress = saved
            connectionViewModel.setServerAddress(saved)
        } else {
            customAddress = activeAddress
            connectionViewModel.connect()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = StellarSurface),
            border = BorderStroke(1.dp, StellarBorder)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    "Serveur StellarPilot",
                    color = StellarText,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "$profileLabel • $activeAddress",
                    color = StellarMuted
                )
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { applyServer(HOME_SERVER) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor =
                                if (activeAddress == HOME_SERVER) StellarOrange else StellarSurface,
                            contentColor =
                                if (activeAddress == HOME_SERVER) StellarBackground else StellarText
                        ),
                        border = BorderStroke(1.dp, StellarOrange)
                    ) {
                        Text("Maison\n192.168.1.46")
                    }

                    Button(
                        onClick = { applyServer(FIELD_SERVER) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor =
                                if (activeAddress == FIELD_SERVER) StellarOrange else StellarSurface,
                            contentColor =
                                if (activeAddress == FIELD_SERVER) StellarBackground else StellarText
                        ),
                        border = BorderStroke(1.dp, StellarOrange)
                    ) {
                        Text("Terrain\n10.42.0.1")
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        customAddress = activeAddress
                        showCustomAddress = !showCustomAddress
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Autre adresse serveur")
                }

                if (showCustomAddress) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customAddress,
                        onValueChange = { customAddress = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Adresse IP ou URL") },
                        supportingText = {
                            Text("Ex. 192.168.1.46, 10.42.0.1 ou http://adresse:8000")
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = { applyServer(customAddress) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StellarOrange,
                            contentColor = StellarBackground
                        )
                    ) {
                        Text("Utiliser cette adresse", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            PreparationV060Screen(
                onOpenSky = onOpenSky,
                connectionViewModel = connectionViewModel
            )
        }
    }
}
