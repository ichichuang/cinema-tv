package com.ichichuang.cinema.feature.player

import java.util.concurrent.atomic.AtomicLong

/**
 * R17 goal A: the single-authority generation token for in-player switch ordering.
 *
 * A switch selection reserves a monotonically increasing generation on the caller thread
 * (synchronously, before any suspension), so a newer selection always carries a higher
 * generation than an older one that is still in flight. [isLatest] is consulted inside
 * [RoomPlaybackProbe.select]'s serialized queue — before any player or Room mutation — so a
 * superseded selection is rejected before it can rebind the Media3 session or write a stale
 * checkpoint.
 *
 * There is no second state authority and no extra timer: the single ExoPlayer and the existing
 * checkpoint coordinator remain the only writers. This class is pure JVM and directly testable.
 */
internal class SelectionGeneration {
    private val latest = AtomicLong(0L)

    /** Reserve the next generation for a new selection request (call on the caller thread). */
    fun reserve(): Long = latest.incrementAndGet()

    /** True while [generation] is still the newest reserved request. */
    fun isLatest(generation: Long): Boolean = latest.get() == generation
}
