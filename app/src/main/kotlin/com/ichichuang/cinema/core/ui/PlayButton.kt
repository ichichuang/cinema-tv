package com.ichichuang.cinema.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.ichichuang.cinema.core.design.rememberTvMetrics
import com.ichichuang.cinema.core.focus.tvFocus

/**
 * 播放按钮 —— 详情面板里的"主角按钮"。
 *
 * 用统一焦点系统（[tvFocus]）：光晕 + 按压回弹与其他组件完全一致。
 * 唯一的区别是描边换成白色 —— 金色按钮上再画金边等于没画。
 *
 * 文案会在「播放 / 继续观看 12:34」之间变化，所以挂了 [animateContentSize]：
 * 宽度跟着平滑过渡，不是"啪"地抖一下。
 */
@Composable
fun PlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "播放",
) {
    val m = rememberTvMetrics()
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(50)
    val bg by animateColorAsState(
        targetValue = if (focused) Gold else Gold.copy(alpha = 0.9f),
        animationSpec = Motion.quick(),
        label = "playButtonBg",
    )

    Row(
        modifier = modifier
            .tvFocus(
                cornerRadius = m.dp(28f),
                borderColor = Color.White.copy(alpha = 0.9f),
                interactionSource = interaction,
                onFocusChanged = { focused = it },
            )
            .clip(shape)
            .background(bg)
            .animateContentSize(animationSpec = Motion.swap())
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) { onClick() }
            .padding(horizontal = m.dp(38f), vertical = m.dp(16f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "▶",
            color = Ink,
            fontSize = m.sp(22f),
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(m.dp(14f)))
        Text(
            text = text,
            color = Ink,
            fontSize = m.sp(23f),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
