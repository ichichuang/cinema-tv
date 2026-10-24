package com.ichichuang.cinema.data.userdata

import com.ichichuang.cinema.domain.model.catalog.*
import com.ichichuang.cinema.domain.model.userdata.*
import com.ichichuang.cinema.domain.repository.PlaybackProgressStore
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PlaybackCheckpointTest {
    private var elapsed = 0L
    private val writes = mutableListOf<PlaybackSnapshot>()
    private var result: CommitResult = CommitResult.Committed
    private val target = PlaybackTarget(WorkId("work"), EpisodeId("one"), VariantId("version"))
    private val coordinator = PlaybackCheckpointCoordinator(
        object : PlaybackProgressStore {
            override suspend fun commit(snapshot: PlaybackSnapshot): CommitResult {
                writes += snapshot
                return result
            }
        }, { _, _ -> CommitResult.Committed }, PlaybackClock { elapsed }, { -123L })

    @Test fun seek_backwards_still_saves_on_clock() = runBlocking<Unit> {
        coordinator.select(target, "session")
        coordinator.ready("session", 1_200_000, 2_000_000)
        coordinator.playing("session", true)
        elapsed = 5_000
        coordinator.pulse("session", 1_205_000, 2_000_000)
        assertEquals(1, writes.size)
        assertEquals(1_205_000L, writes.single().positionMs)
        coordinator.seek("session", 600_000, 2_000_000)
        elapsed += 5_000
        coordinator.pulse("session", 605_000, 2_000_000)
        assertEquals(605_000L, writes.last().positionMs)
        assertEquals(3, writes.size)
        assertEquals(-123L, writes.last().capturedAt)
    }

    private fun resume(position: Long = 600_000) = PlaybackSnapshot(target.workId, target.episodeId,
        target.variantId, "previous", 999, position, 1_000_000, WatchState.IN_PROGRESS, -456)

    private suspend fun startAndQualify() {
        coordinator.select(target, "session")
        coordinator.ready("session", 100, 10_000)
        coordinator.playing("session", true)
        elapsed += 5_000
        coordinator.pulse("session", 5_100, 10_000)
    }

    @Test fun loading_zero_keeps_resume_point() = runBlocking<Unit> {
        coordinator.select(target, "session", resume())
        coordinator.loading("session")
        coordinator.pulse("session", 0, null)
        coordinator.boundary("session")
        assertTrue(writes.isEmpty())
        coordinator.ready("session", 0, null)
        coordinator.playing("session", true)
        elapsed = 5_000
        coordinator.pulse("session", 0, null)
        assertEquals(600_000L, writes.single().positionMs)
        assertEquals(1_000_000L, writes.single().durationMs)
    }

    @Test fun retry_preserves_last_valid_resume_point() = runBlocking<Unit> {
        startAndQualify()
        coordinator.loading("session")
        elapsed += 60_000
        coordinator.ready("session", 0, null)
        coordinator.boundary("session")
        assertEquals(5_100L, writes.last().positionMs)
        assertEquals(10_000L, writes.last().durationMs)
    }

    @Test fun buffering_and_pause_do_not_count_as_active_playback() = runBlocking<Unit> {
        coordinator.select(target, "session")
        coordinator.ready("session", 20, null)
        coordinator.playing("session", true)
        elapsed = 4_999
        coordinator.playing("session", false)
        elapsed += 60_000
        coordinator.pulse("session", 0, null)
        assertTrue(writes.isEmpty())
        coordinator.playing("session", true)
        elapsed++
        coordinator.pulse("session", 5_020, null)
        assertEquals(1, writes.size)
        assertNull(writes.single().durationMs)
    }

    @Test fun explicit_seek_to_zero_and_rewatch_are_valid() = runBlocking<Unit> {
        coordinator.select(target, "session", resume())
        coordinator.ready("session", 600_000, null)
        coordinator.seek("session", 0, null)
        assertEquals(0L, writes.single().positionMs)
        assertEquals(WatchState.IN_PROGRESS, writes.single().watchState)
    }

    @Test fun unknown_duration_does_not_clamp_valid_position() = runBlocking<Unit> {
        startAndQualify()
        coordinator.seek("session", 100_000, 100)
        assertEquals(100_000L, writes.last().positionMs)
        assertEquals(100L, writes.last().durationMs)
        coordinator.seek("session", 100_001, null)
        assertEquals(100_001L, writes.last().positionMs)
        assertEquals(100L, writes.last().durationMs)
    }

    @Test fun save_before_episode_switch() = runBlocking<Unit> {
        startAndQualify()
        elapsed += 500
        coordinator.pulse("session", 5_600, 10_000)
        val next = target.copy(episodeId = EpisodeId("two"))
        assertEquals(CommitResult.Committed, coordinator.select(next, "next"))
        assertEquals(target.episodeId, writes.last().episodeId)
        assertEquals(5_600L, writes.last().positionMs)
        coordinator.pulse("session", 999, 999)
        assertEquals(2, writes.size)
    }

    @Test fun failed_switch_keeps_old_context_and_returns_failure() = runBlocking<Unit> {
        startAndQualify()
        result = CommitResult.Failed("synthetic_io")
        assertEquals(result, coordinator.select(target.copy(episodeId = EpisodeId("two")), "next"))
        result = CommitResult.Committed
        coordinator.seek("session", 77, null)
        assertEquals(target.episodeId, writes.last().episodeId)
        assertEquals(77L, writes.last().positionMs)
    }

    @Test fun preload_does_not_mark_watched() = runBlocking<Unit> {
        coordinator.select(target, "session")
        coordinator.ready("preload", 0, 100)
        coordinator.playing("preload", true)
        elapsed = 10_000
        coordinator.pulse("preload", 50, 100)
        coordinator.boundary("preload")
        coordinator.boundary("session")
        assertTrue(writes.isEmpty())
    }

    @Test fun accidental_exit_before_five_seconds_does_not_promote() = runBlocking<Unit> {
        coordinator.select(target, "session")
        coordinator.ready("session", 0, null)
        coordinator.playing("session", true)
        elapsed = 4_999
        coordinator.pulse("session", 4_999, null)
        coordinator.boundary("session")
        assertTrue(writes.isEmpty())
    }

    @Test fun switch_source_keeps_episode_only_when_identity_matches() = runBlocking<Unit> {
        coordinator.select(target, "session", resume())
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            coordinator.select(target.copy(variantId = VariantId("different-timebase")), "next", resume())
        } }
        coordinator.select(target.copy(variantId = VariantId("different-timebase")), "next")
        coordinator.ready("next", 0, null)
        coordinator.seek("next", 0, null)
        assertEquals(0L, writes.last().positionMs)
        assertEquals(WatchState.NOT_STARTED, writes.last().watchState)
    }

    @Test fun completion_is_per_episode_and_does_not_zero_progress() = runBlocking<Unit> {
        startAndQualify()
        coordinator.ended("session", 10_000, 10_000)
        assertEquals(WatchState.COMPLETED, writes.last().watchState)
        assertEquals(10_000L, writes.last().positionMs)
        assertEquals(target.episodeId, writes.last().episodeId)
        assertFalse(coordinator.isPlaying())
    }

    @Test fun ended_without_actual_playback_does_not_invent_viewing() = runBlocking<Unit> {
        coordinator.select(target, "session")
        coordinator.ready("session", 100, 100)
        coordinator.ended("session", 100, 100)
        assertTrue(writes.isEmpty())
    }

    @Test fun recovery_starts_paused_and_rejects_negative_observations() = runBlocking<Unit> {
        coordinator.select(target, "session", resume())
        assertFalse(coordinator.isPlaying())
        assertThrows(IllegalArgumentException::class.java) { runBlocking { coordinator.seek("session", -1, null) } }
    }

    @Test fun cancelled_commit_propagates() = runBlocking<Unit> {
        val cancelStore = object : PlaybackProgressStore {
            override suspend fun commit(snapshot: PlaybackSnapshot): CommitResult = throw CancellationException("synthetic")
        }
        val c = PlaybackCheckpointCoordinator(cancelStore, { _, _ -> CommitResult.Committed }, PlaybackClock { elapsed }, { 0 })
        c.select(target, "session"); c.ready("session", 5, null)
        assertThrows(CancellationException::class.java) { runBlocking { c.seek("session", 0, null) } }
    }

    @Test fun switch_waits_for_inflight_immutable_old_episode_snapshot() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val allocated = mutableListOf<String>(); val captured = mutableListOf<PlaybackSnapshot>()
        val s = object : PlaybackProgressStore {
            override suspend fun commit(snapshot: PlaybackSnapshot): CommitResult {
                captured += snapshot
                if (captured.size == 1) { entered.complete(Unit); release.await() }
                return CommitResult.Committed
            }
        }
        val c = PlaybackCheckpointCoordinator(s, { _, id -> allocated += id; CommitResult.Committed }, PlaybackClock { elapsed }, { -1 })
        c.select(target, "old"); c.ready("old", 100, null); c.playing("old", true)
        elapsed = 5_000
        val checkpoint = launch { c.pulse("old", 5_100, null) }
        entered.await()
        val switched = async { c.select(target.copy(episodeId = EpisodeId("two")), "new") }
        yield(); assertEquals(listOf("old"), allocated); assertFalse(switched.isCompleted)
        release.complete(Unit); checkpoint.join(); assertEquals(CommitResult.Committed, switched.await())
        assertTrue(captured.all { it.episodeId == target.episodeId && it.sessionId == "old" })
        assertEquals(5_100L, captured.first().positionMs)
    }

    @Test fun a_genuine_short_episode_can_complete_without_inventing_an_entire_series() = runBlocking<Unit> {
        coordinator.select(target, "session"); coordinator.ready("session", 0, 100)
        coordinator.playing("session", true); elapsed = 100
        coordinator.ended("session", 100, 100)
        assertEquals(WatchState.COMPLETED, writes.single().watchState)
        assertEquals(target.episodeId, writes.single().episodeId)
        assertEquals(100L, writes.single().positionMs)
    }

    @Test fun failed_checkpoint_retries_with_a_higher_sequence_and_keeps_valid_state() = runBlocking<Unit> {
        result = CommitResult.Failed("synthetic")
        startAndQualify()
        result = CommitResult.Committed
        elapsed++
        coordinator.pulse("session", 5_101, null)
        assertEquals(listOf(0L, 1L), writes.map { it.operationSequence })
        assertEquals(10_000L, writes.last().durationMs)
    }

    @Test fun failed_explicit_seek_before_five_seconds_is_retried_by_boundary() = runBlocking<Unit> {
        coordinator.select(target, "session", resume()); coordinator.ready("session", 600_000, null)
        result = CommitResult.Failed("synthetic")
        assertEquals(result, coordinator.seek("session", 0, null))
        assertEquals(result, coordinator.boundary("session"))
        result = CommitResult.Committed
        assertEquals(result, coordinator.boundary("session"))
        assertEquals(0L, writes.last().positionMs)
        assertEquals(listOf(0L, 1L, 2L), writes.map { it.operationSequence })
    }

    @Test fun failed_explicit_seek_before_five_seconds_blocks_episode_switch() = runBlocking<Unit> {
        coordinator.select(target, "session", resume()); coordinator.ready("session", 600_000, null)
        result = CommitResult.Failed("synthetic"); coordinator.seek("session", 0, null)
        assertEquals(result, coordinator.select(target.copy(episodeId = EpisodeId("two")), "next"))
        result = CommitResult.Committed
        assertEquals(result, coordinator.select(target.copy(episodeId = EpisodeId("two")), "next"))
        assertEquals(0L, writes.last().positionMs); assertEquals(target.episodeId, writes.last().episodeId)
    }

    @Test fun failed_short_episode_completion_blocks_switch_until_saved() = runBlocking<Unit> {
        coordinator.select(target, "session"); coordinator.ready("session", 0, 100)
        coordinator.playing("session", true); elapsed = 100
        result = CommitResult.Failed("synthetic"); coordinator.ended("session", 100, 100)
        assertEquals(result, coordinator.select(target.copy(episodeId = EpisodeId("two")), "next"))
        result = CommitResult.Committed
        assertEquals(result, coordinator.select(target.copy(episodeId = EpisodeId("two")), "next"))
        assertEquals(WatchState.COMPLETED, writes.last().watchState)
        assertEquals(100L, writes.last().positionMs)
    }

    @Test fun restored_completed_episode_becomes_in_progress_on_genuine_rewatch_without_seek() = runBlocking<Unit> {
        coordinator.select(target, "session", resume().copy(watchState = WatchState.COMPLETED))
        coordinator.ready("session", 600_000, null)
        coordinator.boundary("session"); assertTrue(writes.isEmpty())
        coordinator.playing("session", true); elapsed = 5_000
        coordinator.pulse("session", 605_000, null)
        assertEquals(WatchState.IN_PROGRESS, writes.single().watchState)
        coordinator.ended("session", 1_000_000, 1_000_000); coordinator.boundary("session")
        assertEquals(WatchState.COMPLETED, writes.last().watchState)
    }

    @Test fun queued_short_pause_behind_slow_commit_does_not_count_mutex_wait_as_viewing() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val captured = mutableListOf<PlaybackSnapshot>()
        val slow = object : PlaybackProgressStore {
            override suspend fun commit(snapshot: PlaybackSnapshot): CommitResult {
                captured += snapshot
                if (captured.size == 1) { entered.complete(Unit); release.await() }
                return CommitResult.Committed
            }
        }
        val c = PlaybackCheckpointCoordinator(slow, { _, _ -> CommitResult.Committed }, PlaybackClock { elapsed }, { -1 })
        c.select(target, "session"); c.ready("session", 0, null); c.playing("session", true)
        elapsed = 100
        val seek = launch { c.seek("session", 0, null) }; entered.await()
        elapsed = 200
        val stopped = async { c.boundary("session") }; yield()
        assertFalse(stopped.isCompleted)
        elapsed = 6_000; release.complete(Unit); seek.join()
        assertNull(stopped.await())
        assertTrue(captured.all { it.watchState == WatchState.NOT_STARTED })
        assertFalse(c.isPlaying())
    }

    @Test fun actual_continuous_playback_across_slow_commit_still_counts() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val captured = mutableListOf<PlaybackSnapshot>()
        val slow = object : PlaybackProgressStore {
            override suspend fun commit(snapshot: PlaybackSnapshot): CommitResult {
                captured += snapshot
                if (captured.size == 1) { entered.complete(Unit); release.await() }
                return CommitResult.Committed
            }
        }
        val c = PlaybackCheckpointCoordinator(slow, { _, _ -> CommitResult.Committed }, PlaybackClock { elapsed }, { -1 })
        c.select(target, "session"); c.ready("session", 0, null); c.playing("session", true)
        elapsed = 100; val seek = launch { c.seek("session", 100, null) }; entered.await()
        elapsed = 6_000; release.complete(Unit); seek.join()
        c.pulse("session", 6_000, null)
        assertEquals(WatchState.IN_PROGRESS, captured.last().watchState)
        assertEquals(6_000L, captured.last().positionMs)
    }

    @Test fun cancelled_explicit_checkpoint_keeps_retry_intent_before_five_seconds() = runBlocking<Unit> {
        var cancel = true
        val s = object : PlaybackProgressStore {
            override suspend fun commit(snapshot: PlaybackSnapshot): CommitResult {
                if (cancel) throw CancellationException("synthetic")
                writes += snapshot; return CommitResult.Committed
            }
        }
        val c = PlaybackCheckpointCoordinator(s, { _, _ -> CommitResult.Committed }, PlaybackClock { elapsed }, { -1 })
        c.select(target, "session", resume()); c.ready("session", 600_000, null)
        assertThrows(CancellationException::class.java) { runBlocking { c.seek("session", 0, null) } }
        cancel = false
        assertEquals(CommitResult.Committed, c.boundary("session"))
        assertEquals(0L, writes.single().positionMs)
        assertEquals(1L, writes.single().operationSequence)
    }
}
