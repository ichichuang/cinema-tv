package com.ichichuang.cinema.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichichuang.cinema.data.settings.CinemaSettings
import com.ichichuang.cinema.data.settings.PlaybackProgress
import com.ichichuang.cinema.domain.model.DefaultMovieFilter
import com.ichichuang.cinema.domain.model.Movie
import com.ichichuang.cinema.domain.model.MovieDetail
import com.ichichuang.cinema.domain.model.MovieFilter
import com.ichichuang.cinema.domain.repository.BrowseMemory
import com.ichichuang.cinema.domain.repository.BrowseStore
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
    /**
     * 一次性的"请把焦点挪到这部片"请求（处理完 UI 会回报 consumed）。
     *
     * 为什么要这么绕：焦点是 UI 的东西，但"焦点该在哪"是状态。
     * 如果直接用 selected 驱动焦点，用户每按一次右键都会被"重新聚焦一次"；
     * 所以只在真正需要回显的时刻（从播放器/设置返回、应用重启、换了片源）发这个信号。
     */
    val focusTarget: String? = null,
    /** 选中影片的详情（简介 + 线路），焦点停住后异步到货 */
    val detail: MovieDetail? = null,
    val detailLoading: Boolean = false,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    /** 翻页失败时的一句人话（不打断观看，只在列表底部说明会重试） */
    val pageError: String? = null,
    /** 还没配置影视接口地址 → UI 显示引导，而不是报错 */
    val needsSetup: Boolean = false,
    val error: String? = null,
    /** 上次看到哪了（毫秒）。>0 时播放按钮变成"继续观看 12:34" */
    val resumeMs: Long = 0L,
    /** 年份选项（按当前年份生成，接口只支持具体年份） */
    val yearOptions: List<String> = emptyList(),
    /** 首页每行海报数（设置里可选 4..8，默认 5） */
    val columns: Int = 5,
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
    private val browseStore: BrowseStore,
    private val settings: CinemaSettings,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var page = 0
    private var job: Job? = null
    private var detailJob: Job? = null
    private var browseSaveJob: Job? = null

    /** 从磁盘读回来的浏览位置，用在**第一次**加载结果上（用完即清，之后换筛选不受它影响） */
    private var pendingRestore: BrowseMemory? = null

    /** 详情缓存：焦点来回移动时不重复打接口（32 位电视内存紧，所以有上限） */
    private val detailCache = LinkedHashMap<String, MovieDetail>()

    init {
        _state.update {
            it.copy(yearOptions = yearOptions(Calendar.getInstance().get(Calendar.YEAR)))
        }
        viewModelScope.launch {
            // 每行海报数：设置里一改，海报墙立刻按新列数重排
            settings.homeColumns.collect { cols -> _state.update { it.copy(columns = cols) } }
        }
        viewModelScope.launch {
            // 先恢复上次的筛选（第一次运行 = 默认国语）和上次看到哪了，再开始跟接口地址的变化。
            // 顺序很重要：必须在 collect（会触发第一次加载）之前读完，否则第一次加载用不上记忆。
            val saved = runCatching { filterStore.load() }.getOrNull()
            if (saved != null) _state.update { it.copy(filter = saved) }
            pendingRestore = runCatching { browseStore.load() }.getOrNull()

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

    /**
     * 应用一份筛选（筛选浮层点「完成」时调）。
     * 直接改成 public：浮层里是攒一批改动一起提交，不适合一个个 setter 地调。
     */
    fun applyFilter(next: MovieFilter) {
        if (next == _state.value.filter) return
        _state.update { it.copy(filter = next) }
        viewModelScope.launch { runCatching { filterStore.save(next) } }   // 记住这次选择
        // 换筛选**不抢焦点**：用户这会儿手还在筛选条上，焦点跳进海报墙是耍流氓
        reload(restoreFocus = false)
    }

    /** 焦点落到某部片上：立刻切换右侧详情，并（防抖后）拉详情 */
    fun onMovieFocused(movie: Movie) {
        if (_state.value.selected?.uniqueKey == movie.uniqueKey) return
        _state.update {
            it.copy(
                selected = movie,
                detail = detailCache[movie.uniqueKey],
                detailLoading = detailCache[movie.uniqueKey] == null,
                resumeMs = 0L,   // 先不显示上次的进度，等读到真的再说（避免显示上一部片的）
            )
        }
        loadProgress(movie)
        loadDetail(movie)
        scheduleBrowseSave(movie)
    }

    /**
     * 首页每次"上屏"都会调一次（首次进入 / 从播放器或设置返回）。
     *
     * 这是"来回切换后操作回显"的关键：把焦点还给上次看的那部片。
     * 不这么做的话焦点会落到第一张海报上 —— 右侧详情跟着变成别的片、
     * 列表还被自动滚回顶部，用户会觉得"我刚才翻的全没了"。
     */
    fun onHomeVisible() {
        val key = _state.value.selected?.uniqueKey ?: return
        _state.update { it.copy(focusTarget = key) }
    }

    /**
     * 详情页打开时调一次（详情页和首页共用这一个 ViewModel）。
     *
     * 两个用处：
     *  ① 正常流程：卡片被聚焦时详情/进度已经拉过了，这里基本是空跑（缓存命中，不发请求）
     *  ② 冷启动直接恢复到详情页（进程被杀后重启）：列表还没回来，
     *     先用路由参数把画面顶住，等列表/详情回来再补上真实数据
     */
    fun onDetailOpened(movieId: String, fallbackTitle: String, fallbackPoster: String?) {
        val known = _state.value.movies.firstOrNull { it.id == movieId }
            ?: _state.value.selected?.takeIf { it.id == movieId }
        val movie = known ?: Movie(
            id = movieId,
            title = fallbackTitle,
            year = 0,
            score = 0.0,
            genre = "",
            minutes = 0,
            synopsis = "",
            posterUrl = fallbackPoster,
        )
        if (_state.value.selected?.uniqueKey != movie.uniqueKey) {
            _state.update {
                it.copy(
                    selected = movie,
                    detail = detailCache[movie.uniqueKey],
                    detailLoading = detailCache[movie.uniqueKey] == null,
                    resumeMs = 0L,
                )
            }
        }
        loadProgress(movie)
        loadDetail(movie)
    }

    /** UI 已经把焦点挪到位了（或发现目标不存在）—— 清掉一次性请求 */
    fun onFocusTargetConsumed() {
        if (_state.value.focusTarget == null) return
        _state.update { it.copy(focusTarget = null) }
    }

    /**
     * 首页重新可见时刷新一次（从播放器返回时靠它把按钮变成"继续观看 12:34"）。
     * 首页在导航栈底部：去播放器时它离开组合，回来时重新进入 → 正好触发一次。
     */
    fun refreshSelectedProgress() {
        _state.value.selected?.let { loadProgress(it) }
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
        reload(restoreFocus = true)
    }

    /**
     * 焦点回显：优先按 movieId 找（翻页顺序变了、接口换了都不怕），找不到再按位置兜底。
     * 用完即清 —— 只有"第一次加载"该回到老位置；用户换筛选后应该从新结果的第一张开始。
     */
    private fun takeRestoreTarget(movies: List<Movie>): Movie? {
        val memory = pendingRestore ?: return null
        pendingRestore = null
        return memory.movieId?.let { key ->
            // 新格式存的是 uniqueKey（片源#id）；老版本存的纯 id 也认得
            movies.firstOrNull { it.uniqueKey == key } ?: movies.firstOrNull { it.id == key }
        } ?: movies.getOrNull(memory.index)
    }

    /** 焦点停住 700ms 才落盘：遥控器划得快，不必每动一下就写一次磁盘 */
    private fun scheduleBrowseSave(movie: Movie) {
        browseSaveJob?.cancel()
        browseSaveJob = viewModelScope.launch {
            delay(700)
            val index = _state.value.movies.indexOfFirst { it.uniqueKey == movie.uniqueKey }
                .coerceAtLeast(0)
            // 存 uniqueKey（带片源）：多源下不同站的 vod_id 可能撞车，纯 id 会认错片
            runCatching { browseStore.save(BrowseMemory(movieId = movie.uniqueKey, index = index)) }
        }
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

    /**
     * @param restoreFocus 加载完成后要不要把焦点放到海报上。
     *   首次进入 / 换了片源 → true（开机就能直接按 OK 看片，回到上次那部片）
     *   用户换筛选 → false（他正忙着在筛选条上按，别抢焦点）
     */
    private fun reload(restoreFocus: Boolean = false) {
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
                pageError = null,
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
                    // 恢复上次浏览到的那部片（第一次加载才用得上），没有就落在第一张
                    val focusMovie = takeRestoreTarget(result.movies) ?: result.movies.firstOrNull()
                    _state.update {
                        it.copy(
                            loading = false,
                            filter = fixedFilter,
                            movies = result.movies.distinctBy { movie -> movie.uniqueKey },
                            total = result.total,
                            relaxedNote = if (result.relaxed) relaxedText(fixedFilter) else null,
                            selected = focusMovie,
                            // 只在"该抢焦点"的场合发这个一次性请求（换筛选时不发）
                            focusTarget = if (restoreFocus) focusMovie?.uniqueKey else null,
                            endReached = result.movies.isEmpty(),
                        )
                    }
                    focusMovie?.let { loadDetail(it) }
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
                            // distinctBy：翻页接口有时会返回和上一页重复的片（尤其是聚合多源时），
                            // 列表 key 撞车会直接闪退 —— 在这里兜住
                            movies = (it.movies + result.movies).distinctBy { movie -> movie.uniqueKey },
                            endReached = result.movies.isEmpty(),
                            pageError = null,
                        )
                    }
                }
                .onFailure {
                    // 翻页失败不打断用户：滚到底部会看到一句说明，再往下动一下就会重试
                    _state.update {
                        it.copy(loadingMore = false, pageError = "这一页没加载上，再往下翻一下会重新试")
                    }
                }
        }
    }

    /** 筛条件被放宽时给一句人话 —— 用户必须知道"为什么结果跟我要的不一样" */
    private fun relaxedText(filter: MovieFilter): String {
        val asked = filter.activeParts.joinToString("·")
        return "本站片源缺少「$asked」的标注，已自动放开筛选，先把片子给你看"
    }

    /** 读观看进度：快看完了（离结尾不到 1 分钟）就不显示"继续观看"，当成看完 */
    private fun loadProgress(movie: Movie) {
        viewModelScope.launch {
            // 新键（带片源）优先；老版本存的"纯 id"键兜底读一次，历史进度不丢
            val point = runCatching { progress.get(movie.uniqueKey) }.getOrNull()
                ?: if (movie.origin.isNotEmpty()) {
                    runCatching { progress.get(movie.id) }.getOrNull()
                } else {
                    null
                }
            val resume = point?.let {
                val nearEnd = it.durationMs > 0 && it.positionMs > it.durationMs - 60_000
                if (nearEnd) 0L else it.positionMs
            } ?: 0L
            if (_state.value.selected?.uniqueKey == movie.uniqueKey) {
                _state.update { it.copy(resumeMs = resume) }
            }
        }
    }

    /** 拉详情：焦点停住 250ms 才发请求，避免快速划过时打一串无用请求 */
    private fun loadDetail(movie: Movie) {
        detailCache[movie.uniqueKey]?.let { return }
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            delay(250)
            runCatching { repository.detail(movie.id, movie.origin) }
                .onSuccess { detail ->
                    if (detail == null) {
                        _state.update { it.copy(detailLoading = false) }
                        return@onSuccess
                    }
                    if (detailCache.size > MAX_DETAIL_CACHE) detailCache.clear()
                    detailCache[movie.uniqueKey] = detail
                    // 焦点可能已经移走了，只在还停在它身上时才更新 UI
                    if (_state.value.selected?.uniqueKey == movie.uniqueKey) {
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
