package com.ichichuang.cinema.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.media3.ui.compose.PlayerSurface
import com.ichichuang.cinema.core.design.Gold
import com.ichichuang.cinema.core.design.Ink
import com.ichichuang.cinema.core.design.Motion
import com.ichichuang.cinema.core.design.Panel
import com.ichichuang.cinema.core.design.PanelHi
import com.ichichuang.cinema.core.design.Radii
import com.ichichuang.cinema.core.design.Scrim
import com.ichichuang.cinema.core.design.Sizes
import com.ichichuang.cinema.core.design.Spacing
import com.ichichuang.cinema.core.design.TextSub
import com.ichichuang.cinema.core.design.TvOriginalIcons
import com.ichichuang.cinema.core.design.formatTime
import com.ichichuang.cinema.core.design.rememberTvMetrics
import com.ichichuang.cinema.core.focus.tvFocus
import com.ichichuang.cinema.core.ui.TvPrimaryButton

/**
 * R16 原生播放器控制屏：只消费 PlayerControlsRuntime 的投影与焦点状态；
 * 全部物理按键经运行时（窗口回调层）唯一解释，画面内不拦截方向键。
 * 真实视频面由 PlayerSurface 渲染；未知时长如实呈现，绝不用假进度冒充。
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun TvPlayerControlsScreen(runtime: PlayerControlsRuntime, onExit: () -> Unit) {
    val m = rememberTvMetrics()
    val display = runtime.display
    val controller = runtime.controller
    LaunchedEffect(Unit) { runtime.start() }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val requesters = remember { mutableStateMapOf<String, FocusRequester>() }
    val placed = remember { mutableStateMapOf<String, Boolean>() }
    // Compose 镜像（runtime 同步）：picker/焦点/预览/浮层变化真实触发重组。
    val pickerOpen = runtime.uiPicker
    val uiOverlay = runtime.uiOverlay
    val uiFocus = runtime.uiFocus
    val uiPickerFocus = runtime.uiPickerFocus
    val uiPreview = runtime.uiPreview

    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    // 窗口失焦：作废进行中的物理按键周期（评审项）。
    LaunchedEffect(windowFocused) { runtime.windowFocusGained(windowFocused) }

    // 焦点落点：选择器打开只进选择器；浮层可见落在当前控件；隐藏层不占焦点。
    LaunchedEffect(uiFocus, uiOverlay, pickerOpen, uiPickerFocus, windowFocused, display) {
        if (!windowFocused || PlayerControlsTestHooks.disableControlFocus) return@LaunchedEffect
        val target = if (pickerOpen != null) uiPickerFocus
        else if (uiOverlay) uiFocus else null
        if (target != null && placed[target] == true) requester(target).requestFocus()
    }

    Box(Modifier.fillMaxSize().background(Color.Black).testTag("候选播放器")) {
        // ① 真实视频面（无视频流时安静黑底；绝不用假画面冒充）。
        // TextureView：避开部分电视/模拟器 SurfaceView 的 native-window 协商缺陷。
        PlayerSurface(player = runtime.currentPlayer(), modifier = Modifier.fillMaxSize(),
            surfaceType = androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW)

        // ② 缓冲指示（小而暗，不抢画面）
        AnimatedVisibility(visible = display != null && display.status.buffering && display.error == null,
            enter = fadeIn(Motion.base()), exit = fadeOut(Motion.quick()), modifier = Modifier.align(Alignment.Center)) {
            CircularProgressIndicator(modifier = Modifier.size(m.dp(46f)), color = Gold, strokeWidth = m.dp(3f))
        }

        val overlayShown = uiOverlay || pickerOpen != null
        // ③ 顶部信息
        AnimatedVisibility(visible = overlayShown, enter = fadeIn(Motion.base()), exit = fadeOut(Motion.quick()),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Ink.copy(alpha = 0.92f), Color.Transparent)))
                .padding(horizontal = m.dp(48f), vertical = m.dp(26f))) {
            Column {
                Text(display?.title ?: "本地合成媒体", color = Color.White, fontSize = m.sp(28f),
                    modifier = Modifier.testTag("播放标题"))
                Text(listOfNotNull(display?.lineLabel, display?.episodeLabel).joinToString(" · ")
                    .ifBlank { "未提供线路或剧集名" }, color = TextSub, fontSize = m.sp(16f),
                    modifier = Modifier.testTag("播放身份"))
            }
        }

        // ④ 底部控制浮层
        AnimatedVisibility(visible = uiOverlay && pickerOpen == null,
            enter = fadeIn(Motion.base()), exit = fadeOut(Motion.quick()),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = 0.94f))))
                .padding(horizontal = m.dp(48f), vertical = m.dp(24f))) {
            PlayerControlsPanel(runtime, display, controller, ::requester, placed)
        }

        // ⑤ 错误面板（如实中文 + 可达的重试）
        val errorText = display?.error
        AnimatedVisibility(visible = errorText != null, enter = fadeIn(Motion.base()), exit = fadeOut(Motion.quick()),
            modifier = Modifier.align(Alignment.Center)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(m.dp(12f)),
                modifier = Modifier.background(Scrim(0.6f)).padding(m.dp(28f)).testTag("播放错误")) {
                Text(errorText ?: "", color = Color.White, fontSize = m.sp(20f))
            }
        }

        // ⑥ 瞬时提示（快进/换集/未保存等如实反馈）
        val hint = display?.hint
        AnimatedVisibility(visible = hint != null, enter = fadeIn(Motion.base()), exit = fadeOut(Motion.quick()),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = m.dp(120f))) {
            Text(hint ?: "", color = Gold, fontSize = m.sp(18f),
                modifier = Modifier.background(Scrim(0.55f)).padding(horizontal = m.dp(20f), vertical = m.dp(8f)).testTag("播放提示"))
        }

        // ⑦ 选择器弹层（焦点陷阱；Back 精确恢复打开者）
        if (pickerOpen != null) PlayerPickerSheet(runtime, pickerOpen, ::requester, placed)
    }
}

@Composable
private fun PlayerControlsPanel(
    runtime: PlayerControlsRuntime,
    display: PlayerDisplay?,
    controller: PlayerControlsController,
    requester: (String) -> FocusRequester,
    placed: MutableMap<String, Boolean>,
) {
    val m = rememberTvMetrics()
    Column(verticalArrangement = Arrangement.spacedBy(m.dp(10f)), modifier = Modifier.testTag("控制浮层")) {
        // 状态行：正在播放/已暂停/缓冲中/已播完（U02 投影的如实文案）
        Row(horizontalArrangement = Arrangement.spacedBy(m.dp(14f)), verticalAlignment = Alignment.CenterVertically) {
            Text(display?.status?.statusText ?: "准备中", color = TextSub, fontSize = m.sp(16f),
                modifier = Modifier.testTag("播放状态"))
            display?.pendingExitFailure?.let {
                Text(it, color = Gold, fontSize = m.sp(16f), modifier = Modifier.testTag("退出未保存提示"))
            }
        }
        // 时间轴行：位置 / 进度条 / 时长；未知时长如实标注且禁用（U05/S06）
        TimelineRow(runtime, display, controller, requester, placed)
        // 控件行（全部短 OK 可达；禁用项不可聚焦且有可读禁用语义）
        Row(horizontalArrangement = Arrangement.spacedBy(m.dp(12f)), modifier = Modifier.testTag("控件行")) {
            // 时间轴的焦点承载者是上方进度条本体（唯一可聚焦节点，评审项）；
            // 行内不再渲染第二个“时间轴”按钮。播放/暂停/重试以文字为准（R13 无对应图标，
            // 不为它们新增资产）。播放控制随真实 PLAY_PAUSE 命令可用性启用。
            ControlButton(runtime, "播放控制",
                if (display?.facts?.isPlaying == true) "暂停" else "播放", null,
                display?.playPauseAvailable ?: true, requester, placed)
            ControlButton(runtime, "选集", "选集", TvOriginalIcons.Filter,
                controller.controlEnabled("选集"), requester, placed)
            ControlButton(runtime, "线路", "线路", TvOriginalIcons.Filter,
                controller.controlEnabled("线路"), requester, placed)
            if (display?.error != null)
                ControlButton(runtime, "重试", "重试", null, true, requester, placed)
        }
    }
}

@Composable
private fun TimelineRow(
    runtime: PlayerControlsRuntime,
    display: PlayerDisplay?,
    controller: PlayerControlsController,
    requester: (String) -> FocusRequester,
    placed: MutableMap<String, Boolean>,
) {
    val m = rememberTvMetrics()
    val duration = display?.durationMs
    val position = runtime.uiPreview ?: display?.positionMs ?: 0L
    val seekable = duration != null && display?.seekAvailable == true
    val key = "时间轴"
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.dp(14f)),
        modifier = Modifier.fillMaxWidth()) {
        Text(formatTime(position), color = Color.White, fontSize = m.sp(17f), modifier = Modifier.testTag("当前时间"))
        Box(Modifier.weight(1f).height(m.dp(10f))
            .background(Panel, RoundedCornerShape(m.dp(5f)))
            .focusRequester(requester(key))
            .tvFocus(cornerRadius = m.dp(6f))
            .onFocusChanged { if (it.isFocused) runtime.focusArrived(key) }
            .semantics {
                role = Role.Button
                if (!seekable) disabled()
                // 禁用原因如实说明：时长未知 vs 真实命令缺失（不可拖动）。
                val disabledLabel = if (duration == null) "时长未知，无法拖动" else "当前内容不支持拖动"
                onClick(label = if (seekable) "时间轴" else disabledLabel) {
                    if (seekable) { runtime.activateControl(key); true } else false
                }
            }
            .testTag(key)
            .onGloballyPositioned { placed[key] = true }
            .then(if (seekable) Modifier.focusable() else Modifier)) {
            if (duration != null && duration > 0) {
                val fraction = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction)
                    .let { if (runtime.uiPreview != null) it.background(Gold) else it.background(PanelHi) })
            }
        }
        Text(duration?.let { formatTime(it) } ?: "时长未知", color = if (duration == null) TextSub else Color.White,
            fontSize = m.sp(17f), modifier = Modifier.testTag("总时长"))
        if (controller.previewMs != null)
            Text("预览中，OK 确认，返回取消", color = Gold, fontSize = m.sp(15f), modifier = Modifier.testTag("预览提示"))
    }
}

@Composable
private fun ControlButton(
    runtime: PlayerControlsRuntime,
    key: String, label: String, icon: ImageVector?,
    enabled: Boolean, requester: (String) -> FocusRequester, placed: MutableMap<String, Boolean>,
) {
    TvPrimaryButton(label, icon, tag = key, enabled = enabled,
        focusRequester = requester(key), onPlaced = { placed[key] = true },
        onFocusChanged = { if (it) runtime.focusArrived(key) },
        onClick = { runtime.activateControl(key) })
}

/** 线路/集数选择器：真实证据身份；禁用项如实标注且不可激活。 */
@Composable
private fun PlayerPickerSheet(
    runtime: PlayerControlsRuntime,
    kind: PlayerPickerKind,
    requester: (String) -> FocusRequester,
    placed: MutableMap<String, Boolean>,
) {
    val m = rememberTvMetrics()
    val controller = runtime.controller
    Box(Modifier.fillMaxSize().background(Scrim(0.6f)).testTag(if (kind == PlayerPickerKind.LINES) "线路单" else "集数单")) {
        Column(Modifier.align(Alignment.Center).width(m.dp(640f)).height(m.dp(480f))
            .background(PanelHi, RoundedCornerShape(m.dp(10f))).padding(m.dp(24f)),
            verticalArrangement = Arrangement.spacedBy(m.dp(8f))) {
            Text(if (kind == PlayerPickerKind.LINES) "选择线路" else "选择集数",
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(m.dp(6f))) {
                if (kind == PlayerPickerKind.LINES) {
                    controller.pickerLines.forEach { line ->
                        PickerRow(runtime, "picker:${line.variantId}", line.label,
                            if (line.available) "" else "当前不可播放", line.available,
                            line.variantId == controller.pickerCurrentVariantId, requester, placed)
                    }
                } else {
                    controller.pickerEpisodes.forEach { episode ->
                        PickerRow(runtime, "picker:${episode.episodeId}", episode.label,
                            if (episode.playable) "" else "当前不可播放", episode.playable,
                            episode.episodeId == controller.pickerCurrentEpisodeId, requester, placed)
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    runtime: PlayerControlsRuntime, key: String, label: String, note: String,
    available: Boolean, selected: Boolean,
    requester: (String) -> FocusRequester, placed: MutableMap<String, Boolean>,
) {
    var focused by remember(key) { androidx.compose.runtime.mutableStateOf(false) }
    Row(Modifier.fillMaxWidth()
        .focusRequester(requester(key))
        .tvFocus(cornerRadius = Radii.xs)
        .onFocusChanged { focused = it.isFocused; if (it.isFocused) runtime.focusArrived(key) }
        .background(if (selected && !focused) PanelHi else Panel, RoundedCornerShape(Radii.xs))
        .semantics {
            role = Role.Button
            if (!available) disabled()
            onClick(label = label) { if (available) { runtime.activatePicker(key); true } else false }
        }
        .padding(horizontal = Spacing.n14, vertical = Spacing.n10)
        .testTag(key)
        .onGloballyPositioned { placed[key] = true }
        .then(if (available) Modifier.focusable() else Modifier),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (selected) Box(Modifier.size(Sizes.dot).background(Gold, RoundedCornerShape(Radii.xxs)))
        Text(label, color = if (focused) Gold else MaterialTheme.colorScheme.onSurface)
        if (note.isNotBlank()) Text(note, color = TextSub, style = MaterialTheme.typography.labelSmall)
    }
}
