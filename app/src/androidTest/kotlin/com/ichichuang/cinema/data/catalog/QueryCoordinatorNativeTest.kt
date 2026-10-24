package com.ichichuang.cinema.data.catalog

import com.ichichuang.cinema.data.local.NativeStorageFixture
import com.ichichuang.cinema.data.local.codec.storageJson
import com.ichichuang.cinema.domain.model.catalog.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Real private Room files and injected synthetic AppleCMS bytes only. */
class QueryCoordinatorNativeTest : NativeStorageFixture() {
    private val a=SourceId("query-fixture-A");private val b=SourceId("query-fixture-B")
    private fun query(id: String="native-query",sources: Set<SourceId> = linkedSetOf(a,b),year: Int?=null) =
        CatalogQuery.create(id,yearStart=year,yearEnd=year,enabledSourceIds=sources)
    private fun raw(id: String,year: String="1994")=buildJsonObject {
        put("vod_id",id);put("vod_name","synthetic-same-title");put("vod_year",year)
        put("vod_play_from","fixture-track");put("vod_play_url","fixture-episode${'$'}fixture:media")
    }
    private fun page(next: String?=null,exhausted: Boolean=false,vararg rows: JsonObject)=buildJsonObject {
        put("list",JsonArray(rows.toList()));put("exhausted",exhausted);put("total",9000)
        next?.let {put("next",it)}
    }.storageJson().toByteArray()
    private suspend fun sources(store: CandidateCatalogStore) {
        store.registerSource(a,"fixture:query-A");store.registerSource(b,"fixture:query-B")
    }
    @Test fun query01_independent_cursors_partial_revision_and_source_keys_survive_reopen() = runBlocking<Unit> {
        sources(CandidateCatalogStore(database))
        val fixtures=mapOf((a to "2") to page("3",false,raw("17")),(b to "5") to page("6",false,raw("17")),
            (a to "3") to page(null,true,raw("18")),(b to "6") to page(null,true,raw("18")))
        val transport=CandidateQueryTransport {source,cursor,_,_->fixtures.getValue(source to cursor)}
        var c=QueryCoordinator(RoomCandidateQueryStore(database),transport,QueryBudget(maxRequests=2),initialCursor={if(it==a) "2" else "5"})
        c.start(query());assertEquals(LoadOutcome.Partial(PartialReason.BUDGET_EXHAUSTED),c.loadMore("native-query"))
        val partial=c.snapshot("native-query");assertEquals(listOf("3","6"),partial.sourceCursors.map {it.nextCursor})
        assertEquals(2L,partial.result.snapshotRevision);assertNull(partial.result.knownMatchedCount);assertEquals(2,partial.verifiedCurrentMatchCount)
        reopen();c=QueryCoordinator(RoomCandidateQueryStore(database),transport,QueryBudget(maxRequests=2),initialCursor={"wrong-initial"})
        assertEquals(partial,c.start(query()));assertEquals(LoadOutcome.Complete,c.loadMore("native-query"))
        val complete=c.snapshot("native-query");assertEquals(4,complete.acceptedEntries.size);assertEquals(4L,complete.result.knownMatchedCount)
        assertEquals(4,complete.acceptedEntries.map {it.homeWorkId}.distinct().size)
    }
    @Test fun query02_ignored_year_and_unknown_rows_remain_evidence_outside_accepted_matches() = runBlocking<Unit> {
        val catalog=CandidateCatalogStore(database);sources(catalog)
        val fixtures=mapOf((a to "1") to page("2",false,raw("wrong","2000"),raw("unknown",""),buildJsonObject {put("vod_name","missing-id");put("vod_year","")}),
            (a to "2") to page(null,true,raw("match")))
        val c=QueryCoordinator(RoomCandidateQueryStore(database),CandidateQueryTransport {source,cursor,_,_->fixtures.getValue(source to cursor)},QueryBudget(maxRequests=1))
        c.start(query(sources=setOf(a),year=1994));c.loadMore("native-query");val partial=c.snapshot("native-query")
        assertTrue(partial.acceptedEntries.isEmpty());assertEquals(2,partial.retainedEvidenceKeys.size);assertEquals(1,catalog.pending(a).size)
        reopen();val reopened=CandidateCatalogStore(database)
        assertEquals("2000",reopened.captures(SourceItemKey(a,SourceItemId("wrong"))).single().raw["vod_year"].scalar())
        assertTrue(reopened.captures(SourceItemKey(a,SourceItemId("unknown"))).single().year is FieldEvidence.Unknown)
        val next=QueryCoordinator(RoomCandidateQueryStore(database),CandidateQueryTransport {source,cursor,_,_->fixtures.getValue(source to cursor)})
        next.start(query(sources=setOf(a),year=1994));assertEquals(LoadOutcome.Complete,next.loadMore("native-query"))
        assertEquals(1,next.snapshot("native-query").verifiedCurrentMatchCount);assertEquals(1,reopened.pending(a).size)
    }
    @Test fun query03_late_guard_or_cancellation_rolls_back_evidence_and_session_together() = runBlocking<Unit> {
        val catalog=CandidateCatalogStore(database);sources(catalog);val store=RoomCandidateQueryStore(database)
        val session=store.commit(StoredQuerySession(query(sources=setOf(a)),listOf(SourceCursorState(a,"1"))),emptyList(),QueryBudget()) {}.session
        val key=SourceItemKey(a,SourceItemId("17"));val capture=MaccmsCatalogNormalizer.normalize(a,raw("17"),0)
        for(cancel in listOf(false,true)) {
            var guards=0
            try {
                store.commit(session.copy(evidenceKeys=listOf(key),revision=1,capturedRecords=1),listOf(capture),QueryBudget()) {
                    guards++;if(guards==2) {if(cancel) throw CancellationException("synthetic cancellation") else error("synthetic supersession")}
                };fail("must reject late write")
            } catch(_:CancellationException) {assertTrue(cancel)} catch(_:IllegalStateException) {assertFalse(cancel)}
            assertNull(catalog.workFor(key));assertTrue(catalog.captures(key).isEmpty());assertEquals(session,store.load(session.query.queryId))
        }
        reopen();assertEquals(session,RoomCandidateQueryStore(database).load(session.query.queryId))
        assertNull(CandidateCatalogStore(database).workFor(key))
    }
    @Test fun query04_only_actual_verified_group_deduplicates_and_keeps_both_home_and_playback_entries() = runBlocking<Unit> {
        fun key(source:SourceId)=SourceItemKey(source,SourceItemId("17"))
        val proofs=CatalogIdentityProofs(mapOf("query-proof" to VerifiedCatalogAssociation(key(a),key(b),CatalogIdentifier("fixture","17"))))
        val catalog=CandidateCatalogStore(database,proofs);sources(catalog)
        for(source in listOf(a,b)) catalog.ingest("query-seed-${source.value}",MaccmsCatalogNormalizer.normalize(source,raw("17"),0),
            listOf(DeclaredCatalogEntry("query-entry-${source.value}",EpisodeId("episode-${source.value}"),VariantId("variant-${source.value}"),0,0)))
        val c=QueryCoordinator(RoomCandidateQueryStore(database,catalog),CandidateQueryTransport {_,_,_,_->page(null,true,raw("17"))})
        c.start(query());c.loadMore("native-query");assertEquals(2,c.snapshot("native-query").result.items.size)
        assertEquals(AssociationDecision.LINKED,catalog.associate(key(a),key(b),"query-proof",1))
        val snapshot=c.snapshot("native-query");assertEquals(1,snapshot.result.items.size);assertEquals(2,snapshot.acceptedEntries.size)
        assertEquals(2,snapshot.acceptedEntries.map {it.homeWorkId}.distinct().size)
        assertEquals(2,snapshot.acceptedEntries.flatMap {it.playbackEntryIds}.size)
        reopen();val recovered=QueryCoordinator(RoomCandidateQueryStore(database),CandidateQueryTransport {_,_,_,_->error("must not fetch")})
        assertEquals(snapshot,recovered.start(query()))
    }
    @Test fun query05_session_bound_survives_recreation_and_rejects_new_session_without_loss() = runBlocking<Unit> {
        sources(CandidateCatalogStore(database));val budget=QueryBudget(maxSessions=1)
        val transport=CandidateQueryTransport {_,_,_,_->page(null,true,raw("17"))}
        val first=QueryCoordinator(RoomCandidateQueryStore(database),transport,budget);first.start(query(sources=setOf(a)));first.loadMore("native-query")
        val snapshot=first.snapshot("native-query");reopen()
        val second=QueryCoordinator(RoomCandidateQueryStore(database),transport,budget)
        try {second.start(query("excess",setOf(a)));fail("session limit")} catch(_:IllegalArgumentException) {}
        assertEquals(snapshot,second.start(query(sources=setOf(a))));assertNull(RoomCandidateQueryStore(database).load("excess"))
    }
    @Test fun query06_opaque_utf16_query_and_source_item_ids_survive_sqlite_text_and_blob_boundaries() = runBlocking<Unit> {
        val source=SourceId("query-source-\uD800");val item="query-item-\uDFFF";val id="query-id-\uD800"
        CandidateCatalogStore(database).registerSource(source,"fixture:query-surrogate")
        val c=QueryCoordinator(RoomCandidateQueryStore(database),CandidateQueryTransport {_,_,_,_->page(null,true,raw(item))})
        val q=query(id,setOf(source));c.start(q);c.loadMore(id);val before=c.snapshot(id)
        reopen();val next=QueryCoordinator(RoomCandidateQueryStore(database),CandidateQueryTransport {_,_,_,_->error("must not fetch")})
        assertEquals(before,next.start(q));assertEquals(item,next.snapshot(id).acceptedEntries.single().key.itemId.value)
    }

}
