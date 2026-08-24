package fr.stellarpilot.app.domain.model

data class CatalogTypeDetail(
    val type: String,
    val labelFr: String,
    val count: Int
)

data class CatalogStatus(
    val status: String,
    val databaseName: String?,
    val source: String?,
    val sourceVersion: String?,
    val language: String?,
    val offline: Boolean,
    val objectCount: Int,
    val constellationCount: Int,
    val frenchNameCount: Int,
    val frenchAliasCount: Int,
    val typeDetails: List<CatalogTypeDetail>
)