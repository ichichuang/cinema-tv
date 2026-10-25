package com.ichichuang.cinema.feature.library

import com.ichichuang.cinema.domain.model.catalog.CatalogQuery
import com.ichichuang.cinema.feature.home.visual.HomeImageShape

/**
 * 片库只读 UI 投影 —— 不是新的权威影片记录、来源状态或查询模型。
 * 全部字段来自既有 R09 `CatalogResult`、R10 `CatalogQuery` 与运行时真实加载状态；
 * 缺失事实保持缺失，不由投影补造（年份未知不显示具体年份，覆盖未知不显示总数）。
 */
internal enum class LibraryContentStatus { UNCONFIGURED, LOADING, READY, COMPLETE_EMPTY, PARTIAL, OFFLINE, FAILED }

/** 稳定 workId 为 R09 已核对分组的 groupWorkId；yearText 仅在有依据时给出。 */
internal data class LibraryCardUi(
    val workId: String,
    val title: String,
    val yearText: String?,
    val artworkUri: String?,
    val imageShape: HomeImageShape,
)

/** 覆盖区分完整/部分、失败来源数、是否仍有未核对内容、预算停止与可选的作用域内总数。 */
internal data class LibraryCoverageUi(
    val complete: Boolean,
    val hasMore: Boolean,
    val failedSourceIds: Set<String>,
    val budgetExhausted: Boolean,
    val verifiedLocalCount: Int,
    /** 仅在覆盖完成且相关事实可判定时非空；部分覆盖一律 null，不得渲染全量“共 N 部”。 */
    val scopedTotal: Long?,
)

/** 筛选摘要片：key 为规范字段:取值（不是本地化文案），label 为中文显示。 */
internal data class LibraryFilterChipUi(
    val key: String,
    val field: String,
    val valueId: String?,
    val label: String,
)

internal data class LibraryUiState(
    val appliedQuery: CatalogQuery,
    val status: LibraryContentStatus,
    val items: List<LibraryCardUi>,
    val coverage: LibraryCoverageUi,
    val filterSummary: List<LibraryFilterChipUi>,
    val canContinue: Boolean,
    val isLoading: Boolean,
    val statusText: String,
)
