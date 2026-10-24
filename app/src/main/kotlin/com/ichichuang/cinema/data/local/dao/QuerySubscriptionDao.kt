package com.ichichuang.cinema.data.local.dao

import androidx.room.*
import com.ichichuang.cinema.data.local.entity.*

/** Callers own a real withTransaction boundary for validation and replacement. */
@Dao
internal interface QuerySubscriptionDao {
    @Query("SELECT * FROM candidate_capability_state WHERE singleton=1") suspend fun capability(): CandidateCapabilityStateEntity?
    @Upsert suspend fun capability(row: CandidateCapabilityStateEntity)
    @Query("SELECT * FROM candidate_query_sessions WHERE queryId=:id") suspend fun query(id: ByteArray): CandidateQuerySessionEntity?
    @Upsert suspend fun query(row: CandidateQuerySessionEntity)
    @Query("SELECT count(*) FROM candidate_query_sessions") suspend fun queryCount(): Int
    @Query("SELECT * FROM candidate_subscription_state WHERE catalogId=:id") suspend fun subscription(id: ByteArray): CandidateSubscriptionStateEntity?
    @Upsert suspend fun subscription(row: CandidateSubscriptionStateEntity)
    @Query("SELECT count(*) FROM candidate_subscription_state") suspend fun subscriptionCount(): Int
}
