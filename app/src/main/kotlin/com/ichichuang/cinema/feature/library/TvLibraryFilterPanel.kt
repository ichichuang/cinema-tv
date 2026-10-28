package com.ichichuang.cinema.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ichichuang.cinema.core.design.Danger
import com.ichichuang.cinema.core.design.Gold
import com.ichichuang.cinema.core.design.Panel
import com.ichichuang.cinema.core.design.Radii
import com.ichichuang.cinema.core.design.Sizes
import com.ichichuang.cinema.core.design.Spacing
import com.ichichuang.cinema.core.design.TextSub
import com.ichichuang.cinema.core.focus.tvFocus

/**
 * 条件筛选片：选中（金色下划线）与焦点（tvFocus 描边）是两种独立视觉；
 * 键是规范 字段:取值，中文标签只做显示。点击语义保留，D-pad 激活由运行时驱动。
 */
@Composable
internal fun TvFilterChip(
    label: String,
    tag: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onPlaced: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier = modifier
        .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
        .tvFocus(cornerRadius = Radii.sm)
        .onFocusChanged { focused = it.isFocused; onFocusChanged?.invoke(it.isFocused) }
        .background(if (focused) Gold.copy(alpha = 0.14f) else if (selected) Panel.copy(alpha = 0.9f) else Panel,
            RoundedCornerShape(Radii.sm))
        .semantics {
            role = Role.Checkbox; this.selected = selected
            contentDescription = if (selected) "$label，已选择" else label
            onClick(label = label) { if (enabled) { onClick(); true } else false }
        }
        .then(if (enabled) Modifier.focusable() else Modifier)
        .testTag(tag)
        .onGloballyPositioned { onPlaced?.invoke() }) {
        Text(label, color = if (selected || focused) MaterialTheme.colorScheme.onSurface else TextSub,
            style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm))
        Box(Modifier.padding(start = Spacing.md).size(width = if (selected) Spacing.n20 else 0.dp, height = Spacing.n2)
            .background(if (selected) Gold else Color.Transparent))
    }
}

/**
 * 严格筛选面板：焦点捕获在面板分区内（背景控件 enabled=false），BACK 只关面板并
 * 恢复打开者；全部选择可由 D-pad 完成，年份用步进器可达 2000 年之前，无需键盘。
 */
@Composable
internal fun TvLibraryFilterPanel(runtime: LibraryRuntime) {
    val draft = runtime.draft ?: return
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val windowFocused = LocalWindowInfo.current.isWindowFocused && runtime.foreground
    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    fun selected(key: String): Boolean = when {
        key.startsWith("year-mode-") -> draft.yearMode.name == key.removePrefix("year-mode-")
        key.startsWith("lang-") -> draft.languageScope.name == key.removePrefix("lang-")
        key == "kind-null" -> draft.contentKind == null
        key.startsWith("kind-") -> draft.contentKind?.name == key.removePrefix("kind-")
        key.startsWith("country-") -> key.removePrefix("country-") in draft.countryIds
        key.startsWith("genre-") -> key.removePrefix("genre-") in draft.genreIds
        key.startsWith("sort-") -> draft.sort.name == key.removePrefix("sort-")
        key.startsWith("source-") -> key.removePrefix("source-") in draft.enabledSourceIds
        else -> false
    }

    LaunchedEffect(runtime.panelTarget, windowFocused, draft) {
        if (windowFocused) runtime.panelTarget?.let { requester(it).requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.9f).background(Panel, RoundedCornerShape(Radii.xl))
            .verticalScroll(rememberScrollState()).padding(Spacing.n28)
            .testTag("筛选面板"), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text("筛选条件", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            val yearSummary = when (draft.yearMode) {
                LibraryYearMode.NONE -> "年份：不限"
                LibraryYearMode.EXACT -> "具体年份：${draft.exactYear} 年"
                LibraryYearMode.DECADE -> "年代：${draft.decadeStart}–${draft.decadeStart + 9} 年"
                LibraryYearMode.RANGE -> "起止范围：${draft.rangeStart}–${draft.rangeEnd} 年"
            }
            Text(yearSummary, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("年份摘要"))
            draft.yearError()?.let { error ->
                Text(error, color = Danger,
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("年份错误"))
            }
            runtime.panelZones().forEachIndexed { sectionIndex, (section, keys) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.n10)) {
                    Text(section, color = TextSub, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(Sizes.filterSectionLabelWidth))
                    keys.forEach { panelKey ->
                        key(panelKey) {
                            TvFilterChip(
                                label = runtime.panelLabels(panelKey),
                                tag = panelKey,
                                selected = selected(panelKey),
                                focusRequester = requester(panelKey),
                                onFocusChanged = { focused -> if (focused && runtime.panelTarget != panelKey) runtime.panelFocusChanged(panelKey) },
                                onClick = { runtime.activatePanelKey(panelKey) })
                        }
                    }
                }
                // 步进器行附带当前值，帮助不靠记忆操作。
                if (section == "具体年份") Text("当前 ${draft.exactYear} 年", color = TextSub,
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = Sizes.filterOptionIndent))
                if (section == "年代") Text("当前 ${draft.decadeStart}–${draft.decadeStart + 9} 年", color = TextSub,
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = Sizes.filterOptionIndent))
                if (section == "起止年份") Text("当前 ${draft.rangeStart}–${draft.rangeEnd} 年", color = TextSub,
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = Sizes.filterOptionIndent))
                if (sectionIndex == runtime.panelZones().lastIndex - 1) Spacer(Modifier.height(Spacing.n6))
            }
        }
    }
}
