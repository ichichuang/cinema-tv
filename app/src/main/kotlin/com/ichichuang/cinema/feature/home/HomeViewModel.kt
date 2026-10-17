package com.ichichuang.cinema.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichichuang.cinema.data.settings.PlaybackProgress
import com.ichichuang.cinema.domain.model.DefaultMovieFilter
import com.ichichuang.cinema.domain.model.Movie
import com.ichichuang.cinema.domain.model.MovieDetail
import com.ichichuang.cinema.domain.model.MovieFilter
import com.ichichuang.cinema.domain.repository.CATEGORY_ALL
import com.ichichuang.cinema.domain.repository.FILTER_ALL
import com.ichichuang.cinema.domain.repository.FilterStore
import com.ichichuang.cinema.domain.repository.MovieRepository
import com.ichichuang.cinema.domain.repository.yearOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 首页状态。单一数据源：UI 只读它，所有变化由 ViewModel 产生。
 */
data class HomeUiState(
    val categories: List<String> = emptyList(),
    /** 当前筛选条件（分类 + 语言 + 地区 + 年份） */
    val filter: MovieFilter = DefaultMovieFilter,
    val movies: List<Movie> = emptyList(),
    /** 当前筛选下共有多少部（接口没返回就是 0，UI 会隐藏不显示假的数字） */
    val total: Int = 0,
    /** 筛条件被放宽时的说明（人话），没有就是 null */
    val relaxedNote: String? = null,
    /** 每次重载 +1：海报墙靠它判断"这批是新的"，播交错入场动画 */
    val gridGeneration: Int = 0,
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
    /** 上次看到哪了（毫秒）。>0 时播放按钮变成"继续观看 12:34" */
    val resumeMs: Long = 0L,
    /** 年份选项（按当前年份生成，接口只支持具体年份） */
    val yearOptions: List<String> = emptyList(),
)

/**
 * 首页逻辑。
 *
 * 它只依赖 domain 的接口 —— 换片源格式、换筛选实现，这个类都不用动。
 */
class HomeViewModel(
    private val repository: MovieRepository,
    private val progress: PlaybackProgress,
    private val filterStore: FilterStore,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var page = 0
    private var job: Job? = null
    private var detailJob: Job? = null

    /** 详情缓存：焦点来回移动时不重复打接口（32 位电视内存紧，所以有上限） */
    private val detailCache = LinkedHashMap<String, MovieDetail>()

    init {
        _state.update {
            it.copy(yearOptions = yearOptions(Calendar.getInstance().get(Calendar.YEAR)))
        }
        viewModelScope.launch {
            // 先恢复上次的筛选（第一次运行 = 默认国语），再开始跟接口地址的变化
            val saved = runCatching { filterStore.load() }.getOrNull()
            if (saved != null) _state.update { it.copy(filter = saved) }

            // 接口地址变化（第一次填 / 用户改了）→ 自动全部重载，不需要重启应用
            repository.config.collect { addr ->
                _state.update { it.copy(needsSetup = addr.isBlank()) }
                reloadAll()
            }
        }
    }

    // ── 筛选（界面上传的是选项文字，"全部" 转成空串表示不限制）──────

    fun selectCategory(category: String) =
        applyFilter(_state.value.filter.copy(category = category))

    fun selectLang(option: String) =
        applyFilter(_state.value.filter.copy(lang = option.toFilterValue()))

    fun selectArea(option: String) =
        applyFilter(_state.value.filter.copy(area = option.toFilterValue()))

    fun selectYear(option: String) =
        applyFilter(_state.value.filter.copy(year = option.toFilterValue()))

    /** 一键回到默认：全部分类 + 国语 */
    fun resetFilters() = applyFilter(DefaultMovieFilter)

    private fun applyFilter(next: MovieFilter) {
        if (next == _state.value.filter) return
        _state.update { it.copy(filter = next) }
        viewModelScope.launch { runCatching { filterStore.save(next) } }   // 记住这次选择
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
                resumeMs = 0L,   // 先不显示上次的进度，等读到真的再说（避免显示上一部片的）
            )
        }
        loadProgress(movie.id)
        loadDetail(movie)
    }

    /**
     * 首页重新可见时刷新一次（从播放器返回时靠它把按钮变成"继续观看 12:34"）。
     * 首页在导航栈底部：去播放器时它离开组合，回来时重新进入 → 正好触发一次。
     */
    fun refreshSelectedProgress() {
        _state.value.selected?.let { loadProgress(it.id) }
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

    private fun String.toFilterValue(): String = if (this == FILTER_ALL) "" else this

    private fun reloadAll() {
        loadCategories()
        reload()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            runCatching { repository.categories() }
                .onSuccess { list ->
                    _state.update { s ->
                        // 分类树变了（换了片源站）→ 当前分类不在新列表里就静默回到"全部"
                        if (list.isEmpty() || s.filter.category in list) {
                            s.copy(categories = list)
                        } else {
                            s.copy(categories = list, filter = s.filter.copy(category = CATEGORY_ALL))
                        }
                    }
                }
        }
    }

    private fun reload() {
        job?.cancel()
        page = 0
        val filter = _state.value.filter
        _state.update {
            it.copy(
                loading = true,
                movies = emptyList(),
                selected = null,
                detail = null,
                detailLoading = false,
                endReached = false,
                error = null,
                resumeMs = 0L,
                relaxedNote = null,
                gridGeneration = it.gridGeneration + 1,   // 海报墙：这一批是新数据，播入场动画
            )
        }
        if (_state.value.needsSetup) {
            _state.update { it.copy(loading = false) }
            return
        }
        job = viewModelScope.launch {
            runCatching { repository.movies(filter, 1) }
                .onSuccess { result ->
                    page = 1
                    // 分类被接口改了（比如换了片源站）→ 回退到"全部"，避免分类对不上
                    val cats = _state.value.categories
                    val fixedFilter = if (cats.isNotEmpty() && filter.category !in cats) {
                        filter.copy(category = CATEGORY_ALL)
                    } else {
                        filter
                    }
                    _state.update {
                        it.copy(
                            loading = false,
                            filter = fixedFilter,
                            movies = result.movies,
                            total = result.total,
                            relaxedNote = if (result.relaxed) relaxedText(fixedFilter) else null,
                            selected = result.movies.firstOrNull(),
                            endReached = result.movies.isEmpty(),
                        )
                    }
                    result.movies.firstOrNull()?.let { loadDetail(it) }
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
            runCatching { repository.movies(s.filter, page + 1) }
                .onSuccess { result ->
                    page += 1
                    _state.update {
                        it.copy(
                            loadingMore = false,
                            movies = it.movies + result.movies,
                            endReached = result.movies.isEmpty(),
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(loadingMore = false) }
                }
        }
    }

    /** 筛条件被放宽时给一句人话 —— 用户必须知道"为什么结果跟我要的不一样" */
    private fun relaxedText(filter: MovieFilter): String {
        val asked = filter.activeParts.joinToString("·")
        return "本站片源缺少「$asked」的标注，已自动放开筛选，先把片子给你看"
    }

    /** 读观看进度：快看完了（离结尾不到 1 分钟）就不显示"继续观看"，当成看完 */
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
