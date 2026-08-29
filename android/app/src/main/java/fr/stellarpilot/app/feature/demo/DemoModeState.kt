package fr.stellarpilot.app.feature.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Etat de session du mode D?monstration.
 *
 * Lorsque active == true, aucun ?cran de d?monstration
 * ne doit d?pendre du Raspberry Pi ou du r?seau.
 */
object DemoModeState {

    var active by mutableStateOf(false)
}
