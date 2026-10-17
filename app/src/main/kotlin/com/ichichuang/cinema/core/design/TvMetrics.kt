package com.ichichuang.cinema.core.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 电视尺寸适配。
 *
 * 为什么需要：Android TV 设备的 dp 画布差异极大 —— 同样是 1080P，
 * 有的电视报告 960x540dp，有的是 1920x1080dp。写死 dp 的界面必翻车。
 * 方案：全部按「千分之一屏宽」缩放，任何电视上观感一致。
 *
 * 用法：
 *   val m = rememberTvMetrics()
 *   Modifier.padding(m.dp(48f))   // 屏宽的 4.8%
 *   Text(fontSize = m.sp(20f))
 */
class TvMetrics(val scale: Float) {
    fun dp(v: Float) = (v * scale).dp
    fun sp(v: Float) = (v * scale).sp
    fun dp(v: Int) = (v * scale).dp
    fun sp(v: Int) = (v * scale).sp
}

/** 由 CinemaTheme 提供，页面不要自己创建 */
val LocalTvMetrics = compositionLocalOf { TvMetrics(1f) }

@Composable
internal fun ProvideTvMetrics(content: @Composable () -> Unit) {
    val w = LocalConfiguration.current.screenWidthDp
    val metrics = remember(w) { TvMetrics((w / 1000f).coerceIn(0.8f, 2.2f)) }
    CompositionLocalProvider(LocalTvMetrics provides metrics, content = content)
}

@Composable
fun rememberTvMetrics(): TvMetrics = LocalTvMetrics.current
