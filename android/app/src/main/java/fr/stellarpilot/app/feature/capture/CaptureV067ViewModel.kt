package fr.stellarpilot.app.feature.capture

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.stellarpilot.app.data.remote.BahtinovApiClient
import fr.stellarpilot.app.data.remote.CaptureSessionApiClient
import fr.stellarpilot.app.data.remote.CaptureSessionStatus
import fr.stellarpilot.app.data.remote.CameraPreviewApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val V067_EXPOSURE_SECONDS = 4.0


data class CaptureV067UiState(
    val target: CaptureTarget? = null,
    val session: CaptureSessionStatus? = null,
    val imageBytes: ByteArray? = null,
    val isCapturing: Boolean = false,
    val isSolving: Boolean = false,
    val statusMessage: String? = null,
    val error: String? = null,
    val bahtinovMaskInstalled: Boolean = false,
    val bahtinovImageBytes: ByteArray? = null,
    val bahtinovIsLoading: Boolean = false,
    val bahtinovFocusScore: Int? = null,
    val bahtinovFocusLabel: String? = null,
    val bahtinovFocusErrorPx: Double? = null,
    val bahtinovFocusInstruction: String? = null,
    val bahtinovOptimumStreak: Int = 0,
    val bahtinovValidated: Boolean = false
)


class CaptureV067ViewModel(
    application: Application
) : AndroidViewModel(application) {

    var uiState by mutableStateOf(CaptureV067UiState())
        private set

    private var solveJob: Job? = null

    init {
        loadSelectedTarget()
    }

    fun loadSelectedTarget() {
        val context = getApplication<Application>()
        val preferences = context.getSharedPreferences("stellarpilot_target", 0)

        val name = preferences.getString("name", null)
        val ra = preferences.getString("ra_hours", null)?.toDoubleOrNull()
        val dec = preferences.getString("dec_deg", null)?.toDoubleOrNull()

        val target = if (name != null && ra != null && dec != null) {
            CaptureTarget(
                name = name,
                reference = preferences.getString("reference", null),
                objectType = preferences.getString("object_type", "unknown") ?: "unknown",
                raHours = ra,
                decDeg = dec
            )
        } else {
            null
        }

        val targetChanged =
            uiState.target?.name != target?.name ||
                uiState.target?.raHours != target?.raHours ||
                uiState.target?.decDeg != target?.decDeg

        uiState = if (targetChanged) {
            CaptureV067UiState(target = target)
        } else {
            uiState.copy(target = target, error = null)
        }
    }

    private suspend fun ensureSession(serverBaseUrl: String): CaptureSessionStatus {
        uiState.session?.let { return it }

        val target = uiState.target
            ?: throw IllegalArgumentException("Choisissez d'abord une cible dans Ciel")

        val session = CaptureSessionApiClient(serverBaseUrl).createSession(
            targetName = target.name,
            targetRaHours = target.raHours,
            targetDecDeg = target.decDeg,
            objectType = target.objectType,
            trackingMode = target.trackingMode,
            exposureSeconds = V067_EXPOSURE_SECONDS
        )

        uiState = uiState.copy(session = session)
        return session
    }

    fun captureFourSeconds(serverBaseUrl: String) {
        if (uiState.isCapturing || uiState.isSolving || uiState.bahtinovIsLoading) return

        viewModelScope.launch {
            uiState = uiState.copy(
                isCapturing = true,
                error = null,
                statusMessage = "Capture 4 s en cours…"
            )

            try {
                val session = ensureSession(serverBaseUrl)
                val api = CaptureSessionApiClient(serverBaseUrl)
                val captured = api.captureCenterFrame(session.id)
                val preview = runCatching {
                    api.getPreview(session.id, stack = false)
                }.getOrNull()

                uiState = uiState.copy(
                    isCapturing = false,
                    session = captured,
                    imageBytes = preview ?: uiState.imageBytes,
                    statusMessage = "Image acquise ✓ • astrométrie non lancée",
                    error = null
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isCapturing = false,
                    statusMessage = null,
                    error = error.message ?: "Capture impossible"
                )
            }
        }
    }

    fun launchAstrometry(serverBaseUrl: String) {
        if (uiState.isCapturing || uiState.isSolving || uiState.bahtinovIsLoading) return

        val session = uiState.session ?: return
        val centeringStatus = session.centering.status
        if (centeringStatus !in setOf("captured", "unsolved", "cancelled")) return

        solveJob = viewModelScope.launch {
            uiState = uiState.copy(
                isSolving = true,
                statusMessage = "Astrométrie en cours…",
                error = null
            )

            try {
                val solved = CaptureSessionApiClient(serverBaseUrl)
                    .solveCenterFrame(session.id)

                val message = when (solved.centering.status) {
                    "centered" -> "Cible centrée ✓"
                    "correction_required" ->
                        "Correction nécessaire • aucune correction automatique envoyée"
                    "unsolved" -> "Astrométrie non résolue"
                    "cancelled" -> "Astrométrie arrêtée • image conservée"
                    else -> "Astrométrie terminée"
                }

                uiState = uiState.copy(
                    isSolving = false,
                    session = solved,
                    statusMessage = message,
                    error = null
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isSolving = false,
                    statusMessage = null,
                    error = error.message ?: "Astrométrie impossible"
                )
            } finally {
                solveJob = null
            }
        }
    }

    fun stopAstrometry(serverBaseUrl: String) {
        val session = uiState.session ?: return
        if (!uiState.isSolving) return

        solveJob?.cancel()
        solveJob = null

        uiState = uiState.copy(
            isSolving = false,
            statusMessage = "Arrêt de l'astrométrie demandé…",
            error = null
        )

        viewModelScope.launch {
            try {
                val cancelled = AstrometryCancelApiClient(serverBaseUrl)
                    .cancel(session.id)

                uiState = uiState.copy(
                    session = cancelled,
                    isSolving = false,
                    statusMessage = "Astrométrie arrêtée • image conservée",
                    error = null
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    isSolving = false,
                    statusMessage = "Demande d'arrêt envoyée",
                    error = error.message ?: "Impossible de confirmer l'arrêt de l'astrométrie"
                )
            }
        }
    }

    fun installBahtinovMask() {
        uiState = uiState.copy(
            bahtinovMaskInstalled = true,
            bahtinovImageBytes = null,
            bahtinovFocusScore = null,
            bahtinovFocusLabel = null,
            bahtinovFocusErrorPx = null,
            bahtinovFocusInstruction = null,
            bahtinovOptimumStreak = 0,
            bahtinovValidated = false,
            statusMessage = "Masque Bahtinov installé • lancez une pose de 4 s",
            error = null
        )
    }

    fun removeBahtinovMask() {
        uiState = uiState.copy(
            bahtinovMaskInstalled = false,
            statusMessage = "Masque Bahtinov retiré ✓",
            error = null
        )
    }

    fun captureBahtinov(serverBaseUrl: String) {
        if (!uiState.bahtinovMaskInstalled) return
        if (uiState.isCapturing || uiState.isSolving || uiState.bahtinovIsLoading) return

        viewModelScope.launch {
            uiState = uiState.copy(
                bahtinovIsLoading = true,
                statusMessage = "Pose Bahtinov 4 s…",
                error = null
            )

            try {
                val cameraApi = CameraPreviewApiClient()
                val capture = cameraApi.capture(
                    serverBaseUrl = serverBaseUrl,
                    exposureSeconds = V067_EXPOSURE_SECONDS
                )
                val preview = cameraApi.getPreview(serverBaseUrl)
                val focus = BahtinovApiClient().analyze(
                    serverBaseUrl = serverBaseUrl,
                    imagePath = capture.imagePath
                )

                val nextStreak = if (focus.focusReady) {
                    uiState.bahtinovOptimumStreak + 1
                } else {
                    0
                }
                val validated = nextStreak >= 2

                uiState = uiState.copy(
                    bahtinovIsLoading = false,
                    bahtinovImageBytes = preview,
                    bahtinovFocusScore = focus.focusScore,
                    bahtinovFocusLabel = focus.focusLabel,
                    bahtinovFocusErrorPx = focus.errorFromOptimumPx,
                    bahtinovFocusInstruction = focus.instruction,
                    bahtinovOptimumStreak = nextStreak,
                    bahtinovValidated = validated,
                    statusMessage = when {
                        validated -> "Mise au point optimale validée sur 2 poses ✓"
                        focus.focusReady ->
                            "Optimum détecté • refaites une pose de 4 s pour confirmer"
                        else ->
                            focus.instruction ?: "Ajustez la mise au point puis recommencez"
                    },
                    error = null
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    bahtinovIsLoading = false,
                    bahtinovOptimumStreak = 0,
                    bahtinovValidated = false,
                    statusMessage = null,
                    error = error.message ?: "Analyse Bahtinov impossible"
                )
            }
        }
    }

    fun startStacking(serverBaseUrl: String) {
        if (uiState.isCapturing || uiState.isSolving || uiState.bahtinovIsLoading) return
        if (uiState.session?.stacking?.running == true) return

        viewModelScope.launch {
            try {
                val session = ensureSession(serverBaseUrl)
                if (session.stacking.running) return@launch

                val centered = session.centering.status == "centered"
                StackingTestApiClient(serverBaseUrl).start(session.id)
                val started = CaptureSessionApiClient(serverBaseUrl)
                    .getSession(session.id)

                uiState = uiState.copy(
                    session = started,
                    statusMessage = if (centered) {
                        "Stacking démarré"
                    } else {
                        "Stacking démarré • mode test, cible non centrée"
                    },
                    error = null
                )
            } catch (error: Exception) {
                uiState = uiState.copy(error = error.message ?: "Démarrage du stacking impossible")
            }
        }
    }

    fun stopStacking(serverBaseUrl: String) {
        val session = uiState.session ?: return
        if (!session.stacking.running) return

        viewModelScope.launch {
            try {
                val stopped = CaptureSessionApiClient(serverBaseUrl)
                    .stopStack(session.id)
                uiState = uiState.copy(
                    session = stopped,
                    statusMessage = "Stacking arrêté",
                    error = null
                )
            } catch (error: Exception) {
                uiState = uiState.copy(error = error.message ?: "Arrêt du stacking impossible")
            }
        }
    }
}


private class StackingTestApiClient(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    suspend fun start(sessionId: String) =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/capture/sessions/$sessionId/stack/start-test")
                .post(
                    JSONObject().toString()
                        .toRequestBody("application/json; charset=utf-8".toMediaType())
                )
                .build()

            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) {
                    "HTTP ${response.code} pendant le démarrage du stacking"
                }
                val root = JSONObject(
                    response.body?.string() ?: error("Réponse stacking vide")
                )
                val status = root.optString("status")
                if (status in setOf("finalized", "error")) {
                    error(root.optString("detail", "Démarrage du stacking impossible"))
                }
            }
        }
}


private class AstrometryCancelApiClient(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    suspend fun cancel(sessionId: String): CaptureSessionStatus =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/capture/sessions/$sessionId/center/cancel")
                .post(
                    JSONObject().toString()
                        .toRequestBody("application/json; charset=utf-8".toMediaType())
                )
                .build()

            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) {
                    "HTTP ${response.code} pendant l'arrêt de l'astrométrie"
                }
                val root = JSONObject(
                    response.body?.string() ?: error("Réponse d'arrêt vide")
                )
                val session = root.optJSONObject("session")
                    ?: error("Réponse d'arrêt sans session")
                return@withContext parseCancelledSession(session)
            }
        }

    private fun parseCancelledSession(root: JSONObject): CaptureSessionStatus {
        val target = root.optJSONObject("target") ?: JSONObject()
        val setup = root.optJSONObject("setup") ?: JSONObject()
        val counts = root.optJSONObject("counts") ?: JSONObject()
        val centering = root.optJSONObject("centering") ?: JSONObject()
        val stacking = root.optJSONObject("stacking") ?: JSONObject()

        return CaptureSessionStatus(
            id = root.optString("id"),
            state = root.optString("state", "framing"),
            targetName = target.optString("name", "Cible"),
            targetRaHours = target.optDouble("ra_hours", 0.0),
            targetDecDeg = target.optDouble("dec_deg", 0.0),
            objectType = target.optString("object_type", "unknown"),
            trackingMode = target.optString("tracking_mode", "sidereal"),
            exposureSeconds = setup.optDouble("exposure_s", V067_EXPOSURE_SECONDS),
            capturedFrames = counts.optInt("captured", 0),
            acceptedFrames = counts.optInt("accepted", 0),
            rejectedFrames = counts.optInt("rejected", 0),
            integrationSeconds = root.optDouble("integration_seconds", 0.0),
            centering = fr.stellarpilot.app.data.remote.CaptureCenteringStatus(
                status = centering.optString("status", "cancelled"),
                errorArcsec = nullableDouble(centering, "error_arcsec"),
                solveRaDeg = nullableDouble(centering, "solve_ra_deg"),
                solveDecDeg = nullableDouble(centering, "solve_dec_deg"),
                correctionRaHours = nullableDouble(centering, "correction_ra_hours"),
                correctionDecDeg = nullableDouble(centering, "correction_dec_deg"),
                attempts = centering.optInt("attempts", 0),
                solverStatus = nullableString(centering, "solver_status"),
                solverDetail = nullableString(centering, "solver_detail"),
                verifiedAt = nullableString(centering, "verified_at")
            ),
            centeringQuality = null,
            stacking = fr.stellarpilot.app.data.remote.CaptureStackingStatus(
                running = stacking.optBoolean("running", false),
                recenterRequired = stacking.optBoolean("recenter_required", false),
                recenterReason = nullableString(stacking, "recenter_reason"),
                lastRegistrationDxPx = nullableDouble(stacking, "last_registration_dx_px"),
                lastRegistrationDyPx = nullableDouble(stacking, "last_registration_dy_px"),
                lastRegistrationDistancePx = nullableDouble(stacking, "last_registration_distance_px")
            ),
            hasPreview = root.optBoolean("has_preview", true),
            hasStackPreview = root.optBoolean("has_stack_preview", false),
            galleryPath = nullableString(root, "gallery_path")
        )
    }

    private fun nullableDouble(root: JSONObject, key: String): Double? =
        if (root.has(key) && !root.isNull(key)) root.optDouble(key) else null

    private fun nullableString(root: JSONObject, key: String): String? =
        root.optString(key).takeIf {
            root.has(key) && !root.isNull(key) && it.isNotBlank()
        }
}
