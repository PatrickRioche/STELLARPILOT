package fr.stellarpilot.app.feature.galleries

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.stellarpilot.app.R
import fr.stellarpilot.app.data.remote.CaptureSessionApiClient
import fr.stellarpilot.app.data.remote.GallerySession
import fr.stellarpilot.app.feature.demo.DemoModeState
import kotlinx.coroutines.launch


data class GalleriesUiState(
    val isLoading: Boolean = false,
    val sessions: List<GallerySession> = emptyList(),
    val selectedSessionId: String? = null,
    val previewBytes: ByteArray? = null,
    val error: String? = null
)


class GalleriesViewModel(
    application: Application
) : AndroidViewModel(application) {

    var uiState by mutableStateOf(GalleriesUiState())
        private set

    fun load(serverBaseUrl: String) {
        if (uiState.isLoading) return

        if (DemoModeState.active) {
            uiState = GalleriesUiState(
                isLoading = false,
                sessions = listOf(
                    GallerySession(
                        id = "demo-m103",
                        createdAt = "Mode démonstration local",
                        targetName = "M103 — Démonstration",
                        exposureSeconds = 4.0,
                        acceptedFrames = 24,
                        rejectedFrames = 2,
                        integrationSeconds = 96.0
                    )
                ),
                error = null
            )
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(
                isLoading = true,
                error = null
            )
            try {
                val sessions =
                    CaptureSessionApiClient(serverBaseUrl)
                        .listGalleries()
                uiState = uiState.copy(
                    isLoading = false,
                    sessions = sessions
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    error = error.message
                )
            }
        }
    }

    fun open(
        serverBaseUrl: String,
        sessionId: String
    ) {
        if (DemoModeState.active) {
            val bytes =
                runCatching {
                    getApplication<Application>()
                        .resources
                        .openRawResource(R.drawable.m103_preview)
                        .use { it.readBytes() }
                }.getOrNull()

            uiState = uiState.copy(
                selectedSessionId = sessionId,
                previewBytes = bytes,
                error =
                    if (bytes == null) {
                        "Aperçu de démonstration indisponible"
                    } else {
                        null
                    }
            )
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(
                selectedSessionId = sessionId,
                previewBytes = null,
                error = null
            )
            try {
                val preview =
                    CaptureSessionApiClient(serverBaseUrl)
                        .getGalleryPreview(sessionId)
                uiState = uiState.copy(
                    previewBytes = preview
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    error = error.message
                )
            }
        }
    }
}
