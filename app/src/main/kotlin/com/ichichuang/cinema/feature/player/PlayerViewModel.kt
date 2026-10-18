package com.ichichuang.cinema.feature.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.ichichuang.cinema.core.design.formatTime
import com.ichichuang.cinema.data.remote.DEFAULT_UA
import com.ichichuang.cinema.data.settings.CinemaSettings
import com.ichichuang.cinema.data.settings.PlaybackProgress
import com.ichichuang.cinema.data.settings.WatchPoint
import com.ichichuang.cinema.domain.model.Episode
import com.ichichuang.cinema.domain.model.PlayLine
import com.ichichuang.cinema.domain.repository.MovieRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

data class PlayerUiState(
    val title: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    /** 临时提示（自动换线路、续播、快进多少秒…），几秒后自己消失 */
    val hint: String? = null,
    /** 提示的"序号"：同一句提示连按多次也能重新计时（Value 相等时 Flow 不会重复发射） */
    val hintNonce: Long = 0L,
    val lines: List<PlayLine> = emptyList(),
    val lineIndex: Int = 0,
    val episodes: List<Episode> = emptyList(),
    val episodeIndex: Int = 0,
    val episodeName: String = "",
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val controlsVisible: Boolean = true,
    /** 「播放设置」面板（菜单键呼出）：步长 / 线路 / 选集 */
    val panelVisible: Boolean = false,
    /** 快进/快退步长（毫秒），用户可在面板里选 10/15/30/60 秒 */
    val seekStepMs: Long = CinemaSettings.DEFAULT_SEEK_STEP_MS,
    /** 每次"有操作"都会刷新这个时间戳，界面靠它重新计时自动隐藏 */
    val controlsShownAt: Long = 0L,
) {
    val hasLines: Boolean get() = lines.isNotEmpty()
    val hasEpisodes: Boolean get() = episodes.size > 1
    val hasPrevEpisode: Boolean get() = episodes.size > 1 && episodeIndex > 0
    val hasNextEpisode: Boolean get() = episodes.size > 1 && episodeIndex < episodes.lastIndex
}

/**
 * 播放器逻辑。
 *
 * 两个刻意的架构决定：
 *  1. **ExoPlayer 由 ViewModel 持有**：屏幕重组/进后台不能重建播放器，
 *     否则会闪一下黑屏、进度还丢了。页面只负责把 player 画出来。
 *  2. **线路失败自动切换**：国内在线片源最常见的问题就是"某条线路挂了"，
 *     与其让用户看到英文报错，不如自己换一条，并用人话告诉他发生了什么。
 *
 * 遥控器键位（v0.8.0 定的"二维导航"模型）：
 *  - ←/→ = **时间轴**：快退 / 快进（幅度可选 10/15/30/60 秒，面板里改）
 *  - ↑/↓ = **剧集轴**：上一集 / 下一集 —— 左右管"片内"、上下管"集间"，
 *    两个方向各管一件事，不用记"长按短按"
 *    （单集影片没有剧集轴，上下键就退化成呼出/收起控件，绝不空按没反应）
 *  - OK = 播放/暂停（长按 = 呼出播放设置，兜底没有菜单键的遥控器）
 *  - 菜单键 = 播放设置面板：步长 / 线路 / 选集
 */
class PlayerViewModel(
    private val repository: MovieRepository,
    private val progress: PlaybackProgress,
    private val settings: CinemaSettings,
    context: Context,
) : ViewModel() {

    /** 播放器本体。页面用 PlayerSurface(player) 画它 */
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSource()))
        .setHandleAudioBecomingNoisy(true)   // 拔耳机/蓝牙断开自动暂停
        .build()

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var movieId: String = ""
    /** 影片来自哪个片源（多源聚合时用）；空 = 单源/演示 */
    private var movieOrigin: String = ""
    private var started = false
    /** 已经自动换过几次线路（防止在多条挂掉的线路之间无限循环） */
    private var autoSwitched = 0
    private var lastSavedMs = 0L

    init {
        // 步长跟着设置走：面板里改一次，以后所有片子都记得
        viewModelScope.launch {
            settings.seekStepMs.collect { ms -> _state.update { it.copy(seekStepMs = ms) } }
        }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(playing = isPlaying) }
                if (!isPlaying) persistProgress()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.update {
                    it.copy(
                        buffering = playbackState == Player.STATE_BUFFERING,
                        loading = it.loading && playbackState == Player.STATE_BUFFERING,
                    )
                }
                if (playbackState == Player.STATE_ENDED) onEpisodeEnded()
            }

            override fun onPlayerError(error: PlaybackException) {
                handlePlaybackError(error)
            }
        })

        // 心跳：500ms 刷新进度条；每 10 秒落一次盘
        viewModelScope.launch {
            while (true) {
                val pos = runCatching { player.currentPosition }.getOrDefault(0L)
                val dur = runCatching { player.duration }.getOrDefault(0L).coerceAtLeast(0L)
                _state.update { it.copy(positionMs = pos, durationMs = dur) }
                if (player.isPlaying && pos - lastSavedMs > 10_000) {
                    lastSavedMs = pos
                    persistProgress()
                }
                delay(500)
            }
        }
    }

    /** 页面上屏时调一次（movieId 变了才重新开始） */
    fun start(movieId: String, fallbackTitle: String, origin: String) {
        if (started && this.movieId == movieId && movieOrigin == origin.trim()) return
        started = true
        this.movieId = movieId
        movieOrigin = origin.trim()
        _state.update { it.copy(title = fallbackTitle, loading = true, error = null) }

        viewModelScope.launch {
            val detail = runCatching { repository.detail(movieId, movieOrigin) }.getOrNull()
            if (detail == null || detail.lines.isEmpty()) {
                _state.update {
                    it.copy(
                        loading = false,
                        buffering = false,
                        error = "这部片暂时没有可用的播放地址",
                    )
                }
                return@launch
            }

            // 新键（带片源）优先；老版本存的"纯 id"键兜底读一次，历史进度不丢
            val saved = runCatching { progress.get(progressKey()) }.getOrNull()
                ?: if (movieOrigin.isNotEmpty()) {
                    runCatching { progress.get(movieId) }.getOrNull()
                } else {
                    null
                }
            val lines = detail.lines
            // 默认就用一条线路：优先上次看过的那条，否则第一条
            val lineIndex = (saved?.lineIndex ?: 0).coerceIn(0, lines.lastIndex)
            val episodes = lines[lineIndex].episodes
            val episodeIndex = (saved?.episodeIndex ?: 0).coerceIn(0, (episodes.size - 1).coerceAtLeast(0))
            val resumeMs = saved?.positionMs ?: 0L

            _state.update {
                it.copy(
                    title = detail.movie.title.ifBlank { fallbackTitle },
                    loading = false,
                    lines = lines,
                    lineIndex = lineIndex,
                    episodes = episodes,
                    episodeIndex = episodeIndex,
                    hint = if (resumeMs > 10_000) "已为你续播 ${formatTime(resumeMs)}" else null,
                )
            }
            playEpisode(episodes.getOrNull(episodeIndex), resumeMs)
        }
    }

    // ── 遥控器操作 ─────────────────────────────────────────────

    fun togglePlay() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            // 播完停在最后一帧时，再按 OK = 从头重看一遍。
            // 不处理的话这一下 play() 没有任何反应，用户会以为遥控器/应用坏了。
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0)
                _state.update { it.copy(positionMs = 0) }
            }
            player.play()
        }
        showControls()
    }

    /** 快退一步（步长 = state.seekStepMs） */
    fun seekBackward() = seekBy(-_state.value.seekStepMs)

    /** 快进一步（步长 = state.seekStepMs） */
    fun seekForward() = seekBy(_state.value.seekStepMs)

    /** 快进/快退：给一句"快进 15 秒"的短提示，观众心里有数 */
    fun seekBy(deltaMs: Long) {
        val dur = player.duration
        var target = player.currentPosition + deltaMs
        if (target < 0) target = 0
        if (dur > 0 && target > dur) target = dur
        player.seekTo(target)
        val seconds = abs(deltaMs) / 1000
        _state.update {
            it.copy(
                positionMs = target,
                hint = (if (deltaMs >= 0) "快进 " else "快退 ") + seconds + " 秒",
                hintNonce = it.hintNonce + 1,
                controlsVisible = true,
                controlsShownAt = System.currentTimeMillis(),
            )
        }
    }

    /** ↑ 上一集（只有多集影片才调用；单集影片由界面降级成"呼出控件"） */
    fun prevEpisode() = jumpEpisode(-1)

    /** ↓ 下一集 */
    fun nextEpisode() = jumpEpisode(1)

    private fun jumpEpisode(delta: Int) {
        val s = _state.value
        if (s.episodes.size <= 1) return
        val target = s.episodeIndex + delta
        if (target !in s.episodes.indices) {
            _state.update {
                it.copy(
                    hint = if (delta > 0) "已经是最后一集" else "已经是第一集",
                    hintNonce = it.hintNonce + 1,
                    controlsVisible = true,
                    controlsShownAt = System.currentTimeMillis(),
                )
            }
            return
        }
        val episode = s.episodes[target]
        _state.update {
            it.copy(
                episodeIndex = target,
                panelVisible = false,
                hint = (if (delta > 0) "下一集" else "上一集") + " · " + episode.name,
                hintNonce = it.hintNonce + 1,
                controlsVisible = true,
                controlsShownAt = System.currentTimeMillis(),
            )
        }
        playEpisode(episode, 0L)
    }

    /** 长按 OK / 按菜单键：呼出或收起播放设置面板 */
    fun togglePanel() {
        _state.update {
            it.copy(
                panelVisible = !it.panelVisible,
                controlsVisible = true,
                controlsShownAt = System.currentTimeMillis(),
            )
        }
    }

    fun hidePanel() = _state.update { it.copy(panelVisible = false) }

    /** 面板里选快进/快退幅度：立即生效 + 落盘（下次开机还是它） */
    fun setSeekStep(ms: Long) {
        _state.update {
            it.copy(
                seekStepMs = ms,
                hint = "快进/快退已设为 " + (ms / 1000) + " 秒",
                hintNonce = it.hintNonce + 1,
                controlsShownAt = System.currentTimeMillis(),
            )
        }
        viewModelScope.launch { runCatching { settings.setSeekStep(ms) } }
    }

    fun selectEpisode(index: Int) {
        val s = _state.value
        val ep = s.episodes.getOrNull(index) ?: return
        _state.update {
            it.copy(
                episodeIndex = index,
                panelVisible = false,
                controlsShownAt = System.currentTimeMillis(),
            )
        }
        playEpisode(ep, 0L)
    }

    fun selectLine(index: Int) {
        val s = _state.value
        if (s.lines.getOrNull(index) == null) return
        autoSwitched = 0
        // 手动换线路：带着当前进度换过去，"像没换过一样"接着看
        switchToLine(index, keepEpisodeName = s.episodeName, resumeMs = s.positionMs)
        _state.update {
            it.copy(
                lineIndex = index,
                panelVisible = false,
                // 线路名字对用户没有意义（他只知道"这条能播"），提示说人话就行
                hint = "已切换线路",
                hintNonce = it.hintNonce + 1,
                controlsShownAt = System.currentTimeMillis(),
            )
        }
    }

    fun showControls() = _state.update {
        it.copy(controlsVisible = true, controlsShownAt = System.currentTimeMillis())
    }

    fun hideControls() {
        val s = _state.value
        if (s.panelVisible || s.error != null) return
        _state.update { it.copy(controlsVisible = false) }
    }

    fun clearHint() = _state.update { it.copy(hint = null) }

    fun retry() {
        val s = _state.value
        _state.update { it.copy(error = null) }
        playEpisode(s.episodes.getOrNull(s.episodeIndex), 0L)
    }

    /** 离开播放页：暂停并立刻落盘 */
    fun pauseAndSave() {
        runCatching { player.pause() }
        persistProgress()
    }

    override fun onCleared() {
        runCatching { player.pause() }
        runCatching { player.release() }
        super.onCleared()
    }

    // ── 内部 ───────────────────────────────────────────────────

    /** 进度的键：和首页 Movie.uniqueKey 同一套写法（带来源，防不同站 id 撞车） */
    private fun progressKey(): String =
        if (movieOrigin.isEmpty()) movieId else "$movieOrigin#$movieId"

    private fun playEpisode(episode: Episode?, resumeMs: Long) {
        if (episode == null) {
            _state.update { it.copy(error = "这一集没有播放地址", buffering = false) }
            return
        }
        val item = MediaItem.Builder()
            .setUri(episode.url)
            .setMediaId(episode.name)
            .build()
        runCatching {
            player.setMediaItem(item)
            if (resumeMs > 5_000) player.seekTo(resumeMs)
            player.prepare()
            player.playWhenReady = true
        }.onFailure {
            _state.update { s -> s.copy(error = "播放器出错：${it.message ?: it.javaClass.simpleName}") }
            return
        }
        _state.update {
            it.copy(
                episodeName = episode.name,
                error = null,
                buffering = true,
                positionMs = resumeMs,
                controlsVisible = true,
                controlsShownAt = System.currentTimeMillis(),
            )
        }
    }

    /** 线路失败：自动试下一条（用户无感），全试完才报错 */
    private fun handlePlaybackError(error: PlaybackException) {
        val s = _state.value
        if (s.lines.size > 1 && autoSwitched < s.lines.size - 1) {
            autoSwitched++
            val next = (s.lineIndex + 1) % s.lines.size
            // 自动容错换线路也带着进度走：卡一下不该让用户从头看
            switchToLine(next, keepEpisodeName = s.episodeName, resumeMs = s.positionMs)
            _state.update {
                it.copy(
                    lineIndex = next,
                    hint = "这条线放不动，已自动换了一条",
                    hintNonce = it.hintNonce + 1,
                )
            }
            return
        }
        _state.update {
            it.copy(
                error = friendlyError(error),
                buffering = false,
                controlsVisible = true,
            )
        }
    }

    /** 换线路时尽量停在"同一集 + 同一个位置"（线路之间的集名通常一致） */
    private fun switchToLine(index: Int, keepEpisodeName: String, resumeMs: Long = 0L) {
        val s = _state.value
        val episodes = s.lines.getOrNull(index)?.episodes ?: return
        val epIndex = episodes.indexOfFirst { it.name == keepEpisodeName }.takeIf { it >= 0 }
            ?: s.episodeIndex.coerceIn(0, (episodes.size - 1).coerceAtLeast(0))
        _state.update { it.copy(episodes = episodes, episodeIndex = epIndex) }
        // 关键：把"看到哪儿了"带过去。以前这里写的是 0L ——
        // 手动换条线路就等于把进度清零、"继续观看"直接消失，这是很伤的动作。
        playEpisode(episodes.getOrNull(epIndex), resumeMs)
    }

    private fun onEpisodeEnded() {
        val s = _state.value
        val next = s.episodes.getOrNull(s.episodeIndex + 1)
        if (next != null) {
            // 连播下一集：这是"追剧"场景，不该让用户手动去点
            _state.update { it.copy(episodeIndex = s.episodeIndex + 1) }
            playEpisode(next, 0L)
        } else {
            // 最后一集放完 = 全片看完：进度归零（下次从头看），只留一句人话
            viewModelScope.launch {
                runCatching { progress.save(progressKey(), WatchPoint(s.lineIndex, 0, 0L, s.durationMs)) }
            }
            lastSavedMs = 0L
            _state.update {
                it.copy(
                    // 电影（单集）不说"最后一集"——那是剧集的说法
                    hint = if (s.episodes.size > 1) "已经播完最后一集了" else "播放结束",
                    hintNonce = it.hintNonce + 1,
                    controlsVisible = true,
                    controlsShownAt = System.currentTimeMillis(),
                )
            }
        }
    }

    private fun persistProgress(forceMs: Long? = null) {
        val s = _state.value
        if (movieId.isBlank()) return
        val pos = forceMs ?: runCatching { player.currentPosition }.getOrDefault(s.positionMs)
        val dur = runCatching { player.duration }.getOrDefault(0L).coerceAtLeast(0L)
        viewModelScope.launch {
            runCatching {
                progress.save(progressKey(), WatchPoint(s.lineIndex, s.episodeIndex, pos, dur))
            }
        }
    }

    /**
     * 把 ExoPlayer 的英文错误码翻译成"电视用户能看懂的一句话"。
     * 顺带带上 UA/Referer —— 国内片源站经常靠这两个放行。
     */
    private fun httpDataSource(): DefaultHttpDataSource.Factory =
        DefaultHttpDataSource.Factory()
            .setUserAgent(DEFAULT_UA)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)

    private fun friendlyError(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        -> "连不上播放地址：线路可能挂了，或需要换线路"

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        -> "播放地址返回错误（${e.errorCodeName}）：这条线路的地址已失效"

        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        -> "视频格式不支持：这个地址可能不是有效的视频流"

        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        -> "这台电视解不了这个视频编码（常见于 H.265/AV1 片源）"

        else -> "播放失败（${e.errorCodeName}）"
    }
}
