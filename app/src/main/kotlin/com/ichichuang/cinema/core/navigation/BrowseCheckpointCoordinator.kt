package com.ichichuang.cinema.core.navigation

import com.ichichuang.cinema.domain.model.catalog.CatalogQuery
import com.ichichuang.cinema.domain.model.userdata.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun interface BrowseDelay { suspend fun await(milliseconds: Long) }
internal data class CheckpointToken internal constructor(val generation: Long, val contextKey: String, val query: CatalogQuery?)

/** One instance owns the candidate writer. savedAt is data, never a concurrency clock. */
internal class BrowseCheckpointCoordinator(
    private val store: BrowseNavigationStore,
    private val scope: CoroutineScope,
    private val delay: BrowseDelay = BrowseDelay { kotlinx.coroutines.delay(it) },
) {
    private val stateLock = Any()
    private val writes = Mutex()
    private var generation = 0L
    private var current: CheckpointToken? = null
    private var pending: RestoreNavigationState? = null
    private var revision = 0L
    private var timer: Job? = null
    @Volatile var lastResult: CommitResult? = null
        private set
    val hasPending: Boolean get() = synchronized(stateLock) { pending != null }
    fun begin(state: RestoreNavigationState): CheckpointToken = synchronized(stateLock) {
        timer?.cancel(); timer = null; pending = state; revision++; lastResult = null
        CheckpointToken(++generation, state.snapshot.contextKey, state.snapshot.query).also { current = it }
    }
    fun update(token: CheckpointToken, state: RestoreNavigationState): Boolean = synchronized(stateLock) {
        if (token != current || token.contextKey != state.snapshot.contextKey || token.query != state.snapshot.query)
            return@synchronized false
        pending = state; revision++; timer?.cancel()
        val expected = revision
        timer = scope.launch { delay.await(500); commit(token, expected) }
        true
    }
    /** Critical route leave/onStop; caller waits for acknowledgement before changing active origin. */
    suspend fun flush(token: CheckpointToken): CommitResult {
        synchronized(stateLock) { if (token == current) { timer?.cancel(); timer = null } }
        return commit(token, null)
    }
    private suspend fun commit(token: CheckpointToken, expected: Long?): CommitResult = writes.withLock {
        val capture = synchronized(stateLock) {
            if (token != current || (expected != null && expected != revision)) null
            else pending?.let { it to revision }
        } ?: return@withLock CommitResult.RejectedStale
        val result = store.saveActive(capture.first)
        synchronized(stateLock) {
            if (token == current && revision == capture.second) {
                lastResult = result
                if (result == CommitResult.Committed) pending = null
            }
        }
        result
    }
}
