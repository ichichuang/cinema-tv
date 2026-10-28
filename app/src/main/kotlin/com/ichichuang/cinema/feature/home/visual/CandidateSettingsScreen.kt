package com.ichichuang.cinema.feature.home.visual

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.ichichuang.cinema.core.design.Ink
import com.ichichuang.cinema.core.design.Spacing
import com.ichichuang.cinema.core.design.TvOriginalIcons
import com.ichichuang.cinema.core.ui.TvPrimaryButton
import com.ichichuang.cinema.core.ui.TvStatusMessage

/**
 * 候选设置切片：只含真实可用的设置动作与如实的来源状态。
 * 每个条目都有实际效果（大字、筛选栏、列数、刷新、重载）；
 * 来源状态只陈述已核实事实，不提供死胡同按钮。
 */
@Composable
internal fun CandidateSettingsScreen(runtime: VisualHomeRuntime, onExit: () -> Unit) {
    val windowFocused = LocalWindowInfo.current.isWindowFocused && runtime.foreground
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    LaunchedEffect(runtime.controlTarget, windowFocused) {
        if (windowFocused) runtime.controlTarget?.let { requesters[it]?.requestFocus() }
    }
    val display = runtime.display
    MaterialTheme {
        Column(Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState())
            .semantics { testTagsAsResourceId = true }.testTag("候选设置")
            .padding(start = Spacing.page, top = Spacing.xxl, end = Spacing.page, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.n14)) {
            Text("设置", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            VisualHomeRuntime.SETTINGS_ITEMS.forEach { item ->
                val requester = remember(item) { requesters.getOrPut(item) { FocusRequester() } }
                TvPrimaryButton(
                    label = runtime.settingsItemText(item),
                    icon = when (item) {
                        "大字模式" -> TvOriginalIcons.Details
                        "筛选栏" -> TvOriginalIcons.Filter
                        "列数" -> TvOriginalIcons.Library
                        "刷新目录" -> TvOriginalIcons.Retry
                        else -> TvOriginalIcons.Home
                    },
                    focusRequester = requester,
                    onFocusChanged = { if (it) runtime.controlFocused(item) }) {
                    runtime.activateSettingsItem(item)
                }
            }
            Text("来源状态", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val configured = runtime.display.contentStatus != HomeContentStatus.UNCONFIGURED
            TvStatusMessage(
                text = if (configured) "测试来源已配置；首页目录行显示当前已索引并经严格筛选的作品，未核实总数不虚构"
                else "未配置任何测试来源；片库目录为空属正常状态，个人记录不受影响",
                tag = "设置来源状态",
                icon = if (configured) TvOriginalIcons.Library else TvOriginalIcons.Settings)
            if (display.failedSourceCount > 0) {
                TvStatusMessage(text = "当前查询有 ${display.failedSourceCount} 个来源失败；失败来源的结果不计入完整核对",
                    tag = "设置失败状态", icon = TvOriginalIcons.Retry)
            }
        }
    }
}
