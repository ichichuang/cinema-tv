package com.ichichuang.cinema.data.local.dao

import androidx.room.*
import com.ichichuang.cinema.data.local.entity.*
import com.ichichuang.cinema.data.local.codec.Utf16KeyConverter

@Dao
internal interface UserRecordsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertReference(value: WorkReferenceEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertFavorite(value: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE workId = :workId") suspend fun removeFavorite(workId: ByteArray)
    @Query("SELECT * FROM favorites WHERE workId = :workId") suspend fun favorite(workId: ByteArray): FavoriteEntity?
    @Query("SELECT * FROM work_references WHERE workId = :workId") suspend fun reference(workId: ByteArray): WorkReferenceEntity?
    suspend fun removeFavorite(workId: String) = removeFavorite(Utf16KeyConverter().encode(workId))
    suspend fun favorite(workId: String): FavoriteEntity? = favorite(Utf16KeyConverter().encode(workId))
    suspend fun reference(workId: String): WorkReferenceEntity? = reference(Utf16KeyConverter().encode(workId))
    @Query("SELECT COUNT(*) FROM favorites") suspend fun favoriteCount(): Int
    @Query("SELECT COUNT(*) FROM work_references") suspend fun referenceCount(): Int
}

@Dao
internal interface BrowseSnapshotsDao {
    @Upsert suspend fun upsert(value: BrowseSnapshotEntity)
    @Query("SELECT * FROM browse_snapshots WHERE contextKey = :contextKey") suspend fun find(contextKey: ByteArray): BrowseSnapshotEntity?
    suspend fun find(contextKey: String): BrowseSnapshotEntity? = find(Utf16KeyConverter().encode(contextKey))
}

@Dao
internal interface PlaybackSnapshotsDao {
    @Upsert suspend fun upsert(value: PlaybackSnapshotEntity)
    @Query("SELECT * FROM playback_snapshots WHERE workId = :workId AND episodeKey = :episodeKey AND variantKey = :variantKey")
    suspend fun find(workId: ByteArray, episodeKey: ByteArray, variantKey: ByteArray): PlaybackSnapshotEntity?
    suspend fun find(workId: String, episodeKey: String, variantKey: String): PlaybackSnapshotEntity? =
        Utf16KeyConverter().let { find(it.encode(workId), it.encode(episodeKey), it.encode(variantKey)) }
    @Query("SELECT COUNT(*) FROM playback_snapshots") suspend fun count(): Int
}

@Dao
internal interface MetadataCacheDao {
    @Upsert suspend fun upsert(value: WorkMetadataCacheEntity)
    @Query("SELECT * FROM work_metadata_cache WHERE workId = :workId") suspend fun find(workId: ByteArray): WorkMetadataCacheEntity?
    suspend fun find(workId: String): WorkMetadataCacheEntity? = find(Utf16KeyConverter().encode(workId))
    @Query("DELETE FROM work_metadata_cache") suspend fun evictAll(): Int
}

@Dao
internal interface LegacyImportsDao {
    @Insert suspend fun insertRecords(values: List<LegacyImportRecordEntity>)
    @Insert suspend fun insertBatch(value: LegacyImportBatchEntity)
    @Query("SELECT * FROM legacy_import_batches WHERE importVersion = :version AND contentDigest = :digest")
    suspend fun batch(version: Int, digest: String): LegacyImportBatchEntity?
    @Query("SELECT * FROM legacy_import_records WHERE importVersion = :version AND contentDigest = :digest ORDER BY storeKind, entryKey")
    suspend fun records(version: Int, digest: String): List<LegacyImportRecordEntity>
    @Query("SELECT COUNT(*) FROM legacy_import_batches") suspend fun batchCount(): Int
    @Query("SELECT COUNT(*) FROM legacy_import_records") suspend fun recordCount(): Int
}
