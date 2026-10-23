package com.ichichuang.cinema.domain.model.catalog

import java.util.Collections

enum class QueryCoverage { PARTIAL, COMPLETE }

/** 累计查询快照；空列表和 hasMore=false 都不单独证明完整覆盖。 */
@ConsistentCopyVisibility
data class CatalogResult private constructor(
    val queryId: String,
    val items: List<CatalogItem>,
    val coverage: QueryCoverage,
    val failedSourceIds: Set<SourceId>,
    val unknownFieldCount: Int,
    val knownMatchedCount: Long?,
    val hasMore: Boolean,
    val snapshotRevision: Long,
) {
    init {
        require(queryId.isNotBlank()) { "queryId must not be blank" }
        require(unknownFieldCount >= 0) { "unknownFieldCount must not be negative" }
        require(knownMatchedCount == null || knownMatchedCount >= 0) { "knownMatchedCount must not be negative" }
        require(knownMatchedCount == null || knownMatchedCount >= items.map { it.workId }.distinct().size) {
            "Known total must cover distinct returned works"
        }
        require(snapshotRevision >= 0) { "snapshotRevision must not be negative" }
        require(coverage != QueryCoverage.COMPLETE || (failedSourceIds.isEmpty() && !hasMore)) {
            "Complete coverage must have no failed sources or remaining work"
        }
    }

    companion object {
        fun create(
            queryId: String,
            items: List<CatalogItem>,
            coverage: QueryCoverage,
            failedSourceIds: Set<SourceId> = emptySet(),
            unknownFieldCount: Int = 0,
            knownMatchedCount: Long? = null,
            hasMore: Boolean = false,
            snapshotRevision: Long = 0,
        ): CatalogResult = CatalogResult(
            queryId, Collections.unmodifiableList(ArrayList(items)), coverage,
            Collections.unmodifiableSet(LinkedHashSet(failedSourceIds)),
            unknownFieldCount, knownMatchedCount, hasMore, snapshotRevision,
        )
    }
}
