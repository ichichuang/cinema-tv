package com.ichichuang.cinema.feature.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.ichichuang.cinema.core.navigation.*
import com.ichichuang.cinema.domain.model.catalog.WorkId

@Composable
internal fun BrowseProbeScreen(runtime: BrowseProbeRuntime, onExit: () -> Unit) {
    val grid = rememberLazyGridState()
    val windowFocused = LocalWindowInfo.current.isWindowFocused && runtime.foreground && !BrowseProbeTestHooks.holdWindowReady
    val requesters = remember { mutableMapOf<WorkId, FocusRequester>() }
    val controls = remember { mutableMapOf<String, FocusRequester>() }
    val token = runtime.restoreToken
    val pass = runtime.layoutToken
    val result = runtime.result
    val items = runtime.items
    val columns = runtime.columns
    val snapshot = runtime.navigation?.snapshot
    val viewportWidth = runtime.viewportWidth
    val viewportHeight = runtime.viewportHeight
    var anchorPositioned by remember(pass, viewportWidth, viewportHeight) { mutableStateOf(false) }
    val ready = remember(pass) { mutableSetOf<WorkId>() }
    var pageReady by remember(pass) { mutableStateOf(false) }
    val epoch = runtime.inputEpoch
    var pendingAction by remember(pass) { mutableStateOf<RestoreOutcome?>(null) }
    fun requestRestore() {
        BrowseProbeTestHooks.traceFocus("窗口=$windowFocused token=${token?.routeGeneration}/${runtime.restoreToken?.routeGeneration} pass=$pass current=${runtime.layoutToken} revision=${runtime.result?.snapshotRevision} viewport=${grid.layoutInfo.viewportSize} first=${grid.firstVisibleItemIndex} ready=${ready.size} 待确认=$pendingAction")
        if (!anchorPositioned || !windowFocused || runtime.isDetail || runtime.modal || token == null || pass == null ||
            BrowseProbeTestHooks.blockPlacement || runtime.restoreToken != token || runtime.layoutToken != pass ||
            runtime.result?.snapshotRevision != pass.resultRevision) return
        val outcome = pendingAction ?: runtime.restore.observe(token, runtime.observation(ready.toSet(), pass, pageReady))
        BrowseProbeTestHooks.traceFocus("结果=$outcome")
        // Even an unacknowledged delivery must be backed by this pass's live nodes.
        when (outcome) {
            is RestoreOutcome.ReadyToFocus -> if (outcome.workId !in ready ||
                (outcome.anchorWorkId != null && outcome.anchorWorkId !in ready)) return
            is RestoreOutcome.Fallback -> if (outcome.workId?.let { it !in ready } ?: !pageReady) return
            else -> return
        }
        val requester = when (outcome) {
            is RestoreOutcome.ReadyToFocus -> requesters[outcome.workId]
            is RestoreOutcome.Fallback -> outcome.workId?.let { requesters[it] } ?: controls["片库"]
            else -> return
        }
        // B delivers one intent. A false native focus acknowledgement retains that intent,
        // scoped to its ORIGINAL route/query/layout until a real readiness event retries it.
        pendingAction = outcome
        val accepted = requester?.requestFocus()
        BrowseProbeTestHooks.traceFocus("焦点确认=$accepted")
        if (accepted == true) {
            pendingAction = null
            val height = grid.layoutInfo.viewportSize.height
            if (height > 0) runtime.positioned(items.getOrNull(grid.firstVisibleItemIndex)?.id,
                -grid.firstVisibleItemScrollOffset.toFloat() / height)
        } else BrowseProbeTestHooks.failedFocusRequests.incrementAndGet()
    }
    LaunchedEffect(windowFocused, pass, BrowseProbeTestHooks.disableWorkFocus) { if (windowFocused) requestRestore() }
    LaunchedEffect(pass, token, viewportWidth, viewportHeight) {
        if (pass != null && token != null && snapshot != null && viewportWidth > 0 && viewportHeight > 0 &&
            runtime.restoreToken == token && runtime.layoutToken == pass) {
            val positioning = runtime.restore.positioning(token, runtime.observation(emptySet(), pass, false))
                ?: return@LaunchedEffect
            if (positioning.workId != null) {
                val anchorIndex = items.indexOfFirst { it.id == positioning.workId }
                if (anchorIndex < 0) return@LaunchedEffect
                grid.scrollToItem(anchorIndex, (-positioning.relativeOffset * viewportHeight).toInt().coerceAtLeast(0))
            }
            if (runtime.restoreToken != token || runtime.layoutToken != pass) return@LaunchedEffect
            anchorPositioned = true
            requestRestore()
        }
    }
    LaunchedEffect(runtime.userTarget, epoch) {
        val target = runtime.userTarget ?: return@LaunchedEffect
        val index = items.indexOfFirst { it.id == target }
        if (index >= 0) {
            grid.scrollToItem(index)
            // Existing placed node can focus immediately; newly created nodes use their placement callback.
            if (runtime.inputEpoch == epoch && runtime.userTarget == target && target in ready && grid.layoutInfo.visibleItemsInfo.any { it.key == target.value })
                requesters[target]?.requestFocus()
        }
    }
    LaunchedEffect(runtime.controlTarget, windowFocused) {
        if (windowFocused) runtime.controlTarget?.let { controls[it]?.requestFocus() }
    }
    val currentItems by rememberUpdatedState(items)
    LaunchedEffect(grid) {
        snapshotFlow { grid.firstVisibleItemIndex to grid.firstVisibleItemScrollOffset }.collect { (index, offset) ->
            val height = grid.layoutInfo.viewportSize.height
            if (height > 0) runtime.positioned(currentItems.getOrNull(index)?.id, -offset.toFloat() / height)
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .semantics { testTagsAsResourceId = true }.testTag("候选浏览")) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                (listOf("片库", "收藏", "历史") + (if (runtime.filterVisible) listOf("筛选") else emptyList()) + listOf("列数", "刷新")).forEach { label ->
                    val requester = remember(label) { controls.getOrPut(label) { FocusRequester() } }
                    var focus by remember(label) { mutableStateOf(false) }
                    Text(label, color = if (focus) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.background(if (focus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                            .padding(12.dp, 8.dp).testTag(label).focusRequester(requester)
                            .focusProperties { canFocus = !runtime.modal && !runtime.isDetail }
                            .onFocusChanged { focus = it.isFocused; if (it.isFocused) runtime.controlFocused(label) }
                            .semantics { selected = runtime.top.route == when (label) { "片库" -> "library"; "收藏" -> "favorites"; "历史" -> "history"; else -> "" } }
                            .focusable().onGloballyPositioned {
                                pageReady = true
                                if (windowFocused && runtime.controlTarget == label) requester.requestFocus()
                                requestRestore()
                            })
                }
            }
            Text(runtime.status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("覆盖状态"))
            if (runtime.navigation != null) {
                Text("${when(runtime.top) { BrowseTopLevel.LIBRARY -> "片库"; BrowseTopLevel.FAVORITES -> "收藏"; BrowseTopLevel.HISTORY -> "历史" }} · ${result?.items?.size ?: 0} 项已核对", color = MaterialTheme.colorScheme.onSurface)
            }
            key(pass, BrowseProbeTestHooks.blockPlacement) {
                LazyVerticalGrid(GridCells.Fixed(columns), Modifier.weight(1f).testTag("作品列表").onSizeChanged { if (runtime.layoutToken == pass) runtime.viewportChanged(it.width, it.height) }.onGloballyPositioned { runtime.placed(columns) }, state = grid,
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(items, key = { it.id.value }) { work ->
                        val requester = remember(work.id) { requesters.getOrPut(work.id) { FocusRequester() } }
                        var focus by remember { mutableStateOf(false) }
                        DisposableEffect(work.id, pass) { onDispose { ready.remove(work.id) } }
                        Column(Modifier.height(112.dp).fillMaxWidth()
                            .background(if (focus) MaterialTheme.colorScheme.primary.copy(alpha = .22f) else MaterialTheme.colorScheme.surface)
                            .border(if (focus) 3.dp else 1.dp, if (focus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                            .testTag(work.id.value).focusRequester(requester)
                            .focusProperties { canFocus = !runtime.modal && !runtime.isDetail && !BrowseProbeTestHooks.disableWorkFocus }
                            .onFocusChanged { focus = it.isFocused; if (it.isFocused) runtime.focused(work.id) }
                            .semantics {
                                selected = runtime.selected == work.id
                                contentDescription = "${work.title}，${if (runtime.selected == work.id) "已选择" else "未选择"}"
                            }.focusable().onGloballyPositioned {
                                ready.add(work.id)
                                if (runtime.userTarget == work.id && runtime.inputEpoch == epoch) requester.requestFocus()
                                requestRestore()
                            }.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Text(work.title, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
                            Text(if (runtime.selected == work.id) "已选择" else "按确认查看", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        if (runtime.modal) {
            val close = remember { FocusRequester() }
            var placed by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .72f)), contentAlignment = Alignment.Center) {
                Column(Modifier.width(340.dp).background(MaterialTheme.colorScheme.surface).padding(24.dp)) {
                    Text("筛选条件", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
                    Text("保留当前精确查询；按确认或返回关闭", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("关闭", color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 20.dp).testTag("关闭筛选").focusRequester(close).focusable()
                            .onGloballyPositioned { if (!placed) { placed = true; close.requestFocus() } })
                }
            }
        }
    }
}

@Composable
internal fun BrowseProbeDetail(runtime: BrowseProbeRuntime, onExit: () -> Unit) {
    val requester = remember { FocusRequester() }
    val windowFocused = LocalWindowInfo.current.isWindowFocused && runtime.foreground && !BrowseProbeTestHooks.holdWindowReady
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(placed, windowFocused) { if (placed && windowFocused) requester.requestFocus() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(36.dp)
        .testTag("候选详情")) {
        Text(runtime.items.firstOrNull { it.id == runtime.navigation?.snapshot?.focusedWorkId }?.title ?: "作品资料",
            style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Text("按返回回到原浏览位置", color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp).testTag("详情返回").focusRequester(requester).focusable()
                .onGloballyPositioned { placed = true })
    }
}
