package fr.stellarpilot.app.feature.galleries

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.stellarpilot.app.data.remote.CaptureSessionApiClient
import fr.stellarpilot.app.data.remote.GallerySession
import kotlinx.coroutines.launch


data class GalleriesUiState(
    val isLoading: Boolean = false,
    val sessions: List<GallerySession> = emptyList(),
    val selectedSessionId: String? = null,
    val previewBytes: ByteArray? = null,
    val error: String? = null
)


class GalleriesViewModel : ViewModel() {

    var uiState by mutableStateOf(GalleriesUiState())
        private set

    fun load(serverBaseUrl: String) {
        if (uiState.isLoading) return

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
