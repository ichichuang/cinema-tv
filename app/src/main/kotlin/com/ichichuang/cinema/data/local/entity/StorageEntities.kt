package com.ichichuang.cinema.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.ichichuang.cinema.data.local.codec.PlaybackSnapshotCodec
import com.ichichuang.cinema.data.local.codec.StorageReadException
import com.ichichuang.cinema.data.local.codec.StorageReadFailure
import com.ichichuang.cinema.domain.model.userdata.PlaybackSnapshot

@Entity(tableName = "work_references")
internal data class WorkReferenceEntity(@PrimaryKey @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val workId: String)

@Entity(tableName = "work_metadata_cache", foreignKeys = [ForeignKey(
    entity = WorkReferenceEntity::class, parentColumns = ["workId"], childColumns = ["workId"], onDelete = ForeignKey.RESTRICT)])
internal data class WorkMetadataCacheEntity(@PrimaryKey @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val workId: String, val payload: String)

@Entity(tableName = "favorites", foreignKeys = [ForeignKey(
    entity = WorkReferenceEntity::class, parentColumns = ["workId"], childColumns = ["workId"], onDelete = ForeignKey.RESTRICT)])
internal data class FavoriteEntity(@PrimaryKey @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val workId: String)

@Entity(tableName = "browse_snapshots")
internal data class BrowseSnapshotEntity(@PrimaryKey @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val contextKey: String, val payload: String)

/** Low-level persistence only. No session arbitration or PlaybackProgressStore implementation. */
@Entity(tableName = "playback_snapshots", primaryKeys = ["workId", "episodeKey", "variantKey"], foreignKeys = [ForeignKey(
    entity = WorkReferenceEntity::class, parentColumns = ["workId"], childColumns = ["workId"], onDelete = ForeignKey.RESTRICT)])
internal data class PlaybackSnapshotEntity(
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val workId: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val episodeKey: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val variantKey: String,
    val payload: String,
) {
    fun snapshot(): PlaybackSnapshot = PlaybackSnapshotCodec.decode(payload).also {
        if (it.workId.value != workId || (it.episodeId?.value ?: "") != episodeKey || (it.variantId?.value ?: "") != variantKey) {
            throw StorageReadException(StorageReadFailure.MALFORMED)
        }
    }

    companion object {
        // Empty cannot be a valid EpisodeId/VariantId, so these SQL key sentinels are injective.
        fun from(snapshot: PlaybackSnapshot) = PlaybackSnapshotEntity(snapshot.workId.value,
            snapshot.episodeId?.value ?: "", snapshot.variantId?.value ?: "", PlaybackSnapshotCodec.encode(snapshot))
    }
}

@Entity(tableName = "legacy_import_batches", primaryKeys = ["importVersion", "contentDigest"])
internal data class LegacyImportBatchEntity(
    val importVersion: Int, val contentDigest: String, val capturedAt: Long,
    val totalCount: Int, val recognizedCount: Int, val unresolvedCount: Int, val malformedCount: Int,
)

@Entity(tableName = "legacy_import_records", primaryKeys = ["importVersion", "contentDigest", "storeKind", "entryKey"],
    foreignKeys = [ForeignKey(entity = LegacyImportBatchEntity::class,
        parentColumns = ["importVersion", "contentDigest"], childColumns = ["importVersion", "contentDigest"],
        onDelete = ForeignKey.RESTRICT, deferred = true)])
internal data class LegacyImportRecordEntity(
    val importVersion: Int, val contentDigest: String, val storeKind: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val entryKey: String,
    val valueType: String, val rawValue: String, val status: String, val reason: String,
    val legacyLineIndex: Int?, val legacyEpisodeIndex: Int?, val legacyPositionMs: Long?, val legacyDurationMs: Long?,
)
