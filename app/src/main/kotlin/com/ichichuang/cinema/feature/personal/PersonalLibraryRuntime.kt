package com.ichichuang.cinema.feature.personal

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ichichuang.cinema.data.local.CinemaDatabase
import com.ichichuang.cinema.data.userdata.*
import com.ichichuang.cinema.domain.model.*
import com.ichichuang.cinema.domain.model.catalog.*
import com.ichichuang.cinema.domain.model.userdata.*
import com.ichichuang.cinema.feature.browse.BrowseProbeFixture
import com.ichichuang.cinema.feature.player.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import org.json.JSONObject
import java.io.File

/** Private authorization belongs to the disposable profile, never an Intent or bundled asset. */
internal object PersonalLibraryAuthorization {
    const val FILE = "round12-personal-library.authorization"
    const val TOKEN = "cinema-round12-disposable-personal-library"
    fun requireOwned(context: Context) {
        val file = File(context.noBackupFilesDir, FILE)
        check(file.isFile && file.length() == TOKEN.length.toLong() && file.readText() == TOKEN) {
            "个人记录测试缺少独立授权"
        }
    }
}

/** Bounded native delivery control, like the existing browse probe hooks; no external activation. */
internal object PersonalLibraryTestHooks {
    var beforeProjection: CompletableDeferred<Unit>? = null
    val projectionDeliveries = java.util.concurrent.atomic.AtomicInteger(0)
}

/** Uses the same owned database and the existing Media3 bridge; no second progress writer. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class PersonalLibraryRuntime(
    private val context: Context,
    val database: CinemaDatabase,
    private val scope: CoroutineScope,
    private val fixture: BrowseProbeFixture,
) {
    val repository = RoomPersonalLibraryRepository(database)
    var favorites by mutableStateOf<List<PersonalLibraryCard>>(emptyList()); private set
    var history by mutableStateOf<List<PersonalLibraryCard>>(emptyList()); private set
    var continuing by mutableStateOf<List<PersonalLibraryCard>>(emptyList()); private set
    var message by mutableStateOf(""); private set
    var actionBusy by mutableStateOf(false); private set
    var detailFocus by mutableStateOf("详情返回")
    var confirmation by mutableStateOf(false)
    var undoWork by mutableStateOf<String?>(null); private set
    private var actionRevision = 0L
    private var undoRevision = -1L
    private var undoDatabaseRevision: Long? = null
    private var undoDeadline = 0L
    private val pendingActions = mutableMapOf<Pair<String, String>, PersonalLibraryAction>()
    private val pendingFavorites = mutableMapOf<String, Boolean>()
    private val pendingUnfavorites = mutableMapOf<String, PersonalLibraryUnfavoriteAction>()
    private val catalog = object : PlaybackProbeCatalog {
        override val probeMedia: List<ProbeMedia> = readPrivateMedia()
    }
    val player = ExoPlayer.Builder(context).build()
    private fun createProbe() = RoomPlaybackProbe(context, scope, player, sharedDatabase = database, confirmedPlayback = repository::confirmPlayback) { result ->
        if (result != CommitResult.Committed) message = "播放记录尚未保存，请重试；原记录保持"
        trace()
    }
    private var probe = createProbe()
    var selectedMedia by mutableStateOf<ProbeMedia?>(null); private set
    var playerPlaying by mutableStateOf(false); private set
    var playerPosition by mutableLongStateOf(0L); private set
    var requestedResumePosition by mutableLongStateOf(0L); private set
    var versionChoiceWork by mutableStateOf<String?>(null); private set
    var versionChoiceDescription by mutableStateOf(""); private set
    private var explicitChoiceTarget: ProbeMedia? = null
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            playerPlaying = player.isPlaying; playerPosition = player.currentPosition; trace()
        }
    }
    init { player.addListener(listener) }

    private fun readPrivateMedia(): List<ProbeMedia> {
        val root = JSONObject(File(context.noBackupFilesDir, BrowseProbeFixture.FILE).readText())
        val values = root.optJSONArray("media") ?: return emptyList()
        data class Entry(val work: String, val episode: String, val variant: String, val uri: String)
        val entries = (0 until values.length()).map { index -> values.getJSONObject(index).let {
            Entry(it.getString("work"), it.getString("episode"), it.getString("variant"), it.getString("uri"))
        } }
        require(entries.map { Triple(it.work, it.episode, it.variant) }.distinct().size == entries.size)
        return entries.groupBy { it.work }.flatMap { (workId, records) ->
            val known = fixture.works.singleOrNull { it.id.value == workId } ?: error("本地测试媒体缺少明确作品身份")
            val variants = records.groupBy { it.variant }.entries.toList()
            records.forEach { ProbeMediaFile.requirePrivate(context.filesDir, it.uri) }
            val detail = MovieDetail(Movie(workId, known.title, 0, 0.0, "", 0, "", origin = known.source.value),
                lines = variants.map { (variant, episodes) -> PlayLine(variant, episodes.map { Episode(it.episode, it.uri) }) })
            variants.flatMapIndexed { line, (_, episodes) -> episodes.mapIndexed { episode, entry ->
                ProbeMedia(detail, line, episode, PlaybackTarget(WorkId(workId), EpisodeId(entry.episode), VariantId(entry.variant)))
            } }
        }
    }
    suspend fun start(onListsChanged: () -> Unit) {
        favorites = repository.favorites(); history = repository.recentHistory(); continuing = repository.continueWatching()
        scope.launch {
            combine(repository.observeFavorites(), repository.observeRecentHistory(), repository.observeContinueWatching()) { a, b, c -> Triple(a, b, c) }
                .collect { (a, b, c) ->
                    PersonalLibraryTestHooks.beforeProjection?.await()
                    favorites = a; history = b; continuing = c; onListsChanged()
                    PersonalLibraryTestHooks.projectionDeliveries.incrementAndGet(); trace()
                }
        }
        scope.launch { while (isActive) { delay(500); probe.pulse(); playerPosition = player.currentPosition; trace() } }
    }
    fun isFavorite(id: String) = favorites.any { it.workId == id }
    fun card(id: String) = (history + continuing + favorites).firstOrNull { it.workId == id }
    fun availability(id: String) = if (catalog.probeMedia.any { it.target.workId.value == id }) "仅独立本地合成媒体可播放" else "已保留本地记录；当前来源不可用，未核实可播放内容"
    private suspend fun remember(id: String): CommitResult {
        val work = fixture.works.firstOrNull { it.id.value == id } ?: return if (card(id) != null) CommitResult.Committed else CommitResult.Failed("missing_card")
        return repository.rememberCard(id, work.title, work.source.value)
    }
    suspend fun favorite(id: String): CommitResult {
        if (actionBusy) return CommitResult.RejectedStale
        val selected = pendingFavorites.getOrPut(id) { !isFavorite(id) }
        val revision = ++actionRevision
        undoWork = null
        var receipt: Long? = null
        return mutate(if (selected) "已收藏" else "已取消收藏，10 秒内可撤销") {
            val prepared = remember(id)
            if (prepared != CommitResult.Committed) prepared
            else if (selected) repository.setFavorite(id, true)
            else {
                val token = pendingUnfavorites[id] ?: repository.prepareUnfavorite(id).also { pendingUnfavorites[id] = it }
                repository.unfavorite(token).also { receipt = it.undoRevision }.result
            }
        }.also {
            if (it == CommitResult.Committed) {
                pendingFavorites.remove(id); pendingUnfavorites.remove(id)
                if (!selected && revision == actionRevision && receipt != null) {
                    undoWork = id; undoRevision = revision; undoDatabaseRevision = receipt
                    undoDeadline = SystemClock.elapsedRealtime() + 10_000
                    scope.launch {
                        delay(10_000)
                        if (undoRevision == revision && undoWork == id) {
                            undoWork = null
                            if (detailFocus == "撤销取消收藏") detailFocus = "收藏操作"
                        }
                    }
                }
            } else if (it == CommitResult.RejectedStale) {
                pendingFavorites.remove(id); pendingUnfavorites.remove(id)
                message = "收藏状态已变化，本次操作未执行，请重新确认"
            }
        }
    }
    suspend fun undo(): CommitResult {
        val id = undoWork ?: return CommitResult.RejectedStale
        val receipt = undoDatabaseRevision ?: return CommitResult.RejectedStale
        if (undoRevision != actionRevision || SystemClock.elapsedRealtime() > undoDeadline) {
            undoWork = null; detailFocus = "收藏操作"; message = "撤销已失效，请重新收藏"; return CommitResult.RejectedStale
        }
        return mutate("收藏已恢复") { repository.undoUnfavorite(id, receipt) }.also {
            if (it == CommitResult.Committed) { undoWork = null; ++actionRevision }
            else if (it == CommitResult.RejectedStale) { undoWork = null; detailFocus = "收藏操作"; message = "收藏状态已变化，撤销未执行" }
        }
    }
    suspend fun remove(id: String) = scopedAction("remove", id, "已移出继续观看；历史、收藏和逐集进度保留") { repository.removeFromContinue(it) }
    suspend fun delete(id: String): CommitResult {
        val result = scopedAction("delete", id, "已删除该作品观看记录与续播；收藏保留") { repository.deleteWatchHistory(it) }
        if (result == CommitResult.Committed && selectedMedia?.target?.workId?.value == id) {
            // The deleted session must remain rejected. Retire its in-memory coordinator,
            // wait for its queued work, and let a new explicit play allocate a fresh session.
            actionBusy = true
            try {
                probe.closeAndJoin()
                player.pause(); player.stop(); player.clearMediaItems()
                selectedMedia = null; requestedResumePosition = 0L; playerPosition = 0L
                probe = createProbe(); trace()
            } finally { actionBusy = false }
        }
        return result
    }
    private suspend fun scopedAction(kind: String, id: String, success: String, action: suspend (PersonalLibraryAction) -> CommitResult): CommitResult {
        val key = kind to id
        val token = pendingActions[key] ?: repository.prepareAction(id).also { pendingActions[key] = it }
        return mutate(success) { action(token) }.also { if (it == CommitResult.Committed) pendingActions.remove(key) }
    }
    private suspend fun mutate(success: String, action: suspend () -> CommitResult): CommitResult {
        if (actionBusy) return CommitResult.RejectedStale
        actionBusy = true
        return try {
            val result = action()
            if (result == CommitResult.Committed) {
                favorites = repository.favorites(); history = repository.recentHistory(); continuing = repository.continueWatching()
                message = success
            } else message = "操作未保存或确认尚未完成，请重试并检查记录"
            trace(); result
        } catch (cancelled: CancellationException) { message = "操作尚未确认，请重新检查记录"; throw cancelled }
        finally { actionBusy = false }
    }
    suspend fun prepare(id: String, fromBeginning: Boolean = false): Boolean {
        val candidates = catalog.probeMedia.filter { it.target.workId.value == id }
        val media = if (fromBeginning) explicitChoiceTarget?.takeIf { it.target.workId.value == id }
            ?: candidates.firstOrNull() else candidates.firstOrNull()
        if (media == null) { message = availability(id); return false }
        if (remember(id) != CommitResult.Committed) { message = "作品资料未保存，请重试"; return false }
        var preferredEpisode: EpisodeId? = null
        return try {
            preferredEpisode = probe.loadResume(WorkId(id))?.episodeId
            val selected = (if (fromBeginning) probe.select(catalog, media.detail, media.lineIndex, media.episodeIndex,
                autoPlay = false, startAtBeginning = true) else probe.start(catalog, media.detail)) ?: return false
            versionChoiceWork = null; explicitChoiceTarget = null; versionChoiceDescription = ""
            selectedMedia = selected; requestedResumePosition = player.currentPosition
            player.pause(); message = "已恢复到上次确认位置，按播放后才会开始"; trace(); true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: IllegalArgumentException) {
            val choice = candidates.firstOrNull { it.target.episodeId == preferredEpisode } ?: media
            explicitChoiceTarget = choice; versionChoiceWork = id
            val episodeName = choice.detail.lines[choice.lineIndex].episodes[choice.episodeIndex].name
            versionChoiceDescription = if (preferredEpisode != null && choice.target.episodeId != preferredEpisode)
                "原剧集当前不可用，将选择${episodeName}（从 0 开始）"
            else "${episodeName}（从 0 开始，原版本进度保留）"
            message = "播放版本或时间轴尚未核实；可明确选择当前版本从头准备，原版本进度保留"
            false
        }
        catch (_: Exception) { message = "播放资料读取失败，请重试；原记录保持"; false }
    }
    fun togglePlaying() { if (player.isPlaying) player.pause() else player.play(); trace() }
    suspend fun retry() { probe.retry() }
    fun pause() { player.pause(); scope.launch { probe.boundary(); trace() } }
    suspend fun boundary() = probe.boundary()
    fun close() { player.pause(); player.removeListener(listener); probe.close(); player.release() }
    fun trace() {
        File(context.noBackupFilesDir, "round12-personal-trace.json").writeText(JSONObject()
            .put("pid", android.os.Process.myPid()).put("favorites", org.json.JSONArray(favorites.map { it.workId }))
            .put("history", org.json.JSONArray(history.map { it.workId })).put("continue", org.json.JSONArray(continuing.map { it.workId }))
            .put("playing", player.isPlaying).put("playWhenReady", player.playWhenReady).put("position", player.currentPosition)
            .put("resumePosition", requestedResumePosition).put("episode", selectedMedia?.target?.episodeId?.value)
            .put("variant", selectedMedia?.target?.variantId?.value).put("session", player.currentMediaItem?.mediaId)
            .put("message", message).put("atElapsed", SystemClock.elapsedRealtime()).toString())
    }
}
