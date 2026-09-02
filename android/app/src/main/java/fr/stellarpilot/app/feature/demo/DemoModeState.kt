package fr.stellarpilot.app.feature.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Etat global du mode démonstration.
 *
 * Le mode démo est strictement local : les écrans utilisent des données
 * embarquées et les composants réseau enregistrés sont avertis afin de
 * fermer leurs connexions au Raspberry Pi pendant toute la démonstration.
 */
object DemoModeState {

    private var activeState by mutableStateOf(false)

    private val listeners =
        CopyOnWriteArraySet<(Boolean) -> Unit>()

    var active: Boolean
        get() = activeState
        set(value) {
            if (activeState == value) return
            activeState = value
            listeners.forEach { listener ->
                listener(value)
            }
        }

    fun addListener(
        listener: (Boolean) -> Unit
    ) {
        listeners.add(listener)
    }

    fun removeListener(
        listener: (Boolean) -> Unit
    ) {
        listeners.remove(listener)
    }
}
