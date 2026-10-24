package com.ichichuang.cinema.data.catalog

internal data class CatalogIdentifier(val namespace: String, val value: String) {
    init { require(namespace.isNotBlank() && value.isNotBlank()) }
}
internal enum class AssociationDecision { LINKED, CANDIDATE, CONFLICT, UNRESOLVED }
internal object WorkIdentity {
    fun decide(verified: CatalogIdentifier?, left: Set<CatalogIdentifier>, right: Set<CatalogIdentifier>, titleYearMatch: Boolean): AssociationDecision {
        if (verified == null) return if (titleYearMatch) AssociationDecision.CANDIDATE else AssociationDecision.UNRESOLVED
        val all = left + right + verified
        if (all.groupBy { it.namespace }.values.any { values -> values.map { it.value }.distinct().size > 1 }) return AssociationDecision.CONFLICT
        return AssociationDecision.LINKED
    }
}
