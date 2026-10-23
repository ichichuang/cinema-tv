package com.ichichuang.cinema.domain.repository

import com.ichichuang.cinema.domain.model.userdata.BrowseSnapshot
import com.ichichuang.cinema.domain.model.userdata.CommitResult

/** 按 contextKey 隔离已提交现场，取消向上传播。 */
interface BrowseStateStore {
    suspend fun save(snapshot: BrowseSnapshot): CommitResult
    /** null 只表示没有已提交记录；读取错误不能伪装缺失。 */
    suspend fun load(contextKey: String): BrowseSnapshot?
}
