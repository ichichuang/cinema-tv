package com.ichichuang.cinema.feature.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch

/**
 * R17 goal A: the ordered, single-authority switch boundary used by
 * [PlayerControlsRuntime.selectTarget]. Pure JVM (no Context/Player/Room), so the exact
 * production coordination is directly testable.
 *
 * A selection reserves a monotonically increasing generation and runs its mutation through
 * [OrderedSwitchGate.run], which:
 *  - serializes every switch behind a single Mutex (a newer selection queues behind an older
 *    one rather than interleaving with it), and
 *  - rejects a superseded generation BEFORE the action runs.
 *
 * The action receives a [staleGuard] predicate to re-check the generation at its own
 * cancellation points (before and after a blocking Room-bound selection), so an older
 * selection that already entered selectFromPlayer is still rejected before it mutates the
 * player identity or a later checkpoint. The gate's withTimeout bound means a hung switch
 * releases the lock and fails loudly (timeout) rather than starving later selections; the
 * Mutex releases the lock automatically when the holding coroutine is cancelled.
 *
 * There is no second state authority and no extra timer: the single ExoPlayer and the
 * existing checkpoint coordinator remain the only writers.
 */
internal class OrderedSwitchCoordinator(
    private val scope: CoroutineScope,
    gate: OrderedSwitchGate? = null,
) {
    private val gate: OrderedSwitchGate = gate ?: OrderedSwitchGate()

    /**
     * Reserves the next generation and launches the switch.
     *
     * @param hold an optional test-only hold; when set, the action waits on it before
     *             running (mirrors PlayerControlsTestHooks.holdSwitch).
     * @param onStale invoked when a superseded generation is dropped before mutation.
     * @param onTimeout invoked when the mutation exceeds the gate's bound (a failed switch).
     * @param action the player mutation; it receives the reserved [generation] and the
     *               [staleGuard] predicate, and runs while the switch is still the latest.
     * @return the reserved generation.
     */
    fun select(
        hold: CompletableDeferred<Unit>? = null,
        onStale: (Long) -> Unit = {},
        onTimeout: (Long) -> Unit = {},
        action: suspend (generation: Long, staleGuard: suspend () -> Boolean) -> Unit,
    ): Long {
        val generation = gate.reserve()
        scope.launch {
            try {
                val ran = gate.run(generation) {
                    hold?.await()
                    // When a test-only hold stalled this selection, re-check the generation
                    // after it: a newer selection reserved while this one was held is rejected
                    // before it can touch the player (this is what traces "stale-drop" for
                    // E05/E06). Without a hold, the gate's own isLatest check at the boundary
                    // is the sole entry guard.
                    if (hold != null && !gate.isLatest(generation)) { onStale(generation); return@run }
                    action(generation) { !gate.isLatest(generation) }
                }
                // gate.run returns false when the generation was already superseded at the
                // boundary (queued behind an older switch); report it so the caller can trace it.
                if (!ran) onStale(generation)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (timeout: TimeoutCancellationException) { onTimeout(generation) }
        }
        return generation
    }
}
