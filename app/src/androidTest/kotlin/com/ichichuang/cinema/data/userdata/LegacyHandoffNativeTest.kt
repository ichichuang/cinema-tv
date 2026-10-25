package com.ichichuang.cinema.data.userdata

import android.content.Context
import android.database.sqlite.SQLiteFullException
import androidx.datastore.preferences.core.*
import com.ichichuang.cinema.data.local.codec.PlaybackSnapshotCodec
import com.ichichuang.cinema.data.local.migration.*
import com.ichichuang.cinema.data.settings.*
import com.ichichuang.cinema.domain.model.catalog.*
import com.ichichuang.cinema.domain.model.userdata.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.io.IOException
import java.security.MessageDigest

internal val HANDOFF_TARGET = PlaybackTarget(WorkId("fixture-work-r06"), EpisodeId("fixture-episode-a"), VariantId("fixture-pcm-cut"))
internal suspend fun handoffManifest(context: Context): HandoffManifest {
    val catalog = com.ichichuang.cinema.feature.player.ProbeFixtureRepository(context)
    val proof = { com.ichichuang.cinema.feature.player.HandoffCatalogFingerprint.compute(catalog, context.filesDir) }
    return HandoffManifest(LegacyHandoffRuntime.capture(context).contentDigest, proof(), listOf(
        HandoffLink("fixture-source", "fixture-movie", 0, 0, HANDOFF_TARGET),
        HandoffLink("fixture-source", "fixture-movie", 0, 1, HANDOFF_TARGET.copy(episodeId = EpisodeId("fixture-episode-b")))), proof)
}
internal fun legacyHashes(context: Context): Map<String, String> = File(context.filesDir, "datastore").listFiles().orEmpty()
    .filter { it.name.startsWith("cinema_") && it.isFile }.associate { it.name to
        MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString("") { b -> "%02x".format(b) } }

class LegacyHandoffNativeTest {
    private lateinit var context: Context
    private lateinit var controller: LegacyHandoffController
    private lateinit var manifest: HandoffManifest
    private lateinit var before: Map<String, String>
    @Before fun setup() = runBlocking<Unit> {
        context = ownedHandoffContext()
        File(context.noBackupFilesDir, LegacyHandoffRuntime.REQUIRED).delete()
        context.deleteDatabase(LegacyHandoffRuntime.DATABASE)
        // Separately authorized synthetic setup. Migration itself never edits these handles.
        LegacyHandoffRuntime.stores(context).values.forEach { it.edit { p -> p.clear() } }
        seedOwnedLegacy(context)
        manifest = handoffManifest(context); before = legacyHashes(context)
        controller = LegacyHandoffController.open(context)
    }
    @After fun close() {
        controller.close()
        File(context.noBackupFilesDir, LegacyHandoffRuntime.REQUIRED).delete()
        context.deleteDatabase(LegacyHandoffRuntime.DATABASE)
        File(context.noBackupFilesDir, LegacyHandoffRuntime.CONSENT).delete()
    }
    private fun reopen(hook: suspend (HandoffPhase) -> Unit = {}) {
        controller.close(); controller = LegacyHandoffController.open(context, hook)
    }
    private suspend fun prepare() { assertTrue(controller.prepare(manifest).eligible) }
    private suspend fun activate() { prepare(); assertEquals(CommitResult.Committed, controller.activate(manifest, manifest.expectedDigest)) }
    @Test fun prepare_stages_all_actual_typed_values_and_reopens_with_legacy_authority() = runBlocking<Unit> {
        prepare()
        assertEquals(1, controller.database.legacyImports().batchCount())
        assertEquals(LegacyHandoffRuntime.capture(context).entries.size, controller.database.legacyImports().recordCount())
        reopen(); assertEquals(HandoffWriter.LEGACY, controller.writer())
        assertEquals(before, legacyHashes(context))
    }
    @Test fun activation_commits_exact_snapshot_and_selects_one_writer() = runBlocking<Unit> {
        activate(); assertEquals(HandoffWriter.ROOM, controller.writer())
        val saved = RoomPlaybackProgressStore(controller.database).loadResume(HANDOFF_TARGET.workId)!!
        assertEquals(2_000L, saved.positionMs); assertEquals(HANDOFF_TARGET.episodeId, saved.episodeId)
        assertEquals(CommitResult.Committed, controller.room { RoomFavoritesStore(controller.database).setFavorite(HANDOFF_TARGET.workId.value, true) })
        assertThrows(IllegalStateException::class.java) { runBlocking { PlaybackProgress(context).clear("fixture-source#fixture-movie") } }
        assertEquals(before, legacyHashes(context)); reopen(); assertEquals(HandoffWriter.ROOM, controller.writer())
    }
    @Test fun identical_content_retry_is_idempotent_and_does_not_depend_on_capture_time() = runBlocking<Unit> {
        prepare(); delay(10); assertTrue(controller.prepare(manifest).eligible)
        assertEquals(1, controller.database.legacyImports().batchCount())
        assertEquals(manifest.expectedDigest, LegacyHandoffRuntime.capture(context).contentDigest)
        assertEquals(before, legacyHashes(context))
    }
    @Test fun changed_old_value_and_stale_digest_require_a_new_audit() = runBlocking<Unit> {
        prepare(); PlaybackProgress(context).save("fixture-source#fixture-movie", WatchPoint(0, 0, 3_000, 12_000))
        assertNotEquals(CommitResult.Committed, controller.activate(manifest, manifest.expectedDigest))
        assertEquals(HandoffWriter.LEGACY, controller.writer())
        val changed = handoffManifest(context); assertNotEquals(manifest.expectedDigest, changed.expectedDigest)
        assertTrue(controller.prepare(changed).eligible)
        assertEquals(2, controller.database.legacyImports().batchCount())
        assertNotEquals(CommitResult.Committed, controller.activate(changed, manifest.expectedDigest))
        assertEquals(CommitResult.Committed, controller.activate(changed, changed.expectedDigest))
    }
    @Test fun lost_acknowledgement_after_activation_retries_without_overwriting_newer_room_rewatch() = runBlocking<Unit> {
        prepare(); reopen { if (it == HandoffPhase.ACTIVATED) throw IOException("synthetic acknowledgement loss") }
        assertNotEquals(CommitResult.Committed, controller.activate(manifest, manifest.expectedDigest))
        reopen(); assertEquals(HandoffWriter.ROOM, controller.writer())
        val store = RoomPlaybackProgressStore(controller.database)
        val old = store.loadResume(HANDOFF_TARGET.workId)!!
        assertEquals(CommitResult.Committed, store.beginSession(HANDOFF_TARGET, "newer-rewatch"))
        val newer = old.copy(sessionId = "newer-rewatch", positionMs = 0, operationSequence = 0, capturedAt = -999)
        assertEquals(CommitResult.Committed, controller.room { store.commit(newer) })
        assertEquals(CommitResult.Committed, controller.activate(manifest, manifest.expectedDigest))
        reopen(); assertEquals(newer, RoomPlaybackProgressStore(controller.database).loadResume(HANDOFF_TARGET.workId))
        assertEquals(before, legacyHashes(context))
    }
    @Test fun explicitly_deleted_history_is_not_replayed_by_active_handoff_retry() = runBlocking<Unit> {
        activate()
        val repo=RoomPersonalLibraryRepository(controller.database)
        val store=RoomPlaybackProgressStore(controller.database)
        val imported=store.loadResume(HANDOFF_TARGET.workId)!!
        repo.setFavorite(HANDOFF_TARGET.workId.value,true)
        assertEquals(CommitResult.Committed,repo.deleteWatchHistory(HANDOFF_TARGET.workId.value))
        val stagedBefore=controller.database.legacyImports().records(1,manifest.expectedDigest)
        assertTrue(controller.prepare(manifest).eligible)
        assertEquals(CommitResult.Committed,controller.activate(manifest,manifest.expectedDigest))
        assertEquals(CommitResult.RejectedStale,store.beginSession(HANDOFF_TARGET,imported.sessionId))
        reopen()
        assertNull(RoomPlaybackProgressStore(controller.database).loadResume(HANDOFF_TARGET.workId))
        assertTrue(RoomPersonalLibraryRepository(controller.database).recentHistory().isEmpty())
        assertEquals(1,controller.database.userRecords().favoriteCount())
        assertEquals(stagedBefore,controller.database.legacyImports().records(1,manifest.expectedDigest))
        assertEquals(before,legacyHashes(context))
    }
    @Test fun storage_full_exception_inside_activation_rolls_back_marker_and_all_progress() = runBlocking<Unit> {
        prepare(); reopen { if (it == HandoffPhase.ACTIVATING) throw SQLiteFullException("synthetic full") }
        assertNotEquals(CommitResult.Committed, controller.activate(manifest, manifest.expectedDigest))
        reopen(); assertEquals(HandoffWriter.LEGACY, controller.writer())
        assertEquals(0, controller.database.playbackSnapshots().count())
        assertEquals(before, legacyHashes(context))
        assertEquals(CommitResult.Committed, controller.activate(manifest, manifest.expectedDigest))
    }
    @Test fun cancellation_at_capture_stage_verification_and_activation_is_recoverable() = runBlocking<Unit> {
        for (phase in listOf(HandoffPhase.CAPTURED, HandoffPhase.STAGED, HandoffPhase.VERIFIED, HandoffPhase.ACTIVATING)) {
            controller.close(); File(context.noBackupFilesDir, LegacyHandoffRuntime.REQUIRED).delete()
            context.deleteDatabase(LegacyHandoffRuntime.DATABASE)
            controller = LegacyHandoffController.open(context) { if (it == phase) throw CancellationException("synthetic $phase") }
            val job = async { if (phase == HandoffPhase.ACTIVATING) { prepare(); controller.activate(manifest, manifest.expectedDigest) }
                else controller.prepare(manifest) }
            assertThrows(CancellationException::class.java) { runBlocking { job.await() } }
            reopen(); assertEquals(phase.name, HandoffWriter.LEGACY, controller.writer())
            assertEquals(0, controller.database.playbackSnapshots().count()); assertEquals(before, legacyHashes(context))
            println("ROUND07_CANCEL phase=$phase legacy=true preserved=true")
        }
    }
    @Test fun io_failure_after_staging_keeps_preserved_rows_and_legacy_writer() = runBlocking<Unit> {
        reopen { if (it == HandoffPhase.STAGED) throw IOException("synthetic IO") }
        assertFalse(controller.prepare(manifest).eligible)
        reopen(); assertEquals(1, controller.database.legacyImports().batchCount())
        assertEquals(HandoffWriter.LEGACY, controller.writer()); assertEquals(before, legacyHashes(context))
        prepare(); assertEquals(1, controller.database.legacyImports().batchCount())
    }
    @Test fun actual_inflight_datastore_edit_finishes_before_capture_and_changed_snapshot_refuses_activation() = runBlocking<Unit> {
        prepare()
        val entered = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val store = LegacyHandoffRuntime.stores(context).getValue(LegacyStoreKind.PROGRESS)
        val saving = async { store.editLegacy(context) { p ->
            entered.complete(Unit); finish.await()
            p[stringPreferencesKey("wp_fixture-source#fixture-movie")] = "0,0,3000,12000"
        } }
        entered.await()
        val activating = async { controller.activate(manifest, manifest.expectedDigest) }
        try { yield(); assertFalse(activating.isCompleted) } finally { finish.complete(Unit) }
        saving.await(); assertNotEquals(CommitResult.Committed, activating.await())
        assertEquals(HandoffWriter.LEGACY, controller.writer())
        assertEquals(3_000L, PlaybackProgress(context).get("fixture-source#fixture-movie")!!.positionMs)
    }
    @Test fun delayed_legacy_writer_after_capture_is_refused_and_inactive_room_never_runs() = runBlocking<Unit> {
        var inactive = 0
        assertThrows(IllegalStateException::class.java) { runBlocking { controller.room { inactive++ } } }
        assertEquals(0, inactive)
        prepare()
        val captured = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        reopen { if (it == HandoffPhase.CAPTURED) { captured.complete(Unit); release.await() } }
        val activating = async { controller.activate(manifest, manifest.expectedDigest) }; captured.await()
        val attempted = CompletableDeferred<Unit>()
        val delayed = async { attempted.complete(Unit); runCatching { PlaybackProgress(context).save("fixture-source#fixture-movie", WatchPoint(0, 0, 9_000, 12_000)) } }
        attempted.await()
        try { yield(); assertFalse(delayed.isCompleted) } finally { release.complete(Unit) }
        assertEquals(CommitResult.Committed, activating.await())
        assertTrue(delayed.await().exceptionOrNull() is IllegalStateException)
        assertEquals(before, legacyHashes(context)); assertEquals(HandoffWriter.ROOM, controller.writer())
    }
    @Test fun capture_io_fault_leaves_no_partial_staging_or_activation() = runBlocking<Unit> {
        reopen { if (it == HandoffPhase.CAPTURED) throw IOException("synthetic read boundary") }
        assertFalse(controller.prepare(manifest).eligible)
        reopen(); assertEquals(0, controller.database.legacyImports().batchCount())
        assertEquals(HandoffWriter.LEGACY, controller.writer()); assertEquals(before, legacyHashes(context))
    }
    @Test fun malformed_or_unresolved_progress_is_preserved_and_denies_handoff() = runBlocking<Unit> {
        val store = LegacyHandoffRuntime.stores(context).getValue(LegacyStoreKind.PROGRESS)
        for (raw in listOf("0,0,2000,unknown", "0,0,2000,12000")) {
            store.edit { it[stringPreferencesKey("wp_bare-id")] = raw }
            val m = handoffManifest(context); val bytes = legacyHashes(context)
            assertFalse(controller.prepare(m).eligible)
            assertNotEquals(CommitResult.Committed, controller.activate(m, m.expectedDigest))
            assertEquals(HandoffWriter.LEGACY, controller.writer()); assertEquals(bytes, legacyHashes(context))
            assertTrue(controller.database.legacyImports().records(1, m.expectedDigest).any { it.entryKey == "wp_bare-id" && it.rawValue.contains(raw) })
        }
    }
    @Test fun changed_catalog_and_tampered_staging_cannot_be_activated() = runBlocking<Unit> {
        prepare()
        val changed = HandoffManifest(manifest.expectedDigest, "changed-timebase-or-lineup", manifest.links)
        assertNotEquals(CommitResult.Committed, controller.activate(changed, changed.expectedDigest))
        withContext(Dispatchers.IO) { controller.database.openHelper.writableDatabase.execSQL("UPDATE legacy_import_records SET rawValue='\"tampered\"' WHERE storeKind='progress'") }
        assertNotEquals(CommitResult.Committed, controller.activate(manifest, manifest.expectedDigest))
        assertEquals(HandoffWriter.LEGACY, controller.writer()); assertEquals(before, legacyHashes(context))
    }
    @Test fun media_changed_after_prepare_cannot_activate_with_the_original_manifest() = runBlocking<Unit> {
        prepare()
        val file = File(context.filesDir, "round06-a.wav"); val bytes = file.readBytes()
        try {
            file.writeBytes(bytes.copyOf().also { it[44] = (it[44].toInt() xor 1).toByte() })
            assertNotEquals(CommitResult.Committed, controller.activate(manifest, manifest.expectedDigest))
            assertEquals(HandoffWriter.LEGACY, controller.writer())
            assertEquals(0, controller.database.playbackSnapshots().count())
            withContext(Dispatchers.IO) {
                for (table in listOf("playback_sessions", "playback_resume")) controller.database.openHelper.readableDatabase
                    .query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            }
            assertEquals(before, legacyHashes(context))
        } finally { file.writeBytes(bytes) }
    }
    @Test fun missing_authorization_or_database_after_activation_never_falls_back_to_stale_legacy() = runBlocking<Unit> {
        activate(); File(context.noBackupFilesDir, LegacyHandoffRuntime.CONSENT).delete()
        assertEquals(HandoffWriter.BLOCKED, controller.writer())
        assertThrows(IllegalStateException::class.java) { runBlocking { PlaybackProgress(context).save("fixture-source#fixture-movie", WatchPoint(0, 0, 0, 0)) } }
        File(context.noBackupFilesDir, LegacyHandoffRuntime.CONSENT).writeText(LegacyHandoffRuntime.CONSENT_TOKEN)
        controller.close()
        val file = context.getDatabasePath(LegacyHandoffRuntime.DATABASE); val parked = File(file.path + ".synthetic-parked")
        assertTrue(file.renameTo(parked))
        try { assertThrows(IllegalStateException::class.java) { LegacyHandoffController.open(context) }; assertFalse(file.exists()) }
        finally { assertTrue(parked.renameTo(file)); controller = LegacyHandoffController.open(context) }
        assertEquals(HandoffWriter.ROOM, controller.writer()); assertEquals(before, legacyHashes(context))
    }
    @Test fun missing_activation_intent_with_existing_active_database_cannot_reenable_legacy_writes() = runBlocking<Unit> {
        activate()
        File(context.noBackupFilesDir, LegacyHandoffRuntime.REQUIRED).delete()
        assertFalse(LegacyHandoffRuntime.required(context))
        assertEquals(HandoffWriter.BLOCKED, controller.writer())
        assertThrows(IllegalStateException::class.java) { runBlocking {
            PlaybackProgress(context).save("fixture-source#fixture-movie", WatchPoint(0, 0, 0, 0))
        } }
        assertEquals(before, legacyHashes(context))
    }
    @Test fun clear_image_cache_preserves_progress_favorite_and_staged_browse_filters_sources() = runBlocking<Unit> {
        activate(); val store = RoomPlaybackProgressStore(controller.database)
        val saved = store.loadResume(HANDOFF_TARGET.workId)
        assertEquals(CommitResult.Committed, RoomFavoritesStore(controller.database).setFavorite(HANDOFF_TARGET.workId.value, true))
        val records = controller.database.legacyImports().records(1, manifest.expectedDigest)
        assertNotNull(ImageCache.clear(context))
        assertEquals(saved, store.loadResume(HANDOFF_TARGET.workId)); assertEquals(1, controller.database.userRecords().favoriteCount())
        assertEquals(records, controller.database.legacyImports().records(1, manifest.expectedDigest)); assertEquals(before, legacyHashes(context))
    }
}
