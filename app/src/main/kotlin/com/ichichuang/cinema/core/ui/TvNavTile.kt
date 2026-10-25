package com.ichichuang.cinema.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ichichuang.cinema.core.design.Gold
import com.ichichuang.cinema.core.focus.tvFocus

/**
 * 候选根级导航条目：原创图标 + 中文标签。
 * 选中（当前根目的地）与键盘焦点是两种独立视觉：选中用金色下划线与亮色文字，
 * 焦点用既有 tvFocus 金色描边与缩放；两者可同时存在也可分离（N10/V12）。
 * 条目就绪回调供运行时登记 ready 集，焦点请求由运行时状态驱动。
 */
@Composable
internal fun TvNavTile(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    tag: String = label,
    focusRequester: FocusRequester? = null,
    onPlaced: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier = modifier
        .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
        .tvFocus(cornerRadius = 10.dp)
        .onFocusChanged { focused = it.isFocused; onFocusChanged?.invoke(it.isFocused) }
        .semantics { role = Role.Tab; this.selected = selected; onClick(label = label) { onClick(); true } }
        .focusable()
        .testTag(tag)
        .onGloballyPositioned { onPlaced?.invoke() }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Icon(icon, contentDescription = null,
                tint = if (selected || focused) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
            Text(label, color = if (selected || focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge)
        }
        // 选中态下划线独立绘制，不与焦点描边合并为同一种视觉。
        Box(Modifier.padding(top = 4.dp).size(width = if (selected) 24.dp else 0.dp, height = 3.dp)
            .background(if (selected) Gold else Color.Transparent))
    }
}
