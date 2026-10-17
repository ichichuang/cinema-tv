package com.ichichuang.cinema.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

/**
 * 筛选药丸（分类 / 语言 / 地区 / 年份 通用）。
 *
 * 三种状态各有各的视觉，扫一眼就知道"现在筛的是什么、焦点在哪"：
 * - **选中**：金色实底 + Ink 字（一眼看出当前筛选）
 * - **未选 + 有焦点**：亮一档灰 + 描边 + 放大（遥控器当前位置）
 * - **未选**：深灰
 *
 * 选中/颜色变化都走 [Motion] 的弹簧/快曲线，不是硬切 —— 这是"丝滑"的细节来源。
 * 描边颜色按状态区分：已选中的用白描边（金底上金边看不见），未选中的用暖金。
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
                cornerRadius = m.dp(if (big) 22f else 18f),
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
                horizontal = m.dp(if (big) 26f else 20f),
                vertical = m.dp(if (big) 13f else 10f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = m.sp(if (big) 21f else 17f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/**
 * 一组筛选（可选小标题 + 若干药丸）。
 *
 * ⚠️ 必须放在**横向可滚动**的 `Row(Modifier.horizontalScroll(...))` 里。
 * 它给每个药丸挂了"焦点进入时自动滚到可见"的能力 ——
 * 否则遥控器一直往右按，焦点会跑出屏幕外（电视上最容易犯的错，
 * 用户会以为"遥控器坏了"）。LazyRow 自带这个能力，普通 Row 必须手动做。
 */
@Composable
fun RowScope.FilterGroup(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    big: Boolean = false,
    /** 前面加一条竖直分隔线（用来区分"语言 / 地区 / 年份"三个组） */
    dividerBefore: Boolean = false,
) {
    val m = rememberTvMetrics()

    if (dividerBefore) {
        Box(
            Modifier
                .padding(horizontal = m.dp(20f))
                .width(m.dp(1f))
                .height(m.dp(20f))
                .background(Color.White.copy(alpha = 0.12f)),
        )
    }

    if (title.isNotBlank()) {
        Text(
            text = title,
            color = TextSub.copy(alpha = 0.7f),
            fontSize = m.sp(14f),
            modifier = Modifier.padding(end = m.dp(10f)),
        )
    }

    options.forEach { option ->
        val requester = remember { BringIntoViewRequester() }
        val scope = rememberCoroutineScope()
        FilterChip(
            label = option,
            selected = option == selected,
            big = big,
            onClick = { onSelect(option) },
            modifier = Modifier
                .padding(end = m.dp(10f))
                .bringIntoViewRequester(requester),
            onFocusChanged = { isFocused ->
                if (isFocused) scope.launch { runCatching { requester.bringIntoView() } }
            },
        )
    }
}
