package fr.stellarpilot.app.feature.status

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit


data class OpticalSetupTelemetry(
    val status: String,
    val source: String,
    val databaseAvailable: Boolean,
    val kstarsRunning: Boolean,
    val opticalTrainName: String?,
    val mount: String?,
    val camera: String?,
    val scopeConfig: String?,
    val telescopeName: String?,
    val telescopeType: String?,
    val apertureMm: Double?,
    val focalLengthMm: Double?,
    val focalRatio: Double?,
    val reducer: Double?,
    val effectiveFocalLengthMm: Double?,
    val effectiveFocalRatio: Double?,
    val consistency: String,
    val detail: String?
)


data class SetupDiagnosticsUiState(
    val setup: OpticalSetupTelemetry? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)


class SetupDiagnosticsViewModel : ViewModel() {
    var uiState by mutableStateOf(SetupDiagnosticsUiState())
        private set

    fun refresh(serverBaseUrl: String) {
        if (uiState.isLoading) return

        uiState = uiState.copy(isLoading = true, error = null)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .callTimeout(20, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build()

                val request = Request.Builder()
                    .url(serverBaseUrl.trimEnd('/') + "/setup/status")
                    .header("Connection", "close")
                    .get()
                    .build()

                val root = client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) {
                        "HTTP ${response.code} sur /setup/status"
                    }
                    JSONObject(
                        response.body?.string()
                            ?: error("Réponse /setup/status vide")
                    )
                }

                uiState = SetupDiagnosticsUiState(
                    setup = OpticalSetupTelemetry(
                        status = root.optString("status", "unavailable"),
                        source = root.optString("source", "kstars"),
                        databaseAvailable = root.optBoolean("database_available", false),
                        kstarsRunning = root.optBoolean("kstars_running", false),
                        opticalTrainName = root.optNullableString("optical_train_name"),
                        mount = root.optNullableString("mount"),
                        camera = root.optNullableString("camera"),
                        scopeConfig = root.optNullableString("scope_config"),
                        telescopeName = root.optNullableString("telescope_name"),
                        telescopeType = root.optNullableString("telescope_type"),
                        apertureMm = root.optNullableDouble("aperture_mm"),
                        focalLengthMm = root.optNullableDouble("focal_length_mm"),
                        focalRatio = root.optNullableDouble("focal_ratio"),
                        reducer = root.optNullableDouble("reducer"),
                        effectiveFocalLengthMm = root.optNullableDouble("effective_focal_length_mm"),
                        effectiveFocalRatio = root.optNullableDouble("effective_focal_ratio"),
                        consistency = root.optString("consistency", "unverified"),
                        detail = root.optNullableString("detail")
                    ),
                    isLoading = false,
                    error = null
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    error = error.message ?: "Setup optique indisponible"
                )
            }
        }
    }
}


private fun JSONObject.optNullableString(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

private fun JSONObject.optNullableDouble(name: String): Double? =
    if (!has(name) || isNull(name)) null else optDouble(name, Double.NaN).takeUnless { it.isNaN() }
