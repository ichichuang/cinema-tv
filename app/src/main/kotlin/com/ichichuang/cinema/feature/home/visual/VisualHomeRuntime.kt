package com.ichichuang.cinema.feature.home.visual

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.view.KeyEvent
import androidx.compose.runtime.*
import com.ichichuang.cinema.core.navigation.*
import com.ichichuang.cinema.data.local.CinemaDatabase
import com.ichichuang.cinema.data.local.codec.BrowseNavigationCodec
import com.ichichuang.cinema.data.local.codec.BrowseSnapshotCodec
import com.ichichuang.cinema.data.local.entity.BrowseSnapshotEntity
import com.ichichuang.cinema.data.local.codec.StorageReadException
import com.ichichuang.cinema.data.local.codec.StorageReadFailure
import com.ichichuang.cinema.data.userdata.storageCommit
import com.ichichuang.cinema.core.design.ReduceMotionController
import com.ichichuang.cinema.core.design.SystemAnimatorScaleSource
import com.ichichuang.cinema.core.navigation.BrowseTopLevel
import com.ichichuang.cinema.data.settings.CinemaSettings
import com.ichichuang.cinema.domain.model.catalog.CatalogQuery
import com.ichichuang.cinema.domain.model.catalog.CatalogResult
import com.ichichuang.cinema.domain.model.catalog.QueryCoverage
import com.ichichuang.cinema.domain.model.catalog.WorkId
import com.ichichuang.cinema.domain.model.userdata.BrowseSnapshot
import com.ichichuang.cinema.domain.model.userdata.CommitResult
import com.ichichuang.cinema.domain.model.userdata.WatchState
import com.ichichuang.cinema.feature.browse.BrowseProbeFixture
import com.ichichuang.cinema.feature.browse.BrowseProbeRuntime
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** 私有授权属于一次性测试档案，绝不经 Intent 或打包资源传入。 */
internal object VisualHomeAuthorization {
    const val FILE = "round13-home-visual.authorization"
    const val TOKEN = "cinema-round13-disposable-home-visual"
    fun requireOwned(context: Context) {
        val file = File(context.noBackupFilesDir, FILE)
        check(file.isFile && file.length() == TOKEN.length.toLong() && file.readText() == TOKEN) {
            "首页视觉测试缺少独立授权"
        }
    }
}

/** 仅供原生延迟投递测试的有限控制点，与浏览探针钩子同级，绝无外部激活路径。 */
internal object VisualHomeTestHooks {
    var holdWindowReady by mutableStateOf(false)
    var disableCardFocus by mutableStateOf(false)
    var blockPlacement by mutableStateOf(false)
    val focusTrace = java.util.ArrayDeque<String>()
    fun traceFocus(value: String) { if (focusTrace.size >= 256) focusTrace.removeFirst(); focusTrace.addLast(value) }
    val failedFocusRequests = AtomicInteger(0)
    val activeLeases = AtomicInteger(0)
    val projectionRecomputes = AtomicInteger(0)
    var beforeLoad: CompletableDeferred<Unit>? = null
}

/** 首页 / 片库 / 浏览 / 设置 候选根画面；详情与播放器始终是浏览运行时的子层。 */
internal enum class HomeRootScreen { HOME, LIBRARY, BROWSE, SETTINGS }

/** 首页只写自身上下文键；浏览活动指针（active-navigation）仍由浏览运行时独占。 */
internal class HomeSnapshotStore(private val database: CinemaDatabase, private val contextKey: String) : BrowseNavigationStore {
    override suspend fun saveActive(state: RestoreNavigationState): CommitResult = storageCommit {
        database.browseSnapshots().upsert(BrowseSnapshotEntity(state.snapshot.contextKey, BrowseNavigationCodec.encode(state)))
    }
    override suspend fun loadLatest(topLevel: BrowseTopLevel): RestoreNavigationState? = load()
    override suspend fun loadActive(): RestoreNavigationState? = load()
    suspend fun load(): RestoreNavigationState? {
        val row = database.browseSnapshots().find(contextKey) ?: return null
        val state = BrowseNavigationCodec.decode(row.payload)
        if (state.snapshot.contextKey != contextKey) throw StorageReadException(StorageReadFailure.MALFORMED)
        return state
    }
}

/**
 * 首页只读投影运行时：数据一律来自既有 R08–R12 候选存储（共享浏览运行时持有的
 * 个人库实例与候选目录 fixture），自身不写任何用户记录。焦点与恢复复用 R11
 * 协调器；持久化只写 `home:<queryId>` 上下文键。
 */
internal class VisualHomeRuntime(
    private val context: Context,
    val browse: BrowseProbeRuntime,
    database: CinemaDatabase,
    val scope: CoroutineScope,
    private val fixture: BrowseProbeFixture,
    /** R14 原生片库切片；null（探针未开）时“片库”仍走 R13 浏览占位路径，行为逐字不变。 */
    val library: com.ichichuang.cinema.feature.library.LibraryRuntime? = null,
) {
    companion object {
        const val CONTINUE_ROW = "继续观看"
        const val RECENT_ROW = "最近观看"
        const val CATALOG_ROW = "片库"
        const val HERO_RESUME = "继续播放"
        const val HERO_DETAIL = "查看详情"
        const val GOTO_SETTINGS = "前往设置"
        const val RETRY = "重试"
        val NAV_ITEMS = listOf("首页", "片库", "我的收藏", "最近观看", "设置")
        val SETTINGS_ITEMS = listOf("大字模式", "筛选栏", "列数", "减弱动效", "刷新目录", "重载首页")
        fun cardKey(row: String, workId: String) = "$row/$workId"
    }

    private val query: CatalogQuery = fixture.initial.query!!
    private val contextKey = BrowseTopLevel.HOME.contextKey(query)
    private val store = HomeSnapshotStore(database, contextKey)
    val restore = BrowseRestoreCoordinator()
    private val checkpoint = BrowseCheckpointCoordinator(store, scope, onResult = { commit ->
        lastCommit = commit
        if (commit != CommitResult.Committed) status = "首页位置尚未保存，请保留当前页面后重试"
    })

    var screen by mutableStateOf(HomeRootScreen.HOME); private set
    var largeText by mutableStateOf(false); private set
    var status by mutableStateOf("正在读取首页数据"); private set

    /**
     * The user's stored "减弱动效" preference, read live from DataStore.
     * Drives the settings switch label; do not read this for animation decisions.
     */
    var reduceMotionSetting by mutableStateOf(false); private set

    /**
     * The effective reduced-motion signal: [reduceMotionSetting] OR the platform
     * animator scale being 0. Provided at the probe root as [LocalReducedMotion]
     * so every component in the new UI changes mode in the same frame.
     */
    var reduceMotion by mutableStateOf(false); private set

    private val settings = CinemaSettings(context)
    private val reduceMotionController =
        ReduceMotionController(SystemAnimatorScaleSource(context.contentResolver), settings.reduceMotion)

    /** Number of enabled sources, read live from DataStore; 0 = unconfigured. */
    var activeSourceCount by mutableStateOf(0); private set
    var lastCommit by mutableStateOf<CommitResult?>(null); private set
    var navigation by mutableStateOf<RestoreNavigationState?>(null); private set
    private var checkpointToken: CheckpointToken? = null
    var restoreToken by mutableStateOf<RestoreToken?>(null); private set
    var layoutToken by mutableStateOf<RestoreLayoutToken?>(null); private set
    var userTarget by mutableStateOf<String?>(null); private set
    var controlTarget by mutableStateOf<String?>(null); private set
    var inputEpoch by mutableLongStateOf(0); private set
    var focusedControl by mutableStateOf<String?>(null); private set
    var foreground by mutableStateOf(true); private set
    var display by mutableStateOf(HomeDisplayState(emptyList(), emptyList(), emptyList(),
        HomeContentStatus.LOADING, 0, false, null)); private set
    private var stopped = false
    private var revision = 1L
    private var catalogWorks: List<BrowseProbeFixture.Work> = emptyList()
    private var catalogResult: CatalogResult? = null
    private var offlineNow by mutableStateOf(false)
    private val held = mutableSetOf<Int>()
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        // 回调线程随注册方式不同（API26 以下在系统内部线程）：状态写与重投影统一跳回主线程。
        private fun post(action: () -> Unit) { scope.launch { action() } }
        override fun onAvailable(network: Network) { post { offlineNow = false; recompute() } }
        override fun onLost(network: Network) { post { refreshOffline(); recompute() } }
    }

    init {
        browse.onPersonalChanged = { recompute() }
        // Reduced-motion: mirror the stored preference and the merged effective signal.
        scope.launch { settings.reduceMotion.collect { reduceMotionSetting = it } }
        scope.launch { reduceMotionController.reduced.collect { reduceMotion = it } }
        // Source count for the settings slice, live from DataStore.
        scope.launch { settings.activeUrls.collect { activeSourceCount = it.size } }
        // 子层（详情/播放器）关闭时，若仍处首页/片库根画面，重新落焦到进入子层前的位置。
        scope.launch {
            var wasChild = false
            snapshotFlow { browse.isDetail || browse.isPlayer }.collect { child ->
                if (wasChild && !child) {
                    when {
                        // 片库入口直接经浏览子层返回：不依赖首页的 childReturnPending 记账。
                        screen == HomeRootScreen.LIBRARY -> library?.childReturned()
                        childReturnPending -> {
                            childReturnPending = false
                            if (screen == HomeRootScreen.HOME && !stopped)
                                controlTarget = lastHomeFocus ?: "nav:${NAV_ITEMS.first()}"
                        }
                    }
                }
                wasChild = child
            }
        }
    }
    private var childReturnPending = false
    private var lastHomeFocus: String? = null

    val heroCard: HomeDisplayCard? get() = display.continueCards.firstOrNull()
    val heroButtons: List<String> get() = if (heroCard != null) listOf(HERO_RESUME, HERO_DETAIL) else emptyList()

    /** 只读重投影：个人记录读共享实例的实时状态；目录读 fixture 查询结果。 */
    fun recompute() {
        VisualHomeTestHooks.projectionRecomputes.incrementAndGet()
        val personal = browse.personal ?: return
        val catalog = catalogWorks.map { HomeCatalogEntry(it.id.value, it.title, it.artworkUri, it.artworkShape) }
        val configured = query.enabledSourceIds.isNotEmpty() && fixture.works.isNotEmpty()
        display = HomeProjection.project(catalog, catalogResult, configured,
            personal.continuing.map { it.toHomeEntry() }, personal.history.map { it.toHomeEntry() },
            offlineNow, loadFailed, !loadedOnce)
        status = statusText()
    }

    private var loadedOnce = false
    private var loadFailed = false

    private fun com.ichichuang.cinema.data.userdata.PersonalLibraryCard.toHomeEntry() =
        HomePersonalEntry(workId, title, sourceLabel, episodeId?.value, variantId?.value,
            snapshot?.positionMs, snapshot?.durationMs,
            snapshot?.watchState == WatchState.IN_PROGRESS, completedEpisode)

    fun contentRows(): List<Pair<String, List<HomeDisplayCard>>> = buildList {
        if (display.continueCards.size > 1) add(CONTINUE_ROW to display.continueCards.drop(1))
        if (display.recentCards.isNotEmpty()) add(RECENT_ROW to display.recentCards)
        // 目录行仅在有真实卡片时出现：完整空状态由首页“完整空”文案表达，不渲染空焦点区。
        if (display.catalogCards.isNotEmpty()) add(CATALOG_ROW to display.catalogCards)
    }

    suspend fun start() {
        val ticket = restore.reserveLoad()
        VisualHomeTestHooks.traceFocus("start reserveLoad")
        VisualHomeTestHooks.beforeLoad?.await()
        // 回调绑定主线程（API26+）；API24/25 用单参注册并在回调内跳回主线程。
        runCatching {
            val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
            if (android.os.Build.VERSION.SDK_INT >= 26)
                manager.registerDefaultNetworkCallback(networkCallback, android.os.Handler(android.os.Looper.getMainLooper()))
            else manager.registerDefaultNetworkCallback(networkCallback)
        }
        refreshOffline()
        val saved = runCatching { store.load() }.getOrNull()?.snapshot
        val snapshot = (saved ?: fixture.initial).copy(
            contextKey = contextKey, route = BrowseTopLevel.HOME.route, query = query,
            focusedWorkId = saved?.focusedWorkId, anchorWorkId = saved?.anchorWorkId, relativeOffset = 0f)
        val token = restore.completeLoad(ticket, snapshot)
        VisualHomeTestHooks.traceFocus("start completeLoad token=${token?.routeGeneration} saved=${saved != null}")
        install(snapshot, token)
    }

    private fun install(snapshot: BrowseSnapshot, token: RestoreToken?) {
        navigation = RestoreNavigationState(snapshot)
        checkpointToken = checkpoint.begin(navigation!!)
        restoreToken = token
        revision = maxOf(revision, snapshot.resultRevision ?: 0L) + 1
        loadFailed = false
        runCatching { fixture.query(query, BrowseTopLevel.LIBRARY, revision) }.fold({
            catalogWorks = it.first; catalogResult = it.second
        }, { catalogWorks = emptyList(); catalogResult = null; loadFailed = true })
        loadedOnce = true
        layoutToken = token?.let { restore.advanceLayout(it, revision) }
        recompute()
        if (token == null && focusedControl == null && controlTarget == null) controlTarget = "nav:首页"
        VisualHomeTestHooks.traceFocus("install token=${token?.routeGeneration} status=$status")
    }

    private fun refreshOffline() {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        val network = manager.activeNetwork
        offlineNow = network == null || manager.getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true
    }

    fun statusText(): String = when (display.contentStatus) {
        HomeContentStatus.UNCONFIGURED -> "尚未配置任何来源；已保存的个人记录仍可查看，可前往设置查看来源状态"
        HomeContentStatus.LOADING -> "正在读取首页数据"
        HomeContentStatus.OFFLINE -> "离线状态：已保存的个人记录仍可查看；在线片库暂无法核对"
        HomeContentStatus.PARTIAL -> "部分来源未查询完成或有失败（${display.failedSourceCount} 个失败）；结果不代表全部片库"
        HomeContentStatus.FAILED -> "首页数据读取失败，原记录保持；可重试"
        HomeContentStatus.READY -> if (display.totalKnown != null) "当前查询已完整核对，共 ${display.totalKnown} 部" else "当前查询已完整核对"
    }

    /** Settings slice projection, driven by the same observable read facts as the home screen. */
    fun settingsDisplay(): SettingsDisplayState = SettingsProjection.project(
        loading = display.contentStatus == HomeContentStatus.LOADING,
        loadFailed = display.contentStatus == HomeContentStatus.FAILED,
        activeCount = activeSourceCount,
        readStatus = display.contentStatus,
        failedSourceCount = display.failedSourceCount,
    )

    private fun stateNow(): RestoreNavigationState? = navigation?.let { state ->
        val focused = userTarget ?: focusedControl
        state.copy(snapshot = state.snapshot.copy(
            focusedWorkId = focused?.let { WorkId(it) }, anchorWorkId = focused?.let { WorkId(it) },
            relativeOffset = 0f, resultRevision = revision, savedAt = System.currentTimeMillis()))
    }

    private fun update() {
        val state = stateNow() ?: return
        navigation = state
        checkpointToken?.let { checkpoint.update(it, state) }
    }

    fun cardFocused(row: String, workId: String) {
        if (!foreground || stopped) return
        focusedControl = cardKey(row, workId); userTarget = null; controlTarget = null; update()
    }

    fun controlFocused(key: String) {
        if (!foreground || stopped) return
        focusedControl = key
        // 焦点已真实落定：清掉对应的待落焦意图，避免下一次按键从旧意图回退。
        if (controlTarget == key) controlTarget = null
        if (userTarget == key) userTarget = null
        update()
    }

    fun retry() {
        val ticket = restore.reserveLoad()
        VisualHomeTestHooks.traceFocus("retry reserveLoad")
        scope.launch {
            val saved = runCatching { store.load() }.getOrNull()?.snapshot ?: fixture.initial
            install(saved, restore.completeLoad(ticket, saved))
        }
    }

    fun openTopDestination(label: String) {
        when (label) {
            "设置" -> { screen = HomeRootScreen.SETTINGS; controlTarget = SETTINGS_ITEMS.first() }
            // R14：片库探针开启时进入原生片库切片；未开启保持 R13 浏览占位路径。
            "片库" -> if (library != null) { screen = HomeRootScreen.LIBRARY; library.childReturned() }
            else { screen = HomeRootScreen.BROWSE; browse.switch(BrowseTopLevel.LIBRARY) }
            "我的收藏" -> { screen = HomeRootScreen.BROWSE; browse.switch(BrowseTopLevel.FAVORITES) }
            "最近观看" -> { screen = HomeRootScreen.BROWSE; browse.switch(BrowseTopLevel.HISTORY) }
        }
    }

    fun openWork(workId: String, resume: Boolean) {
        lastHomeFocus = userTarget ?: focusedControl ?: controlTarget
        childReturnPending = true
        scope.launch { flush() }
        browse.openFromHome(WorkId(workId), resume)
    }

    fun toggleLargeText() { largeText = !largeText }

    /** Toggle the stored "减弱动效" preference; the merged signal follows the DataStore flow. */
    fun toggleReduceMotion() {
        val next = !reduceMotionSetting
        scope.launch { runCatching { settings.setReduceMotion(next) } }
    }

    fun activateSettingsItem(label: String) {
        when (label) {
            "大字模式" -> toggleLargeText()
            "筛选栏" -> browse.setFilterBarVisible(!browse.filterVisible)
            "列数" -> browse.changeColumns()
            "减弱动效" -> toggleReduceMotion()
            "刷新目录" -> browse.refresh()
            "重载首页" -> retry()
        }
    }

    fun settingsItemText(label: String): String = when (label) {
        "大字模式" -> if (largeText) "大字模式：开" else "大字模式：关"
        "筛选栏" -> if (browse.filterVisible) "筛选栏：显示" else "筛选栏：隐藏"
        "减弱动效" -> if (reduceMotionSetting) "减弱动效：开" else "减弱动效：关"
        else -> label
    }

    fun returnToHome() { screen = HomeRootScreen.HOME; if (controlTarget == null) controlTarget = focusedControl ?: "nav:首页" }

    /** 片库根画面返回：回到首页并把落焦意图交回“片库”导航条目。 */
    fun returnFromLibrary() { screen = HomeRootScreen.HOME; controlTarget = "nav:片库" }

    fun delivered(outcome: RestoreOutcome): String? = when (outcome) {
        is RestoreOutcome.ReadyToFocus -> outcome.workId.value
        is RestoreOutcome.Fallback -> (outcome.workId?.value ?: "nav:${NAV_ITEMS.first()}").also { if (outcome.workId == null) controlTarget = it }
        else -> null
    }

    fun observation(ready: Set<String>, pass: RestoreLayoutToken): RestoreObservation {
        val state = requireNotNull(navigation)
        val rows = contentRows().map { (row, cards) -> row to cards.map { WorkId(cardKey(row, it.workId)) } }
        val navRow = NAV_ITEMS.map { WorkId("nav:$it") }
        val hero = heroButtons.map { WorkId(it) }
        val status = buildList {
            if (display.contentStatus == HomeContentStatus.UNCONFIGURED) add(WorkId(GOTO_SETTINGS))
            if (display.contentStatus == HomeContentStatus.FAILED) add(WorkId(RETRY))
        }
        return RestoreObservation(query, catalogResult?.snapshotRevision,
            (listOfNotNull("导航" to navRow, "首行动作".takeIf { hero.isNotEmpty() }?.let { it to hero }) +
                rows.filter { it.second.isNotEmpty() }).toMap(),
            catalogResult?.coverage == QueryCoverage.COMPLETE,
            ready.map(::WorkId).toSet(), true, "导航", navRow, pass)
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
        try { if (checkpoint.hasPending) checkpointToken?.let { checkpoint.flush(it) } } catch (_: Exception) {}
    }

    private val deferredKeys = mutableSetOf<Int>()

    /** 每个平台按键先同步作废待恢复焦点，再执行路由/数据动作；确认键去重防双导航。 */
    fun key(event: KeyEvent, exit: () -> Unit): Boolean {
        val code = event.keyCode
        if (code !in setOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK)) return false
        VisualHomeTestHooks.traceFocus("按键 code=$code action=${event.action} screen=$screen")
        // UP 必须成对清理本类 held 集，即使画面在 DOWN→UP 之间切到了片库；
        // 片库根的 UP 同步转发，片库自身的 held 集同样成对清理（防“隔次吞确认键”）。
        if (event.action == KeyEvent.ACTION_UP) {
            held.remove(code)
            if (screen == HomeRootScreen.LIBRARY && library != null && !browse.isDetail && !browse.isPlayer)
                library.key(event) { returnFromLibrary() }
            if (deferredKeys.remove(code)) return false
            return true
        }
        if (event.action != KeyEvent.ACTION_DOWN) return true
        // R14 片库根画面：DOWN/重复按键交片库运行时自管（其内部自持去重与面板捕获）。
        if (screen == HomeRootScreen.LIBRARY && library != null && !browse.isDetail && !browse.isPlayer) {
            val consumed = library.key(event) { returnFromLibrary() }
            if (consumed) return true
        }
        // 详情／播放器是浏览运行时的子层：全部按键让位，首页不得在子层上抢返回或移动焦点。
        if (browse.isDetail || browse.isPlayer) { deferredKeys.add(code); return false }
        // 浏览根：方向/确认键交给浏览；弹层打开时返回也交给浏览先关弹层。
        // 仅“无弹层的浏览根返回”由首页接管 —— 浏览尽力保存现场后回首页，保存失败不困死用户。
        // 让位的键不进入本类 held 去重集（其 UP 同样让位），否则会吞掉浏览侧每隔一次的确认键。
        if (screen == HomeRootScreen.BROWSE) {
            if (code != KeyEvent.KEYCODE_BACK || browse.modal) { deferredKeys.add(code); return false }
            restore.onUserInput(); inputEpoch++; restoreToken = null
            browse.back({})
            scope.launch {
                var waited = 0
                while (browse.busy && waited < 2_000) { delay(20); waited += 20 }
                if (!browse.modal) returnToHome()
            }
            return true
        }
        if (code in setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK) &&
            (!held.add(code) || event.repeatCount > 0)) return true
        restore.onUserInput(); inputEpoch++; restoreToken = null
        if (code == KeyEvent.KEYCODE_BACK) {
            when (screen) {
                HomeRootScreen.SETTINGS -> { screen = HomeRootScreen.HOME; controlTarget = "nav:设置" }
                HomeRootScreen.HOME -> exit()
                else -> return false
            }
            return true
        }
        if (screen == HomeRootScreen.SETTINGS) { settingsKey(code); return true }
        homeKey(code)
        return true
    }

    private fun settingsKey(code: Int) {
        val items = SETTINGS_ITEMS
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
            // 激活以可见焦点为准；未落焦时退回待落焦意图。
            activateSettingsItem(focusedControl?.takeIf { it in items } ?: controlTarget?.takeIf { it in items } ?: items.first())
            return
        }
        val index = items.indexOf(controlTarget).takeIf { it >= 0 } ?: items.indexOf(focusedControl).coerceAtLeast(0)
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> controlTarget = items[(index - 1).coerceAtLeast(0)]
            KeyEvent.KEYCODE_DPAD_DOWN -> controlTarget = items[(index + 1).coerceAtMost(items.lastIndex)]
        }
    }

    private fun homeKey(code: Int) {
        val zones = focusZones()
        if (zones.isEmpty()) return
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
            // 激活以“用户可见的已落焦”为准：陈旧的待落焦意图不得替用户选目的地。
            val visible = focusedControl ?: userTarget ?: controlTarget
            if (visible != null && zones.any { visible in it.second }) { activate(visible); return }
        }
        // 位置解析优先“待落焦意图”：连发按键时已落焦回报可能滞后一帧，不能让序列回退。
        val currentKey = userTarget ?: controlTarget ?: focusedControl ?: zones.first().second.first()
        val location = zones.indexOfFirst { currentKey in it.second }
        if (location < 0) { target(zones.first().second.first()); return }
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
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> activate(currentKey)
        }
    }

    private fun target(key: String) {
        // 卡片键（行/作品ID）走 userTarget 以带动行内滚动；控件键走 controlTarget 直达。
        if ('/' in key && !key.startsWith("nav:")) userTarget = key else controlTarget = key
    }

    private fun activate(key: String) {
        when {
            key.startsWith("nav:") -> openTopDestination(key.removePrefix("nav:"))
            key.startsWith("action:") -> when (key.removePrefix("action:")) {
                GOTO_SETTINGS -> openTopDestination("设置")
                RETRY -> retry()
            }
            key == HERO_RESUME -> heroCard?.let { openWork(it.workId, resume = true) }
            key == HERO_DETAIL -> heroCard?.let { openWork(it.workId, resume = false) }
            else -> {
                val row = key.substringBefore('/')
                openWork(key.substringAfter('/'), resume = row == CONTINUE_ROW)
            }
        }
    }

    /** D-pad 可达的有序焦点区：导航 → 首行动作 → 内容各行 → 状态动作。 */
    fun focusZones(): List<Pair<String, List<String>>> = buildList {
        add("导航" to NAV_ITEMS.map { "nav:$it" })
        if (heroButtons.isNotEmpty()) add("首行" to heroButtons)
        contentRows().forEach { (row, cards) -> add(row to cards.map { cardKey(row, it.workId) }) }
        if (display.contentStatus == HomeContentStatus.UNCONFIGURED) add("状态" to listOf("action:$GOTO_SETTINGS"))
        if (display.contentStatus == HomeContentStatus.FAILED) add("状态" to listOf("action:$RETRY"))
    }
}
