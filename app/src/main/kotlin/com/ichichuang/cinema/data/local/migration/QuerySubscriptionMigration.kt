package com.ichichuang.cinema.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive candidate-only snapshots. No prior row, authority or evidence is rewritten. */
internal val QUERY_SUBSCRIPTION_MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `candidate_capability_state` (`singleton` INTEGER NOT NULL, `payload` TEXT NOT NULL, PRIMARY KEY(`singleton`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `candidate_query_sessions` (`queryId` BLOB NOT NULL, `payload` TEXT NOT NULL, PRIMARY KEY(`queryId`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `candidate_subscription_state` (`catalogId` BLOB NOT NULL, `payload` TEXT NOT NULL, PRIMARY KEY(`catalogId`))")
    }
}
