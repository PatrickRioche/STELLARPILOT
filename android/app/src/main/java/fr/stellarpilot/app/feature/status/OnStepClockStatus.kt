package fr.stellarpilot.app.feature.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import fr.stellarpilot.app.data.remote.MountClockStatus
import fr.stellarpilot.app.data.remote.MountTimeApiClient
import fr.stellarpilot.app.ui.theme.StellarGreen
import fr.stellarpilot.app.ui.theme.StellarMuted
import fr.stellarpilot.app.ui.theme.StellarOrange
import fr.stellarpilot.app.ui.theme.StellarText
import kotlinx.coroutines.launch
import java.util.Locale


data class MountClockUiState(
    val isLoading: Boolean = false,
    val clock: MountClockStatus? = null,
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
                val clock =
                    MountTimeApiClient(serverBaseUrl)
                        .getStatus()

                uiState = MountClockUiState(
                    isLoading = false,
                    clock = clock,
                    error = null
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    error = error.message
                        ?: "Lecture de l'heure OnStep impossible"
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
    val clock = state.clock

    LaunchedEffect(serverBaseUrl, refreshKey) {
        viewModel.load(serverBaseUrl)
    }

    Spacer(Modifier.height(14.dp))

    Text(
        text = "HORLOGE ONSTEP • INDI",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = StellarOrange
    )

    Spacer(Modifier.height(6.dp))

    ClockInfoLine(
        label = "Heure OnStep (UTC)",
        value = clock?.utc ?: "Non disponible"
    )

    ClockInfoLine(
        label = "Offset OnStep",
        value = clock?.offsetHours
            ?.let {
                String.format(
                    Locale.FRANCE,
                    "%+.2f h",
                    it
                )
            }
            ?: "Non disponible"
    )

    ClockInfoLine(
        label = "Référence",
        value = when (clock?.referenceSource) {
            "gps" -> "GPS"
            "android" -> "Tablette Android"
            "system_untrusted" -> "Système Pi non fiable"
            null -> "Non disponible"
            else -> clock.referenceSource.uppercase(Locale.ROOT)
        }
    )

    val synchronized = clock?.synchronized == true
    val synchronizationLabel = when {
        state.isLoading -> "Lecture en cours…"
        synchronized -> "SYNCHRONISÉE"
        clock?.synchronization == "drift" -> "À RESYNCHRONISER"
        clock?.status != "available" -> "HEURE INDI INDISPONIBLE"
        clock?.referenceSource == "system_untrusted" -> "NON VÉRIFIÉE"
        else -> "NON VÉRIFIÉE"
    }

    ClockStatusLine(
        label = "Synchronisation",
        value = synchronizationLabel,
        good = synchronized
    )

    clock?.driftSeconds?.let { drift ->
        ClockInfoLine(
            label = "Écart mesuré",
            value = String.format(
                Locale.FRANCE,
                "%.1f s",
                drift
            )
        )
    }

    if (synchronized) {
        Text(
            text = "✓ Heure OnStep cohérente avec la référence fiable (tolérance 10 s)",
            color = StellarGreen,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    } else if (!state.isLoading) {
        Text(
            text =
                clock?.detail
                    ?: "Vérifiez l'heure de la monture avant un déplacement depuis Ciel.",
            color = StellarOrange,
            style = MaterialTheme.typography.bodySmall
        )
    }

    state.error?.let { error ->
        Spacer(Modifier.height(4.dp))
        Text(
            text = error,
            color = StellarOrange,
            style = MaterialTheme.typography.bodySmall
        )
    }
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
