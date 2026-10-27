package com.ichichuang.cinema.feature.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R17 goal A (JVM, runtime layer): drives [OrderedSwitchCoordinator] — the exact production
 * coordination that [PlayerControlsRuntime.selectTarget] uses — through a deterministic
 * inversion of two (or more) rapid selections' completion order.
 *
 * Each selection mutation updates a small "authority" model mirroring the real writers:
 *  - the selected work / episode / variant identity (PersonalLibraryRuntime.selectedMedia),
 *  - the player media identity and seek position (ExoPlayer),
 *  - the last committed Room checkpoint (RoomPlaybackProbe),
 *  - the UI selected item (the picker highlight).
 *
 * These are NOT re-implementations of the gate: the coordinator is the production code the
 * runtime actually calls, and the [staleGuard] the action uses is the predicate the
 * coordinator hands to it (the same predicate the runtime forwards to selectFromPlayer).
 * The test never adds its own second isLatest check.
 */
class OrderedSwitchCoordinatorTest {

    /** A fake single-writer authority; a stale switch must not touch any of its fields. */
    private class Authority {
        var selectedWork: String? = null; private set
        var selectedEpisode: String? = null; private set
        var selectedVariant: String? = null; private set
        var playerMediaId: String? = null; private set
        var playerPositionMs: Long = 0L; private set
        var lastCheckpoint: String? = null; private set
        var uiSelected: String? = null; private set
        val mutations = mutableListOf<String>()

        fun switchTo(work: String, episode: String, variant: String, mediaId: String, positionMs: Long) {
            selectedWork = work; selectedEpisode = episode; selectedVariant = variant
            playerMediaId = mediaId; playerPositionMs = positionMs
            lastCheckpoint = "$work/$episode/$variant"
            uiSelected = "$episode:$variant"
            mutations += "$work/$episode/$variant"
        }
    }

    private val authority = Authority()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /**
     * A selection that, when it actually runs, consults the coordinator-provided [staleGuard]
     * (exactly as selectFromPlayer does) and then mutates the authority. `hold` models the
     * test-only holdSwitch gate that stalls a selection right before its blocking selection.
     */
    private fun selectTarget(
        coordinator: OrderedSwitchCoordinator,
        target: String,
        hold: CompletableDeferred<Unit>?,
        stale: MutableList<Long>? = null,
        consultStaleGuard: Boolean = true,
    ) {
        val (work, episode, variant) = target.split("/")
        coordinator.select(
            hold = hold,
            onStale = { stale?.add(it) },
        ) { _generation, staleGuard ->
            if (consultStaleGuard && staleGuard()) return@select
            authority.switchTo(work, episode, variant, "media-$target", 42L)
        }
    }

    // ── the core race: an older selection resolves last but must not overwrite any authority ──

    @Test(timeout = 10_000)
    fun stale_selection_completing_after_newer_never_overwrites_any_authority() = runBlocking<Unit> {
        val coordinator = OrderedSwitchCoordinator(scope)
        val holdOlder = CompletableDeferred<Unit>()
        val stale = mutableListOf<Long>()

        selectTarget(coordinator, "w1/ep-a/v1", holdOlder, stale)   // older, reserves g1, holds the gate
        selectTarget(coordinator, "w1/ep-b/v2", null)               // newer, reserves g2, queues behind g1
        yield()

        // Release the older hold: it re-checks its generation, finds it stale (g2 is newer),
        // and must NOT mutate. The newer selection then runs and becomes the sole authority.
        holdOlder.complete(Unit)
        yield()

        assertEquals("w1", authority.selectedWork)
        assertEquals("ep-b", authority.selectedEpisode)
        assertEquals("v2", authority.selectedVariant)
        assertEquals("media-w1/ep-b/v2", authority.playerMediaId)
        assertEquals(42L, authority.playerPositionMs)
        assertEquals("w1/ep-b/v2", authority.lastCheckpoint)
        assertEquals("ep-b:v2", authority.uiSelected)
        assertEquals(listOf("w1/ep-b/v2"), authority.mutations)
    }

    // ── three rapid selections, completion order inverted: only the newest may mutate ──

    @Test(timeout = 10_000)
    fun three_rapid_selections_inverted_completion_only_newest_mutates() = runBlocking<Unit> {
        val coordinator = OrderedSwitchCoordinator(scope)
        val g1 = CompletableDeferred<Unit>()
        val g2 = CompletableDeferred<Unit>()

        selectTarget(coordinator, "w1/ep-a/v1", g1)
        selectTarget(coordinator, "w1/ep-b/v2", g2)
        selectTarget(coordinator, "w1/ep-c/v3", null)

        // Release the holds in reverse order: the two older selections must be rejected, only
        // the newest (v3) mutates.
        g2.complete(Unit); yield()
        g1.complete(Unit); yield()

        assertEquals("w1/ep-c/v3", authority.lastCheckpoint)
        assertEquals("ep-c:v3", authority.uiSelected)
        assertEquals("media-w1/ep-c/v3", authority.playerMediaId)
        assertEquals(listOf("w1/ep-c/v3"), authority.mutations)
    }

    // ── serialization: a newer selection queues behind an older one, never interleaving ──

    @Test(timeout = 10_000)
    fun selections_are_serialized_so_a_newer_one_cannot_interleave() = runBlocking<Unit> {
        val coordinator = OrderedSwitchCoordinator(scope)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val trace = mutableListOf<String>()

        val first = async {
            coordinator.select(hold = null) { _, _ ->
                trace += "first-enter"
                entered.complete(Unit)
                release.await()
                trace += "first-exit"
            }
        }
        entered.await()
        // The second selection must wait on the mutex, not interleave with the first.
        coordinator.select(hold = null) { _, _ -> trace += "second" }
        yield()
        assertEquals(listOf("first-enter"), trace)
        release.complete(Unit)
        first.await()
        yield()
        assertEquals(listOf("first-enter", "first-exit", "second"), trace)
    }

    // ── boundedness: a hung selection times out and releases the lock, later ones proceed ──

    @Test(timeout = 10_000)
    fun a_hung_selection_times_out_and_does_not_starve_later_selections() = runBlocking<Unit> {
        val shortGate = OrderedSwitchGate(timeoutMs = 200L)
        val coordinator = OrderedSwitchCoordinator(scope, shortGate)
        val hold = CompletableDeferred<Unit>()
        val timedOut = mutableListOf<Long>()
        val timeoutSignalled = CompletableDeferred<Unit>()

        // The coordinator's hold is never released: the gate's withTimeout bound must fire
        // (a timeout is a failed switch) and release the mutex for later selections.
        coordinator.select(hold = hold, onTimeout = { timedOut += it; timeoutSignalled.complete(Unit) }) { _, _ -> }
        // The later selection must not queue forever behind the hung one: the timeout frees
        // the mutex and lets it run.
        selectTarget(coordinator, "w1/ep-b/v2", null)
        timeoutSignalled.await()
        yield()

        assertEquals(listOf(1L), timedOut)
        assertEquals("w1/ep-b/v2", authority.lastCheckpoint)
    }

    // ── gate distinction: the gate's own isLatest check is the only entry guard ──
    // The action here deliberately does NOT consult the staleGuard, so the only thing that can
    // reject a superseded generation is OrderedSwitchGate.run's isLatest check. This test must
    // fail when that check is removed (the R17 gate mutation).
    @Test(timeout = 10_000)
    fun superseded_generation_queued_behind_the_gate_is_rejected_without_staleGuard() = runBlocking<Unit> {
        val coordinator = OrderedSwitchCoordinator(scope)
        val blockerRelease = CompletableDeferred<Unit>()
        val stale = mutableListOf<Long>()

        // A blocker selection holds the gate (it never mutates the authority).
        coordinator.select(hold = null) { _, _ -> blockerRelease.await() }
        yield()
        // While the gate is held, the "older" selection queues, then a "newer" one supersedes it.
        selectTarget(coordinator, "w1/ep-b/v2", null, stale, consultStaleGuard = false)  // queues behind the blocker
        selectTarget(coordinator, "w1/ep-c/v3", null, consultStaleGuard = false)          // supersedes ep-b
        yield()

        // Release the blocker: ep-b gets the gate first but is already superseded (v3 is newer),
        // so gate.run's isLatest check rejects it before its action runs. Only v3 mutates.
        blockerRelease.complete(Unit)
        yield()

        assertEquals("w1/ep-c/v3", authority.lastCheckpoint)
        assertEquals(listOf("w1/ep-c/v3"), authority.mutations)
    }

    // ── staleGuard: an already-entered selection re-checks its generation at its own
    // cancellation point (mirrors selectFromPlayer checking before/after probe.select) ──

    @Test(timeout = 10_000)
    fun selection_entered_but_superseded_during_its_blocking_step_is_rejected_by_staleGuard() = runBlocking<Unit> {
        val coordinator = OrderedSwitchCoordinator(scope)
        val entered = CompletableDeferred<Unit>()
        val releaseBlockingStep = CompletableDeferred<Unit>()

        // The first selection enters its action (no hold), reaches a blocking step, and stalls.
        coordinator.select(hold = null) { _, staleGuard ->
            if (staleGuard()) return@select
            entered.complete(Unit)
            releaseBlockingStep.await()          // models the blocking Room-bound probe.select
            if (staleGuard()) return@select      // models selectFromPlayer's post-select re-check
            authority.switchTo("w1", "ep-a", "v1", "media-w1/ep-a/v1", 42L)
        }
        entered.await()
        // A newer selection supersedes it while the first is still blocked.
        selectTarget(coordinator, "w1/ep-b/v2", null)
        yield()
        // Release the blocking step: the first selection re-checks its generation via the
        // staleGuard and must NOT mutate; the newer selection is the sole authority.
        releaseBlockingStep.complete(Unit)
        yield()

        assertEquals("w1/ep-b/v2", authority.lastCheckpoint)
        assertEquals(listOf("w1/ep-b/v2"), authority.mutations)
    }
}
