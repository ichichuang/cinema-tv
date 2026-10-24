package com.ichichuang.cinema.feature.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.BuildConfig
import com.ichichuang.cinema.data.local.CinemaDatabaseFactory
import com.ichichuang.cinema.data.settings.CinemaSettings
import com.ichichuang.cinema.data.settings.PlaybackProgress
import com.ichichuang.cinema.data.userdata.PlaybackTarget
import com.ichichuang.cinema.data.userdata.RoomPlaybackProgressStore
import com.ichichuang.cinema.domain.model.*
import com.ichichuang.cinema.domain.model.catalog.*
import com.ichichuang.cinema.domain.model.userdata.*
import com.ichichuang.cinema.domain.repository.MovieRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal const val PROBE_DB = "round06-playback-probe.db"
internal const val AUTH_FILE = "round06-playback-test.authorization"
internal const val AUTH_TEXT = "cinema-round06-disposable-local-media"

/** No network or downloaded fixture: 12 seconds of generated PCM silence (8 kHz, mono, 16 bit). */
internal fun syntheticWav(file: File) {
    if (file.exists()) return
    val bytes = 12 * 8_000 * 2
    val out = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN)
    out.put("RIFF".toByteArray()).putInt(36 + bytes).put("WAVEfmt ".toByteArray()).putInt(16)
    out.putShort(1).putShort(1).putInt(8_000).putInt(16_000).putShort(2).putShort(16)
    out.put("data".toByteArray()).putInt(bytes)
    file.writeBytes(out.array())
}
internal class ProbeFixtureRepository(context: Context) : MovieRepository, PlaybackProbeCatalog {
    private val files = listOf("round06-a.wav", "round06-b.wav").map { File(context.filesDir, it).also(::syntheticWav) }
    val targets = listOf("fixture-episode-a", "fixture-episode-b").map {
        PlaybackTarget(WorkId("fixture-work-r06"), EpisodeId(it), VariantId("fixture-pcm-cut"))
    }
    val detail = MovieDetail(Movie("fixture-movie", "合成测试", 0, 0.0, "", 0, "", origin = "fixture-source"),
        lines = listOf(PlayLine("合成线路", files.mapIndexed { i, f -> Episode("合成集${i + 1}", f.toURI().toString()) })))
    override val probeMedia = targets.mapIndexed { i, t -> ProbeMedia(detail, 0, i, t) }
    override val config = flowOf("fixture-source")
    override suspend fun detail(id: String, origin: String) = detail.takeIf { id == "fixture-movie" && origin == "fixture-source" }
    override suspend fun categoryTree(): CategoryTree = error("未使用")
    override suspend fun movies(filter: MovieFilter, page: Int, pageSize: Int): MoviePage = error("未使用")
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackRuntimeNativeTest {
    private lateinit var context: Context
    private lateinit var repository: ProbeFixtureRepository
    private var vm: PlayerViewModel? = null
    @Before fun authorizeOnlyThisDisposableInstrumentationProfile() {
        context = ApplicationProvider.getApplicationContext()
        check(context.packageName == "com.ichichuang.cinema.debug")
        check(BuildConfig.PLAYBACK_ROOM_PROBE)
        check(InstrumentationRegistry.getArguments().getString("cinema.disposable") == "round06")
        check(!File(context.filesDir, "datastore/cinema_progress.preferences_pb").exists())
        File(context.noBackupFilesDir, AUTH_FILE).writeText(AUTH_TEXT)
        context.deleteDatabase(PROBE_DB)
        repository = ProbeFixtureRepository(context)
    }
    private suspend fun launchVm(): PlayerViewModel = withContext(Dispatchers.Main) {
        PlayerViewModel(repository, PlaybackProgress(context), CinemaSettings(context), context).also {
            vm = it; it.start("fixture-movie", "合成测试", "fixture-source")
        }
    }
    private suspend fun until(timeout: Long = 10_000, condition: suspend () -> Boolean) = withTimeout(timeout) {
        while (!condition()) delay(25)
    }
    private suspend fun readResume(): PlaybackSnapshot? {
        val db = CinemaDatabaseFactory.open(context, PROBE_DB)
        return try { RoomPlaybackProgressStore(db).loadResume(repository.targets[0].workId) } finally { db.close() }
    }
    private suspend fun ready(v: PlayerViewModel) = until { withContext(Dispatchers.Main) {
        v.player.playbackState == androidx.media3.common.Player.STATE_READY
    } }
    private suspend fun playAndQualify(v: PlayerViewModel) {
        ready(v); withContext(Dispatchers.Main) { if (!v.player.isPlaying) v.togglePlay() }
        until { withContext(Dispatchers.Main) { v.player.isPlaying } }
        until { readResume()?.watchState == WatchState.IN_PROGRESS }
    }
    @After fun closeOnlySyntheticResources() = runBlocking<Unit> {
        withContext(Dispatchers.Main) { vm?.let {
            // ViewModelStore.clear owns the real onCleared/viewModelScope cancellation.
            val store = androidx.lifecycle.ViewModelStore(); store.put("probe", it); store.clear()
        } }
        delay(100)
        File(context.noBackupFilesDir, AUTH_FILE).delete()
        context.deleteDatabase(PROBE_DB)
    }
    @Test fun current_player_retry_retains_nonzero_position_instead_of_restarting_at_zero() = runBlocking<Unit> {
        val v = launchVm(); delay(300)
        // Characterize the old ViewModel too: provide the local source directly before retry().
        withContext(Dispatchers.Main) {
            val source = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context))
                .createMediaSource(v.player.currentMediaItem ?: MediaItem.fromUri(repository.detail.lines[0].episodes[0].url))
            v.player.setMediaSource(source, 4_000); v.player.prepare(); v.player.play()
        }
        ready(v); until { withContext(Dispatchers.Main) { v.player.currentPosition >= 4_100 } }
        withContext(Dispatchers.Main) { v.pauseAndSave() }
        delay(200)
        withContext(Dispatchers.Main) { v.retry() }
        delay(300)
        assertTrue("retry must not reset a valid position", withContext(Dispatchers.Main) { v.player.currentPosition >= 4_000 })
    }
    @Test fun actual_media3_pause_seek_backwards_zero_rewatch_and_file_room_reopen() = runBlocking<Unit> {
        val v = launchVm(); ready(v)
        assertFalse(withContext(Dispatchers.Main) { v.player.playWhenReady })
        assertNull(readResume())
        playAndQualify(v)
        withContext(Dispatchers.Main) { v.player.seekTo(2_000); v.player.pause() }
        until { readResume()?.positionMs?.let { it in 2_000..2_500 } == true }
        val saved = readResume()!!
        assertEquals(repository.targets[0].episodeId, saved.episodeId)
        withContext(Dispatchers.Main) { v.player.seekTo(0) }
        until { readResume()?.positionMs == 0L }
        assertEquals(WatchState.IN_PROGRESS, readResume()!!.watchState)
        assertFalse(File(context.filesDir, "datastore/cinema_progress.preferences_pb").exists())
    }
    @Test fun episode_switch_and_late_callback_keep_new_work_resume_pointer() = runBlocking<Unit> {
        val v = launchVm(); playAndQualify(v)
        val oldId = withContext(Dispatchers.Main) { v.player.currentMediaItem!!.mediaId }
        withContext(Dispatchers.Main) { v.selectEpisode(1) }
        until { withContext(Dispatchers.Main) { v.state.value.episodeIndex == 1 } }
        ready(v); playAndQualify(v)
        until { readResume()?.episodeId == repository.targets[1].episodeId }
        val newer = readResume()!!
        withContext(Dispatchers.Main) { v.roomProbe!!.observe(RuntimeObservation(oldId, android.os.SystemClock.elapsedRealtime(),
            RuntimeState.READY, true, 11_000, 12_000, RuntimeIntent.SEEK)) }
        delay(100)
        assertEquals(repository.targets[1].episodeId, readResume()!!.episodeId)
        val db = CinemaDatabaseFactory.open(context, PROBE_DB)
        try {
            val store = RoomPlaybackProgressStore(db)
            assertEquals(CommitResult.RejectedStale, store.commit(newer.copy(positionMs = 0)))
        } finally { db.close() }
        withContext(Dispatchers.Main) { v.selectEpisode(0) }
        ready(v); withContext(Dispatchers.Main) { v.pauseAndSave() }
        // An unplayed restored episode cannot steal the work-level pointer.
        delay(200); assertEquals(repository.targets[1].episodeId, readResume()!!.episodeId)
    }
    @Test fun real_room_failure_blocks_switch_and_retry_preserves_old_episode() = runBlocking<Unit> {
        val v = launchVm(); playAndQualify(v)
        withContext(Dispatchers.Main) { v.roomProbe!!.boundary() }
        val db = CinemaDatabaseFactory.open(context, PROBE_DB)
        try {
            withContext(Dispatchers.IO) { db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER round06_fail BEFORE INSERT ON playback_resume BEGIN SELECT RAISE(ABORT, 'synthetic'); END") }
            // A pause callback may commit after the explicit boundary's acknowledgement. Freeze
            // writes first, then capture the real pre-failure baseline; compare all fields below.
            val before = readResume()!!
            withContext(Dispatchers.Main) {
                v.selectEpisode(1)
                assertEquals(CommitResult.Failed("storage_sql"), v.roomProbe!!.boundary())
            }
            until { withContext(Dispatchers.Main) { v.state.value.hint?.contains("未保存") == true } }
            assertEquals(0, withContext(Dispatchers.Main) { v.state.value.episodeIndex })
            assertEquals(before, readResume())
            withContext(Dispatchers.IO) { db.openHelper.writableDatabase.execSQL("DROP TRIGGER round06_fail") }
            withContext(Dispatchers.Main) { v.selectEpisode(1) }
            until { withContext(Dispatchers.Main) { v.state.value.episodeIndex == 1 } }
        } finally { db.close() }
    }
    @Test fun background_pause_during_blocked_switch_and_retry_cancels_pending_autoplay() = runBlocking<Unit> {
        val v = launchVm(); playAndQualify(v)
        val db = CinemaDatabaseFactory.open(context, PROBE_DB)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val lock = launch(executor) {
            val sql = db.openHelper.writableDatabase
            sql.beginTransaction()
            try { entered.complete(Unit); release.await() } finally { sql.endTransaction() }
        }
        try {
            entered.await()
            withContext(Dispatchers.Main) {
                v.selectEpisode(1)
                v.retry()
                // This is the same intent used by PlayerScreen ON_STOP/Back, while VM remains alive.
                v.pauseAndSave()
                assertEquals(0, v.state.value.episodeIndex)
            }
            release.complete(Unit); lock.join()
            until { withContext(Dispatchers.Main) { v.state.value.episodeIndex == 1 } }
            ready(v)
            withContext(Dispatchers.Main) {
                assertFalse("background pause must win over queued switch/retry", v.player.playWhenReady)
                assertFalse(v.player.isPlaying)
            }
            assertEquals(repository.targets[0].episodeId, readResume()!!.episodeId)
        } finally { release.complete(Unit); lock.join(); executor.close(); db.close() }
    }
    @Test fun loading_and_failed_retry_do_not_replace_last_committed_position_with_zero() = runBlocking<Unit> {
        val v = launchVm(); playAndQualify(v)
        withContext(Dispatchers.Main) { v.roomProbe!!.boundary() }
        val saved = readResume()!!
        val media = File(java.net.URI(repository.detail.lines[0].episodes[0].url))
        val hidden = File(media.parentFile, media.name + ".temporarily-missing")
        check(media.renameTo(hidden))
        try {
            withContext(Dispatchers.Main) { v.player.stop(); v.retry() }
            until { withContext(Dispatchers.Main) { v.player.playerError != null && v.state.value.error != null } }
            assertEquals(saved.positionMs, readResume()!!.positionMs)
        } finally { check(hidden.renameTo(media)) }
        withContext(Dispatchers.Main) { v.retry() }
        ready(v)
        assertNull("successful retry must dismiss the error", withContext(Dispatchers.Main) { v.state.value.error })
        withContext(Dispatchers.Main) { v.roomProbe!!.boundary() }
        assertTrue(readResume()!!.positionMs >= saved.positionMs)
    }
    @Test fun missing_marker_and_dirty_legacy_profile_refuse_without_touching_bytes() = runBlocking<Unit> {
        File(context.noBackupFilesDir, AUTH_FILE).delete()
        assertThrows(IllegalStateException::class.java) { runBlocking { launchVm() } }
        File(context.noBackupFilesDir, AUTH_FILE).writeText(AUTH_TEXT)
        val legacy = File(context.filesDir, "datastore/cinema_progress.preferences_pb")
        check(!legacy.exists()); legacy.parentFile!!.mkdirs(); val bytes = byteArrayOf(10, 2, 99, 5)
        try {
            legacy.writeBytes(bytes)
            assertThrows(IllegalStateException::class.java) { runBlocking { launchVm() } }
            assertArrayEquals(bytes, legacy.readBytes())
        } finally { legacy.delete() }
    }
}
