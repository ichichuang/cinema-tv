package com.ichichuang.cinema.core.navigation

import com.ichichuang.cinema.domain.model.userdata.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Test

class BrowseCheckpointCoordinatorTest {
    private class Store : BrowseNavigationStore {
        val rows = linkedMapOf<String, RestoreNavigationState>()
        var active: RestoreNavigationState? = null
        var result: CommitResult = CommitResult.Committed
        var cancelled = false
        override suspend fun saveActive(state: RestoreNavigationState): CommitResult {
            if (cancelled) throw CancellationException("test cancellation")
            if (result == CommitResult.Committed) { rows[state.snapshot.contextKey] = state; active = state }
            return result
        }
        override suspend fun loadLatest(topLevel: BrowseTopLevel) = rows.values.lastOrNull { it.snapshot.route == topLevel.route }
        override suspend fun loadActive() = active
    }
    @Test fun save_on_leave_survives_debounce() = runBlocking {
        val store = Store(); val wake = Channel<Unit>(Channel.UNLIMITED)
        val c = BrowseCheckpointCoordinator(store, this, BrowseDelay { assertEquals(500L, it); wake.receive() })
        val s = RestoreNavigationState(browseSnapshot()); val t = c.begin(s)
        c.update(t, s.copy(snapshot = s.snapshot.copy(relativeOffset = .6f)))
        yield(); assertNull(store.active)
        assertEquals(CommitResult.Committed, c.flush(t)); assertEquals(.6f, store.active!!.snapshot.relativeOffset)
        assertFalse(c.hasPending)
    }
    @Test fun stale_route_write_cannot_replace_newer_context() = runBlocking {
        val store = Store(); val c = BrowseCheckpointCoordinator(store, this)
        val a = RestoreNavigationState(browseSnapshot()); val old = c.begin(a)
        val b = RestoreNavigationState(browseSnapshot("favorites")); val current = c.begin(b)
        assertEquals(CommitResult.Committed, c.flush(current))
        assertFalse(c.update(old, a)); assertEquals(CommitResult.RejectedStale, c.flush(old))
        assertEquals("favorites", store.active!!.snapshot.contextKey)
    }
    @Test fun stable_focus_debounces_to_last_observation() = runBlocking {
        val store = Store(); val wake = Channel<Unit>(Channel.UNLIMITED)
        val c = BrowseCheckpointCoordinator(store, this, BrowseDelay { wake.receive() })
        val s = RestoreNavigationState(browseSnapshot()); val t = c.begin(s)
        c.update(t, s); yield()
        c.update(t, s.copy(snapshot = s.snapshot.copy(focusedWorkId = com.ichichuang.cinema.domain.model.catalog.WorkId("last"))))
        yield(); assertNull(store.active); wake.send(Unit); yield()
        assertEquals("last", store.active!!.snapshot.focusedWorkId!!.value)
    }
    @Test fun failure_stale_and_cancellation_retain_retry() = runBlocking {
        val store = Store(); val c = BrowseCheckpointCoordinator(store, this)
        val t = c.begin(RestoreNavigationState(browseSnapshot()))
        store.result = CommitResult.Failed("storage_io")
        assertEquals(store.result, c.flush(t)); assertTrue(c.hasPending)
        store.result = CommitResult.RejectedStale
        assertEquals(store.result, c.flush(t)); assertTrue(c.hasPending)
        store.cancelled = true
        try { c.flush(t); fail("must propagate cancellation") } catch (_: CancellationException) { }
        assertTrue(c.hasPending); assertNull(store.active)
        store.cancelled = false; store.result = CommitResult.Committed
        assertEquals(CommitResult.Committed, c.flush(t))
    }
    @Test fun old_query_callback_is_rejected_after_successful_flush() = runBlocking {
        val store = Store(); val c = BrowseCheckpointCoordinator(store, this)
        val state = RestoreNavigationState(browseSnapshot()); val token = c.begin(state)
        assertEquals(CommitResult.Committed, c.flush(token))
        assertFalse(c.update(token, state.copy(snapshot = state.snapshot.copy(query = browseSnapshot("history").query))))
    }

    @Test fun in_flight_old_route_commit_finishes_before_new_active_pointer() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val writes = mutableListOf<String>()
        val store = object : BrowseNavigationStore {
            override suspend fun saveActive(state: RestoreNavigationState): CommitResult {
                if (state.snapshot.contextKey == "library") { entered.complete(Unit); release.await() }
                writes += state.snapshot.contextKey
                return CommitResult.Committed
            }
            override suspend fun loadLatest(topLevel: BrowseTopLevel): RestoreNavigationState? = null
            override suspend fun loadActive(): RestoreNavigationState? = null
        }
        val c = BrowseCheckpointCoordinator(store, this)
        val a = c.begin(RestoreNavigationState(browseSnapshot()))
        val oldWrite = async { c.flush(a) }; entered.await()
        val b = c.begin(RestoreNavigationState(browseSnapshot("history")))
        val newWrite = async { c.flush(b) }; yield()
        assertTrue(writes.isEmpty()); release.complete(Unit)
        assertEquals(CommitResult.Committed, oldWrite.await())
        assertEquals(CommitResult.Committed, newWrite.await())
        assertEquals(listOf("library", "history"), writes)
        assertEquals(CommitResult.Committed, c.lastResult)
    }
    @Test fun three_top_level_contexts_keep_independent_queries_and_anchors() = runBlocking {
        val store = Store(); val c = BrowseCheckpointCoordinator(store, this)
        for (context in listOf("library", "favorites", "history")) {
            val state = RestoreNavigationState(browseSnapshot(context))
            assertEquals(CommitResult.Committed, c.flush(c.begin(state)))
        }
        assertEquals(setOf("library", "favorites", "history"), store.rows.keys)
        assertEquals("library", store.rows.getValue("library").snapshot.query!!.queryId)
        assertEquals("favorites", store.rows.getValue("favorites").snapshot.query!!.queryId)
        assertEquals("history", store.active!!.snapshot.query!!.queryId)
    }

    @Test fun pending_checkpoint_defensively_captures_caller_return_chain() = runBlocking {
        val chain = mutableListOf(BrowseChildRoute.DETAIL)
        val store = Store(); val c = BrowseCheckpointCoordinator(store, this)
        val state = RestoreNavigationState(browseSnapshot(), chain)
        val token = c.begin(state)
        chain += BrowseChildRoute.PLAYER; chain += BrowseChildRoute.DETAIL
        assertEquals(CommitResult.Committed, c.flush(token))
        assertEquals(listOf(BrowseChildRoute.DETAIL), store.active!!.returnChain)
        assertThrows(UnsupportedOperationException::class.java) {
            (state.returnChain as MutableList<BrowseChildRoute>).add(BrowseChildRoute.PLAYER)
        }
        Unit
    }

}
