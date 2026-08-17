package fr.stellarpilot.app.domain.model

data class ServerSession(
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val timestamp: String?,
    val mountType: String?
)

data class ServerStatus(
    val service: String,
    val status: String,
    val poc: Boolean,
    val session: ServerSession
)
