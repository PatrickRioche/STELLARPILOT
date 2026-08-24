package fr.stellarpilot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import fr.stellarpilot.app.ui.theme.StellarBackground
import fr.stellarpilot.app.ui.theme.StellarMuted
import fr.stellarpilot.app.ui.theme.StellarOrange
import fr.stellarpilot.app.ui.theme.StellarPilotTheme
import fr.stellarpilot.app.ui.theme.StellarText

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            StellarPilotTheme {

                var showSplash by remember {
                    mutableStateOf(true)
                }

                LaunchedEffect(Unit) {
                    delay(5000)
                    showSplash = false
                }

                if (showSplash) {
                    StellarPilotSplash()
                } else {
                    StellarPilotApp()
                }
            }
        }
    }
}

@Composable
private fun StellarPilotSplash() {

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    StellarBackground
                ),
        contentAlignment =
            Alignment.Center
    ) {

        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.Center
        ) {

            Image(
                painter =
                    painterResource(
                        id =
                            R.mipmap.ic_launcher
                    ),
                contentDescription =
                    "Logo StellarPilot",
                modifier =
                    Modifier.size(
                        190.dp
                    )
            )

            Spacer(
                Modifier.height(
                    24.dp
                )
            )

            Text(
                text =
                    "STELLARPILOT",
                color =
                    StellarText,
                style =
                    MaterialTheme
                        .typography
                        .headlineLarge,
                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                Modifier.height(
                    8.dp
                )
            )

            Text(
                text =
                    "Assistant d'observation astronomique",
                color =
                    StellarMuted,
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge
            )

            Spacer(
                Modifier.height(
                    18.dp
                )
            )

            Text(
                text =
                    "\u2605",
                color =
                    StellarOrange,
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium
            )
        }
    }
}