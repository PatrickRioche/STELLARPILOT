package fr.stellarpilot.app.feature.preparation

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.stellarpilot.app.data.remote.FlatCalibrationApiClient
import fr.stellarpilot.app.ui.theme.StellarBackground
import fr.stellarpilot.app.ui.theme.StellarBorder
import fr.stellarpilot.app.ui.theme.StellarGreen
import fr.stellarpilot.app.ui.theme.StellarMuted
import fr.stellarpilot.app.ui.theme.StellarOrange
import fr.stellarpilot.app.ui.theme.StellarRed
import fr.stellarpilot.app.ui.theme.StellarSurface
import fr.stellarpilot.app.ui.theme.StellarText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale


data class FlatCalibrationUiState(
    val expanded: Boolean = false,
    val isLoading: Boolean = false,
    val exposureSeconds: Double = 0.20,
    val requestedCount: Int = 20,
    val capturedCount: Int = 0,
    val validCount: Int = 0,
    val sessionId: String? = null,
    val masterFlatPath: String? = null,
    val cameraName: String? = null,
    val gain: Double? = null,
    val offset: Double? = null,
    val bayerPattern: String? = null,
    val message: String? = null,
    val error: String? = null
)


class FlatCalibrationViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val FLAT_COUNT = 20
        private const val MIN_EXPOSURE = 0.01
        private const val MAX_EXPOSURE = 5.0
        private const val STEP = 0.05
    }

    var uiState by mutableStateOf(FlatCalibrationUiState())
        private set

    fun toggle() {
        uiState = uiState.copy(expanded = !uiState.expanded)
    }

    fun reset() {
        uiState = FlatCalibrationUiState(expanded = true)
    }

    fun changeExposure(delta: Double) {
        if (uiState.isLoading || uiState.sessionId != null) return
        val value = (uiState.exposureSeconds + delta)
            .coerceIn(MIN_EXPOSURE, MAX_EXPOSURE)
        uiState = uiState.copy(
            exposureSeconds = value,
            message = null,
            error = null
        )
    }

    fun start(serverBaseUrl: String) {
        if (uiState.isLoading || uiState.sessionId != null) return
        val exposure = uiState.exposureSeconds
        uiState = uiState.copy(
            isLoading = true,
            requestedCount = FLAT_COUNT,
            capturedCount = 0,
            validCount = 0,
            message = "Préparation de $FLAT_COUNT flats…",
            error = null
        )

        viewModelScope.launch {
            try {
                val api = FlatCalibrationApiClient()
                var status = api.start(
                    serverBaseUrl = serverBaseUrl,
                    exposureSeconds = exposure,
                    count = FLAT_COUNT
                )
                uiState = uiState.copy(sessionId = status.id)

                while (
                    status.status != "complete" &&
                    status.capturedCount < status.requestedCount
                ) {
                    val next = status.capturedCount + 1
                    uiState = uiState.copy(
                        isLoading = true,
                        message = "Flat $next/${status.requestedCount} • ${String.format(Locale.FRANCE, "%.2f", status.exposureSeconds)} s…",
                        error = null
                    )
                    status = api.capture(serverBaseUrl, status.id)
                    uiState = uiState.copy(
                        capturedCount = status.capturedCount,
                        validCount = status.validCount,
                        cameraName = status.cameraName,
                        gain = status.gain,
                        offset = status.offset,
                        bayerPattern = status.bayerPattern,
                        masterFlatPath = status.masterFlatPath,
                        message = if (status.status == "complete") {
                            "Master Flat créé • ${status.validCount}/${status.requestedCount} flats valides ✓"
                        } else {
                            "Flat ${status.capturedCount}/${status.requestedCount} enregistré"
                        }
                    )
                    if (status.status != "complete") delay(350)
                }

                uiState = uiState.copy(
                    isLoading = false,
                    masterFlatPath = status.masterFlatPath,
                    message = if (status.status == "complete") {
                        "Master Flat prêt ✓ • il sera sélectionné automatiquement s'il est compatible"
                    } else {
                        status.detail ?: uiState.message
                    },
                    error = if (status.status == "error") status.detail else null
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    error = error.message ?: "Création des flats impossible"
                )
            }
        }
    }
}


@Composable
fun FlatCalibrationPanel(
    serverBaseUrl: String,
    viewModel: FlatCalibrationViewModel = viewModel()
) {
    val state = viewModel.uiState

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = StellarSurface),
        border = BorderStroke(1.dp, StellarBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            OutlinedButton(
                onClick = viewModel::toggle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (state.expanded) "MASQUER • FLATS JOUR" else "FLATS JOUR • POUSSIÈRES & VIGNETTAGE",
                    fontWeight = FontWeight.Bold
                )
            }

            if (!state.expanded) return@Column

            Spacer(Modifier.height(8.dp))
            Text(
                "Éclairez uniformément l'ouverture (écran à flats / ciel clair diffus). Ne tournez pas la caméra et ne changez ni filtre, binning, ROI ni train optique avant l'observation.",
                color = StellarText
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "La température et le temps de pose des LIGHTS n'ont pas à correspondre. StellarPilot réutilisera le Master Flat compatible le plus récent.",
                color = StellarMuted
            )
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.changeExposure(-0.05) },
                    enabled = !state.isLoading && state.sessionId == null,
                    modifier = Modifier.weight(1f)
                ) { Text("− 0,05 s") }
                Text(
                    "${String.format(Locale.FRANCE, "%.2f", state.exposureSeconds)} s",
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = StellarText,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = { viewModel.changeExposure(+0.05) },
                    enabled = !state.isLoading && state.sessionId == null,
                    modifier = Modifier.weight(1f)
                ) { Text("+ 0,05 s") }
            }

            if (state.isLoading || state.capturedCount > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = if (state.requestedCount > 0) {
                        state.capturedCount.toFloat() / state.requestedCount.toFloat()
                    } else 0f,
                    modifier = Modifier.fillMaxWidth(),
                    color = StellarOrange
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "${state.capturedCount}/${state.requestedCount} • ${state.validCount} valides",
                    color = StellarMuted
                )
            }

            state.message?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = if (state.masterFlatPath != null) StellarGreen else StellarOrange)
            }
            state.error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = StellarRed)
            }
            if (state.cameraName != null) {
                Spacer(Modifier.height(5.dp))
                Text(
                    "${state.cameraName} • gain ${state.gain ?: "—"} • offset ${state.offset ?: "—"} • ${state.bayerPattern ?: "Bayer ?"}",
                    color = StellarMuted
                )
            }

            Spacer(Modifier.height(10.dp))
            if (state.sessionId == null) {
                Button(
                    onClick = { viewModel.start(serverBaseUrl) },
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StellarOrange,
                        contentColor = StellarBackground
                    )
                ) {
                    Text("CAPTURER 20 FLATS", fontWeight = FontWeight.Bold)
                }
            } else if (!state.isLoading) {
                OutlinedButton(
                    onClick = viewModel::reset,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("NOUVELLE SÉRIE DE FLATS")
                }
            }
        }
    }
}
