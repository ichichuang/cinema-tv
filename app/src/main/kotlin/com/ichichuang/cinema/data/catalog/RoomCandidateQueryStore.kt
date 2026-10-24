package com.ichichuang.cinema.data.catalog

import androidx.room.withTransaction
import com.ichichuang.cinema.data.local.CinemaDatabase
import com.ichichuang.cinema.data.local.codec.Utf16KeyConverter
import com.ichichuang.cinema.data.local.entity.CandidateQuerySessionEntity
import com.ichichuang.cinema.domain.model.catalog.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.UUID

/** Candidate evidence and cursor revision share one Room transaction; no runtime writer authority. */
internal class RoomCandidateQueryStore(private val database: CinemaDatabase,
    private val catalog: CandidateCatalogStore = CandidateCatalogStore(database)) : CandidateQueryStore {
    private val dao=database.querySubscription()
    private val converter=Utf16KeyConverter()
    override suspend fun load(queryId: String): StoredQuerySession? = dao.query(converter.encode(queryId))?.let {
        QuerySessionCodec.decode(it.payload).also { session -> require(session.query.queryId==queryId) }
    }
    override suspend fun entries(keys: List<SourceItemKey>): List<QueryCatalogEntry> = database.withTransaction {
        keys.map { key ->
            val captures=catalog.captures(key)
            QueryCatalogEntry(key,requireNotNull(catalog.workFor(key)),requireNotNull(catalog.groupFor(key)),
                captures.lastOrNull()?.raw?.get("vod_name").scalar().orEmpty(),StrictCaptureFacts.fromCaptures(captures),catalog.entries(key))
        }
    }
    override suspend fun commit(session: StoredQuerySession, captures: List<NormalizedCatalogCapture>, budget: QueryBudget, guard: () -> Unit): CommittedQueryState = database.withTransaction {
        currentCoroutineContext().ensureActive();guard()
        val previous=load(session.query.queryId)
        if(previous == null && dao.queryCount()>=budget.maxSessions) throw QueryStateLimitException("Candidate query session limit")
        require(previous == null || previous.query==session.query) { "Query identity changed" }
        require(previous == null || session==previous || session.revision==previous.revision+1) { "Stale query revision" }
        require(session.capturedRecords==(previous?.capturedRecords ?: 0)+captures.size)
        require(session.unidentifiedRecords==(previous?.unidentifiedRecords ?: 0)+captures.count {it.itemId==null})
        require(session.evidenceKeys==((previous?.evidenceKeys ?: emptyList())+captures.mapNotNull { c -> c.itemId?.let {SourceItemKey(c.sourceId,it)} }).distinct())
        require(captures.all {it.sourceId in session.query.enabledSourceIds})
        if(session.cursors.size>budget.maxSources || session.capturedRecords>budget.maxRecords ||
            session.cursors.any {it.requestedCursors.size>budget.maxPagesPerSource}) throw QueryStateLimitException("Candidate query record/page/source limit")
        // One random token per immutable observation; mismatches and unknowns retain their source evidence.
        captures.forEach {catalog.ingest("candidate-query-${UUID.randomUUID()}",it)}
        val projection=entries(session.evidenceKeys)
        val reconciled=reconcileQueryProjection(session,previous,projection)
        val payload=QuerySessionCodec.encode(reconciled)
        if(payload.toByteArray().size>budget.maxPayloadBytes) throw QueryStateLimitException("Candidate query payload limit")
        require(QuerySessionCodec.decode(payload)==reconciled)
        dao.query(CandidateQuerySessionEntity(reconciled.query.queryId,payload))
        currentCoroutineContext().ensureActive();guard()
        CommittedQueryState(reconciled,projection)
    }
}
