package com.ichichuang.cinema.data.local

import android.database.sqlite.SQLiteFullException
import com.ichichuang.cinema.data.local.migration.LegacySnapshotImporter
import com.ichichuang.cinema.data.userdata.*
import com.ichichuang.cinema.domain.model.catalog.*
import com.ichichuang.cinema.domain.model.userdata.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PlaybackCheckpointNativeTest : NativeStorageFixture() {
    private val target = PlaybackTarget(WorkId("work"), EpisodeId("one"), VariantId("version"))
    private fun snapshot(session: String = "z-old", sequence: Long = 0, position: Long = 600_000,
        t: PlaybackTarget = target, state: WatchState = WatchState.IN_PROGRESS) = PlaybackSnapshot(
        t.workId, t.episodeId, t.variantId, session, sequence, position, null, state, -123)
    private fun store() = RoomPlaybackProgressStore(database)
    private suspend fun start(id: String = "z-old", t: PlaybackTarget = target) {
        assertEquals(CommitResult.Committed, store().beginSession(t, id))
    }
    private suspend fun row(t: PlaybackTarget = target) = database.playbackSnapshots().find(
        t.workId.value, t.episodeId?.value ?: "", t.variantId?.value ?: "")?.snapshot()

    @Test fun durable_commit_survives_reopen_with_unknown_duration_and_negative_timestamp() = runBlocking<Unit> {
        start(); val value = snapshot().copy(durationMs = 100)
        assertEquals(CommitResult.Committed, store().commit(value))
        reopen(); assertEquals(value, row()); assertEquals(value, store().loadResume(target.workId))
    }

    @Test fun unallocated_session_is_rejected_without_creating_progress() = runBlocking<Unit> {
        assertEquals(CommitResult.RejectedStale, store().commit(snapshot()))
        reopen(); assertNull(row()); assertEquals(0, database.userRecords().referenceCount())
    }

    @Test fun old_sequence_cannot_overwrite_newer_same_episode() = runBlocking<Unit> {
        start(); val newest = snapshot(sequence = 5, position = 10)
        assertEquals(CommitResult.Committed, store().commit(newest))
        assertEquals(CommitResult.RejectedStale, store().commit(snapshot(sequence = 4, position = 9_999)))
        reopen(); assertEquals(newest, row())
    }

    @Test fun duplicate_same_snapshot_is_idempotent_after_reopen() = runBlocking<Unit> {
        start(); val value = snapshot()
        assertEquals(CommitResult.Committed, store().commit(value))
        reopen(); assertEquals(CommitResult.Committed, store().commit(value))
        assertEquals(1, database.playbackSnapshots().count()); assertEquals(value, row())
    }

    @Test fun same_sequence_different_payload_is_rejected() = runBlocking<Unit> {
        start(); val value = snapshot()
        store().commit(value)
        assertEquals(CommitResult.RejectedStale, store().commit(value.copy(positionMs = 1)))
        assertEquals(CommitResult.RejectedStale, store().commit(value.copy(capturedAt = 456)))
        reopen(); assertEquals(value, row())
    }

    @Test fun old_session_cannot_overwrite_newer_session_even_with_larger_position_or_timestamp() = runBlocking<Unit> {
        start(); store().commit(snapshot())
        start("a-new"); val newest = snapshot("a-new", position = 1).copy(capturedAt = -999)
        store().commit(newest); reopen()
        assertEquals(CommitResult.RejectedStale, store().commit(snapshot(sequence = 99).copy(capturedAt = 999)))
        assertEquals(CommitResult.RejectedStale, store().commit(snapshot()))
        assertEquals(newest, row())
    }

    @Test fun old_session_cannot_overwrite_new_episode_resume_pointer() = runBlocking<Unit> {
        start(); store().commit(snapshot())
        val next = target.copy(episodeId = EpisodeId("two"))
        start("a-new", next); val newest = snapshot("a-new", t = next, position = 5)
        store().commit(newest)
        val oldFinal = snapshot(sequence = 1, position = 700_000)
        assertEquals(CommitResult.Committed, store().commit(oldFinal))
        reopen(); assertEquals(oldFinal, row()); assertEquals(newest, row(next))
        assertEquals(newest, store().loadResume(target.workId))
    }

    @Test fun unqualified_selection_does_not_erase_previous_resume_or_allow_old_pointer_update() = runBlocking<Unit> {
        start(); val value = snapshot(); store().commit(value)
        start("a-new", target.copy(episodeId = EpisodeId("two")))
        store().commit(snapshot(sequence = 1, position = 700_000))
        reopen(); assertEquals(value, store().loadResume(target.workId))
        assertEquals(700_000L, row()!!.positionMs)
    }

    @Test fun preload_does_not_mark_watched_or_promote_pointer() = runBlocking<Unit> {
        start(); store().commit(snapshot())
        val next = target.copy(episodeId = EpisodeId("two")); start("a-new", next)
        store().commit(snapshot("a-new", t = next, position = 0, state = WatchState.NOT_STARTED))
        reopen(); assertEquals(target.episodeId, store().loadResume(target.workId)!!.episodeId)
        assertEquals(WatchState.NOT_STARTED, row(next)!!.watchState)
    }

    @Test fun failed_resume_write_rolls_back_episode_and_session_sequence() = runBlocking<Unit> {
        start(); val value = snapshot(); store().commit(value)
        withContext(Dispatchers.IO) { database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_resume BEFORE INSERT ON playback_resume BEGIN SELECT RAISE(ABORT, 'synthetic fault'); END") }
        assertTrue(store().commit(snapshot(sequence = 1, position = 7)) is CommitResult.Failed)
        reopen(); assertEquals(value, row()); assertEquals(value, store().loadResume(target.workId))
        withContext(Dispatchers.IO) { database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_resume") }
        assertEquals(CommitResult.Committed, store().commit(snapshot(sequence = 1, position = 7)))
    }

    @Test fun cancellation_inside_transaction_propagates_and_rolls_back() = runBlocking<Unit> {
        start()
        val fault = RoomPlaybackProgressStore(database, beforeCommit = { throw CancellationException("synthetic") })
        assertThrows(CancellationException::class.java) { runBlocking { fault.commit(snapshot()) } }
        reopen(); assertNull(row()); assertNull(store().loadResume(target.workId))
        assertEquals(CommitResult.Committed, store().commit(snapshot()))
    }

    @Test fun acknowledgement_loss_retries_identical_durable_snapshot() = runBlocking<Unit> {
        start()
        val fault = RoomPlaybackProgressStore(database, afterCommit = { throw IOException("synthetic") })
        assertEquals(CommitResult.Failed("storage_io"), fault.commit(snapshot()))
        reopen(); assertEquals(snapshot(), row())
        assertEquals(CommitResult.Committed, store().commit(snapshot()))
    }

    @Test fun cancellation_after_commit_does_not_erase_durable_snapshot() = runBlocking<Unit> {
        start()
        val fault = RoomPlaybackProgressStore(database, afterCommit = { throw CancellationException("synthetic") })
        assertThrows(CancellationException::class.java) { runBlocking { fault.commit(snapshot()) } }
        reopen(); assertEquals(snapshot(), row())
        assertEquals(CommitResult.Committed, store().commit(snapshot()))
    }

    @Test fun controlled_concurrent_old_completion_is_stale_after_new_session_commit() = runBlocking<Unit> {
        start(); val releaseOld = CompletableDeferred<Unit>()
        val old = async { releaseOld.await(); store().commit(snapshot(sequence = 99)) }
        start("a-new"); val newest = snapshot("a-new", position = 2)
        assertEquals(CommitResult.Committed, async(Dispatchers.IO) { store().commit(newest) }.await())
        releaseOld.complete(Unit); assertEquals(CommitResult.RejectedStale, old.await())
        reopen(); assertEquals(newest, row())
    }

    @Test fun session_target_binding_rejects_identity_change() = runBlocking<Unit> {
        start()
        val other = target.copy(variantId = VariantId("other-timebase"))
        assertEquals(CommitResult.RejectedStale, store().beginSession(other, "z-old"))
        assertEquals(CommitResult.RejectedStale, store().commit(snapshot(t = other)))
        assertNull(row(other))
    }

    @Test fun session_generation_floor_survives_reopen_and_opaque_utf16_ids() = runBlocking<Unit> {
        start("\uD800"); store().commit(snapshot("\uD800")); reopen()
        start("\uD801"); val newest = snapshot("\uD801", position = 0)
        store().commit(newest); reopen()
        assertEquals(CommitResult.RejectedStale, store().commit(snapshot("\uD800", sequence = Long.MAX_VALUE)))
        assertEquals(newest, row())
    }

    @Test fun resume_after_database_reopen_is_paused() = runBlocking<Unit> {
        start(); store().commit(snapshot()); reopen()
        val s = store(); val recovery = s.loadResume(target.workId)!!
        val c = PlaybackCheckpointCoordinator(s, s::beginSession, PlaybackClock { 0 }, { 0 })
        assertEquals(CommitResult.Committed, c.select(target, "recreated", recovery))
        assertFalse(c.isPlaying())
    }

    @Test fun cache_eviction_keeps_progress_and_original_import_staging() = runBlocking<Unit> {
        val legacy = seedLegacy(); LegacySnapshotImporter(database).preserve(legacy)
        val before = database.legacyImports().recordCount()
        start(); store().commit(snapshot()); database.metadataCache().evictAll(); reopen()
        assertEquals(snapshot(), row()); assertEquals(snapshot(), store().loadResume(target.workId))
        assertEquals(before, database.legacyImports().recordCount())
    }

    @Test fun failed_session_allocation_rolls_back_reference() = runBlocking<Unit> {
        withContext(Dispatchers.IO) { database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_session BEFORE INSERT ON playback_sessions BEGIN SELECT RAISE(ABORT, 'synthetic'); END") }
        assertTrue(store().beginSession(target, "session") is CommitResult.Failed)
        reopen(); assertEquals(0, database.userRecords().referenceCount())
    }

    @Test fun selecting_new_session_blocks_old_same_episode_before_first_checkpoint() = runBlocking<Unit> {
        start(); val value = snapshot(); store().commit(value)
        start("new"); reopen()
        assertEquals(CommitResult.RejectedStale, store().commit(snapshot(sequence = 99, position = 9)))
        assertEquals(value, row()); assertEquals(value, store().loadResume(target.workId))
    }

    @Test fun unviewed_snapshot_cannot_downgrade_a_viewed_episode() = runBlocking<Unit> {
        start(); val value = snapshot(); store().commit(value); start("new")
        assertEquals(CommitResult.RejectedStale, store().commit(snapshot("new", position = 0, state = WatchState.NOT_STARTED)))
        reopen(); assertEquals(value, row()); assertEquals(value, store().loadResume(target.workId))
    }

    @Test fun sqlite_full_error_rolls_back_all_progress_and_allows_retry() = runBlocking<Unit> {
        start()
        val failing = RoomPlaybackProgressStore(database, beforeCommit = { throw SQLiteFullException("synthetic full") })
        assertEquals(CommitResult.Failed("storage_sql"), failing.commit(snapshot()))
        reopen(); assertNull(row()); assertNull(store().loadResume(target.workId))
        assertEquals(CommitResult.Committed, store().commit(snapshot()))
    }

    @Test fun separate_connections_allocate_distinct_durable_generations() = runBlocking<Unit> {
        val other = CinemaDatabaseFactory.open(context, databaseName)
        try {
            val calls = listOf(async(Dispatchers.IO) { store().beginSession(target, "one") },
                async(Dispatchers.IO) { RoomPlaybackProgressStore(other).beginSession(target, "two") })
            assertTrue(calls.awaitAll().all { it == CommitResult.Committed })
            val one = database.playbackSessions().session("one")!!.generation
            val two = database.playbackSessions().session("two")!!.generation
            assertNotEquals(one, two)
            reopen()
            val newId = if (one > two) "one" else "two"
            val oldId = if (one > two) "two" else "one"
            assertEquals(CommitResult.Committed, store().commit(snapshot(newId)))
            assertEquals(CommitResult.RejectedStale, store().commit(snapshot(oldId)))
        } finally { other.close() }
    }

    @Test fun failed_explicit_seek_keeps_real_room_switch_barrier_until_retry_commits() = runBlocking<Unit> {
        start(); store().commit(snapshot())
        val s = store(); val c = PlaybackCheckpointCoordinator(s, s::beginSession, PlaybackClock { 0 }, { -1 })
        c.select(target, "recreated", snapshot()); c.ready("recreated", 600_000, null)
        withContext(Dispatchers.IO) { database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_resume BEFORE INSERT ON playback_resume BEGIN SELECT RAISE(ABORT, 'synthetic fault'); END") }
        assertTrue(c.seek("recreated", 0, null) is CommitResult.Failed)
        val next = target.copy(episodeId = EpisodeId("two"))
        assertTrue(c.select(next, "next") is CommitResult.Failed)
        assertNull(database.playbackSessions().session("next"))
        withContext(Dispatchers.IO) { database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_resume") }
        assertEquals(CommitResult.Committed, c.select(next, "next"))
        reopen(); assertEquals(0L, row()!!.positionMs); assertEquals(0L, store().loadResume(target.workId)!!.positionMs)
    }

    @Test fun actual_job_cancellation_rolls_back_progress_and_sequence() = runBlocking<Unit> {
        start(); val entered = CompletableDeferred<Unit>(); val blocked = CompletableDeferred<Unit>()
        val slow = RoomPlaybackProgressStore(database, beforeCommit = { entered.complete(Unit); blocked.await() })
        val job = async { slow.commit(snapshot()) }; entered.await(); job.cancel()
        assertThrows(CancellationException::class.java) { runBlocking { job.await() } }
        job.join(); assertTrue(job.isCancelled)
        reopen(); assertNull(row()); assertNull(store().loadResume(target.workId))
        assertEquals(CommitResult.Committed, store().commit(snapshot()))
    }

    @Test fun queued_short_exit_during_slow_room_commit_preserves_previous_work_resume() = runBlocking<Unit> {
        start(); store().commit(snapshot())
        var elapsed = 0L
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var calls = 0
        val slow = RoomPlaybackProgressStore(database, beforeCommit = {
            if (calls++ == 0) { entered.complete(Unit); release.await() }
        })
        val c = PlaybackCheckpointCoordinator(slow, slow::beginSession, PlaybackClock { elapsed }, { -1 })
        val next = target.copy(episodeId = EpisodeId("two"))
        c.select(next, "next"); c.ready("next", 0, null); c.playing("next", true)
        elapsed = 100
        val seek = launch { c.seek("next", 0, null) }; entered.await()
        elapsed = 200; val stopped = async { c.boundary("next") }; yield()
        elapsed = 6_000; release.complete(Unit); seek.join()
        assertNull(stopped.await())
        reopen(); assertEquals(snapshot(), store().loadResume(target.workId))
        assertEquals(WatchState.NOT_STARTED, row(next)!!.watchState)
    }
}
