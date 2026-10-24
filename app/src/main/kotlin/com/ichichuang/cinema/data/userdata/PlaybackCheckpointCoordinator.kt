package com.ichichuang.cinema.data.userdata

import com.ichichuang.cinema.domain.model.catalog.*
import com.ichichuang.cinema.domain.model.userdata.*
import com.ichichuang.cinema.domain.repository.PlaybackProgressStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun interface PlaybackClock { fun elapsedRealtimeMs(): Long }

internal data class PlaybackTarget(val workId: WorkId, val episodeId: EpisodeId?, val variantId: VariantId?) {
    fun matches(snapshot: PlaybackSnapshot) = workId == snapshot.workId && episodeId == snapshot.episodeId && variantId == snapshot.variantId
}

/** Unbound candidate; the caller supplies observations and a process-local monotonic clock. */
internal class PlaybackCheckpointCoordinator(
    private val store: PlaybackProgressStore,
    private val allocateSession: suspend (PlaybackTarget, String) -> CommitResult,
    private val clock: PlaybackClock,
    private val capturedAt: () -> Long,
) {
    private class Session(val target: PlaybackTarget, val id: String, var lastClock: Long) {
        var position = 0L
        var duration: Long? = null
        var ready = false
        var playing = false
        var activeMs = 0L
        var sinceSaveMs = 0L
        var sequence = 0L
        var state = WatchState.NOT_STARTED
        var pendingSave = false
    }
    private val mutex = Mutex()
    private var session: Session? = null

    suspend fun select(target: PlaybackTarget, sessionId: String, resume: PlaybackSnapshot? = null): CommitResult = event { eventTime ->
        require(sessionId.isNotBlank()) { "Session identity is required" }
        require(resume == null || target.matches(resume)) { "Resume identity/timebase mismatch" }
        session?.let { old ->
            old.account(eventTime)
            if (old.pendingSave || old.activeMs >= INTERVAL_MS) {
                val saved = save(old)
                if (saved != CommitResult.Committed) return@event saved
            }
        }
        val result = allocateSession(target, sessionId)
        if (result == CommitResult.Committed) session = Session(target, sessionId, eventTime).apply {
            if (resume != null) { position = resume.positionMs; duration = resume.durationMs; state = resume.watchState }
        }
        result
    }

    suspend fun ready(sessionId: String, positionMs: Long, durationMs: Long?) = event { eventTime ->
        current(sessionId)?.apply { account(eventTime); observe(positionMs, durationMs); ready = true }
        Unit
    }

    suspend fun playing(sessionId: String, value: Boolean) = event { eventTime ->
        current(sessionId)?.apply {
            account(eventTime); playing = value && ready
            if (playing && state == WatchState.COMPLETED) state = WatchState.IN_PROGRESS
        }
        Unit
    }

    suspend fun pulse(sessionId: String, positionMs: Long, durationMs: Long?): CommitResult? = event { eventTime ->
        val s = current(sessionId) ?: return@event null
        s.account(eventTime)
        if (!s.ready || !s.playing) return@event null
        s.observe(positionMs, durationMs)
        if (s.sinceSaveMs >= INTERVAL_MS) save(s) else null
    }

    suspend fun seek(sessionId: String, positionMs: Long, durationMs: Long?): CommitResult? = event { eventTime ->
        val s = current(sessionId) ?: return@event null
        require(positionMs >= 0) { "Negative playback position" }
        require(durationMs == null || durationMs >= 0) { "Unknown duration must be null" }
        if (!s.ready) return@event null
        s.account(eventTime); s.position = positionMs
        if (durationMs != null) s.duration = durationMs
        if (s.state == WatchState.COMPLETED) s.state = WatchState.IN_PROGRESS
        save(s)
    }

    private fun current(id: String) = session?.takeIf { it.id == id }
    private fun Session.account(eventTime: Long) {
        val now = eventTime.coerceAtLeast(lastClock)
        val delta = now - lastClock
        if (playing) { activeMs += delta; sinceSaveMs += delta }
        lastClock = now
        if (activeMs >= INTERVAL_MS && state == WatchState.NOT_STARTED) state = WatchState.IN_PROGRESS
    }
    private fun Session.observe(positionMs: Long, durationMs: Long?) {
        require(positionMs >= 0) { "Negative playback position" }
        require(durationMs == null || durationMs >= 0) { "Unknown duration must be null" }
        // A transient zero cannot undo a known position. Explicit seek() owns intentional zero.
        if (positionMs != 0L || position == 0L) position = positionMs
        if (durationMs != null) duration = durationMs
    }
    private suspend fun save(s: Session): CommitResult {
        // Keep explicit intent retryable even below the initial viewing threshold or after cancellation.
        s.pendingSave = true
        val snapshot = PlaybackSnapshot(s.target.workId, s.target.episodeId, s.target.variantId,
            s.id, s.sequence++, s.position, s.duration, s.state, capturedAt())
        return store.commit(snapshot).also { if (it == CommitResult.Committed) { s.sinceSaveMs = 0; s.pendingSave = false } }
    }
    companion object { const val INTERVAL_MS = 5_000L }
    suspend fun loading(sessionId: String) = event { eventTime ->
        current(sessionId)?.apply { account(eventTime); playing = false; ready = false }
        Unit
    }
    suspend fun boundary(sessionId: String): CommitResult? = event { eventTime ->
        val s = current(sessionId) ?: return@event null
        s.account(eventTime); s.playing = false
        if (s.pendingSave || s.activeMs >= INTERVAL_MS) save(s) else null
    }
    suspend fun ended(sessionId: String, positionMs: Long, durationMs: Long?): CommitResult? = event { eventTime ->
        val s = current(sessionId) ?: return@event null
        s.account(eventTime)
        if (!s.ready || s.activeMs == 0L) return@event null
        s.observe(positionMs, durationMs); s.playing = false; s.state = WatchState.COMPLETED
        save(s)
    }
    suspend fun isPlaying(): Boolean = mutex.withLock { session?.playing == true }

    private suspend fun <T> event(block: suspend (Long) -> T): T {
        // Capture before the first suspension: storage/Mutex wait is not extra playback.
        val eventTime = clock.elapsedRealtimeMs()
        return mutex.withLock { block(eventTime) }
    }
}
