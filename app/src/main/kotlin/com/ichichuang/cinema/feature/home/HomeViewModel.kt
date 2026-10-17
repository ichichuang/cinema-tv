package com.ichichuang.cinema.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichichuang.cinema.domain.model.Movie
import com.ichichuang.cinema.domain.model.MovieDetail
import com.ichichuang.cinema.domain.repository.CATEGORY_ALL
import com.ichichuang.cinema.domain.repository.MovieRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 首页状态。单一数据源：UI 只读它，所有变化由 ViewModel 产生。
 */
data class HomeUiState(
    val categories: List<String> = emptyList(),
    val category: String = CATEGORY_ALL,
    val movies: List<Movie> = emptyList(),
    /** 当前焦点所在影片（右侧详情面板显示的就是它） */
    val selected: Movie? = null,
    /** 选中影片的详情（简介 + 线路），焦点停住后异步到货 */
    val detail: MovieDetail? = null,
    val detailLoading: Boolean = false,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    /** 还没配置影视接口地址 → UI 显示引导，而不是报错 */
    val needsSetup: Boolean = false,
    val error: String? = null,
)

/**
 * 首页逻辑。
 *
 * 它只依赖 MovieRepository 接口 —— 换片源格式、换实现，这个类一行都不用改。
 */
class HomeViewModel(private val repository: MovieRepository) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var page = 0
    private var job: Job? = null
    private var detailJob: Job? = null

    /** 详情缓存：焦点来回移动时不重复打接口（32 位电视内存紧，所以有上限） */
    private val detailCache = LinkedHashMap<String, MovieDetail>()

    init {
        // 接口地址变化（第一次填 / 用户改了）→ 自动全部重载，不需要重启应用
        viewModelScope.launch {
            repository.config.collect { addr ->
                _state.update { it.copy(needsSetup = addr.isBlank()) }
                reloadAll()
            }
        }
    }

    fun selectCategory(category: String) {
        if (_state.value.category == category) return
        _state.update { it.copy(category = category) }
        reload()
    }

    /** 焦点落到某部片上：立刻切换右侧详情，并（防抖后）拉详情 */
    fun onMovieFocused(movie: Movie) {
        if (_state.value.selected?.id == movie.id) return
        _state.update {
            it.copy(
                selected = movie,
                detail = detailCache[movie.id],
                detailLoading = detailCache[movie.id] == null,
            )
        }
        loadDetail(movie)
    }

    /**
     * 网格滚动到接近底部时调用（UI 观察滚动位置触发）。
     * 阈值逻辑放在这里，UI 不需要懂"什么时候该加载下一页"。
     */
    fun onVisibleIndexChanged(lastVisibleIndex: Int) {
        val s = _state.value
        if (s.loading || s.loadingMore || s.endReached) return
        if (lastVisibleIndex >= s.movies.size - 6) {
            loadNextPage()
        }
    }

    fun retry() = reloadAll()

    // ── 内部 ───────────────────────────────────────────────────

    private fun reloadAll() {
        loadCategories()
        reload()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            runCatching { repository.categories() }
                .onSuccess { list ->
                    _state.update { s ->
                        s.copy(
                            categories = list,
                            category = if (list.isEmpty() || s.category in list) s.category else CATEGORY_ALL,
                        )
                    }
                }
        }
    }

    private fun reload() {
        job?.cancel()
        page = 0
        _state.update {
            it.copy(
                loading = true,
                movies = emptyList(),
                selected = null,
                detail = null,
                detailLoading = false,
                endReached = false,
                error = null,
            )
        }
        if (_state.value.needsSetup) {
            _state.update { it.copy(loading = false) }
            return
        }
        job = viewModelScope.launch {
            runCatching { repository.movies(_state.value.category, 1) }
                .onSuccess { list ->
                    page = 1
                    _state.update {
                        it.copy(
                            loading = false,
                            movies = list,
                            selected = list.firstOrNull(),
                            endReached = list.isEmpty(),
                        )
                    }
                    list.firstOrNull()?.let { loadDetail(it) }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "加载失败") }
                }
        }
    }

    private fun loadNextPage() {
        val s = _state.value
        if (s.needsSetup) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            runCatching { repository.movies(s.category, page + 1) }
                .onSuccess { list ->
                    page += 1
                    _state.update {
                        it.copy(
                            loadingMore = false,
                            movies = it.movies + list,
                            endReached = list.isEmpty(),
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(loadingMore = false) }
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
                        _state.update { it.copy(detailLoading = false) }
                        return@onSuccess
                    }
                    if (detailCache.size > MAX_DETAIL_CACHE) detailCache.clear()
                    detailCache[movie.id] = detail
                    // 焦点可能已经移走了，只在还停在它身上时才更新 UI
                    if (_state.value.selected?.id == movie.id) {
                        _state.update { it.copy(detail = detail, detailLoading = false) }
                    }
                }
                .onFailure {
                    _state.update { it.copy(detailLoading = false) }
                }
        }
    }

    private companion object {
        const val MAX_DETAIL_CACHE = 60
    }
}
