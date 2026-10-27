package com.ichichuang.cinema.feature.player

/**
 * R17 goal A: the pure switch orchestration that [com.ichichuang.cinema.feature.personal.PersonalLibraryRuntime.selectFromPlayer]
 * delegates to. It is pure JVM (no Context/Player/Room), so the exact production ordering is
 * directly testable.
 *
 * The ordering contract:
 *  1. Before [select] runs, a superseded generation is rejected without invoking [select] at
 *     all (a fast-path that avoids even queuing the blocking selection).
 *  2. [select] is the caller-bound `probe.select(..., generation)`; its authoritative stale
 *     check happens inside [RoomPlaybackProbe.select]'s serialized queue, before any player or
 *     Room mutation. A superseded selection returns null there and never mutates the player.
 *  3. After [select] resolves, [adopt] is only invoked when the selection is still the latest;
 *     this guards the in-memory [com.ichichuang.cinema.feature.personal.PersonalLibraryRuntime.selectedMedia]
 *     mirror so an expired result is never adopted.
 *
 * The generation token is the single state authority; there is no second writer, no extra
 * timer, and [kotlinx.coroutines.CancellationException] is never swallowed here.
 */
internal class SwitchSelection {
    /**
     * Runs one selection through the switch boundary.
     *
     * @param isStale a predicate backed by the single [SelectionGeneration] authority
     *                ([RoomPlaybackProbe.isSelectionStale] in production).
     * @param select the blocking, Room-bound selection bound to a concrete target; returns null
     *               when the selection was superseded (checked inside the serialized queue) or
     *               failed to commit.
     * @param adopt the identity adoption that runs only for the latest selection.
     * @param onStale invoked when a superseded generation is rejected before adoption.
     * @return true when the selection was adopted, false when it was superseded or failed.
     */
    suspend fun perform(
        isStale: suspend () -> Boolean,
        select: suspend () -> ProbeMedia?,
        adopt: suspend (ProbeMedia) -> Unit,
        onStale: suspend () -> Unit,
    ): Boolean {
        if (isStale()) { onStale(); return false }
        val selected = select() ?: return false
        if (isStale()) { onStale(); return false }
        adopt(selected)
        return true
    }
}
