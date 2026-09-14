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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


data class GalleriesUiState(
    val isLoading: Boolean = false,
    val isExporting: Boolean = false,
    val sessions: List<GallerySession> = emptyList(),
    val selectedSessionId: String? = null,
    val previewBytes: ByteArray? = null,
    val exportMessage: String? = null,
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
                        createdAt = "2026-09-06T20:00:00+00:00",
                        targetName = "M103 — Démonstration",
                        exposureSeconds = 4.0,
                        capturedFrames = 26,
                        acceptedFrames = 24,
                        rejectedFrames = 2,
                        integrationSeconds = 96.0,
                        latitude = 47.4500,
                        longitude = 0.6167,
                        altitudeM = null,
                        locationSource = "demo",
                        placeName = null
                    )
                ),
                error = null
            )
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(
                isLoading = true,
                error = null,
                exportMessage = null
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
                exportMessage = null,
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
                exportMessage = null,
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

    fun exportSelected(sessionId: String) {
        if (uiState.isExporting) return

        val session = uiState.sessions.firstOrNull { it.id == sessionId }
        val preview = uiState.previewBytes
        if (session == null || preview == null || uiState.selectedSessionId != sessionId) {
            uiState = uiState.copy(
                error = "Ouvrez d'abord le stack avant de l'enregistrer."
            )
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(
                isExporting = true,
                exportMessage = null,
                error = null
            )

            try {
                val filename = withContext(Dispatchers.IO) {
                    GalleryExportRenderer.saveToDeviceGallery(
                        context = getApplication<Application>(),
                        previewBytes = preview,
                        session = session
                    )
                }
                uiState = uiState.copy(
                    isExporting = false,
                    exportMessage = "Image enregistrée dans Pictures/StellarPilot • $filename"
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isExporting = false,
                    error = error.message ?: "Enregistrement impossible"
                )
            }
        }
    }
}
