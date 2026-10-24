package com.ichichuang.cinema.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive only: the seven original tables and their exact values are retained. */
internal val PLAYBACK_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `playback_sessions` (`generation` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` BLOB NOT NULL, `workId` BLOB NOT NULL, `episodeKey` BLOB NOT NULL, `variantKey` BLOB NOT NULL, `sequence` INTEGER NOT NULL, `payload` TEXT, FOREIGN KEY(`workId`) REFERENCES `work_references`(`workId`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_playback_sessions_sessionId` ON `playback_sessions` (`sessionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_sessions_workId` ON `playback_sessions` (`workId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `playback_record_order` (`workId` BLOB NOT NULL, `episodeKey` BLOB NOT NULL, `variantKey` BLOB NOT NULL, `generation` INTEGER NOT NULL, PRIMARY KEY(`workId`, `episodeKey`, `variantKey`), FOREIGN KEY(`workId`) REFERENCES `work_references`(`workId`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`generation`) REFERENCES `playback_sessions`(`generation`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_record_order_generation` ON `playback_record_order` (`generation`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `playback_resume` (`workId` BLOB NOT NULL, `generation` INTEGER NOT NULL, `payload` TEXT NOT NULL, PRIMARY KEY(`workId`), FOREIGN KEY(`workId`) REFERENCES `work_references`(`workId`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`generation`) REFERENCES `playback_sessions`(`generation`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_resume_generation` ON `playback_resume` (`generation`)")
    }
}
