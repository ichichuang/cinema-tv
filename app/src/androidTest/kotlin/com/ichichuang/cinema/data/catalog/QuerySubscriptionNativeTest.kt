package com.ichichuang.cinema.data.catalog

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.data.local.*
import com.ichichuang.cinema.domain.model.catalog.SourceId
import kotlinx.serialization.json.*
import com.ichichuang.cinema.data.local.codec.Utf16KeyConverter
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Real private Room files; all records are synthetic. No runtime catalog or user profile. */
class QuerySubscriptionNativeTest : NativeStorageFixture() {
    private val cv = Utf16KeyConverter()
    private fun oldSchema() = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
        .open("com.ichichuang.cinema.data.local.CinemaDatabase/4.json").bufferedReader().use { it.readText() })
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
    @Test fun schema_four_to_five_preserves_all_nineteen_seeded_tables_and_reopens() = runBlocking<Unit> {
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
                sql.version=4
                val w=cv.encode("synthetic-old-work");val e=cv.encode("synthetic-old-episode")
                val v=cv.encode("synthetic-old-variant");val s=cv.encode("synthetic-old-source")
                val item=cv.encode("17");val capture=cv.encode("synthetic-old-capture");val entry=cv.encode("synthetic-old-entry")
                sql.execSQL("INSERT INTO work_references VALUES (?)",arrayOf<Any>(w))
                sql.execSQL("INSERT INTO work_metadata_cache VALUES (?,?)",arrayOf<Any>(w,"old-exact-cache"))
                sql.execSQL("INSERT INTO favorites VALUES (?)",arrayOf<Any>(w))
                sql.execSQL("INSERT INTO browse_snapshots VALUES (?,?)",arrayOf<Any>(cv.encode("old-context"),"old-exact-browse"))
                sql.execSQL("INSERT INTO playback_snapshots VALUES (?,?,?,?)",arrayOf<Any>(w,e,v,"old-exact-checkpoint"))
                sql.execSQL("INSERT INTO legacy_import_batches VALUES (1,'synthetic-old-digest',-123,1,0,1,0)")
                sql.execSQL("INSERT INTO legacy_import_records VALUES (1,'synthetic-old-digest','progress',?,'string','0,1,2000,0','unresolved','missing_identity',0,1,2000,NULL)",arrayOf<Any>(cv.encode("wp_old")))
                sql.execSQL("INSERT INTO playback_sessions VALUES (1,?,?,?,?,?,?)",arrayOf<Any>(cv.encode("old-session"),w,e,v,7,"old-session-payload"))
                sql.execSQL("INSERT INTO playback_record_order VALUES (?,?,?,1)",arrayOf<Any>(w,e,v))
                sql.execSQL("INSERT INTO playback_resume VALUES (?,1,?)",arrayOf<Any>(w,"old-resume-payload"))
                sql.execSQL("INSERT INTO legacy_handoff_state VALUES (1,'verified',1,'synthetic-old-digest','old-map',1)")
                sql.execSQL("INSERT INTO catalog_sources VALUES (?,0)",arrayOf<Any>(s))
                sql.execSQL("INSERT INTO catalog_endpoints VALUES (?,?,NULL)",arrayOf<Any>(cv.encode("fixture:old-endpoint"),s))
                sql.execSQL("INSERT INTO catalog_items VALUES (?,?,?,?)",arrayOf<Any>(s,item,w,w))
                sql.execSQL("INSERT INTO catalog_captures VALUES (?,?,?,?,?)",arrayOf<Any>(capture,s,item,"old-capture-payload","old-entries-payload"))
                sql.execSQL("INSERT INTO catalog_entries VALUES (?,?,?,?,?)",arrayOf<Any>(entry,s,item,e,v))
                sql.execSQL("INSERT INTO catalog_routes VALUES (?,?,?)",arrayOf<Any>(capture,entry,"old-route-payload"))
                sql.execSQL("INSERT INTO catalog_verified_identifiers VALUES (?,?,?,?,?,1)",arrayOf<Any>(s,item,cv.encode("old-namespace"),cv.encode("old-id"),cv.encode("old-proof")))
                sql.execSQL("INSERT INTO catalog_association_history VALUES (1,?,?,?,?,?,?,?,?)",arrayOf<Any>(s,item,w,w,"split",cv.encode("old-proof"),123L,"old-history"))
                rows(tables) { sql.rawQuery(it,null) }
            }
        }
        assertEquals(19,before.size);assertTrue(before.values.all { it.size==1 })
        database=CinemaDatabaseFactory.open(context,databaseName)
        withContext(Dispatchers.IO) {
            val sql=database.openHelper.writableDatabase
            sql.query("PRAGMA user_version").use { assertTrue(it.moveToFirst());assertEquals(6,it.getInt(0)) }
            assertEquals(before,rows(tables) { sql.query(it) })
            sql.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
        reopen()
        withContext(Dispatchers.IO) { assertEquals(before,rows(tables) { database.openHelper.writableDatabase.query(it) }) }
    }
    @Test fun capability_cache_reopens_without_turning_timeout_into_unsupported_or_renewing_expiry() = runBlocking<Unit> {
        val source=SourceId("synthetic-capability")
        val context=SourceCapabilityContext(source,"fixture:capability","adapter","protocol","local-auth")
        val capture=MaccmsCatalogNormalizer.normalize(source,buildJsonObject {
            put("vod_id","17");put("vod_year","1994")
        },100)
        val cache=SourceCapabilityCache.open(2,RoomSourceCapabilityPersistence(database))
        val observed=SourceCapabilityProbe.observe(context,100,100,listOf(capture),requestedYear=1994)
        cache.record(observed)
        cache.record(SourceCapabilityProbe.observe(context,150,100,emptyList(),health=ProbeHealth.TIMEOUT))
        reopen()
        val restored=SourceCapabilityCache.open(2,RoomSourceCapabilityPersistence(database))
        val state=restored.lookup(context,175)
        assertNotNull(state);assertEquals(CapabilityFreshness.FRESH,state!!.freshness)
        assertEquals(ProbeHealth.TIMEOUT,state.record.latest.health)
        assertEquals(observed,state.record.lastSuccessful)
        assertEquals(CapabilityFreshness.EXPIRED,restored.lookup(context,201)!!.freshness)
        restored.record(SourceCapabilityProbe.observe(context,250,100,emptyList(),health=ProbeHealth.TIMEOUT))
        reopen()
        val rolledBack=SourceCapabilityCache.open(2,RoomSourceCapabilityPersistence(database))
        assertEquals(CapabilityFreshness.CLOCK_ROLLBACK,rolledBack.lookup(context,150)!!.freshness)
        assertEquals(CapabilityFreshness.EXPIRED,rolledBack.lookup(context,275)!!.freshness)
        assertEquals(200L,rolledBack.lookup(context,275)!!.record.lastSuccessful!!.expiresAt)
    }

    @Test fun oversized_capability_snapshot_fails_closed_without_replacing_original_row() = runBlocking<Unit> {
        val payload=SourceCapabilityCacheCodec.encode(emptyList())+" ".repeat(1_048_577)
        database.querySubscription().capability(com.ichichuang.cinema.data.local.entity.CandidateCapabilityStateEntity(payload=payload))
        reopen()
        try {
            SourceCapabilityCache.open(2,RoomSourceCapabilityPersistence(database));fail("oversized snapshot accepted")
        } catch (error: com.ichichuang.cinema.data.local.codec.StorageReadException) {
            assertEquals(com.ichichuang.cinema.data.local.codec.StorageReadFailure.MALFORMED,error.reason)
        }
        assertEquals(payload,database.querySubscription().capability()!!.payload)
    }

}
