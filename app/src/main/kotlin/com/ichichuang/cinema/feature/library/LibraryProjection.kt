package com.ichichuang.cinema.feature.library

import com.ichichuang.cinema.domain.model.catalog.CatalogQuery
import com.ichichuang.cinema.domain.model.catalog.CatalogResult
import com.ichichuang.cinema.domain.model.catalog.FieldEvidence
import com.ichichuang.cinema.domain.model.catalog.QueryCoverage
import com.ichichuang.cinema.feature.home.visual.HomeImageShape

/**
 * 片库投影（纯函数）：把既有 R09 查询结果与真实加载状态映射为只读 UI 状态。
 * 状态优先级 FAILED > LOADING > UNCONFIGURED > OFFLINE > PARTIAL > READY/COMPLETE_EMPTY；
 * 部分覆盖一律不给出总数；零匹配且仍有来源未核对时保留继续核对，不宣告最终空。
 */
internal object LibraryProjection {
    /** 已核对分组作品的可显示资料；缺图保持缺图，形状未知保持未知。 */
    data class WorkDisplay(val title: String, val artworkUri: String?, val imageShape: HomeImageShape)

    fun project(
        appliedQuery: CatalogQuery,
        result: CatalogResult?,
        works: Map<String, WorkDisplay>,
        sourcesConfigured: Boolean,
        loading: Boolean,
        offline: Boolean,
        loadFailed: Boolean,
        budgetExhausted: Boolean,
    ): LibraryUiState {
        val items = result?.items?.map { item ->
            val display = works[item.workId.value]
            LibraryCardUi(
                workId = item.workId.value,
                title = item.title,
                yearText = when (val year = item.year) {
                    is FieldEvidence.SourceReported -> year.value.toString()
                    is FieldEvidence.Confirmed -> year.value.toString()
                    // 冲突与未知都不显示具体年份；卡片层如实标注“年份未知”，不挑争议值。
                    else -> null
                },
                artworkUri = display?.artworkUri,
                imageShape = display?.imageShape ?: HomeImageShape.UNKNOWN,
            )
        } ?: emptyList()
        val verified = items.map(LibraryCardUi::workId).distinct().size
        val complete = result?.coverage == QueryCoverage.COMPLETE
        val failedSources = result?.failedSourceIds?.map { it.value }?.toSet() ?: emptySet()
        val hasMore = result?.hasMore == true
        val coverage = LibraryCoverageUi(
            complete = complete,
            hasMore = hasMore,
            failedSourceIds = failedSources,
            budgetExhausted = budgetExhausted,
            verifiedLocalCount = verified,
            scopedTotal = result?.knownMatchedCount,
        )
        val status = when {
            loadFailed && items.isEmpty() -> LibraryContentStatus.FAILED
            loading && items.isEmpty() -> LibraryContentStatus.LOADING
            !sourcesConfigured -> LibraryContentStatus.UNCONFIGURED
            offline -> LibraryContentStatus.OFFLINE
            result == null -> LibraryContentStatus.LOADING
            !complete -> LibraryContentStatus.PARTIAL
            items.isEmpty() -> LibraryContentStatus.COMPLETE_EMPTY
            else -> LibraryContentStatus.READY
        }
        val canContinue = sourcesConfigured && !offline && !loading && result != null &&
            (hasMore || !complete)
        val statusText = when (status) {
            LibraryContentStatus.UNCONFIGURED -> "尚未配置任何来源；已保存的个人记录不受影响，可前往设置查看来源状态"
            LibraryContentStatus.LOADING -> "正在按当前条件核对来源"
            LibraryContentStatus.OFFLINE ->
                if (items.isEmpty()) "离线状态：暂无可展示的核对结果；恢复网络后可继续核对"
                else "离线状态：已核对的 $verified 项仍可查看；在线来源暂无法继续核对"
            LibraryContentStatus.FAILED -> "来源核对失败，原查询与记录保持不变；可重试"
            LibraryContentStatus.PARTIAL -> when {
                failedSources.isNotEmpty() -> "部分来源未完成或失败（${failedSources.size} 个失败）；已核对 $verified 项，结果不代表全部片库"
                budgetExhausted -> "本次核对已达预算上限；已核对 $verified 项，结果不完整，可继续核对后续内容"
                else -> "仍有来源未核对完成；已核对 $verified 项，结果不代表全部片库"
            }
            LibraryContentStatus.COMPLETE_EMPTY -> "当前筛选与来源范围已完整核对，暂无匹配影片；可调整筛选条件或来源"
            LibraryContentStatus.READY -> result?.knownMatchedCount?.let { total ->
                "当前筛选与来源范围已完整核对，共 $total 部匹配影片"
            } ?: "已完整核对当前筛选与来源范围，共 $verified 部匹配影片"
        }
        return LibraryUiState(appliedQuery, status, items, coverage,
            LibraryFilterLabels.chips(appliedQuery), canContinue, loading, statusText)
    }
}
