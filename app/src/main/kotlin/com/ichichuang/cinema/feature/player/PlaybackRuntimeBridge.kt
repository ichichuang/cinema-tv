package com.ichichuang.cinema.feature.player

import com.ichichuang.cinema.data.userdata.PlaybackCheckpointCoordinator
import com.ichichuang.cinema.data.userdata.PlaybackClock
import com.ichichuang.cinema.data.userdata.PlaybackTarget
import com.ichichuang.cinema.domain.model.userdata.CommitResult
import com.ichichuang.cinema.domain.model.userdata.PlaybackSnapshot
import com.ichichuang.cinema.domain.repository.PlaybackProgressStore
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class PlaybackRuntimeMode { LEGACY, ROOM_PROBE }
internal object PlaybackProbeGate {
    fun select(compiled: Boolean, debug: Boolean, authorized: Boolean, legacyFiles: Boolean): PlaybackRuntimeMode {
        if (!compiled) return PlaybackRuntimeMode.LEGACY
        check(debug && authorized && !legacyFiles) { "播放探针需要已授权且没有旧记录的独立 Debug 测试环境" }
        return PlaybackRuntimeMode.ROOM_PROBE
    }
}

/** Immutable observations, captured on the player looper before any storage suspension. */
internal enum class RuntimeState { IDLE, BUFFERING, READY, ENDED }
internal enum class RuntimeIntent { SAMPLE, SEEK, BOUNDARY }
internal data class RuntimeObservation(val sessionId: String, val atMs: Long, val state: RuntimeState,
    val playing: Boolean, val positionMs: Long, val durationMs: Long, val intent: RuntimeIntent = RuntimeIntent.SAMPLE)

internal class PlaybackRuntimeBridge(store: PlaybackProgressStore,
    allocate: suspend (PlaybackTarget, String) -> CommitResult, private val clock: PlaybackClock, capturedAt: () -> Long,
    private val confirmedPlayback: suspend (String) -> CommitResult = { CommitResult.Committed }) {
    private val mutex = Mutex()
    private var eventTime = 0L
    private val coordinator = PlaybackCheckpointCoordinator(store, allocate, PlaybackClock { eventTime }, capturedAt)
    private var confirmedSessionId: String? = null
    private var sessionId: String? = null
    var positionMs = 0L
        private set

    suspend fun select(target: PlaybackTarget, id: String, resume: PlaybackSnapshot? = null): CommitResult {
        require(target.episodeId != null && target.variantId != null) { "缺少可信的测试剧集或版本身份" }
        require(resume == null || target.matches(resume)) { "续播版本或时间轴不一致" }
        val atMs = clock.elapsedRealtimeMs()
        return mutex.withLock {
            currentCoroutineContext().ensureActive()
            eventTime = atMs
            coordinator.select(target, id, resume).also {
                if (it == CommitResult.Committed) { sessionId = id; positionMs = resume?.positionMs ?: 0L }
            }
        }
    }

    suspend fun accept(value: RuntimeObservation): CommitResult? = mutex.withLock {
        currentCoroutineContext().ensureActive()
        if (sessionId != value.sessionId) return@withLock null
        eventTime = value.atMs
        val id = value.sessionId
        // Actual selected Media3 playback is history evidence independently of resume promotion.
        if (value.state == RuntimeState.READY && value.playing && confirmedSessionId != id) {
            val committed = confirmedPlayback(id)
            if (committed != CommitResult.Committed) return@withLock committed
            confirmedSessionId = id
        }
        val duration = value.durationMs.takeIf { it >= 0 }
        if (value.state == RuntimeState.READY || value.state == RuntimeState.ENDED) {
            require(value.positionMs >= 0)
            if (value.positionMs != 0L || positionMs == 0L || value.intent == RuntimeIntent.SEEK) positionMs = value.positionMs
            coordinator.ready(id, value.positionMs, duration)
            coordinator.playing(id, value.playing && value.state == RuntimeState.READY)
        } else {
            coordinator.loading(id)
        }
        when {
            value.intent == RuntimeIntent.BOUNDARY -> coordinator.boundary(id)
            value.intent == RuntimeIntent.SEEK -> coordinator.seek(id, value.positionMs, duration)
            value.state == RuntimeState.ENDED -> coordinator.ended(id, value.positionMs, duration)
            else -> coordinator.pulse(id, value.positionMs, duration)
        }
    }

    suspend fun retry(id: String, atMs: Long): Long = mutex.withLock {
        currentCoroutineContext().ensureActive()
        if (sessionId == id) { eventTime = atMs; coordinator.loading(id) }
        positionMs
    }
}
