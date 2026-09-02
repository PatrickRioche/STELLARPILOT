package fr.stellarpilot.app.feature.status

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit


data class StorageTelemetry(
    val status: String,
    val path: String?,
    val totalBytes: Long?,
    val usedBytes: Long?,
    val availableBytes: Long?,
    val usedPercent: Double?
)


data class CatalogTypeTelemetry(
    val type: String,
    val labelFr: String,
    val count: Int
)


data class CatalogTelemetry(
    val status: String,
    val databaseName: String?,
    val databaseSizeBytes: Long?,
    val source: String?,
    val sourceVersion: String?,
    val language: String?,
    val offline: Boolean,
    val objectCount: Int,
    val constellationCount: Int,
    val constellationCodesInCatalog: Int,
    val frenchNameCount: Int,
    val frenchAliasCount: Int,
    val typeDetails: List<CatalogTypeTelemetry>
)


data class MountPositionTelemetry(
    val status: String,
    val mount: String?,
    val coordinateProperty: String?,
    val raHours: Double?,
    val decDeg: Double?,
    val targetRaHours: Double?,
    val targetDecDeg: Double?,
    val progressPercent: Double?,
    val remainingDeg: Double?,
    val indiState: String?,
    val trackingMode: String?,
    val virtualPosition: Boolean
)


data class StatusDiagnosticsUiState(
    val storage: StorageTelemetry? = null,
    val catalog: CatalogTelemetry? = null,
    val mount: MountPositionTelemetry? = null,
    val staticLoading: Boolean = false,
    val mountLoading: Boolean = false,
    val staticError: String? = null,
    val mountError: String? = null
)


private class StatusDiagnosticsApiClient(
    baseUrl: String
) {
    private val baseUrl = baseUrl.trimEnd('/')

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    private fun getJson(path: String): JSONObject {
        val request =
            Request.Builder()
                .url("$baseUrl/${path.trimStart('/')}")
                .header("Connection", "close")
                .get()
                .build()

        client.newCall(request)
            .execute()
            .use { response ->
                check(response.isSuccessful) {
                    "HTTP ${response.code} sur /$path"
                }

                val body = response.body?.string()
                    ?: error("Réponse /$path vide")

                return JSONObject(body)
            }
    }

    fun storage(): StorageTelemetry {
        val root = getJson("status")
        val system = root.optJSONObject("system") ?: JSONObject()
        val storage = system.optJSONObject("storage") ?: JSONObject()

        return StorageTelemetry(
            status = storage.optString("status", "unavailable"),
            path = storage.optNullableString("path"),
            totalBytes = storage.optNullableLong("total_bytes"),
            usedBytes = storage.optNullableLong("used_bytes"),
            availableBytes = storage.optNullableLong("available_bytes"),
            usedPercent = storage.optNullableDouble("used_percent")
        )
    }

    fun catalog(): CatalogTelemetry {
        val root = getJson("catalog/status")
        val details = root.optJSONArray("type_details")

        val parsedDetails = buildList {
            if (details != null) {
                for (index in 0 until details.length()) {
                    val item = details.optJSONObject(index) ?: continue
                    add(
                        CatalogTypeTelemetry(
                            type = item.optString("type", "unknown"),
                            labelFr = item.optString(
                                "label_fr",
                                "Autres objets"
                            ),
                            count = item.optInt("count", 0)
                        )
                    )
                }
            }
        }

        return CatalogTelemetry(
            status = root.optString("status", "unavailable"),
            databaseName = root.optNullableString("database_name"),
            databaseSizeBytes = root.optNullableLong("database_size_bytes"),
            source = root.optNullableString("source"),
            sourceVersion = root.optNullableString("source_version"),
            language = root.optNullableString("language"),
            offline = root.optBoolean("offline", true),
            objectCount = root.optInt("object_count", 0),
            constellationCount = root.optInt("constellation_count", 0),
            constellationCodesInCatalog =
                root.optInt("constellation_codes_in_catalog", 0),
            frenchNameCount = root.optInt("french_name_count", 0),
            frenchAliasCount = root.optInt("french_alias_count", 0),
            typeDetails = parsedDetails
        )
    }

    fun mount(): MountPositionTelemetry {
        val root = getJson("mount/status")

        return MountPositionTelemetry(
            status = root.optString("status", "unknown"),
            mount = root.optNullableString("mount"),
            coordinateProperty =
                root.optNullableString("coordinate_property"),
            raHours = root.optNullableDouble("ra"),
            decDeg = root.optNullableDouble("dec"),
            targetRaHours = root.optNullableDouble("target_ra"),
            targetDecDeg = root.optNullableDouble("target_dec"),
            progressPercent = root.optNullableDouble("progress_percent"),
            remainingDeg = root.optNullableDouble("remaining_deg"),
            indiState = root.optNullableString("indi_state"),
            trackingMode = root.optNullableString("tracking_mode"),
            virtualPosition = root.optBoolean("virtual_position", true)
        )
    }
}


class StatusDiagnosticsViewModel : ViewModel() {
    var uiState by mutableStateOf(StatusDiagnosticsUiState())
        private set

    private var staticBaseUrl: String? = null

    fun refreshStatic(serverBaseUrl: String) {
        if (uiState.staticLoading) return

        staticBaseUrl = serverBaseUrl
        uiState = uiState.copy(
            staticLoading = true,
            staticError = null
        )

        viewModelScope.launch {
            try {
                val api = StatusDiagnosticsApiClient(serverBaseUrl)
                val storage = api.storage()
                val catalog = api.catalog()

                if (staticBaseUrl != serverBaseUrl) return@launch

                uiState = uiState.copy(
                    storage = storage,
                    catalog = catalog,
                    staticLoading = false,
                    staticError = null
                )
            } catch (error: Exception) {
                if (staticBaseUrl != serverBaseUrl) return@launch

                uiState = uiState.copy(
                    staticLoading = false,
                    staticError = error.message
                        ?: "Diagnostics système indisponibles"
                )
            }
        }
    }

    fun refreshMount(serverBaseUrl: String) {
        if (uiState.mountLoading) return

        uiState = uiState.copy(
            mountLoading = true,
            mountError = null
        )

        viewModelScope.launch {
            try {
                val mount = StatusDiagnosticsApiClient(serverBaseUrl).mount()

                uiState = uiState.copy(
                    mount = mount,
                    mountLoading = false,
                    mountError = null
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    mountLoading = false,
                    mountError = error.message
                        ?: "Position de la monture indisponible"
                )
            }
        }
    }
}


private fun JSONObject.optNullableString(name: String): String? =
    if (!has(name) || isNull(name)) {
        null
    } else {
        optString(name).takeIf { it.isNotBlank() }
    }


private fun JSONObject.optNullableDouble(name: String): Double? =
    if (!has(name) || isNull(name)) {
        null
    } else {
        optDouble(name, Double.NaN).takeUnless { it.isNaN() }
    }


private fun JSONObject.optNullableLong(name: String): Long? =
    if (!has(name) || isNull(name)) {
        null
    } else {
        optLong(name)
    }
