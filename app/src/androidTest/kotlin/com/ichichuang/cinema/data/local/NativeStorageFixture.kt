package com.ichichuang.cinema.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ichichuang.cinema.data.local.migration.*
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Before
import java.io.File
import java.util.UUID

/** Exclusively owned synthetic files; never resolves the application's legacy store delegates. */
abstract class NativeStorageFixture {
    internal lateinit var context: Context
    internal lateinit var database: CinemaDatabase
    internal lateinit var databaseName: String
    internal lateinit var fixtureDirectory: File
    internal lateinit var fixtureScope: CoroutineScope
    internal lateinit var stores: Map<LegacyStoreKind, androidx.datastore.core.DataStore<Preferences>>
    internal lateinit var sourceFiles: Map<LegacyStoreKind, File>

    @Before fun createSyntheticFiles() {
        context = ApplicationProvider.getApplicationContext()
        check(context.packageName == "com.ichichuang.cinema.debug")
        databaseName = "synthetic-round02-${UUID.randomUUID()}.db"
        database = CinemaDatabaseFactory.open(context, databaseName)
        fixtureDirectory = File(context.filesDir, "synthetic-round02-${UUID.randomUUID()}").apply { mkdirs() }
        fixtureScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        sourceFiles = listOf(LegacyStoreKind.PROGRESS, LegacyStoreKind.BROWSE, LegacyStoreKind.FILTERS).associateWith { File(fixtureDirectory, "cinema_${it.tag}.preferences_pb") }
        stores = sourceFiles.mapValues { (_, file) -> PreferenceDataStoreFactory.create(
            scope = fixtureScope, produceFile = { file }) }
    }

    internal fun reopen() {
        database.close()
        database = CinemaDatabaseFactory.open(context, databaseName)
    }

    internal suspend fun seedLegacy(): LegacySnapshot {
        stores.getValue(LegacyStoreKind.PROGRESS).edit {
            it[stringPreferencesKey("wp_7")] = "0,9001,90000,0"
            it[stringPreferencesKey("wp_https://source-a.synthetic.invalid/api::7")] = "1,8,1001,1000"
            it[stringPreferencesKey("wp_https://source-b.synthetic.invalid/api::7")] = "2,8,0,0"
            it[stringPreferencesKey("wp_malformed")] = "0,1,20,unknown"
            it[longPreferencesKey("unrecognized-long")] = Long.MIN_VALUE
        }
        stores.getValue(LegacyStoreKind.BROWSE).edit {
            it[stringPreferencesKey("last_movie_id")] = "https://source-a.synthetic.invalid/api::7"
            it[intPreferencesKey("last_index")] = 9001
        }
        stores.getValue(LegacyStoreKind.FILTERS).edit {
            it[stringPreferencesKey("f_category")] = "电影"
            it[stringPreferencesKey("f_lang")] = ""
            it[stringPreferencesKey("f_area")] = "未知原值"
            it[stringPreferencesKey("f_year")] = "1994"
            it[stringPreferencesKey("f_types")] = "动作片\n\n剧情片"
        }
        return LegacySnapshotReader(stores).capture(-123)
    }

    @After fun closeSyntheticFiles() = runBlocking {
        database.close()
        fixtureScope.coroutineContext.job.cancelAndJoin()
        context.deleteDatabase(databaseName)
        fixtureDirectory.deleteRecursively()
        Unit
    }
}
