package fr.stellarpilot.app.data.remote

import fr.stellarpilot.app.domain.model.DeviceStatus
import fr.stellarpilot.app.domain.model.GpsStatus
import fr.stellarpilot.app.domain.model.ServerSession
import fr.stellarpilot.app.domain.model.ServerStatus
import fr.stellarpilot.app.domain.model.SystemDevices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class StellarPilotApiClient(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun getStatus(): ServerStatus = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint("status"))
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "HTTP ${response.code} sur /status"
            }

            val body = response.body?.string()
                ?: error("Réponse /status vide")

            parseStatus(body)
        }
    }

    fun openEvents(listener: WebSocketListener): WebSocket {
        val request = Request.Builder()
            .url(webSocketEndpoint())
            .build()

        return client.newWebSocket(request, listener)
    }

    private fun endpoint(path: String): String =
        baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    private fun webSocketEndpoint(): String {
        val httpUrl = endpoint("ws")

        return when {
            httpUrl.startsWith("https://") ->
                "wss://" + httpUrl.removePrefix("https://")

            httpUrl.startsWith("http://") ->
                "ws://" + httpUrl.removePrefix("http://")

            else ->
                error("Schéma serveur non supporté: $baseUrl")
        }
    }

    private fun parseStatus(json: String): ServerStatus {
        val root = JSONObject(json)
        val session = root.optJSONObject("session") ?: JSONObject()
        val devices = root.optJSONObject("devices") ?: JSONObject()

        val server = devices.optJSONObject("server") ?: JSONObject()
        val mount = devices.optJSONObject("mount") ?: JSONObject()
        val camera = devices.optJSONObject("camera") ?: JSONObject()
        val gps = devices.optJSONObject("gps") ?: JSONObject()

        return ServerStatus(
            service = root.optString("service", "unknown"),
            status = root.optString("status", "unknown"),
            poc = root.optBoolean("poc", false),
            mode = root.optString("mode", "unknown"),
            devices = SystemDevices(
                server = DeviceStatus(
                    status = server.optString("status", "unknown")
                ),
                mount = DeviceStatus(
                    status = mount.optString("status", "unknown"),
                    name = mount.optNullableString("name")
                ),
                camera = DeviceStatus(
                    status = camera.optString("status", "unknown"),
                    name = camera.optNullableString("name")
                ),
                gps = GpsStatus(
                    status = gps.optString("status", "unknown"),
                    latitude = gps.optNullableDouble("latitude"),
                    longitude = gps.optNullableDouble("longitude")
                )
            ),
            session = ServerSession(
                latitude = session.optNullableDouble("latitude"),
                longitude = session.optNullableDouble("longitude"),
                altitude = session.optNullableDouble("altitude"),
                timestamp = session.optNullableString("timestamp"),
                mountType = session.optNullableString("mount_type")
            )
        )
    }
}

private fun JSONObject.optNullableDouble(name: String): Double? =
    if (!has(name) || isNull(name)) null else optDouble(name)

private fun JSONObject.optNullableString(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name)
