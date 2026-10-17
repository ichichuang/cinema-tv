package com.ichichuang.cinema.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import com.ichichuang.cinema.core.design.Gold
import com.ichichuang.cinema.core.design.Ink
import com.ichichuang.cinema.core.design.Motion
import com.ichichuang.cinema.core.design.rememberTvMetrics

/**
 * 播放按钮 —— 详情面板里的"主角按钮"。
 *
 * 金色胶囊 + 焦点放大 + 白描边。
 * 目标体验：焦点落在电影上 → 按 OK 就能看，中间不需要任何多余步骤。
 */
@Composable
fun PlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "播放",
) {
    val m = rememberTvMetrics()
    var focused by remember { mutableStateOf(false) }
    val p by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = Motion.quick(),
        label = "playButtonFocus",
    )
    val shape = RoundedCornerShape(50)

    Row(
        modifier = modifier
            .graphicsLayer {
                val s = 1f + 0.05f * p
                scaleX = s
                scaleY = s
            }
            .clip(shape)
            .background(if (focused) Gold else Gold.copy(alpha = 0.88f))
            .border(m.dp(2f), Color.White.copy(alpha = 0.75f * p), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .onFocusChanged { focused = it.isFocused }
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
        )
    }
}
