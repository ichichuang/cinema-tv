package com.ichichuang.cinema.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive durable actual-play evidence and scoped user intent. No old row is interpreted or changed. */
internal val PERSONAL_LIBRARY_MIGRATION_5_6=object:Migration(5,6) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `personal_library_state` (`workId` BLOB NOT NULL, `hiddenThroughGeneration` INTEGER NOT NULL, `deletedThroughGeneration` INTEGER NOT NULL, `favoriteOrder` INTEGER NOT NULL, `favoriteRevision` INTEGER NOT NULL, `undoneRevision` INTEGER NOT NULL, PRIMARY KEY(`workId`), FOREIGN KEY(`workId`) REFERENCES `work_references`(`workId`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        db.execSQL("CREATE TABLE IF NOT EXISTS `confirmed_plays` (`eventOrder` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionGeneration` INTEGER NOT NULL, `workId` BLOB NOT NULL, FOREIGN KEY(`workId`) REFERENCES `work_references`(`workId`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`sessionGeneration`) REFERENCES `playback_sessions`(`generation`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_confirmed_plays_sessionGeneration` ON `confirmed_plays` (`sessionGeneration`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_confirmed_plays_workId` ON `confirmed_plays` (`workId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `personal_library_cards` (`workId` BLOB NOT NULL, `title` TEXT NOT NULL, `sourceLabel` TEXT, PRIMARY KEY(`workId`), FOREIGN KEY(`workId`) REFERENCES `work_references`(`workId`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
    }
}
