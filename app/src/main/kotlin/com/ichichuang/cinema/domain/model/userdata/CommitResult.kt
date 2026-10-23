package com.ichichuang.cinema.domain.model.userdata

sealed interface CommitResult {
    /** 仅在目标存储真实提交成功后返回；此类型本身不执行写入。 */
    data object Committed : CommitResult
    data object RejectedStale : CommitResult
    /** 内部错误代码，不是原始异常或直接展示给用户的文案。 */
    data class Failed(val reason: String) : CommitResult {
        init { require(reason.isNotBlank()) { "Failure reason must not be blank" } }
    }
}
