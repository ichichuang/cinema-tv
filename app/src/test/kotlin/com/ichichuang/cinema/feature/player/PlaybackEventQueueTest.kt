package com.ichichuang.cinema.feature.player

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PlaybackEventQueueTest {
    @Test(timeout = 10_000) fun cancelling_an_entered_request_stops_its_side_effects_and_next_request_still_runs() = runBlocking<Unit> {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val queue = PlaybackEventQueue(owner)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var oldMediaSet = false
        val obsolete = launch { queue.await { entered.complete(Unit); release.await(); oldMediaSet = true } }
        entered.await(); obsolete.cancelAndJoin(); release.complete(Unit)
        assertEquals("new request", queue.await { "new request" })
        assertFalse("cancelled request must not set old media", oldMediaSet)
        queue.close(); owner.cancel()
    }
    @Test(timeout = 10_000) fun no_detached_post_destroy_write_and_pending_await_is_cancelled() = runBlocking<Unit> {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val queue = PlaybackEventQueue(owner)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); var committed = false
        val request = async { queue.await { entered.complete(Unit); release.await(); committed = true } }
        entered.await(); owner.cancel(); release.complete(Unit)
        assertThrows(CancellationException::class.java) { runBlocking { request.await() } }
        assertFalse(committed)
        assertFalse(queue.post { committed = true })
    }
    @Test(timeout = 10_000) fun switching_waits_for_an_inflight_write_in_order() = runBlocking<Unit> {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val queue = PlaybackEventQueue(owner)
        val trace = mutableListOf<String>(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        queue.post { trace += "old-write"; entered.complete(Unit); release.await(); trace += "committed" }
        entered.await(); val selected = async { queue.await { trace += "select-new" } }; yield()
        assertEquals(listOf("old-write"), trace); assertFalse(selected.isCompleted)
        release.complete(Unit); selected.await(); assertEquals(listOf("old-write", "committed", "select-new"), trace)
        queue.close(); owner.cancel()
    }
}
