package com.ichichuang.cinema.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Separate candidate snapshots; none is an active application setting or playable media claim. */
@Entity(tableName = "candidate_capability_state")
internal data class CandidateCapabilityStateEntity(@PrimaryKey val singleton: Int = 1, val payload: String)

@Entity(tableName = "candidate_query_sessions")
internal data class CandidateQuerySessionEntity(
    @PrimaryKey @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val queryId: String,
    val payload: String,
)

@Entity(tableName = "candidate_subscription_state")
internal data class CandidateSubscriptionStateEntity(
    @PrimaryKey @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val catalogId: String,
    val payload: String,
)
