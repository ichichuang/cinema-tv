package com.ichichuang.cinema.data.catalog

import com.ichichuang.cinema.domain.model.catalog.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class QueryCoordinatorTest {
    private val a = SourceId("fixture-A"); private val b = SourceId("fixture-B")
    private fun query(id: String = "query", sources: Set<SourceId> = linkedSetOf(a,b), year: Int? = null) =
        CatalogQuery.create(id, yearStart = year, yearEnd = year, enabledSourceIds = sources)
    private fun row(id: String, year: String = "1994", title: String = "same-title") = buildJsonObject {
        put("vod_id",id); put("vod_name",title); put("vod_year",year)
    }
    private fun page(next: String? = null, exhausted: Boolean = false, vararg rows: JsonObject): ByteArray = buildJsonObject {
        put("list",JsonArray(rows.toList())); put("exhausted",exhausted)
        if(next != null) put("next",next)
        put("total",999999)
    }.toString().toByteArray()
    /** Synthetic injected transport: no URL or live network. Real production parser consumes these bytes. */
    private class FixtureTransport(val responses: MutableMap<Pair<SourceId,String>,ByteArray>) : CandidateQueryTransport {
        val requested = mutableListOf<Pair<SourceId,String>>()
        val failures = mutableSetOf<SourceId>()
        override suspend fun fetch(source: SourceId, cursor: String, query: CatalogQuery, maxBytes: Int): ByteArray {
            requested += source to cursor
            if(source in failures) error("synthetic outage")
            return responses[source to cursor] ?: error("unexpected fixture cursor $source/$cursor")
        }
    }
    private class MemoryStore(private val strictRevision: Boolean = false) : CandidateQueryStore {
        val sessions = mutableMapOf<String,StoredQuerySession>()
        val evidence = mutableMapOf<SourceItemKey,MutableList<NormalizedCatalogCapture>>()
        val groups = mutableMapOf<SourceItemKey,WorkId>()
        override suspend fun load(queryId: String) = sessions[queryId]
        override suspend fun entries(keys: List<SourceItemKey>) = keys.map { key ->
            val captures = evidence.getValue(key)
            QueryCatalogEntry(key,WorkId("home:${key.sourceId.value}:${key.itemId.value}"),groups[key] ?: WorkId("home:${key.sourceId.value}:${key.itemId.value}"),
                captures.last().raw["vod_name"].scalar().orEmpty(),CatalogClaims.combine(captures.map { it.year }),
                CatalogClaims.combine(captures.map { it.facts.getValue("originalLanguages") }),CatalogClaims.combine(captures.map { it.facts.getValue("countries") }),listOf("entry:${key.sourceId.value}:${key.itemId.value}"))
        }
        override suspend fun commit(session: StoredQuerySession, captures: List<NormalizedCatalogCapture>, budget: QueryBudget, guard: () -> Unit): CommittedQueryState {
            guard(); require(sessions.containsKey(session.query.queryId) || sessions.size < budget.maxSessions)
            val previous=sessions[session.query.queryId]
            if(strictRevision) require(previous==null || session==previous || session.revision==previous.revision+1) { "Stale query revision" }
            captures.forEach { c -> c.itemId?.let { evidence.getOrPut(SourceItemKey(c.sourceId,it)) { mutableListOf() }.add(c) } }
            val projection=entries(session.evidenceKeys)
            val reconciled=reconcileQueryProjection(session,previous,projection)
            sessions[session.query.queryId] = reconciled; guard()
            return CommittedQueryState(reconciled,projection)
        }
    }
    @Test fun independent_cursors_deduplicate_only_source_keys_and_complete_on_explicit_exhaustion() = runBlocking {
        val s=MemoryStore(); val t=FixtureTransport(mutableMapOf(
            (a to "2") to page("3",false,row("17")), (a to "3") to page(null,true,row("17"),row("18")),
            (b to "5") to page(null,true,row("17"))))
        val c=QueryCoordinator(s,t,initialCursor={if(it==a) "2" else "5"})
        c.start(query()); assertEquals(LoadOutcome.Complete,c.loadMore("query"))
        val result=c.snapshot("query")
        assertEquals(listOf(a to "2",b to "5",a to "3"),t.requested)
        assertEquals(3,result.acceptedEntries.size); assertEquals(3,result.result.items.size)
        assertEquals(3L,result.result.knownMatchedCount); assertFalse(result.result.hasMore)
    }
    @Test fun empty_accepted_page_is_partial_then_continues_and_retains_mismatch_and_unknown_evidence() = runBlocking {
        val s=MemoryStore(); val t=FixtureTransport(mutableMapOf((a to "1") to page("2",false,row("old","2000"),row("unknown","")),
            (a to "2") to page(null,true,row("match"))))
        val c=QueryCoordinator(s,t,QueryBudget(maxRequests=1)); c.start(query(sources=setOf(a),year=1994))
        assertEquals(LoadOutcome.Partial(PartialReason.BUDGET_EXHAUSTED),c.loadMore("query"))
        val partial=c.snapshot("query"); assertTrue(partial.acceptedEntries.isEmpty()); assertTrue(partial.result.hasMore)
        assertNull(partial.result.knownMatchedCount); assertEquals(2,s.evidence.size)
        assertEquals(LoadOutcome.Complete,c.loadMore("query")); assertEquals(1,c.snapshot("query").verifiedCurrentMatchCount)
        assertEquals(3,c.snapshot("query").retainedEvidenceKeys.size)
    }
    @Test fun partial_failure_keeps_success_and_retry_cursor_without_guessing_remote_total() = runBlocking {
        val s=MemoryStore(); val t=FixtureTransport(mutableMapOf((a to "1") to page(null,true,row("a")),(b to "1") to page(null,true,row("b"))))
        t.failures+=b;val c=QueryCoordinator(s,t);c.start(query())
        assertEquals(LoadOutcome.Partial(PartialReason.SOURCE_FAILURE),c.loadMore("query"))
        val partial=c.snapshot("query"); assertEquals(1,partial.verifiedCurrentMatchCount);assertNull(partial.result.knownMatchedCount)
        assertEquals(setOf(b),partial.result.failedSourceIds);assertEquals("1",partial.sourceCursors.last().nextCursor)
        t.failures.clear();assertEquals(LoadOutcome.Complete,c.loadMore("query"));assertEquals(2L,c.snapshot("query").result.knownMatchedCount)
    }
    @Test fun verified_groups_keep_home_and_playback_entries_and_same_title_never_merges() = runBlocking {
        val s=MemoryStore();val t=FixtureTransport(mutableMapOf((a to "1") to page(null,true,row("17")),(b to "1") to page(null,true,row("17"))))
        val c=QueryCoordinator(s,t);c.start(query());c.loadMore("query");assertEquals(2,c.snapshot("query").result.items.size)
        s.groups[SourceItemKey(a,SourceItemId("17"))]=WorkId("verified-group")
        s.groups[SourceItemKey(b,SourceItemId("17"))]=WorkId("verified-group")
        val linked=c.snapshot("query");assertEquals(1,linked.result.items.size);assertEquals(2,linked.acceptedEntries.size)
        assertEquals(2,linked.acceptedEntries.map { it.homeWorkId }.distinct().size);assertEquals(2,linked.acceptedEntries.flatMap { it.playbackEntryIds }.size)
    }
    @Test fun repeated_page_and_cursor_cycles_stop_partial_in_finite_requests() = runBlocking {
        for (cycle in listOf(false,true)) {
            val s=MemoryStore(); val t=FixtureTransport(mutableMapOf((a to "1") to page("2",false,row("17")),
                (a to "2") to page(if(cycle) "1" else "3",false,row("17"))))
            val c=QueryCoordinator(s,t);c.start(query(sources=setOf(a)));assertTrue(c.loadMore("query") is LoadOutcome.Partial)
            assertEquals(2,t.requested.size);assertFalse(c.snapshot("query").result.hasMore);assertNull(c.snapshot("query").result.knownMatchedCount)
            c.loadMore("query");assertEquals(2,t.requested.size)
        }
    }
    @Test fun byte_time_and_record_budgets_remain_partial_without_overbudget_commits() = runBlocking {
        val large=page(null,true,row("17"));val s=MemoryStore();val t=FixtureTransport(mutableMapOf((a to "1") to large))
        val c=QueryCoordinator(s,t,QueryBudget(maxBytes=large.size-1));c.start(query(sources=setOf(a)))
        assertTrue(c.loadMore("query") is LoadOutcome.Partial);assertTrue(s.evidence.isEmpty());assertNull(c.snapshot("query").result.knownMatchedCount)
        var now=0L;val timed=QueryCoordinator(MemoryStore(),CandidateQueryTransport { _,_,_,_ -> now=20;large },QueryBudget(maxElapsedMillis=10),{now})
        timed.start(query(sources=setOf(a)));assertTrue(timed.loadMore("query") is LoadOutcome.Partial);assertEquals(0,timed.snapshot("query").verifiedCurrentMatchCount)
        val bounded=QueryCoordinator(MemoryStore(),FixtureTransport(mutableMapOf((a to "1") to page(null,true,row("1"),row("2")))),QueryBudget(maxRecords=1))
        bounded.start(query(sources=setOf(a)));assertTrue(bounded.loadMore("query") is LoadOutcome.Partial);assertEquals(0,bounded.snapshot("query").verifiedCurrentMatchCount)
    }
    @Test fun cancellation_propagates_and_noncooperative_late_q1_cannot_write_after_q2_starts() = runBlocking {
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();val s=MemoryStore()
        val c=QueryCoordinator(s,CandidateQueryTransport { _,_,q,_ -> if(q.queryId=="q1") withContext(NonCancellable) {entered.complete(Unit);release.await()};page(null,true,row(q.queryId)) })
        c.start(query("q1",setOf(a)));val old=async {c.loadMore("q1")};withTimeout(2_000) { entered.await() };c.start(query("q2",setOf(a)));release.complete(Unit)
        assertEquals(LoadOutcome.Cancelled,old.await());assertFalse(s.evidence.keys.any {it.itemId.value=="q1"})
        assertEquals(LoadOutcome.Complete,c.loadMore("q2"));assertEquals("q2",c.snapshot("q2").acceptedEntries.single().key.itemId.value)
        val entered2=CompletableDeferred<Unit>();val c2=QueryCoordinator(s,CandidateQueryTransport {_,_,_,_->entered2.complete(Unit);awaitCancellation()})
        c2.start(query("cancel",setOf(a)));val job=launch {c2.loadMore("cancel")};withTimeout(2_000) { entered2.await() };job.cancelAndJoin();assertTrue(job.isCancelled)
        assertEquals(0,c2.snapshot("cancel").verifiedCurrentMatchCount)
    }
    @Test fun rejects_unsupported_filters_and_reused_query_identity_and_bounds_sessions_sources() = runBlocking {
        val c=QueryCoordinator(MemoryStore(),CandidateQueryTransport {_,_,_,_->error("must not fetch")},QueryBudget(maxSessions=1,maxSources=1))
        suspend fun rejected(q: CatalogQuery) { try {c.start(q);fail("must reject")} catch(_:IllegalArgumentException) {} }
        rejected(CatalogQuery.create("bad",contentKind=ContentKind.MOVIE,enabledSourceIds=setOf(a)))
        rejected(query(sources=setOf(a,b)));c.start(query(sources=setOf(a)))
        rejected(query(year=1994,sources=setOf(a)));rejected(query("second",setOf(a)))
    }
    @Test fun applecms_page_metadata_proves_exhaustion_but_remote_total_never_becomes_global_count() = runBlocking {
        val first=buildJsonObject { put("page",2);put("pagecount",3);put("total",888);put("list",JsonArray(listOf(row("17")))) }.toString().toByteArray()
        val last=buildJsonObject { put("page",3);put("pagecount",3);put("total",888);put("list",JsonArray(listOf(row("18")))) }.toString().toByteArray()
        val t=FixtureTransport(mutableMapOf((a to "2") to first,(a to "3") to last))
        val c=QueryCoordinator(MemoryStore(),t,initialCursor={"2"});c.start(query(sources=setOf(a)))
        assertEquals(LoadOutcome.Complete,c.loadMore("query"));assertEquals(2L,c.snapshot("query").result.knownMatchedCount)
    }
    @Test fun timed_out_source_keeps_explicit_failed_coverage_and_retry_cursor() = runBlocking {
        val c=QueryCoordinator(MemoryStore(),CandidateQueryTransport {_,_,_,_->delay(1_000);page(null,true,row("17"))},QueryBudget(maxElapsedMillis=30))
        c.start(query(sources=setOf(a)));assertTrue(c.loadMore("query") is LoadOutcome.Partial)
        assertEquals(setOf(a),c.snapshot("query").result.failedSourceIds)
        assertEquals("1",c.snapshot("query").sourceCursors.single().nextCursor)
    }
    @Test fun cancelling_observation_fences_separately_started_noncooperative_fetch() = runBlocking {
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();val observed=CompletableDeferred<Unit>();val s=MemoryStore()
        val c=QueryCoordinator(s,CandidateQueryTransport {_,_,_,_->withContext(NonCancellable) {entered.complete(Unit);release.await()};page(null,true,row("late"))})
        val observer=launch { c.observeQuery(query(sources=setOf(a))).collect { observed.complete(Unit) } }
        withTimeout(2_000) {observed.await()};val loading=async {c.loadMore("query")};withTimeout(2_000) {entered.await()}
        observer.cancelAndJoin();release.complete(Unit);assertEquals(LoadOutcome.Cancelled,loading.await());assertTrue(s.evidence.isEmpty())
    }

    @Test fun verified_group_conflict_excludes_whole_group_and_complete_unknown_total_stays_null() = runBlocking {
        val s=MemoryStore();val t=FixtureTransport(mutableMapOf((a to "1") to page(null,true,row("17","1994")),(b to "1") to page(null,true,row("17","1995"))))
        s.groups[SourceItemKey(a,SourceItemId("17"))]=WorkId("verified-group");s.groups[SourceItemKey(b,SourceItemId("17"))]=WorkId("verified-group")
        val c=QueryCoordinator(s,t);c.start(query(year=1994));c.loadMore("query")
        assertTrue(c.snapshot("query").acceptedEntries.isEmpty());assertNull(c.snapshot("query").result.knownMatchedCount)
        val unknown=QueryCoordinator(MemoryStore(),FixtureTransport(mutableMapOf((a to "1") to page(null,true,row("unknown","")))))
        unknown.start(query(sources=setOf(a),year=1994));assertEquals(LoadOutcome.Complete,unknown.loadMore("query"))
        assertNull(unknown.snapshot("query").result.knownMatchedCount)
    }
    @Test fun external_group_projection_change_advances_persisted_query_revision_on_explicit_snapshot() = runBlocking {
        val s=MemoryStore();val t=FixtureTransport(mutableMapOf((a to "1") to page(null,true,row("17")),(b to "1") to page(null,true,row("17"))))
        val c=QueryCoordinator(s,t);c.start(query());c.loadMore("query");val before=c.snapshot("query")
        s.groups[SourceItemKey(a,SourceItemId("17"))]=WorkId("group");s.groups[SourceItemKey(b,SourceItemId("17"))]=WorkId("group")
        val linked=c.snapshot("query");assertEquals(before.result.snapshotRevision+1,linked.result.snapshotRevision);assertEquals(1,linked.result.items.size)
        assertEquals(linked.result.snapshotRevision,c.snapshot("query").result.snapshotRevision)
        s.groups.clear();val split=c.snapshot("query");assertEquals(linked.result.snapshotRevision+1,split.result.snapshotRevision);assertEquals(2,split.result.items.size)
    }

    @Test fun unidentified_source_rows_preserve_evidence_and_prevent_a_reliable_total() = runBlocking {
        val noId=buildJsonObject {put("vod_name","unidentified");put("vod_year","1994")}
        val c=QueryCoordinator(MemoryStore(),FixtureTransport(mutableMapOf((a to "1") to page(null,true,noId))))
        c.start(query(sources=setOf(a)));assertEquals(LoadOutcome.Complete,c.loadMore("query"))
        assertEquals(0,c.snapshot("query").verifiedCurrentMatchCount);assertNull(c.snapshot("query").result.knownMatchedCount)
    }

    @Test fun page_delta_uses_current_revision_after_snapshot_splits_group_during_fetch() = runBlocking {
        val s=MemoryStore(strictRevision=true)
        val ka=SourceItemKey(a,SourceItemId("17"));val kb=SourceItemKey(b,SourceItemId("17"))
        s.groups[ka]=WorkId("verified-group");s.groups[kb]=WorkId("verified-group")
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val c=QueryCoordinator(s,CandidateQueryTransport {source,cursor,_,_->
            if(source==a && cursor=="2") {entered.complete(Unit);withTimeout(2_000) {release.await()};page(null,true,row("18"))}
            else if(source==a) page("2",false,row("17")) else page(null,true,row("17"))
        },QueryBudget(maxRequests=2))
        c.start(query());c.loadMore("query");val before=c.snapshot("query")
        val loading=async {runCatching {c.loadMore("query")}}
        withTimeout(2_000) {entered.await()}
        s.groups.clear();val split=c.snapshot("query")
        assertEquals(before.result.snapshotRevision+1,split.result.snapshotRevision)
        release.complete(Unit)
        val outcome=withTimeout(2_000) {loading.await()}
        assertEquals(LoadOutcome.Complete,outcome.getOrNull())
        val finished=c.snapshot("query")
        assertEquals(split.result.snapshotRevision+1,finished.result.snapshotRevision)
        assertEquals(3,finished.acceptedEntries.size);assertEquals(3,finished.result.items.size)
        assertEquals(1,s.evidence.getValue(SourceItemKey(a,SourceItemId("18"))).size)
    }
    @Test fun escaped_distinct_unpaired_surrogate_ids_do_not_collide_as_repeated_pages() = runBlocking {
        val first="""{"list":[{"vod_id":"id-\uD800","vod_name":"same","vod_year":"1994"}],"next":"2","exhausted":false}""".toByteArray()
        val last="""{"list":[{"vod_id":"id-\uDFFF","vod_name":"same","vod_year":"1994"}],"exhausted":true}""".toByteArray()
        val t=FixtureTransport(mutableMapOf((a to "1") to first,(a to "2") to last))
        val c=QueryCoordinator(MemoryStore(),t);c.start(query(sources=setOf(a)))
        assertEquals(LoadOutcome.Complete,c.loadMore("query"))
        assertEquals(setOf("id-\uD800","id-\uDFFF"),c.snapshot("query").acceptedEntries.map {it.key.itemId.value}.toSet())
        assertEquals(2,c.snapshot("query").sourceCursors.single().pageFingerprints.distinct().size)
    }
    @Test fun malformed_utf8_is_source_failure_and_preserves_successful_source_results() = runBlocking {
        val s=MemoryStore();val t=FixtureTransport(mutableMapOf((a to "1") to page(null,true,row("good")),
            (b to "1") to byteArrayOf(0xc3.toByte(),0x28)))
        val c=QueryCoordinator(s,t);c.start(query())
        val outcome=runCatching {c.loadMore("query")}
        assertEquals(LoadOutcome.Partial(PartialReason.SOURCE_FAILURE),outcome.getOrNull())
        val snapshot=c.snapshot("query")
        assertEquals(setOf(b),snapshot.result.failedSourceIds);assertEquals(1,snapshot.verifiedCurrentMatchCount)
        assertEquals("good",snapshot.acceptedEntries.single().key.itemId.value)
        assertEquals("1",snapshot.sourceCursors.last().nextCursor);assertNull(snapshot.result.knownMatchedCount)
    }

}
