package fr.stellarpilot.app.feature.sky

import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.stellarpilot.app.R
import fr.stellarpilot.app.domain.model.SkyObject
import fr.stellarpilot.app.ui.theme.StellarBackground
import fr.stellarpilot.app.ui.theme.StellarBorder
import fr.stellarpilot.app.ui.theme.StellarGreen
import fr.stellarpilot.app.ui.theme.StellarMuted
import fr.stellarpilot.app.ui.theme.StellarOrange
import fr.stellarpilot.app.ui.theme.StellarSurfaceRaised
import fr.stellarpilot.app.ui.theme.StellarText
import java.util.Locale

private data class TargetCategory(
    val id: String,
    val label: String
)

private val targetCategories =
    listOf(
        TargetCategory(
            "all",
            "Tous"
        ),
        TargetCategory(
            "star",
            "\u00C9toiles"
        ),
        TargetCategory(
            "galaxy",
            "Galaxies"
        ),
        TargetCategory(
            "nebula",
            "N\u00E9buleuses"
        ),
        TargetCategory(
            "cluster",
            "Amas"
        )
    )

@Composable
fun TargetCatalogPanel(
    serverBaseUrl: String,
    observerKey: String,
    viewModel: SkyTargetViewModel = viewModel()
) {

    val state =
        viewModel.uiState

    val context =
        LocalContext.current

    var category by rememberSaveable {
        mutableStateOf("all")
    }

    var searchText by rememberSaveable {
        mutableStateOf("")
    }

    var minAltitude by rememberSaveable {
        mutableStateOf(15.0)
    }

    var direction by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var constellation by rememberSaveable {
        mutableStateOf("")
    }

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

    LaunchedEffect(
        serverBaseUrl,
        observerKey
    ) {

        if (
            observerLatitude != null &&
            observerLongitude != null
        ) {
            viewModel.load(
                serverBaseUrl =
                    serverBaseUrl,
                category =
                    category,
                query =
                    searchText,
                minAltitude =
                    minAltitude,
                direction =
                    direction,
                constellation =
                    constellation,
                latitude =
                    observerLatitude,
                longitude =
                    observerLongitude
            )
        }
    }

    val selectTarget:
        (SkyObject) -> Unit =
        { target ->

            viewModel.select(
                target
            )

            context
                .getSharedPreferences(
                    "stellarpilot_target",
                    0
                )
                .edit()
                .putInt(
                    "id",
                    target.id
                )
                .putString(
                    "name",
                    target.name
                )
                .putString(
                    "reference",
                    target.reference
                )
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

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(18.dp),
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
                Modifier.fillMaxWidth()
                    .then(
                        Modifier
                    )
        ) {

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
            ) {

                Text(
                    text =
                        "CHOISIR UNE CIBLE",
                    color =
                        StellarOrange,
                    style =
                        MaterialTheme
                            .typography
                            .labelLarge,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    Modifier.height(6.dp)
                )

                Text(
                    text =
                        "Catalogue local des objets visibles",
                    color =
                        StellarMuted
                )

                Spacer(
                    Modifier.height(16.dp)
                )

                state.selected?.let {
                    target ->

                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                        colors =
                            CardDefaults
                                .cardColors(
                                    containerColor =
                                        StellarBackground
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
                                text =
                                    "CIBLE S\u00C9LECTIONN\u00C9E",
                                color =
                                    StellarGreen,
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Spacer(
                                Modifier.height(6.dp)
                            )

                            Row(
                                modifier =
                                    Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        12.dp
                                    )
                            ) {

                                Icon(
                                    painter =
                                        painterResource(
                                            id =
                                                targetObjectIconResource(
                                                    target.objectType
                                                )
                                        ),
                                    contentDescription =
                                        target.objectTypeLabelFr,
                                    tint =
                                        StellarOrange,
                                    modifier =
                                        Modifier.size(
                                            40.dp
                                        )
                                )

                                Text(
                                    text =
                                        target.name,
                                    color =
                                        StellarText,
                                    style =
                                        MaterialTheme
                                            .typography
                                            .titleLarge,
                                    fontWeight =
                                        FontWeight.Bold,
                                    modifier =
                                        Modifier.weight(
                                            1f
                                        )
                                )
                            }

                            target.reference
                                ?.takeIf {
                                    it !=
                                        target.name
                                }
                                ?.let {

                                    Text(
                                        text = it,
                                        color =
                                            StellarOrange
                                    )
                                }

                            Spacer(
                                Modifier.height(6.dp)
                            )

                            Text(
                                text =
                                    buildString {

                                        append(
                                            target
                                                .objectTypeLabelFr
                                        )

                                        target
                                            .constellation
                                            ?.let {

                                                append(
                                                    " \u2022 "
                                                )

                                                append(it)
                                            }
                                    },
                                color =
                                    StellarMuted
                            )

                            Spacer(
                                Modifier.height(6.dp)
                            )

                            Text(
                                text =
                                    "AD ${
                                        formatTargetNumber(
                                            target.raHours,
                                            4
                                        )
                                    } h \u2022 Dec ${
                                        formatSigned(
                                            target.decDeg
                                        )
                                    }\u00B0",
                                color =
                                    StellarMuted
                            )
                            Spacer(
                                Modifier.height(
                                    14.dp
                                )
                            )

                            Button(
                                onClick = {
                                    viewModel.gotoTarget(
                                        serverBaseUrl =
                                            serverBaseUrl,
                                        target =
                                            target
                                    )
                                },
                                enabled =
                                    !state.isGotoLoading,
                                modifier =
                                    Modifier.fillMaxWidth()
                            ) {

                                Icon(
                                    painter =
                                        painterResource(
                                            id =
                                                R.drawable
                                                    .ic_action_goto_target
                                        ),
                                    contentDescription =
                                        "Pointer la cible",
                                    modifier =
                                        Modifier.size(
                                            22.dp
                                        )
                                )

                                Spacer(
                                    Modifier.size(
                                        8.dp
                                    )
                                )

                                Text(
                                    text =
                                        if (
                                            state.isGotoLoading
                                        ) {
                                            "Pointage en cours..."
                                        } else {
                                            "Pointer la cible"
                                        },
                                    fontWeight =
                                        FontWeight.Bold
                                )
                            }

                            state.gotoMessage
                                ?.let { message ->

                                    Spacer(
                                        Modifier.height(
                                            8.dp
                                        )
                                    )

                                    Text(
                                        text = message,
                                        color =
                                            StellarGreen
                                    )
                                }

                            state.gotoError
                                ?.let { message ->

                                    Spacer(
                                        Modifier.height(
                                            8.dp
                                        )
                                    )

                                    Text(
                                        text = message,
                                        color =
                                            StellarOrange
                                    )
                                }


                            state.gotoStatus
                                ?.let { gotoStatus ->

                                    Spacer(
                                        Modifier.height(
                                            10.dp
                                        )
                                    )

                                    if (
                                        state.gotoVirtualPosition
                                    ) {
                                        Text(
                                            text =
                                                "Position virtuelle OnStep",
                                            color =
                                                StellarMuted,
                                            fontWeight =
                                                FontWeight.Bold
                                        )
                                    }

                                    if (
                                        state.gotoCurrentRa != null &&
                                        state.gotoCurrentDec != null
                                    ) {
                                        Text(
                                            text =
                                                "AD actuelle ${
                                                    formatTargetNumber(
                                                        state.gotoCurrentRa,
                                                        4
                                                    )
                                                } h \u2022 Dec ${
                                                    formatSigned(
                                                        state.gotoCurrentDec
                                                    )
                                                }\u00B0",
                                            color =
                                                StellarMuted
                                        )
                                    }

                                    if (
                                        state.gotoTargetRa != null &&
                                        state.gotoTargetDec != null
                                    ) {
                                        Text(
                                            text =
                                                "AD cible ${
                                                    formatTargetNumber(
                                                        state.gotoTargetRa,
                                                        4
                                                    )
                                                } h \u2022 Dec ${
                                                    formatSigned(
                                                        state.gotoTargetDec
                                                    )
                                                }\u00B0",
                                            color =
                                                StellarMuted
                                        )
                                    }

                                    state.gotoProgress
                                        ?.let { progress ->

                                            Text(
                                                text =
                                                    "Progression ${
                                                        formatTargetNumber(
                                                            progress * 100.0,
                                                            0
                                                        )
                                                    } % \u2022 ${
                                                        gotoStatus.uppercase(
                                                            Locale.ROOT
                                                        )
                                                    }",
                                                color =
                                                    if (
                                                        progress >= 1.0
                                                    ) {
                                                        StellarGreen
                                                    } else {
                                                        StellarOrange
                                                    },
                                                fontWeight =
                                                    FontWeight.Bold
                                            )
                                        }

                                    state.gotoIndiState
                                        ?.let { indiState ->
                                            Text(
                                                text =
                                                    "INDI : $indiState",
                                                color =
                                                    StellarMuted,
                                                style =
                                                    MaterialTheme
                                                        .typography
                                                        .bodySmall
                                            )
                                        }
                                }
                        }
                    }

                    Spacer(
                        Modifier.height(16.dp)
                    )
                }

                OutlinedTextField(
                    value =
                        searchText,
                    onValueChange = {
                        searchText = it
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    singleLine = true,
                    label = {
                        Text(
                            "Rechercher un objet"
                        )
                    },
                    placeholder = {
                        Text(
                            "M31, M42, NGC 7000, Tourbillon..."
                        )
                    }
                )

                Spacer(
                    Modifier.height(8.dp)
                )

                Button(
                    onClick = {

                        viewModel.load(
                            serverBaseUrl =
                                serverBaseUrl,
                            category =
                                category,
                            query =
                                searchText,
                            minAltitude =
                                minAltitude,
                            direction =
                                direction,
                            constellation =
                                constellation
                        )
                    },
                    enabled =
                        !state.isLoading,
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    colors =
                        ButtonDefaults
                            .buttonColors(
                                containerColor =
                                    StellarOrange,
                                contentColor =
                                    StellarBackground
                            )
                ) {

                    Text(
                        text =
                            "Rechercher",
                        fontWeight =
                            FontWeight.Bold
                    )
                }

                Spacer(
                    Modifier.height(14.dp)
                )

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(
                                rememberScrollState()
                            ),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {

                    targetCategories
                        .forEach {
                            item ->

                            FilterChip(
                                selected =
                                    category ==
                                        item.id,

                                onClick = {

                                    category =
                                        item.id

                                    viewModel.load(
                                        serverBaseUrl =
                                            serverBaseUrl,
                                        category =
                                            item.id,
                                        query =
                                            searchText,
                                        minAltitude =
                                            minAltitude,
                                        direction =
                                            direction,
                                        constellation =
                                            constellation
                                    )
                                },

                                leadingIcon = {
                                    Icon(
                                        painter =
                                            painterResource(
                                                id =
                                                    targetCategoryIconResource(
                                                        item.id
                                                    )
                                            ),
                                        contentDescription =
                                            item.label,
                                        tint =
                                            StellarOrange,
                                        modifier =
                                            Modifier.size(
                                                18.dp
                                            )
                                    )
                                },

                                label = {
                                    Text(
                                        item.label
                                    )
                                }
                            )
                        }
                }

                Spacer(
                    Modifier.height(18.dp)
                )

                Text(
                    text = "FILTRES",
                    color = StellarOrange,
                    style =
                        MaterialTheme.typography.labelLarge,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    Modifier.height(10.dp)
                )

                Text(
                    text = "Hauteur minimale",
                    color = StellarMuted
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

                    listOf(
                        15.0,
                        30.0,
                        45.0,
                        60.0
                    ).forEach { altitude ->

                        FilterChip(
                            selected =
                                minAltitude ==
                                    altitude,

                            onClick = {

                                minAltitude =
                                    altitude

                                viewModel.load(
                                    serverBaseUrl =
                                        serverBaseUrl,
                                    category =
                                        category,
                                    query =
                                        searchText,
                                    minAltitude =
                                        altitude,
                                    direction =
                                        direction,
                                    constellation =
                                        constellation
                                )
                            },

                            label = {
                                Text(
                                    "${
                                        altitude.toInt()
                                    }\u00B0+"
                                )
                            }
                        )
                    }
                }

                Spacer(
                    Modifier.height(14.dp)
                )

                Text(
                    text =
                        "Localisation dans le ciel",
                    color =
                        StellarMuted
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
                    ).forEach { value ->

                        FilterChip(
                            selected =
                                direction ==
                                    value,

                            onClick = {

                                direction =
                                    value

                                viewModel.load(
                                    serverBaseUrl =
                                        serverBaseUrl,
                                    category =
                                        category,
                                    query =
                                        searchText,
                                    minAltitude =
                                        minAltitude,
                                    direction =
                                        value,
                                    constellation =
                                        constellation
                                )
                            },

                            label = {
                                Text(
                                    value
                                        ?: "Toutes"
                                )
                            }
                        )
                    }
                }

                Spacer(
                    Modifier.height(14.dp)
                )

                ConstellationMultiSelect(
                    selectedValue =
                        constellation,
                    onSelectedValueChange = {
                        constellation = it
                    }
                )

                Spacer(
                    Modifier.height(8.dp)
                )

                OutlinedButton(
                    onClick = {

                        viewModel.load(
                            serverBaseUrl =
                                serverBaseUrl,
                            category =
                                category,
                            query =
                                searchText,
                            minAltitude =
                                minAltitude,
                            direction =
                                direction,
                            constellation =
                                constellation
                        )
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Appliquer les filtres"
                    )
                }

                Spacer(
                    Modifier.height(14.dp)
                )

                if (state.isLoading) {

                    CircularProgressIndicator(
                        color =
                            StellarOrange
                    )

                    Spacer(
                        Modifier.height(10.dp)
                    )

                    Text(
                        text =
                            "Calcul des objets visibles...",
                        color =
                            StellarMuted
                    )

                } else if (
                    state.error != null
                ) {

                    Text(
                        text =
                            state.error,
                        color =
                            StellarOrange
                    )

                } else {

                    state.result?.let {
                        result ->

                        if (
                            result.status ==
                                "location_required"
                        ) {

                            Text(
                                text =
                                    "Une position est necessaire.",
                                color =
                                    StellarOrange
                            )

                        } else {

                            Text(
                                text =
                                    "TRIER PAR",
                                color =
                                    StellarOrange,
                                style =
                                    MaterialTheme
                                        .typography
                                        .labelLarge,
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Spacer(
                                Modifier.height(
                                    6.dp
                                )
                            )

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(
                                            rememberScrollState()
                                        ),
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        8.dp
                                    )
                            ) {

                                listOf(
                                    "magnitude" to
                                        "Magnitude",
                                    "altitude" to
                                        "Hauteur",
                                    "name" to
                                        "Nom"
                                ).forEach {
                                    option ->

                                    val sortId =
                                        option.first

                                    val selected =
                                        result.sort ==
                                            sortId

                                    val label =
                                        when (sortId) {

                                            "magnitude" ->
                                                if (selected) {
                                                    "Magnitude " +
                                                        if (
                                                            result.order ==
                                                                "asc"
                                                        ) {
                                                            "\u2191"
                                                        } else {
                                                            "\u2193"
                                                        }
                                                } else {
                                                    "Magnitude"
                                                }

                                            "altitude" ->
                                                if (selected) {
                                                    "Hauteur " +
                                                        if (
                                                            result.order ==
                                                                "asc"
                                                        ) {
                                                            "\u2191"
                                                        } else {
                                                            "\u2193"
                                                        }
                                                } else {
                                                    "Hauteur"
                                                }

                                            else ->
                                                if (selected) {
                                                    if (
                                                        result.order ==
                                                            "asc"
                                                    ) {
                                                        "Nom A-Z"
                                                    } else {
                                                        "Nom Z-A"
                                                    }
                                                } else {
                                                    "Nom"
                                                }
                                        }

                                    FilterChip(
                                        selected =
                                            selected,
                                        onClick = {

                                            val nextOrder =
                                                if (selected) {

                                                    if (
                                                        result.order ==
                                                            "asc"
                                                    ) {
                                                        "desc"
                                                    } else {
                                                        "asc"
                                                    }

                                                } else {

                                                    if (
                                                        sortId ==
                                                            "altitude"
                                                    ) {
                                                        "desc"
                                                    } else {
                                                        "asc"
                                                    }
                                                }

                                            viewModel.load(
                                                serverBaseUrl =
                                                    serverBaseUrl,
                                                category =
                                                    category,
                                                query =
                                                    searchText,
                                                minAltitude =
                                                    minAltitude,
                                                direction =
                                                    direction,
                                                constellation =
                                                    constellation,
                                                sort =
                                                    sortId,
                                                order =
                                                    nextOrder,
                                                offset =
                                                    0
                                            )
                                        },
                                        label = {
                                            Text(
                                                label
                                            )
                                        }
                                    )
                                }
                            }

                            Spacer(
                                Modifier.height(
                                    12.dp
                                )
                            )

                            val firstObject =
                                if (
                                    result.returnedCount >
                                        0
                                ) {
                                    result.offset + 1
                                } else {
                                    0
                                }

                            val lastObject =
                                result.offset +
                                    result.returnedCount

                            Text(
                                text =
                                    "Objets $firstObject-$lastObject / " +
                                        "${result.visibleCount} visibles",
                                color =
                                    StellarMuted
                            )

                            Text(
                                text =
                                    "Objets au-dessus de ${
                                        formatTargetNumber(
                                            result.minAltitudeDeg,
                                            0
                                        )
                                    }\u00B0",
                                color =
                                    StellarMuted,
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall
                            )

                            Spacer(
                                Modifier.height(12.dp)
                            )

                            if (
                                result.objects
                                    .isEmpty()
                            ) {

                                Text(
                                    text =
                                        "Aucun objet visible ne correspond a la recherche.",
                                    color =
                                        StellarMuted
                                )

                            } else {

                                result.objects
                                    .forEach {
                                        target ->

                                        TargetObjectRow(
                                            target =
                                                target,
                                            selected =
                                                state
                                                    .selected
                                                    ?.id ==
                                                    target.id,
                                            onSelect = {
                                                selectTarget(
                                                    target
                                                )
                                            }
                                        )

                                        Spacer(
                                            Modifier
                                                .height(
                                                    8.dp
                                                )
                                        )
                                    }

                                if (
                                    result.visibleCount >
                                        result.limit
                                ) {

                                    val pageSize =
                                        result.limit
                                            .coerceAtLeast(
                                                1
                                            )

                                    val currentPage =
                                        (
                                            result.offset /
                                                pageSize
                                            ) + 1

                                    val pageCount =
                                        maxOf(
                                            1,
                                            (
                                                result.visibleCount +
                                                    pageSize -
                                                    1
                                                ) /
                                                pageSize
                                        )

                                    Spacer(
                                        Modifier.height(
                                            8.dp
                                        )
                                    )

                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth(),
                                        horizontalArrangement =
                                            Arrangement.spacedBy(
                                                8.dp
                                            )
                                    ) {

                                        OutlinedButton(
                                            onClick = {

                                                viewModel.load(
                                                    serverBaseUrl =
                                                        serverBaseUrl,
                                                    category =
                                                        category,
                                                    query =
                                                        searchText,
                                                    minAltitude =
                                                        minAltitude,
                                                    direction =
                                                        direction,
                                                    constellation =
                                                        constellation,
                                                    offset =
                                                        (
                                                            result.offset -
                                                                pageSize
                                                            )
                                                            .coerceAtLeast(
                                                                0
                                                            )
                                                )
                                            },
                                            enabled =
                                                result.offset >
                                                    0,
                                            modifier =
                                                Modifier
                                                    .weight(
                                                        1f
                                                    )
                                        ) {
                                            Text(
                                                "\u2190 Precedents"
                                            )
                                        }

                                        Text(
                                            text =
                                                "$currentPage / $pageCount",
                                            color =
                                                StellarMuted,
                                            modifier =
                                                Modifier
                                                    .padding(
                                                        top =
                                                            12.dp
                                                    )
                                        )

                                        Button(
                                            onClick = {

                                                viewModel.load(
                                                    serverBaseUrl =
                                                        serverBaseUrl,
                                                    category =
                                                        category,
                                                    query =
                                                        searchText,
                                                    minAltitude =
                                                        minAltitude,
                                                    direction =
                                                        direction,
                                                    constellation =
                                                        constellation,
                                                    offset =
                                                        result.offset +
                                                            pageSize
                                                )
                                            },
                                            enabled =
                                                (
                                                    result.offset +
                                                        result.returnedCount
                                                    ) <
                                                    result.visibleCount,
                                            modifier =
                                                Modifier
                                                    .weight(
                                                        1f
                                                    )
                                        ) {
                                            Text(
                                                "Suivants \u2192"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun ConstellationMultiSelect(
    selectedValue: String,
    onSelectedValueChange: (String) -> Unit
) {
    var expanded by rememberSaveable {
        mutableStateOf(false)
    }

    val selectedNames =
        selectedValue
            .split("|")
            .map { value ->
                value.trim()
            }
            .filter { value ->
                value.isNotEmpty()
            }

    val summary =
        when (selectedNames.size) {
            0 ->
                "Toutes les constellations"

            1 ->
                selectedNames.first()

            else ->
                "${selectedNames.size} constellations sélectionnées"
        }

    Box(
        modifier =
            Modifier.fillMaxWidth()
    ) {
        OutlinedButton(
            onClick = {
                expanded = true
            },
            modifier =
                Modifier.widthIn(
                    min = 440.dp,
                    max = 560.dp
                )
        ) {
            Text(
                text = summary,
                modifier =
                    Modifier.weight(1f)
            )

            Text(
                text =
                    if (expanded) {
                        "\u25B2"
                    } else {
                        "\u25BC"
                    }
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            },
            modifier =
                Modifier.widthIn(
                    min = 440.dp,
                    max = 560.dp
                )
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        "Toutes les constellations"
                    )
                },
                leadingIcon = {
                    Checkbox(
                        checked =
                            selectedNames.isEmpty(),
                        onCheckedChange =
                            null
                    )
                },
                onClick = {
                    onSelectedValueChange("")
                }
            )

            DropdownMenuItem(
                text = {
                    Text("\u2190 Retour")
                },
                onClick = {
                    expanded = false
                }
            )

            targetConstellationsFr
                .chunked(3)
                .forEach { pair ->

                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        pair.forEach { name ->

                            val checked =
                                name in selectedNames

                            Row(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .padding(
                                            horizontal = 6.dp,
                                            vertical = 4.dp
                                        ),
                                verticalAlignment =
                                    androidx.compose.ui.Alignment.CenterVertically
                            ) {

                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        val next =
                                            selectedNames
                                                .toMutableList()

                                        if (checked) {
                                            next.remove(name)
                                        } else {
                                            next.add(name)
                                        }

                                        onSelectedValueChange(
                                            next.joinToString("|")
                                        )
                                    }
                                )

                                Text(
                                    text = name,
                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodySmall
                                )
                            }
                        }

                        repeat(3 - pair.size) {
                            Spacer(
                                modifier =
                                    Modifier.weight(1f)
                            )
                        }
                    }
                }

            DropdownMenuItem(
                text = {
                    Text("\u2190 Retour")
                },
                onClick = {
                    expanded = false
                }
            )
        }
    }
}
@Composable
private fun TargetObjectRow(
    target: SkyObject,
    selected: Boolean,
    onSelect: () -> Unit
) {

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    StellarBackground
            ),
        border =
            BorderStroke(
                1.dp,
                if (selected)
                    StellarOrange
                else
                    StellarBorder
            )
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
        ) {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        12.dp
                    )
            ) {

                Icon(
                    painter =
                        painterResource(
                            id =
                                targetObjectIconResource(
                                    target.objectType
                                )
                        ),
                    contentDescription =
                        target.objectTypeLabelFr,
                    tint =
                        StellarOrange,
                    modifier =
                        Modifier.size(
                            36.dp
                        )
                )

                Text(
                    text =
                        target.name,
                    color =
                        StellarText,
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,
                    fontWeight =
                        FontWeight.Bold,
                    modifier =
                        Modifier.weight(
                            1f
                        )
                )
            }

            target.reference
                ?.takeIf {
                    it != target.name
                }
                ?.let {

                    Spacer(
                        Modifier.height(2.dp)
                    )

                    Text(
                        text = it,
                        color =
                            StellarOrange,
                        fontWeight =
                            FontWeight.Bold
                    )
                }

            Spacer(
                Modifier.height(5.dp)
            )

            Text(
                text =
                    buildString {

                        append(
                            target
                                .objectTypeLabelFr
                        )

                        target
                            .constellation
                            ?.let {

                                append(
                                    " \u2022 "
                                )

                                append(it)
                            }
                    },
                color =
                    StellarMuted
            )

            Spacer(
                Modifier.height(5.dp)
            )

            val magnitude =
                target.magnitude
                    ?.let {
                        formatTargetNumber(
                            it,
                            2
                        )
                    }
                    ?: "\u2014"

            Text(
                text =
                    "Mag $magnitude \u2022 Alt. ${
                        formatTargetNumber(
                            target.altitudeDeg,
                            1
                        )
                    }\u00B0 \u2022 Az. ${
                        formatTargetNumber(
                            target.azimuthDeg,
                            1
                        )
                    }\u00B0 ${
                        target.azimuthDirection
                    }",
                color =
                    StellarMuted
            )

            if (
                target.majorAxisArcmin != null
            ) {

                Spacer(
                    Modifier.height(4.dp)
                )

                Text(
                    text =
                        buildString {

                            append(
                                "Taille "
                            )

                            append(
                                formatTargetNumber(
                                    target
                                        .majorAxisArcmin,
                                    1
                                )
                            )

                            append("'")

                            target
                                .minorAxisArcmin
                                ?.let {

                                    append(
                                        " x "
                                    )

                                    append(
                                        formatTargetNumber(
                                            it,
                                            1
                                        )
                                    )

                                    append("'")
                                }
                        },
                    color =
                        StellarMuted
                )
            }

            Spacer(
                Modifier.height(10.dp)
            )

            if (selected) {

                Button(
                    onClick =
                        onSelect,
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    colors =
                        ButtonDefaults
                            .buttonColors(
                                containerColor =
                                    StellarOrange,
                                contentColor =
                                    StellarBackground
                            )
                ) {

                    Text(
                        text =
                            "Cible selectionnee",
                        fontWeight =
                            FontWeight.Bold
                    )
                }

            } else {

                OutlinedButton(
                    onClick =
                        onSelect,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                ) {

                    Text(
                        text =
                            "Choisir cette cible"
                    )
                }
            }
        }
    }
}

private fun formatTargetNumber(
    value: Double,
    decimals: Int
): String =
    String.format(
        Locale.FRANCE,
        "%.${decimals}f",
        value
    )

private fun formatSigned(
    value: Double
): String =
    String.format(
        Locale.FRANCE,
        "%+.4f",
        value
    )