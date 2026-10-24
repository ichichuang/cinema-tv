package com.ichichuang.cinema.data.local.dao

import androidx.room.*
import com.ichichuang.cinema.data.local.entity.LegacyHandoffEntity

@Dao
internal interface LegacyHandoffDao {
    @Query("SELECT * FROM legacy_handoff_state WHERE id = 1") suspend fun state(): LegacyHandoffEntity?
    @Upsert suspend fun put(state: LegacyHandoffEntity)
}
