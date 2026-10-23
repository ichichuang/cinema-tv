package com.ichichuang.cinema.domain.model.subscription

import com.ichichuang.cinema.domain.model.catalog.SourceId
import java.util.Collections

enum class RefreshReason { USER_REQUEST, AUTOMATIC_REFRESH, RETRY }

/** 目录身份不产生用户授权；null 表示尚无授权修订，非 null 也须由管理器核对本地授权。 */
@ConsistentCopyVisibility
data class SubscriptionRefreshRequest private constructor(
    val reason: RefreshReason,
    val catalogId: String,
    val enabledSourceIds: Set<SourceId>,
    val consentRevision: Long?,
) {
    init {
        require(catalogId.isNotBlank()) { "catalogId must not be blank" }
        require(consentRevision == null || consentRevision >= 0) { "consentRevision must not be negative" }
    }

    companion object {
        fun create(
            reason: RefreshReason,
            catalogId: String,
            enabledSourceIds: Set<SourceId>,
            consentRevision: Long?,
        ): SubscriptionRefreshRequest = SubscriptionRefreshRequest(
            reason, catalogId, Collections.unmodifiableSet(LinkedHashSet(enabledSourceIds)), consentRevision,
        )
    }
}
