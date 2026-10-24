package com.ichichuang.cinema.feature.player

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.ichichuang.cinema.BuildConfig
import com.ichichuang.cinema.data.local.CinemaDatabaseFactory
import com.ichichuang.cinema.data.userdata.PlaybackClock
import com.ichichuang.cinema.data.userdata.RoomPlaybackProgressStore
import com.ichichuang.cinema.domain.model.MovieDetail
import com.ichichuang.cinema.domain.model.userdata.CommitResult
import com.ichichuang.cinema.domain.model.userdata.PlaybackSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.util.UUID

internal object PlaybackProbeAuthorization {
    fun mode(context: Context): PlaybackRuntimeMode {
        if (!BuildConfig.PLAYBACK_ROOM_PROBE) return PlaybackRuntimeMode.LEGACY
        val marker = File(context.noBackupFilesDir, "round06-playback-test.authorization")
        val authorized = marker.isFile && marker.length() == "cinema-round06-disposable-local-media".length.toLong() &&
            marker.readText() == "cinema-round06-disposable-local-media"
        val files = File(context.filesDir, "datastore")
        // Treat unreadable directories and any old record file (even empty/temp) as dirty.
        val legacy = files.exists() && (files.listFiles()?.any { file ->
            listOf("cinema_progress", "cinema_browse", "cinema_filters").any { file.name.startsWith(it) }
        } ?: true)
        return PlaybackProbeGate.select(BuildConfig.PLAYBACK_ROOM_PROBE, BuildConfig.DEBUG, authorized, legacy)
    }
}

/** Test-gated Media3/Room binding. Every player read and command stays on its application looper. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class RoomPlaybackProbe(
    private val context: Context,
    scope: CoroutineScope,
    private val player: Player,
    private val outcome: (CommitResult) -> Unit,
) {
    private val database = CinemaDatabaseFactory.open(context, "round06-playback-probe.db")
    private val store = RoomPlaybackProgressStore(database)
    private val queue = PlaybackEventQueue(scope) { database.close() }
    private val clock = PlaybackClock { SystemClock.elapsedRealtime() }
    private val bridge = PlaybackRuntimeBridge(store, store::beginSession, clock, System::currentTimeMillis)
    private var preparedId: String? = null
    private var pauseId: String? = null
    private var pauseRevision = 0L
    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST ||
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)) pauseId = player.currentMediaItem?.mediaId
        }
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason != Player.DISCONTINUITY_REASON_SEEK || newPosition.mediaItem?.mediaId != preparedId) return
            val observation = capture(RuntimeIntent.SEEK).copy(sessionId = newPosition.mediaItem!!.mediaId,
                positionMs = newPosition.positionMs, state = RuntimeState.READY)
            observe(observation)
        }
        override fun onEvents(player: Player, events: Player.Events) {
            val currentId = player.currentMediaItem?.mediaId
            if (player.playbackState == Player.STATE_READY) preparedId = currentId
            val intent = if (pauseId != null && pauseId == currentId) RuntimeIntent.BOUNDARY else RuntimeIntent.SAMPLE
            pauseId = null
            if (player.playbackState != Player.STATE_ENDED || events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) observe(capture(intent))
        }
    }
    init { player.addListener(listener) }

    private fun capture(intent: RuntimeIntent = RuntimeIntent.SAMPLE) = RuntimeObservation(
        player.currentMediaItem?.mediaId.orEmpty(), clock.elapsedRealtimeMs(), when (player.playbackState) {
            Player.STATE_READY -> RuntimeState.READY
            Player.STATE_ENDED -> RuntimeState.ENDED
            Player.STATE_BUFFERING -> RuntimeState.BUFFERING
            else -> RuntimeState.IDLE
        }, player.isPlaying, player.currentPosition, player.duration, intent)
    private fun report(result: CommitResult?) { if (result != null) outcome(result) }
    fun observe(observation: RuntimeObservation) { queue.post { report(bridge.accept(observation)) } }
    fun pulse() { if (player.isPlaying) observe(capture()) }

    suspend fun start(catalog: PlaybackProbeCatalog, detail: MovieDetail): ProbeMedia? {
        val candidates = catalog.probeMedia.filter { it.detail == detail }
        require(candidates.isNotEmpty() && candidates.map { it.target.workId }.distinct().size == 1) { "缺少可信的测试作品身份" }
        val resume = queue.await { store.loadResume(candidates.first().target.workId) }
        val selected = if (resume == null) candidates.first() else candidates.singleOrNull { it.target.matches(resume) }
            ?: throw IllegalArgumentException("续播剧集或版本已变化，原记录保持")
        return select(catalog, detail, selected.lineIndex, selected.episodeIndex, autoPlay = false, resume = resume)
    }

    suspend fun select(catalog: PlaybackProbeCatalog, detail: MovieDetail, line: Int, episode: Int,
        autoPlay: Boolean, resume: PlaybackSnapshot? = null): ProbeMedia? {
        val media = ProbeIdentity.resolve(catalog, detail, line, episode)
            ?: throw IllegalArgumentException("测试剧集身份缺失、重复或列表已变化")
        val item = detail.lines[line].episodes[episode]
        ProbeMediaFile.requirePrivate(context.filesDir, item.url)
        val previous = capture(RuntimeIntent.BOUNDARY)
        val requestedPauseRevision = pauseRevision
        player.pause()
        return queue.await {
            val saved = bridge.accept(previous)
            report(saved)
            if (saved != null && saved != CommitResult.Committed) return@await null
            val restored = resume ?: database.playbackSnapshots().find(media.target.workId.value,
                media.target.episodeId!!.value, media.target.variantId!!.value)?.snapshot()
            val id = UUID.randomUUID().toString()
            val result = bridge.select(media.target, id, restored)
            report(result)
            if (result != CommitResult.Committed) return@await null
            currentCoroutineContext().ensureActive()
            preparedId = null
            player.setMediaItem(MediaItem.Builder().setUri(item.url).setMediaId(id).build(), restored?.positionMs ?: 0L)
            player.prepare()
            player.playWhenReady = autoPlay && requestedPauseRevision == pauseRevision
            media
        }
    }

    suspend fun retry() {
        val observation = capture()
        val requestedPauseRevision = pauseRevision
        queue.await {
            report(bridge.accept(observation))
            val position = bridge.retry(observation.sessionId, observation.atMs)
            val item = player.currentMediaItem ?: return@await
            currentCoroutineContext().ensureActive()
            preparedId = null
            player.setMediaItem(item, position)
            player.prepare()
            player.playWhenReady = requestedPauseRevision == pauseRevision
        }
    }
    suspend fun boundary(): CommitResult? {
        // Revoke pending autoplay synchronously, including a switch/retry waiting on Room.
        pauseRevision++
        val observation = capture(RuntimeIntent.BOUNDARY)
        player.pause()
        return queue.await {
            player.pause()
            // Keep the original identity/time; never relabel an old observation as the new item.
            bridge.accept(observation).also(::report)
        }
    }
    fun close() { player.removeListener(listener); queue.close() }
}
