package com.ichichuang.cinema.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 原创电视图标几何 —— 全部路径由本项目绘制，统一 24×24 网格、
 * 2 单位圆头描边、无填充（播放等个别概念同样以描边呈现，保持整套语言一致）。
 *
 * 可编辑母版位于仓库 `design/icons` 目录（tv-*.svg），其 `d` 属性与本文件中的
 * [TvIconPaths] 字符串逐字符一致；两者由 JVM 测试双向核对。
 * 图标不含第三方图标库的路径数据，也不从参考效果图裁图。
 * 光学微调（个别概念偏离正网格）记录在 `design/icons/README.md`。
 */
internal object TvIconPaths {
    const val HOME = "M4.5 11.2 L12 4.8 L19.5 11.2 M6.2 10.4 V19.2 H17.8 V10.4 M10.4 19.2 V13.8 H13.6 V19.2"
    const val LIBRARY = "M4.5 5 H19.5 V19 H4.5 Z M9 5 V19 M15 5 V19 M4.5 9.5 H9 M4.5 14.5 H9 M15 9.5 H19.5 M15 14.5 H19.5"
    const val FAVORITE = "M12 19.5 C7.2 16.2 4.2 13.2 4.2 9.9 C4.2 7.4 6.2 5.6 8.5 5.6 C10 5.6 11.3 6.4 12 7.7 C12.7 6.4 14 5.6 15.5 5.6 C17.8 5.6 19.8 7.4 19.8 9.9 C19.8 13.2 16.8 16.2 12 19.5 Z"
    const val HISTORY = "M12 5.2 A6.8 6.8 0 1 1 5.2 12 M5.2 8.4 L5.2 12 L8.8 12 M12 8.8 V12 L14.6 13.4"
    const val SETTINGS = "M4.5 7.2 H20 M4.5 12 H20 M4.5 16.8 H20 M7.5 7.2 A2 2 0 1 0 11.5 7.2 A2 2 0 1 0 7.5 7.2 M13 12 A2 2 0 1 0 17 12 A2 2 0 1 0 13 12 M6 16.8 A2 2 0 1 0 10 16.8 A2 2 0 1 0 6 16.8"
    const val PLAY = "M8.6 5.9 C8.6 5.1 9.5 4.6 10.2 5.0 L18.4 10.5 C19.1 11.0 19.1 12.1 18.4 12.6 L10.2 18.2 C9.5 18.6 8.6 18.1 8.6 17.3 Z"
    const val DETAILS = "M10.6 5.0 A5.6 5.6 0 1 0 10.6 16.2 A5.6 5.6 0 1 0 10.6 5.0 M14.7 14.7 L19.8 19.8 M8.2 9.2 H13.0 M8.2 11.8 H11.4"
    const val FILTER = "M4.5 5.5 H19.5 L14.2 11.8 V18.5 L9.8 16.0 V11.8 Z"
    const val BACK = "M14.6 4.8 L7.4 12.0 L14.6 19.2"
    const val RETRY = "M19.2 12.0 A7.2 7.2 0 1 1 15.9 6.3 M15.9 6.3 L16.4 2.9 M15.9 6.3 L12.6 6.0"
    const val TEXT = "M5.5 18.0 L12.0 6.0 L18.5 18.0 M8.0 13.0 H16.0"
    const val COLUMNS = "M4.5 4.5 H19.5 V19.5 H4.5 Z M12.0 4.5 V19.5 M4.5 12.0 H19.5"
}

/** 原生矢量映射；描边颜色固定中性值，由调用方以 tint 统一着色。 */
internal object TvOriginalIcons {
    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = PathParser().parsePathString(pathData).toNodes(), name = "$name-path",
                pathFillType = PathFillType.NonZero, fill = null,
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .build()

    val Home: ImageVector by lazy { icon("tv-home", TvIconPaths.HOME) }
    val Library: ImageVector by lazy { icon("tv-library", TvIconPaths.LIBRARY) }
    val Favorite: ImageVector by lazy { icon("tv-favorite", TvIconPaths.FAVORITE) }
    val History: ImageVector by lazy { icon("tv-history", TvIconPaths.HISTORY) }
    val Settings: ImageVector by lazy { icon("tv-settings", TvIconPaths.SETTINGS) }
    val Play: ImageVector by lazy { icon("tv-play", TvIconPaths.PLAY) }
    val Details: ImageVector by lazy { icon("tv-details", TvIconPaths.DETAILS) }
    val Filter: ImageVector by lazy { icon("tv-filter", TvIconPaths.FILTER) }
    val Back: ImageVector by lazy { icon("tv-back", TvIconPaths.BACK) }
    val Retry: ImageVector by lazy { icon("tv-retry", TvIconPaths.RETRY) }
    val Text: ImageVector by lazy { icon("tv-text", TvIconPaths.TEXT) }
    val Columns: ImageVector by lazy { icon("tv-columns", TvIconPaths.COLUMNS) }

    /** 概念 → 中文无障碍名称的唯一映射，导航与按钮共用，避免同义文案漂移。 */
    val accessibilityLabels: Map<String, String> = mapOf(
        "home" to "首页", "library" to "片库", "favorite" to "我的收藏", "history" to "最近观看",
        "settings" to "设置", "play" to "播放", "details" to "查看详情", "filter" to "筛选",
        "back" to "返回", "retry" to "重试", "text" to "大字模式", "columns" to "列数")
}
