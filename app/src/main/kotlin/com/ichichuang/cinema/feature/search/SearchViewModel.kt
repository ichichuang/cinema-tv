package com.ichichuang.cinema.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichichuang.cinema.data.settings.PlaybackProgress
import com.ichichuang.cinema.domain.model.Movie
import com.ichichuang.cinema.domain.model.MovieDetail
import com.ichichuang.cinema.domain.repository.MovieRepository
import com.ichichuang.cinema.domain.repository.SearchHistoryStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    /** 输入框里正在编辑的内容 */
    val query: String = "",
    /** 真正搜出去的那个词（结果和它对应） */
    val submitted: String = "",
    val results: List<Movie> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    /** 最近搜过的词，最新的在最前 */
    val history: List<String> = emptyList(),
    /** 焦点所在的那部（右侧详情显示的就是它） */
    val selected: Movie? = null,
    val detail: MovieDetail? = null,
    val detailLoading: Boolean = false,
    val resumeMs: Long = 0L,
)

/**
 * 搜索逻辑。
 *
 * 三个刻意的取舍：
 *  1. **不边打边搜**：电视上打字要过输入法（拼音拼字过程中全是半成品），
 *     逐字发请求既没用又费流量 —— 按「搜索」键（或输入法的搜索键）才真的搜。
 *  2. **历史必须留**：遥控器打字是全应用最贵的操作，搜过的词一键重搜。
 *  3. **详情/进度走同一套**：和首页一样，焦点停在哪部就显示哪部的详情和"继续观看"。
 */
class SearchViewModel(
    private val repository: MovieRepository,
    private val progress: PlaybackProgress,
    private val historyStore: SearchHistoryStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var detailJob: Job? = null
    private val detailCache = LinkedHashMap<String, MovieDetail>()

    /** 一页够用：用户搜的是具体片名，翻二十几条已经很少见；也避免 32 位电视越攒越占内存 */
    private val pageSize = 24
    private val maxHistory = 8

    init {
        viewModelScope.launch {
            val items = runCatching { historyStore.load() }.getOrDefault(emptyList())
            _state.update { it.copy(history = items) }
        }
    }

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    /** 点最近搜索里的词：词填回输入框（用户看得见搜的是什么），然后直接搜 */
    fun searchHistory(word: String) {
        _state.update { it.copy(query = word) }
        submit()
    }

    fun submit() {
        val word = _state.value.query.trim()
        if (word.isEmpty()) return
        if (_state.value.loading && word == _state.value.submitted) return

        _state.update {
            it.copy(
                submitted = word,
                loading = true,
                error = null,
                results = emptyList(),
                selected = null,
                detail = null,
                detailLoading = false,
                resumeMs = 0L,
            )
        }
        rememberWord(word)

        viewModelScope.launch {
            runCatching { repository.search(word, 1, pageSize) }
                .onSuccess { list ->
                    val first = list.firstOrNull()
                    _state.update {
                        it.copy(
                            loading = false,
                            results = list,
                            selected = first,
                            detail = first?.let { movie -> detailCache[movie.id] },
                            detailLoading = first != null && detailCache[first.id] == null,
                        )
                    }
                    first?.let { movie ->
                        loadProgress(movie.id)
                        loadDetail(movie)
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, results = emptyList(), error = e.message ?: "搜索失败")
                    }
                }
        }
    }

    fun retry() {
        if (_state.value.submitted.isBlank()) return
        _state.update { it.copy(query = it.submitted) }
        submit()
    }

    fun clearHistory() {
        _state.update { it.copy(history = emptyList()) }
        viewModelScope.launch { runCatching { historyStore.save(emptyList()) } }
    }

    /** 焦点落到某部片上：切换右侧详情，并（防抖后）拉详情 */
    fun onMovieFocused(movie: Movie) {
        if (_state.value.selected?.id == movie.id) return
        _state.update {
            it.copy(
                selected = movie,
                detail = detailCache[movie.id],
                detailLoading = detailCache[movie.id] == null,
                resumeMs = 0L,   // 先不显示上一部片的进度
            )
        }
        loadProgress(movie.id)
        loadDetail(movie)
    }

    /** 回到这个页面时刷一次进度：按钮会变成"继续观看 12:34" */
    fun refreshSelectedProgress() {
        val selected = _state.value.selected ?: return
        loadProgress(selected.id)
    }

    // ── 内部 ───────────────────────────────────────────────────

    private fun rememberWord(word: String) {
        val next = (listOf(word) + _state.value.history.filter { it != word }).take(maxHistory)
        _state.update { it.copy(history = next) }
        viewModelScope.launch { runCatching { historyStore.save(next) } }
    }

    /** 读观看进度：离结尾不到 1 分钟就不显示"继续观看"，当成看完 */
    private fun loadProgress(movieId: String) {
        viewModelScope.launch {
            val point = runCatching { progress.get(movieId) }.getOrNull()
            val resume = point?.let {
                val nearEnd = it.durationMs > 0 && it.positionMs > it.durationMs - 60_000
                if (nearEnd) 0L else it.positionMs
            } ?: 0L
            if (_state.value.selected?.id == movieId) {
                _state.update { it.copy(resumeMs = resume) }
            }
        }
    }

    /** 拉详情：焦点停住 250ms 才发请求，避免快速划过时打一串无用请求 */
    private fun loadDetail(movie: Movie) {
        detailCache[movie.id]?.let { return }
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            delay(250)
            runCatching { repository.detail(movie.id) }
                .onSuccess { detail ->
                    if (detail == null) {
                        if (_state.value.selected?.id == movie.id) {
                            _state.update { it.copy(detailLoading = false) }
                        }
                        return@onSuccess
                    }
                    // 缓存上限：32 位电视内存紧，满了整体丢掉重来（比维护 LRU 简单可靠）
                    if (detailCache.size > MAX_DETAIL_CACHE) detailCache.clear()
                    detailCache[movie.id] = detail
                    if (_state.value.selected?.id == movie.id) {
                        _state.update { it.copy(detail = detail, detailLoading = false) }
                    }
                }
                .onFailure {
                    if (_state.value.selected?.id == movie.id) {
                        _state.update { it.copy(detailLoading = false) }
                    }
                }
        }
    }

    private companion object {
        const val MAX_DETAIL_CACHE = 60
    }
}
