package com.ichichuang.cinema.domain.repository

import com.ichichuang.cinema.domain.model.userdata.CommitResult
import com.ichichuang.cinema.domain.model.userdata.PlaybackSnapshot

interface PlaybackProgressStore {
    /** 原子核对会话及会话内序号；只有实际持久提交才返回 Committed，取消向上传播。 */
    suspend fun commit(snapshot: PlaybackSnapshot): CommitResult
}
