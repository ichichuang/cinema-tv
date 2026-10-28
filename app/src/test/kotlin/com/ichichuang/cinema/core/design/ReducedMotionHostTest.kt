package com.ichichuang.cinema.core.design

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the real [ReducedMotionHost] — the production class that wires the
 * store flow and the [ReduceMotionController] into the two signals the probe
 * root consumes — over an in-memory store and an injected scale source.
 *
 * The store fake is only the storage boundary (a `MutableStateFlow`); the merge
 * logic and the toggle->write contract under test are the production code, not
 * a stand-in. These tests are what a mutation that drops the `setReduceMotion`
 * write (the "toggle does not persist" defect) must trip.
 */
class ReducedMotionHostTest {

    /** In-memory [ReduceMotionStore]; records writes so tests can assert persistence. */
    private class FakeStore(initial: Boolean = false) : ReduceMotionStore {
        private val state = MutableStateFlow(initial)
        var lastWritten: Boolean? = null
        override val reduceMotion: Flow<Boolean> = state

        override suspend fun setReduceMotion(enabled: Boolean) {
            lastWritten = enabled
            state.value = enabled
        }
    }

    private fun host(store: ReduceMotionStore, scale: Float = 1f): ReducedMotionHost =
        ReducedMotionHost(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            store = store,
            scaleSource = SystemAnimatorScaleSource { scale },
        )

    /** Poll until [condition] is true, so launches on the host's scope settle. */
    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 2_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            runBlocking { delay(5) }
        }
        assertTrue("condition did not become true in time", condition())
    }

    @Test
    fun `toggling writes the store and flips the effective signal`() {
        val store = FakeStore(false)
        val host = host(store)

        assertEquals(false, host.reduceMotionSetting)
        assertEquals(false, host.reduceMotion)

        host.toggle()
        await { store.lastWritten == true }
        assertTrue(host.reduceMotionSetting)
        assertTrue(host.reduceMotion)
    }

    @Test
    fun `a store change re-emits the effective signal through the controller`() {
        val store = FakeStore(false)
        val host = host(store)
        assertEquals(false, host.reduceMotion)

        // Simulate an external write (e.g. the phone config path) and assert the
        // effective signal follows without a toggle() call.
        runBlocking { store.setReduceMotion(true) }
        await { host.reduceMotion }
        assertTrue(host.reduceMotionSetting)
    }

    @Test
    fun `the setting and the zero platform scale combine with OR`() {
        val store = FakeStore(false)
        // Platform scale 0 must reduce even with the setting off.
        val host = host(store, scale = 0f)
        await { host.reduceMotion }
        assertEquals(false, host.reduceMotionSetting)
    }

    @Test
    fun `a failed write surfaces a retryable error instead of being swallowed`() {
        val failing = object : ReduceMotionStore {
            override val reduceMotion: Flow<Boolean> = MutableStateFlow(false)
            override suspend fun setReduceMotion(enabled: Boolean) {
                throw IllegalStateException("disk full")
            }
        }
        val host = host(failing)
        host.toggle()
        await { host.writeError != null }
        assertNotNull(host.writeError)
        assertTrue(host.writeError!!.contains("重试"))
        // The setting must not lie: a failed write leaves the stored value off.
        assertEquals(false, host.reduceMotionSetting)
    }
}
