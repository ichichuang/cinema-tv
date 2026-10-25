package com.ichichuang.cinema.feature.browse

import android.content.Context
import android.view.KeyEvent
import androidx.compose.runtime.*
import com.ichichuang.cinema.core.navigation.*
import com.ichichuang.cinema.domain.model.catalog.*
import com.ichichuang.cinema.domain.model.userdata.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Bounded controls for native delayed-delivery tests, never Intent extras or an ordinary UI route. */
internal object BrowseProbeTestHooks {
    var holdWindowReady by mutableStateOf(false)
    var disableWorkFocus by mutableStateOf(false)
    val focusTrace = java.util.ArrayDeque<String>()
    fun traceFocus(value: String) { if (focusTrace.size >= 32) focusTrace.removeFirst(); focusTrace.addLast(value) }
    val failedFocusRequests = java.util.concurrent.atomic.AtomicInteger(0)
    val activeLeases = java.util.concurrent.atomic.AtomicInteger(0)
    var beforeSave: CompletableDeferred<Unit>? = null
    val waitingSaves = java.util.concurrent.atomic.AtomicInteger(0)
    var beforeTopLevelLoad: CompletableDeferred<Unit>? = null
    var beforeLoad: CompletableDeferred<Unit>? = null
    var blockPlacement by mutableStateOf(false)
}

/** Lives strictly inside DebugBrowseProbeGate.withOwnedProfile and its structured child scope. */
internal class BrowseProbeRuntime(
    private val context: Context,
    private val store: BrowseNavigationStore,
    val scope: CoroutineScope,
    private val fixture: BrowseProbeFixture,
    personalDatabase: com.ichichuang.cinema.data.local.CinemaDatabase? = null,
) {
    val personal = personalDatabase?.let { com.ichichuang.cinema.feature.personal.PersonalLibraryRuntime(context, it, scope, fixture) }
    val restore = BrowseRestoreCoordinator()
    private val checkpoint = BrowseCheckpointCoordinator(object : BrowseNavigationStore by store {
        override suspend fun saveActive(state: RestoreNavigationState): CommitResult {
            BrowseProbeTestHooks.beforeSave?.let { hold ->
                BrowseProbeTestHooks.waitingSaves.incrementAndGet()
                try { hold.await() } finally { BrowseProbeTestHooks.waitingSaves.decrementAndGet() }
            }
            saveInFlight = true
            committingSnapshot = state.snapshot
            trace()
            return try {
                if (fixture.saveDelayMillis > 0) delay(fixture.saveDelayMillis)
                store.saveActive(state)
            } finally { saveInFlight = false; committingSnapshot = null; trace() }
        }
    }, scope, onResult = { commit ->
        lastCommit = commit
        status = if (commit != CommitResult.Committed) "浏览位置尚未保存，请保留当前页面后重试"
            else if (result?.coverage == QueryCoverage.COMPLETE) "当前测试来源已完整核对"
            else "当前测试来源仅有部分结果，缺失不表示已移除"
        trace()
    })
    private var saveInFlight = false
    private var committingSnapshot: com.ichichuang.cinema.domain.model.userdata.BrowseSnapshot? = null
    private var checkpointToken: CheckpointToken? = null
    var navigation by mutableStateOf<RestoreNavigationState?>(null); private set
    var items by mutableStateOf<List<BrowseProbeFixture.Work>>(emptyList()); private set
    var result by mutableStateOf<CatalogResult?>(null); private set
    var restoreToken by mutableStateOf<RestoreToken?>(null); private set
    var layoutToken by mutableStateOf<RestoreLayoutToken?>(null); private set
    var columns by mutableIntStateOf(5); private set
    var viewportWidth by mutableIntStateOf(0); private set
    var viewportHeight by mutableIntStateOf(0); private set
    fun viewportChanged(width: Int, height: Int) {
        if (!foreground || stopped) return
        if (width == viewportWidth && height == viewportHeight) return
        val measured = viewportWidth > 0 && viewportHeight > 0
        viewportWidth = width; viewportHeight = height
        if (!measured || width <= 0 || height <= 0) return
        val revision = result?.snapshotRevision ?: return
        val token = restoreToken
        // Resize is an actual measurement boundary. An undelivered request keeps its
        // original query/target, invalidates old placement, then scrolls the anchor again.
        val nextPass = token?.let { restore.advanceLayout(it, revision) }
        if (nextPass != null) { layoutToken = nextPass; return }
        // A completed/current user focus can be preserved under the new geometry. A
        // cancelled restore with control focus must never revive its old saved poster.
        if (focused == null && token == null) return
        val state = stateNow() ?: return
        val target = focused ?: state.snapshot.focusedWorkId
        val index = items.indexOfFirst { it.id == target }.takeIf { it >= 0 }
            ?: items.indexOfFirst { it.id == state.snapshot.anchorWorkId }
        if (index < 0) return
        val visibleRows = (height / (124f * context.resources.displayMetrics.density)).toInt().coerceAtLeast(1)
        val oldAnchor = items.indexOfFirst { it.id == state.snapshot.anchorWorkId }
        val fits = oldAnchor >= 0 && kotlin.math.abs(index / columns - oldAnchor / columns) < visibleRows
        val anchored = if (fits) state else state.copy(snapshot = state.snapshot.copy(
            anchorWorkId = items[(index / columns) * columns].id, relativeOffset = 0f))
        navigation = anchored
        checkpointToken?.let { checkpoint.update(it, anchored) }
        restoreToken = restore.begin(anchored.snapshot)
        layoutToken = restoreToken?.let { restore.advanceLayout(it, revision) }
    }
    var placedColumns by mutableIntStateOf(0); private set
    fun placed(columns: Int) { placedColumns = columns }
    var status by mutableStateOf("正在读取上次浏览位置"); private set
    var selected by mutableStateOf<WorkId?>(null); private set
    var focused by mutableStateOf<WorkId?>(null); private set
    var focusedControl by mutableStateOf<String?>(null); private set
    var modal by mutableStateOf(false); private set
    var filterVisible by mutableStateOf(true); private set
    var userTarget by mutableStateOf<WorkId?>(null); private set
    var controlTarget by mutableStateOf<String?>(null); private set
    var inputEpoch by mutableLongStateOf(0); private set
    var busy by mutableStateOf(false); private set
    var lastCommit by mutableStateOf<CommitResult?>(null); private set
    private var revision = 1L
    var visibleAnchor by mutableStateOf<WorkId?>(null); private set
    var visibleOffset by mutableFloatStateOf(0f); private set
    private var anchor: WorkId? = null
    private var offset = 0f
    private var modalInvoker: String? = null
    private var modalInvokerRow: List<String> = emptyList()
    var foreground by mutableStateOf(true); private set
    private val held = mutableSetOf<Int>()
    private var stopped = false
    val top: BrowseTopLevel get() = BrowseTopLevel.entries.firstOrNull { it.route == navigation?.snapshot?.route } ?: BrowseTopLevel.LIBRARY
    val isPlayer get() = navigation?.returnChain?.lastOrNull() == BrowseChildRoute.PLAYER
    val isDetail get() = navigation?.returnChain?.isNotEmpty() == true

    /** R13 首页共享同一个人库实例；列表变化时同步通知首页重投影（只读，无第二写入者）。 */
    var onPersonalChanged: (() -> Unit)? = null

    suspend fun start() {
        val ticket = restore.reserveLoad() // Before any asynchronous Room/fixture wait.
        BrowseProbeTestHooks.beforeLoad?.await()
        personal?.start { personalListsChanged(); onPersonalChanged?.invoke() }
        val saved = store.loadActive() ?: RestoreNavigationState(fixture.initial)
        val token = restore.completeLoad(ticket, saved.snapshot)
        // A cancelled initial load may still populate an empty page after user input,
        // but it must never replace a newer explicit query/context already installed.
        if (token != null || navigation == null) install(saved, token)
        if (isPlayer && personal != null) navigation?.snapshot?.focusedWorkId?.let { id ->
            if (personal.prepare(id.value)) personal.detailFocus = "播放暂停"
            else {
                // A persisted PLAYER is an origin context, never permission to map an
                // unavailable timeline or leave an unusable player above the choice UI.
                selected = id; personal.detailFocus = "详情返回"
                val detail = requireNotNull(navigation).copy(returnChain = listOf(BrowseChildRoute.DETAIL))
                navigation = detail; checkpointToken = checkpoint.begin(detail); flush()
            }
        }
    }
    private fun install(state: RestoreNavigationState, token: RestoreToken?) {
        val priorControl = focusedControl
        navigation = state
        checkpointToken = checkpoint.begin(state)
        anchor = state.snapshot.anchorWorkId; offset = state.snapshot.relativeOffset
        focused = null; focusedControl = if (token == null) priorControl else null
        restoreToken = token
        val query = requireNotNull(state.snapshot.query)
        // A new Activity/process must not restart below the durable result generation.
        revision = maxOf(revision, state.snapshot.resultRevision ?: 0L)
        if (revision < Long.MAX_VALUE) revision++
        val found = personalResult(query) ?: fixture.query(query, top, revision)
        items = found.first; result = found.second
        if (personal != null && top != BrowseTopLevel.LIBRARY && state.snapshot.focusedWorkId == null && items.isNotEmpty()) {
            val first = items.first().id
            val initial = state.copy(snapshot = state.snapshot.copy(focusedWorkId = first, anchorWorkId = first))
            navigation = initial; checkpointToken = checkpoint.begin(initial)
            restoreToken = token?.let { restore.begin(initial.snapshot) }
        }
        layoutToken = restoreToken?.let { restore.advanceLayout(it, revision) }
        status = if (result?.coverage == QueryCoverage.COMPLETE) "当前测试来源已完整核对" else "当前测试来源仅有部分结果，缺失不表示已移除"
        if (token == null) controlTarget = priorControl ?: controlTarget ?: "片库"
        trace()
    }
    private fun stateNow(): RestoreNavigationState? = navigation?.let { state -> state.copy(snapshot = state.snapshot.copy(
        focusedWorkId = focused ?: state.snapshot.focusedWorkId,
        anchorWorkId = anchor ?: state.snapshot.anchorWorkId, relativeOffset = offset,
        resultRevision = result?.snapshotRevision ?: state.snapshot.resultRevision, savedAt = System.currentTimeMillis())) }
    private fun update() { val state = stateNow() ?: return; navigation = state; checkpointToken?.let { checkpoint.update(it, state) }; trace() }
    fun positioned(id: WorkId?, relativeOffset: Float) { if (!foreground || stopped) return; visibleAnchor = id; visibleOffset = relativeOffset; if (focused == null) return; anchor = id; offset = relativeOffset; if (focused != null) update() }
    fun focused(id: WorkId) { if (!foreground || stopped) return; focused = id; focusedControl = null; userTarget = null; controlTarget = null; update() }
    fun controlFocused(name: String) { if (!foreground || stopped || modal) return; focused = null; focusedControl = name; if (controlTarget == name) controlTarget = null; trace() }
    suspend fun flush(): CommitResult {
        val token = checkpointToken ?: return CommitResult.RejectedStale
        if (!checkpoint.hasPending) return lastCommit ?: checkpoint.lastResult ?: CommitResult.RejectedStale
        val result = checkpoint.flush(token); lastCommit = result
        if (result != CommitResult.Committed) status = "浏览位置尚未保存，请保留当前页面后重试"
        trace(); return result
    }
    fun background() { scope.launch { flush() } }
    fun pause() { foreground = false; personal?.pause(); background() }
    fun resume() { if (!stopped) foreground = true }
    suspend fun shutdown() { stopped = true; try { if (checkpoint.hasPending) flush() } finally { personal?.close() } }
    private val routeChanges = Mutex()
    private var routeRequest = 0L
    /** Capture before launching: later physical actions supersede an unexecuted navigation. */
    private fun leave(cancelOnInput: Boolean = false, action: suspend (() -> Boolean) -> Unit) {
        val request = ++routeRequest
        val epoch = inputEpoch
        scope.launch {
            routeChanges.withLock {
                val current = { !stopped && request == routeRequest && (!cancelOnInput || epoch == inputEpoch) }
                if (!current()) return@withLock
                busy = true
                try {
                    if ((!checkpoint.hasPending || flush() == CommitResult.Committed) && current()) action(current)
                } finally { busy = false }
            }
        }
    }
    fun switch(top: BrowseTopLevel) {
        val ticket = restore.reserveLoad()
        leave { current ->
            BrowseProbeTestHooks.beforeTopLevelLoad?.await()
            val saved = store.loadLatest(top) ?: RestoreNavigationState(fixture.initial.copy(
                contextKey = top.contextKey(requireNotNull(fixture.initial.query)), route = top.route,
                focusedWorkId = null, anchorWorkId = null, relativeOffset = 0f))
            if (current()) install(saved.copy(returnChain = emptyList()), restore.completeLoad(ticket, saved.snapshot))
        }
    }
    fun changeQuery() {
        val ticket = restore.reserveLoad()
        leave {
            val next = fixture.alternative.copy(route = top.route, contextKey = top.contextKey(requireNotNull(fixture.alternative.query)))
            require(next.query?.queryId != navigation?.snapshot?.query?.queryId)
            install(RestoreNavigationState(next), restore.completeLoad(ticket, next))
        }
    }
    fun openDetail() {
        val id = focused ?: return
        if (personal != null && top == BrowseTopLevel.CONTINUE) { openPlayer(id); return }
        leave(cancelOnInput = true) {
            selected = id
            personal?.detailFocus = "详情返回"
            val state = requireNotNull(stateNow()).enter(BrowseChildRoute.DETAIL)
            navigation = state; checkpointToken = checkpoint.begin(state)
            flush()
        }
    }

    /** R13 首页卡片入口：沿用既有详情/播放语义；续播走真实检查点并保持暂停恢复。 */
    fun openFromHome(workId: WorkId, resume: Boolean) {
        leave(cancelOnInput = true) { current ->
            focused = workId; focusedControl = null
            if (resume && personal != null) {
                val prepared = personal.prepare(workId.value)
                if (!current()) return@leave
                selected = workId
                personal.detailFocus = if (prepared) "播放暂停" else "详情返回"
                val child = if (prepared) BrowseChildRoute.PLAYER else BrowseChildRoute.DETAIL
                val state = requireNotNull(stateNow()).enter(child)
                navigation = state; checkpointToken = checkpoint.begin(state)
                flush()
            } else openDetail()
        }
    }

    /** R13 候选设置入口的真实筛选栏开关；与既有移除入口同域，不新增持久层。 */
    fun setFilterBarVisible(visible: Boolean) { filterVisible = visible }
    fun back(exit: () -> Unit) {
        if (personal?.confirmation == true) { personal.confirmation = false; personal.detailFocus = "删除观看记录"; return }
        if (modal) { modal = false; controlTarget = modalReturnControl(modalInvoker, modalInvokerRow, toolbarControls(), "片库"); return }
        if (!isDetail) { leave { exit() }; return }
        val ticket = restore.reserveLoad()
        leave {
            if (isPlayer && personal != null) {
                val saved = personal.boundary()
                if (saved != null && saved != CommitResult.Committed) return@leave
                personal.detailFocus = "详情返回"
            }
            val state = requireNotNull(navigation?.back())
            install(state, restore.completeLoad(ticket, state.snapshot))
        }
    }
    fun toolbarControls() = listOf("片库", "收藏", "历史") + (if (personal != null) listOf("继续观看") else emptyList()) + (if (filterVisible) listOf("筛选") else emptyList()) + listOf("列数", "刷新") + (if (personal != null && top == BrowseTopLevel.CONTINUE) listOf("移出继续观看") else emptyList())
    fun openModal() { modalInvoker = focusedControl ?: "筛选"; modalInvokerRow = toolbarControls(); modal = true; focused = null; focusedControl = null }
    fun removeModalInvoker() { filterVisible = false }
    fun changeColumns() {
        val state = stateNow() ?: return
        val next = if (columns == 5) 3 else 5
        val index = items.indexOfFirst { it.id == state.snapshot.focusedWorkId }
        // A new grid geometry invalidates old row/pixel coordinates, while preserving the opaque target.
        val newAnchor = if (index >= 0) items[(index / next) * next].id else state.snapshot.anchorWorkId
        val rebased = state.copy(snapshot = state.snapshot.copy(anchorWorkId = newAnchor, relativeOffset = 0f))
        navigation = rebased
        checkpointToken?.let { checkpoint.update(it, rebased) }
        restoreToken = restore.begin(rebased.snapshot)
        layoutToken = restoreToken?.let { restore.advanceLayout(it, requireNotNull(result).snapshotRevision) }
        columns = next
    }
    fun refresh() {
        // Metadata/source refresh preserves the installed active order and focus. No restore request.
        result = result?.let { CatalogResult.create(it.queryId, it.items, it.coverage, it.failedSourceIds,
            it.unknownFieldCount, it.knownMatchedCount, it.hasMore, if (revision < Long.MAX_VALUE) ++revision else revision) }
        restoreToken = null
    }
    fun delivered(outcome: RestoreOutcome): WorkId? = when (outcome) {
        is RestoreOutcome.ReadyToFocus -> outcome.workId
        is RestoreOutcome.Fallback -> outcome.workId.also { if (it == null) controlTarget = "片库" }
        else -> null
    }
    fun observation(ready: Set<WorkId>, pass: RestoreLayoutToken, pageReady: Boolean): RestoreObservation {
        val snapshot = requireNotNull(navigation).snapshot
        val original = fixture.savedOrder
        val oldIndex = original.indexOf(snapshot.focusedWorkId).coerceAtLeast(0)
        val rowStart = (oldIndex / columns) * columns
        val row = original.drop(rowStart).take(columns)
        return RestoreObservation(snapshot.query, result?.snapshotRevision,
            mapOf("原行" to items.map { it.id }.filter { it in row }, "其余" to items.map { it.id }.filter { it !in row }),
            result?.coverage == QueryCoverage.COMPLETE, ready, pageReady, "原行", row, pass)
    }
    /** Every platform key invalidates pending restore synchronously, before any route/data action. */
    fun key(event: KeyEvent, exit: () -> Unit): Boolean {
        val code = event.keyCode
        if (code !in setOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK)) return false
        if (event.action == KeyEvent.ACTION_UP) { held.remove(code); return true }
        if (event.action != KeyEvent.ACTION_DOWN) return true
        if (code in setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK) &&
            (!held.add(code) || event.repeatCount > 0)) return true
        restore.onUserInput(); inputEpoch++; restoreToken = null
        if (code == KeyEvent.KEYCODE_BACK) { back(exit); return true }
        if (modal) { if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) back(exit); return true }
        if (isDetail) {
            if (personal != null) {
                val actions = detailActions()
                val index = actions.indexOf(personal.detailFocus).coerceAtLeast(0)
                if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_LEFT) personal.detailFocus = actions[(index - 1).coerceAtLeast(0)]
                if (code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_RIGHT) personal.detailFocus = actions[(index + 1).coerceAtMost(actions.lastIndex)]
                if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) activateDetail(personal.detailFocus, exit)
            }
            return true
        }
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
            when (focusedControl) {
                "片库" -> switch(BrowseTopLevel.LIBRARY); "收藏" -> switch(BrowseTopLevel.FAVORITES)
                "历史" -> switch(BrowseTopLevel.HISTORY); "继续观看" -> switch(BrowseTopLevel.CONTINUE); "筛选" -> openModal()
                "列数" -> changeColumns(); "刷新" -> refresh()
                "移出继续观看" -> navigation?.snapshot?.focusedWorkId?.let { id -> scope.launch { personal?.remove(id.value); personalListsChanged() } }
                else -> openDetail()
            }
            return true
        }
        val controls = toolbarControls()
        val controlIndex = controls.indexOf(focusedControl)
        if (controlIndex >= 0) {
            when (code) {
                KeyEvent.KEYCODE_DPAD_DOWN -> userTarget = items.firstOrNull()?.id
                KeyEvent.KEYCODE_DPAD_LEFT -> controlTarget = controls[(controlIndex - 1).coerceAtLeast(0)]
                KeyEvent.KEYCODE_DPAD_RIGHT -> controlTarget = controls[(controlIndex + 1).coerceAtMost(controls.lastIndex)]
            }
        } else {
            val index = items.indexOfFirst { it.id == (userTarget ?: focused) }
            if (index < 0) controlTarget = "片库"
            else {
                val next = when (code) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> index - 1; KeyEvent.KEYCODE_DPAD_RIGHT -> index + 1
                    KeyEvent.KEYCODE_DPAD_UP -> index - columns; else -> index + columns
                }
                if (next < 0) controlTarget = "片库" else userTarget = items[next.coerceAtMost(items.lastIndex)].id
            }
        }
        return true
    }

    private fun personalResult(query: CatalogQuery): Pair<List<BrowseProbeFixture.Work>, CatalogResult>? {
        val owner = personal ?: return null
        val cards = when (top) {
            BrowseTopLevel.LIBRARY, BrowseTopLevel.HOME -> return null
            BrowseTopLevel.FAVORITES -> owner.favorites
            BrowseTopLevel.HISTORY -> owner.history
            BrowseTopLevel.CONTINUE -> owner.continuing
        }
        val works = cards.map { card ->
            val known = fixture.works.firstOrNull { it.id.value == card.workId }
            known?.copy(title = card.title) ?: BrowseProbeFixture.Work(WorkId(card.workId), card.title,
                SourceId(card.sourceLabel?.takeIf { it.isNotBlank() } ?: "unavailable-personal-source"),
                com.ichichuang.cinema.domain.filter.StrictWorkFacts(), owner.isFavorite(card.workId), card.snapshot != null)
        }
        val result = CatalogResult.create(query.queryId, works.map { CatalogItem(it.id, it.title, it.facts.year, it.facts.originalLanguages, it.facts.countries) },
            QueryCoverage.COMPLETE, knownMatchedCount = works.size.toLong(), hasMore = false, snapshotRevision = revision)
        return works to result
    }
    fun personalListsChanged() {
        val state = navigation ?: return
        if (top == BrowseTopLevel.LIBRARY) return
        val previous = items
        val priorId = focused ?: state.snapshot.focusedWorkId
        val priorIndex = previous.indexOfFirst { it.id == priorId }
        val query = requireNotNull(state.snapshot.query)
        // Room invalidates flows for table writes even when this page's projection is unchanged.
        // Retain the original layout proof for identical data; a real new projection requires
        // a fresh placement pass tied to the existing, still-cancellable restore intent.
        if (personalResult(query)?.first == items) { trace(); return }
        if (revision < Long.MAX_VALUE) revision++
        val found = personalResult(query) ?: return
        items = found.first; result = found.second
        restoreToken?.let { layoutToken = restore.advanceLayout(it, revision) }
        if (priorId != null && items.none { it.id == priorId }) {
            val neighbor = items.getOrNull(priorIndex.coerceAtLeast(0).coerceAtMost((items.size - 1).coerceAtLeast(0)))?.id
            navigation = state.copy(snapshot = state.snapshot.copy(focusedWorkId = neighbor, anchorWorkId = neighbor, relativeOffset = 0f))
            anchor = neighbor; offset = 0f
            checkpointToken?.let { checkpoint.update(it, requireNotNull(navigation)) }
            restore.onUserInput(); restoreToken = null
            if (!isDetail) { focused = null; if (neighbor != null) userTarget = neighbor else controlTarget = toolbarControls().first() }
        }
        trace()
    }
    fun detailActions(): List<String> = when {
        personal?.confirmation == true -> listOf("取消删除", "确认删除")
        isPlayer -> listOf("播放暂停", "重试播放", "播放器返回")
        else -> listOf("详情返回", "收藏操作", "播放本地测试媒体", "移出继续观看", "删除观看记录") +
            (if (personal?.undoWork != null) listOf("撤销取消收藏") else emptyList()) +
            (if (personal?.versionChoiceWork != null && personal.versionChoiceWork == (selected ?: navigation?.snapshot?.focusedWorkId)?.value) listOf("从头准备当前版本") else emptyList())
    }
    fun activateDetail(label: String, exit: () -> Unit) {
        val owner = personal ?: return
        val id = selected ?: navigation?.snapshot?.focusedWorkId ?: return
        when (label) {
            "详情返回", "播放器返回" -> back(exit)
            "删除观看记录" -> { owner.confirmation = true; owner.detailFocus = "取消删除" }
            "取消删除" -> { owner.confirmation = false; owner.detailFocus = "删除观看记录" }
            "播放本地测试媒体" -> openPlayer(id)
            "从头准备当前版本" -> openPlayer(id, fromBeginning = true)
            "播放暂停" -> owner.togglePlaying()
            "重试播放" -> scope.launch { owner.retry() }
            else -> scope.launch {
                when (label) {
                    "收藏操作" -> owner.favorite(id.value)
                    "移出继续观看" -> owner.remove(id.value)
                    "确认删除" -> { owner.delete(id.value); owner.confirmation = false; owner.detailFocus = "删除观看记录" }
                    "撤销取消收藏" -> { if (owner.undo() == CommitResult.Committed) owner.detailFocus = "收藏操作" }
                }
                personalListsChanged()
            }
        }
    }
    private fun openPlayer(id: WorkId, fromBeginning: Boolean = false) {
        val owner = personal ?: return
        leave(cancelOnInput = true) { current ->
            val prepared = owner.prepare(id.value, fromBeginning)
            if (!current()) return@leave
            if (prepared) {
                selected = id; owner.detailFocus = "播放暂停"
                val state = requireNotNull(stateNow()).enter(BrowseChildRoute.PLAYER)
                navigation = state; checkpointToken = checkpoint.begin(state); flush()
            } else if (!isDetail) {
                // Continue opens the same existing detail route when explicit version
                // choice is required, preserving its original top-level return context.
                selected = id; owner.detailFocus = "详情返回"
                val state = requireNotNull(stateNow()).enter(BrowseChildRoute.DETAIL)
                navigation = state; checkpointToken = checkpoint.begin(state); flush()
            }
        }
    }

    /** R15 详情切片：精确（作品,剧集,版本）已由既有管线就绪后进入播放器子层。 */
    fun enterPlayerPrepared(id: WorkId) {
        val owner = personal ?: return
        leave(cancelOnInput = true) {
            selected = id; owner.detailFocus = "播放暂停"
            val state = requireNotNull(stateNow()).enter(BrowseChildRoute.PLAYER)
            navigation = state; checkpointToken = checkpoint.begin(state); flush()
        }
    }

    private fun trace() {
        val snapshot = navigation?.snapshot
        File(context.noBackupFilesDir, "round11-browse-trace.json").writeText(org.json.JSONObject()
            .put("pid", android.os.Process.myPid()).put("route", snapshot?.route)
            .put("detail", isDetail).put("focused", focused?.value).put("control", focusedControl)
            .put("context", snapshot?.contextKey).put("anchor", anchor?.value).put("offset", offset)
            .put("snapshot", snapshot?.let(com.ichichuang.cinema.data.local.codec.BrowseSnapshotCodec::encode))
            .put("viewportWidth", viewportWidth).put("viewportHeight", viewportHeight)
            .put("waitingSave", saveInFlight)
            .put("committingSnapshot", committingSnapshot?.let(com.ichichuang.cinema.data.local.codec.BrowseSnapshotCodec::encode))
            .put("commit", lastCommit?.toString() ?: checkpoint.lastResult?.toString()).toString())
    }
}
