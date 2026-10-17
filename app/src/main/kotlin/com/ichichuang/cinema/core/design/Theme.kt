package com.ichichuang.cinema.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 应用主题。已包含电视尺寸适配（TvMetrics），
 * 页面只需要包一层 CinemaTheme，然后 val m = rememberTvMetrics()。
 */
@Composable
fun CinemaTheme(content: @Composable () -> Unit) {
    ProvideTvMetrics {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Gold,
                onPrimary = Ink,
                background = Ink,
                onBackground = TextMain,
                surface = Panel,
                onSurface = TextMain,
                surfaceVariant = PanelHi,
                onSurfaceVariant = TextSub,
            ),
            content = content,
        )
    }
}

/** 没有真实海报图时，用"种子"稳定生成一个深色渐变，比灰色方块高级得多 */
private val placeholderPairs = listOf(
    Color(0xFF1B2A4A) to Color(0xFF0F1626), // 深海蓝
    Color(0xFF3A2456) to Color(0xFF17102A), // 紫罗兰
    Color(0xFF14403C) to Color(0xFF0B1F1E), // 墨绿
    Color(0xFF4A1F2B) to Color(0xFF1E0D13), // 酒红
    Color(0xFF4A3517) to Color(0xFF1E1508), // 琥珀
    Color(0xFF2A2A55) to Color(0xFF14142B), // 靛青
    Color(0xFF1F3A24) to Color(0xFF0E1B10), // 松林
    Color(0xFF2C3238) to Color(0xFF12161A), // 石墨
)

fun placeholderGradient(seed: String): List<Color> {
    val size = placeholderPairs.size
    val i = ((seed.hashCode() % size) + size) % size
    val p = placeholderPairs[i]
    return listOf(p.first, p.second)
}
