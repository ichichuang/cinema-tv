package com.ichichuang.cinema.feature.home.visual

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextStyle
import com.ichichuang.cinema.core.design.Ink
import com.ichichuang.cinema.core.design.Panel
import com.ichichuang.cinema.core.design.Spacing
import com.ichichuang.cinema.core.design.TileSize
import com.ichichuang.cinema.core.design.TvOriginalIcons
import com.ichichuang.cinema.core.ui.TvLoadingTile
import com.ichichuang.cinema.core.ui.TvNavTile
import com.ichichuang.cinema.core.ui.TvPosterTile
import com.ichichuang.cinema.core.ui.TvPrimaryButton
import com.ichichuang.cinema.core.ui.TvStatusMessage
import com.ichichuang.cinema.core.navigation.RestoreOutcome
import com.ichichuang.cinema.core.navigation.RestoreToken
import com.ichichuang.cinema.core.navigation.RestoreLayoutToken

/**
 * 首页显示边界：只接收不可变展示状态与回调，不直接访问网络或存储。
 * 焦点/恢复沿用 R11 令牌管线：运行时状态驱动唯一焦点，图片装载与迟到投递不抢焦点。
 */
@Composable
internal fun TvHomeScreen(runtime: VisualHomeRuntime, onExit: () -> Unit) {
    val windowFocused = LocalWindowInfo.current.isWindowFocused && runtime.foreground && !VisualHomeTestHooks.holdWindowReady
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val ready = remember { mutableSetOf<String>() }
    val token = runtime.restoreToken
    val pass = runtime.layoutToken
    var pendingAction by remember(pass) { mutableStateOf<RestoreOutcome?>(null) }
    val display = runtime.display
    val hero = runtime.heroCard
    val zones = runtime.focusZones()

    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    fun requestRestore() {
        if (!windowFocused || token == null || pass == null || VisualHomeTestHooks.blockPlacement ||
            runtime.restoreToken != token || runtime.layoutToken != pass) return
        val fresh = pendingAction == null
        val outcome = pendingAction ?: runtime.restore.observe(token, runtime.observation(ready.toSet(), pass))
        if (fresh) VisualHomeTestHooks.traceFocus("首页结果=$outcome")
        when (outcome) {
            is RestoreOutcome.ReadyToFocus -> if (outcome.workId.value !in ready) return
            is RestoreOutcome.Fallback -> if (outcome.workId?.let { it.value !in ready } ?: false) return
            else -> return
        }
        val key = when (outcome) {
            is RestoreOutcome.ReadyToFocus -> outcome.workId.value
            is RestoreOutcome.Fallback -> outcome.workId?.value
            else -> null
        } ?: return
        pendingAction = outcome
        val accepted = requesters[key]?.requestFocus()
        VisualHomeTestHooks.traceFocus("首页焦点确认=$accepted")
        if (accepted == true) pendingAction = null else VisualHomeTestHooks.failedFocusRequests.incrementAndGet()
    }
    LaunchedEffect(windowFocused, pass, VisualHomeTestHooks.disableCardFocus) { if (windowFocused) requestRestore() }

    LaunchedEffect(runtime.userTarget, runtime.inputEpoch) {
        val target = runtime.userTarget ?: return@LaunchedEffect
        val card = zones.flatMap { it.second }.firstOrNull { it == target }
        if (card != null && runtime.userTarget == target) {
            val accepted = requester(card).requestFocus()
            VisualHomeTestHooks.traceFocus("卡片焦点请求=$card 接受=$accepted")
        }
    }
    LaunchedEffect(runtime.controlTarget, windowFocused) {
        if (windowFocused) runtime.controlTarget?.let {
            val accepted = requester(it).requestFocus()
            VisualHomeTestHooks.traceFocus("控件焦点请求=$it 接受=$accepted")
        }
    }

    fun focusCallback(key: String): ((Boolean) -> Unit) = { focused -> if (focused) runtime.controlFocused(key) }
    val statusAction = when (display.contentStatus) {
        HomeContentStatus.UNCONFIGURED -> "action:${VisualHomeRuntime.GOTO_SETTINGS}"
        HomeContentStatus.FAILED -> "action:${VisualHomeRuntime.RETRY}"
        else -> null
    }
    // 输入代次：放置回调以其核对 userTarget/controlTarget，新合成或刚放置的节点可重试焦点请求（镜像浏览页模式）。
    val epoch = runtime.inputEpoch
    fun placed(key: String) {
        ready.add(key)
        if (!VisualHomeTestHooks.disableCardFocus && windowFocused) {
            if (runtime.userTarget == key && runtime.inputEpoch == epoch) requester(key).requestFocus()
            if (runtime.controlTarget == key) requester(key).requestFocus()
        }
        requestRestore()
    }

    // 大字模式：同一套排版按比例放大，不引入第二字体体系（token 权威唯一）。
    val typography = MaterialTheme.typography.scaled(runtime.largeText)
    MaterialTheme(typography = typography) {
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Panel.copy(alpha = 0.4f), Ink)))
            .semantics { testTagsAsResourceId = true }.testTag("候选首页")) {
            Row(Modifier.padding(start = Spacing.xxl, top = Spacing.n20, end = Spacing.xxl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.n20),
                verticalAlignment = Alignment.CenterVertically) {
                VisualHomeRuntime.NAV_ITEMS.forEachIndexed { index, label ->
                    key("nav$index") {
                        TvNavTile(label = label,
                            icon = when (label) {
                                "首页" -> TvOriginalIcons.Home; "片库" -> TvOriginalIcons.Library
                                "我的收藏" -> TvOriginalIcons.Favorite; "最近观看" -> TvOriginalIcons.History
                                else -> TvOriginalIcons.Settings
                            },
                            selected = label == "首页",
                            tag = "nav:$label",
                            focusRequester = requester("nav:$label"),
                            onPlaced = { placed("nav:$label") },
                            onFocusChanged = focusCallback("nav:$label"),
                            onClick = { runtime.openTopDestination(label) })
                    }
                }
            }
            TvStatusMessage(text = runtime.statusText(),
                icon = when (display.contentStatus) {
                    HomeContentStatus.OFFLINE -> TvOriginalIcons.History
                    HomeContentStatus.FAILED -> TvOriginalIcons.Retry
                    HomeContentStatus.UNCONFIGURED -> TvOriginalIcons.Settings
                    else -> TvOriginalIcons.Details
                },
                actionLabel = when (display.contentStatus) {
                    HomeContentStatus.UNCONFIGURED -> VisualHomeRuntime.GOTO_SETTINGS
                    HomeContentStatus.FAILED -> VisualHomeRuntime.RETRY
                    else -> null
                },
                actionIcon = when (display.contentStatus) {
                    HomeContentStatus.UNCONFIGURED -> TvOriginalIcons.Settings
                    HomeContentStatus.FAILED -> TvOriginalIcons.Retry
                    else -> null
                },
                actionTag = statusAction,
                focusRequester = statusAction?.let { requester(it) },
                onPlaced = { statusAction?.let { placed(it) } },
                onFocusChanged = statusAction?.let { focusCallback(it) },
                onAction = when (display.contentStatus) {
                    HomeContentStatus.UNCONFIGURED -> { { runtime.openTopDestination("设置") } }
                    HomeContentStatus.FAILED -> { { runtime.retry() } }
                    else -> null
                },
                modifier = Modifier.padding(start = Spacing.xxl, top = Spacing.n10))

            LazyColumn(Modifier.weight(1f).padding(start = Spacing.xxl, top = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.n18)) {
                if (hero != null) {
                    item(key = "hero") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.n20), verticalAlignment = Alignment.CenterVertically) {
                            TvPosterTile(title = hero.title, artworkUri = hero.artworkUri, imageShape = hero.imageShape,
                                tag = "hero/${hero.workId}", secondaryText = hero.secondaryText,
                                progressText = hero.progressText, wide = true, landscape = true,
                                focusable = false,
                                progressFraction = hero.progressFraction())
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.n10)) {
                                Text("继续观看", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(hero.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                TvPrimaryButton(VisualHomeRuntime.HERO_RESUME, TvOriginalIcons.Play,
                                    focusRequester = requester(VisualHomeRuntime.HERO_RESUME),
                                    onPlaced = { placed(VisualHomeRuntime.HERO_RESUME) },
                                    onFocusChanged = focusCallback(VisualHomeRuntime.HERO_RESUME)) {
                                    runtime.openWork(hero.workId, resume = true)
                                }
                                TvPrimaryButton(VisualHomeRuntime.HERO_DETAIL, TvOriginalIcons.Details,
                                    focusRequester = requester(VisualHomeRuntime.HERO_DETAIL),
                                    onPlaced = { placed(VisualHomeRuntime.HERO_DETAIL) },
                                    onFocusChanged = focusCallback(VisualHomeRuntime.HERO_DETAIL)) {
                                    runtime.openWork(hero.workId, resume = false)
                                }
                            }
                        }
                    }
                }
                runtime.contentRows().forEach { (row, cards) ->
                    item(key = row) {
                        val listState = rememberLazyListState()
                        LaunchedEffect(runtime.userTarget) {
                            val index = cards.indexOfFirst { VisualHomeRuntime.cardKey(row, it.workId) == runtime.userTarget }
                            if (index >= 0) listState.animateScrollToItem(index)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text(row, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(Spacing.n14)) {
                                items(cards, key = { it.workId }) { card ->
                                    val key = VisualHomeRuntime.cardKey(row, card.workId)
                                    TvPosterTile(title = card.title, artworkUri = card.artworkUri, imageShape = card.imageShape,
                                        tag = key, secondaryText = card.secondaryText, progressText = card.progressText,
                                        progressFraction = card.progressFraction(),
                                        landscape = row != VisualHomeRuntime.CONTINUE_ROW,
                                        focusRequester = requester(key),
                                        onPlaced = { placed(key) },
                                        onFocusChanged = focusCallback(key))
                                }
                            }
                        }
                    }
                }
                if (display.contentStatus == HomeContentStatus.LOADING && display.catalogCards.isEmpty() && display.continueCards.isEmpty()) {
                    item(key = "loading") {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.n14)) {
                            repeat(4) {
                                TvLoadingTile(width = TileSize.landscapeWidth, height = TileSize.landscapeHeight)
                            }
                        }
                    }
                }
                if (display.contentStatus == HomeContentStatus.READY && display.catalogCards.isEmpty() && display.continueCards.isEmpty() && display.recentCards.isEmpty()) {
                    item(key = "empty") {
                        Text("当前查询范围已完整核对，暂无匹配影片；可调整来源或筛选条件",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.testTag("首页完整空"))
                    }
                }
            }
        }
    }
}

internal fun HomeDisplayCard.progressFraction(): Float? {
    val progress = progressText ?: return null
    if (progress == "本集已看完") return 1f
    val position = progress.substringAfter("· ", progress).substringBefore(" /").trim()
    val total = progress.substringAfter(" /", "").trim()
    val p = position.split(":").mapNotNull { it.toLongOrNull() }
    val t = total.split(":").mapNotNull { it.toLongOrNull() }
    if (p.isEmpty() || t.isEmpty()) return null
    fun seconds(parts: List<Long>): Long? = when (parts.size) {
        3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]).takeIf { _ -> true }
        2 -> parts[0] * 60 + parts[1]
        else -> null
    }
    val ps = seconds(p) ?: return null
    val ts = seconds(t) ?: return null
    if (ts <= 0) return null
    return (ps.toDouble() / ts).toFloat()
}

/** 大字模式按同一比例缩放整套排版；不引入第二字体或绝对字号。 */
private fun androidx.compose.material3.Typography.scaled(large: Boolean): androidx.compose.material3.Typography {
    if (!large) return this
    fun TextStyle.scaled(): TextStyle = copy(fontSize = fontSize * 1.3f, lineHeight = lineHeight * 1.3f)
    return androidx.compose.material3.Typography(
        displayLarge = displayLarge.scaled(), displayMedium = displayMedium.scaled(), displaySmall = displaySmall.scaled(),
        headlineLarge = headlineLarge.scaled(), headlineMedium = headlineMedium.scaled(), headlineSmall = headlineSmall.scaled(),
        titleLarge = titleLarge.scaled(), titleMedium = titleMedium.scaled(), titleSmall = titleSmall.scaled(),
        bodyLarge = bodyLarge.scaled(), bodyMedium = bodyMedium.scaled(), bodySmall = bodySmall.scaled(),
        labelLarge = labelLarge.scaled(), labelMedium = labelMedium.scaled(), labelSmall = labelSmall.scaled())
}
