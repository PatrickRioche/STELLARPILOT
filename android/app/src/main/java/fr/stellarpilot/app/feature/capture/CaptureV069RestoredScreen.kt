package fr.stellarpilot.app.feature.capture

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.stellarpilot.app.feature.preparation.BahtinovViewModel
import fr.stellarpilot.app.ui.components.StellarImagePreview
import fr.stellarpilot.app.ui.theme.StellarBackground
import fr.stellarpilot.app.ui.theme.StellarBorder
import fr.stellarpilot.app.ui.theme.StellarGreen
import fr.stellarpilot.app.ui.theme.StellarMuted
import fr.stellarpilot.app.ui.theme.StellarOrange
import fr.stellarpilot.app.ui.theme.StellarRed
import fr.stellarpilot.app.ui.theme.StellarSurface
import java.util.Locale

@Composable
fun CaptureV069RestoredScreen(
    serverBaseUrl: String,
    bahtinovViewModel: BahtinovViewModel = viewModel(),
    captureViewModel: CaptureViewModel = viewModel()
) {
    var bahtinovExpanded by rememberSaveable { mutableStateOf(false) }
    val bahtinov = bahtinovViewModel.uiState

    // CaptureViewModel is activity-scoped and survives tab changes. Re-read the
    // target persisted by Ciel every time this Capture composition is entered
    // so CIBLE ACTIVE always reflects the observer's latest selection.
    LaunchedEffect(serverBaseUrl) {
        captureViewModel.loadSelectedTarget()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = StellarSurface),
            border = BorderStroke(1.dp, StellarBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                OutlinedButton(
                    onClick = { bahtinovExpanded = !bahtinovExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (bahtinovExpanded) {
                            "MASQUER • MISE AU POINT BAHTINOV"
                        } else {
                            "MISE AU POINT BAHTINOV"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }

                if (bahtinovExpanded) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Pointez une étoile brillante, installez le masque de Bahtinov puis ajustez le focuser. StellarPilot mesure le motif sur des poses de 4 s.",
                        color = StellarMuted
                    )
                    Spacer(Modifier.height(8.dp))

                    StellarImagePreview(
                        imageBytes = bahtinov.imageBytes,
                        contentDescription = "Motif Bahtinov dans Capture",
                        loadingText = if (bahtinov.isLoading) "Pose Bahtinov 4 s…" else null,
                        emptyText = "Aucune mesure Bahtinov",
                        showCrosshair = true
                    )

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Score : ${bahtinov.focusScore?.let { "$it/100" } ?: "—"} ${bahtinov.focusLabel ?: ""}",
                        color = if (bahtinov.focusValidated) StellarGreen else StellarMuted
                    )
                    Text(
                        "Écart optimum : ${bahtinov.focusErrorPx?.let { String.format(Locale.FRANCE, "%+.2f px", it) } ?: "—"}",
                        color = StellarMuted
                    )
                    Text(
                        "Confirmation optimum : ${bahtinov.optimumStreak}/2",
                        color = if (bahtinov.focusValidated) StellarGreen else StellarMuted
                    )

                    bahtinov.focusInstruction?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = StellarOrange, fontWeight = FontWeight.SemiBold)
                    }
                    bahtinov.message?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            it,
                            color = if (bahtinov.focusValidated) StellarGreen else StellarOrange,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    bahtinov.error?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = StellarRed)
                    }

                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { bahtinovViewModel.captureFocus(serverBaseUrl) },
                        enabled = !bahtinov.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StellarOrange,
                            contentColor = StellarBackground
                        )
                    ) {
                        Text(
                            if (bahtinov.imageBytes == null) {
                                "MESURER LE BAHTINOV • 4 s"
                            } else {
                                "NOUVELLE MESURE • 4 s"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = bahtinovViewModel::resetFocus,
                        enabled = !bahtinov.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("RÉINITIALISER LE BAHTINOV")
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            CaptureScreen(
                serverBaseUrl = serverBaseUrl,
                viewModel = captureViewModel
            )
        }
    }
}
