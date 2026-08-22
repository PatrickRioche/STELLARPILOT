package fr.stellarpilot.app.domain.model

data class SkyObserver(
    val latitude: Double?,
    val longitude: Double?,
    val timestampUtc: String?,
    val locationSource: String?
)

data class SkyStar(
    val id: String,
    val name: String,
    val constellation: String,
    val objectType: String,
    val magnitude: Double,
    val raHours: Double,
    val decDeg: Double,
    val altitudeDeg: Double,
    val azimuthDeg: Double,
    val azimuthDirection: String,
    val aboveHorizon: Boolean,
    val alignmentCandidate: Boolean,
    val alignmentScore: Double?
)

data class SkyStatus(
    val status: String,
    val observer: SkyObserver?,
    val catalogCount: Int,
    val aboveHorizonCount: Int,
    val alignmentCandidateCount: Int,
    val recommended: SkyStar?,
    val stars: List<SkyStar>
)