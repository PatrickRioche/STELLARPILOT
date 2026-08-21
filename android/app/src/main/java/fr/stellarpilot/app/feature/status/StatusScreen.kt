package fr.stellarpilot.app.feature.status

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.stellarpilot.app.BuildConfig
import fr.stellarpilot.app.feature.connection.ConnectionViewModel
import fr.stellarpilot.app.ui.theme.StellarBackground
import fr.stellarpilot.app.ui.theme.StellarBorder
import fr.stellarpilot.app.ui.theme.StellarGreen
import fr.stellarpilot.app.ui.theme.StellarMuted
import fr.stellarpilot.app.ui.theme.StellarOrange
import fr.stellarpilot.app.ui.theme.StellarRed
import fr.stellarpilot.app.ui.theme.StellarSurface
import fr.stellarpilot.app.ui.theme.StellarText
import java.util.Locale

@Composable
fun StatusScreen(
    viewModel: ConnectionViewModel = viewModel()
) {
    val state = viewModel.uiState
    val server = state.server

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
                    vertical = 24.dp
                )
        ) {

            Text(
                text = "STATUT SYST\u00C8ME",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = StellarOrange
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Cockpit StellarPilot",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = StellarText
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Diagnostic et informations techniques",
                style = MaterialTheme.typography.bodyMedium,
                color = StellarMuted
            )

            Spacer(Modifier.height(24.dp))

            if (server == null) {

                StatusCard(
                    title = "Serveur StellarPilot"
                ) {
                    Text(
                        text =
                            if (state.isConnecting) {
                                "Connexion au serveur..."
                            } else {
                                "Serveur non connect\u00E9"
                            },
                        color = StellarMuted
                    )
                }

            } else {

                // =====================================================
                // SERVEUR
                // =====================================================

                StatusCard(
                    title = "Serveur"
                ) {

                    StatusLine(
                        label = "\u00C9tat",
                        value = server.devices.server.status.uppercase(),
                        status = server.devices.server.status
                    )

                    InfoLine(
                        label = "Service",
                        value = server.service
                    )

                    InfoLine(
                        label = "Mode",
                        value = server.mode.uppercase()
                    )

                    InfoLine(
                        label = "Adresse",
                        value = state.serverBaseUrl
                    )

                    InfoLine(
                        label = "REST",
                        value = state.restStatus
                    )

                    InfoLine(
                        label = "WebSocket",
                        value = state.webSocketStatus
                    )
                }

                Spacer(Modifier.height(16.dp))

                // =====================================================
                // MONTURE
                // =====================================================

                val mount = server.devices.mount

                StatusCard(
                    title = "Monture"
                ) {

                    StatusLine(
                        label = "\u00C9tat",
                        value = mount.status.uppercase(),
                        status = mount.status
                    )

                    InfoLine(
                        label = "P\u00E9riph\u00E9rique",
                        value = mount.name
                            ?: "Non identifi\u00E9"
                    )

                    InfoLine(
                        label = "Type",
                        value = mountTypeDisplay(
                            mount.type,
                            mount.typeLabel
                        )
                    )

                    InfoLine(
                        label = "Code INDI",
                        value = mount.type
                            ?: "Non disponible"
                    )

                    InfoLine(
                        label = "D\u00E9tection",
                        value = when (
                            server.session.mountTypeSource
                        ) {
                            "indi" ->
                                "Automatique via INDI"

                            "manual" ->
                                "Configuration manuelle"

                            else ->
                                "Non disponible"
                        }
                    )

                    if (
                        server.session.mountTypeSource == "indi"
                    ) {
                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "\u2713 Type de monture d\u00E9tect\u00E9 automatiquement",
                            color = StellarGreen,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // =====================================================
                // CAMERA
                // =====================================================

                val camera = server.devices.camera

                StatusCard(
                    title = "Cam\u00E9ra"
                ) {

                    StatusLine(
                        label = "\u00C9tat",
                        value = camera.status.uppercase(),
                        status = camera.status
                    )

                    InfoLine(
                        label = "P\u00E9riph\u00E9rique",
                        value = camera.name
                            ?: "Non identifi\u00E9e"
                    )

                    InfoLine(
                        label = "R\u00E9solution capteur",
                        value = resolutionDisplay(
                            camera.sensor.width,
                            camera.sensor.height
                        )
                    )

                    InfoLine(
                        label = "Taille pixel",
                        value = camera.sensor.pixelSizeUm
                            ?.let {
                                "${decimal(it, 2)} \u00B5m"
                            }
                            ?: "Non disponible"
                    )

                    InfoLine(
                        label = "Profondeur",
                        value = camera.sensor.bitsPerPixel
                            ?.let {
                                "$it bits"
                            }
                            ?: "Non disponible"
                    )

                    InfoLine(
                        label = "Zone de capture",
                        value = resolutionDisplay(
                            camera.capture.frameWidth,
                            camera.capture.frameHeight
                        )
                    )

                    InfoLine(
                        label = "Binning",
                        value = binningDisplay(
                            camera.capture.binX,
                            camera.capture.binY
                        )
                    )

                    InfoLine(
                        label = "Gain",
                        value = camera.capture.gain
                            ?.let {
                                numberDisplay(it)
                            }
                            ?: "Non disponible"
                    )

                    InfoLine(
                        label = "Offset",
                        value = camera.capture.offset
                            ?.let {
                                numberDisplay(it)
                            }
                            ?: "Non disponible"
                    )

                    InfoLine(
                        label = "Exposition",
                        value = camera.capture.exposureS
                            ?.let {
                                "${decimal(it, 2)} s"
                            }
                            ?: "Non disponible"
                    )

                    InfoLine(
                        label = "Type image",
                        value = frameTypeDisplay(
                            camera.capture.frameType
                        )
                    )

                    InfoLine(
                        label = "Temp\u00E9rature",
                        value = camera.temperatureC
                            ?.let {
                                "${decimal(it, 1)} \u00B0C"
                            }
                            ?: "Non disponible"
                    )
                }

                Spacer(Modifier.height(16.dp))

                // =====================================================
                // GPS
                // =====================================================

                StatusCard(
                    title = "GPS"
                ) {

                    StatusLine(
                        label = "\u00C9tat",
                        value = server.devices.gps.status.uppercase(),
                        status = server.devices.gps.status
                    )

                    InfoLine(
                        label = "Latitude",
                        value = formatCoordinate(
                            server.devices.gps.latitude
                        )
                    )

                    InfoLine(
                        label = "Longitude",
                        value = formatCoordinate(
                            server.devices.gps.longitude
                        )
                    )

                    Spacer(Modifier.height(8.dp))

                    if (
                        server.devices.gps.status.lowercase() ==
                        "fix"
                    ) {
                        Text(
                            text = "\u2713 Position GPS disponible",
                            color = StellarGreen,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            text = "Position GPS en attente de FIX",
                            color = StellarOrange,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // =====================================================
                // SESSION
                // =====================================================

                StatusCard(
                    title = "Session"
                ) {

                    InfoLine(
                        label = "Latitude",
                        value = formatCoordinate(
                            server.session.latitude
                        )
                    )

                    InfoLine(
                        label = "Longitude",
                        value = formatCoordinate(
                            server.session.longitude
                        )
                    )

                    InfoLine(
                        label = "Altitude",
                        value = server.session.altitude
                            ?.let {
                                "${decimal(it, 1)} m"
                            }
                            ?: "Non disponible"
                    )

                    InfoLine(
                        label = "Horodatage",
                        value = server.session.timestamp
                            ?: "Non disponible"
                    )

                    InfoLine(
                        label = "Type monture",
                        value = mountTypeDisplay(
                            server.session.mountType,
                            mount.typeLabel
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // =========================================================
            // APPLICATION
            // =========================================================

            StatusCard(
                title = "Application"
            ) {

                InfoLine(
                    label = "Version",
                    value = BuildConfig.VERSION_NAME
                )

                InfoLine(
                    label = "Commit",
                    value = BuildConfig.GIT_SHA
                )

                InfoLine(
                    label = "Backend",
                    value = BuildConfig.BACKEND_MODE
                )
            }

            state.error?.let { error ->

                Spacer(Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor =
                            StellarRed.copy(alpha = 0.12f)
                    ),
                    border = BorderStroke(
                        1.dp,
                        StellarRed.copy(alpha = 0.45f)
                    )
                ) {
                    Text(
                        text = "Erreur : $error",
                        modifier = Modifier.padding(16.dp),
                        color = StellarRed
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = viewModel::connect,
                enabled = !state.isConnecting,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StellarOrange,
                    contentColor = StellarBackground
                )
            ) {
                Text(
                    text =
                        if (state.isConnecting) {
                            "Actualisation..."
                        } else {
                            "Actualiser le statut"
                        },
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    content: @Composable () -> Unit
) {
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

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = StellarText
            )

            Spacer(Modifier.height(16.dp))

            content()
        }
    }
}

@Composable
private fun StatusLine(
    label: String,
    value: String,
    status: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .background(
                    statusColor(status),
                    CircleShape
                )
                .padding(5.dp)
        )

        Text(
            text = label,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
            color = StellarMuted
        )

        Text(
            text = value,
            color = statusColor(status),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun InfoLine(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top
    ) {

        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = StellarMuted
        )

        Text(
            text = value,
            modifier = Modifier.weight(1f),
            color = StellarText,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun mountTypeDisplay(
    type: String?,
    fallback: String?
): String =
    when (type) {
        "altaz" ->
            "Alt-Az"

        "eq_gem" ->
            "\u00C9quatoriale allemande (GEM)"

        "eq_fork" ->
            "\u00C9quatoriale \u00E0 fourche"

        "equatorial" ->
            "\u00C9quatoriale"

        else ->
            fallback ?: "Non disponible"
    }

private fun resolutionDisplay(
    width: Int?,
    height: Int?
): String =
    if (width != null && height != null) {
        "$width \u00D7 $height"
    } else {
        "Non disponible"
    }

private fun binningDisplay(
    x: Int?,
    y: Int?
): String =
    if (x != null && y != null) {
        "$x \u00D7 $y"
    } else {
        "Non disponible"
    }

private fun frameTypeDisplay(
    value: String?
): String =
    when (value?.lowercase()) {
        "light" -> "LIGHT"
        "dark" -> "DARK"
        "flat" -> "FLAT"
        "bias" -> "BIAS"
        else -> value?.uppercase() ?: "Non disponible"
    }

private fun formatCoordinate(
    value: Double?
): String =
    value?.let {
        "${decimal(it, 6)}\u00B0"
    } ?: "Non disponible"

private fun numberDisplay(
    value: Double
): String =
    if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        decimal(value, 2)
    }

private fun decimal(
    value: Double,
    digits: Int
): String =
    String.format(
        Locale.getDefault(),
        "%.${digits}f",
        value
    )

private fun statusColor(
    value: String
): Color =
    when (value.lowercase()) {

        "online",
        "ready",
        "ok",
        "fix",
        "connected" ->
            StellarGreen

        "offline",
        "error",
        "failed",
        "disconnected",
        "unavailable" ->
            StellarRed

        else ->
            StellarOrange
    }