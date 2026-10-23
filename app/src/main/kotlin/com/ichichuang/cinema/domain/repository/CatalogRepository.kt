package com.ichichuang.cinema.domain.repository

import com.ichichuang.cinema.domain.model.catalog.CatalogQuery
import com.ichichuang.cinema.domain.model.catalog.CatalogResult
import com.ichichuang.cinema.domain.model.catalog.LoadOutcome
import kotlinx.coroutines.flow.Flow

/** 查询会话与来源游标独立；空列表不推导完整覆盖，协程取消须向上传播。 */
interface CatalogRepository {
    fun observeQuery(query: CatalogQuery): Flow<CatalogResult>
    suspend fun loadMore(queryId: String): LoadOutcome
}
