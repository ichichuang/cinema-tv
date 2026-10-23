package com.ichichuang.cinema.domain.userdata

import com.ichichuang.cinema.domain.model.catalog.*
import com.ichichuang.cinema.domain.model.userdata.*
import org.junit.Assert.*
import org.junit.Test

class UserDataContractTest {
    private fun playback() = PlaybackSnapshot(
        WorkId("work-a"), EpisodeId("episode-a"), VariantId("variant-a"),
        "session-a", 0, 0, null, WatchState.NOT_STARTED, 1_000,
    )

    @Test fun negative_position_is_rejected() {
        assertEquals(0L, playback().positionMs)
        assertThrows(IllegalArgumentException::class.java) { playback().copy(positionMs = -1) }
    }

    @Test fun unknown_duration_remains_null() {
        assertNull(playback().durationMs)
        assertEquals(0L, playback().copy(durationMs = 0).durationMs)
        assertThrows(IllegalArgumentException::class.java) { playback().copy(durationMs = -1) }
    }

    @Test fun negative_operation_sequence_is_rejected() {
        assertEquals(0L, playback().operationSequence)
        assertThrows(IllegalArgumentException::class.java) { playback().copy(operationSequence = -1) }
    }

    @Test fun session_sequence_does_not_claim_global_recency() {
        val previous = playback().copy(operationSequence = 100, positionMs = 90_000, capturedAt = 2_000)
        val nextSession = playback().copy(sessionId = "session-b", capturedAt = 1_000)
        assertEquals(0L, nextSession.operationSequence)
        assertNotEquals(previous.sessionId, nextSession.sessionId)
        // A clock moving backwards and a new session are representable, not ordered by this value object.
        val seekBack = previous.copy(operationSequence = 101, positionMs = 0)
        assertEquals(0L, seekBack.positionMs)
        assertEquals(101L, seekBack.operationSequence)
    }

    @Test fun browse_contexts_remain_independent() {
        val first = BrowseSnapshot(
            "library", "library", null, CatalogQuery.create("query-a"), 4,
            WorkId("work-a"), WorkId("work-a"), -0.25f, 1_000,
        )
        val second = first.copy(contextKey = "favorites", route = "favorites", focusedWorkId = WorkId("work-b"))
        assertEquals("library", first.contextKey)
        assertEquals("favorites", second.contextKey)
        assertEquals(WorkId("work-a"), first.focusedWorkId)
        assertEquals(WorkId("work-b"), second.focusedWorkId)
        assertEquals(-0.25f, second.relativeOffset)
        val empty = first.copy(query = null, resultRevision = null, focusedWorkId = null, anchorWorkId = null)
        assertNull(empty.focusedWorkId)
        assertNull(empty.anchorWorkId)
        assertNull(empty.resultRevision)
    }

    @Test fun commit_outcomes_are_distinct() {
        val outcomes = setOf(CommitResult.Committed, CommitResult.RejectedStale, CommitResult.Failed("storage_unavailable"))
        assertEquals(3, outcomes.size)
        assertEquals("storage_unavailable", (outcomes.last() as CommitResult.Failed).reason)
        // No store is instantiated and no durable write is claimed.
    }

    @Test fun invalid_snapshot_context_is_rejected() {
        assertThrows(IllegalArgumentException::class.java) { playback().copy(sessionId = "") }
        val valid = BrowseSnapshot("library", "library", null, null, null, null, null, 0f, 0)
        assertThrows(IllegalArgumentException::class.java) { valid.copy(contextKey = " ") }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(route = "") }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(resultRevision = -1) }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(relativeOffset = Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(relativeOffset = Float.POSITIVE_INFINITY) }
    }
}
