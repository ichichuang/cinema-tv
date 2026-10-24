package com.ichichuang.cinema.data.userdata

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LegacyWriterQuiescenceTest {
    @Test fun capture_waits_for_real_inflight_io_then_refuses_delayed_legacy_writes() = runBlocking<Unit> {
        val q = LegacyWriterQuiescence(); var writer = HandoffWriter.LEGACY
        val entered = CompletableDeferred<Unit>(); val finishIo = CompletableDeferred<Unit>()
        val legacy = launch { q.legacy({ writer }) { entered.complete(Unit); finishIo.await() } }
        entered.await()
        val capture = async { q.exclusive { writer = HandoffWriter.ROOM } }
        yield(); assertFalse("capture must wait for DataStore edit completion", capture.isCompleted)
        finishIo.complete(Unit); legacy.join(); capture.await()
        assertThrows(IllegalStateException::class.java) { runBlocking { q.legacy({ writer }) { error("inactive write") } } }
    }
    @Test fun cancelled_waiter_cannot_write_after_handoff_and_does_not_strand_barrier() = runBlocking<Unit> {
        val q = LegacyWriterQuiescence(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val owner = launch { q.exclusive { entered.complete(Unit); release.await() } }; entered.await()
        var writes = 0
        val waiter = launch { q.legacy({ HandoffWriter.LEGACY }) { writes++ } }
        yield(); waiter.cancelAndJoin(); release.complete(Unit); owner.join()
        assertEquals(0, writes)
        q.legacy({ HandoffWriter.LEGACY }) { writes++ }; assertEquals(1, writes)
    }
    @Test fun blocked_authority_never_executes_a_legacy_write() = runBlocking<Unit> {
        var writes = 0
        val q = LegacyWriterQuiescence()
        assertThrows(IllegalStateException::class.java) { runBlocking { q.legacy({ HandoffWriter.BLOCKED }) { writes++ } } }
        assertEquals(0, writes)
    }
}
