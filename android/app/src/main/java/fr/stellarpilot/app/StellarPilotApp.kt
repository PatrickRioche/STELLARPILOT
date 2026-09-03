package fr.stellarpilot.app

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.stellarpilot.app.feature.capture.CaptureScreen
import fr.stellarpilot.app.feature.connection.ConnectionViewModel
import fr.stellarpilot.app.feature.galleries.GalleriesScreen
import fr.stellarpilot.app.feature.preparation.PreparationV060Screen
import fr.stellarpilot.app.feature.sky.SkyScreen
import fr.stellarpilot.app.feature.status.StatusScreen
import fr.stellarpilot.app.ui.theme.StellarBackground
import fr.stellarpilot.app.ui.theme.StellarMuted
import fr.stellarpilot.app.ui.theme.StellarOrange
import fr.stellarpilot.app.ui.theme.StellarSurface
import fr.stellarpilot.app.ui.theme.StellarSurfaceRaised

private data class StellarTab(
    val label: String,
    @DrawableRes val icon: Int,
    val title: String,
    val subtitle: String
)

private val tabs = listOf(
    StellarTab(
        "Pr\u00E9paration",
        R.drawable.ic_nav_preparation,
        "Pr\u00E9paration",
        "Assistant de pr\u00E9paration de l'observation"
    ),
    StellarTab(
        "Statut",
        R.drawable.ic_nav_status,
        "Statut syst\u00E8me",
        "\u00C9tat technique de StellarPilot"
    ),
    StellarTab(
        "Ciel",
        R.drawable.ic_nav_sky,
        "Ciel & Cible",
        "Choix de la cible astronomique"
    ),
    StellarTab(
        "Capture",
        R.drawable.ic_nav_capture,
        "Capture",
        "Acquisition des images"
    ),
    StellarTab(
        "Galeries",
        R.drawable.ic_nav_results,
        "Galeries",
        "Images et captures de vos sessions"
    )
)

@Composable
fun StellarPilotApp() {

    val connectionViewModel:
        ConnectionViewModel = viewModel()

    var selectedTab by rememberSaveable {
        mutableIntStateOf(0)
    }

    val tabStateHolder =
        rememberSaveableStateHolder()

    Scaffold(
        containerColor = StellarBackground,
        bottomBar = {
            NavigationBar(
                containerColor = StellarSurface
            ) {
                tabs.forEachIndexed { index, tab ->

                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                        },
                        icon = {
                            Icon(
                                painter = painterResource(tab.icon),
                                contentDescription = tab.label
                            )
                        },
                        label = {
                            Text(tab.label)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = StellarOrange,
                            selectedTextColor = StellarOrange,
                            indicatorColor = StellarSurfaceRaised,
                            unselectedIconColor = StellarMuted,
                            unselectedTextColor = StellarMuted
                        )
                    )
                }
            }
        }
    ) { innerPadding ->

        Box(
            modifier = Modifier.padding(innerPadding)
        ) {

            tabStateHolder.SaveableStateProvider(
                key = selectedTab
            ) {

                when (selectedTab) {

                    0 -> PreparationV060Screen(
                        onOpenSky = {
                            selectedTab = 2
                        },
                        connectionViewModel =
                            connectionViewModel
                    )

                    1 -> StatusScreen(
                        viewModel =
                            connectionViewModel
                    )

                    2 -> SkyScreen(
                        serverBaseUrl =
                            connectionViewModel
                                .uiState
                                .serverBaseUrl
                    )

                    3 -> CaptureScreen(
                        serverBaseUrl =
                            connectionViewModel
                                .uiState
                                .serverBaseUrl
                    )

                    4 -> GalleriesScreen(
                        serverBaseUrl =
                            connectionViewModel
                                .uiState
                                .serverBaseUrl
                    )
                }
            }
        }
    }
}
