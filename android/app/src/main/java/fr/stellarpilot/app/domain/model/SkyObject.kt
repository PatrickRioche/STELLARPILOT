package fr.stellarpilot.app.domain.model

data class SkyObject(
    val id: Int,
    val name: String,
    val catalogName: String,
    val reference: String?,
    val objectType: String,
    val objectTypeLabelFr: String,
    val constellation: String?,
    val raHours: Double,
    val decDeg: Double,
    val magnitude: Double?,
    val magnitudeBand: String?,
    val majorAxisArcmin: Double?,
    val minorAxisArcmin: Double?,
    val aliasesFr: String?,
    val altitudeDeg: Double,
    val azimuthDeg: Double,
    val azimuthDirection: String
)

data class SkyObjectsResult(
    val status: String,
    val category: String,
    val categoryLabelFr: String,
    val query: String?,
    val minAltitudeDeg: Double,
    val visibleCount: Int,
    val returnedCount: Int,
    val sort: String,
    val order: String,
    val offset: Int,
    val limit: Int,
    val objects: List<SkyObject>
)