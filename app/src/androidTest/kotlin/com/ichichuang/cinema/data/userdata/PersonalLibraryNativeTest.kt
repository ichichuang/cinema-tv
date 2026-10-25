package com.ichichuang.cinema.data.userdata

import com.ichichuang.cinema.data.local.NativeStorageFixture
import com.ichichuang.cinema.data.catalog.SourceItemKey
import com.ichichuang.cinema.domain.model.catalog.*
import com.ichichuang.cinema.domain.model.userdata.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Real isolated file-backed Room; no actual user records, source or media network. */
class PersonalLibraryNativeTest : NativeStorageFixture() {
    private val a = PlaybackTarget(WorkId("library-a"), EpisodeId("episode-1"), VariantId("cut-a"))
    private val b = PlaybackTarget(WorkId("library-b"), EpisodeId("episode-2"), VariantId("cut-b"))
    private fun library(hook: suspend () -> Unit = {}) = RoomPersonalLibraryRepository(database, beforeCommit = hook)
    private fun store() = RoomPlaybackProgressStore(database)
    private suspend fun play(target: PlaybackTarget = a, id: String = "session-a", position: Long = 9000, capturedAt:Long=-90): PlaybackSnapshot {
        assertEquals(CommitResult.Committed, store().beginSession(target,id))
        assertEquals(CommitResult.Committed, library().confirmPlayback(id))
        val snapshot=PlaybackSnapshot(target.workId,target.episodeId,target.variantId,id,0,position,120000,WatchState.IN_PROGRESS,capturedAt)
        assertEquals(CommitResult.Committed,store().commit(snapshot));return snapshot
    }
    @Test fun preload_and_detail_do_not_create_history_but_confirmed_play_does_once() = runBlocking<Unit> {
        val repo=library();repo.rememberCard(a.workId.value,"真实保存的标题","测试来源")
        store().beginSession(a,"session-a");assertTrue(repo.recentHistory().isEmpty())
        assertEquals(CommitResult.Committed,repo.confirmPlayback("session-a"))
        assertEquals(CommitResult.Committed,repo.confirmPlayback("session-a"))
        assertEquals(listOf("library-a"),repo.recentHistory().map { it.workId })
        assertNull(repo.recentHistory().single().snapshot);assertTrue(repo.continueWatching().isEmpty())
        assertEquals(EpisodeId("episode-1"),repo.recentHistory().single().episodeId)
        assertEquals(VariantId("cut-a"),repo.recentHistory().single().variantId)
        assertEquals("session-a",repo.recentHistory().single().sessionId)
        reopen();assertEquals("真实保存的标题",library().recentHistory().single().title)
    }
    @Test fun remove_from_continue_preserves_history_favorite_and_checkpoint() = runBlocking<Unit> {
        val snapshot=play();library().setFavorite(a.workId.value,true)
        assertEquals(CommitResult.Committed,library().removeFromContinue(a.workId.value))
        assertTrue(library().continueWatching().isEmpty());assertEquals(1,library().recentHistory().size)
        assertEquals(1,library().favorites().size);assertEquals(snapshot,store().loadResume(a.workId))
        reopen();assertTrue(library().continueWatching().isEmpty());assertEquals(snapshot,store().loadResume(a.workId))
    }
    @Test fun dismissed_resume_requires_new_confirmed_session_and_eligible_resume() = runBlocking<Unit> {
        play();library().removeFromContinue(a.workId.value)
        assertEquals(CommitResult.Committed,library().confirmPlayback("session-a"));assertTrue(library().continueWatching().isEmpty())
        store().beginSession(b.copy(workId=a.workId),"session-new")
        assertTrue(library().continueWatching().isEmpty())
        library().confirmPlayback("session-new");assertTrue(library().continueWatching().isEmpty())
        val next=PlaybackSnapshot(a.workId,b.episodeId,b.variantId,"session-new",0,6000,null,WatchState.IN_PROGRESS,0)
        store().commit(next);assertEquals(next,library().continueWatching().single().snapshot)
    }
    @Test fun delete_history_preserves_favorite_other_work_and_rejects_late_callbacks() = runBlocking<Unit> {
        val old=play()
        val other=b.copy(episodeId=a.episodeId,variantId=a.variantId)
        val otherSnapshot=play(other,"session-b")
        library().setFavorite(a.workId.value,true);library().setFavorite(b.workId.value,true)
        assertEquals(CommitResult.Committed,library().deleteWatchHistory(a.workId.value))
        assertEquals(CommitResult.RejectedStale,store().commit(old.copy(operationSequence=1,positionMs=20000)))
        assertEquals(CommitResult.RejectedStale,library().confirmPlayback("session-a"))
        assertEquals(CommitResult.RejectedStale,store().beginSession(a,"session-a"))
        assertNull(store().loadResume(a.workId));assertNull(database.playbackSnapshots().find("library-a","episode-1","cut-a"))
        assertEquals(listOf("library-b"),library().recentHistory().map { it.workId })
        assertEquals(setOf("library-a","library-b"),library().favorites().map { it.workId }.toSet())
        assertEquals(otherSnapshot,database.playbackSnapshots().find("library-b","episode-1","cut-a")!!.snapshot())
        reopen();assertNull(store().loadResume(a.workId));assertEquals(otherSnapshot,store().loadResume(b.workId))
        assertEquals(setOf("library-a","library-b"),library().favorites().map { it.workId }.toSet())
        play(a,"legitimate-new");assertEquals(2,library().recentHistory().size)
    }
    @Test fun favorite_idempotency_order_unfavorite_scope_and_committed_undo() = runBlocking<Unit> {
        play();library().setFavorite(a.workId.value,true);library().setFavorite(b.workId.value,true)
        library().setFavorite(a.workId.value,true)
        assertEquals(listOf("library-b","library-a"),library().favorites().map { it.workId })
        library().setFavorite(a.workId.value,false);assertEquals(1,library().recentHistory().size);assertNotNull(store().loadResume(a.workId))
        assertEquals(CommitResult.Committed,library().setFavorite(a.workId.value,true))
        reopen();assertEquals(listOf("library-a","library-b"),library().favorites().map { it.workId })
    }
    @Test fun action_io_failure_and_cancellation_roll_back_without_false_success() = runBlocking<Unit> {
        play();library().setFavorite(a.workId.value,true)
        val failing=library { throw java.io.IOException("synthetic") }
        assertEquals(CommitResult.Failed("storage_io"),failing.deleteWatchHistory(a.workId.value))
        assertEquals(CommitResult.Failed("storage_io"),failing.setFavorite(a.workId.value,false))
        val cancelled=library { throw CancellationException("synthetic") }
        try { cancelled.removeFromContinue(a.workId.value);fail("cancellation swallowed") } catch (_:CancellationException) { }
        reopen();assertEquals(1,library().favorites().size);assertEquals(1,library().recentHistory().size)
        assertEquals(1,library().continueWatching().size)
    }
    @Test fun brief_play_keeps_resume_and_completed_latest_does_not_imply_series_finality() = runBlocking<Unit> {
        val prior=play();val next=a.copy(episodeId=EpisodeId("latest"));store().beginSession(next,"brief");library().confirmPlayback("brief")
        assertEquals(prior,library().continueWatching().single().snapshot)
        val end=PlaybackSnapshot(next.workId,next.episodeId,next.variantId,"brief",0,1000,1000,WatchState.COMPLETED,-1)
        store().commit(end);assertTrue(library().continueWatching().isEmpty())
        assertTrue(library().recentHistory().single().completedEpisode)
    }
    @Test fun cache_eviction_keeps_saved_offline_labels_and_opaque_identity() = runBlocking<Unit> {
        library().rememberCard(a.workId.value,"保留标题","不可用来源");library().setFavorite(a.workId.value,true);play()
        database.metadataCache().upsert(com.ichichuang.cinema.data.local.entity.WorkMetadataCacheEntity(a.workId.value,"synthetic-evictable-cache"))
        assertEquals(1,database.metadataCache().evictAll());reopen()
        assertEquals("保留标题",library().favorites().single().title)
        assertEquals("不可用来源",library().recentHistory().single().sourceLabel)
        assertEquals("library-a",library().continueWatching().single().workId)
    }
    @Test fun concurrent_idempotent_favorite_taps_keep_one_committed_relation() = runBlocking<Unit> {
        coroutineScope { List(8) { async { library().setFavorite(a.workId.value,true) } }.awaitAll()
            .forEach { assertEquals(CommitResult.Committed,it) } }
        reopen();assertEquals(listOf("library-a"),library().favorites().map { it.workId })
        assertEquals(1,database.userRecords().favoriteCount())
    }
    @Test fun history_order_ignores_capture_time_rollback_and_duplicate_confirmation() = runBlocking<Unit> {
        play(a,"first",capturedAt=10000);play(b,"second",capturedAt=-10000)
        library().confirmPlayback("first")
        reopen();assertEquals(listOf("library-b","library-a"),library().recentHistory().map { it.workId })
    }
    @Test fun actual_legacy_staging_retry_preserves_delete_tombstone_and_original_records() = runBlocking<Unit> {
        play();val snapshot=seedLegacy()
        val importer=com.ichichuang.cinema.data.local.migration.LegacySnapshotImporter(database)
        importer.preserve(snapshot);val before=database.legacyImports().records(1,snapshot.contentDigest)
        library().deleteWatchHistory(a.workId.value);importer.preserve(snapshot)
        reopen();assertTrue(library().recentHistory().isEmpty());assertNull(store().loadResume(a.workId))
        assertEquals(before,database.legacyImports().records(1,snapshot.contentDigest))
        assertEquals(CommitResult.RejectedStale,library().confirmPlayback("session-a"))
    }
    @Test fun source_disable_and_three_member_split_preserve_personal_rows_and_staging() = runBlocking<Unit> {
        val sources=listOf(SourceId("personal-source-a"),SourceId("personal-source-b"),SourceId("personal-source-c"))
        val keys=sources.map { SourceItemKey(it,SourceItemId("17")) }
        val identifier=com.ichichuang.cinema.data.catalog.CatalogIdentifier("synthetic.namespace","same-work")
        val proofs=com.ichichuang.cinema.data.catalog.CatalogIdentityProofs(mapOf(
            "AB" to com.ichichuang.cinema.data.catalog.VerifiedCatalogAssociation(keys[0],keys[1],identifier),
            "BC" to com.ichichuang.cinema.data.catalog.VerifiedCatalogAssociation(keys[1],keys[2],identifier)))
        var ids=0
        val catalog=com.ichichuang.cinema.data.catalog.CandidateCatalogStore(database,proofs) { "opaque-personal-${++ids}" }
        sources.forEachIndexed { index,source ->
            catalog.registerSource(source,"fixture:personal-$index")
            catalog.ingest("capture-personal-$index",com.ichichuang.cinema.data.catalog.MaccmsCatalogNormalizer.normalize(source,
                kotlinx.serialization.json.buildJsonObject {
                    put("vod_id",kotlinx.serialization.json.JsonPrimitive("17"))
                    put("vod_name",kotlinx.serialization.json.JsonPrimitive("来源原名"))
                    put("vod_year",kotlinx.serialization.json.JsonPrimitive("1994"))
                },123))
        }
        val original=catalog.workFor(keys[0])!!
        val target=a.copy(workId=original)
        play(target,"identity-play");library().rememberCard(original.value,"来源原名","原始来源")
        library().setFavorite(original.value,true)
        val staged=seedLegacy();com.ichichuang.cinema.data.local.migration.LegacySnapshotImporter(database).preserve(staged)
        val stagedBefore=database.legacyImports().records(1,staged.contentDigest)
        val favoritesBefore=library().favorites();val historyBefore=library().recentHistory();val continueBefore=library().continueWatching()
        assertEquals(com.ichichuang.cinema.data.catalog.AssociationDecision.LINKED,catalog.associate(keys[0],keys[1],"AB",1))
        assertEquals(com.ichichuang.cinema.data.catalog.AssociationDecision.LINKED,catalog.associate(keys[1],keys[2],"BC",2))
        assertEquals(catalog.groupFor(keys[0]),catalog.groupFor(keys[2]))
        catalog.split(keys[1],"错误关联修正",3);catalog.unavailable(sources[0])
        assertNotEquals(catalog.groupFor(keys[0]),catalog.groupFor(keys[1]))
        assertEquals(false,database.catalogIdentity().source(com.ichichuang.cinema.data.local.codec.Utf16KeyConverter().encode(sources[0].value))!!.available)
        reopen();assertEquals(favoritesBefore,library().favorites());assertEquals(historyBefore,library().recentHistory())
        assertEquals(continueBefore,library().continueWatching());assertEquals(stagedBefore,database.legacyImports().records(1,staged.contentDigest))
        assertEquals(original.value,library().favorites().single().workId)
    }
    @Test fun undo_is_bound_to_committed_unfavorite_and_rejects_intervening_action() = runBlocking<Unit> {
        library().setFavorite(a.workId.value,true)
        val removed=library().unfavorite(a.workId.value)
        assertEquals(CommitResult.Committed,removed.result);assertNotNull(removed.undoRevision)
        val broken=library { throw java.io.IOException("synthetic undo fault") }
        assertEquals(CommitResult.Failed("storage_io"),broken.undoUnfavorite(a.workId.value,removed.undoRevision!!))
        assertTrue(library().favorites().isEmpty())
        assertEquals(CommitResult.Committed,library().undoUnfavorite(a.workId.value,removed.undoRevision!!))
        assertEquals(CommitResult.Committed,library().undoUnfavorite(a.workId.value,removed.undoRevision!!))
        library().setFavorite(a.workId.value,false)
        assertEquals(CommitResult.RejectedStale,library().undoUnfavorite(a.workId.value,removed.undoRevision!!))
        reopen();assertTrue(library().favorites().isEmpty())
    }
    @Test fun lost_acknowledgement_after_commit_retries_without_duplicate_or_reorder() = runBlocking<Unit> {
        val lost=RoomPersonalLibraryRepository(database,afterCommit={throw java.io.IOException("synthetic acknowledgement")})
        assertEquals(CommitResult.Failed("storage_io"),lost.setFavorite(a.workId.value,true))
        library().setFavorite(b.workId.value,true)
        assertEquals(CommitResult.Committed,library().setFavorite(a.workId.value,true))
        reopen();assertEquals(listOf("library-b","library-a"),library().favorites().map { it.workId })
        store().beginSession(a,"history-ack")
        val lostHistory=RoomPersonalLibraryRepository(database,afterCommit={throw java.io.IOException("synthetic acknowledgement")})
        assertEquals(CommitResult.Failed("storage_io"),lostHistory.confirmPlayback("history-ack"))
        assertEquals(CommitResult.Committed,library().confirmPlayback("history-ack"));assertEquals(1,library().recentHistory().size)
    }
    @Test fun retried_delete_and_hide_actions_cannot_remove_newly_confirmed_playback() = runBlocking<Unit> {
        play();val deletion=library().prepareAction(a.workId.value)
        assertEquals(CommitResult.Committed,library().deleteWatchHistory(deletion))
        val newer=play(a,"new-after-delete")
        assertEquals(CommitResult.Committed,library().deleteWatchHistory(deletion))
        assertEquals(newer,store().loadResume(a.workId));assertEquals(1,library().recentHistory().size)
        val hiding=library().prepareAction(a.workId.value)
        library().removeFromContinue(hiding)
        val newest=play(a,"new-after-hide")
        assertEquals(CommitResult.Committed,library().removeFromContinue(hiding))
        reopen();assertEquals(newest,library().continueWatching().single().snapshot)
    }
    @Test fun unfavorite_lost_ack_retry_recovers_same_undo_receipt_without_stealing_new_intent() = runBlocking<Unit> {
        library().setFavorite(a.workId.value,true)
        val action=library().prepareUnfavorite(a.workId.value)
        val lost=RoomPersonalLibraryRepository(database,afterCommit={throw java.io.IOException("synthetic acknowledgement")})
        assertEquals(CommitResult.Failed("storage_io"),lost.unfavorite(action).result)
        assertTrue(library().favorites().isEmpty())
        val receipt=library().unfavorite(action)
        assertEquals(CommitResult.Committed,receipt.result);assertNotNull(receipt.undoRevision)
        assertEquals(receipt,library().unfavorite(action))
        assertEquals(CommitResult.Committed,library().undoUnfavorite(a.workId.value,receipt.undoRevision!!))
        library().setFavorite(a.workId.value,false)
        assertEquals(CommitResult.RejectedStale,library().unfavorite(action).result)
        reopen();assertTrue(library().favorites().isEmpty())
    }
    @Test fun separate_episodes_and_variants_keep_distinct_positions() = runBlocking<Unit> {
        play();play(a.copy(episodeId=EpisodeId("episode-2")),"second",20000)
        play(a.copy(variantId=VariantId("cut-b")),"variant",30000)
        assertEquals(9000L,database.playbackSnapshots().find("library-a","episode-1","cut-a")!!.snapshot().positionMs)
        assertEquals(20000L,database.playbackSnapshots().find("library-a","episode-2","cut-a")!!.snapshot().positionMs)
        assertEquals(30000L,database.playbackSnapshots().find("library-a","episode-1","cut-b")!!.snapshot().positionMs)
    }
}
