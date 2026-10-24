package com.ichichuang.cinema.feature.player

import com.ichichuang.cinema.data.userdata.*
import com.ichichuang.cinema.domain.model.catalog.*
import com.ichichuang.cinema.domain.model.userdata.*
import com.ichichuang.cinema.domain.repository.PlaybackProgressStore
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PlaybackRuntimeBridgeTest {
    private var time = 0L
    private val target = PlaybackTarget(WorkId("fixture-work"), EpisodeId("episode-a"), VariantId("cut-a"))
    private val writes = mutableListOf<PlaybackSnapshot>()
    private var outcome: CommitResult = CommitResult.Committed
    private val allocations = mutableListOf<String>()
    private val store = object : PlaybackProgressStore {
        override suspend fun commit(snapshot: PlaybackSnapshot): CommitResult { writes += snapshot; return outcome }
    }
    private fun bridge(s: PlaybackProgressStore = store) = PlaybackRuntimeBridge(s, { _, id ->
        allocations += id; CommitResult.Committed
    }, PlaybackClock { time }, { -9 })
    private fun sample(id: String = "old", pos: Long = 1_000, playing: Boolean = true,
        state: RuntimeState = RuntimeState.READY, intent: RuntimeIntent = RuntimeIntent.SAMPLE,
        duration: Long = -9223372036854775807L) = RuntimeObservation(id, time, state, playing, pos, duration, intent)
    private suspend fun start(b: PlaybackRuntimeBridge) {
        assertEquals(CommitResult.Committed, b.select(target, "old")); b.accept(sample())
    }
    private suspend fun qualify(b: PlaybackRuntimeBridge) { start(b); time = 5_000; b.accept(sample(pos = 6_000)) }

    @Test fun probe_off_keeps_legacy_writer_even_with_marker_or_old_files() {
        assertEquals(PlaybackRuntimeMode.LEGACY, PlaybackProbeGate.select(false, true, true, true))
    }
    @Test fun probe_requires_disposable_authorization() {
        assertThrows(IllegalStateException::class.java) { PlaybackProbeGate.select(true, true, false, false) }
    }
    @Test fun existing_debug_legacy_records_block_probe() {
        assertThrows(IllegalStateException::class.java) { PlaybackProbeGate.select(true, true, true, true) }
    }
    @Test fun release_runtime_refuses_probe() {
        assertThrows(IllegalStateException::class.java) { PlaybackProbeGate.select(true, false, true, false) }
    }
    @Test fun authorized_clean_debug_selects_exclusive_room_mode() {
        assertEquals(PlaybackRuntimeMode.ROOM_PROBE, PlaybackProbeGate.select(true, true, true, false))
    }
    @Test fun actual_playing_seconds_advance_checkpoint_clock_after_backward_seek() = runBlocking<Unit> {
        val b = bridge(); start(b); time = 5_000; b.accept(sample(pos = 1_200_000))
        b.accept(sample(pos = 600_000, intent = RuntimeIntent.SEEK))
        time = 10_000; b.accept(sample(pos = 605_000))
        assertEquals(listOf(1_200_000L, 600_000L, 605_000L), writes.map { it.positionMs })
    }
    @Test fun buffering_false_is_not_user_pause_or_a_zero_checkpoint() = runBlocking<Unit> {
        val b = bridge(); qualify(b); time = 5_200
        b.accept(sample(pos = 0, playing = false, state = RuntimeState.BUFFERING)); time = 50_000
        b.accept(sample(pos = 0, playing = false, state = RuntimeState.IDLE))
        assertEquals(1, writes.size); assertEquals(6_000L, b.positionMs)
        b.accept(sample(pos = 6_000)); time = 50_100
        b.accept(sample(pos = 6_100, playing = false, intent = RuntimeIntent.BOUNDARY))
        assertEquals(6_100L, writes.last().positionMs)
    }
    @Test fun unknown_duration_does_not_persist_zero_or_clamp_position() = runBlocking<Unit> {
        val b = bridge(); qualify(b); assertNull(writes.single().durationMs)
        b.accept(sample(pos = 9_000, intent = RuntimeIntent.SEEK, duration = 100))
        assertEquals(9_000L, writes.last().positionMs); assertEquals(100L, writes.last().durationMs)
    }
    @Test fun retry_preserves_last_valid_nonzero_position() = runBlocking<Unit> {
        val b = bridge(); qualify(b)
        b.accept(sample(pos = 0, playing = false, state = RuntimeState.IDLE))
        assertEquals(6_000L, b.retry("old", time))
        b.accept(sample(pos = 0, playing = false)); b.accept(sample(pos = 0, playing = false, intent = RuntimeIntent.BOUNDARY))
        assertEquals(6_000L, writes.last().positionMs)
    }
    @Test fun explicit_from_start_is_not_suppressed_and_rewatch_is_per_episode() = runBlocking<Unit> {
        val b = bridge(); qualify(b); time++
        b.accept(sample(pos = 12_000, playing = false, state = RuntimeState.ENDED))
        assertEquals(WatchState.COMPLETED, writes.last().watchState)
        b.accept(sample(pos = 0, playing = false, intent = RuntimeIntent.SEEK))
        assertEquals(0L, writes.last().positionMs); assertEquals(WatchState.IN_PROGRESS, writes.last().watchState)
        assertTrue(writes.all { it.episodeId == EpisodeId("episode-a") })
    }
    @Test fun switch_waits_for_old_episode_checkpoint_and_failure_keeps_context() = runBlocking<Unit> {
        val b = bridge(); qualify(b); outcome = CommitResult.Failed("io")
        assertEquals(outcome, b.select(target.copy(episodeId = EpisodeId("episode-b")), "new"))
        assertEquals(listOf("old"), allocations)
        outcome = CommitResult.Committed
        assertEquals(outcome, b.select(target.copy(episodeId = EpisodeId("episode-b")), "new"))
        assertTrue(writes.all { it.episodeId == EpisodeId("episode-a") })
    }
    @Test fun late_old_callback_cannot_steal_new_session_or_resume_target() = runBlocking<Unit> {
        val b = bridge(); qualify(b); b.select(target.copy(episodeId = EpisodeId("episode-b")), "new")
        b.accept(sample(id = "new", pos = 0)); time += 5_000; b.accept(sample(id = "new", pos = 5_000))
        val before = writes.toList(); b.accept(sample(pos = 900_000, intent = RuntimeIntent.SEEK))
        assertEquals(before, writes); assertEquals(EpisodeId("episode-b"), writes.last().episodeId)
    }
    @Test fun preloaded_metadata_and_ended_without_play_do_not_create_history() = runBlocking<Unit> {
        val b = bridge(); b.select(target, "old")
        b.accept(sample(id = "preload")); time = 90_000
        b.accept(sample(id = "preload", state = RuntimeState.ENDED))
        b.accept(sample(playing = false)); b.accept(sample(playing = false, state = RuntimeState.ENDED))
        assertTrue(writes.isEmpty())
    }
    @Test fun variant_timebase_mismatch_preserves_original_and_missing_ids_fail_closed() = runBlocking<Unit> {
        val b = bridge(); qualify(b); val saved = writes.single()
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            b.select(target.copy(variantId = VariantId("cut-b")), "new", saved)
        } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            b.select(target.copy(episodeId = null), "missing")
        } }
        assertEquals(listOf("old"), allocations); assertEquals(saved, writes.single())
    }
    @Test fun failed_commit_is_not_reported_saved_and_remains_retryable() = runBlocking<Unit> {
        val b = bridge(); start(b); outcome = CommitResult.Failed("io")
        assertEquals(outcome, b.accept(sample(pos = 0, intent = RuntimeIntent.SEEK)))
        outcome = CommitResult.Committed
        assertEquals(outcome, b.accept(sample(playing = false, intent = RuntimeIntent.BOUNDARY)))
        assertEquals(listOf(0L, 1L), writes.map { it.operationSequence })
    }
    @Test fun cancellation_propagates_from_real_coordinator() = runBlocking<Unit> {
        val b = bridge(object : PlaybackProgressStore {
            override suspend fun commit(snapshot: PlaybackSnapshot): CommitResult = throw CancellationException("cancel")
        }); start(b)
        assertThrows(CancellationException::class.java) { runBlocking { b.accept(sample(intent = RuntimeIntent.SEEK)) } }
    }
    @Test(timeout = 10_000) fun queued_pause_during_slow_io_uses_observation_time_not_drain_time() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val b = bridge(object : PlaybackProgressStore {
            override suspend fun commit(snapshot: PlaybackSnapshot): CommitResult {
                writes += snapshot; if (writes.size == 1) { entered.complete(Unit); release.await() }; return CommitResult.Committed
            }
        }); start(b); time = 100
        val first = launch { b.accept(sample(intent = RuntimeIntent.SEEK)) }; entered.await()
        time = 200; val paused = sample(playing = false, intent = RuntimeIntent.BOUNDARY)
        val second = async { b.accept(paused) }; yield(); time = 9_000
        release.complete(Unit); first.join(); assertNull(second.await())
        assertEquals(WatchState.NOT_STARTED, writes.single().watchState)
    }
    @Test fun rapid_back_does_not_overwrite_last_valid_position_with_loading_zero() = runBlocking<Unit> {
        val b = bridge(); qualify(b); time++
        b.accept(sample(pos = 0, playing = false, state = RuntimeState.BUFFERING, intent = RuntimeIntent.BOUNDARY))
        b.accept(sample(pos = 0, playing = false, state = RuntimeState.IDLE, intent = RuntimeIntent.BOUNDARY))
        assertTrue(writes.all { it.positionMs == 6_000L })
    }
}
