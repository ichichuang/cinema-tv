package com.ichichuang.cinema.feature.home.visual

/**
 * 首页展示状态 —— 只读 UI 投影，不是新的权威影片记录。
 * 全部字段来自既有 R08–R12 候选数据；缺失字段保持缺失，不由投影补造。
 */
internal enum class HomeContentStatus { UNCONFIGURED, LOADING, READY, PARTIAL, OFFLINE, FAILED }

internal enum class HomeImageShape { LANDSCAPE, PORTRAIT, UNKNOWN }

internal data class HomeDisplayCard(
    val workId: String,
    val title: String,
    val artworkUri: String?,
    val imageShape: HomeImageShape,
    val secondaryText: String?,
    val progressText: String?,
)

internal data class HomeDisplayState(
    val continueCards: List<HomeDisplayCard>,
    val recentCards: List<HomeDisplayCard>,
    val catalogCards: List<HomeDisplayCard>,
    val contentStatus: HomeContentStatus,
    val failedSourceCount: Int,
    val hasMore: Boolean,
    val totalKnown: Int?,
)
