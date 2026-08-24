package fr.stellarpilot.app.feature.connection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.stellarpilot.app.BuildConfig
import fr.stellarpilot.app.R
import fr.stellarpilot.app.ui.theme.StellarBackground
import fr.stellarpilot.app.ui.theme.StellarBorder
import fr.stellarpilot.app.ui.theme.StellarGreen
import fr.stellarpilot.app.ui.theme.StellarMuted
import fr.stellarpilot.app.ui.theme.StellarOrange
import fr.stellarpilot.app.ui.theme.StellarRed
import fr.stellarpilot.app.ui.theme.StellarSurface
import fr.stellarpilot.app.ui.theme.StellarSurfaceRaised
import fr.stellarpilot.app.ui.theme.StellarText

@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel = viewModel()
) {
    val state = viewModel.uiState
    val server = state.server

    /*
     * La connexion au serveur depend de ConnectionState.
     * /status ne represente que la telemetrie materielle.
     */
    val serverConnected =
        state.connectionState == ConnectionState.CONNECTED

    val serverConnecting =
        state.connectionState == ConnectionState.CONNECTING ||
        state.connectionState == ConnectionState.RECONNECTING

    LaunchedEffect(Unit) {
        viewModel.connect()
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
                    horizontal = 24.dp,
                    vertical = 28.dp
                )
        ) {

            // ----------------------------------------------------
            // HEADER
            // ----------------------------------------------------

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Image(
                    painter = painterResource(R.mipmap.ic_launcher),
                    contentDescription = "StellarPilot",
                    modifier = Modifier.size(68.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp)
                ) {

                    Text(
                        text = "StellarPilot",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = StellarText
                    )

                    Text(
                        text = "Pilotage astronomique",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StellarMuted
                    )
                }

                ModeBadge(
                    text = state.backendMode
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = "Connexion",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = StellarText
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Serveur StellarPilot et mat\u00E9riel d'observation",
                style = MaterialTheme.typography.bodyMedium,
                color = StellarMuted
            )

            Spacer(Modifier.height(20.dp))

            // ----------------------------------------------------
            // SERVER
            // ----------------------------------------------------

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = StellarSurface
                ),
                border = BorderStroke(
                    1.dp,
                    StellarBorder
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        StatusDot(
                            status =
                                when {
                                    serverConnected ->
                                        "connected"

                                    serverConnecting ->
                                        "connecting"

                                    else ->
                                        "offline"
                                }
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 14.dp)
                        ) {

                            Text(
                                text = "Serveur StellarPilot",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = StellarText
                            )

                            Text(
                                text =
                                    when {
                                        serverConnected ->
                                            "Connect\u00E9 \u2022 ${state.serverBaseUrl}"

                                        serverConnecting ->
                                            "Reconnexion en cours..."

                                        else ->
                                            "Serveur non connect\u00E9"
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = StellarMuted
                            )
                        }

                        server?.let {
                            ModeBadge(
                                text = it.mode.uppercase()
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {

                        BuildInfo(
                            label = "VERSION",
                            value = BuildConfig.VERSION_NAME
                        )

                        BuildInfo(
                            label = "COMMIT",
                            value = BuildConfig.GIT_SHA
                        )

                        BuildInfo(
                            label = "APP",
                            value = state.backendMode
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ----------------------------------------------------
            // HARDWARE STATUS
            // ----------------------------------------------------

            Text(
                text = "Statut syst\u00E8me",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = StellarText
            )

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = StellarSurface
                ),
                border = BorderStroke(
                    1.dp,
                    StellarBorder
                )
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 20.dp,
                        vertical = 10.dp
                    )
                ) {

                    if (server == null) {

                        Text(
                            text =
                                when {
                                    serverConnected ->
                                        "Serveur connect\u00E9 - t\u00E9l\u00E9m\u00E9trie /status indisponible"

                                    serverConnecting ->
                                        "Lecture de l'\u00E9tat du serveur..."

                                    else ->
                                        "Aucune donn\u00E9e disponible"
                                },
                            modifier = Modifier.padding(vertical = 20.dp),
                            color = StellarMuted
                        )

                    } else {

                        StatusRow(
                            label = "Serveur",
                            detail = server.service,
                            status = server.devices.server.status
                        )

                        StatusSeparator()

                        StatusRow(
                            label = "Monture",
                            detail = server.devices.mount.name
                                ?: "P\u00E9riph\u00E9rique INDI",
                            status = server.devices.mount.status
                        )

                        StatusSeparator()

                        StatusRow(
                            label = "Cam\u00E9ra",
                            detail = server.devices.camera.name
                                ?: "P\u00E9riph\u00E9rique INDI",
                            status = server.devices.camera.status
                        )

                        StatusSeparator()

                        val gps = server.devices.gps

                        StatusRow(
                            label = "GPS",
                            detail =
                                if (
                                    gps.latitude != null &&
                                    gps.longitude != null
                                ) {
                                    "${gps.latitude}, ${gps.longitude}"
                                } else {
                                    "Position en attente"
                                },
                            status = gps.status
                        )
                    }
                }
            }

            state.error?.let { error ->

                Spacer(Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = StellarRed.copy(alpha = 0.12f)
                    ),
                    border = BorderStroke(
                        1.dp,
                        StellarRed.copy(alpha = 0.45f)
                    )
                ) {

                    Text(
                        text = "Erreur : $error",
                        modifier = Modifier.padding(16.dp),
                        color = StellarRed,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ----------------------------------------------------
            // REFRESH
            // ----------------------------------------------------

            Button(
                onClick = viewModel::connect,
                enabled = !state.isConnecting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StellarOrange,
                    contentColor = StellarBackground,
                    disabledContainerColor =
                        StellarOrange.copy(alpha = 0.45f),
                    disabledContentColor =
                        StellarBackground.copy(alpha = 0.7f)
                )
            ) {

                Text(
                    text =
                        if (state.isConnecting) {
                            "Connexion..."
                        } else {
                            "Actualiser les \u00E9tats"
                        },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(20.dp))

            // ----------------------------------------------------
            // FOOTER
            // ----------------------------------------------------

            Text(
                text =
                    "v${BuildConfig.VERSION_NAME} \u2022 " +
                    "${BuildConfig.GIT_SHA} \u2022 " +
                    "App ${state.backendMode}" +
                    (
                        server?.let {
                            " \u2022 Serveur ${it.mode.uppercase()}"
                        } ?: ""
                    ),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.bodySmall,
                color = StellarMuted
            )

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ModeBadge(
    text: String
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = StellarSurfaceRaised,
        border = BorderStroke(
            1.dp,
            StellarOrange.copy(alpha = 0.65f)
        )
    ) {

        Text(
            text = text.uppercase(),
            modifier = Modifier.padding(
                horizontal = 11.dp,
                vertical = 6.dp
            ),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = StellarOrange
        )
    }
}

@Composable
private fun BuildInfo(
    label: String,
    value: String
) {
    Column {

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = StellarMuted
        )

        Spacer(Modifier.height(3.dp))

        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = StellarText
        )
    }
}

@Composable
private fun StatusRow(
    label: String,
    detail: String,
    status: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        StatusDot(
            status = status
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {

            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = StellarText
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = StellarMuted
            )
        }

        Text(
            text = status.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = statusColor(status)
        )
    }
}

@Composable
private fun StatusDot(
    status: String
) {
    Box(
        modifier = Modifier
            .size(11.dp)
            .background(
                color = statusColor(status),
                shape = CircleShape
            )
    )
}

@Composable
private fun StatusSeparator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(StellarBorder.copy(alpha = 0.65f))
    )
}

private fun statusColor(
    status: String
): Color =
    when (status.lowercase()) {

        "online",
        "ready",
        "fix",
        "ok",
        "connected" -> StellarGreen

        "offline",
        "error",
        "failed",
        "unavailable",
        "disconnected" -> StellarRed

        else -> StellarOrange
    }
