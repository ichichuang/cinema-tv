package com.ichichuang.cinema.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import com.ichichuang.cinema.core.design.Gold
import com.ichichuang.cinema.core.design.Ink
import com.ichichuang.cinema.core.design.Motion
import com.ichichuang.cinema.core.design.TextMain
import com.ichichuang.cinema.core.design.TextSub
import com.ichichuang.cinema.core.design.rememberTvMetrics
import com.ichichuang.cinema.core.focus.tvFocus
import kotlinx.coroutines.delay

/**
 * 居中提示卡（未配置片源 / 接口出错 / 筛选或搜索无结果时用）。
 *
 * 它们都带一个**可聚焦**的主按钮，并且出现时自动拿焦点 ——
 * 遥控器用户不需要"摸索着找按钮在哪"，按 OK 就是最该做的那件事。
 */
@Composable
fun CenterHint(
    title: String,
    body: String,
    actionText: String,
    onAction: () -> Unit,
) {
    val m = rememberTvMetrics()
    val focus = remember { FocusRequester() }
    val radius = m.dp(14f)
    val interaction = remember { MutableInteractionSource() }
    // 空态/错误态往往是"突然发生"的：让它轻轻浮上来，而不是硬生生砸在屏幕上
    val appear = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        appear.animateTo(1f, Motion.enter())
        delay(120)
        runCatching { focus.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * m.dp(18f).toPx()
            }
            .padding(horizontal = m.dp(48f)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = title,
            color = TextMain,
            fontSize = m.sp(30f),
            fontWeight = FontWeight.Bold,
            letterSpacing = m.sp(0.5f),
        )
        Spacer(Modifier.height(m.dp(14f)))
        Text(
            text = body,
            color = TextSub,
            fontSize = m.sp(19f),
            lineHeight = m.sp(30f),
            modifier = Modifier.fillMaxWidth(0.55f),
        )
        Spacer(Modifier.height(m.dp(30f)))
        Box(
            modifier = Modifier
                .tvFocus(
                    cornerRadius = radius,
                    // 金色按钮上再画金边等于没画，焦点色换白
                    borderColor = Color.White.copy(alpha = 0.9f),
                    interactionSource = interaction,
                )
                .focusRequester(focus)
                .clip(RoundedCornerShape(radius))
                .background(Gold)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                ) { onAction() }
                .padding(horizontal = m.dp(30f), vertical = m.dp(16f)),
        ) {
            Text(actionText, color = Ink, fontSize = m.sp(20f), fontWeight = FontWeight.Bold)
        }
    }
}
