package com.ichichuang.cinema.data.local.dao

import androidx.room.*
import com.ichichuang.cinema.data.local.entity.*

@Dao
internal interface CatalogIdentityDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun source(value: CatalogSourceEntity): Long
    @Query("SELECT * FROM catalog_sources WHERE sourceId=:source") suspend fun source(source: ByteArray): CatalogSourceEntity?
    @Query("UPDATE catalog_sources SET available=0 WHERE sourceId=:source") suspend fun unavailable(source: ByteArray)
    @Insert suspend fun endpoint(value: CatalogEndpointEntity)
    @Query("SELECT * FROM catalog_endpoints WHERE endpoint=:endpoint") suspend fun endpoint(endpoint: ByteArray): CatalogEndpointEntity?
    @Insert suspend fun item(value: CatalogItemEntity)
    @Query("SELECT * FROM catalog_items WHERE sourceId=:source AND itemId=:item") suspend fun item(source: ByteArray, item: ByteArray): CatalogItemEntity?
    @Query("SELECT * FROM catalog_items WHERE groupWorkId=:work") suspend fun group(work: ByteArray): List<CatalogItemEntity>
    @Query("UPDATE catalog_items SET groupWorkId=:work WHERE sourceId=:source AND itemId=:item") suspend fun regroup(source: ByteArray, item: ByteArray, work: ByteArray)
    @Insert suspend fun capture(value: CatalogCaptureEntity)
    @Query("SELECT * FROM catalog_captures WHERE captureId=:capture") suspend fun capture(capture: ByteArray): CatalogCaptureEntity?
    @Query("SELECT * FROM catalog_captures WHERE sourceId=:source AND itemId=:item ORDER BY captureId") suspend fun captures(source: ByteArray, item: ByteArray): List<CatalogCaptureEntity>
    @Query("SELECT * FROM catalog_captures WHERE sourceId=:source AND itemId IS NULL ORDER BY captureId") suspend fun pending(source: ByteArray): List<CatalogCaptureEntity>
    @Insert suspend fun entry(value: CatalogEntryEntity)
    @Query("SELECT * FROM catalog_entries WHERE entryId=:entry") suspend fun entry(entry: ByteArray): CatalogEntryEntity?
    @Query("SELECT * FROM catalog_entries WHERE sourceId=:source AND itemId=:item ORDER BY entryId") suspend fun entries(source: ByteArray, item: ByteArray): List<CatalogEntryEntity>
    @Insert suspend fun route(value: CatalogRouteEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun identifier(value: CatalogVerifiedIdentifierEntity): Long
    @Query("SELECT * FROM catalog_verified_identifiers WHERE sourceId=:source AND itemId=:item AND revoked=0") suspend fun identifiers(source: ByteArray, item: ByteArray): List<CatalogVerifiedIdentifierEntity>
    @Query("SELECT * FROM catalog_verified_identifiers WHERE proofId=:proof") suspend fun proof(proof: ByteArray): List<CatalogVerifiedIdentifierEntity>
    @Query("UPDATE catalog_verified_identifiers SET revoked=1 WHERE proofId=:proof") suspend fun revoke(proof: ByteArray)
    @Insert suspend fun history(value: CatalogAssociationHistoryEntity)
}
