package fr.stellarpilot.app.feature.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.stellarpilot.app.data.remote.MountTimeApiClient
import fr.stellarpilot.app.data.remote.TimeSourceStatus
import fr.stellarpilot.app.data.remote.TimeSynchronizationStatus
import fr.stellarpilot.app.ui.theme.StellarGreen
import fr.stellarpilot.app.ui.theme.StellarMuted
import fr.stellarpilot.app.ui.theme.StellarOrange
import fr.stellarpilot.app.ui.theme.StellarText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale


data class MountClockUiState(
    val isLoading: Boolean = false,
    val synchronization: TimeSynchronizationStatus? = null,
    val error: String? = null
)


class MountClockViewModel : ViewModel() {
    var uiState by mutableStateOf(MountClockUiState())
        private set

    fun load(serverBaseUrl: String) {
        if (uiState.isLoading) return

        viewModelScope.launch {
            uiState = uiState.copy(
                isLoading = true,
                error = null
            )

            try {
                val synchronization =
                    MountTimeApiClient(serverBaseUrl)
                        .getSynchronizationStatus()

                uiState = MountClockUiState(
                    isLoading = false,
                    synchronization = synchronization,
                    error = null
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    error = error.message
                        ?: "Lecture de la synchronisation temporelle impossible"
                )
            }
        }
    }
}


@Composable
fun OnStepClockStatusBlock(
    serverBaseUrl: String,
    refreshKey: String?,
    viewModel: MountClockViewModel = viewModel()
) {
    val state = viewModel.uiState
    val synchronization = state.synchronization

    LaunchedEffect(serverBaseUrl, refreshKey) {
        while (true) {
            viewModel.load(serverBaseUrl)
            delay(5_000)
        }
    }

    Spacer(Modifier.height(14.dp))

    Text(
        text = "SYNCHRONISATION TEMPORELLE",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = StellarOrange
    )

    Spacer(Modifier.height(4.dp))

    Text(
        text = "Actualisation automatique toutes les 5 s",
        color = StellarMuted,
        style = MaterialTheme.typography.bodySmall
    )

    Spacer(Modifier.height(6.dp))

    val overallGood = synchronization?.status == "synchronized"
    val overallLabel = when {
        state.isLoading && synchronization == null -> "LECTURE EN COURS…"
        synchronization?.status == "synchronized" -> "TOUT SYNCHRONISÉ"
        synchronization?.status == "partial" -> "SYNCHRONISATION PARTIELLE"
        synchronization?.status == "attention" -> "À VÉRIFIER"
        else -> "NON VÉRIFIÉE"
    }

    ClockStatusLine(
        label = "État global",
        value = overallLabel,
        good = overallGood
    )

    ClockStatusLine(
        label = "Pointage Ciel",
        value = when {
            state.isLoading && synchronization == null -> "VÉRIFICATION…"
            synchronization?.mountControlReady == true -> "PRÊT"
            else -> "BLOQUÉ / À VÉRIFIER"
        },
        good = synchronization?.mountControlReady == true
    )

    ClockInfoLine(
        label = "Référence",
        value = referenceSourceLabel(
            synchronization?.referenceSource
        )
    )

    ClockInfoLine(
        label = "Heure de référence",
        value = synchronization?.referenceUtc
            ?: "Non disponible"
    )

    ClockInfoLine(
        label = "Tolérance",
        value = synchronization?.toleranceSeconds
            ?.let {
                String.format(
                    Locale.FRANCE,
                    "%.0f s",
                    it
                )
            }
            ?: "10 s"
    )

    synchronization?.let { sync ->
        TimeSourceBlock(
            title = "GPS",
            source = sync.gps
        )

        TimeSourceBlock(
            title = "Tablette Android",
            source = sync.android,
            extraValue = sync.android.timezoneOffsetMinutes
                ?.let { minutes ->
                    val hours = minutes / 60.0
                    String.format(
                        Locale.FRANCE,
                        "UTC%+.1f",
                        hours
                    )
                },
            extraLabel = "Fuseau"
        )

        TimeSourceBlock(
            title = "Raspberry Pi",
            source = sync.raspberryPi,
            subtitle = "Contrôle uniquement · non autoritaire"
        )

        TimeSourceBlock(
            title = "OnStep / INDI",
            source = sync.onStep,
            subtitle = "TIME_UTC publiée par INDI · diagnostic uniquement",
            extraValue = sync.onStep.offsetHours
                ?.let { offset ->
                    String.format(
                        Locale.FRANCE,
                        "%+.2f h",
                        offset
                    )
                },
            extraLabel = "Offset OnStep"
        )

        sync.onStep.expectedOffsetHours?.let { expected ->
            ClockInfoLine(
                label = "Offset attendu",
                value = String.format(
                    Locale.FRANCE,
                    "%+.2f h",
                    expected
                )
            )
        }

        sync.onStep.offsetMatchesReference?.let { matches ->
            ClockStatusLine(
                label = "Cohérence offset",
                value = if (matches) "OK" else "À CORRIGER",
                good = matches
            )
        }

        sync.onStep.indiState?.let { indiState ->
            ClockStatusLine(
                label = "État TIME_UTC INDI",
                value = indiState,
                good = indiState.equals(
                    "Ok",
                    ignoreCase = true
                )
            )
        }

        sync.note?.let { note ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = note,
                color = StellarMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    state.error?.let { error ->
        Spacer(Modifier.height(6.dp))
        Text(
            text = error,
            color = StellarOrange,
            style = MaterialTheme.typography.bodySmall
        )
    }
}


@Composable
private fun TimeSourceBlock(
    title: String,
    source: TimeSourceStatus,
    subtitle: String? = null,
    extraLabel: String? = null,
    extraValue: String? = null
) {
    Spacer(Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = StellarText,
            fontWeight = FontWeight.Bold
        )

        SourceStatusBadge(source)
    }

    subtitle?.let {
        Text(
            text = it,
            color = StellarMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }

    ClockInfoLine(
        label = "UTC",
        value = source.utc ?: "Non disponible"
    )

    source.driftSeconds?.let { drift ->
        ClockInfoLine(
            label = if (source.driftAdvisory) {
                "Écart TIME_UTC (indicatif)"
            } else {
                "Écart référence"
            },
            value = String.format(
                Locale.FRANCE,
                "%.1f s",
                drift
            )
        )
    }

    if (extraLabel != null && extraValue != null) {
        ClockInfoLine(
            label = extraLabel,
            value = extraValue
        )
    }

    source.detail?.let { detail ->
        Text(
            text = detail,
            color = StellarOrange,
            style = MaterialTheme.typography.bodySmall
        )
    }
}


@Composable
private fun SourceStatusBadge(
    source: TimeSourceStatus
) {
    val alert = source.indiState?.equals(
        "Alert",
        ignoreCase = true
    ) == true

    val good = source.trustedReference ||
        source.controlReady == true ||
        source.synchronized == true

    val label = when {
        source.trustedReference -> "RÉFÉRENCE"
        !source.available -> "INDISPONIBLE"
        alert -> "ALERTE"
        source.controlReady == true -> "PRÊT"
        source.synchronized == true -> "SYNCHRONISÉ"
        source.synchronized == false -> "ÉCART"
        else -> "DISPONIBLE"
    }

    val color = if (good && !alert) StellarGreen else StellarOrange

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .background(color, CircleShape)
                .padding(4.dp)
        )

        Text(
            text = label,
            modifier = Modifier.padding(start = 6.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}


private fun referenceSourceLabel(source: String?): String =
    when (source) {
        "gps" -> "GPS"
        "android" -> "Tablette Android"
        "system_untrusted" -> "Raspberry Pi · non fiable"
        null -> "Non disponible"
        else -> source.uppercase(Locale.ROOT)
    }


@Composable
private fun ClockStatusLine(
    label: String,
    value: String,
    good: Boolean
) {
    val color = if (good) StellarGreen else StellarOrange

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .background(color, CircleShape)
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
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}


@Composable
private fun ClockInfoLine(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
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
