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
import fr.stellarpilot.app.feature.connection.ConnectionState
import fr.stellarpilot.app.feature.connection.ConnectionViewModel
import fr.stellarpilot.app.feature.demo.DemoModeState
import fr.stellarpilot.app.ui.format.statusDisplay
import fr.stellarpilot.app.ui.theme.StellarBackground
import fr.stellarpilot.app.ui.theme.StellarBorder
import fr.stellarpilot.app.ui.theme.StellarGreen
import fr.stellarpilot.app.ui.theme.StellarMuted
import fr.stellarpilot.app.ui.theme.StellarOrange
import fr.stellarpilot.app.ui.theme.StellarRed
import fr.stellarpilot.app.ui.theme.StellarSurface
import fr.stellarpilot.app.ui.theme.StellarText
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor


@Composable
fun StatusScreen(
    viewModel: ConnectionViewModel = viewModel(),
    diagnosticsViewModel: StatusDiagnosticsViewModel = viewModel()
) {
    val state = viewModel.uiState
    val server = state.server
    val diagnostics = diagnosticsViewModel.uiState
    val demoMode = DemoModeState.active

    LaunchedEffect(Unit) {
        viewModel.connect()
    }

    LaunchedEffect(
        state.serverBaseUrl,
        demoMode
    ) {
        if (!demoMode) {
            diagnosticsViewModel.refreshStatic(
                state.serverBaseUrl
            )

            while (true) {
                diagnosticsViewModel.refreshMount(
                    state.serverBaseUrl
                )
                delay(2_000L)
            }
        }
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
                text = "STATUT SYSTÈME",
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
                text = "Diagnostic, position monture et ressources du système",
                style = MaterialTheme.typography.bodyMedium,
                color = StellarMuted
            )

            Spacer(Modifier.height(24.dp))

            if (server == null) {
                StatusCard(
                    title = "Serveur StellarPilot"
                ) {
                    Text(
                        text = when (state.connectionState) {
                            ConnectionState.CONNECTED ->
                                "Serveur connecté · télémétrie indisponible"

                            ConnectionState.CONNECTING ->
                                "Connexion au serveur..."

                            ConnectionState.RECONNECTING ->
                                "Reconnexion au serveur..."

                            ConnectionState.DISCONNECTED,
                            ConnectionState.STOPPED ->
                                if (demoMode) {
                                    "Mode démonstration · réseau désactivé"
                                } else {
                                    "Serveur non connecté"
                                }
                        },
                        color = StellarMuted
                    )
                }
            } else {
                StatusCard(
                    title = "Serveur"
                ) {
                    StatusLine(
                        label = "État",
                        value = statusDisplay(
                            server.devices.server.status
                        ),
                        status = server.devices.server.status
                    )

                    InfoLine("Service", server.service)
                    InfoLine("Mode", server.mode.uppercase())
                    InfoLine("Adresse", state.serverBaseUrl)
                    InfoLine("REST", state.restStatus)
                    InfoLine("WebSocket", state.webSocketStatus)

                    Spacer(Modifier.height(16.dp))

                    SectionTitle("STOCKAGE RASPBERRY PI")

                    diagnostics.storage?.let { storage ->
                        StatusLine(
                            label = "État",
                            value = if (storage.status == "ready") {
                                "Disponible"
                            } else {
                                "Indisponible"
                            },
                            status = storage.status
                        )

                        InfoLine(
                            "Capacité",
                            formatBytes(storage.totalBytes)
                        )
                        InfoLine(
                            "Utilisé",
                            formatBytes(storage.usedBytes)
                        )
                        InfoLine(
                            "Disponible",
                            formatBytes(storage.availableBytes)
                        )
                        InfoLine(
                            "Occupation",
                            storage.usedPercent?.let {
                                String.format(
                                    Locale.FRANCE,
                                    "%.1f %%",
                                    it
                                )
                            } ?: "Non disponible"
                        )
                        InfoLine(
                            "Répertoire",
                            storage.path ?: "Non disponible"
                        )
                    } ?: Text(
                        text = if (diagnostics.staticLoading) {
                            "Lecture du stockage..."
                        } else {
                            "Stockage non disponible"
                        },
                        color = StellarMuted
                    )
                }

                Spacer(Modifier.height(16.dp))

                val mount = server.devices.mount

                StatusCard(
                    title = "Monture"
                ) {
                    StatusLine(
                        label = "État matériel",
                        value = statusDisplay(mount.status),
                        status = mount.status
                    )

                    InfoLine(
                        "Périphérique",
                        mount.name ?: "Non identifié"
                    )
                    InfoLine(
                        "Type",
                        mountTypeDisplay(
                            mount.type,
                            mount.typeLabel
                        )
                    )
                    InfoLine(
                        "Code INDI",
                        mount.type ?: "Non disponible"
                    )
                    InfoLine(
                        "Détection",
                        when (server.session.mountTypeSource) {
                            "indi" -> "Automatique via INDI"
                            "manual" -> "Configuration manuelle"
                            else -> "Non disponible"
                        }
                    )

                    Spacer(Modifier.height(18.dp))

                    SectionTitle("POSITION MONTURE · TEMPS RÉEL")

                    Text(
                        text = "Actualisation automatique toutes les 2 s",
                        color = StellarMuted,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(Modifier.height(6.dp))

                    diagnostics.mount?.let { position ->
                        StatusLine(
                            label = "Mouvement",
                            value = mountMotionLabel(position.status),
                            status = mountMotionStatusKey(
                                position.status
                            )
                        )

                        InfoLine(
                            "Ascension droite",
                            formatRa(position.raHours)
                        )
                        InfoLine(
                            "RA décimale",
                            position.raHours?.let {
                                String.format(
                                    Locale.FRANCE,
                                    "%.6f h",
                                    it
                                )
                            } ?: "Non disponible"
                        )
                        InfoLine(
                            "Déclinaison",
                            formatDec(position.decDeg)
                        )
                        InfoLine(
                            "DEC décimale",
                            position.decDeg?.let {
                                String.format(
                                    Locale.FRANCE,
                                    "%+.6f°",
                                    it
                                )
                            } ?: "Non disponible"
                        )
                        InfoLine(
                            "Coordonnées INDI",
                            position.coordinateProperty
                                ?: "Non disponible"
                        )
                        InfoLine(
                            "État INDI",
                            position.indiState ?: "Non disponible"
                        )
                        InfoLine(
                            "Suivi",
                            trackingModeLabel(
                                position.trackingMode
                            )
                        )
                        InfoLine(
                            "Source position",
                            if (position.virtualPosition) {
                                "Virtuelle"
                            } else {
                                "OnStep / INDI réel"
                            }
                        )

                        if (
                            position.targetRaHours != null ||
                            position.targetDecDeg != null
                        ) {
                            Spacer(Modifier.height(10.dp))
                            SectionTitle("POINTAGE EN COURS / DERNIÈRE CIBLE")
                            InfoLine(
                                "Cible RA",
                                formatRa(position.targetRaHours)
                            )
                            InfoLine(
                                "Cible DEC",
                                formatDec(position.targetDecDeg)
                            )
                            InfoLine(
                                "Progression",
                                position.progressPercent?.let {
                                    String.format(
                                        Locale.FRANCE,
                                        "%.1f %%",
                                        it
                                    )
                                } ?: "Non disponible"
                            )
                            InfoLine(
                                "Écart restant",
                                position.remainingDeg?.let {
                                    String.format(
                                        Locale.FRANCE,
                                        "%.4f°",
                                        it
                                    )
                                } ?: "Non disponible"
                            )
                        }
                    } ?: Text(
                        text = if (diagnostics.mountLoading) {
                            "Lecture de la position..."
                        } else {
                            "Position de la monture non disponible"
                        },
                        color = StellarMuted
                    )

                    diagnostics.mountError?.let { error ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = error,
                            color = StellarOrange,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    OnStepClockStatusBlock(
                        serverBaseUrl = state.serverBaseUrl,
                        refreshKey = server.session.timestamp
                    )
                }

                Spacer(Modifier.height(16.dp))

                val camera = server.devices.camera

                StatusCard(
                    title = "Caméra"
                ) {
                    StatusLine(
                        label = "État",
                        value = statusDisplay(camera.status),
                        status = camera.status
                    )
                    InfoLine(
                        "Périphérique",
                        camera.name ?: "Non identifiée"
                    )
                    InfoLine(
                        "Résolution capteur",
                        resolutionDisplay(
                            camera.sensor.width,
                            camera.sensor.height
                        )
                    )
                    InfoLine(
                        "Taille pixel",
                        camera.sensor.pixelSizeUm?.let {
                            "${decimal(it, 2)} µm"
                        } ?: "Non disponible"
                    )
                    InfoLine(
                        "Profondeur",
                        camera.sensor.bitsPerPixel?.let {
                            "$it bits"
                        } ?: "Non disponible"
                    )
                    InfoLine(
                        "Zone de capture",
                        resolutionDisplay(
                            camera.capture.frameWidth,
                            camera.capture.frameHeight
                        )
                    )
                    InfoLine(
                        "Binning",
                        binningDisplay(
                            camera.capture.binX,
                            camera.capture.binY
                        )
                    )
                    InfoLine(
                        "Gain",
                        camera.capture.gain?.let {
                            numberDisplay(it)
                        } ?: "Non disponible"
                    )
                    InfoLine(
                        "Offset",
                        camera.capture.offset?.let {
                            numberDisplay(it)
                        } ?: "Non disponible"
                    )
                    InfoLine(
                        "Exposition",
                        camera.capture.exposureS?.let {
                            "${decimal(it, 3)} s"
                        } ?: "Non disponible"
                    )
                    InfoLine(
                        "Type image",
                        frameTypeDisplay(
                            camera.capture.frameType
                        )
                    )
                    InfoLine(
                        "Température",
                        camera.temperatureC?.let {
                            "${decimal(it, 1)} °C"
                        } ?: "Non disponible"
                    )
                }

                Spacer(Modifier.height(16.dp))

                StatusCard(
                    title = "GPS"
                ) {
                    StatusLine(
                        label = "État",
                        value = statusDisplay(
                            server.devices.gps.status
                        ),
                        status = server.devices.gps.status
                    )
                    InfoLine(
                        "Latitude",
                        formatCoordinate(
                            server.devices.gps.latitude
                        )
                    )
                    InfoLine(
                        "Longitude",
                        formatCoordinate(
                            server.devices.gps.longitude
                        )
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = if (
                            server.devices.gps.status.equals(
                                "fix",
                                ignoreCase = true
                            )
                        ) {
                            "✓ Position GPS disponible"
                        } else {
                            "Position GPS non fixée"
                        },
                        color = if (
                            server.devices.gps.status.equals(
                                "fix",
                                ignoreCase = true
                            )
                        ) {
                            StellarGreen
                        } else {
                            StellarOrange
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(16.dp))

                StatusCard(
                    title = "Session"
                ) {
                    InfoLine(
                        "Latitude",
                        formatCoordinate(server.session.latitude)
                    )
                    InfoLine(
                        "Longitude",
                        formatCoordinate(server.session.longitude)
                    )
                    InfoLine(
                        "Altitude",
                        server.session.altitude?.let {
                            "${decimal(it, 1)} m"
                        } ?: "Non disponible"
                    )
                    InfoLine(
                        "Horodatage",
                        server.session.timestamp
                            ?: "Non disponible"
                    )
                    InfoLine(
                        "Type monture",
                        mountTypeDisplay(
                            server.session.mountType,
                            mount.typeLabel
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            diagnostics.catalog?.let { catalog ->
                StatusCard(
                    title = "Catalogue astronomique"
                ) {
                    StatusLine(
                        label = "État",
                        value = if (catalog.status == "ready") {
                            "Prêt"
                        } else {
                            "Indisponible"
                        },
                        status = catalog.status
                    )
                    InfoLine(
                        "Source",
                        catalog.source ?: "Non disponible"
                    )
                    InfoLine(
                        "Version",
                        catalog.sourceVersion ?: "Non disponible"
                    )
                    InfoLine(
                        "Base",
                        catalog.databaseName ?: "Non disponible"
                    )
                    InfoLine(
                        "Taille de la base",
                        formatBytes(catalog.databaseSizeBytes)
                    )
                    InfoLine(
                        "Objets",
                        integerDisplay(catalog.objectCount)
                    )
                    InfoLine(
                        "Constellations IAU",
                        catalog.constellationCount.toString()
                    )
                    InfoLine(
                        "Codes constellation présents",
                        catalog.constellationCodesInCatalog.toString()
                    )
                    InfoLine(
                        "Noms français",
                        integerDisplay(catalog.frenchNameCount)
                    )
                    InfoLine(
                        "Groupes d'alias français",
                        integerDisplay(catalog.frenchAliasCount)
                    )
                    InfoLine(
                        "Langue",
                        if (catalog.language == "fr") {
                            "Français"
                        } else {
                            catalog.language ?: "Non disponible"
                        }
                    )
                    InfoLine(
                        "Mode",
                        if (catalog.offline) {
                            "Local · hors ligne"
                        } else {
                            "En ligne"
                        }
                    )

                    if (catalog.typeDetails.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        SectionTitle("CONTENU")
                        Spacer(Modifier.height(4.dp))

                        catalog.typeDetails.forEach { detail ->
                            InfoLine(
                                detail.labelFr,
                                integerDisplay(detail.count)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            diagnostics.staticError?.let { error ->
                StatusCard(
                    title = "Diagnostics système"
                ) {
                    Text(
                        text = error,
                        color = StellarOrange
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            StatusCard(
                title = "Application"
            ) {
                InfoLine("Version", BuildConfig.VERSION_NAME)
                InfoLine("Commit", BuildConfig.GIT_SHA)
                InfoLine("Backend", BuildConfig.BACKEND_MODE)
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
                onClick = {
                    viewModel.connect()
                    if (!demoMode) {
                        diagnosticsViewModel.refreshStatic(
                            state.serverBaseUrl
                        )
                        diagnosticsViewModel.refreshMount(
                            state.serverBaseUrl
                        )
                    }
                },
                enabled = !state.isConnecting,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StellarOrange,
                    contentColor = StellarBackground
                )
            ) {
                Text(
                    text = if (state.isConnecting) {
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
private fun SectionTitle(value: String) {
    Text(
        text = value,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = StellarOrange
    )
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


private fun statusColor(value: String): Color =
    when (value.lowercase()) {
        "online",
        "ready",
        "ok",
        "fix",
        "connected",
        "tracking",
        "idle" -> StellarGreen

        "error",
        "offline",
        "failed",
        "disconnected",
        "unavailable" -> StellarRed

        else -> StellarOrange
    }


private fun mountMotionStatusKey(status: String): String =
    when (status.lowercase()) {
        "idle", "tracking" -> "ready"
        "slewing" -> "busy"
        else -> status
    }


private fun mountMotionLabel(status: String): String =
    when (status.lowercase()) {
        "idle" -> "Position disponible"
        "tracking" -> "Suivi"
        "slewing" -> "Pointage en cours"
        "error" -> "Erreur"
        else -> status
    }


private fun trackingModeLabel(value: String?): String =
    when (value?.lowercase()) {
        "sidereal" -> "Sidéral"
        "solar" -> "Solaire"
        "lunar" -> "Lunaire"
        null -> "Non publié par la session"
        else -> value
    }


private fun mountTypeDisplay(
    type: String?,
    fallback: String?
): String =
    when (type) {
        "altaz" -> "Alt-Az"
        "eq_gem" -> "Équatoriale allemande (GEM)"
        "eq_fork" -> "Équatoriale à fourche"
        "equatorial" -> "Équatoriale"
        else -> fallback ?: "Non disponible"
    }


private fun resolutionDisplay(
    width: Int?,
    height: Int?
): String =
    if (width != null && height != null) {
        "$width × $height"
    } else {
        "Non disponible"
    }


private fun binningDisplay(
    x: Int?,
    y: Int?
): String =
    if (x != null && y != null) {
        "$x × $y"
    } else {
        "Non disponible"
    }


private fun frameTypeDisplay(value: String?): String =
    when (value?.lowercase()) {
        "light" -> "Light"
        "dark" -> "Dark"
        "flat" -> "Flat"
        "bias" -> "Bias"
        null -> "Non disponible"
        else -> value
    }


private fun formatCoordinate(value: Double?): String =
    value?.let {
        String.format(
            Locale.FRANCE,
            "%.6f°",
            it
        )
    } ?: "Non disponible"


private fun decimal(
    value: Double,
    digits: Int
): String =
    String.format(
        Locale.FRANCE,
        "%.$digits" + "f",
        value
    )


private fun numberDisplay(value: Double): String =
    if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        decimal(value, 2)
    }


private fun integerDisplay(value: Int): String =
    String.format(
        Locale.FRANCE,
        "%,d",
        value
    )


private fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes < 0L) {
        return "Non disponible"
    }

    val value = bytes.toDouble()
    val gib = 1024.0 * 1024.0 * 1024.0
    val mib = 1024.0 * 1024.0
    val kib = 1024.0

    return when {
        value >= gib -> String.format(
            Locale.FRANCE,
            "%.1f Gio",
            value / gib
        )
        value >= mib -> String.format(
            Locale.FRANCE,
            "%.1f Mio",
            value / mib
        )
        value >= kib -> String.format(
            Locale.FRANCE,
            "%.1f Kio",
            value / kib
        )
        else -> "$bytes octets"
    }
}


private fun formatRa(value: Double?): String {
    if (value == null) return "Non disponible"

    val normalized = ((value % 24.0) + 24.0) % 24.0
    val totalSeconds = normalized * 3600.0
    val hours = floor(totalSeconds / 3600.0).toInt()
    val minutes = floor(
        (totalSeconds - hours * 3600.0) / 60.0
    ).toInt()
    val seconds = totalSeconds -
        hours * 3600.0 -
        minutes * 60.0

    return String.format(
        Locale.FRANCE,
        "%02dh %02dm %04.1fs",
        hours,
        minutes,
        seconds
    )
}


private fun formatDec(value: Double?): String {
    if (value == null) return "Non disponible"

    val sign = if (value < 0.0) "−" else "+"
    val absolute = abs(value)
    val degrees = floor(absolute).toInt()
    val minutesValue = (absolute - degrees) * 60.0
    val minutes = floor(minutesValue).toInt()
    val seconds = (minutesValue - minutes) * 60.0

    return String.format(
        Locale.FRANCE,
        "%s%02d° %02d′ %04.1f″",
        sign,
        degrees,
        minutes,
        seconds
    )
}
