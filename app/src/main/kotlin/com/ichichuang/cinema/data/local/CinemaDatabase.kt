package com.ichichuang.cinema.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ichichuang.cinema.data.local.codec.Utf16KeyConverter
import com.ichichuang.cinema.data.local.dao.*
import com.ichichuang.cinema.data.local.entity.*

@Database(entities = [WorkReferenceEntity::class, WorkMetadataCacheEntity::class, FavoriteEntity::class,
    BrowseSnapshotEntity::class, PlaybackSnapshotEntity::class, LegacyImportBatchEntity::class, LegacyImportRecordEntity::class,
    PlaybackSessionEntity::class, PlaybackRecordOrderEntity::class, PlaybackResumeEntity::class, LegacyHandoffEntity::class, CatalogSourceEntity::class, CatalogEndpointEntity::class, CatalogItemEntity::class,
        CatalogCaptureEntity::class, CatalogEntryEntity::class, CatalogRouteEntity::class,
        CatalogVerifiedIdentifierEntity::class, CatalogAssociationHistoryEntity::class],
    version = 4, exportSchema = true)
@TypeConverters(Utf16KeyConverter::class)
internal abstract class CinemaDatabase : RoomDatabase() {
    abstract fun catalogIdentity(): CatalogIdentityDao
    abstract fun userRecords(): UserRecordsDao
    abstract fun browseSnapshots(): BrowseSnapshotsDao
    abstract fun playbackSnapshots(): PlaybackSnapshotsDao
    abstract fun metadataCache(): MetadataCacheDao
    abstract fun legacyImports(): LegacyImportsDao
    abstract fun playbackSessions(): PlaybackSessionsDao
    abstract fun legacyHandoff(): LegacyHandoffDao
}
