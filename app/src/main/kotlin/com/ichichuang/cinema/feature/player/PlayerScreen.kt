package com.ichichuang.cinema.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.ichichuang.cinema.core.design.Gold
import com.ichichuang.cinema.core.design.Ink
import com.ichichuang.cinema.core.design.TextMain
import com.ichichuang.cinema.core.design.TextSub
import com.ichichuang.cinema.core.design.rememberTvMetrics

/**
 * 播放器页（阶段 3 实现）。
 *
 * 预留的架构决定：
 * - 引擎用 Media3 ExoPlayer（纯 Java，无 ABI 风险），m3u8/HLS 原生支持
 * - UI 用官方的 androidx.media3:media3-ui-compose
 * - 播放位置写回 Room，回到首页时按钮变"继续观看 12:34"
 * - 单线路播不动时，右上角常驻"切换线路"提示
 */
@Composable
fun PlayerScreen(title: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val m = rememberTvMetrics()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF17171C), Ink))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "▶",
                color = Gold,
                fontSize = m.sp(72f),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(m.dp(24f)))
            Text(
                text = title.ifBlank { "未选择影片" },
                color = TextMain,
                fontSize = m.sp(34f),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(m.dp(14f)))
            Text(
                text = "阶段 3 接入播放器：m3u8 / HLS · 换线路 · 进度记忆",
                color = TextSub,
                fontSize = m.sp(18f),
            )
            Spacer(Modifier.height(m.dp(44f)))
            Text(
                text = "按遥控器「返回」键回到首页",
                color = TextSub.copy(alpha = 0.7f),
                fontSize = m.sp(16f),
            )
        }
    }
}
