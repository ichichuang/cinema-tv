package com.ichichuang.cinema.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive candidate catalog only. Original user tables and authority are unchanged. */
internal val CATALOG_MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `catalog_sources` (`sourceId` BLOB NOT NULL, `available` INTEGER NOT NULL, PRIMARY KEY(`sourceId`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `catalog_endpoints` (`endpoint` BLOB NOT NULL, `sourceId` BLOB NOT NULL, `proofId` BLOB, PRIMARY KEY(`endpoint`), FOREIGN KEY(`sourceId`) REFERENCES `catalog_sources`(`sourceId`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_catalog_endpoints_sourceId` ON `catalog_endpoints` (`sourceId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `catalog_items` (`sourceId` BLOB NOT NULL, `itemId` BLOB NOT NULL, `homeWorkId` BLOB NOT NULL, `groupWorkId` BLOB NOT NULL, PRIMARY KEY(`sourceId`, `itemId`), FOREIGN KEY(`sourceId`) REFERENCES `catalog_sources`(`sourceId`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`homeWorkId`) REFERENCES `work_references`(`workId`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`groupWorkId`) REFERENCES `work_references`(`workId`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_catalog_items_homeWorkId` ON `catalog_items` (`homeWorkId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_catalog_items_groupWorkId` ON `catalog_items` (`groupWorkId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `catalog_captures` (`captureId` BLOB NOT NULL, `sourceId` BLOB NOT NULL, `itemId` BLOB, `payload` TEXT NOT NULL, `entriesPayload` TEXT NOT NULL, PRIMARY KEY(`captureId`), FOREIGN KEY(`sourceId`) REFERENCES `catalog_sources`(`sourceId`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`sourceId`, `itemId`) REFERENCES `catalog_items`(`sourceId`, `itemId`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_catalog_captures_sourceId_itemId` ON `catalog_captures` (`sourceId`, `itemId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `catalog_entries` (`entryId` BLOB NOT NULL, `sourceId` BLOB NOT NULL, `itemId` BLOB NOT NULL, `episodeId` BLOB NOT NULL, `variantId` BLOB NOT NULL, PRIMARY KEY(`entryId`), FOREIGN KEY(`sourceId`, `itemId`) REFERENCES `catalog_items`(`sourceId`, `itemId`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_catalog_entries_sourceId_itemId` ON `catalog_entries` (`sourceId`, `itemId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `catalog_routes` (`captureId` BLOB NOT NULL, `entryId` BLOB NOT NULL, `payload` TEXT NOT NULL, PRIMARY KEY(`captureId`, `entryId`), FOREIGN KEY(`captureId`) REFERENCES `catalog_captures`(`captureId`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`entryId`) REFERENCES `catalog_entries`(`entryId`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_catalog_routes_entryId` ON `catalog_routes` (`entryId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `catalog_verified_identifiers` (`sourceId` BLOB NOT NULL, `itemId` BLOB NOT NULL, `namespace` BLOB NOT NULL, `identifier` BLOB NOT NULL, `proofId` BLOB NOT NULL, `revoked` INTEGER NOT NULL, PRIMARY KEY(`sourceId`, `itemId`, `namespace`, `identifier`, `proofId`), FOREIGN KEY(`sourceId`, `itemId`) REFERENCES `catalog_items`(`sourceId`, `itemId`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `catalog_association_history` (`eventId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sourceId` BLOB NOT NULL, `itemId` BLOB NOT NULL, `fromWorkId` BLOB NOT NULL, `toWorkId` BLOB NOT NULL, `action` TEXT NOT NULL, `proofId` BLOB NOT NULL, `occurredAt` INTEGER NOT NULL, `detail` TEXT NOT NULL, FOREIGN KEY(`sourceId`, `itemId`) REFERENCES `catalog_items`(`sourceId`, `itemId`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`fromWorkId`) REFERENCES `work_references`(`workId`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`toWorkId`) REFERENCES `work_references`(`workId`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_catalog_association_history_sourceId_itemId` ON `catalog_association_history` (`sourceId`, `itemId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_catalog_association_history_fromWorkId` ON `catalog_association_history` (`fromWorkId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_catalog_association_history_toWorkId` ON `catalog_association_history` (`toWorkId`)")
    }
}
