package com.ichichuang.cinema.domain.model.userdata

import com.ichichuang.cinema.domain.model.catalog.EpisodeId
import com.ichichuang.cinema.domain.model.catalog.VariantId
import com.ichichuang.cinema.domain.model.catalog.WorkId

enum class WatchState { NOT_STARTED, IN_PROGRESS, COMPLETED }

/** 序号仅在 sessionId 内有序；capturedAt 是 Unix 毫秒记录时间，不用于跨会话排序。 */
data class PlaybackSnapshot(
    val workId: WorkId,
    val episodeId: EpisodeId?,
    val variantId: VariantId?,
    val sessionId: String,
    val operationSequence: Long,
    val positionMs: Long,
    val durationMs: Long?,
    val watchState: WatchState,
    val capturedAt: Long,
) {
    init {
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        require(operationSequence >= 0) { "operationSequence must not be negative" }
        require(positionMs >= 0) { "positionMs must not be negative" }
        require(durationMs == null || durationMs >= 0) { "durationMs must not be negative" }
    }
}
