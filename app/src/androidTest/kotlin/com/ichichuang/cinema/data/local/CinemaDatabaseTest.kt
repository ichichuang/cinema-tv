package com.ichichuang.cinema.data.local

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichichuang.cinema.data.local.codec.*
import com.ichichuang.cinema.data.local.entity.*
import com.ichichuang.cinema.data.userdata.*
import com.ichichuang.cinema.domain.model.catalog.*
import com.ichichuang.cinema.domain.model.userdata.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CinemaDatabaseTest : NativeStorageFixture() {
    private fun browse(contextKey: String = "library", savedAt: Long = -1) = BrowseSnapshot(
        contextKey, "library", "home", CatalogQuery.create("q", yearStart = 1895, yearEnd = 1994),
        7, WorkId("work"), WorkId("anchor"), -0.5f, savedAt)
    private val playback = PlaybackSnapshot(WorkId("work"), null, VariantId("variant"),
        "session", Long.MAX_VALUE, 1001, 1000, WatchState.IN_PROGRESS, -123)

    private suspend fun seedUserData() {
        assertEquals(CommitResult.Committed, RoomBrowseStateStore(database).save(browse()))
        assertEquals(CommitResult.Committed, RoomFavoritesStore(database).setFavorite("work", true))
        database.withTransaction { database.playbackSnapshots().upsert(PlaybackSnapshotEntity.from(playback)) }
    }

    @Test fun file_backed_values_survive_close_and_reopen() = runBlocking {
        seedUserData()
        val metadata = CatalogItem(WorkId("work"), "合成资料", FieldEvidence.Confirmed(1895,
            EvidenceReference(SourceId("synthetic"), null, "1895", "rule-one")),
            FieldEvidence.Confirmed(FactIds.of(emptySet()), EvidenceReference(SourceId("synthetic"), null, "", "rule-one")))
        database.metadataCache().upsert(WorkMetadataCacheEntity("work", CatalogItemCodec.encode(metadata)))
        assertTrue(context.getDatabasePath(databaseName).isFile)
        reopen()
        assertEquals(browse(), RoomBrowseStateStore(database).load("library"))
        assertNotNull(database.userRecords().favorite("work"))
        assertEquals(playback, database.playbackSnapshots().find("work", "", "variant")!!.snapshot())
        assertEquals(metadata, CatalogItemCodec.decode(database.metadataCache().find("work")!!.payload))
    }

    @Test fun browse_contexts_do_not_overwrite_each_other() = runBlocking {
        val store = RoomBrowseStateStore(database)
        store.save(browse("library", 20))
        store.save(browse("favorites", 30))
        // Direct calls accept their serialized write order, not wall-clock order.
        store.save(browse("library", -20))
        reopen()
        assertEquals(browse("library", -20), RoomBrowseStateStore(database).load("library"))
        assertEquals(browse("favorites", 30), RoomBrowseStateStore(database).load("favorites"))
        assertNull(RoomBrowseStateStore(database).load("absent"))
    }

    @Test fun favorite_setting_is_idempotent() = runBlocking {
        val store = RoomFavoritesStore(database)
        repeat(2) { assertEquals(CommitResult.Committed, store.setFavorite(" work ", true)) }
        assertEquals(1, database.userRecords().favoriteCount())
        assertNotNull(database.userRecords().favorite(" work "))
        repeat(2) { assertEquals(CommitResult.Committed, store.setFavorite(" work ", false)) }
        assertEquals(0, database.userRecords().favoriteCount())
        assertEquals(CommitResult.Committed, store.setFavorite("never-selected", false))
        assertNull(database.userRecords().reference("never-selected"))
        assertEquals(CommitResult.Failed("invalid_work_id"), store.setFavorite(" ", true))
    }

    @Test fun cache_eviction_preserves_user_records() = runBlocking {
        seedUserData()
        database.metadataCache().upsert(WorkMetadataCacheEntity("work", CatalogItemCodec.encode(CatalogItem(WorkId("work"), "合成"))))
        assertEquals(1, database.metadataCache().evictAll())
        reopen()
        assertNull(database.metadataCache().find("work"))
        assertEquals(browse(), RoomBrowseStateStore(database).load("library"))
        assertNotNull(database.userRecords().favorite("work"))
        assertEquals(playback, database.playbackSnapshots().find("work", "", "variant")!!.snapshot())
    }

    @Test fun actual_sql_constraints_reject_dangling_duplicate_and_null_rows() = runBlocking {
        withContext(Dispatchers.IO) {
            val sql = database.openHelper.writableDatabase
            val key = arrayOf<Any>(Utf16KeyConverter().encode("work"))
            assertThrows(SQLiteConstraintException::class.java) {
                sql.execSQL("INSERT INTO favorites(workId) VALUES (?)", arrayOf<Any>(Utf16KeyConverter().encode("absent")))
            }
            sql.execSQL("INSERT INTO work_references(workId) VALUES (?)", key)
            sql.execSQL("INSERT INTO favorites(workId) VALUES (?)", key)
            assertThrows(SQLiteConstraintException::class.java) { sql.execSQL("INSERT INTO favorites(workId) VALUES (?)", key) }
            assertThrows(SQLiteConstraintException::class.java) { sql.execSQL("INSERT INTO browse_snapshots(contextKey,payload) VALUES ('bad',NULL)") }
            assertThrows(SQLiteConstraintException::class.java) { sql.execSQL("DELETE FROM work_references WHERE workId=?", key) }
        }
        reopen()
        assertNotNull(database.userRecords().favorite("work"))
    }

    @Test fun actual_schema_has_required_columns_and_deferred_import_fk() = runBlocking {
        withContext(Dispatchers.IO) {
            val sql = database.openHelper.writableDatabase
            val expected = mapOf(
                "work_references" to listOf("workId"), "favorites" to listOf("workId"),
                "work_metadata_cache" to listOf("workId", "payload"),
                "browse_snapshots" to listOf("contextKey", "payload"),
                "playback_snapshots" to listOf("workId", "episodeKey", "variantKey", "payload"),
                "legacy_import_batches" to listOf("importVersion", "contentDigest", "capturedAt", "totalCount", "recognizedCount", "unresolvedCount", "malformedCount"),
                "legacy_import_records" to listOf("importVersion", "contentDigest", "storeKind", "entryKey", "valueType", "rawValue", "status", "reason",
                    "legacyLineIndex", "legacyEpisodeIndex", "legacyPositionMs", "legacyDurationMs"),
            )
            for ((table, columns) in expected) sql.query("PRAGMA table_info(`$table`)").use { cursor ->
                val actual = mutableListOf<String>()
                while (cursor.moveToNext()) {
                    val column = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    actual += column
                    if (column in setOf("workId", "contextKey", "episodeKey", "variantKey", "entryKey")) {
                        assertEquals("BLOB", cursor.getString(cursor.getColumnIndexOrThrow("type")))
                    }
                }
                assertEquals(columns, actual)
            }
            sql.query("SELECT sql FROM sqlite_master WHERE name='legacy_import_records'").use {
                assertTrue(it.moveToFirst())
                assertTrue(it.getString(0).contains("DEFERRABLE INITIALLY DEFERRED"))
            }
        }
    }

    @Test fun playback_null_keys_and_opaque_ids_are_distinct() = runBlocking {
        val id = WorkId(" work:opaque ")
        database.userRecords().insertReference(WorkReferenceEntity(id.value))
        val noEpisode = playback.copy(workId = id, episodeId = null, variantId = null, durationMs = null)
        val anEpisode = noEpisode.copy(episodeId = EpisodeId(" e "))
        database.playbackSnapshots().upsert(PlaybackSnapshotEntity.from(noEpisode))
        database.playbackSnapshots().upsert(PlaybackSnapshotEntity.from(anEpisode))
        reopen()
        assertEquals(noEpisode, database.playbackSnapshots().find(id.value, "", "")!!.snapshot())
        assertEquals(anEpisode, database.playbackSnapshots().find(id.value, " e ", "")!!.snapshot())
    }

    @Test fun corrupt_existing_payload_is_explicit_and_is_not_deleted() = runBlocking {
        val raw = "synthetic-private-corrupt"
        database.browseSnapshots().upsert(BrowseSnapshotEntity("library", raw))
        val error = assertThrows(StorageReadException::class.java) { runBlocking { RoomBrowseStateStore(database).load("library") } }
        assertEquals(StorageReadFailure.MALFORMED, error.reason)
        assertFalse(error.toString().contains(raw))
        reopen()
        assertEquals(raw, database.browseSnapshots().find("library")!!.payload)
    }

    @Test fun unsupported_format_is_retained() = runBlocking {
        val raw = BrowseSnapshotCodec.encode(browse()).replace("\"version\":1", "\"version\":2")
        database.browseSnapshots().upsert(BrowseSnapshotEntity("library", raw))
        assertEquals(StorageReadFailure.UNSUPPORTED_VERSION, assertThrows(StorageReadException::class.java) {
            runBlocking { RoomBrowseStateStore(database).load("library") }
        }.reason)
        reopen()
        assertEquals(raw, database.browseSnapshots().find("library")!!.payload)
    }

    @Test fun payload_cannot_claim_a_different_context() = runBlocking<Unit> {
        database.browseSnapshots().upsert(BrowseSnapshotEntity("favorites", BrowseSnapshotCodec.encode(browse())))
        assertThrows(StorageReadException::class.java) { runBlocking { RoomBrowseStateStore(database).load("favorites") } }
    }

    @Test fun failed_transaction_never_reports_committed_or_leaves_reference() = runBlocking {
        withContext(Dispatchers.IO) { database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_favorite BEFORE INSERT ON favorites BEGIN SELECT RAISE(ABORT, 'synthetic fault'); END") }
        assertEquals(CommitResult.Failed("storage_sql"), RoomFavoritesStore(database).setFavorite("rollback-work", true))
        reopen()
        assertNull(database.userRecords().reference("rollback-work"))
        assertEquals(0, database.userRecords().favoriteCount())
    }

    @Test fun failed_save_preserves_previous_snapshot() = runBlocking {
        RoomBrowseStateStore(database).save(browse())
        withContext(Dispatchers.IO) { database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_browse BEFORE INSERT ON browse_snapshots BEGIN SELECT RAISE(ABORT, 'synthetic fault'); END") }
        assertEquals(CommitResult.Failed("storage_sql"), RoomBrowseStateStore(database).save(browse(savedAt = 100)))
        reopen()
        assertEquals(browse(), RoomBrowseStateStore(database).load("library"))
    }

    @Test fun cancelled_calls_propagate() = runBlocking {
        val store = RoomBrowseStateStore(database)
        val call = async(start = CoroutineStart.UNDISPATCHED) {
            currentCoroutineContext().cancel()
            store.save(browse())
        }
        assertThrows(CancellationException::class.java) { runBlocking { call.await() } }
        assertNull(store.load("library"))
    }

    @Test fun unsupported_database_version_does_not_reset_records() = runBlocking {
        seedUserData()
        database.close()
        withContext(Dispatchers.IO) {
            SQLiteDatabase.openDatabase(context.getDatabasePath(databaseName).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.version = 99
            }
        }
        database = CinemaDatabaseFactory.open(context, databaseName)
        assertThrows(IllegalStateException::class.java) { runBlocking { database.userRecords().favoriteCount() } }
        database.close()
        withContext(Dispatchers.IO) {
            SQLiteDatabase.openDatabase(context.getDatabasePath(databaseName).path, null, SQLiteDatabase.OPEN_READONLY).use { sql ->
                assertEquals(99, sql.version)
                sql.rawQuery("SELECT COUNT(*) FROM favorites", null).use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            }
        }
    }

    @Test fun opaque_utf16_keys_and_payloads_survive_close_and_reopen() = runBlocking {
        val keys = listOf("\uD800", "\uD801", "?", "\\ud800", "\uDC00", "\uD83D\uDE00")
        for (key in keys) {
            assertEquals(CommitResult.Committed, RoomFavoritesStore(database).setFavorite(key, true))
            assertEquals(CommitResult.Committed, RoomBrowseStateStore(database).save(browse(key).copy(route = key)))
            database.playbackSnapshots().upsert(PlaybackSnapshotEntity.from(playback.copy(
                workId = WorkId(key), episodeId = EpisodeId(key), variantId = VariantId(key), sessionId = key)))
        }
        reopen()
        assertEquals(keys.size, database.userRecords().favoriteCount())
        for (key in keys) {
            assertEquals(key, database.userRecords().favorite(key)!!.workId)
            assertEquals(key, RoomBrowseStateStore(database).load(key)!!.route)
            assertEquals(key, database.playbackSnapshots().find(key, key, key)!!.snapshot().sessionId)
        }
    }
}
