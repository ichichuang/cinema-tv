package com.ichichuang.cinema.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val HANDOFF_MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `legacy_handoff_state` (`id` INTEGER NOT NULL, `phase` TEXT NOT NULL, `importVersion` INTEGER NOT NULL, `contentDigest` TEXT NOT NULL, `mappingDigest` TEXT NOT NULL, `totalCount` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`importVersion`, `contentDigest`) REFERENCES `legacy_import_batches`(`importVersion`, `contentDigest`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_legacy_handoff_state_importVersion_contentDigest` ON `legacy_handoff_state` (`importVersion`, `contentDigest`)")
    }
}
