package com.ichichuang.cinema.data.local.entity

import androidx.room.*

/** One explicit disposable profile per database; the activation record references preserved data. */
@Entity(tableName = "legacy_handoff_state", foreignKeys = [ForeignKey(
    entity = LegacyImportBatchEntity::class, parentColumns = ["importVersion", "contentDigest"],
    childColumns = ["importVersion", "contentDigest"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["importVersion", "contentDigest"])])
internal data class LegacyHandoffEntity(@PrimaryKey val id: Int = 1, val phase: String,
    val importVersion: Int = 1, val contentDigest: String, val mappingDigest: String, val totalCount: Int)
