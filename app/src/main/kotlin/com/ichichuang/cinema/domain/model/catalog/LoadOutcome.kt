package com.ichichuang.cinema.domain.model.catalog

enum class PartialReason { BUDGET_EXHAUSTED, SOURCE_FAILURE }

sealed interface LoadOutcome {
    data object Loaded : LoadOutcome
    data object Complete : LoadOutcome
    data class Partial(val reason: PartialReason) : LoadOutcome
    /** 会话已取消的观察结果；协程自身取消仍须传播 CancellationException。 */
    data object Cancelled : LoadOutcome
    data class Failed(val reason: String) : LoadOutcome {
        init { require(reason.isNotBlank()) { "Failure reason must not be blank" } }
    }
}
