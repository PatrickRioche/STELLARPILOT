package fr.stellarpilot.app.domain.model

data class ServerSession(
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val timestamp: String?,
    val mountType: String?
)

data class DeviceStatus(
    val status: String,
    val name: String? = null
)

data class GpsStatus(
    val status: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class SystemDevices(
    val server: DeviceStatus,
    val mount: DeviceStatus,
    val camera: DeviceStatus,
    val gps: GpsStatus
)

data class ServerStatus(
    val service: String,
    val status: String,
    val poc: Boolean,
    val mode: String,
    val devices: SystemDevices,
    val session: ServerSession
)
