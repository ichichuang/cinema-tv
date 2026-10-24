package com.ichichuang.cinema.feature.player

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.BuildConfig
import com.ichichuang.cinema.data.local.codec.PlaybackSnapshotCodec
import com.ichichuang.cinema.data.settings.*
import com.ichichuang.cinema.data.userdata.*
import com.ichichuang.cinema.domain.model.userdata.*
import com.ichichuang.cinema.domain.repository.MovieRepository
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

internal class HandoffProbeFixture(context: Context, override val handoffManifest: HandoffManifest,
    private val fixture: ProbeFixtureRepository = ProbeFixtureRepository(context)) :
    MovieRepository by fixture, HandoffPlaybackProbeCatalog {
    override val probeMedia = fixture.probeMedia
}

/** Host invokes these independently. Phase B must have a different PID; phase C uses a probe-OFF APK. */
class LegacyHandoffProcessTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun file(name: String) = File(context.noBackupFilesDir, "round07-$name")
    private fun expectedLegacyHashes(): Map<String, String> {
        val json = JSONObject(file("legacy-hashes.json").readText())
        return json.keys().asSequence().associateWith { json.getString(it) }
    }
    private suspend fun until(condition: suspend () -> Boolean) = withTimeout(20_000) { while (!condition()) delay(25) }
    private suspend fun read(): PlaybackSnapshot? = LegacyHandoffController.open(context).use {
        it.room { RoomPlaybackProgressStore(it.database).loadResume(HANDOFF_TARGET.workId) }
    }
    private suspend fun launch(manifest: HandoffManifest): PlayerViewModel = withContext(Dispatchers.Main) {
        PlayerViewModel(HandoffProbeFixture(context, manifest), PlaybackProgress(context), CinemaSettings(context), context).also {
            it.start("fixture-movie", "合成测试", "fixture-source")
        }
    }
    @Test fun phase_prepare_interrupted_after_staging_commit() = runBlocking<Unit> {
        ownedHandoffContext(); resetOwnedHandoffCandidate(context)
        LegacyHandoffRuntime.stores(context).values.forEach { it.edit { p -> p.clear() } }
        seedOwnedLegacy(context)
        val manifest = handoffManifest(context)
        LegacyHandoffController.open(context) { if (it == HandoffPhase.STAGED) throw java.io.IOException("synthetic interrupted acknowledgement") }.use {
            assertFalse(it.prepare(manifest).eligible)
            assertEquals("staged", it.database.legacyHandoff().state()!!.phase)
            assertEquals(HandoffWriter.LEGACY, it.writer())
        }
        file("legacy-hashes.json").writeText(JSONObject(legacyHashes(context)).toString())
        file("process.pid").writeText(android.os.Process.myPid().toString())
        println("ROUND07_STAGED pid=${android.os.Process.myPid()} committed=true activation=false")
    }
    @Test fun phase_resume_before_activation_keeps_legacy_after_real_process_death() = runBlocking<Unit> {
        ownedHandoffContext()
        val previous = file("process.pid").readText().toInt(); assertNotEquals(previous, android.os.Process.myPid())
        LegacyHandoffController.open(context).use {
            assertEquals("staged", it.database.legacyHandoff().state()!!.phase)
            assertEquals(HandoffWriter.LEGACY, it.writer())
        }
        assertEquals(PlaybackRuntimeMode.LEGACY, PlaybackProbeAuthorization.mode(context))
        assertEquals(2_000L, PlaybackProgress(context).get("fixture-source#fixture-movie")!!.positionMs)
        assertEquals(expectedLegacyHashes(), legacyHashes(context))
        println("ROUND07_PREACTIVE_RESTART pid=${android.os.Process.myPid()} previous=$previous legacy=true preserved=true")
        resetOwnedHandoffCandidate(context)
    }
    @Test fun phase_a_actual_legacy_to_paused_room_restore_and_newer_checkpoint() = runBlocking<Unit> {
        ownedHandoffContext()
        check(!LegacyHandoffRuntime.required(context) && !context.getDatabasePath(LegacyHandoffRuntime.DATABASE).exists())
        LegacyHandoffRuntime.stores(context).values.forEach { it.edit { p -> p.clear() } }
        seedOwnedLegacy(context)
        val manifest = handoffManifest(context)
        val old = legacyHashes(context)
        LegacyHandoffController.open(context).use {
            assertTrue(it.prepare(manifest).eligible)
            assertEquals(CommitResult.Committed, it.activate(manifest, manifest.expectedDigest))
        }
        val vm = launch(manifest)
        until { withContext(Dispatchers.Main) { vm.player.playbackState == Player.STATE_READY } }
        withContext(Dispatchers.Main) {
            assertEquals(2_000L, vm.player.currentPosition); assertFalse(vm.player.playWhenReady); assertFalse(vm.player.isPlaying)
            vm.togglePlay()
        }
        until { read()?.positionMs?.let { it >= 7_000 } == true }
        val paused = CompletableDeferred<Unit>()
        withContext(Dispatchers.Main) {
            vm.player.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) && !player.playWhenReady) {
                        player.removeListener(this); paused.complete(Unit)
                    }
                }
            }); vm.player.pause()
        }
        withTimeout(10_000) { paused.await() }
        assertEquals(CommitResult.Committed, withContext(Dispatchers.Main) { vm.roomProbe!!.boundary() })
        val saved = read()!!
        assertTrue(saved.positionMs >= 7_000); assertEquals(old, legacyHashes(context))
        file("expected.json").writeText(PlaybackSnapshotCodec.encode(saved))
        file("legacy-hashes.json").writeText(JSONObject(old).toString())
        file("manifest.digest").writeText(manifest.expectedDigest)
        file("process.pid").writeText(android.os.Process.myPid().toString())
        println("ROUND07_PHASE_A pid=${android.os.Process.myPid()} legacy=2000 restored=2000 committed=${saved.positionMs} sequence=${saved.operationSequence}")
    }
    @Test fun phase_b_separate_process_restores_new_room_position_paused_and_never_legacy() = runBlocking<Unit> {
        ownedHandoffContext()
        val previous = file("process.pid").readText().toInt(); assertNotEquals(previous, android.os.Process.myPid())
        val expected = PlaybackSnapshotCodec.decode(file("expected.json").readText())
        val manifest = handoffManifest(context)
        assertEquals(file("manifest.digest").readText(), manifest.expectedDigest)
        assertEquals(expected, read())
        val vm = launch(manifest)
        until { withContext(Dispatchers.Main) { vm.player.playbackState == Player.STATE_READY } }
        withContext(Dispatchers.Main) {
            assertEquals(expected.positionMs, vm.player.currentPosition); assertFalse(vm.player.isPlaying); assertFalse(vm.player.playWhenReady)
        }
        delay(300); assertEquals(expected, read())
        assertEquals(expectedLegacyHashes(), legacyHashes(context))
        println("ROUND07_PHASE_B pid=${android.os.Process.myPid()} previous=$previous restored=${expected.positionMs} paused=true")
        withContext(Dispatchers.Main) { ViewModelStore().apply { put("handoff", vm); clear() } }
    }
    @Test fun phase_c_probe_off_on_activated_profile_fails_closed_without_any_old_write() = runBlocking<Unit> {
        check(!BuildConfig.PLAYBACK_ROOM_PROBE && context.packageName == "com.ichichuang.cinema.debug")
        check(InstrumentationRegistry.getArguments().getString("cinema.handoff") == "round07")
        val before = legacyHashes(context)
        assertTrue(LegacyHandoffRuntime.required(context))
        assertThrows(IllegalStateException::class.java) { runBlocking { withContext(Dispatchers.Main) {
            PlayerViewModel(ProbeFixtureRepository(context), PlaybackProgress(context), CinemaSettings(context), context)
        } } }
        assertThrows(IllegalStateException::class.java) { runBlocking { PlaybackProgress(context).save("fixture-source#fixture-movie", WatchPoint(0, 0, 0, 0)) } }
        assertEquals(before, legacyHashes(context))
        println("ROUND07_GATE_OFF activated=true blocked=true oldBytesUnchanged=true")
    }
}
