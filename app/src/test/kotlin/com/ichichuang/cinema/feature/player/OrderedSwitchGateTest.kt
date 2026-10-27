package com.ichichuang.cinema.feature.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R17 goal A (JVM, gate layer): the ordered switch boundary primitive. A superseded
 * generation is rejected BEFORE its action runs; generations are serialized behind a single
 * Mutex; isLatest is true only for the newest reserved generation. The full runtime
 * coordination (reserve -> launch -> hold -> staleGuard -> mutate) is covered by
 * OrderedSwitchCoordinatorTest, which drives the production OrderedSwitchCoordinator.
 */
class OrderedSwitchGateTest {

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
     * The gate's own isLatest check is the entry guard: a generation that was superseded
     * before its run() acquires the lock is rejected (run returns false) without running the
     * action. This is the gate-level counterpart of OrderedSwitchCoordinatorTest, which drives
     * the full runtime coordination.
     */
    @Test(timeout = 10_000)
    fun superseded_generation_is_rejected_without_running_the_action() = runBlocking<Unit> {
        val gate = OrderedSwitchGate()
        val applied = mutableListOf<Long>()
        val g1 = gate.reserve()
        val g2 = gate.reserve()   // g2 supersedes g1 before g1 runs

        assertFalse("superseded generation must be rejected without running its action",
            gate.run(g1) { applied += 1L })
        assertTrue("latest generation must run", gate.run(g2) { applied += 2L })
        assertEquals(listOf(2L), applied)
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
