package com.ichichuang.cinema.feature.player

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.BuildConfig
import com.ichichuang.cinema.data.settings.CinemaSettings
import com.ichichuang.cinema.data.settings.PlaybackProgress
import com.ichichuang.cinema.data.settings.WatchPoint
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class LegacyPlaybackRuntimeNativeTest {
    @Test fun probe_off_uses_original_datastore_with_existing_records_and_does_not_touch_room() = runBlocking<Unit> {
        val context: Context = ApplicationProvider.getApplicationContext()
        check(!BuildConfig.PLAYBACK_ROOM_PROBE && context.packageName == "com.ichichuang.cinema.debug")
        check(InstrumentationRegistry.getArguments().getString("cinema.disposable") == "round06")
        fun roomFiles() = listOf("", "-wal", "-shm").map { context.getDatabasePath(PROBE_DB + it) }
            .filter(File::exists).associate { it.name to MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList() }
        val before = roomFiles()
        val repository = ProbeFixtureRepository(context)
        val progress = PlaybackProgress(context)
        val key = "fixture-source#fixture-movie"
        val original = WatchPoint(0, 0, 6_000, 12_000)
        progress.save(key, original); assertEquals(original, progress.get(key))
        val vm = withContext(Dispatchers.Main) { PlayerViewModel(repository, progress, CinemaSettings(context), context) }
        try {
            assertNull(vm.roomProbe)
            withContext(Dispatchers.Main) { vm.start("fixture-movie", "合成测试", "fixture-source") }
            withTimeout(10_000) { while (withContext(Dispatchers.Main) { vm.state.value.episodes.isEmpty() }) delay(25) }
            withContext(Dispatchers.Main) {
                val source = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context)).createMediaSource(vm.player.currentMediaItem!!)
                vm.player.setMediaSource(source, 4_000); vm.player.prepare(); vm.player.play()
            }
            withTimeout(10_000) { while (withContext(Dispatchers.Main) {
                vm.player.playbackState != Player.STATE_READY || !vm.player.isPlaying || vm.player.currentPosition < 4_050
            }) delay(25) }
            withContext(Dispatchers.Main) { vm.pauseAndSave() }
            withTimeout(10_000) { while (progress.get(key)?.positionMs?.let { it in 4_000..5_000 } != true) delay(25) }
            assertEquals(0, progress.get(key)!!.episodeIndex)
            assertEquals(12_000L, progress.get(key)!!.durationMs)
            assertEquals(before, roomFiles())
            println("ROUND06_LEGACY_ONLY existingRecord=true stored=${progress.get(key)!!.positionMs} roomUnchanged=true")
        } finally { withContext(Dispatchers.Main) { ViewModelStore().apply { put("legacy", vm); clear() } } }
    }
}
