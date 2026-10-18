package com.ichichuang.cinema.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.ichichuang.cinema.core.design.Gold
import com.ichichuang.cinema.core.design.Ink
import com.ichichuang.cinema.core.design.Motion
import com.ichichuang.cinema.core.design.Panel
import com.ichichuang.cinema.core.design.PanelHi
import com.ichichuang.cinema.core.design.TextMain
import com.ichichuang.cinema.core.design.TextSub
import com.ichichuang.cinema.core.design.rememberTvMetrics
import com.ichichuang.cinema.core.focus.tvFocus

/**
 * 筛选药丸（分类 / 语言 / 地区 / 年份 通用，筛选浮层里的标准零件）。
 *
 * 三种状态各有各的视觉，扫一眼就知道"现在筛的是什么、焦点在哪"：
 * - **选中**：金色实底 + Ink 字（一眼看出当前筛选）
 * - **未选 + 有焦点**：亮一档灰 + 描边 + 放大（遥控器当前位置）
 * - **未选**：深灰
 *
 * ⚠️ 历史教训（v0.7.0）：这里曾经还有一个 FilterGroup —— 把一排药丸塞进
 * `Row(horizontalScroll)` 并给每个药丸手动 `bringIntoViewRequester`。
 * 结果就是用户看到的"按钮左右抖动"：**手动 bringIntoView 和滚动容器自带的
 * 焦点滚动会互相打架**，两边各滚一点、来回抢。
 * 现在的做法：药丸只放在**换行网格（FlowRow）**里 —— 不横向滚动就没有这个冲突，
 * 排版也不会因为一行太长而失控。
 */
@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    val m = rememberTvMetrics()
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }

    val bg by animateColorAsState(
        targetValue = when {
            selected -> Gold
            focused -> PanelHi
            else -> Panel
        },
        animationSpec = Motion.focus(),
        label = "chipBg",
    )
    val fg by animateColorAsState(
        targetValue = when {
            selected -> Ink
            focused -> TextMain
            else -> TextSub
        },
        animationSpec = Motion.quick(),
        label = "chipFg",
    )

    Box(
        modifier = modifier
            // tvFocus 必须放在最前（它自己负责放大 + 光晕 + 按压回弹，且描边画在内容之上）
            .tvFocus(
                cornerRadius = m.dp(if (big) 24f else 18f),
                borderColor = if (selected) Color.White.copy(alpha = 0.85f) else Gold,
                scale = if (big) Motion.FOCUS_SCALE else 1.08f,
                interactionSource = interaction,
                onFocusChanged = { isFocused ->
                    focused = isFocused
                    onFocusChanged?.invoke(isFocused)
                },
            )
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) { onClick() }
            .padding(
                horizontal = m.dp(if (big) 28f else 20f),
                vertical = m.dp(if (big) 14f else 10f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = m.sp(if (big) 20f else 17f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}
