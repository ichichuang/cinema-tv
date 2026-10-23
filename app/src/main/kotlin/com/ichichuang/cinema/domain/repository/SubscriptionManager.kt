package com.ichichuang.cinema.domain.repository

import com.ichichuang.cinema.domain.model.subscription.SubscriptionRefreshRequest
import com.ichichuang.cinema.domain.model.subscription.SubscriptionState
import kotlinx.coroutines.flow.Flow

interface SubscriptionManager {
    /** 核对真实用户授权；失败保留有效配置，提交配置不表示媒体可播放；取消向上传播。 */
    fun refresh(request: SubscriptionRefreshRequest): Flow<SubscriptionState>
}
