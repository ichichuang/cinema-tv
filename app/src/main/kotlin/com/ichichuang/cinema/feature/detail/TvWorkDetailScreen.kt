package com.ichichuang.cinema.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.ichichuang.cinema.core.design.Gold
import com.ichichuang.cinema.core.design.Panel
import com.ichichuang.cinema.core.design.PanelHi
import com.ichichuang.cinema.core.design.Radii
import com.ichichuang.cinema.core.design.Scrim
import com.ichichuang.cinema.core.design.Sizes
import com.ichichuang.cinema.core.design.Spacing
import com.ichichuang.cinema.core.design.Scrim
import com.ichichuang.cinema.core.design.TextSub
import com.ichichuang.cinema.core.design.TvOriginalIcons
import com.ichichuang.cinema.core.design.rememberTvMetrics
import com.ichichuang.cinema.core.focus.tvFocus
import com.ichichuang.cinema.core.ui.PosterImage
import com.ichichuang.cinema.core.ui.TvPrimaryButton

/**
 * 原生作品详情屏：只消费运行时投影与焦点状态；全部按键经
 * WorkDetailRuntime（窗口回调层）处理，画面内不拦截方向键。
 * 焦点≠选中双视觉；弹层打开时背景一律不可聚焦（陷阱），关闭精确恢复打开者。
 */
@Composable
internal fun TvWorkDetailScreen(runtime: WorkDetailRuntime, onExit: () -> Unit) {
    val m = rememberTvMetrics()
    val display = runtime.display
    LaunchedEffect(Unit) { runtime.open() }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val placed = remember { mutableMapOf<String, Boolean>() }
    val sheetOpen = runtime.sheet

    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    // 弹层打开：焦点只进弹层项；弹层关闭：回到当前焦点键；重组漂移由 focusCorrection 拉回。
    LaunchedEffect(runtime.focusedKey, runtime.sheetFocusedKey, sheetOpen, windowFocused, display, runtime.focusCorrection) {
        if (!windowFocused || WorkDetailTestHooks.disableActionFocus) return@LaunchedEffect
        val target = if (sheetOpen != null) runtime.sheetFocusedKey else runtime.focusedKey
        if (target != null && placed[target] == true) requester(target).requestFocus()
    }
    LaunchedEffect(runtime.restoreRequest, windowFocused, sheetOpen) {
        val key = runtime.restoreRequest ?: return@LaunchedEffect
        if (!windowFocused || WorkDetailTestHooks.disableActionFocus || sheetOpen != null) return@LaunchedEffect
        val consumed = runtime.consumeRestore()
        if (consumed != null && placed[consumed] == true) {
            val accepted = requester(consumed).requestFocus()
            WorkDetailTestHooks.traceFocus("详情焦点恢复=$consumed 接受=$accepted")
        } else WorkDetailTestHooks.failedFocusRequests.incrementAndGet()
    }

    if (display == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("候选详情"))
        return
    }
    val locked = sheetOpen != null
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("候选详情")) {
        Row(Modifier.fillMaxSize().padding(m.dp(40f)), horizontalArrangement = Arrangement.spacedBy(m.dp(36f))) {
            // 左列：竖幅海报；缺图时安静深色块 + 详情图标（叠在占位背景之上才可见），标题始终在页面真实呈现（L04/L07）。
            Box(Modifier.width(m.dp(240f)).fillMaxHeight().background(Panel, RoundedCornerShape(m.dp(8f)))) {
                PosterImage(display.artworkUri, display.title,
                    Modifier.fillMaxSize().padding(m.dp(2f)))
                if (display.artworkUri == null)
                    androidx.compose.material3.Icon(TvOriginalIcons.Details, contentDescription = null,
                        tint = TextSub.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.Center).size(m.dp(64f)))
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(m.dp(10f))) {
                Text(display.title, style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag("作品标题"))
                Text(factLine(display), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("作品事实"))
                Text(display.availabilityText, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("线路总状态"))
                val status = runtime.currentStatus()
                if (status.isNotBlank())
                    Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("详情操作状态"))
                // 窄面板/大字号下动作行换行（与线路 chips 同语言），保证每个操作真实可见而非被推出屏外。
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.n14), modifier = Modifier.testTag("详情动作")) {
                    DetailAction(runtime, "播放操作", display.primaryActionText, TvOriginalIcons.Play,
                        display.primaryActionEnabled && !locked, ::requester, placed)
                    DetailAction(runtime, "选择线路", "选择线路", TvOriginalIcons.Filter, !locked, ::requester, placed)
                    DetailAction(runtime, "选择集数", "选择集数", TvOriginalIcons.Details, !locked, ::requester, placed)
                    DetailAction(runtime, "收藏操作", display.favoriteText, TvOriginalIcons.Favorite, !locked, ::requester, placed)
                    DetailAction(runtime, "详情返回", "返回", TvOriginalIcons.Back, !locked, ::requester, placed)
                }
                if (display.primaryActionExplanation != null)
                    Text(display.primaryActionExplanation, color = TextSub, modifier = Modifier.testTag("播放说明"))
                if (display.routes.isNotEmpty()) {
                    Text("播放线路", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.n10), modifier = Modifier.testTag("线路列表")) {
                        display.routes.forEach { route ->
                            RouteChip(route, route.routeId == display.selectedRouteId, locked, ::requester, placed, runtime)
                        }
                    }
                }
                if (display.episodes.isNotEmpty()) {
                    Text("剧集", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.testTag("集数列表"), verticalArrangement = Arrangement.spacedBy(Spacing.n6)) {
                        display.episodes.forEach { episode ->
                            EpisodeRow(episode, episode.episodeId == display.selectedEpisodeId, locked, ::requester, placed, runtime)
                        }
                    }
                }
            }
        }
        if (sheetOpen != null) DetailSheet(runtime, sheetOpen, ::requester, placed)
    }
}

/** 语义/指针入口与按键走同一确认管线；弹层锁定时背景动作不可用。 */
@Composable
private fun DetailAction(
    runtime: WorkDetailRuntime, key: String, label: String, icon: ImageVector,
    enabled: Boolean, requester: (String) -> FocusRequester, placed: MutableMap<String, Boolean>,
) {
    TvPrimaryButton(label = label, icon = icon, tag = key, enabled = enabled,
        focusRequester = requester(key),
        onPlaced = { placed[key] = true },
        onFocusChanged = { if (it) runtime.focusArrived(key) },
        onClick = { runtime.activate(key) })
}

@Composable
private fun RouteChip(route: DetailRouteUi, selected: Boolean, locked: Boolean,
    requester: (String) -> FocusRequester, placed: MutableMap<String, Boolean>, runtime: WorkDetailRuntime) {
    val key = "线路:${route.routeId}"
    var focused by remember(key) { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.n6),
        modifier = Modifier
            .focusRequester(requester(key))
            .tvFocus(cornerRadius = Radii.full)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused && !locked) runtime.focusArrived(key) }
            .background(if (selected) PanelHi else Panel, RoundedCornerShape(Radii.full))
            .semantics {
                role = Role.Button
                if (!route.available || locked) disabled()
                onClick(label = route.label) { if (!locked) { runtime.activate(key); true } else false }
            }
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .testTag(key)
            .onGloballyPositioned { placed[key] = true }
            .then(if (locked) Modifier else Modifier.focusable())) {
        if (selected) Box(Modifier.size(Sizes.dot).background(Gold, RoundedCornerShape(Radii.xxs)))
        Text(route.label, color = MaterialTheme.colorScheme.onSurface)
        Text(route.stateText, color = TextSub, style = MaterialTheme.typography.labelSmall)
        route.episodeCountText?.let { Text(it, color = TextSub, style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun EpisodeRow(episode: DetailEpisodeUi, selected: Boolean, locked: Boolean,
    requester: (String) -> FocusRequester, placed: MutableMap<String, Boolean>, runtime: WorkDetailRuntime) {
    val key = "集数:${episode.episodeId}"
    var focused by remember(key) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth()
        .focusRequester(requester(key))
        .tvFocus(cornerRadius = Radii.xs)
        .onFocusChanged { focused = it.isFocused; if (it.isFocused && !locked) runtime.focusArrived(key) }
        .background(if (selected) PanelHi else Panel, RoundedCornerShape(Radii.xs))
        .semantics {
            role = Role.Button
            if (!episode.playable || locked) disabled()
            onClick(label = episode.label) { if (!locked) { runtime.activate(key); true } else false }
        }
        .padding(horizontal = Spacing.n14, vertical = Spacing.sm)
        .testTag(key)
        .onGloballyPositioned { placed[key] = true }
        .then(if (locked) Modifier else Modifier.focusable()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.n10)) {
        if (selected) Box(Modifier.size(Sizes.dot).background(Gold, RoundedCornerShape(Radii.xxs)))
        Text(episode.label, color = MaterialTheme.colorScheme.onSurface)
        episode.orderText?.let { Text(it, color = TextSub) }
        episode.resumeText?.let { Text(it, color = Gold) }
    }
}

/** 顶层线路/集数弹层：焦点捕获在列表内，Back 关闭并恢复打开者。 */
@Composable
private fun DetailSheet(
    runtime: WorkDetailRuntime, kind: DetailSheetKind,
    requester: (String) -> FocusRequester, placed: MutableMap<String, Boolean>,
) {
    val m = rememberTvMetrics()
    val display = requireNotNull(runtime.display)
    val items = when (kind) {
        DetailSheetKind.LINES -> display.routes.map { "线路单:${it.routeId}" to listOf(it.label, it.stateText) }
        DetailSheetKind.EPISODES -> display.episodes.map {
            "集数单:${it.episodeId}" to listOf(it.label) + listOfNotNull(it.orderText, it.resumeText)
        }
    }
    Box(Modifier.fillMaxSize().background(Scrim(0.6f)).testTag(if (kind == DetailSheetKind.LINES) "线路单" else "集数单")) {
        Column(Modifier.align(Alignment.Center).width(m.dp(640f))
            .background(PanelHi, RoundedCornerShape(m.dp(10f))).padding(m.dp(24f)),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(if (kind == DetailSheetKind.LINES) "选择线路" else "选择集数",
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            items.forEach { (key, texts) ->
                var focused by remember(key) { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth()
                    .focusRequester(requester(key))
                    .tvFocus(cornerRadius = Radii.xs)
                    .onFocusChanged { focused = it.isFocused; if (it.isFocused) runtime.focusArrived(key) }
                    .background(Panel, RoundedCornerShape(Radii.xs))
                    .semantics { role = Role.Button; onClick(label = texts.first()) { runtime.activate(key); true } }
                    .padding(horizontal = Spacing.n14, vertical = Spacing.n10)
                    .testTag(key)
                    .onGloballyPositioned { placed[key] = true }
                    .focusable(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    texts.forEach { text -> Text(text, color = if (focused) Gold else MaterialTheme.colorScheme.onSurface) }
                }
            }
        }
    }
}

private fun factLine(display: DetailUiState): String = listOf(
    display.year.text, display.originalLanguages.text, display.countries.text,
    display.genres.text, display.contentKind.text).joinToString(" · ")
