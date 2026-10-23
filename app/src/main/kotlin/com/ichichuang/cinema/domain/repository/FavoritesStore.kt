package com.ichichuang.cinema.domain.repository

import com.ichichuang.cinema.domain.model.userdata.CommitResult

interface FavoritesStore {
    /** workId 是内部 WorkId.value；selected 是幂等目标值，不是翻转指令。取消向上传播。 */
    suspend fun setFavorite(workId: String, selected: Boolean): CommitResult
}
