package com.ichichuang.cinema.feature.personal

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import com.ichichuang.cinema.core.design.Spacing
import com.ichichuang.cinema.feature.browse.BrowseProbeRuntime

@Composable
private fun PersonalAction(runtime: BrowseProbeRuntime, label: String, text: String = label, onExit: () -> Unit) {
    val owner = requireNotNull(runtime.personal)
    val requester = remember(label) { FocusRequester() }
    val visibility = remember(label) { BringIntoViewRequester() }
    var measuredSize by remember(label) { mutableStateOf(IntSize.Zero) }
    var focused by remember(label) { mutableStateOf(false) }
    val windowReady = LocalWindowInfo.current.isWindowFocused && runtime.foreground
    var placed by remember(label) { mutableStateOf(false) }
    LaunchedEffect(owner.detailFocus, windowReady, placed) {
        if (placed && windowReady && owner.detailFocus == label) requester.requestFocus()
    }
    LaunchedEffect(focused, windowReady, placed, measuredSize, owner.message, text) {
        if (focused && placed && windowReady) {
            // Include the full action's padding, after the error/confirmation text has laid out.
            withFrameNanos { }
            visibility.bringIntoView()
        }
    }
    Text(text, color = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().bringIntoViewRequester(visibility).background(if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.n14, vertical = Spacing.sm).testTag(label).focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .semantics { role = Role.Button; if (owner.actionBusy) disabled(); onClick { runtime.activateDetail(label, onExit); true } }
            .focusable().onGloballyPositioned { placed = true; measuredSize = it.size })
}

@Composable
internal fun PersonalLibraryDetail(runtime: BrowseProbeRuntime, onExit: () -> Unit) {
    val owner = requireNotNull(runtime.personal)
    val id = runtime.selected ?: runtime.navigation?.snapshot?.focusedWorkId
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(Spacing.n28)
        .testTag("候选详情"), verticalArrangement = Arrangement.spacedBy(Spacing.n6)) {
        Text(id?.let { owner.card(it.value)?.title ?: runtime.items.firstOrNull { work -> work.id == it }?.title } ?: "本地作品记录",
            style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(id?.let { owner.availability(it.value) } ?: "作品资料尚未核实", color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("个人来源状态"))
        Text(owner.message, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("个人操作状态"))
        if (owner.confirmation) {
            Text("删除该作品的观看记录、续播与逐集进度？收藏和其他作品会保留。", color = MaterialTheme.colorScheme.onSurface)
            runtime.detailActions().forEach { PersonalAction(runtime, it, onExit = onExit) }
        } else runtime.detailActions().forEach { label ->
            PersonalAction(runtime, label, if (label == "收藏操作") {
                if (id?.let { owner.isFavorite(it.value) } == true) "取消收藏" else "收藏"
            } else if (label == "从头准备当前版本") "$label · ${owner.versionChoiceDescription}" else label, onExit)
        }
    }
}

@Composable
internal fun PersonalLibraryPlayer(runtime: BrowseProbeRuntime, onExit: () -> Unit) {
    val owner = requireNotNull(runtime.personal)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(Spacing.xxl)
        .testTag("候选播放器"), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(owner.selectedMedia?.detail?.movie?.title ?: "本地合成媒体", style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface)
        Text("${owner.selectedMedia?.target?.episodeId?.value} · ${owner.selectedMedia?.target?.variantId?.value} · ${owner.playerPosition / 1000} 秒",
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag("实际播放目标"))
        Text(if (owner.playerPlaying) "正在播放独立本地合成媒体" else "已暂停；按播放后才开始",
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("实际播放状态"))
        Text(owner.message, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("个人操作状态"))
        runtime.detailActions().forEach { label -> PersonalAction(runtime, label,
            if (label == "播放暂停") { if (owner.playerPlaying) "暂停" else "播放" } else label, onExit) }
    }
}
