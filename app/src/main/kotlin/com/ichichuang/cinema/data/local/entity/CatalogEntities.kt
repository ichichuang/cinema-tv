package com.ichichuang.cinema.data.local.entity

import androidx.room.*

@Entity(tableName = "catalog_sources", primaryKeys = ["sourceId"])
internal data class CatalogSourceEntity(
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val sourceId: String,
    val available: Boolean
)

@Entity(tableName = "catalog_endpoints", primaryKeys = ["endpoint"], foreignKeys = [ForeignKey(entity = CatalogSourceEntity::class, parentColumns = ["sourceId"], childColumns = ["sourceId"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["sourceId"], unique = false)])
internal data class CatalogEndpointEntity(
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val endpoint: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val sourceId: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val proofId: String?
)

@Entity(tableName = "catalog_items", primaryKeys = ["sourceId", "itemId"], foreignKeys = [ForeignKey(entity = CatalogSourceEntity::class, parentColumns = ["sourceId"], childColumns = ["sourceId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = WorkReferenceEntity::class, parentColumns = ["workId"], childColumns = ["homeWorkId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = WorkReferenceEntity::class, parentColumns = ["workId"], childColumns = ["groupWorkId"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["homeWorkId"], unique = true), Index(value = ["groupWorkId"], unique = false)])
internal data class CatalogItemEntity(
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val sourceId: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val itemId: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val homeWorkId: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val groupWorkId: String
)

@Entity(tableName = "catalog_captures", primaryKeys = ["captureId"], foreignKeys = [ForeignKey(entity = CatalogSourceEntity::class, parentColumns = ["sourceId"], childColumns = ["sourceId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = CatalogItemEntity::class, parentColumns = ["sourceId", "itemId"], childColumns = ["sourceId", "itemId"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["sourceId", "itemId"], unique = false)])
internal data class CatalogCaptureEntity(
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val captureId: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val sourceId: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val itemId: String?,
    val payload: String,
    val entriesPayload: String
)

@Entity(tableName = "catalog_entries", primaryKeys = ["entryId"], foreignKeys = [ForeignKey(entity = CatalogItemEntity::class, parentColumns = ["sourceId", "itemId"], childColumns = ["sourceId", "itemId"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["sourceId", "itemId"], unique = false)])
internal data class CatalogEntryEntity(
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val entryId: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val sourceId: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val itemId: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val episodeId: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val variantId: String
)

@Entity(tableName = "catalog_routes", primaryKeys = ["captureId", "entryId"], foreignKeys = [ForeignKey(entity = CatalogCaptureEntity::class, parentColumns = ["captureId"], childColumns = ["captureId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = CatalogEntryEntity::class, parentColumns = ["entryId"], childColumns = ["entryId"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["entryId"], unique = false)])
internal data class CatalogRouteEntity(
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val captureId: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val entryId: String,
    val payload: String
)

@Entity(tableName = "catalog_verified_identifiers", primaryKeys = ["sourceId", "itemId", "namespace", "identifier", "proofId"], foreignKeys = [ForeignKey(entity = CatalogItemEntity::class, parentColumns = ["sourceId", "itemId"], childColumns = ["sourceId", "itemId"], onDelete = ForeignKey.RESTRICT)])
internal data class CatalogVerifiedIdentifierEntity(
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val sourceId: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val itemId: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val namespace: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val identifier: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val proofId: String,
    val revoked: Boolean
)

@Entity(tableName = "catalog_association_history", foreignKeys = [ForeignKey(entity = CatalogItemEntity::class, parentColumns = ["sourceId", "itemId"], childColumns = ["sourceId", "itemId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = WorkReferenceEntity::class, parentColumns = ["workId"], childColumns = ["fromWorkId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = WorkReferenceEntity::class, parentColumns = ["workId"], childColumns = ["toWorkId"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["sourceId", "itemId"], unique = false), Index(value = ["fromWorkId"], unique = false), Index(value = ["toWorkId"], unique = false)])
internal data class CatalogAssociationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val eventId: Long = 0,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val sourceId: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val itemId: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val fromWorkId: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val toWorkId: String,
    val action: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val proofId: String,
    val occurredAt: Long,
    val detail: String
)
