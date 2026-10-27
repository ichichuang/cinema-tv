package com.ichichuang.cinema.feature.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R17 goal A (JVM): ordered selection boundary. A superseded selection is rejected
 * BEFORE it can mutate the player, even when its async work would complete after a
 * newer selection. This mirrors PlayerControlsRuntime.selectTarget's reserve -> launch
 * -> (test hold) -> re-check -> mutate shape, using a controllable CompletableDeferred
 * to deterministically invert the completion order of two rapid selections.
 */
class OrderedSwitchGateTest {

    @Test(timeout = 10_000)
    fun stale_generation_is_rejected_before_action_runs() = runBlocking<Unit> {
        val gate = OrderedSwitchGate()
        val g1 = gate.reserve()
        val g2 = gate.reserve()   // g2 supersedes g1
        val applied = mutableListOf<Long>()
        assertFalse("superseded generation must not run its mutation", gate.run(g1) { applied += 1L })
        assertTrue("latest generation must run", gate.run(g2) { applied += 2L })
        assertEquals(listOf(2L), applied)
    }

    @Test(timeout = 10_000)
    fun serialized_selections_run_in_order_without_interleaving() = runBlocking<Unit> {
        val gate = OrderedSwitchGate()
        val trace = mutableListOf<String>()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val g1 = gate.reserve()
        val first = async { gate.run(g1) { trace += "first-enter"; entered.complete(Unit); release.await(); trace += "first-exit" } }
        entered.await()
        val g2 = gate.reserve()
        val second = async { gate.run(g2) { trace += "second" } }
        yield()
        // The second selection cannot enter the boundary while the first is still inside it.
        assertEquals(listOf("first-enter"), trace)
        release.complete(Unit)
        first.await(); second.await()
        assertEquals(listOf("first-enter", "first-exit", "second"), trace)
    }

    /**
     * The core race: an older selection is held (its async work would finish last), while a
     * newer selection arrives. The older one must be rejected before touching the player;
     * the newer one is the only mutation and the final applied state.
     */
    @Test(timeout = 10_000)
    fun stale_selection_completing_after_newer_is_rejected_before_mutation() = runBlocking<Unit> {
        val gate = OrderedSwitchGate()
        val applied = mutableListOf<String>()
        val holdSwitch = CompletableDeferred<Unit>()
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        // Mirrors PlayerControlsRuntime.selectTarget: reserve -> launch -> hold -> re-check -> mutate.
        fun selectTarget(target: String) {
            val generation = gate.reserve()
            owner.launch {
                gate.run(generation) {
                    holdSwitch.await()
                    if (!gate.isLatest(generation)) return@run   // rejected before mutation
                    applied += target                            // the player mutation
                }
            }
        }

        selectTarget("older-ep")   // older selection, blocked on the shared hold
        selectTarget("newer-ep")   // newer selection supersedes it

        // Release the hold: the older selection resumes first (FIFO on the unconfined owner),
        // sees its generation is stale, and must NOT mutate. The newer selection then runs.
        holdSwitch.complete(Unit)
        owner.cancel()

        assertEquals(listOf("newer-ep"), applied)
    }

    @Test(timeout = 10_000)
    fun is_latest_only_true_for_the_newest_reserved_generation() {
        val gate = OrderedSwitchGate()
        val g1 = gate.reserve()
        assertTrue(gate.isLatest(g1))
        val g2 = gate.reserve()
        assertFalse(gate.isLatest(g1))
        assertTrue(gate.isLatest(g2))
    }
}
