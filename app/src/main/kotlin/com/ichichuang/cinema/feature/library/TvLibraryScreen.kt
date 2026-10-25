package com.ichichuang.cinema.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.ichichuang.cinema.core.design.Ink
import com.ichichuang.cinema.core.design.Panel
import com.ichichuang.cinema.core.design.TvOriginalIcons
import com.ichichuang.cinema.core.design.TextSub
import com.ichichuang.cinema.core.ui.TvLoadingTile
import com.ichichuang.cinema.core.ui.TvPosterTile
import com.ichichuang.cinema.core.ui.TvPrimaryButton
import com.ichichuang.cinema.core.ui.TvStatusMessage
import com.ichichuang.cinema.core.navigation.RestoreOutcome

/**
 * 原生片库屏：只接收不可变展示状态与回调，不直接访问查询管线或存储。
 * 焦点/恢复沿用 R11 令牌管线：运行时状态驱动唯一焦点，图片装载与迟到投递不抢焦点；
 * 面板打开时背景一律不可聚焦（焦点捕获在面板内），关闭恢复打开者。
 */
@Composable
internal fun TvLibraryScreen(runtime: LibraryRuntime, onExit: () -> Unit) {
    val grid = rememberLazyGridState()
    val windowFocused = LocalWindowInfo.current.isWindowFocused && runtime.foreground &&
        !LibraryTestHooks.blockPlacement
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val ready = remember { mutableSetOf<String>() }
    val token = runtime.restoreToken
    val pass = runtime.layoutToken
    val display = runtime.display
    val items = display.items
    val columns = runtime.columns
    val viewportWidth = runtime.viewportWidth
    val viewportHeight = runtime.viewportHeight
    var anchorPositioned by remember(pass, viewportWidth, viewportHeight) { mutableStateOf(false) }
    var pageReady by remember(pass) { mutableStateOf(false) }
    val epoch = runtime.inputEpoch
    var pendingAction by remember(pass) { mutableStateOf<RestoreOutcome?>(null) }

    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    fun requestRestore() {
        if (!anchorPositioned || !windowFocused || runtime.panelOpen || token == null || pass == null ||
            LibraryTestHooks.blockPlacement || runtime.restoreToken != token || runtime.layoutToken != pass) return
        val outcome = pendingAction ?: runtime.restore.observe(token, runtime.observation(ready.toSet(), pass, pageReady))
        when (outcome) {
            is RestoreOutcome.ReadyToFocus -> if (outcome.workId.value !in ready ||
                (outcome.anchorWorkId != null && outcome.anchorWorkId.value !in ready)) return
            is RestoreOutcome.Fallback -> if (outcome.workId?.let { it.value !in ready } ?: !pageReady) return
            else -> return
        }
        val target = when (outcome) {
            is RestoreOutcome.ReadyToFocus -> outcome.workId.value
            is RestoreOutcome.Fallback -> outcome.workId?.value ?: LibraryRuntime.FILTER_ENTRY
            else -> return
        }
        if (outcome is RestoreOutcome.Fallback && outcome.workId == null) {
            runtime.delivered(outcome)
            pendingAction = null
            return
        }
        pendingAction = outcome
        val accepted = requester(target).requestFocus()
        LibraryTestHooks.traceFocus("片库焦点确认=$target 接受=$accepted")
        if (accepted == true) {
            pendingAction = null
            runtime.delivered(outcome)
            val height = grid.layoutInfo.viewportSize.height
            if (height > 0) runtime.positioned(items.getOrNull(grid.firstVisibleItemIndex)?.workId,
                -grid.firstVisibleItemScrollOffset.toFloat() / height)
        } else LibraryTestHooks.failedFocusRequests.incrementAndGet()
    }

    LaunchedEffect(windowFocused, pass, LibraryTestHooks.disableWorkFocus) { if (windowFocused) requestRestore() }
    LaunchedEffect(pass, token, viewportWidth, viewportHeight) {
        if (pass != null && token != null && viewportWidth > 0 && viewportHeight > 0 &&
            runtime.restoreToken == token && runtime.layoutToken == pass) {
            val positioning = runtime.restore.positioning(token, runtime.observation(emptySet(), pass, false))
                ?: return@LaunchedEffect
            if (positioning.workId != null) {
                val anchorIndex = items.indexOfFirst { it.workId == positioning.workId.value }
                // R15：锚点作品已从结果消失时不再卡死定位——跳过滚动但放行恢复判定，
                // 由协调器按全量已知序回退到原行最近存活邻居（无邻居才回控制层）。
                if (anchorIndex >= 0)
                    grid.scrollToItem(anchorIndex, (-positioning.relativeOffset * viewportHeight).toInt().coerceAtLeast(0))
            }
            if (runtime.restoreToken != token || runtime.layoutToken != pass) return@LaunchedEffect
            anchorPositioned = true
            requestRestore()
        }
    }
    LaunchedEffect(runtime.userTarget, epoch) {
        val target = runtime.userTarget ?: return@LaunchedEffect
        val index = items.indexOfFirst { it.workId == target }
        if (index >= 0) {
            grid.scrollToItem(index)
            if (runtime.inputEpoch == epoch && runtime.userTarget == target && target in ready &&
                grid.layoutInfo.visibleItemsInfo.any { it.key == target })
                requester(target).requestFocus()
        }
    }
    LaunchedEffect(runtime.controlTarget, windowFocused) {
        if (windowFocused && !runtime.panelOpen) runtime.controlTarget?.let { requester(it).requestFocus() }
    }
    val currentItems by rememberUpdatedState(items)
    LaunchedEffect(grid) {
        snapshotFlow { grid.firstVisibleItemIndex to grid.firstVisibleItemScrollOffset }.collect { (index, offset) ->
            val height = grid.layoutInfo.viewportSize.height
            if (height > 0) runtime.positioned(currentItems.getOrNull(index)?.workId, -offset.toFloat() / height)
        }
    }

    Column(Modifier.fillMaxSize()
        .background(Brush.verticalGradient(listOf(Panel.copy(alpha = 0.4f), Ink)))
        .semantics { testTagsAsResourceId = true }.testTag("候选片库")
        .padding(start = 32.dp, top = 20.dp, end = 32.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("片库", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            TvPrimaryButton(label = LibraryRuntime.FILTER_ENTRY, icon = TvOriginalIcons.Filter,
                tag = LibraryRuntime.FILTER_ENTRY,
                enabled = !runtime.panelOpen,
                focusRequester = requester(LibraryRuntime.FILTER_ENTRY),
                onPlaced = {
                    ready.add(LibraryRuntime.FILTER_ENTRY); pageReady = true
                    if (windowFocused && !runtime.panelOpen && runtime.controlTarget == LibraryRuntime.FILTER_ENTRY)
                        requester(LibraryRuntime.FILTER_ENTRY).requestFocus()
                    requestRestore()
                },
                onFocusChanged = { if (it) runtime.controlFocused(LibraryRuntime.FILTER_ENTRY) }) {
                runtime.openPanel()
            }
        }
        // 已应用条件摘要：删除片是显式用户动作，其他字段不受影响。
        // FlowRow 换行保证所有条件片始终合成且可见（长标签不把后面的片挤出屏幕）。
        if (display.filterSummary.isNotEmpty()) {
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                display.filterSummary.forEach { chip ->
                    key(chip.key) {
                        TvFilterChip(
                            label = chip.label,
                            tag = "chip:${chip.key}",
                            focusRequester = requester("chip:${chip.key}"),
                            enabled = !runtime.panelOpen,
                            onPlaced = {
                                ready.add("chip:${chip.key}")
                                if (windowFocused && !runtime.panelOpen && runtime.controlTarget == "chip:${chip.key}")
                                    requester("chip:${chip.key}").requestFocus()
                            },
                            onFocusChanged = { if (it) runtime.controlFocused("chip:${chip.key}") },
                            onClick = { runtime.activateControl("chip:${chip.key}") })
                    }
                }
            }
        }
        TvStatusMessage(
            text = runtime.status,
            tag = "片库状态",
            icon = when (display.status) {
                LibraryContentStatus.OFFLINE -> TvOriginalIcons.History
                LibraryContentStatus.FAILED -> TvOriginalIcons.Retry
                LibraryContentStatus.UNCONFIGURED -> TvOriginalIcons.Settings
                else -> TvOriginalIcons.Details
            },
            actionLabel = runtime.statusActions().firstOrNull(),
            actionIcon = when (display.status) { LibraryContentStatus.FAILED -> TvOriginalIcons.Retry; else -> TvOriginalIcons.Details },
            actionTag = runtime.statusActions().firstOrNull(),
            actionEnabled = !runtime.panelOpen,
            focusRequester = runtime.statusActions().firstOrNull()?.let { requester(it) },
            onPlaced = { runtime.statusActions().firstOrNull()?.let { action -> ready.add(action) } },
            onFocusChanged = { action -> if (action) runtime.statusActions().firstOrNull()?.let { runtime.controlFocused(it) } },
            onAction = when (runtime.statusActions().firstOrNull()) {
                LibraryRuntime.CONTINUE -> { { runtime.continueLoading() } }
                LibraryRuntime.RETRY -> { { runtime.retry() } }
                else -> null
            })
        runtime.auditText?.let { audit ->
            Text(audit, color = TextSub, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("筛选变更说明"))
        }
        key(pass, LibraryTestHooks.blockPlacement) {
            LazyVerticalGrid(GridCells.Fixed(columns), Modifier.weight(1f).testTag("作品列表")
                .onSizeChanged { if (runtime.layoutToken == pass) runtime.viewportChanged(it.width, it.height) }
                .onGloballyPositioned { runtime.placed(columns) }, state = grid,
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items, key = { it.workId }) { card ->
                    val cardRequester = remember(card.workId) { requesters.getOrPut(card.workId) { FocusRequester() } }
                    DisposableEffect(card.workId, pass) { onDispose { ready.remove(card.workId) } }
                    TvPosterTile(
                        title = card.title,
                        artworkUri = card.artworkUri,
                        imageShape = card.imageShape,
                        tag = card.workId,
                        secondaryText = card.yearText ?: "年份未知",
                        fillWidth = true,
                        landscape = true,
                        focusable = !runtime.panelOpen && !LibraryTestHooks.disableWorkFocus,
                        focusRequester = cardRequester,
                        onPlaced = {
                            ready.add(card.workId)
                            if (windowFocused && !runtime.panelOpen && runtime.userTarget == card.workId &&
                                runtime.inputEpoch == epoch) cardRequester.requestFocus()
                            requestRestore()
                        },
                        onFocusChanged = { if (it) runtime.focused(card.workId) },
                        onClick = { runtime.openWork(card.workId) })
                }
            }
        }
        if (display.status == LibraryContentStatus.LOADING && items.isEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(5) { TvLoadingTile(width = 208.dp, height = 117.dp) }
            }
        }
        if (display.status == LibraryContentStatus.COMPLETE_EMPTY) {
            Text("当前筛选与来源范围已完整核对，暂无匹配影片；可调整筛选条件或来源",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("片库完整空"))
        }
    }

    if (runtime.panelOpen) TvLibraryFilterPanel(runtime)
}
