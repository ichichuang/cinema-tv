package com.ichichuang.cinema.data.local

import com.ichichuang.cinema.data.local.migration.QUERY_SUBSCRIPTION_MIGRATION_4_5
import com.ichichuang.cinema.data.local.migration.CATALOG_MIGRATION_3_4
import android.content.Context
import androidx.room.Room
import com.ichichuang.cinema.data.local.migration.PERSONAL_LIBRARY_MIGRATION_5_6
import com.ichichuang.cinema.data.local.migration.PLAYBACK_MIGRATION_1_2
import com.ichichuang.cinema.data.local.migration.HANDOFF_MIGRATION_2_3

/** Explicit candidate construction only; no singleton, legacy reads, or startup registration. */
internal object CinemaDatabaseFactory {
    fun open(context: Context, databaseName: String): CinemaDatabase {
        require(databaseName.matches(Regex("[A-Za-z0-9_.-]+")) && databaseName != "." && databaseName != "..") {
            "A private candidate database name is required"
        }
        return Room.databaseBuilder(context.applicationContext, CinemaDatabase::class.java, databaseName)
            .addMigrations(PLAYBACK_MIGRATION_1_2, HANDOFF_MIGRATION_2_3, CATALOG_MIGRATION_3_4, QUERY_SUBSCRIPTION_MIGRATION_4_5, PERSONAL_LIBRARY_MIGRATION_5_6).build()
    }
}
