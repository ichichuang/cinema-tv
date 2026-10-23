package com.ichichuang.cinema.domain.model.userdata

import com.ichichuang.cinema.domain.model.catalog.CatalogQuery
import com.ichichuang.cinema.domain.model.catalog.WorkId

/** relativeOffset 为锚点起点相对视口起点的主轴偏移 / 视口长度，允许负值；savedAt 为 Unix 毫秒。 */
data class BrowseSnapshot(
    val contextKey: String,
    val route: String,
    val parentRoute: String?,
    val query: CatalogQuery?,
    val resultRevision: Long?,
    val focusedWorkId: WorkId?,
    val anchorWorkId: WorkId?,
    val relativeOffset: Float,
    val savedAt: Long,
) {
    init {
        require(contextKey.isNotBlank()) { "contextKey must not be blank" }
        require(route.isNotBlank()) { "route must not be blank" }
        require(parentRoute == null || parentRoute.isNotBlank()) { "parentRoute must not be blank" }
        require(resultRevision == null || resultRevision >= 0) { "resultRevision must not be negative" }
        require(relativeOffset.isFinite()) { "relativeOffset must be finite" }
    }
}
