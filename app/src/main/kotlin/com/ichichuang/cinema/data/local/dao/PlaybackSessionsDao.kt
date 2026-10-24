package com.ichichuang.cinema.data.local.dao

import androidx.room.*
import com.ichichuang.cinema.data.local.codec.Utf16KeyConverter
import com.ichichuang.cinema.data.local.entity.*

@Dao
internal interface PlaybackSessionsDao {
    @Insert suspend fun insertSession(value: PlaybackSessionEntity): Long
    @Update suspend fun updateSession(value: PlaybackSessionEntity)
    @Query("SELECT * FROM playback_sessions WHERE sessionId = :sessionId")
    suspend fun session(sessionId: ByteArray): PlaybackSessionEntity?
    @Query("SELECT MAX(generation) FROM playback_sessions WHERE workId = :workId")
    suspend fun latestGeneration(workId: ByteArray): Long?
    @Upsert suspend fun upsertOrder(value: PlaybackRecordOrderEntity)
    @Query("SELECT * FROM playback_record_order WHERE workId = :workId AND episodeKey = :episodeKey AND variantKey = :variantKey")
    suspend fun order(workId: ByteArray, episodeKey: ByteArray, variantKey: ByteArray): PlaybackRecordOrderEntity?
    @Upsert suspend fun upsertResume(value: PlaybackResumeEntity)
    @Query("SELECT * FROM playback_resume WHERE workId = :workId")
    suspend fun resume(workId: ByteArray): PlaybackResumeEntity?

    suspend fun session(id: String) = session(Utf16KeyConverter().encode(id))
    suspend fun latestGeneration(workId: String) = latestGeneration(Utf16KeyConverter().encode(workId))
    suspend fun order(workId: String, episodeKey: String, variantKey: String) = Utf16KeyConverter().let {
        order(it.encode(workId), it.encode(episodeKey), it.encode(variantKey))
    }
    suspend fun resume(workId: String) = resume(Utf16KeyConverter().encode(workId))
}
