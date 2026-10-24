package com.ichichuang.cinema.data.catalog

import com.ichichuang.cinema.domain.model.catalog.*

/** Opaque source-owned cursors. Only an explicit source response proves exhaustion. */
internal data class SourceCursorState(
    val sourceId: SourceId,
    val nextCursor: String,
    val exhausted: Boolean = false,
    val failure: String? = null,
    val stopReason: String? = null,
    val requestedCursors: List<String> = emptyList(),
    val pageFingerprints: List<String> = emptyList(),
)
internal data class QueryBudget(
    val maxRequests: Int = 16,
    val maxBytes: Int = 1_048_576,
    val maxElapsedMillis: Long = 5_000,
    val maxPagesPerSource: Int = 32,
    val maxPageRecords: Int = 100,
    val maxRecords: Int = 500,
    val maxSources: Int = 16,
    val maxSessions: Int = 8,
    val maxPayloadBytes: Int = 262_144,
) {
    init {
        require(maxRequests in 1..128 && maxBytes in 1..4_194_304 && maxElapsedMillis in 1..60_000)
        require(maxPagesPerSource in 1..128 && maxPageRecords in 1..500 && maxRecords in 1..2_000)
        require(maxSources in 1..32 && maxSessions in 1..32 && maxPayloadBytes in 1..1_048_576)
    }
}
internal data class StoredQuerySession(val query: CatalogQuery, val cursors: List<SourceCursorState>,
    val evidenceKeys: List<SourceItemKey> = emptyList(), val revision: Long = 0, val capturedRecords: Int = 0, val projectionDigest: String = "", val unidentifiedRecords: Int = 0)
internal data class QueryCatalogEntry(val key: SourceItemKey, val homeWorkId: WorkId, val groupWorkId: WorkId,
    val title: String, val year: FieldEvidence<Int>, val originalLanguages: FieldEvidence<FactIds>,
    val countries: FieldEvidence<FactIds>, val playbackEntryIds: List<String>)
internal data class CandidateQuerySnapshot(val result: CatalogResult, val sourceCursors: List<SourceCursorState>,
    val acceptedEntries: List<QueryCatalogEntry>, val retainedEvidenceKeys: List<SourceItemKey>,
    val verifiedCurrentMatchCount: Int)
internal data class CommittedQueryState(val session: StoredQuerySession, val entries: List<QueryCatalogEntry>)
internal class QueryStateLimitException(message: String) : IllegalArgumentException(message)
internal interface CandidateQueryStore {
    suspend fun load(queryId: String): StoredQuerySession?
    suspend fun entries(keys: List<SourceItemKey>): List<QueryCatalogEntry>
    /** The guard MUST run inside the same transaction, before and after all evidence/session writes. */
    suspend fun commit(session: StoredQuerySession, captures: List<NormalizedCatalogCapture>, budget: QueryBudget, guard: () -> Unit): CommittedQueryState
}
internal fun interface CandidateQueryTransport {
    /** Transport must bound reading to maxBytes; coordinator also rejects overlarge returned buffers. */
    suspend fun fetch(source: SourceId, cursor: String, query: CatalogQuery, maxBytes: Int): ByteArray
}
