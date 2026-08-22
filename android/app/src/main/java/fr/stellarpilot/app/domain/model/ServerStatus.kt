package fr.stellarpilot.app.domain.model

data class ServerSession(
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val timestamp: String?,
    val mountType: String?,
    val mountTypeSource: String?,
    val mountFamily: String?,
    val mountFamilyLabel: String?,
    val startupTarget: String?
)

data class DeviceStatus(
    val status: String,
    val name: String? = null
)

data class MountStatus(
    val status: String,
    val name: String? = null,
    val type: String? = null,
    val typeLabel: String? = null,
    val family: String? = null,
    val familyLabel: String? = null,
    val startupTarget: String? = null
)

data class CameraSensor(
    val width: Int? = null,
    val height: Int? = null,
    val pixelSizeUm: Double? = null,
    val pixelSizeXUm: Double? = null,
    val pixelSizeYUm: Double? = null,
    val bitsPerPixel: Int? = null
)

data class CameraCapture(
    val exposureS: Double? = null,
    val gain: Double? = null,
    val offset: Double? = null,
    val binX: Int? = null,
    val binY: Int? = null,
    val frameWidth: Int? = null,
    val frameHeight: Int? = null,
    val frameType: String? = null
)

data class CameraStatus(
    val status: String,
    val name: String? = null,
    val sensor: CameraSensor = CameraSensor(),
    val capture: CameraCapture = CameraCapture(),
    val temperatureC: Double? = null
)

data class GpsStatus(
    val status: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class SystemDevices(
    val server: DeviceStatus,
    val mount: MountStatus,
    val camera: CameraStatus,
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