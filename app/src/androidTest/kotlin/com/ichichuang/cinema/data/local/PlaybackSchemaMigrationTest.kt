package com.ichichuang.cinema.data.local

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.data.local.codec.PlaybackSnapshotCodec
import com.ichichuang.cinema.data.local.codec.Utf16KeyConverter
import com.ichichuang.cinema.data.userdata.*
import com.ichichuang.cinema.domain.model.catalog.*
import com.ichichuang.cinema.domain.model.userdata.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlaybackSchemaMigrationTest : NativeStorageFixture() {
    private fun rows(tables: List<String>, query: (String) -> Cursor): Map<String, List<List<String>>> = tables.associateWith { table ->
        query("SELECT * FROM `$table`").use { c ->
            buildList { while (c.moveToNext()) add((0 until c.columnCount).map { column ->
                when (c.getType(column)) {
                    Cursor.FIELD_TYPE_NULL -> "null"
                    Cursor.FIELD_TYPE_BLOB -> "blob:" + c.getBlob(column).joinToString("") { "%02x".format(it) }
                    Cursor.FIELD_TYPE_INTEGER -> "int:" + c.getLong(column)
                    Cursor.FIELD_TYPE_FLOAT -> "float:" + c.getDouble(column)
                    else -> "text:" + c.getString(column)
                }
            }) }
        }
    }

    @Test fun original_schema_one_migrates_preserving_every_old_field_and_row() = migrateSchema(1)
    @Test fun schema_two_migrates_preserving_user_rows_sessions_and_resume() = migrateSchema(2)

    private fun migrateSchema(version: Int) = runBlocking<Unit> {
        database.close(); context.deleteDatabase(databaseName)
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open(
            "com.ichichuang.cinema.data.local.CinemaDatabase/$version.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        assertEquals(version, schema.getInt("version"))
        val entities = schema.getJSONArray("entities")
        val tables = (0 until entities.length()).map { entities.getJSONObject(it).getString("tableName") }
        val target = PlaybackTarget(WorkId("\uD800"), EpisodeId("episode"), VariantId("version"))
        val original = PlaybackSnapshot(target.workId, target.episodeId, target.variantId, "old-opaque", 99,
            600_000, null, WatchState.IN_PROGRESS, -123)
        val key = Utf16KeyConverter().encode(target.workId.value)
        val before = withContext(Dispatchers.IO) {
            SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null).use { sql ->
                sql.execSQL("PRAGMA foreign_keys=ON")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    sql.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    val indices = entity.optJSONArray("indices")
                    if (indices != null) for (j in 0 until indices.length()) sql.execSQL(
                        indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) sql.execSQL(setup.getString(i))
                sql.execSQL("INSERT INTO work_references VALUES (?)", arrayOf<Any>(key))
                sql.execSQL("INSERT INTO favorites VALUES (?)", arrayOf<Any>(key))
                sql.execSQL("INSERT INTO work_metadata_cache VALUES (?,?)", arrayOf<Any>(key, "raw-cache"))
                sql.execSQL("INSERT INTO browse_snapshots VALUES (?,?)", arrayOf<Any>(Utf16KeyConverter().encode("library"), "raw-browse"))
                sql.execSQL("INSERT INTO playback_snapshots VALUES (?,?,?,?)", arrayOf<Any>(key,
                    Utf16KeyConverter().encode("episode"), Utf16KeyConverter().encode("version"), PlaybackSnapshotCodec.encode(original)))
                sql.execSQL("INSERT INTO legacy_import_batches VALUES (1,'digest',-9,1,0,1,0)")
                sql.execSQL("INSERT INTO legacy_import_records VALUES (1,'digest','progress',?,'string','0,8,90000,0','unresolved','missing_identity',0,8,90000,NULL)",
                    arrayOf<Any>(Utf16KeyConverter().encode("wp_opaque")))
                if (version == 2) {
                    val session = Utf16KeyConverter().encode(original.sessionId)
                    sql.execSQL("INSERT INTO playback_sessions (generation,sessionId,workId,episodeKey,variantKey,sequence,payload) VALUES (1,?,?,?,?,?,?)",
                        arrayOf<Any>(session, key, Utf16KeyConverter().encode("episode"), Utf16KeyConverter().encode("version"), 99, PlaybackSnapshotCodec.encode(original)))
                    sql.execSQL("INSERT INTO playback_record_order VALUES (?,?,?,1)", arrayOf<Any>(key, Utf16KeyConverter().encode("episode"), Utf16KeyConverter().encode("version")))
                    sql.execSQL("INSERT INTO playback_resume VALUES (?,1,?)", arrayOf<Any>(key, PlaybackSnapshotCodec.encode(original)))
                }
                sql.version = version
                rows(tables) { sql.rawQuery(it, null) }
            }
        }
        database = CinemaDatabaseFactory.open(context, databaseName)
        assertEquals(1, database.userRecords().favoriteCount())
        withContext(Dispatchers.IO) {
            val sql = database.openHelper.writableDatabase
            assertEquals(6, sql.version)
            assertEquals(before, rows(tables) { sql.query(it) })
        }
        if (version == 1) assertNull(RoomPlaybackProgressStore(database).loadResume(target.workId))
        else assertEquals(original, RoomPlaybackProgressStore(database).loadResume(target.workId))
        assertNull(database.legacyHandoff().state())
        val store = RoomPlaybackProgressStore(database)
        assertEquals(CommitResult.Committed, store.beginSession(target, "new-session"))
        assertEquals(CommitResult.Committed, store.commit(original.copy(sessionId = "new-session", operationSequence = 0, positionMs = 0)))
        reopen()
        assertEquals(0L, RoomPlaybackProgressStore(database).loadResume(target.workId)!!.positionMs)
        assertEquals(1, database.legacyImports().recordCount())
    }
}
