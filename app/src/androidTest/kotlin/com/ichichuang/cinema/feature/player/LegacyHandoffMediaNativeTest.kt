package com.ichichuang.cinema.feature.player

import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModelStore
import com.ichichuang.cinema.data.settings.*
import com.ichichuang.cinema.data.userdata.*
import com.ichichuang.cinema.domain.model.userdata.CommitResult
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.URI

class LegacyHandoffMediaNativeTest {
    private suspend fun withActivatedPlayer(block: suspend (android.content.Context, HandoffProbeFixture, PlayerViewModel) -> Unit) {
        val context = ownedHandoffContext(); resetOwnedHandoffCandidate(context)
        LegacyHandoffRuntime.stores(context).values.forEach { it.edit { p -> p.clear() } }; seedOwnedLegacy(context)
        val manifest = handoffManifest(context)
        LegacyHandoffController.open(context).use {
            assertTrue(it.prepare(manifest).eligible); assertEquals(CommitResult.Committed, it.activate(manifest, manifest.expectedDigest))
        }
        val fixture = HandoffProbeFixture(context, manifest)
        val vm = withContext(Dispatchers.Main) { PlayerViewModel(fixture, PlaybackProgress(context), CinemaSettings(context), context).also {
            it.start("fixture-movie", "合成测试", "fixture-source")
        } }
        try {
            withTimeout(10_000) { while (withContext(Dispatchers.Main) { vm.player.playbackState != androidx.media3.common.Player.STATE_READY }) delay(25) }
            block(context, fixture, vm)
        } finally {
            withContext(Dispatchers.Main) { ViewModelStore().apply { put("handoff", vm); clear() } }
            delay(100); resetOwnedHandoffCandidate(context)
        }
    }
    private suspend fun sessions(context: android.content.Context): Int = LegacyHandoffController.open(context).use { c ->
        withContext(Dispatchers.IO) { c.database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM playback_sessions").use {
            it.moveToFirst(); it.getInt(0)
        } }
    }
    @Test fun replaced_next_episode_is_refused_before_actual_viewmodel_switch_or_new_session() = runBlocking<Unit> {
        withActivatedPlayer { context, fixture, vm ->
            val count = sessions(context); val hashes = legacyHashes(context)
            val saved = LegacyHandoffController.open(context).use { RoomPlaybackProgressStore(it.database).loadResume(HANDOFF_TARGET.workId) }
            val media = fixture.probeMedia[1]
            val file = File(URI(media.detail.lines[media.lineIndex].episodes[media.episodeIndex].url)); val original = file.readBytes()
            try {
                file.writeBytes(original.copyOf().also { it[44] = (it[44].toInt() xor 1).toByte() })
                withContext(Dispatchers.Main) { vm.selectEpisode(1) }
                withTimeout(10_000) { while (withContext(Dispatchers.Main) { vm.state.value.error == null && vm.state.value.episodeIndex == 0 }) delay(25) }
                assertEquals(0, withContext(Dispatchers.Main) { vm.state.value.episodeIndex })
                assertNotNull(withContext(Dispatchers.Main) { vm.state.value.error })
                assertEquals(count, sessions(context)); assertEquals(hashes, legacyHashes(context))
                LegacyHandoffController.open(context).use { assertEquals(saved, RoomPlaybackProgressStore(it.database).loadResume(HANDOFF_TARGET.workId)) }
            } finally { file.writeBytes(original) }
        }
    }
    @Test fun replaced_current_media_is_refused_before_real_retry_and_checkpoint_side_effects() = runBlocking<Unit> {
        withActivatedPlayer { context, fixture, vm ->
            val count = sessions(context); val hashes = legacyHashes(context)
            val saved = LegacyHandoffController.open(context).use { RoomPlaybackProgressStore(it.database).loadResume(HANDOFF_TARGET.workId) }
            val media = fixture.probeMedia[0]
            val file = File(URI(media.detail.lines[media.lineIndex].episodes[media.episodeIndex].url)); val original = file.readBytes()
            try {
                file.writeBytes(original.copyOf().also { it[44] = (it[44].toInt() xor 1).toByte() })
                assertThrows(IllegalArgumentException::class.java) { runBlocking { withContext(Dispatchers.Main) { vm.roomProbe!!.retry() } } }
                withContext(Dispatchers.Main) { vm.retry() }
                withTimeout(10_000) { while (withContext(Dispatchers.Main) { vm.state.value.error == null }) delay(25) }
                assertEquals(count, sessions(context)); assertEquals(hashes, legacyHashes(context))
                LegacyHandoffController.open(context).use { assertEquals(saved, RoomPlaybackProgressStore(it.database).loadResume(HANDOFF_TARGET.workId)) }
            } finally { file.writeBytes(original) }
        }
    }
    @Test fun revoked_authorization_reports_a_failed_checkpoint_without_crashing_or_writing_legacy() = runBlocking<Unit> {
        val context = ownedHandoffContext(); resetOwnedHandoffCandidate(context)
        LegacyHandoffRuntime.stores(context).values.forEach { it.edit { p -> p.clear() } }; seedOwnedLegacy(context)
        val manifest = handoffManifest(context)
        LegacyHandoffController.open(context).use {
            assertTrue(it.prepare(manifest).eligible); assertEquals(CommitResult.Committed, it.activate(manifest, manifest.expectedDigest))
        }
        val fixture = HandoffProbeFixture(context, manifest)
        val vm = withContext(Dispatchers.Main) { PlayerViewModel(fixture, PlaybackProgress(context), CinemaSettings(context), context).also {
            it.start("fixture-movie", "合成测试", "fixture-source")
        } }
        try {
            withTimeout(10_000) { while (withContext(Dispatchers.Main) { vm.player.playbackState != androidx.media3.common.Player.STATE_READY }) delay(25) }
            withContext(Dispatchers.Main) { vm.togglePlay() }
            withTimeout(15_000) {
                while (LegacyHandoffController.open(context).use { c ->
                    c.room { RoomPlaybackProgressStore(c.database).loadResume(HANDOFF_TARGET.workId) }!!.positionMs < 7_000
                }) delay(25)
            }
            val paused = CompletableDeferred<Unit>()
            withContext(Dispatchers.Main) {
                vm.player.addListener(object : androidx.media3.common.Player.Listener {
                    override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
                        if (events.contains(androidx.media3.common.Player.EVENT_PLAY_WHEN_READY_CHANGED) && !player.playWhenReady) {
                            player.removeListener(this); paused.complete(Unit)
                        }
                    }
                }); vm.player.pause()
            }
            withTimeout(10_000) { paused.await() }
            assertEquals(CommitResult.Committed, withContext(Dispatchers.Main) { vm.roomProbe!!.boundary() })
            val before = legacyHashes(context)
            File(context.noBackupFilesDir, LegacyHandoffRuntime.CONSENT).delete()
            assertEquals(CommitResult.Failed("handoff_unavailable"), withContext(Dispatchers.Main) { vm.roomProbe!!.boundary() })
            assertTrue(withContext(Dispatchers.Main) { vm.state.value.hint?.contains("未保存") == true })
            assertEquals(before, legacyHashes(context))
        } finally {
            File(context.noBackupFilesDir, LegacyHandoffRuntime.CONSENT).writeText(LegacyHandoffRuntime.CONSENT_TOKEN)
            withContext(Dispatchers.Main) { ViewModelStore().apply { put("handoff", vm); clear() } }
            delay(100); resetOwnedHandoffCandidate(context)
        }
    }
    @Test fun changed_media_bytes_with_same_uri_episode_and_variant_cannot_use_a_verified_handoff() = runBlocking<Unit> {
        val context = ownedHandoffContext(); resetOwnedHandoffCandidate(context)
        LegacyHandoffRuntime.stores(context).values.forEach { it.edit { p -> p.clear() } }
        seedOwnedLegacy(context)
        val manifest = handoffManifest(context)
        LegacyHandoffController.open(context).use {
            assertTrue(it.prepare(manifest).eligible)
            assertEquals(CommitResult.Committed, it.activate(manifest, manifest.expectedDigest))
        }
        val fixture = HandoffProbeFixture(context, manifest)
        val media = fixture.probeMedia.first()
        val wav = File(URI(media.detail.lines[media.lineIndex].episodes[media.episodeIndex].url))
        val original = wav.readBytes(); val before = legacyHashes(context)
        val vm = withContext(Dispatchers.Main) { PlayerViewModel(fixture, PlaybackProgress(context), CinemaSettings(context), context) }
        try {
            wav.writeBytes(original.copyOf().also { it[44] = (it[44].toInt() xor 1).toByte() })
            assertThrows(IllegalArgumentException::class.java) { runBlocking { withContext(Dispatchers.Main) {
                vm.roomProbe!!.start(fixture, media.detail)
            } } }
            assertEquals(before, legacyHashes(context))
            LegacyHandoffController.open(context).use {
                assertEquals(2_000L, it.room { RoomPlaybackProgressStore(it.database).loadResume(HANDOFF_TARGET.workId) }!!.positionMs)
            }
        } finally {
            wav.writeBytes(original)
            withContext(Dispatchers.Main) { ViewModelStore().apply { put("handoff", vm); clear() } }
            delay(100); resetOwnedHandoffCandidate(context)
        }
    }
}
