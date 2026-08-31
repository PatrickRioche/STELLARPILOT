package fr.stellarpilot.app.feature.sky

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.stellarpilot.app.domain.model.SkyObject
import fr.stellarpilot.app.ui.theme.StellarBackground
import fr.stellarpilot.app.ui.theme.StellarBorder
import fr.stellarpilot.app.ui.theme.StellarGreen
import fr.stellarpilot.app.ui.theme.StellarMuted
import fr.stellarpilot.app.ui.theme.StellarOrange
import fr.stellarpilot.app.ui.theme.StellarRed
import fr.stellarpilot.app.ui.theme.StellarSurface
import fr.stellarpilot.app.ui.theme.StellarSurfaceRaised
import fr.stellarpilot.app.ui.theme.StellarText
import java.util.Locale


@Composable
fun SolarSystemPanel(
    serverBaseUrl: String,
    observerKey: String,
    viewModel: SkyTargetViewModel =
        viewModel(
            key = "solar-system-targets"
        )
) {
    val state = viewModel.uiState
    val context = LocalContext.current

    val observerParts =
        observerKey.split(
            ":",
            limit = 2
        )

    val observerLatitude =
        observerParts
            .getOrNull(0)
            ?.toDoubleOrNull()

    val observerLongitude =
        observerParts
            .getOrNull(1)
            ?.toDoubleOrNull()

    var pendingSolarTarget by remember {
        mutableStateOf<SkyObject?>(null)
    }

    var gotoTargetId by rememberSaveable {
        mutableStateOf<Int?>(null)
    }

    fun loadTargets() {
        if (
            observerLatitude != null &&
            observerLongitude != null
        ) {
            viewModel.load(
                serverBaseUrl = serverBaseUrl,
                category = "solar_system",
                query = "",
                minAltitude = 0.0,
                direction = null,
                constellation = "",
                latitude = observerLatitude,
                longitude = observerLongitude,
                sort = "altitude",
                order = "desc"
            )
        }
    }

    fun selectTarget(target: SkyObject) {
        viewModel.select(target)

        context
            .getSharedPreferences(
                "stellarpilot_target",
                0
            )
            .edit()
            .putInt("id", target.id)
            .putString("name", target.name)
            .putString("reference", target.reference)
            .putString(
                "object_type",
                target.objectType
            )
            .putString(
                "constellation",
                target.constellation
            )
            .putString(
                "ra_hours",
                target.raHours.toString()
            )
            .putString(
                "dec_deg",
                target.decDeg.toString()
            )
            .apply()
    }

    fun gotoTarget(target: SkyObject) {
        if (target.solarWarning) {
            pendingSolarTarget = target
        } else {
            gotoTargetId = target.id
            viewModel.gotoTarget(
                serverBaseUrl = serverBaseUrl,
                target = target
            )
        }
    }

    LaunchedEffect(
        serverBaseUrl,
        observerKey
    ) {
        loadTargets()
    }

    pendingSolarTarget?.let { target ->
        AlertDialog(
            onDismissRequest = {
                pendingSolarTarget = null
            },
            containerColor = StellarSurface,
            titleContentColor = StellarRed,
            textContentColor = StellarText,
            title = {
                Text(
                    text =
                        "⚠ DANGER — OBSERVATION SOLAIRE",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text =
                        "Ne pointez jamais un télescope vers le Soleil sans filtre solaire adapté correctement installé. Une observation non protégée peut provoquer des lésions oculaires graves et endommager le matériel."
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingSolarTarget = null
                    }
                ) {
                    Text("ANNULER")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        gotoTargetId = target.id
                        pendingSolarTarget = null
                        viewModel.gotoTarget(
                            serverBaseUrl =
                                serverBaseUrl,
                            target = target
                        )
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = StellarRed,
                            contentColor = StellarBackground
                        )
                ) {
                    Text(
                        text =
                            "JE CONFIRME — GOTO SOLEIL",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    StellarSurfaceRaised
            ),
        border =
            BorderStroke(
                1.dp,
                StellarBorder
            )
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
        ) {
            Text(
                text = "SYSTÈME SOLAIRE",
                color = StellarOrange,
                style =
                    MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                Modifier.height(6.dp)
            )

            Text(
                text =
                    "Positions calculées pour l'heure et le lieu d'observation actuels.",
                color = StellarMuted
            )

            state.selected?.let { selectedTarget ->
                Spacer(
                    Modifier.height(16.dp)
                )

                SelectedSolarSystemTargetCard(
                    target = selectedTarget,
                    isGotoLoading =
                        state.isGotoLoading,
                    showGotoStatus =
                        gotoTargetId ==
                            selectedTarget.id,
                    gotoMessage =
                        state.gotoMessage,
                    gotoError =
                        state.gotoError,
                    gotoProgress =
                        state.gotoProgress,
                    onGoto = {
                        gotoTarget(selectedTarget)
                    }
                )
            }

            Spacer(
                Modifier.height(14.dp)
            )

            OutlinedButton(
                onClick = {
                    loadTargets()
                },
                enabled =
                    !state.isLoading &&
                        observerLatitude != null &&
                        observerLongitude != null,
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Text("Actualiser les positions")
            }

            Spacer(
                Modifier.height(16.dp)
            )

            when {
                observerLatitude == null ||
                    observerLongitude == null -> {
                    Text(
                        text =
                            "Position observateur indisponible.",
                        color = StellarRed
                    )
                }

                state.isLoading &&
                    state.result == null -> {
                    CircularProgressIndicator(
                        color = StellarOrange,
                        modifier =
                            Modifier.size(30.dp)
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Text(
                        text =
                            "Calcul des éphémérides...",
                        color = StellarMuted
                    )
                }

                state.error != null &&
                    state.result == null -> {
                    Text(
                        text =
                            state.error
                                ?: "Erreur éphémérides",
                        color = StellarRed
                    )
                }

                else -> {
                    val result = state.result

                    if (result != null) {
                        Text(
                            text =
                                "${result.visibleCount} au-dessus de l'horizon • ${result.returnedCount} corps calculés",
                            color = StellarMuted,
                            style =
                                MaterialTheme.typography.bodySmall
                        )

                        Spacer(
                            Modifier.height(12.dp)
                        )

                        result.objects
                            .forEach { target ->
                                SolarSystemTargetCard(
                                    target = target,
                                    selected =
                                        state.selected?.id ==
                                            target.id,
                                    onSelect = {
                                        selectTarget(target)
                                    }
                                )

                                Spacer(
                                    Modifier.height(10.dp)
                                )
                            }
                    }
                }
            }
        }
    }
}


@Composable
private fun SelectedSolarSystemTargetCard(
    target: SkyObject,
    isGotoLoading: Boolean,
    showGotoStatus: Boolean,
    gotoMessage: String?,
    gotoError: String?,
    gotoProgress: Double?,
    onGoto: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = StellarBackground
            ),
        border =
            BorderStroke(
                1.dp,
                StellarGreen
            )
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
        ) {
            Text(
                text = "CIBLE SÉLECTIONNÉE",
                color = StellarGreen,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                Modifier.height(8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text =
                            target.symbol ?: "●",
                        color =
                            if (target.solarWarning) {
                                StellarRed
                            } else {
                                StellarOrange
                            },
                        style =
                            MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Column {
                        Text(
                            text = target.name,
                            color = StellarText,
                            style =
                                MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text =
                                target.objectTypeLabelFr,
                            color = StellarMuted
                        )
                    }
                }

                Text(
                    text =
                        if (target.aboveHorizon) {
                            "VISIBLE"
                        } else {
                            "SOUS HORIZON"
                        },
                    color =
                        if (target.aboveHorizon) {
                            StellarGreen
                        } else {
                            StellarMuted
                        },
                    style =
                        MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(
                Modifier.height(8.dp)
            )

            Text(
                text =
                    "AD ${formatSolarNumber(target.raHours, 4)} h • Dec ${formatSolarSigned(target.decDeg)}°",
                color = StellarMuted
            )

            Text(
                text =
                    "Alt ${formatSolarSigned(target.altitudeDeg)}° • Az ${formatSolarNumber(target.azimuthDeg, 1)}° ${target.azimuthDirection}",
                color = StellarMuted
            )

            if (target.solarWarning) {
                Spacer(
                    Modifier.height(8.dp)
                )

                Text(
                    text =
                        "Filtre solaire adapté obligatoire avant tout pointage.",
                    color = StellarRed,
                    fontWeight = FontWeight.Bold,
                    style =
                        MaterialTheme.typography.bodySmall
                )
            }

            Spacer(
                Modifier.height(14.dp)
            )

            Button(
                onClick = onGoto,
                enabled =
                    target.aboveHorizon &&
                        !isGotoLoading,
                modifier =
                    Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = StellarOrange,
                        contentColor = StellarBackground,
                        disabledContainerColor =
                            StellarSurfaceRaised,
                        disabledContentColor =
                            StellarMuted
                    )
            ) {
                Text(
                    text =
                        when {
                            !target.aboveHorizon ->
                                "Pointage indisponible — sous l'horizon"

                            isGotoLoading &&
                                showGotoStatus ->
                                "Pointage en cours..."

                            else ->
                                "Pointer la cible"
                        },
                    fontWeight = FontWeight.Bold
                )
            }

            if (showGotoStatus) {
                gotoMessage?.let { message ->
                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Text(
                        text = message,
                        color = StellarGreen
                    )
                }

                gotoError?.let { message ->
                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Text(
                        text = message,
                        color = StellarRed
                    )
                }

                gotoProgress?.let { progress ->
                    Text(
                        text =
                            "Progression ${formatSolarNumber(progress * 100.0, 0)} %",
                        color = StellarMuted,
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}


@Composable
private fun SolarSystemTargetCard(
    target: SkyObject,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val cardAlpha =
        if (target.aboveHorizon) {
            1f
        } else {
            0.5f
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(cardAlpha),
        shape = RoundedCornerShape(14.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = StellarBackground
            ),
        border =
            BorderStroke(
                1.dp,
                if (selected) {
                    StellarOrange
                } else {
                    StellarBorder
                }
            )
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text =
                            target.symbol ?: "●",
                        color =
                            if (target.solarWarning) {
                                StellarRed
                            } else {
                                StellarOrange
                            },
                        style =
                            MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Column {
                        Text(
                            text = target.name,
                            color = StellarText,
                            style =
                                MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text =
                                target.objectTypeLabelFr,
                            color = StellarMuted,
                            style =
                                MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Text(
                    text =
                        if (target.aboveHorizon) {
                            "VISIBLE"
                        } else {
                            "SOUS HORIZON"
                        },
                    color =
                        if (target.aboveHorizon) {
                            StellarGreen
                        } else {
                            StellarMuted
                        },
                    style =
                        MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(
                Modifier.height(10.dp)
            )

            Text(
                text =
                    "AD ${formatSolarNumber(target.raHours, 4)} h • Dec ${formatSolarSigned(target.decDeg)}°",
                color = StellarMuted
            )

            Text(
                text =
                    "Alt ${formatSolarSigned(target.altitudeDeg)}° • Az ${formatSolarNumber(target.azimuthDeg, 1)}° ${target.azimuthDirection}",
                color = StellarMuted
            )

            if (target.solarWarning) {
                Spacer(
                    Modifier.height(8.dp)
                )

                Text(
                    text =
                        "Filtre solaire adapté obligatoire avant tout pointage.",
                    color = StellarRed,
                    fontWeight = FontWeight.Bold,
                    style =
                        MaterialTheme.typography.bodySmall
                )
            }

            Spacer(
                Modifier.height(12.dp)
            )

            if (selected) {
                Button(
                    onClick = onSelect,
                    enabled = target.aboveHorizon,
                    modifier =
                        Modifier.fillMaxWidth(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = StellarOrange,
                            contentColor = StellarBackground,
                            disabledContainerColor =
                                StellarSurfaceRaised,
                            disabledContentColor =
                                StellarMuted
                        )
                ) {
                    Text(
                        text = "Cible sélectionnée",
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onSelect,
                    enabled = target.aboveHorizon,
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        text =
                            if (target.aboveHorizon) {
                                "Choisir cette cible"
                            } else {
                                "Sous l'horizon"
                            }
                    )
                }
            }
        }
    }
}


private fun formatSolarNumber(
    value: Double,
    decimals: Int
): String =
    String.format(
        Locale.FRANCE,
        "%.${decimals}f",
        value
    )


private fun formatSolarSigned(
    value: Double
): String =
    String.format(
        Locale.FRANCE,
        "%+.1f",
        value
    )
