package com.ichichuang.cinema.data.userdata

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.data.local.*
import com.ichichuang.cinema.domain.model.catalog.*
import com.ichichuang.cinema.domain.model.userdata.*
import com.ichichuang.cinema.data.local.codec.*
import com.ichichuang.cinema.data.catalog.*
import kotlinx.serialization.json.*
import com.ichichuang.cinema.data.local.codec.Utf16KeyConverter
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Real private Room files; all records are synthetic. No runtime catalog or user profile. */
class PersonalLibraryMigrationNativeTest : NativeStorageFixture() {
    private val cv = Utf16KeyConverter()
    private fun oldSchema() = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
        .open("com.ichichuang.cinema.data.local.CinemaDatabase/5.json").bufferedReader().use { it.readText() })
        .getJSONObject("database")
    private fun rows(tables: List<String>, query: (String) -> Cursor) = tables.associateWith { table ->
        query("SELECT * FROM `$table`").use { cursor -> buildList {
            while(cursor.moveToNext()) add((0 until cursor.columnCount).map { column ->
                when(cursor.getType(column)) {
                    Cursor.FIELD_TYPE_NULL -> "NULL"
                    Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(column).joinToString("") { "%02x".format(it) }
                    else -> cursor.getString(column)
                }
            })
        }.sortedBy { it.toString() } }
    }
    @Test fun v5_to_v6_preserves_all_twenty_two_seeded_tables_and_reopens() = migrate(false)
    @Test fun failed_v5_to_v6_migration_rolls_back_version_all_rows_and_new_tables() = migrate(true)
    @Test fun v5_resume_projects_without_fabricating_confirmed_history() = migrate(false,true)
    private fun migrate(injectFailure:Boolean,verifyLegacyResume:Boolean=false) = runBlocking<Unit> {
        database.close();context.deleteDatabase(databaseName)
        val schema=oldSchema();val entities=schema.getJSONArray("entities")
        val tables=(0 until entities.length()).map { entities.getJSONObject(it).getString("tableName") }
        val before=withContext(Dispatchers.IO) {
            SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName),null).use { sql ->
                for(i in 0 until entities.length()) {
                    val entity=entities.getJSONObject(i);val table=entity.getString("tableName")
                    sql.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
                    val indices=entity.optJSONArray("indices")
                    for(j in 0 until (indices?.length() ?: 0)) sql.execSQL(indices!!.getJSONObject(j)
                        .getString("createSql").replace("\${TABLE_NAME}",table))
                }
                val setup=schema.getJSONArray("setupQueries")
                for(i in 0 until setup.length()) sql.execSQL(setup.getString(i))
                sql.version=5
                sql.execSQL("PRAGMA foreign_keys=ON")
                val target=PlaybackTarget(WorkId("synthetic-old-work"),EpisodeId("synthetic-old-episode"),VariantId("synthetic-old-variant"))
                val progress=PlaybackSnapshot(target.workId,target.episodeId,target.variantId,"old-session",7,9000,120000,WatchState.IN_PROGRESS,-123)
                val progressPayload=PlaybackSnapshotCodec.encode(progress)
                val catalogCapture=MaccmsCatalogNormalizer.normalize(SourceId("synthetic-old-source"),buildJsonObject {
                    put("vod_id","17");put("vod_name","迁移原始标题");put("vod_year","1994")
                    put("vod_play_from","测试版本");put("vod_play_url","第一集${'$'}fixture:local")
                },-123)
                val declared=DeclaredCatalogEntry("synthetic-old-entry",target.episodeId!!,target.variantId!!,0,0)
                val query=CatalogQuery.create("old-query",yearStart=1994,yearEnd=1994,enabledSourceIds=setOf(SourceId("synthetic-old-source")))
                val queryPayload=QuerySessionCodec.encode(StoredQuerySession(query,listOf(SourceCursorState(SourceId("synthetic-old-source"),"1",false)),emptyList(),0,0))
                val w=cv.encode("synthetic-old-work");val e=cv.encode("synthetic-old-episode")
                val v=cv.encode("synthetic-old-variant");val s=cv.encode("synthetic-old-source")
                val item=cv.encode("17");val capture=cv.encode("synthetic-old-capture");val entry=cv.encode("synthetic-old-entry")
                sql.execSQL("INSERT INTO work_references VALUES (?)",arrayOf<Any>(w))
                sql.execSQL("INSERT INTO work_metadata_cache VALUES (?,?)",arrayOf<Any>(w,CatalogItemCodec.encode(CatalogItem(target.workId,"迁移原始标题"))))
                sql.execSQL("INSERT INTO favorites VALUES (?)",arrayOf<Any>(w))
                for(context in listOf("library","favorites","history")) sql.execSQL("INSERT INTO browse_snapshots VALUES (?,?)",
                    arrayOf<Any>(cv.encode(context),BrowseSnapshotCodec.encode(BrowseSnapshot(context,context,null,query,7,target.workId,target.workId,-0.25f,-123))))
                sql.execSQL("INSERT INTO playback_snapshots VALUES (?,?,?,?)",arrayOf<Any>(w,e,v,progressPayload))
                val alternate=progress.copy(episodeId=EpisodeId("episode-8"),variantId=VariantId("alternate-cut"),positionMs=80000)
                sql.execSQL("INSERT INTO playback_snapshots VALUES (?,?,?,?)",arrayOf<Any>(w,cv.encode("episode-8"),cv.encode("alternate-cut"),PlaybackSnapshotCodec.encode(alternate)))
                sql.execSQL("INSERT INTO legacy_import_batches VALUES (1,'synthetic-old-digest',-123,1,0,1,0)")
                sql.execSQL("INSERT INTO legacy_import_records VALUES (1,'synthetic-old-digest','progress',?,'string','0,1,2000,0','unresolved','missing_identity',0,1,2000,NULL)",arrayOf<Any>(cv.encode("wp_old")))
                sql.execSQL("INSERT INTO playback_sessions VALUES (1,?,?,?,?,?,?)",arrayOf<Any>(cv.encode("old-session"),w,e,v,7,progressPayload))
                sql.execSQL("INSERT INTO playback_record_order VALUES (?,?,?,1)",arrayOf<Any>(w,e,v))
                sql.execSQL("INSERT INTO playback_resume VALUES (?,1,?)",arrayOf<Any>(w,progressPayload))
                sql.execSQL("INSERT INTO legacy_handoff_state VALUES (1,'verified',1,'synthetic-old-digest','old-map',1)")
                sql.execSQL("INSERT INTO catalog_sources VALUES (?,0)",arrayOf<Any>(s))
                sql.execSQL("INSERT INTO catalog_endpoints VALUES (?,?,NULL)",arrayOf<Any>(cv.encode("fixture:old-endpoint"),s))
                sql.execSQL("INSERT INTO catalog_items VALUES (?,?,?,?)",arrayOf<Any>(s,item,w,w))
                sql.execSQL("INSERT INTO catalog_captures VALUES (?,?,?,?,?)",arrayOf<Any>(capture,s,item,CatalogCaptureCodec.encode(catalogCapture),declaredEntriesJson(listOf(declared))))
                sql.execSQL("INSERT INTO catalog_entries VALUES (?,?,?,?,?)",arrayOf<Any>(entry,s,item,e,v))
                sql.execSQL("INSERT INTO catalog_routes VALUES (?,?,?)",arrayOf<Any>(capture,entry,MediaVariantCodec.encode(MediaVariant(target.variantId!!,target.workId,catalogCapture.sourceId,catalogCapture.itemId!!))))
                sql.execSQL("INSERT INTO catalog_verified_identifiers VALUES (?,?,?,?,?,1)",arrayOf<Any>(s,item,cv.encode("old-namespace"),cv.encode("old-id"),cv.encode("old-proof")))
                sql.execSQL("INSERT INTO catalog_association_history VALUES (1,?,?,?,?,?,?,?,?)",arrayOf<Any>(s,item,w,w,"split",cv.encode("old-proof"),123L,"old-history"))
                sql.execSQL("INSERT INTO candidate_capability_state VALUES (1,?)",arrayOf<Any>(SourceCapabilityCacheCodec.encode(emptyList())))
                sql.execSQL("INSERT INTO candidate_query_sessions VALUES (?,?)",arrayOf<Any>(cv.encode("old-query"),queryPayload))
                sql.execSQL("INSERT INTO candidate_subscription_state VALUES (?,?)",arrayOf<Any>(cv.encode("old-subscription"),com.ichichuang.cinema.data.subscription.CandidateSubscriptionStore.encode(
                    com.ichichuang.cinema.data.subscription.CandidateSubscriptionSnapshot(consentSequence=3,
                        configuration=com.ichichuang.cinema.data.subscription.CandidateConfiguration(7,"a".repeat(64),"fixture:catalog",100,
                            listOf(com.ichichuang.cinema.data.subscription.CandidateConfiguredSource(
                                com.ichichuang.cinema.data.subscription.DirectorySource(SourceId("synthetic-old-source"),"原始来源","fixture:old-endpoint"),false,false))),
                        disabled=setOf(SourceId("synthetic-old-source")),clockHighWater=100))))
                rows(tables) { sql.rawQuery(it,null) }
            }
        }
        assertEquals(22,before.size);assertTrue(before.values.all { it.isNotEmpty() })
        assertEquals(3,before.getValue("browse_snapshots").size);assertEquals(2,before.getValue("playback_snapshots").size)
        if(injectFailure) {
            val failing=androidx.room.Room.databaseBuilder(context,CinemaDatabase::class.java,databaseName)
                .addMigrations(object:androidx.room.migration.Migration(5,6) {
                    override fun migrate(sql:androidx.sqlite.db.SupportSQLiteDatabase) {
                        com.ichichuang.cinema.data.local.migration.PERSONAL_LIBRARY_MIGRATION_5_6.migrate(sql)
                        throw android.database.sqlite.SQLiteException("synthetic migration failure")
                    }
                }).build()
            try { withContext(Dispatchers.IO) { failing.openHelper.writableDatabase };fail("migration fault swallowed") }
            catch (_:android.database.sqlite.SQLiteException) { } finally { failing.close() }
            withContext(Dispatchers.IO) {
                SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName),null).use { sql ->
                    assertEquals(5,sql.version);assertEquals(before,rows(tables) { sql.rawQuery(it,null) })
                    sql.rawQuery("SELECT name FROM sqlite_master WHERE name='confirmed_plays'",null).use { assertFalse(it.moveToFirst()) }
                }
            }
        }
        database=CinemaDatabaseFactory.open(context,databaseName)
        withContext(Dispatchers.IO) {
            val sql=database.openHelper.writableDatabase
            sql.query("PRAGMA user_version").use { assertTrue(it.moveToFirst());assertEquals(6,it.getInt(0)) }
            assertEquals(before,rows(tables) { sql.query(it) })
            val exported=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
                .open("com.ichichuang.cinema.data.local.CinemaDatabase/6.json").bufferedReader().use { it.readText() })
                .getJSONObject("database").getString("identityHash")
            sql.query("SELECT identity_hash FROM room_master_table WHERE id=42").use { assertTrue(it.moveToFirst());assertEquals(exported,it.getString(0)) }
            sql.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
        if(verifyLegacyResume) {
            assertTrue(RoomPersonalLibraryRepository(database).recentHistory().isEmpty())
            val card=RoomPersonalLibraryRepository(database).continueWatching().single()
            assertEquals("synthetic-old-work",card.workId)
            assertEquals(EpisodeId("synthetic-old-episode"),card.snapshot!!.episodeId)
            assertEquals(9000L,card.snapshot!!.positionMs)
        }
        reopen()
        withContext(Dispatchers.IO) { assertEquals(before,rows(tables) { database.openHelper.writableDatabase.query(it) }) }
    }
}
