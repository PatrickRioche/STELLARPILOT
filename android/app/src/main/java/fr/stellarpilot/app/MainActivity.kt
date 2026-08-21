package fr.stellarpilot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import fr.stellarpilot.app.ui.theme.StellarPilotTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            StellarPilotTheme {
                StellarPilotApp()
            }
        }
    }
}