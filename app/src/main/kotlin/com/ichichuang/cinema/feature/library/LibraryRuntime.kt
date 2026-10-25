package com.ichichuang.cinema.feature.library

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.view.KeyEvent
import androidx.compose.runtime.*
import androidx.room.withTransaction
import com.ichichuang.cinema.core.navigation.*
import com.ichichuang.cinema.data.catalog.QueryBudget
import com.ichichuang.cinema.data.catalog.QueryCoordinator
import com.ichichuang.cinema.data.local.CinemaDatabase
import com.ichichuang.cinema.data.local.codec.BrowseNavigationCodec
import com.ichichuang.cinema.data.local.codec.BrowseSnapshotCodec
import com.ichichuang.cinema.data.local.codec.StorageReadException
import com.ichichuang.cinema.data.local.codec.StorageReadFailure
import com.ichichuang.cinema.data.local.entity.BrowseSnapshotEntity
import com.ichichuang.cinema.data.userdata.storageCommit
import com.ichichuang.cinema.domain.model.catalog.CatalogQuery
import com.ichichuang.cinema.domain.model.catalog.CatalogResult
import com.ichichuang.cinema.domain.model.catalog.CatalogSort
import com.ichichuang.cinema.domain.model.catalog.ContentKind
import com.ichichuang.cinema.domain.model.catalog.FactIds
import com.ichichuang.cinema.domain.model.catalog.FieldEvidence
import com.ichichuang.cinema.domain.model.catalog.LanguageScope
import com.ichichuang.cinema.domain.model.catalog.LoadOutcome
import com.ichichuang.cinema.domain.model.catalog.QueryCoverage
import com.ichichuang.cinema.domain.model.catalog.WorkId
import com.ichichuang.cinema.domain.model.userdata.BrowseSnapshot
import com.ichichuang.cinema.domain.model.userdata.CommitResult
import com.ichichuang.cinema.feature.browse.BrowseProbeFixture
import com.ichichuang.cinema.feature.browse.BrowseProbeRuntime
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** 私有授权属于一次性测试档案，绝不经 Intent 或打包资源传入。 */
internal object LibraryAuthorization {
    const val FILE = "round14-library-filter.authorization"
    const val TOKEN = "cinema-round14-disposable-library-filter"
    fun requireOwned(context: Context) {
        val file = File(context.noBackupFilesDir, FILE)
        check(file.isFile && file.length() == TOKEN.length.toLong() && file.readText() == TOKEN) {
            "片库筛选测试缺少独立授权"
        }
    }
}

/** 仅供原生延迟投递/陈旧结果测试的有限控制点，与浏览/首页探针钩子同级，绝无外部激活路径。 */
internal object LibraryTestHooks {
    /** 单次消费：下一个新查询的首页装载被挂起，用于真实陈旧代次实验。 */
    var holdFirstLoad: CompletableDeferred<Unit>? = null
    var blockPlacement by mutableStateOf(false)
    var disableWorkFocus by mutableStateOf(false)
    val focusTrace = java.util.ArrayDeque<String>()
    fun traceFocus(value: String) { if (focusTrace.size >= 256) focusTrace.removeFirst(); focusTrace.addLast(value) }
    val failedFocusRequests = AtomicInteger(0)
    val activeLeases = AtomicInteger(0)
    val appliedQueries = AtomicInteger(0)
    val continuedLoads = AtomicInteger(0)
}

/** 片库只写自身上下文键与私有指针；浏览活动指针（active-navigation）仍由浏览运行时独占。 */
internal class LibrarySnapshotStore(private val database: CinemaDatabase) : BrowseNavigationStore {
    companion object {
        const val ACTIVE_KEY = "\u0000cinema.library.active"
        fun contextKey(query: CatalogQuery) = "library-ui:${query.queryId}"
    }

    override suspend fun saveActive(state: RestoreNavigationState): CommitResult {
        require(state.snapshot.contextKey != ACTIVE_KEY)
        return storageCommit {
            database.withTransaction {
                database.browseSnapshots().upsert(BrowseSnapshotEntity(state.snapshot.contextKey,
                    BrowseSnapshotCodec.encode(state.snapshot)))
                database.browseSnapshots().upsert(BrowseSnapshotEntity(ACTIVE_KEY,
                    BrowseNavigationCodec.encode(state)))
            }
        }
    }

    override suspend fun loadLatest(topLevel: BrowseTopLevel): RestoreNavigationState? = load()
    override suspend fun loadActive(): RestoreNavigationState? = load()

    suspend fun load(): RestoreNavigationState? {
        val row = database.browseSnapshots().find(ACTIVE_KEY) ?: return null
        val state = BrowseNavigationCodec.decode(row.payload)
        if (!state.snapshot.contextKey.startsWith("library-ui:") ||
            state.snapshot.route != BrowseTopLevel.LIBRARY.route) throw StorageReadException(StorageReadFailure.MALFORMED)
        return state
    }
}

/**
 * R14 原生片库运行时：数据一律来自既有 R09 `QueryCoordinator` 合成查询管线（预算、
 * 代次、取消、部分覆盖）+ R10 严格评估，焦点与恢复复用 R11 令牌管线，详情/播放器
 * 永远是浏览运行时的子层。查询身份只在用户真实意图下更换；取消/陈旧来源结果与
 * 迟到回调不得重绘错误列表或抢焦点。片库自身不产生观看记录（G05/H13）。
 */
internal class LibraryRuntime(
    private val context: Context,
    val browse: BrowseProbeRuntime,
    database: CinemaDatabase,
    val scope: CoroutineScope,
    private val fixture: BrowseProbeFixture,
) {
    companion object {
        const val ROUTE = "library"
        const val FILTER_ENTRY = "筛选"
        const val CONTINUE = "继续核对"
        const val RETRY = "重试"
        const val COLUMNS = 5
        /** 面板/工具条控件的中文标签唯一来源；测试 tag 与语义共用。 */
        val YEAR_MODE_LABELS = mapOf(
            LibraryYearMode.NONE to "不限", LibraryYearMode.EXACT to "具体年份",
            LibraryYearMode.DECADE to "年代", LibraryYearMode.RANGE to "起止范围")
        val LANGUAGE_LABELS = mapOf(
            "ALL" to "全部", "CHINESE" to "华语", "NON_CHINESE" to "非华语")
        val SORT_LABELS = listOf("DEFAULT" to "默认", "YEAR_ASCENDING" to "从早到晚", "YEAR_DESCENDING" to "从晚到早")
    }

    private val store = LibrarySnapshotStore(database)
    val restore = BrowseRestoreCoordinator()
    private val checkpoint = BrowseCheckpointCoordinator(store, scope, onResult = { commit ->
        lastCommit = commit
        if (commit != CommitResult.Committed) status = "片库位置尚未保存，请保留当前页面后重试"
    })
    private val engine = QueryCoordinator(LibraryCandidateStore(),
        LibraryFixtureTransport(fixture.works, fixture.library), QueryBudget(maxElapsedMillis = 10_000))

    /** 国家/类型候选只来自 fixture 中有证据的资料，不虚构词条、不把未获取当不存在。 */
    val countryCandidates: List<String> = evidencedIds { it.countries }
    val genreCandidates: List<String> = evidencedIds { it.genres }
    val knownSources: List<String> = (fixture.works.map { it.source.value } +
        fixture.library.failingSources + fixture.library.mirrorSources.keys).distinct().sorted()
    private fun evidencedIds(field: (com.ichichuang.cinema.domain.filter.StrictWorkFacts) -> FieldEvidence<FactIds>) =
        fixture.works.flatMap { work ->
            when (val evidence = field(work.facts)) {
                is FieldEvidence.SourceReported -> evidence.value.values.toList()
                is FieldEvidence.Confirmed -> evidence.value.values.toList()
                else -> emptyList()
            }
        }.distinct().sorted()

    var appliedQuery by mutableStateOf<CatalogQuery?>(null); private set
    var result by mutableStateOf<CatalogResult?>(null); private set
    var display by mutableStateOf(LibraryProjection.project(
        CatalogQuery.create("片库查询-1", null, LanguageScope.ALL, emptySet(), emptySet(), null, null,
            CatalogSort.DEFAULT, emptySet()), null, emptyMap(), false, true, false, false, false)); private set
    var status by mutableStateOf("正在按当前条件核对来源"); private set
    var lastCommit by mutableStateOf<CommitResult?>(null); private set
    var navigation by mutableStateOf<RestoreNavigationState?>(null); private set
    private var checkpointToken: CheckpointToken? = null
    var restoreToken by mutableStateOf<RestoreToken?>(null); private set
    var layoutToken by mutableStateOf<RestoreLayoutToken?>(null); private set
    var userTarget by mutableStateOf<String?>(null); private set
    var controlTarget by mutableStateOf<String?>(null); private set
    var focused by mutableStateOf<String?>(null); private set
    var focusedControl by mutableStateOf<String?>(null); private set
    var inputEpoch by mutableLongStateOf(0); private set
    var foreground by mutableStateOf(true); private set
    var columns by mutableIntStateOf(COLUMNS); private set
    var placedColumns by mutableIntStateOf(0); private set
    var viewportWidth by mutableIntStateOf(0); private set
    var viewportHeight by mutableIntStateOf(0); private set
    var visibleAnchor by mutableStateOf<String?>(null); private set
    var visibleOffset by mutableFloatStateOf(0f); private set
    private var anchor: String? = null
    private var offset = 0f
    private var revision = 1L
    private var queryCounter = 0L
    private var observationJob: Job? = null
    private var loadFailed = false
    private var budgetExhausted = false
    private var offlineNow by mutableStateOf(false)
    private var stopped = false
    private val held = mutableSetOf<Int>()
    private var lastLibraryFocus: String? = null

    // 筛选面板（焦点被捕获在面板内；关闭恢复打开者控件）。
    var panelOpen by mutableStateOf(false); private set
    var draft by mutableStateOf<LibraryFilterDraft?>(null); private set
    var panelError by mutableStateOf<String?>(null); private set
    var panelTarget by mutableStateOf<String?>(null); private set
    var auditText by mutableStateOf<String?>(null); private set
    private val panelHeld = mutableSetOf<Int>()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        // 回调线程随注册方式不同：状态写与重投影统一跳回主线程。
        private fun post(action: () -> Unit) { scope.launch { action() } }
        override fun onAvailable(network: Network) { post { offlineNow = false; recompute() } }
        override fun onLost(network: Network) { post { refreshOffline(); recompute() } }
    }

    fun viewportChanged(width: Int, height: Int) {
        if (!foreground || stopped) return
        if (width == viewportWidth && height == viewportHeight) return
        val measured = viewportWidth > 0 && viewportHeight > 0
        viewportWidth = width; viewportHeight = height
        if (!measured || width <= 0 || height <= 0) return
        val current = result?.snapshotRevision ?: return
        val token = restoreToken
        val nextPass = token?.let { restore.advanceLayout(it, current) }
        if (nextPass != null) { layoutToken = nextPass; return }
    }

    fun placed(columns: Int) { placedColumns = columns }

    private fun refreshOffline() {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        val network = manager.activeNetwork
        offlineNow = network == null || manager.getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true
    }

    private fun nextQueryId(): String { queryCounter += 1; return "片库查询-$queryCounter" }

    suspend fun start() {
        val ticket = restore.reserveLoad()
        LibraryTestHooks.traceFocus("start reserveLoad")
        runCatching {
            val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
            if (android.os.Build.VERSION.SDK_INT >= 26)
                manager.registerDefaultNetworkCallback(networkCallback, android.os.Handler(android.os.Looper.getMainLooper()))
            else manager.registerDefaultNetworkCallback(networkCallback)
        }
        refreshOffline()
        val saved = runCatching { store.load() }.getOrNull()
        val initial = saved?.snapshot?.query ?: libraryDefaultQuery(fixture.works, fixture.library, "片库查询-1")
        // 重开已提交查询时沿用其编号起点，后续用户查询只递增、不回卷复用。
        queryCounter = initial.queryId.substringAfterLast('-').toLongOrNull() ?: 0
        installQuery(initial, saved?.snapshot, ticket)
        LibraryTestHooks.traceFocus("start installed=${initial.queryId} saved=${saved != null} savedFocused=${saved?.snapshot?.focusedWorkId?.value} savedAnchor=${saved?.snapshot?.anchorWorkId?.value}")
    }

    /** 新查询身份只能由用户意图触发：完整字段、新上下文键、新结果上下文。 */
    private fun installQuery(query: CatalogQuery, saved: BrowseSnapshot?, ticket: RestoreLoadToken) {
        appliedQuery = query
        result = null; loadFailed = false; budgetExhausted = false
        val contextKey = LibrarySnapshotStore.contextKey(query)
        val snapshot = (saved ?: BrowseSnapshot(contextKey, ROUTE, null, query, null, null, null, 0f,
            System.currentTimeMillis())).copy(contextKey = contextKey, query = query)
        val token = restore.completeLoad(ticket, snapshot)
        navigation = RestoreNavigationState(snapshot)
        checkpointToken = checkpoint.begin(navigation!!)
        anchor = snapshot.anchorWorkId?.value; offset = snapshot.relativeOffset
        focused = null; focusedControl = null
        restoreToken = token
        revision = maxOf(revision, snapshot.resultRevision ?: 0L)
        observe(query)
        layoutToken = token?.let { restore.advanceLayout(it, revision) }
        if (token == null && controlTarget == null) controlTarget = FILTER_ENTRY
        recompute()
        // 新查询身份必须启动防抖落盘：begin 只挂 pending，焦点若无后续变化，进程终止前将无提交。
        update()
        LibraryTestHooks.traceFocus("install ${query.queryId} token=${token?.routeGeneration}")
    }

    private fun observe(query: CatalogQuery) {
        observationJob?.cancel()
        observationJob = scope.launch {
            try {
                engine.observeQuery(query).collect { update ->
                    result = update
                    revision = maxOf(revision, update.snapshotRevision)
                    restoreToken?.let { token -> layoutToken = restore.advanceLayout(token, revision) }
                    recompute()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { loadFailed = true; recompute() }
        }
        scope.launch {
            LibraryTestHooks.holdFirstLoad?.let { hold ->
                LibraryTestHooks.holdFirstLoad = null
                hold.await()
            }
            applyOutcome(engine.loadMore(query.queryId))
        }
    }

    private fun applyOutcome(outcome: LoadOutcome) {
        when (outcome) {
            is LoadOutcome.Partial -> if (outcome.reason ==
                com.ichichuang.cinema.domain.model.catalog.PartialReason.BUDGET_EXHAUSTED) budgetExhausted = true
            is LoadOutcome.Failed -> { loadFailed = true; budgetExhausted = false }
            is LoadOutcome.Complete -> budgetExhausted = false
            else -> Unit
        }
        recompute()
    }

    fun recompute() {
        val query = appliedQuery ?: return
        val works = fixture.works.associate {
            it.id.value to LibraryProjection.WorkDisplay(it.title, it.artworkUri, it.artworkShape)
        }
        display = LibraryProjection.project(query, result, works,
            librarySourcesConfigured(fixture.works, fixture.library, query),
            result == null && !loadFailed, offlineNow, loadFailed, budgetExhausted)
        status = display.statusText
    }

    /** 用户显式应用新查询（面板确认、删片、重置确认）；旧查询上下文行保留。 */
    fun applyUserQuery(next: CatalogQuery, auditReason: String?) {
        LibraryTestHooks.appliedQueries.incrementAndGet()
        restore.onUserInput(); inputEpoch++; restoreToken = null
        auditText = auditReason
        userTarget = null; focused = null; focusedControl = null
        val ticket = restore.reserveLoad()
        installQuery(next, null, ticket)
        controlTarget = FILTER_ENTRY
    }

    fun continueLoading() {
        if (!display.canContinue) return
        LibraryTestHooks.continuedLoads.incrementAndGet()
        scope.launch { applyOutcome(engine.loadMore(requireNotNull(appliedQuery).queryId)) }
    }

    fun retry() {
        loadFailed = false; budgetExhausted = false
        recompute()
        scope.launch { applyOutcome(engine.loadMore(requireNotNull(appliedQuery).queryId)) }
    }

    fun openPanel() {
        draft = LibraryFilterController.begin(requireNotNull(appliedQuery))
        panelError = null
        panelOpen = true
        panelTarget = "year-mode-${draft!!.yearMode.name}"
        restore.onUserInput(); restoreToken = null
        LibraryTestHooks.traceFocus("openPanel")
    }

    fun closePanel() {
        panelOpen = false; draft = null; panelError = null; panelHeld.clear()
        controlTarget = FILTER_ENTRY
        LibraryTestHooks.traceFocus("closePanel cancel")
    }

    private fun applyDraft() {
        val current = draft ?: return
        when (val outcome = LibraryFilterController.apply(current, ::nextQueryId)) {
            is LibraryFilterController.ApplyOutcome.Unchanged -> {
                panelOpen = false; draft = null; panelHeld.clear(); controlTarget = FILTER_ENTRY
                LibraryTestHooks.traceFocus("applyDraft unchanged")
            }
            is LibraryFilterController.ApplyOutcome.Invalid -> panelError = outcome.reason
            is LibraryFilterController.ApplyOutcome.Applied -> {
                panelOpen = false; draft = null; panelError = null; panelHeld.clear()
                LibraryTestHooks.traceFocus("applyDraft applied ${outcome.query.queryId} " +
                    "year=${outcome.query.yearStart}..${outcome.query.yearEnd} reason=${outcome.auditReason}")
                applyUserQuery(outcome.query, outcome.auditReason)
            }
        }
    }

    private fun mutateDraft(transform: (LibraryFilterDraft) -> LibraryFilterDraft) {
        draft = draft?.let(transform)
        panelError = null
    }

    /** 面板分区（含当前年份模式对应的步进器行）；面板渲染与按键导航共用同一来源。 */
    internal fun panelZones(): List<Pair<String, List<String>>> = buildList {
        add("年份方式" to LibraryYearMode.entries.map { "year-mode-${it.name}" })
        draft?.let { d ->
            when (d.yearMode) {
                LibraryYearMode.EXACT -> add("具体年份" to listOf("year-exact-minus", "year-exact-plus"))
                LibraryYearMode.DECADE -> add("年代" to listOf("year-decade-minus", "year-decade-plus"))
                LibraryYearMode.RANGE -> add("起止年份" to listOf("year-start-minus", "year-start-plus", "year-end-minus", "year-end-plus"))
                LibraryYearMode.NONE -> Unit
            }
        }
        add("语言范围" to LanguageScope.entries.map { "lang-${it.name}" })
        add("内容类别" to (listOf("kind-null") + ContentKind.entries.map { "kind-${it.name}" }))
        add("国家地区" to countryCandidates.map { "country-$it" })
        add("类型" to genreCandidates.map { "genre-$it" })
        add("排序" to SORT_LABELS.map { "sort-${it.first}" })
        add("参与来源" to knownSources.map { "source-$it" })
        add("操作" to listOf("panel-reset", "panel-cancel", "panel-apply"))
    }

    fun panelLabels(key: String): String {
        val kind = key.substringAfter("kind-")
        return when {
            key.startsWith("year-mode-") -> YEAR_MODE_LABELS.getValue(LibraryYearMode.valueOf(key.removePrefix("year-mode-")))
            key.startsWith("lang-") -> LANGUAGE_LABELS.getValue(key.removePrefix("lang-"))
            key == "kind-null" -> "不限"
            key.startsWith("kind-") -> LibraryFilterLabels.kinds[kind.lowercase()] ?: kind
            key.startsWith("country-") -> LibraryFilterLabels.country(key.removePrefix("country-"))
            key.startsWith("genre-") -> LibraryFilterLabels.genre(key.removePrefix("genre-"))
            key.startsWith("sort-") -> SORT_LABELS.first { it.first == key.removePrefix("sort-") }.second
            key.startsWith("source-") -> "来源 ${key.removePrefix("source-")}"
            // 步进器可访问描述携带当前值：只听操作名无法得知“当前 1994”。
            key.startsWith("year-exact") -> "年份${if (key.endsWith("minus")) "减一" else "加一"}（当前 ${draft?.exactYear ?: "未编辑"}）"
            key.startsWith("year-decade") -> "年代${if (key.endsWith("minus")) "提前" else "推后"}（当前 ${draft?.decadeStart ?: "未编辑"}）"
            key.startsWith("year-start") -> "起点${if (key.endsWith("minus")) "减一" else "加一"}（当前 ${draft?.rangeStart ?: "未编辑"}）"
            key.startsWith("year-end") -> "终点${if (key.endsWith("minus")) "减一" else "加一"}（当前 ${draft?.rangeEnd ?: "未编辑"}）"
            key == "panel-reset" -> "重置"
            key == "panel-cancel" -> "取消"
            key == "panel-apply" -> "应用"
            else -> key
        }
    }

    private fun activatePanel(key: String) {
        val d = draft ?: return
        when {
            key.startsWith("year-mode-") -> mutateDraft { it.copy(yearMode = LibraryYearMode.valueOf(key.removePrefix("year-mode-"))) }
            key.startsWith("lang-") -> mutateDraft { it.copy(languageScope = LanguageScope.valueOf(key.removePrefix("lang-"))) }
            key == "kind-null" -> mutateDraft { it.copy(contentKind = null) }
            key.startsWith("kind-") -> mutateDraft { it.copy(contentKind = ContentKind.valueOf(kindOf(key))) }
            key.startsWith("country-") -> { val id = key.removePrefix("country-")
                mutateDraft { it.copy(countryIds = if (id in it.countryIds) it.countryIds - id else it.countryIds + id) } }
            key.startsWith("genre-") -> { val id = key.removePrefix("genre-")
                mutateDraft { it.copy(genreIds = if (id in it.genreIds) it.genreIds - id else it.genreIds + id) } }
            key.startsWith("sort-") -> mutateDraft { it.copy(sort = CatalogSort.valueOf(key.removePrefix("sort-"))) }
            key.startsWith("source-") -> { val id = key.removePrefix("source-")
                mutateDraft { it.copy(enabledSourceIds = if (id in it.enabledSourceIds) it.enabledSourceIds - id else it.enabledSourceIds + id) } }
            key == "year-exact-minus" -> mutateDraft { it.copy(exactYear = (it.exactYear - 1).coerceAtLeast(1)) }
            key == "year-exact-plus" -> mutateDraft { it.copy(exactYear = (it.exactYear + 1).coerceAtMost(9_999)) }
            key == "year-decade-minus" -> mutateDraft { it.copy(decadeStart = (it.decadeStart - 10).coerceAtLeast(10)) }
            key == "year-decade-plus" -> mutateDraft { it.copy(decadeStart = (it.decadeStart + 10).coerceAtMost(9_990)) }
            key == "year-start-minus" -> mutateDraft { it.copy(rangeStart = (it.rangeStart - 1).coerceAtLeast(1)) }
            key == "year-start-plus" -> mutateDraft { it.copy(rangeStart = (it.rangeStart + 1).coerceAtMost(9_999)) }
            key == "year-end-minus" -> mutateDraft { it.copy(rangeEnd = (it.rangeEnd - 1).coerceAtLeast(1)) }
            key == "year-end-plus" -> mutateDraft { it.copy(rangeEnd = (it.rangeEnd + 1).coerceAtMost(9_999)) }
            key == "panel-reset" -> mutateDraft {
                val defaults = libraryDefaultQuery(fixture.works, fixture.library, "重置草稿")
                it.copy(contentKind = null, languageScope = LanguageScope.ALL, countryIds = emptySet(),
                    genreIds = emptySet(), yearMode = LibraryYearMode.NONE, sort = CatalogSort.DEFAULT,
                    enabledSourceIds = defaults.enabledSourceIds.map { id -> id.value }.toSet())
            }
            key == "panel-cancel" -> closePanel()
            key == "panel-apply" -> applyDraft()
            else -> Unit
        }
    }

    private fun kindOf(key: String) = key.removePrefix("kind-")

    /** 面板控件的真实点击入口（触摸/语义与 D-pad 同一动作；测试钩子不经过此路径）。 */
    fun activatePanelKey(key: String) {
        panelTarget = key
        activatePanel(key)
    }

    /** 面板内真实焦点回报：同步运行时的待落焦意图，避免按键序列回退。 */
    fun panelFocusChanged(key: String) { panelTarget = key }

    /** 屏面控件（条件片/状态动作）的真实点击入口；与 D-pad 激活同一动作。 */
    fun activateControl(key: String) {
        restore.onUserInput(); inputEpoch++; restoreToken = null
        activate(key)
    }

    /** 面板按键：焦点捕获在面板分区内；BACK 只关面板并恢复打开者。 */
    private fun panelKey(code: Int) {
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
            panelTarget?.let(::activatePanel)
            return
        }
        if (code == KeyEvent.KEYCODE_BACK) { closePanel(); return }
        val zones = panelZones()
        if (zones.isEmpty()) return
        val current = panelTarget
        val location = zones.indexOfFirst { current in it.second }.takeIf { it >= 0 } ?: 0
        val keys = zones[location].second
        val index = keys.indexOf(current).takeIf { it >= 0 } ?: 0
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> panelTarget = keys[(index - 1).coerceAtLeast(0)]
            KeyEvent.KEYCODE_DPAD_RIGHT -> panelTarget = keys[(index + 1).coerceAtMost(keys.lastIndex)]
            KeyEvent.KEYCODE_DPAD_UP -> if (location > 0) {
                val upper = zones[location - 1].second
                panelTarget = upper[index.coerceAtMost(upper.lastIndex)]
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (location < zones.lastIndex) {
                val lower = zones[location + 1].second
                panelTarget = lower[index.coerceAtMost(lower.lastIndex)]
            }
        }
    }

    /** D-pad 可达的有序焦点区：工具条 → 条件片 → 内容网格 → 状态动作。 */
    fun focusZones(): List<Pair<String, List<String>>> = buildList {
        add("工具条" to listOf(FILTER_ENTRY))
        if (display.filterSummary.isNotEmpty()) add("条件" to display.filterSummary.map { "chip:${it.key}" })
        if (display.items.isNotEmpty()) add("内容" to display.items.map { it.workId })
        val actions = statusActions()
        if (actions.isNotEmpty()) add("状态" to actions)
    }

    fun statusActions(): List<String> = buildList {
        if (display.canContinue) add(CONTINUE)
        if (display.status == LibraryContentStatus.FAILED) add(RETRY)
    }

    private fun stateNow(): RestoreNavigationState? = navigation?.let { state ->
        val focus = focused ?: userTarget
        state.copy(snapshot = state.snapshot.copy(
            focusedWorkId = focus?.let(::WorkId),
            anchorWorkId = (anchor ?: visibleAnchor ?: focus)?.let(::WorkId),
            relativeOffset = if (focused != null) offset else visibleOffset,
            resultRevision = revision, savedAt = System.currentTimeMillis()))
    }

    private fun update() {
        val state = stateNow() ?: return
        navigation = state
        checkpointToken?.let { checkpoint.update(it, state) }
    }

    fun positioned(id: String?, relativeOffset: Float) {
        if (!foreground || stopped) return
        visibleAnchor = id; visibleOffset = relativeOffset
        if (focused == null) return
        anchor = id; offset = relativeOffset
        update()
    }

    fun focused(workId: String) {
        if (!foreground || stopped || panelOpen) return
        focused = workId; focusedControl = null; userTarget = null; controlTarget = null
        update()
    }

    fun controlFocused(key: String) {
        if (!foreground || stopped || panelOpen) return
        focused = null; focusedControl = key
        if (controlTarget == key) controlTarget = null
    }

    /** 当前结果修订号（只读）：供测试把真实 observation 与真实协调器对账（R15 N10）。 */
    internal val currentResultRevision: Long get() = revision

    fun observation(ready: Set<String>, pass: RestoreLayoutToken, pageReady: Boolean): RestoreObservation {
        val state = requireNotNull(navigation)
        val ids = display.items.map { it.workId }
        // 保存序用全量已知作品（含已从当前结果消失的锚点），与 R11 浏览的 savedOrder 同构：
        // 锚点被移除时邻居搜索才能落在“原行最近存活项”，而非退化为页面默认。
        val original = fixture.works.map { it.id.value }
        val savedIndex = original.indexOf(state.snapshot.focusedWorkId?.value ?: "").coerceAtLeast(0)
        val rowStart = (savedIndex / columns) * columns
        val row = original.drop(rowStart).take(columns)
        return RestoreObservation(state.snapshot.query, revision,
            mapOf("原行" to ids.filter { it in row }.map(::WorkId), "其余" to ids.filter { it !in row }.map(::WorkId)),
            result?.coverage == QueryCoverage.COMPLETE, ready.map(::WorkId).toSet(), pageReady, "原行",
            row.map(::WorkId), pass)
    }

    fun delivered(outcome: RestoreOutcome): String? = when (outcome) {
        is RestoreOutcome.ReadyToFocus -> outcome.workId.value
        is RestoreOutcome.Fallback -> outcome.workId?.value.also { if (outcome.workId == null) controlTarget = FILTER_ENTRY }
        else -> null
    }

    fun openWork(workId: String) {
        // 记录进入子层前的真实焦点（网格作品优先），详情返回据此恢复。
        lastLibraryFocus = focused ?: userTarget ?: focusedControl ?: controlTarget
        // 进入详情/播放器子层后按键让位浏览：清空本类去重集，避免子层关闭后的首个确认被吞。
        held.clear()
        scope.launch { flush() }
        browse.openFromHome(WorkId(workId), resume = false)
    }

    /** 子层（详情/播放器）关闭后回到片库根画面时恢复进入前的位置。 */
    fun childReturned() {
        if (stopped) return
        val target = lastLibraryFocus ?: FILTER_ENTRY
        // 网格作品键走 userTarget（带动滚动定位）；控件键直达。
        if (target != FILTER_ENTRY && target != CONTINUE && target != RETRY && !target.startsWith("chip:")) {
            userTarget = target; controlTarget = null
        } else { controlTarget = target; userTarget = null }
        LibraryTestHooks.traceFocus("childReturned target=$target")
    }

    /**
     * 片库根画面按键（含 UP/去重自管）。面板打开时全部按键捕获在面板内；
     * 根画面 BACK 由宿主回首页（先尽力保存现场，保存失败不困死用户）。
     */
    fun key(event: KeyEvent, onBackToHome: () -> Unit): Boolean {
        val code = event.keyCode
        if (code !in setOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK)) return false
        // UP 成对清理两个去重集：面板开关/进入子层/返回首页会让 UP 离开本类域，不得留滞留键。
        if (event.action == KeyEvent.ACTION_UP) { held.remove(code); panelHeld.remove(code); return true }
        if (event.action != KeyEvent.ACTION_DOWN) return true
        if (panelOpen) {
            if (code in setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK) &&
                (!panelHeld.add(code) || event.repeatCount > 0)) return true
            panelKey(code)
            return true
        }
        if (code in setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK) &&
            (!held.add(code) || event.repeatCount > 0)) return true
        restore.onUserInput(); inputEpoch++; restoreToken = null
        if (code == KeyEvent.KEYCODE_BACK) {
            held.clear()
            scope.launch { runCatching { flush() } }
            onBackToHome()
            return true
        }
        val zones = focusZones()
        if (zones.isEmpty()) return true
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
            // 激活以用户可见的已落焦为准（控件/网格作品）；陈旧待落焦意图不得替用户选目标。
            val visible = focusedControl ?: focused ?: userTarget ?: controlTarget
            if (visible != null && zones.any { visible in it.second }) { activate(visible); return true }
        }
        // 位置解析含真实网格焦点：R11 恢复落焦后三个意图可能全空，不得回退首控件。
        val currentKey = userTarget ?: controlTarget ?: focusedControl ?: focused ?: zones.first().second.first()
        val location = zones.indexOfFirst { currentKey in it.second }
        if (location < 0) { target(zones.first().second.first()); return true }
        val keys = zones[location].second
        val index = keys.indexOf(currentKey)
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val next = if (code == KeyEvent.KEYCODE_DPAD_LEFT) index - 1 else index + 1
                if (next in keys.indices) target(keys[next])
            }
            KeyEvent.KEYCODE_DPAD_UP -> if (location > 0) {
                val upper = zones[location - 1].second
                target(upper[index.coerceAtMost(upper.lastIndex)])
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (location < zones.lastIndex) {
                val lower = zones[location + 1].second
                target(lower[index.coerceAtMost(lower.lastIndex)])
            }
        }
        return true
    }

    private fun target(key: String) {
        // 网格作品键走 userTarget 带动滚动；控件键（筛选/条件片/状态动作）直达。
        if (!key.startsWith("chip:") && key != FILTER_ENTRY && key != CONTINUE && key != RETRY) userTarget = key
        else controlTarget = key
    }

    private fun activate(key: String) {
        when {
            key == FILTER_ENTRY -> openPanel()
            key == CONTINUE -> continueLoading()
            key == RETRY -> retry()
            key.startsWith("chip:") -> {
                val chip = display.filterSummary.firstOrNull { "chip:${it.key}" == key } ?: return
                applyUserQuery(LibraryFilterController.removeChip(requireNotNull(appliedQuery), chip, ::nextQueryId),
                    "已删除筛选片：${chip.label}")
            }
            else -> openWork(key)
        }
    }

    suspend fun flush(): CommitResult {
        val token = checkpointToken ?: return CommitResult.RejectedStale
        if (!checkpoint.hasPending) return lastCommit ?: checkpoint.lastResult ?: CommitResult.RejectedStale
        return checkpoint.flush(token).also { lastCommit = it }
    }

    fun background() { scope.launch { checkpointToken?.let { runCatching { flush() } } } }
    fun pause() { foreground = false; background() }
    fun resume() { if (!stopped) foreground = true }

    suspend fun shutdown() {
        stopped = true
        runCatching { context.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(networkCallback) }
        observationJob?.cancel()
        try { if (checkpoint.hasPending) checkpointToken?.let { checkpoint.flush(it) } } catch (_: Exception) {}
    }
}
