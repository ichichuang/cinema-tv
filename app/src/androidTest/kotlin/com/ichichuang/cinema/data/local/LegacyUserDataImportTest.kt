package com.ichichuang.cinema.data.local

import android.database.sqlite.SQLiteFullException
import android.database.sqlite.SQLiteConstraintException
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichichuang.cinema.data.local.migration.*
import com.ichichuang.cinema.data.local.entity.LegacyImportRecordEntity
import com.ichichuang.cinema.data.userdata.RoomFavoritesStore
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class LegacyUserDataImportTest : NativeStorageFixture() {
    @Test fun import_is_idempotent() = runBlocking {
        val snapshot = seedLegacy()
        assertTrue(LegacySnapshotImporter(database).preserve(snapshot) is LegacyImportResult.Preserved)
        reopen()
        assertTrue(LegacySnapshotImporter(database).preserve(snapshot) is LegacyImportResult.AlreadyPreserved)
        assertEquals(1, database.legacyImports().batchCount())
        assertEquals(snapshot.entries.size, database.legacyImports().recordCount())
    }

    @Test fun changed_snapshot_is_not_silently_skipped() = runBlocking {
        val first = seedLegacy()
        LegacySnapshotImporter(database).preserve(first)
        stores.getValue(LegacyStoreKind.PROGRESS).edit { it[stringPreferencesKey("wp_7")] = "0,9001,12,0" }
        val changed = LegacySnapshotReader(stores).capture(first.capturedAt)
        assertNotEquals(first.contentDigest, changed.contentDigest)
        assertTrue(LegacySnapshotImporter(database).preserve(changed) is LegacyImportResult.Preserved)
        reopen()
        assertEquals(2, database.legacyImports().batchCount())
        assertEquals(first.entries.size + changed.entries.size, database.legacyImports().recordCount())
    }

    @Test fun same_content_new_capture_time_is_not_new_data() = runBlocking {
        val first = seedLegacy()
        LegacySnapshotImporter(database).preserve(first)
        val later = LegacySnapshotReader(stores).capture(Long.MAX_VALUE)
        assertEquals(first.contentDigest, later.contentDigest)
        assertTrue(LegacySnapshotImporter(database).preserve(later) is LegacyImportResult.AlreadyPreserved)
        assertEquals(1, database.legacyImports().batchCount())
    }

    @Test fun unresolved_legacy_id_is_preserved() = runBlocking {
        val snapshot = seedLegacy()
        LegacySnapshotImporter(database).preserve(snapshot)
        reopen()
        val records = database.legacyImports().records(1, snapshot.contentDigest)
        for (entry in snapshot.entries.filter { it.store == LegacyStoreKind.PROGRESS && it.key.startsWith("wp_") && !it.key.endsWith("malformed") }) {
            val row = records.single { it.storeKind == entry.store.tag && it.entryKey == entry.key }
            assertEquals("unresolved", row.status)
            assertEquals(entry.value.encoded, row.rawValue)
            assertNotNull(row.legacyEpisodeIndex)
        }
        assertEquals(0, database.userRecords().referenceCount())
        assertEquals(0, database.playbackSnapshots().count())
        assertEquals(0, database.userRecords().favoriteCount())
    }

    @Test fun malformed_entry_is_quarantined() = runBlocking {
        val snapshot = seedLegacy()
        val result = LegacySnapshotImporter(database).preserve(snapshot) as LegacyImportResult.Preserved
        assertEquals(1, result.report.malformedCount)
        reopen()
        val row = database.legacyImports().records(1, snapshot.contentDigest).single { it.entryKey == "wp_malformed" }
        assertEquals("malformed", row.status)
        assertEquals("\"0,1,20,unknown\"", row.rawValue)
        assertNull(row.legacyDurationMs)
        assertEquals("invalid_progress_coordinates", row.reason)
    }

    @Test fun legacy_files_are_unchanged() = runBlocking {
        val snapshot = seedLegacy()
        val before = sourceFiles.mapValues { it.value.readBytes() }
        LegacySnapshotImporter(database).preserve(snapshot)
        val after = LegacySnapshotReader(stores).capture(999)
        assertEquals(snapshot.entries, after.entries)
        for ((kind, bytes) in before) assertArrayEquals(bytes, sourceFiles.getValue(kind).readBytes())
    }

    @Test fun failure_before_marker_rolls_back_rows() = runBlocking {
        val snapshot = seedLegacy()
        val result = LegacySnapshotImporter(database) { phase ->
            if (phase == LegacyImportPhase.ROWS_STAGED) throw IOException("synthetic I/O failure")
        }.preserve(snapshot)
        assertEquals(LegacyImportResult.Failed("storage_io"), result)
        assertEmptyAfterReopen()
    }

    @Test fun failure_after_marker_before_commit_rolls_back_all() = runBlocking {
        val snapshot = seedLegacy()
        val result = LegacySnapshotImporter(database) { phase ->
            if (phase == LegacyImportPhase.MARKER_WRITTEN) throw IOException("synthetic I/O failure")
        }.preserve(snapshot)
        assertEquals(LegacyImportResult.Failed("storage_io"), result)
        assertEmptyAfterReopen()
    }

    @Test fun cancellation_before_commit_rolls_back_and_propagates() = runBlocking {
        val snapshot = seedLegacy()
        val reached = CompletableDeferred<Unit>()
        val waitForCancellation = CompletableDeferred<Unit>()
        val call = async {
            LegacySnapshotImporter(database) { phase ->
                if (phase == LegacyImportPhase.MARKER_WRITTEN) {
                    reached.complete(Unit)
                    waitForCancellation.await()
                }
            }.preserve(snapshot)
        }
        withTimeout(10_000) { reached.await() }
        call.cancel()
        assertThrows(CancellationException::class.java) { runBlocking { call.await() } }
        assertEmptyAfterReopen()
    }

    @Test fun retry_after_commit_acknowledgement_loss_is_idempotent() = runBlocking {
        val snapshot = seedLegacy()
        val result = LegacySnapshotImporter(database) { phase ->
            if (phase == LegacyImportPhase.COMMITTED) throw IOException("synthetic acknowledgement loss")
        }.preserve(snapshot)
        assertEquals(LegacyImportResult.Failed("storage_io"), result)
        reopen()
        assertEquals(snapshot.entries.size, database.legacyImports().recordCount())
        assertEquals(1, database.legacyImports().batchCount())
        assertTrue(LegacySnapshotImporter(database).preserve(snapshot) is LegacyImportResult.AlreadyPreserved)
        assertEquals(snapshot.entries.size, database.legacyImports().recordCount())
    }

    @Test fun cancellation_after_commit_propagates_and_retry_reconciles() = runBlocking {
        val snapshot = seedLegacy()
        val reached = CompletableDeferred<Unit>()
        val waitForCancellation = CompletableDeferred<Unit>()
        val call = async {
            LegacySnapshotImporter(database) { phase ->
                if (phase == LegacyImportPhase.COMMITTED) {
                    reached.complete(Unit)
                    waitForCancellation.await()
                }
            }.preserve(snapshot)
        }
        withTimeout(10_000) { reached.await() }
        call.cancel()
        assertThrows(CancellationException::class.java) { runBlocking { call.await() } }
        reopen()
        assertEquals(snapshot.entries.size, database.legacyImports().recordCount())
        assertTrue(LegacySnapshotImporter(database).preserve(snapshot) is LegacyImportResult.AlreadyPreserved)
    }

    @Test fun injected_storage_full_has_no_false_success_or_partial_batch() = runBlocking {
        val snapshot = seedLegacy()
        val result = LegacySnapshotImporter(database) { phase ->
            if (phase == LegacyImportPhase.ROWS_STAGED) throw SQLiteFullException("synthetic storage-full injection")
        }.preserve(snapshot)
        assertEquals(LegacyImportResult.Failed("storage_sql"), result)
        assertEmptyAfterReopen()
    }

    @Test fun source_read_failure_cannot_create_a_completed_batch() = runBlocking {
        sourceFiles.getValue(LegacyStoreKind.PROGRESS).writeBytes(byteArrayOf(-1, -1, -1))
        assertThrows(IOException::class.java) { runBlocking { LegacySnapshotReader(stores).capture(1) } }
        assertEmptyAfterReopen()
    }

    @Test fun every_entry_is_accounted_for_without_overwriting_user_records() = runBlocking {
        RoomFavoritesStore(database).setFavorite("7", true)
        val snapshot = seedLegacy()
        val report = (LegacySnapshotImporter(database).preserve(snapshot) as LegacyImportResult.Preserved).report
        assertEquals(snapshot.entries.size, report.totalCount)
        assertEquals(report.totalCount, report.recognizedCount + report.unresolvedCount + report.malformedCount)
        assertEquals(5, report.recognizedCount)
        assertEquals(1, report.malformedCount)
        reopen()
        assertNotNull(database.userRecords().favorite("7"))
        assertEquals(1, database.userRecords().referenceCount())
        val rows = database.legacyImports().records(1, snapshot.contentDigest)
        assertEquals(snapshot.entries.size, rows.size)
        for (entry in snapshot.entries) {
            val row = rows.single { it.entryKey == entry.key && it.storeKind == entry.store.tag }
            assertEquals(entry.value.type, row.valueType)
            assertEquals(entry.value.encoded, row.rawValue)
        }
    }

    @Test fun empty_snapshot_is_an_idempotent_preserved_batch() = runBlocking {
        val snapshot = LegacySnapshot.create(emptyList(), -1)
        val result = LegacySnapshotImporter(database).preserve(snapshot) as LegacyImportResult.Preserved
        assertEquals(0, result.report.totalCount)
        reopen()
        assertTrue(LegacySnapshotImporter(database).preserve(snapshot) is LegacyImportResult.AlreadyPreserved)
        assertEquals(1, database.legacyImports().batchCount())
        assertEquals(0, database.legacyImports().recordCount())
    }

    @Test fun staging_rows_without_a_batch_cannot_commit() = runBlocking {
        val row = LegacyImportRecordEntity(1, "synthetic-digest", "progress", "wp_7", "string", "\"0,0,0,0\"",
            "unresolved", "canonical_identity_unresolved", 0, 0, 0L, 0L)
        assertThrows(SQLiteConstraintException::class.java) { runBlocking { database.legacyImports().insertRecords(listOf(row)) } }
        assertEmptyAfterReopen()
    }

    @Test fun unicode_key_order_cannot_drop_or_reject_entries() = runBlocking {
        val snapshot = LegacySnapshot.create(listOf("\uE000", "\uD83D\uDE00").map {
            LegacyEntry(LegacyStoreKind.PROGRESS, it, LegacyValue.from("exact raw"))
        }, 1)
        assertTrue(LegacySnapshotImporter(database).preserve(snapshot) is LegacyImportResult.Preserved)
        reopen()
        assertEquals(snapshot.entries.map { it.key }.toSet(),
            database.legacyImports().records(1, snapshot.contentDigest).map { it.entryKey }.toSet())
    }

    @Test fun changed_utf16_content_is_preserved_as_distinct_batches() = runBlocking {
        val values = listOf("\uD800", "\uD801", "?")
        val snapshots = values.map { value -> LegacySnapshot.create(listOf(
            LegacyEntry(LegacyStoreKind.BROWSE, "\uD802", LegacyValue.from(value))), 1) }
        for (snapshot in snapshots) assertTrue(LegacySnapshotImporter(database).preserve(snapshot) is LegacyImportResult.Preserved)
        reopen()
        assertEquals(values.size, database.legacyImports().batchCount())
        for (snapshot in snapshots) {
            val row = database.legacyImports().records(1, snapshot.contentDigest).single()
            assertEquals(snapshot.entries.single().key, row.entryKey)
            assertEquals(snapshot.entries.single().value.encoded, row.rawValue)
        }
    }

    @Test fun existing_marker_with_different_raw_rows_is_not_acknowledged() = runBlocking {
        val snapshot = seedLegacy()
        LegacySnapshotImporter(database).preserve(snapshot)
        withContext(Dispatchers.IO) { database.openHelper.writableDatabase.execSQL(
            "UPDATE legacy_import_records SET rawValue='\"synthetic changed raw\"' WHERE contentDigest=?", arrayOf(snapshot.contentDigest)) }
        assertEquals(LegacyImportResult.Failed("import_accounting"), LegacySnapshotImporter(database).preserve(snapshot))
        reopen()
        assertEquals(1, database.legacyImports().batchCount())
        assertEquals(snapshot.entries.size, database.legacyImports().recordCount())
    }

    private suspend fun assertEmptyAfterReopen() {
        reopen()
        assertEquals(0, database.legacyImports().recordCount())
        assertEquals(0, database.legacyImports().batchCount())
    }
}
