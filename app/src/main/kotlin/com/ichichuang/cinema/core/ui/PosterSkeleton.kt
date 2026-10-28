package com.ichichuang.cinema.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.ichichuang.cinema.core.design.LocalReducedMotion
import com.ichichuang.cinema.core.design.Motion
import com.ichichuang.cinema.core.design.Panel
import com.ichichuang.cinema.core.design.rememberTvMetrics
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition

/**
 * 海报骨架：加载数据时用"微光占位"，绝不是白屏或转圈。
 * 张数和真实结果接近，数据到货时布局不会跳。
 */
@Composable
fun PosterSkeleton() {
    val m = rememberTvMetrics()
    val transition = rememberInfiniteTransition(label = "skeleton")
    val animated by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = Motion.pulse(),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    // Reduced motion: hold a steady dim tone instead of pulsing.
    val alpha = if (LocalReducedMotion.current) 0.5f else animated
    val shape = RoundedCornerShape(m.dp(14f))
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(Panel.copy(alpha = alpha))
        )
        Spacer(Modifier.height(m.dp(10f)))
        Box(
            Modifier
                .fillMaxWidth(0.7f)
                .height(m.dp(16f))
                .clip(RoundedCornerShape(m.dp(4f)))
                .background(Panel.copy(alpha = alpha))
        )
        Spacer(Modifier.height(m.dp(6f)))
        Box(
            Modifier
                .fillMaxWidth(0.5f)
                .height(m.dp(12f))
                .clip(RoundedCornerShape(m.dp(4f)))
                .background(Panel.copy(alpha = alpha))
        )
    }
}
