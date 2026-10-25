package com.ichichuang.cinema.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ichichuang.cinema.core.design.Gold
import com.ichichuang.cinema.core.focus.tvFocus

/**
 * 主操作按钮：原创图标 + 中文标签的胶囊造型。禁用时不再可聚焦（避免焦点陷阱），
 * 并保留可读的禁用语义；焦点态由 tvFocus 金色描边表达。
 */
@Composable
internal fun TvPrimaryButton(
    label: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    tag: String = label,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onPlaced: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .tvFocus(cornerRadius = 999.dp)
            .onFocusChanged { focused = it.isFocused; onFocusChanged?.invoke(it.isFocused) }
            .background(if (focused) Gold.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(999.dp))
            .semantics {
                role = Role.Button
                if (!enabled) disabled()
                onClick(label = label) { if (enabled) { onClick(); true } else false }
            }
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .testTag(tag)
            .onGloballyPositioned { onPlaced?.invoke() }
            .then(if (enabled) Modifier.focusable() else Modifier)) {
        if (icon != null) androidx.compose.material3.Icon(icon, contentDescription = null,
            tint = if (focused) Gold else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
        Text(label, color = if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * 真实状态条：原创图标 + 如实中文文案 + 可选操作按钮。
 * 仅呈现运行时给出的真实状态；加载/离线/失败/未配置互不冒充。
 */
@Composable
internal fun TvStatusMessage(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tag: String = "首页状态",
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    actionTag: String? = null,
    focusRequester: FocusRequester? = null,
    onPlaced: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier) {
        if (icon != null) androidx.compose.material3.Icon(icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f, fill = false).testTag(tag))
        if (actionLabel != null && onAction != null) {
            TvPrimaryButton(actionLabel, actionIcon, tag = actionTag ?: actionLabel, enabled = true,
                focusRequester = focusRequester, onPlaced = onPlaced, onFocusChanged = onFocusChanged, onClick = onAction)
        }
    }
}

/** 骨架装载块：仅表示“真实数据未到”，不含任何虚构影片内容，也不做无限循环动画。 */
@Composable
internal fun TvLoadingTile(modifier: Modifier = Modifier, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    Box(modifier.width(width).height(height)
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(5.dp)))
    }
}
