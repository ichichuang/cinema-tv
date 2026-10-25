package com.ichichuang.cinema.feature.home.visual

import com.ichichuang.cinema.domain.model.catalog.CatalogResult
import com.ichichuang.cinema.domain.model.catalog.QueryCoverage

/** 候选目录条目的最小投影输入；缺失字段保持缺失。 */
internal data class HomeCatalogEntry(
    val workId: String,
    val title: String,
    val artworkUri: String?,
    val imageShape: HomeImageShape,
)

/** 个人记录卡的最小投影输入，字段取自 R12 真实存储投影。 */
internal data class HomePersonalEntry(
    val workId: String,
    val title: String,
    val sourceLabel: String?,
    val episodeId: String?,
    val variantId: String?,
    val positionMs: Long?,
    val durationMs: Long?,
    val inProgress: Boolean,
    val completedEpisode: Boolean,
)

/**
 * 纯函数首页投影：只重组既有数据，不发明事实。
 * 状态优先级：读取失败 FAILED > 仍在装载 LOADING > 未配置来源 UNCONFIGURED >
 * 离线 OFFLINE > 部分覆盖 PARTIAL > 完整 READY。
 * totalKnown 只在查询声明完整时采用其核对计数；部分覆盖一律为 null，绝不以加和冒充总数。
 */
internal object HomeProjection {
    fun project(
        catalog: List<HomeCatalogEntry>,
        result: CatalogResult?,
        sourcesConfigured: Boolean,
        continueEntries: List<HomePersonalEntry>,
        recentEntries: List<HomePersonalEntry>,
        offline: Boolean,
        loadFailed: Boolean,
        loading: Boolean,
    ): HomeDisplayState {
        val status = when {
            loadFailed -> HomeContentStatus.FAILED
            loading || result == null -> HomeContentStatus.LOADING
            !sourcesConfigured -> HomeContentStatus.UNCONFIGURED
            offline -> HomeContentStatus.OFFLINE
            result.coverage == QueryCoverage.PARTIAL -> HomeContentStatus.PARTIAL
            else -> HomeContentStatus.READY
        }
        val artwork = catalog.associateBy { it.workId }
        fun card(entry: HomePersonalEntry, resume: Boolean): HomeDisplayCard {
            val known = artwork[entry.workId]
            val progress = if (resume) progressText(entry) else null
            val secondary = entry.sourceLabel?.takeIf { it.isNotBlank() }
                ?: known?.title?.let { return@card HomeDisplayCard(entry.workId, entry.title, known.artworkUri, known.imageShape, null, progress) }
            return HomeDisplayCard(entry.workId, entry.title, known?.artworkUri, known?.imageShape ?: HomeImageShape.UNKNOWN, secondary, progress)
        }
        val continueCards = continueEntries.filter { it.inProgress }.map { card(it, resume = true) }
        val recentCards = recentEntries.map { card(it, resume = false) }
        val catalogCards = catalog.map { entry ->
            HomeDisplayCard(entry.workId, entry.title, entry.artworkUri, entry.imageShape, null, null)
        }
        // 总数只在“已配置来源且查询声明完整”时呈现；未配置/离线/部分一律不得冒充总数。
        val totalKnown = if (status == HomeContentStatus.READY && result?.coverage == QueryCoverage.COMPLETE)
            result.knownMatchedCount?.toInt() else null
        return HomeDisplayState(continueCards, recentCards, catalogCards, status,
            result?.failedSourceIds?.size ?: 0, result?.hasMore ?: false, totalKnown)
    }

    /** 进度文案只来自已确认检查点；未知时长不显示总量，不猜测剩余时间。 */
    fun progressText(entry: HomePersonalEntry): String? {
        if (!entry.inProgress) return null
        if (entry.completedEpisode) return "本集已看完"
        val position = entry.positionMs ?: return "已确认实际观看，进度尚未保存"
        val seconds = position / 1000
        val text = if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
        else "%d:%02d".format(seconds / 60, seconds % 60)
        val duration = entry.durationMs?.let { it / 1000 }
        val durationText = duration?.let { d ->
            if (d >= 3600) "%d:%02d:%02d".format(d / 3600, (d % 3600) / 60, d % 60)
            else "%d:%02d".format(d / 60, d % 60)
        }
        val episode = entry.episodeId?.let { "$it · " } ?: ""
        return episode + text + (durationText?.let { " / $it" } ?: "")
    }
}
