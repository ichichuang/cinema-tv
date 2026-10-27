package com.ichichuang.cinema.feature.player

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicLong

/**
 * R17 goal A: an ordered, single-authority boundary for in-player switch operations
 * (episode / line changes).
 *
 * Every selection reserves a monotonically increasing generation and runs its mutation
 * only while holding a single [Mutex], and only while its generation is still the latest
 * when the boundary is entered. A superseded selection is rejected BEFORE any player
 * mutation, so an expired asynchronous operation can never rebind the Media3 session,
 * seek, or restore a stale play/pause intent after a newer selection has been made.
 *
 * There is no second state authority and no extra timer: the single ExoPlayer and the
 * existing checkpoint coordinator remain the only writers. Callers may re-check
 * [isLatest] from inside a long-running mutation (e.g. after a test-only hold) to drop
 * work that became stale mid-flight.
 *
 * Boundedness (R17 goal A follow-up): the boundary is entered under a [withTimeout].
 * A suspended mutation that is cancelled (or that exceeds the bound) releases the lock,
 * so a hung switch can never make every subsequent selection queue forever. A timeout is
 * a failure, never a silent pass-through: the caller observes the exception and reports it.
 */
internal class OrderedSwitchGate(
    // A switch through the Room probe is I/O-bound; a generous but finite bound keeps one
    // hung switch from starving all later selections without false-failing healthy ones.
    private val timeoutMs: Long = 30_000L,
) {
    private val mutex = Mutex()
    private val latest = AtomicLong(0L)

    /** Reserve the next generation for a new selection request (call on the caller thread). */
    fun reserve(): Long = latest.incrementAndGet()

    /** True while [generation] is still the newest request. */
    fun isLatest(generation: Long): Boolean = latest.get() == generation

    /**
     * Runs [action] while holding the single switch boundary, bounded by [timeoutMs].
     *
     * @return false when [generation] was already superseded (rejected before [action]
     *         runs), true when [action] actually executed.
     * @throws kotlinx.coroutines.TimeoutCancellationException when [action] does not finish
     *         within [timeoutMs]. The lock is released on cancellation and on timeout, and
     *         the caller must treat a timeout as a failed switch, never a silent success.
     */
    suspend fun run(generation: Long, action: suspend () -> Unit): Boolean = mutex.withLock {
        if (!isLatest(generation)) return@withLock false
        withTimeout(timeoutMs) {
            action()
        }
        true
    }
}
