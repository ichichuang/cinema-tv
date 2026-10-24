package com.ichichuang.cinema.feature.player

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.BuildConfig
import com.ichichuang.cinema.data.local.CinemaDatabaseFactory
import com.ichichuang.cinema.data.local.codec.PlaybackSnapshotCodec
import com.ichichuang.cinema.data.settings.CinemaSettings
import com.ichichuang.cinema.data.settings.PlaybackProgress
import com.ichichuang.cinema.data.userdata.RoomPlaybackProgressStore
import com.ichichuang.cinema.domain.model.userdata.CommitResult
import com.ichichuang.cinema.domain.model.userdata.PlaybackSnapshot
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Two methods MUST run in two separate host-controlled instrumentations with force-stop in between. */
class PlaybackProcessReplayTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun authorize(): ProbeFixtureRepository {
        check(BuildConfig.PLAYBACK_ROOM_PROBE && context.packageName == "com.ichichuang.cinema.debug")
        check(InstrumentationRegistry.getArguments().getString("cinema.disposable") == "round06")
        File(context.noBackupFilesDir, AUTH_FILE).writeText(AUTH_TEXT)
        return ProbeFixtureRepository(context)
    }
    private suspend fun read(repository: ProbeFixtureRepository): PlaybackSnapshot? {
        val db = CinemaDatabaseFactory.open(context, PROBE_DB)
        return try { RoomPlaybackProgressStore(db).loadResume(repository.targets[0].workId) } finally { db.close() }
    }
    private suspend fun until(condition: suspend () -> Boolean) = withTimeout(15_000) { while (!condition()) delay(25) }
    @Test fun phase_a_commit_from_real_player() = runBlocking<Unit> {
        val repository = authorize()
        check(!context.getDatabasePath(PROBE_DB).exists()) { "Phase A requires a fresh synthetic database" }
        val vm = withContext(Dispatchers.Main) { PlayerViewModel(repository, PlaybackProgress(context), CinemaSettings(context), context).also {
            it.start("fixture-movie", "合成测试", "fixture-source")
        } }
        until { withContext(Dispatchers.Main) { vm.player.playbackState == Player.STATE_READY } }
        withContext(Dispatchers.Main) { vm.togglePlay() }
        until { read(repository) != null }
        val pausedEvent = CompletableDeferred<Unit>()
        withContext(Dispatchers.Main) {
            vm.player.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) && !player.playWhenReady) {
                        player.removeListener(this)
                        pausedEvent.complete(Unit)
                    }
                }
            })
            vm.player.pause()
        }
        withTimeout(10_000) { pausedEvent.await() }
        assertEquals(CommitResult.Committed, withContext(Dispatchers.Main) { vm.roomProbe!!.boundary() })
        val saved = read(repository)!!
        assertTrue(saved.positionMs >= 5_000)
        File(context.noBackupFilesDir, "round06-process.expected.json").writeText(PlaybackSnapshotCodec.encode(saved))
        File(context.noBackupFilesDir, "round06-process.pid").writeText(android.os.Process.myPid().toString())
        println("ROUND06_PHASE_A pid=${android.os.Process.myPid()} committed=${saved.positionMs} episode=${saved.episodeId?.value}")
        // Do not simulate a reopen here. The host ends this process before phase B.
    }
    @Test fun phase_b_new_process_restores_last_commit_paused() = runBlocking<Unit> {
        val repository = authorize()
        val previousPid = File(context.noBackupFilesDir, "round06-process.pid").readText().toInt()
        assertNotEquals(previousPid, android.os.Process.myPid())
        val expected = PlaybackSnapshotCodec.decode(File(context.noBackupFilesDir, "round06-process.expected.json").readText())
        assertEquals(expected, read(repository))
        val vm = withContext(Dispatchers.Main) { PlayerViewModel(repository, PlaybackProgress(context), CinemaSettings(context), context).also {
            it.start("fixture-movie", "合成测试", "fixture-source")
        } }
        until { withContext(Dispatchers.Main) { vm.player.playbackState == Player.STATE_READY } }
        withContext(Dispatchers.Main) {
            assertFalse(vm.player.playWhenReady); assertFalse(vm.player.isPlaying)
            assertEquals(expected.positionMs, vm.player.currentPosition)
            assertEquals("合成集1", vm.state.value.episodeName)
        }
        delay(300)
        assertEquals(expected, read(repository))
        assertFalse(File(context.filesDir, "datastore/cinema_progress.preferences_pb").exists())
        println("ROUND06_PHASE_B pid=${android.os.Process.myPid()} previous=$previousPid restored=${expected.positionMs} paused=true")
        withContext(Dispatchers.Main) { ViewModelStore().apply { put("probe", vm); clear() } }
    }
}
