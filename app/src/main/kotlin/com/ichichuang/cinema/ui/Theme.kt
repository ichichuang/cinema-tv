package com.ichichuang.cinema.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── 色板（Apple TV 的克制 + Netflix 的深色沉浸）──────────────
val Ink = Color(0xFF0A0A0C)        // 背景：近纯黑，不发灰
val Panel = Color(0xFF16161A)      // 卡片/浮层
val PanelHi = Color(0xFF22222A)    // 卡片高亮
val Gold = Color(0xFFD4A056)       // 强调色：暖金，和任何海报都和谐
val TextMain = Color(0xFFF5F5F7)
val TextSub = Color(0xFF8E8E93)

@Composable
fun CinemaTheme(content: @Composable () -> Unit) {
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
        typography = Typography(defaultFontFamily = FontFamily.SansSerif),
        content = content,
    )
}

/**
 * 电视上不同设备的 dp 画布差异极大（960x540 ~ 1920x1080，取决于厂商给的分辨率倍率）。
 * 统一按「千分之一屏宽」缩放，保证在任何电视上观感一致。
 */
class TvMetrics(val scale: Float) {
    fun dp(v: Float) = (v * scale).dp
    fun sp(v: Float) = (v * scale).sp
}

@Composable
fun rememberTvMetrics(): TvMetrics {
    val w = LocalConfiguration.current.screenWidthDp
    return remember(w) { TvMetrics((w / 1000f).coerceIn(0.8f, 2.2f)) }
}
