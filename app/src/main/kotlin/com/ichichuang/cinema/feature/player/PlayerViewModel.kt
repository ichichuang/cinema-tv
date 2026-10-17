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

data class PlayerUiState(
    val title: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    /** 临时提示（自动换线路、续播等），几秒后自己消失 */
    val hint: String? = null,
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
    val listVisible: Boolean = false,
    /** 每次"有操作"都会刷新这个时间戳，界面靠它重新计时自动隐藏 */
    val controlsShownAt: Long = 0L,
) {
    val hasLines: Boolean get() = lines.isNotEmpty()
    val hasEpisodes: Boolean get() = episodes.size > 1
}

/**
 * 播放器逻辑。
 *
 * 两个刻意的架构决定：
 *  1. **ExoPlayer 由 ViewModel 持有**：屏幕重组/进后台不能重建播放器，
 *     否则会闪一下黑屏、进度还丢了。页面只负责把 player 画出来。
 *  2. **线路失败自动切换**：国内在线片源最常见的问题就是"某条线路挂了"，
 *     与其让用户看到英文报错，不如自己换一条，并用人话告诉他发生了什么。
 */
class PlayerViewModel(
    private val repository: MovieRepository,
    private val progress: PlaybackProgress,
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
    private var started = false
    /** 已经自动换过几次线路（防止在多条挂掉的线路之间无限循环） */
    private var autoSwitched = 0
    private var lastSavedMs = 0L

    init {
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
    fun start(movieId: String, fallbackTitle: String) {
        if (started && this.movieId == movieId) return
        started = true
        this.movieId = movieId
        _state.update { it.copy(title = fallbackTitle, loading = true, error = null) }

        viewModelScope.launch {
            val detail = runCatching { repository.detail(movieId) }.getOrNull()
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

            val saved = runCatching { progress.get(movieId) }.getOrNull()
            val lines = detail.lines
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
            player.play()
        }
        showControls()
    }

    fun seekBy(deltaMs: Long) {
        val dur = player.duration
        var target = player.currentPosition + deltaMs
        if (target < 0) target = 0
        if (dur > 0 && target > dur) target = dur
        player.seekTo(target)
        _state.update { it.copy(positionMs = target) }
        showControls()
    }

    fun selectEpisode(index: Int) {
        val s = _state.value
        val ep = s.episodes.getOrNull(index) ?: return
        _state.update {
            it.copy(
                episodeIndex = index,
                listVisible = false,
                controlsShownAt = System.currentTimeMillis(),
            )
        }
        playEpisode(ep, 0L)
    }

    fun selectLine(index: Int) {
        val s = _state.value
        val line = s.lines.getOrNull(index) ?: return
        autoSwitched = 0
        // 手动换线路：带着当前进度换过去，"像没换过一样"接着看
        switchToLine(index, keepEpisodeName = s.episodeName, resumeMs = s.positionMs)
        _state.update {
            it.copy(
                lineIndex = index,
                listVisible = false,
                hint = "已切到「${line.name}」",
                controlsShownAt = System.currentTimeMillis(),
            )
        }
    }

    fun toggleList() {
        val s = _state.value
        if (!s.hasEpisodes && s.lines.size <= 1 && !s.hasLines) return
        _state.update { it.copy(listVisible = !it.listVisible) }
    }

    fun hideList() = _state.update { it.copy(listVisible = false) }

    fun showControls() = _state.update {
        it.copy(controlsVisible = true, controlsShownAt = System.currentTimeMillis())
    }

    fun hideControls() {
        val s = _state.value
        if (s.listVisible || s.error != null) return
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
                    hint = "「${s.lines.getOrNull(s.lineIndex)?.name ?: "线路"}」播不动，已自动换到「${s.lines[next].name}」",
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
            _state.update { it.copy(episodeIndex = s.episodeIndex + 1) }
            playEpisode(next, 0L)
        } else {
            _state.update {
                it.copy(error = "这条线路已经播完了", controlsVisible = true)
            }
        }
    }

    private fun persistProgress() {
        val s = _state.value
        if (movieId.isBlank()) return
        val pos = runCatching { player.currentPosition }.getOrDefault(s.positionMs)
        val dur = runCatching { player.duration }.getOrDefault(0L).coerceAtLeast(0L)
        viewModelScope.launch {
            runCatching {
                progress.save(movieId, WatchPoint(s.lineIndex, s.episodeIndex, pos, dur))
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
