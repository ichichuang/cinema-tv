package com.ichichuang.cinema.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

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
                // 输入框未聚焦时的描边：Material 默认是带紫调的灰，和暖金主题不搭
                outline = TextSub.copy(alpha = 0.55f),
                outlineVariant = TextSub.copy(alpha = 0.25f),
            ),
            content = content,
        )
    }
}

