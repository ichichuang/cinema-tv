package com.ichichuang.cinema.domain.model.subscription

sealed interface SubscriptionState {
    data object Idle : SubscriptionState
    data object Checking : SubscriptionState
    data object CatalogEmpty : SubscriptionState
    data class CandidatesFound(val count: Int) : SubscriptionState {
        init { require(count > 0) { "CandidatesFound requires a positive count" } }
    }
    data object Saving : SubscriptionState
    /** 只证明配置已提交；不声明目录或任何媒体入口可播放。 */
    data class Configured(val configurationRevision: Long) : SubscriptionState {
        init { require(configurationRevision >= 0) { "configurationRevision must not be negative" } }
    }
    data class Failed(val reason: String) : SubscriptionState {
        init { require(reason.isNotBlank()) { "Failure reason must not be blank" } }
    }
}
