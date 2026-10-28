package com.ichichuang.cinema.feature.home.visual

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.ichichuang.cinema.core.design.Ink
import com.ichichuang.cinema.core.design.LocalReducedMotion
import com.ichichuang.cinema.core.design.Motion
import com.ichichuang.cinema.core.design.Spacing
import com.ichichuang.cinema.core.design.TvOriginalIcons
import com.ichichuang.cinema.core.design.rememberMotionEnter
import com.ichichuang.cinema.core.design.rememberMotionExit
import com.ichichuang.cinema.core.design.rememberTvMetrics
import com.ichichuang.cinema.core.ui.TvPrimaryButton
import com.ichichuang.cinema.core.ui.TvStatusMessage

/**
 * 候选设置切片：分组（显示 / 动效 / 来源 / 关于），每个条目都有真实效果，
 * 来源状态只陈述已核实事实。四个页面状态（加载 / 空 / 错误 / 内容）由
 * [SettingsProjection] 投影；入场用 ST1 的 [rememberMotionEnter]，列表逐项
 * stagger（[Motion.staggerDelayMs]），减弱动效下不位移不缩放只淡入淡出。
 */
@Composable
internal fun CandidateSettingsScreen(runtime: VisualHomeRuntime, onExit: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val windowFocused = LocalWindowInfo.current.isWindowFocused && runtime.foreground
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    // 页面入场必须真实触发：初始 false，进入组合后置 true，让 enter 过渡真正播放
    //（`visible = true` 从一开始就为真，AnimatedVisibility 不会跑入场动画）。
    val enterState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { if (shouldTriggerEntrance(firstCompositionDone = true)) enterState.targetState = true }
    LaunchedEffect(runtime.controlTarget, windowFocused) {
        if (windowFocused) runtime.controlTarget?.let { requesters[it]?.requestFocus() }
    }
    val display = runtime.settingsDisplay()

    MaterialTheme {
        Box(Modifier.fillMaxSize().background(Ink)) {
            AnimatedVisibility(
                visibleState = enterState,
                enter = rememberMotionEnter(reduced),
                exit = rememberMotionExit(reduced),
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .semantics { testTagsAsResourceId = true }.testTag("候选设置")
                    .padding(start = Spacing.page, top = Spacing.xxl, end = Spacing.page, bottom = Spacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.n14)) {
                    Text("设置", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)

                    // ── 显示 ─────────────────────────────────────
                    SectionLabel("显示")
                    SettingsItem(runtime, requesters, "大字模式", TvOriginalIcons.Text)
                    SettingsItem(runtime, requesters, "筛选栏", TvOriginalIcons.Filter)
                    SettingsItem(runtime, requesters, "列数", TvOriginalIcons.Columns)

                    // ── 动效 ─────────────────────────────────────
                    SectionLabel("动效")
                    SettingsItem(runtime, requesters, "减弱动效", TvOriginalIcons.Settings)
                    runtime.reduceMotionError?.let { error ->
                        TvStatusMessage(
                            text = error,
                            tag = "减弱动效保存失败",
                            icon = TvOriginalIcons.Retry,
                        )
                    }

                    // ── 来源 ─────────────────────────────────────
                    SectionLabel("来源")
                    SourceSection(runtime, display, requesters)

                    VisualHomeRuntime.SETTINGS_ITEMS.filter { it in setOf("刷新目录", "重载首页") }
                        .forEach { label -> SettingsItem(runtime, requesters, label, TvOriginalIcons.Retry) }

                    // ── 关于 ─────────────────────────────────────
                    SectionLabel("关于")
                    Text("极简影院 · 设置页", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("来源由用户自行配置；应用本身不内置任何片源。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** 分组标题：只做视觉分区，不参与焦点序列。 */
@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.n10))
}

/**
 * 一条可聚焦的设置项。逐项 stagger 入场：延迟来自 [Motion.staggerDelayMs]，
 * 位移/缩放只走 graphicsLayer；减弱动效时延迟归零、无位移无缩放。
 */
@Composable
private fun SettingsItem(
    runtime: VisualHomeRuntime,
    requesters: MutableMap<String, FocusRequester>,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val reduced = LocalReducedMotion.current
    val index = VisualHomeRuntime.SETTINGS_ITEMS.indexOf(label).coerceAtLeast(0)
    val appear = remember(label) { Animatable(0f) }
    LaunchedEffect(label) {
        kotlinx.coroutines.delay(Motion.staggerDelayMs(index, reduced).toLong())
        appear.animateTo(1f, Motion.enter(reduced))
    }
    val m = rememberTvMetrics()
    val requester = remember(label) { requesters.getOrPut(label) { FocusRequester() } }
    Column(Modifier.graphicsLayer {
        alpha = appear.value
        if (!reduced) translationY = (1f - appear.value) * m.dp(Motion.ENTER_OFFSET_DP).toPx()
    }) {
        TvPrimaryButton(
            label = runtime.settingsItemText(label),
            icon = icon,
            focusRequester = requester,
            onFocusChanged = { if (it) runtime.controlFocused(label) }) {
            runtime.activateSettingsItem(label)
        }
    }
}

/** 来源管理：只陈述真实读取到的状态；读不到就显示未配置与引导，不造假、不放死胡同按钮。 */
@Composable
private fun SourceSection(
    runtime: VisualHomeRuntime,
    display: SettingsDisplayState,
    requesters: MutableMap<String, FocusRequester>,
) {
    val reduced = LocalReducedMotion.current
    val enter = rememberMotionEnter(reduced)
    val exit = rememberMotionExit(reduced)
    val retryKey = SettingsSourceSection.RETRY_KEY
    val retryRequester = remember(retryKey) { requesters.getOrPut(retryKey) { FocusRequester() } }
    val retryFocus = { focused: Boolean -> if (focused) runtime.controlFocused(retryKey) }
    val onRetry = { runtime.retry() }
    // 状态切换（加载 → 空/错误/内容）用 ST1 入场/出场过渡：旧状态淡出、新状态淡入，
    // 减弱动效下只淡入淡出不位移不缩放。
    androidx.compose.animation.AnimatedContent(
        targetState = display.status,
        transitionSpec = { enter togetherWith exit },
        label = "settingsSourceState",
    ) { status ->
        when (status) {
            SettingsPageStatus.LOADING -> TvStatusMessage(
                text = "正在读取来源状态",
                tag = "设置来源状态",
                icon = TvOriginalIcons.Library,
            )
            SettingsPageStatus.EMPTY -> TvStatusMessage(
                text = display.source?.let { SettingsProjection.sourceLine(it) } ?: "尚未配置来源",
                tag = "设置来源状态",
                icon = TvOriginalIcons.Settings,
            )
            SettingsPageStatus.ERROR -> TvStatusMessage(
                text = display.source?.let { SettingsProjection.sourceLine(it) } ?: "来源状态读取失败",
                tag = "设置来源状态",
                icon = TvOriginalIcons.Retry,
                actionLabel = "重试",
                actionIcon = TvOriginalIcons.Retry,
                actionTag = retryKey,
                focusRequester = retryRequester,
                onFocusChanged = retryFocus,
                onAction = onRetry,
            )
            SettingsPageStatus.CONTENT -> {
                val state = display.source
                val retryable = state != null && SettingsProjection.retryable(state)
                TvStatusMessage(
                    text = state?.let { SettingsProjection.sourceLine(it) } ?: "尚未配置来源",
                    tag = "设置来源状态",
                    icon = if (retryable) TvOriginalIcons.Retry else TvOriginalIcons.Library,
                    actionLabel = if (retryable) "重试" else null,
                    actionIcon = if (retryable) TvOriginalIcons.Retry else null,
                    actionTag = retryKey,
                    focusRequester = if (retryable) retryRequester else null,
                    onFocusChanged = if (retryable) retryFocus else null,
                    onAction = if (retryable) onRetry else null,
                )
            }
        }
    }
}
