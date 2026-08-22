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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.stellarpilot.app.R
import fr.stellarpilot.app.domain.model.SkyStar
import fr.stellarpilot.app.ui.theme.StellarBackground
import fr.stellarpilot.app.ui.theme.StellarBorder
import fr.stellarpilot.app.ui.theme.StellarGreen
import fr.stellarpilot.app.ui.theme.StellarMuted
import fr.stellarpilot.app.ui.theme.StellarOrange
import fr.stellarpilot.app.ui.theme.StellarSurface
import fr.stellarpilot.app.ui.theme.StellarSurfaceRaised
import fr.stellarpilot.app.ui.theme.StellarText
import java.util.Locale

private enum class SkySortMode {
    MAGNITUDE,
    ALTITUDE,
    AZIMUTH
}

@Composable
fun SkyScreen(
    serverBaseUrl: String,
    viewModel: SkyViewModel = viewModel()
) {
    val state = viewModel.uiState
    val sky = state.sky

    

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
var sortMode by remember {
        mutableStateOf(SkySortMode.MAGNITUDE)
    }

    var directionFilter by remember {
        mutableStateOf<String?>(null)
    }

    var minAltitude by remember {
        mutableStateOf<Double?>(null)
    }

    var maxMagnitude by remember {
        mutableStateOf<Double?>(null)
    }

    LaunchedEffect(serverBaseUrl) {
        viewModel.load(serverBaseUrl)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = StellarBackground
    ) {

        Column(
            modifier = Modifier
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
                text = "Ciel observable",
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
                    "Étoiles calculées pour la position et l'heure actuelles",
                color = StellarMuted
            )

            Spacer(
                Modifier.height(22.dp)
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
                            "Calcul du ciel en cours...",
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
                            text = state.error,
                            color = StellarMuted
                        )
                    }
                }

                sky?.status == "location_required" || editManualLocation -> {

                    SkyCard {

                        Text(
                            text = "POSITION NÉCESSAIRE",
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
                                "Le GPS n'est pas fixé. Indique la position du lieu d'observation.",
                            color = StellarMuted
                        )

                        Spacer(
                            Modifier.height(16.dp)
                        )

                        OutlinedTextField(
                            value = manualLatitude,
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
                            value = manualLongitude,
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

                        Spacer(
                            Modifier.height(16.dp)
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
                    }
                }
                sky != null -> {

                    val observer =
                        sky.observer

                    if (
                        observer?.locationSource ==
                            "manual"
                    ) {

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

                        Spacer(
                            Modifier.height(12.dp)
                        )
                    }

                    SkyCard {

                        Text(
                            text = "POSITION",
                            color = StellarOrange,
                            style =
                                MaterialTheme.typography.labelLarge,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Spacer(
                            Modifier.height(8.dp)
                        )

                        SkyInfoRow(
                            label = "Latitude",
                            value =
                                observer
                                    ?.latitude
                                    ?.let {
                                        formatNumber(
                                            it,
                                            5
                                        ) + "°"
                                    }
                                    ?: "—"
                        )

                        SkyInfoRow(
                            label = "Longitude",
                            value =
                                observer
                                    ?.longitude
                                    ?.let {
                                        formatNumber(
                                            it,
                                            5
                                        ) + "°"
                                    }
                                    ?: "—"
                        )

                        SkyInfoRow(
                            label = "Source",
                            value =
                                when (
                                    observer
                                        ?.locationSource
                                ) {
                                    "gps" ->
                                        "GPS"
                                    "manual" ->
                                        "Manuelle"
                                    "query" ->
                                        "Test"
                                    else ->
                                        observer
                                            ?.locationSource
                                            ?: "—"
                                }
                        )
                    }

                    Spacer(
                        Modifier.height(16.dp)
                    )

                    sky.recommended?.let {
                        recommended ->

                        RecommendedStarCard(
                            star = recommended
                        )

                        Spacer(
                            Modifier.height(20.dp)
                        )
                    }

                    FilterCard(
                        sortMode = sortMode,
                        onSortModeChange = {
                            sortMode = it
                        },
                        directionFilter =
                            directionFilter,
                        onDirectionChange = {
                            directionFilter = it
                        },
                        minAltitude =
                            minAltitude,
                        onAltitudeChange = {
                            minAltitude = it
                        },
                        maxMagnitude =
                            maxMagnitude,
                        onMagnitudeChange = {
                            maxMagnitude = it
                        }
                    )

                    Spacer(
                        Modifier.height(18.dp)
                    )

                    val filteredStars =
                        sky.stars
                            .filter { star ->
                                directionFilter == null ||
                                    star.azimuthDirection ==
                                    directionFilter
                            }
                            .filter { star ->
                                minAltitude == null ||
                                    star.altitudeDeg >=
                                    minAltitude!!
                            }
                            .filter { star ->
                                maxMagnitude == null ||
                                    star.magnitude <=
                                    maxMagnitude!!
                            }

                    val displayedStars =
                        when (sortMode) {

                            SkySortMode.MAGNITUDE ->
                                filteredStars.sortedBy {
                                    it.magnitude
                                }

                            SkySortMode.ALTITUDE ->
                                filteredStars.sortedByDescending {
                                    it.altitudeDeg
                                }

                            SkySortMode.AZIMUTH ->
                                filteredStars.sortedBy {
                                    it.azimuthDeg
                                }
                        }

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {

                        Text(
                            text = "ÉTOILES VISIBLES",
                            color = StellarOrange,
                            style =
                                MaterialTheme.typography.labelLarge,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            text =
                                "${displayedStars.size} / ${sky.aboveHorizonCount}",
                            color = StellarMuted
                        )
                    }

                    Spacer(
                        Modifier.height(10.dp)
                    )

                    if (displayedStars.isEmpty()) {

                        SkyCard {
                            Text(
                                text =
                                    "Aucune étoile ne correspond aux filtres sélectionnés.",
                                color = StellarMuted
                            )
                        }

                    } else {

                        displayedStars.forEach {
                            star ->

                            StarRow(
                                star = star
                            )

                            Spacer(
                                Modifier.height(8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(
                Modifier.height(18.dp)
            )

            Button(
                onClick = {
                    viewModel.load(
                        serverBaseUrl
                    )
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
                    text = "Actualiser le ciel",
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
private fun FilterCard(
    sortMode: SkySortMode,
    onSortModeChange: (SkySortMode) -> Unit,
    directionFilter: String?,
    onDirectionChange: (String?) -> Unit,
    minAltitude: Double?,
    onAltitudeChange: (Double?) -> Unit,
    maxMagnitude: Double?,
    onMagnitudeChange: (Double?) -> Unit
) {
    SkyCard {

        Text(
            text = "TRI & FILTRES",
            color = StellarOrange,
            style =
                MaterialTheme.typography.labelLarge,
            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            Modifier.height(12.dp)
        )

        Text(
            text = "Trier par",
            color = StellarMuted,
            style =
                MaterialTheme.typography.bodySmall
        )

        Spacer(
            Modifier.height(6.dp)
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
                    sortMode ==
                        SkySortMode.MAGNITUDE,
                onClick = {
                    onSortModeChange(
                        SkySortMode.MAGNITUDE
                    )
                },
                label = {
                    Text("Magnitude")
                }
            )

            FilterChip(
                selected =
                    sortMode ==
                        SkySortMode.ALTITUDE,
                onClick = {
                    onSortModeChange(
                        SkySortMode.ALTITUDE
                    )
                },
                label = {
                    Text("Altitude")
                }
            )

            FilterChip(
                selected =
                    sortMode ==
                        SkySortMode.AZIMUTH,
                onClick = {
                    onSortModeChange(
                        SkySortMode.AZIMUTH
                    )
                },
                label = {
                    Text("Azimut")
                }
            )
        }

        Spacer(
            Modifier.height(14.dp)
        )

        Text(
            text = "Direction",
            color = StellarMuted,
            style =
                MaterialTheme.typography.bodySmall
        )

        Spacer(
            Modifier.height(6.dp)
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

            val directions =
                listOf(
                    null,
                    "N",
                    "NE",
                    "E",
                    "SE",
                    "S",
                    "SW",
                    "W",
                    "NW"
                )

            directions.forEach {
                direction ->

                FilterChip(
                    selected =
                        directionFilter ==
                            direction,
                    onClick = {
                        onDirectionChange(
                            direction
                        )
                    },
                    label = {
                        Text(
                            direction
                                ?: "Tous"
                        )
                    }
                )
            }
        }

        Spacer(
            Modifier.height(14.dp)
        )

        Text(
            text = "Altitude minimale",
            color = StellarMuted,
            style =
                MaterialTheme.typography.bodySmall
        )

        Spacer(
            Modifier.height(6.dp)
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

            val altitudes =
                listOf<Double?>(
                    null,
                    25.0,
                    40.0,
                    60.0
                )

            altitudes.forEach {
                altitude ->

                FilterChip(
                    selected =
                        minAltitude ==
                            altitude,
                    onClick = {
                        onAltitudeChange(
                            altitude
                        )
                    },
                    label = {
                        Text(
                            altitude?.let {
                                "${it.toInt()}°+"
                            } ?: "Toutes"
                        )
                    }
                )
            }
        }

        Spacer(
            Modifier.height(14.dp)
        )

        Text(
            text = "Magnitude maximale",
            color = StellarMuted,
            style =
                MaterialTheme.typography.bodySmall
        )

        Spacer(
            Modifier.height(6.dp)
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

            val magnitudes =
                listOf<Double?>(
                    null,
                    0.0,
                    1.0,
                    2.0
                )

            magnitudes.forEach {
                magnitude ->

                FilterChip(
                    selected =
                        maxMagnitude ==
                            magnitude,
                    onClick = {
                        onMagnitudeChange(
                            magnitude
                        )
                    },
                    label = {
                        Text(
                            magnitude?.let {
                                "≤ ${it.toInt()}"
                            } ?: "Toutes"
                        )
                    }
                )
            }
        }
    }
}


@Composable
private fun RecommendedStarCard(
    star: SkyStar
) {
    Card(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(22.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    StellarSurfaceRaised
            ),
        border =
            BorderStroke(
                1.dp,
                StellarGreen.copy(
                    alpha = 0.55f
                )
            )
    ) {

        Column(
            modifier =
                Modifier.padding(20.dp)
        ) {

            Text(
                text =
                    "★ ÉTOILE RECOMMANDÉE",
                color = StellarGreen,
                style =
                    MaterialTheme.typography.labelLarge,
                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                Modifier.height(10.dp)
            )

            Text(
                text = star.name,
                color = StellarText,
                style =
                    MaterialTheme.typography.headlineSmall,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                text = "Constellation : " + star.constellation,
                color = StellarMuted
            )

            Spacer(
                Modifier.height(14.dp)
            )

            SkyInfoRow(
                label = "Magnitude",
                value =
                    formatNumber(
                        star.magnitude,
                        2
                    )
            )

            SkyInfoRow(
                label = "Altitude",
                value =
                    formatNumber(
                        star.altitudeDeg,
                        1
                    ) + "°"
            )

            SkyInfoRow(
                label = "Azimut",
                value =
                    formatNumber(
                        star.azimuthDeg,
                        1
                    ) +
                        "° " +
                        star.azimuthDirection
            )

            SkyInfoRow(
                label = "Score",
                value =
                    star.alignmentScore
                        ?.let {
                            formatNumber(
                                it * 100.0,
                                0
                            ) + " %"
                        }
                        ?: "—"
            )
        }
    }
}


private fun skyObjectIconResource(
    objectType: String
): Int =
    when (
        objectType.lowercase(Locale.ROOT)
    ) {
        "star" ->
            R.drawable.ic_object_star

        "star_double" ->
            R.drawable.ic_object_star_double

        "star_triple" ->
            R.drawable.ic_object_star_triple

        "star_multiple" ->
            R.drawable.ic_object_star_multiple

        "cluster_open" ->
            R.drawable.ic_object_cluster_open

        "cluster_globular" ->
            R.drawable.ic_object_cluster_globular

        "nebula_diffuse" ->
            R.drawable.ic_object_nebula_diffuse

        "nebula_planetary" ->
            R.drawable.ic_object_nebula_planetary

        "nebula_dark" ->
            R.drawable.ic_object_nebula_dark

        "supernova_remnant" ->
            R.drawable.ic_object_supernova_remnant

        "galaxy" ->
            R.drawable.ic_object_galaxy

        "galaxy_pair" ->
            R.drawable.ic_object_galaxy_pair

        "galaxy_group" ->
            R.drawable.ic_object_galaxy_group

        "planet" ->
            R.drawable.ic_object_planet

        "moon" ->
            R.drawable.ic_object_moon

        "comet" ->
            R.drawable.ic_object_comet

        "asterism" ->
            R.drawable.ic_object_asterism

        else ->
            R.drawable.ic_object_unknown
    }

@Composable
private fun StarRow(
    star: SkyStar
) {
    Card(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(16.dp),
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
                Modifier.padding(14.dp)
        ) {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Icon(
                    painter = painterResource(
                        id = skyObjectIconResource(
                            star.objectType
                        )
                    ),
                    contentDescription = null,
                    tint = StellarOrange,
                    modifier = Modifier.size(26.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {

                    Text(
                        text = star.name,
                        color = StellarText,
                        fontWeight =
                            FontWeight.SemiBold
                    )

                    Text(
                        text = "Constellation : " + star.constellation,
                        color = StellarMuted,
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                }

                Text(
                    text =
                        "mag " +
                            formatNumber(
                                star.magnitude,
                                2
                            ),
                    color = StellarMuted,
                    style =
                        MaterialTheme.typography.bodySmall
                )
            }

            Spacer(
                Modifier.height(8.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(
                    text =
                        "Alt " +
                            formatNumber(
                                star.altitudeDeg,
                                1
                            ) +
                            "°",
                    color =
                        if (
                            star.alignmentCandidate
                        )
                            StellarGreen
                        else
                            StellarMuted
                )

                Text(
                    text =
                        "Az " +
                            formatNumber(
                                star.azimuthDeg,
                                1
                            ) +
                            "° " +
                            star.azimuthDirection,
                    color = StellarMuted
                )
            }
        }
    }
}


@Composable
private fun SkyInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 3.dp
                ),
        horizontalArrangement =
            Arrangement.SpaceBetween
    ) {

        Text(
            text = label,
            color = StellarMuted
        )

        Text(
            text = value,
            color = StellarText,
            fontWeight =
                FontWeight.SemiBold
        )
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
                Modifier.padding(16.dp)
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
        Locale.US,
        "%.${decimals}f",
        value
    )