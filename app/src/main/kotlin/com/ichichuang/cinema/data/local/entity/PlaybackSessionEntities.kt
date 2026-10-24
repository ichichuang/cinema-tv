package com.ichichuang.cinema.data.local.entity

import androidx.room.*

@Entity(tableName = "playback_sessions", indices = [Index(value = ["sessionId"], unique = true), Index(value = ["workId"])],
    foreignKeys = [ForeignKey(entity = WorkReferenceEntity::class, parentColumns = ["workId"], childColumns = ["workId"], onDelete = ForeignKey.RESTRICT)])
internal data class PlaybackSessionEntity(
    @PrimaryKey(autoGenerate = true) val generation: Long = 0,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val sessionId: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val workId: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val episodeKey: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val variantKey: String,
    val sequence: Long = -1,
    val payload: String? = null,
)

/** Selection floor, independent of whether this episode has a qualifying checkpoint yet. */
@Entity(tableName = "playback_record_order", primaryKeys = ["workId", "episodeKey", "variantKey"],
    indices = [Index(value = ["generation"])], foreignKeys = [
        ForeignKey(entity = WorkReferenceEntity::class, parentColumns = ["workId"], childColumns = ["workId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = PlaybackSessionEntity::class, parentColumns = ["generation"], childColumns = ["generation"], onDelete = ForeignKey.RESTRICT)])
internal data class PlaybackRecordOrderEntity(
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val workId: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val episodeKey: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val variantKey: String,
    val generation: Long,
)

@Entity(tableName = "playback_resume", indices = [Index(value = ["generation"])], foreignKeys = [
    ForeignKey(entity = WorkReferenceEntity::class, parentColumns = ["workId"], childColumns = ["workId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = PlaybackSessionEntity::class, parentColumns = ["generation"], childColumns = ["generation"], onDelete = ForeignKey.RESTRICT)])
internal data class PlaybackResumeEntity(
    @PrimaryKey @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val workId: String,
    val generation: Long,
    val payload: String,
)
