package com.ichichuang.cinema.data.catalog

import com.ichichuang.cinema.domain.model.catalog.*
import com.ichichuang.cinema.domain.filter.*
import com.ichichuang.cinema.domain.repository.CatalogRepository
import com.ichichuang.cinema.data.local.codec.storageJson
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.security.MessageDigest

/** Offline candidate only. Fetch concurrency is one, with independent durable source cursors. */
internal class QueryCoordinator(private val store: CandidateQueryStore, private val transport: CandidateQueryTransport,
    private val budget: QueryBudget = QueryBudget(), private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val initialCursor: (SourceId) -> String = { "1" }) : CatalogRepository {
    private class Run(var session: StoredQuerySession, snapshot: CandidateQuerySnapshot) {
        val updates = MutableStateFlow<CandidateQuerySnapshot?>(snapshot)
    }
    private val gate = Mutex()
    private val loads = Mutex()
    private var active: Run? = null

    private fun validate(query: CatalogQuery) {
        require(query.queryId.length <= 256 && query.enabledSourceIds.size <= budget.maxSources)
        require(query.enabledSourceIds.all { it.value.length <= 256 })

    }
    private suspend fun activate(query: CatalogQuery): Run = gate.withLock {
        validate(query)
        val stored = store.load(query.queryId)
        require(stored == null || stored.query == query) { "Query identity cannot be reused with different filters" }
        val session = stored ?: StoredQuerySession(query,query.enabledSourceIds.map { source ->
            SourceCursorState(source,initialCursor(source).also { require(it.isNotBlank() && it.length <= 256) })
        })
        val context = currentCoroutineContext()
        val committed=store.commit(session,emptyList(),budget) { context.ensureActive() }
        val run = Run(committed.session,buildSnapshot(committed.session,committed.entries))
        active?.updates?.value = null
        active = run
        run
    }
    suspend fun start(query: CatalogQuery): CandidateQuerySnapshot = requireNotNull(activate(query).updates.value)
    suspend fun snapshot(queryId: String): CandidateQuerySnapshot = gate.withLock {
        val context=currentCoroutineContext()
        val committed=store.commit(requireNotNull(store.load(queryId)) { "Unknown query" },emptyList(),budget) {context.ensureActive()}
        val snapshot=buildSnapshot(committed.session,committed.entries)
        active?.takeIf {it.session.query.queryId==queryId}?.let {it.session=committed.session;it.updates.value=snapshot}
        snapshot
    }
    override fun observeQuery(query: CatalogQuery): Flow<CatalogResult> = flow {
        val run = activate(query)
        try { emitAll(run.updates.takeWhile { it != null }.map { requireNotNull(it).result }) }
        finally { withContext(NonCancellable) { gate.withLock { if(active === run) active = null } } }
    }
    private fun buildSnapshot(session: StoredQuerySession, evidence: List<QueryCatalogEntry>): CandidateQuerySnapshot {
        val query=session.query
        val groups=evidence.groupBy {it.groupWorkId}
        fun factsOf(members: List<QueryCatalogEntry>): StrictWorkFacts {
            fun <T> combine(field: (StrictWorkFacts)->FieldEvidence<T>)=CatalogClaims.combine(members.map {field(it.facts)})
            return StrictWorkFacts(combine {it.year},combine {it.countries},combine {it.genres},
                combine {it.contentKind},combine {it.originalLanguages},combine {it.completeOriginalLanguages})
        }
        val evaluations=groups.mapValues {StrictFilterEvaluator.evaluate(query,factsOf(it.value))}
        val acceptedGroups=groups.filterKeys {evaluations.getValue(it).verdict==MatchVerdict.MATCH}
        val accepted=acceptedGroups.values.flatten()
        val unsorted=acceptedGroups.map { (group,members) -> CatalogItem(group,members.first().title,
            CatalogClaims.combine(members.map {it.year}),CatalogClaims.combine(members.map {it.originalLanguages}),CatalogClaims.combine(members.map {it.countries})) }
        fun year(item:CatalogItem):Int?=when(val fact=item.year) {
            is FieldEvidence.SourceReported -> fact.value
            is FieldEvidence.Confirmed -> fact.value
            else -> null
        }
        val chronological=Comparator<CatalogItem> {left,right ->
            val a=year(left);val b=year(right)
            val ordered=when {a==null && b==null->0;a==null->1;b==null->-1
                query.sort==CatalogSort.YEAR_DESCENDING->b.compareTo(a);else->a.compareTo(b)}
            if(ordered!=0) ordered else compareValuesBy(left,right,{it.title},{it.workId.value})
        }
        val items=if(query.sort==CatalogSort.DEFAULT) unsorted else unsorted.sortedWith(chronological)
        val complete=session.cursors.all {it.exhausted}
        val unresolvedCount=evaluations.values.count {evaluation -> evaluation.dimensions.values.any {it.verdict==MatchVerdict.INDETERMINATE}}
        val unresolved=unresolvedCount>0
        val more=session.cursors.any {!it.exhausted && it.stopReason==null}
        val unknown=evaluations.values.sumOf {evaluation -> evaluation.dimensions.values.count {it.verdict==MatchVerdict.INDETERMINATE}} + session.unidentifiedRecords
        val scopedCount=if(complete && !unresolved && session.unidentifiedRecords==0) items.size.toLong() else null
        val display=buildList {
            if(!complete) add("仍有内容未查询完成")
            if(session.cursors.any {it.failure!=null}) add("部分来源查询失败")
            if(evaluations.values.any {it.dimensions.getValue("year").verdict==MatchVerdict.INDETERMINATE}) add("部分影片缺少年份资料或年份存在冲突")
            if(unresolved || session.unidentifiedRecords>0) add("部分影片资料不足或存在冲突")
            if(complete && !unresolved && session.unidentifiedRecords==0) add(if(items.isEmpty()) "已查询来源中未找到符合条件的影片" else "已查询来源筛选完成")
        }.joinToString("；")
        return CandidateQuerySnapshot(CatalogResult.create(query.queryId,items,if(complete) QueryCoverage.COMPLETE else QueryCoverage.PARTIAL,
            session.cursors.filter {it.failure!=null}.map {it.sourceId}.toSet(),unknown,
            scopedCount,more,session.revision),session.cursors.toList(),accepted,session.evidenceKeys.toList(),items.size,
            StrictQueryProjection(query,items.size,scopedCount,unresolvedCount,
                if(complete && !unresolved && session.unidentifiedRecords==0) StrictSortScope.EXHAUSTED_SELECTED_SOURCES else StrictSortScope.OBSERVED_ONLY,display))
    }
    override suspend fun loadMore(queryId: String): LoadOutcome = try { loads.withLock {
        val run = gate.withLock { active?.takeIf { it.session.query.queryId == queryId } } ?: return@withLock LoadOutcome.Cancelled
        val started = monotonicMillis()
        var requests = 0
        var bytes = 0
        val failedThisLoad = mutableSetOf<SourceId>()
        suspend fun save(expectedCursor: SourceCursorState, captures: List<NormalizedCatalogCapture> = emptyList(),
            update: (StoredQuerySession) -> StoredQuerySession): Boolean = gate.withLock {
            currentCoroutineContext().ensureActive()
            if(active !== run) return@withLock false
            val current=run.session
            // A snapshot may have refreshed only projection/revision during fetch. Apply the page
            // to that current session, while refusing any changed cursor or retired generation.
            if(current.cursors.singleOrNull {it.sourceId==expectedCursor.sourceId} != expectedCursor) return@withLock false
            val session=update(current)
            val context = currentCoroutineContext()
            val committed=store.commit(session,captures,budget) { context.ensureActive(); check(active === run) { "Superseded query" } }
            run.session = committed.session
            run.updates.value = buildSnapshot(committed.session,committed.entries)
            true
        }
        while(true) {
            currentCoroutineContext().ensureActive()
            if(gate.withLock { active !== run }) return@withLock LoadOutcome.Cancelled
            val session=run.session
            if(session.cursors.all { it.exhausted }) return@withLock LoadOutcome.Complete
            val candidates=session.cursors.filter { !it.exhausted && it.stopReason == null && it.sourceId !in failedThisLoad }
            if(candidates.isEmpty()) return@withLock LoadOutcome.Partial(if(session.cursors.any { it.failure != null }) PartialReason.SOURCE_FAILURE else PartialReason.BUDGET_EXHAUSTED)
            if(requests >= budget.maxRequests || bytes >= budget.maxBytes || monotonicMillis()-started >= budget.maxElapsedMillis)
                return@withLock LoadOutcome.Partial(PartialReason.BUDGET_EXHAUSTED)
            // Round-robin by completed page count, preserving initial A2/B5 rather than sharing a page number.
            val cursor=candidates.minBy { it.requestedCursors.size }
            if(cursor.requestedCursors.size >= budget.maxPagesPerSource || session.capturedRecords >= budget.maxRecords) {
                val stopped=cursor.copy(stopReason="record-or-page-budget")
                if(!save(cursor) { current -> current.copy(cursors=current.cursors.map {if(it.sourceId==cursor.sourceId) stopped else it},revision=current.revision+1) })
                    return@withLock LoadOutcome.Cancelled
                continue
            }
            requests++
            var timedOut=false
            val response = try {
                withTimeout((budget.maxElapsedMillis-(monotonicMillis()-started)).coerceAtLeast(1)) {
                    transport.fetch(cursor.sourceId,cursor.nextCursor,session.query,budget.maxBytes-bytes)
                }
            } catch (cancelled: CancellationException) {
                currentCoroutineContext().ensureActive()
                if(cancelled !is TimeoutCancellationException) throw cancelled
                timedOut=true
                null
            } catch (_: Exception) { null }
            currentCoroutineContext().ensureActive()
            if(gate.withLock { active !== run }) return@withLock LoadOutcome.Cancelled
            if(!timedOut && monotonicMillis()-started >= budget.maxElapsedMillis) return@withLock LoadOutcome.Partial(PartialReason.BUDGET_EXHAUSTED)
            if(response != null && response.size > budget.maxBytes-bytes) return@withLock LoadOutcome.Partial(PartialReason.BUDGET_EXHAUSTED)
            bytes += response?.size ?: 0
            val page = try { response?.let { parsePage(it,cursor.sourceId,cursor.nextCursor) } }
                catch (_: IllegalArgumentException) { null }
                catch (_: IOException) { null }
            if(page == null) {
                failedThisLoad += cursor.sourceId
                val failure=cursor.copy(failure="source-response-failed")
                if(!save(cursor) { current -> current.copy(cursors=current.cursors.map {if(it.sourceId==cursor.sourceId) failure else it},revision=current.revision+1) })
                    return@withLock LoadOutcome.Cancelled
                if(timedOut) return@withLock LoadOutcome.Partial(PartialReason.SOURCE_FAILURE)
                continue
            }
            if(page.captures.size > budget.maxRecords-session.capturedRecords) return@withLock LoadOutcome.Partial(PartialReason.BUDGET_EXHAUSTED)
            val repeated = page.fingerprint in cursor.pageFingerprints
            val visited = cursor.requestedCursors + cursor.nextCursor
            val cyclic = !page.exhausted && (page.next == null || page.next in visited)
            val next=cursor.copy(nextCursor=page.next ?: cursor.nextCursor,exhausted=page.exhausted && !repeated,
                failure=null,stopReason=when { repeated -> "repeated-page"; cyclic -> "cursor-cycle"; else -> null },
                requestedCursors=visited,pageFingerprints=cursor.pageFingerprints+page.fingerprint)
            val captures=if(repeated) emptyList() else page.captures
            if(!save(cursor,captures) { current ->
                val keys=(current.evidenceKeys+captures.mapNotNull {c -> c.itemId?.let {SourceItemKey(c.sourceId,it)}}).distinct()
                current.copy(cursors=current.cursors.map {if(it.sourceId==cursor.sourceId) next else it},
                    evidenceKeys=keys,revision=current.revision+1,capturedRecords=current.capturedRecords+captures.size,
                    unidentifiedRecords=current.unidentifiedRecords+captures.count {it.itemId==null})
            }) return@withLock LoadOutcome.Cancelled
        }
        @Suppress("UNREACHABLE_CODE") LoadOutcome.Cancelled
    } } catch (_: QueryStateLimitException) { LoadOutcome.Partial(PartialReason.BUDGET_EXHAUSTED) }
    private data class Page(val captures: List<NormalizedCatalogCapture>,val next: String?,val exhausted: Boolean,val fingerprint: String)
    private fun parsePage(bytes: ByteArray, source: SourceId, requestedCursor: String): Page {
        val raw=Json.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence=true)).jsonObject
        val rows=raw["list"]?.jsonArray ?: throw IllegalArgumentException("Missing AppleCMS list")
        require(rows.size <= budget.maxPageRecords)
        val page=raw["page"]?.jsonPrimitive?.intOrNull
        val pageCount=raw["pagecount"]?.jsonPrimitive?.intOrNull
        val declaredPages=page!=null && pageCount!=null
        if(declaredPages) require(page!!>0 && pageCount!!>=page && requestedCursor.toIntOrNull()==page) { "Source page did not match requested cursor" }
        val next=raw["next"]?.jsonPrimitive?.contentOrNull?.also { require(it.isNotBlank() && it.length <= 256) }
        // Empty list and raw total are deliberately insufficient to declare exhaustion.
        val exhausted=raw["exhausted"]?.jsonPrimitive?.booleanOrNull == true || (declaredPages && page==pageCount)
        val captures=rows.map { MaccmsCatalogNormalizer.normalize(source,it.jsonObject,0).also { c -> require(c.itemId == null || c.itemId.value.length <= 256) } }
        val fingerprint=MessageDigest.getInstance("SHA-256").digest(rows.storageJson().toByteArray()).joinToString("") { "%02x".format(it) }
        return Page(captures,next ?: if(declaredPages && !exhausted) (page!!+1).toString() else null,exhausted,fingerprint)
    }
}
