package com.ichichuang.cinema.data.catalog

import com.ichichuang.cinema.domain.model.catalog.*

internal data class SourceItemKey(val sourceId: SourceId, val itemId: SourceItemId)
internal data class VerifiedCatalogAssociation(val left: SourceItemKey, val right: SourceItemKey, val identifier: CatalogIdentifier)
internal data class VerifiedSourceAlias(val sourceId: SourceId, val previousEndpoint: String, val nextEndpoint: String)

/** Explicit local verification input. Never constructed from source DTOs, redirects or raw external-ID claims. */
internal class CatalogIdentityProofs(associations: Map<String, VerifiedCatalogAssociation> = emptyMap(), aliases: Map<String, VerifiedSourceAlias> = emptyMap()) {
    private val associations = associations.toMap()
    private val aliases = aliases.toMap()
    init { require((associations.keys + aliases.keys).all { it.isNotBlank() }) }
    fun association(proofId: String, left: SourceItemKey, right: SourceItemKey): CatalogIdentifier? = associations[proofId]
        ?.takeIf { (it.left == left && it.right == right) || (it.left == right && it.right == left) }?.identifier
    fun alias(proofId: String, sourceId: SourceId, previous: String, next: String) =
        aliases[proofId] == VerifiedSourceAlias(sourceId, previous, next)
}
