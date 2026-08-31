package fr.stellarpilot.app.feature.sky

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.stellarpilot.app.ui.theme.StellarBackground
import fr.stellarpilot.app.ui.theme.StellarBorder
import fr.stellarpilot.app.ui.theme.StellarMuted
import fr.stellarpilot.app.ui.theme.StellarOrange
import fr.stellarpilot.app.ui.theme.StellarSurface
import fr.stellarpilot.app.ui.theme.StellarText
import java.util.Locale


@Composable
fun SkyScreen(
    serverBaseUrl: String,
    viewModel: SkyViewModel = viewModel()
) {
    val state = viewModel.uiState
    val sky = state.sky

    val demoMode =
        fr.stellarpilot.app.feature.demo
            .DemoModeState.active

    val context = LocalContext.current

    val locationPreferences = remember {
        context.getSharedPreferences(
            "stellarpilot_location",
            0
        )
    }

    var manualLatitude by rememberSaveable {
        mutableStateOf(
            locationPreferences.getString(
                "last_latitude",
                ""
            ) ?: ""
        )
    }

    var manualLongitude by rememberSaveable {
        mutableStateOf(
            locationPreferences.getString(
                "last_longitude",
                ""
            ) ?: ""
        )
    }

    var editManualLocation by rememberSaveable {
        mutableStateOf(false)
    }

    var skyMode by rememberSaveable {
        mutableStateOf("solar_system")
    }

    LaunchedEffect(
        serverBaseUrl,
        demoMode
    ) {
        if (demoMode) {
            viewModel.loadDemoSnapshot()
        } else {
            viewModel.load(
                serverBaseUrl
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = StellarBackground
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(
                        rememberScrollState()
                    )
                    .padding(
                        horizontal = 24.dp,
                        vertical = 24.dp
                    )
        ) {
            Text(
                text = "CIEL & CIBLE",
                style =
                    MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = StellarOrange
            )

            Spacer(
                Modifier.height(8.dp)
            )

            Text(
                text = "Choisir une cible",
                style =
                    MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = StellarText
            )

            Spacer(
                Modifier.height(6.dp)
            )

            Text(
                text =
                    "Système solaire ou catalogue du ciel profond, calculés depuis votre position actuelle.",
                color = StellarMuted
            )

            Spacer(
                Modifier.height(20.dp)
            )

            when {
                state.isLoading &&
                    sky == null -> {

                    CircularProgressIndicator(
                        color = StellarOrange
                    )

                    Spacer(
                        Modifier.height(12.dp)
                    )

                    Text(
                        text =
                            "Calcul de la position du ciel...",
                        color = StellarMuted
                    )
                }

                state.error != null &&
                    sky == null -> {

                    SkyCard {
                        Text(
                            text =
                                "Impossible de calculer le ciel",
                            color = StellarOrange,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Spacer(
                            Modifier.height(8.dp)
                        )

                        Text(
                            text =
                                state.error
                                    ?: "Erreur inconnue",
                            color = StellarMuted
                        )
                    }
                }

                sky?.status ==
                    "location_required" ||
                    editManualLocation -> {

                    SkyCard {
                        Text(
                            text =
                                "POSITION NÉCESSAIRE",
                            color = StellarOrange,
                            style =
                                MaterialTheme.typography.labelLarge,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Spacer(
                            Modifier.height(8.dp)
                        )

                        Text(
                            text =
                                "Le GPS n'est pas fixé. Indiquez la position du lieu d'observation.",
                            color = StellarMuted
                        )

                        Spacer(
                            Modifier.height(16.dp)
                        )

                        OutlinedTextField(
                            value =
                                manualLatitude,
                            onValueChange = {
                                manualLatitude = it
                            },
                            modifier =
                                Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = {
                                Text("Latitude")
                            },
                            placeholder = {
                                Text("47.46094")
                            }
                        )

                        Spacer(
                            Modifier.height(10.dp)
                        )

                        OutlinedTextField(
                            value =
                                manualLongitude,
                            onValueChange = {
                                manualLongitude = it
                            },
                            modifier =
                                Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = {
                                Text("Longitude")
                            },
                            placeholder = {
                                Text("-0.61042")
                            }
                        )

                        val latitude =
                            manualLatitude
                                .trim()
                                .replace(",", ".")
                                .toDoubleOrNull()

                        val longitude =
                            manualLongitude
                                .trim()
                                .replace(",", ".")
                                .toDoubleOrNull()

                        Spacer(
                            Modifier.height(16.dp)
                        )

                        Button(
                            onClick = {
                                if (
                                    latitude != null &&
                                    longitude != null
                                ) {
                                    locationPreferences
                                        .edit()
                                        .putString(
                                            "last_latitude",
                                            manualLatitude
                                        )
                                        .putString(
                                            "last_longitude",
                                            manualLongitude
                                        )
                                        .apply()

                                    editManualLocation =
                                        false

                                    viewModel.setManualLocation(
                                        serverBaseUrl =
                                            serverBaseUrl,
                                        latitude =
                                            latitude,
                                        longitude =
                                            longitude
                                    )
                                }
                            },
                            enabled =
                                latitude != null &&
                                longitude != null &&
                                latitude in -90.0..90.0 &&
                                longitude in -180.0..180.0 &&
                                !state.isLoading,
                            modifier =
                                Modifier.fillMaxWidth(),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor =
                                        StellarOrange,
                                    contentColor =
                                        StellarBackground
                                )
                        ) {
                            Text(
                                text =
                                    "Utiliser cette position",
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }

                        if (
                            sky != null &&
                            sky.status !=
                                "location_required"
                        ) {
                            Spacer(
                                Modifier.height(8.dp)
                            )

                            OutlinedButton(
                                onClick = {
                                    editManualLocation =
                                        false
                                },
                                modifier =
                                    Modifier.fillMaxWidth()
                            ) {
                                Text("Annuler")
                            }
                        }
                    }
                }

                sky != null -> {
                    val observer =
                        sky.observer

                    val locationSourceLabel =
                        when (
                            observer?.locationSource
                        ) {
                            "gps" -> "GPS"
                            "manual" -> "Manuelle"
                            "query" -> "Test"
                            "demo" -> "Démo"
                            "onstep" -> "OnStep"
                            else ->
                                observer?.locationSource
                                    ?: "Inconnue"
                        }

                    SkyCard {
                        Text(
                            text =
                                "POSITION • $locationSourceLabel",
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
                                "${observer?.latitude?.let { formatNumber(it, 5) } ?: "—"}°  •  ${observer?.longitude?.let { formatNumber(it, 5) } ?: "—"}°",
                            color = StellarText,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (
                            observer?.locationSource ==
                                "manual"
                        ) {
                            Spacer(
                                Modifier.height(10.dp)
                            )

                            OutlinedButton(
                                onClick = {
                                    observer.latitude
                                        ?.let {
                                            manualLatitude =
                                                it.toString()
                                        }

                                    observer.longitude
                                        ?.let {
                                            manualLongitude =
                                                it.toString()
                                        }

                                    editManualLocation =
                                        true
                                },
                                modifier =
                                    Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Modifier la position"
                                )
                            }
                        }
                    }

                    Spacer(
                        Modifier.height(16.dp)
                    )

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(
                                    rememberScrollState()
                                ),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected =
                                skyMode ==
                                    "solar_system",
                            onClick = {
                                skyMode =
                                    "solar_system"
                            },
                            label = {
                                Text("Système solaire")
                            }
                        )

                        FilterChip(
                            selected =
                                skyMode ==
                                    "catalog",
                            onClick = {
                                skyMode = "catalog"
                            },
                            label = {
                                Text("Catalogue du ciel")
                            }
                        )
                    }

                    Spacer(
                        Modifier.height(14.dp)
                    )

                    val observerKey =
                        listOf(
                            observer?.latitude,
                            observer?.longitude
                        ).joinToString(":")

                    if (
                        skyMode ==
                            "solar_system"
                    ) {
                        SolarSystemPanel(
                            serverBaseUrl =
                                serverBaseUrl,
                            observerKey =
                                observerKey
                        )
                    } else {
                        TargetCatalogPanel(
                            serverBaseUrl =
                                serverBaseUrl,
                            observerKey =
                                observerKey
                        )
                    }
                }
            }

            Spacer(
                Modifier.height(18.dp)
            )

            Button(
                onClick = {
                    if (demoMode) {
                        viewModel.loadDemoSnapshot()
                    } else {
                        viewModel.load(
                            serverBaseUrl
                        )
                    }
                },
                enabled =
                    !state.isLoading,
                modifier =
                    Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            StellarOrange,
                        contentColor =
                            StellarBackground
                    )
            ) {
                Text(
                    text =
                        "Actualiser la position",
                    fontWeight =
                        FontWeight.Bold
                )
            }

            Spacer(
                Modifier.height(24.dp)
            )
        }
    }
}


@Composable
private fun SkyCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(18.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    StellarSurface
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
            content()
        }
    }
}


private fun formatNumber(
    value: Double,
    decimals: Int
): String =
    String.format(
        Locale.FRANCE,
        "%.${decimals}f",
        value
    )
