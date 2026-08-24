package fr.stellarpilot.app.feature.preparation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.stellarpilot.app.data.remote.CameraPreviewApiClient
import kotlinx.coroutines.launch

data class CameraPreviewUiState(
    val isLoading: Boolean = false,
    val imageBytes: ByteArray? = null,
    val error: String? = null
)

class CameraPreviewViewModel : ViewModel() {

    var uiState by mutableStateOf(
        CameraPreviewUiState()
    )
        private set

    fun load(
        serverBaseUrl: String
    ) {
        if (uiState.isLoading) return

        uiState = uiState.copy(
            isLoading = true,
            error = null
        )

        viewModelScope.launch {
            try {
                val api =
                    CameraPreviewApiClient()

                val image =
                    api.getPreview(
                        serverBaseUrl
                    )

                uiState = uiState.copy(
                    isLoading = false,
                    imageBytes = image,
                    error = null
                )

            } catch (error: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    error =
                        "${error::class.java.simpleName}: ${error.message}"
                )
            }
        }
    }
}