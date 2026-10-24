package com.ichichuang.cinema.data.userdata

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.BuildConfig
import com.ichichuang.cinema.data.settings.*
import com.ichichuang.cinema.domain.model.MovieFilter
import com.ichichuang.cinema.domain.repository.BrowseMemory
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

internal fun ownedHandoffContext(): Context {
    val context: Context = ApplicationProvider.getApplicationContext()
    check(context.packageName == "com.ichichuang.cinema.debug" && BuildConfig.PLAYBACK_ROOM_PROBE)
    check(InstrumentationRegistry.getArguments().getString("cinema.disposable") == "round06")
    check(InstrumentationRegistry.getArguments().getString("cinema.handoff") == "round07")
    val profile = File(context.noBackupFilesDir, LegacyHandoffRuntime.PROFILE)
    if (!profile.exists()) {
        val files = File(context.filesDir, "datastore").listFiles().orEmpty()
        check(files.none { f -> listOf("cinema_progress", "cinema_browse", "cinema_filters", "cinema_settings").any(f.name::startsWith) })
        profile.writeText(LegacyHandoffRuntime.PROFILE_TOKEN)
    }
    check(profile.readText() == LegacyHandoffRuntime.PROFILE_TOKEN)
    File(context.noBackupFilesDir, "round06-playback-test.authorization").writeText("cinema-round06-disposable-local-media")
    File(context.noBackupFilesDir, LegacyHandoffRuntime.CONSENT).writeText(LegacyHandoffRuntime.CONSENT_TOKEN)
    return context
}

internal suspend fun seedOwnedLegacy(context: Context) {
    PlaybackProgress(context).save("fixture-source#fixture-movie", WatchPoint(0, 0, 2_000, 12_000))
    DataStoreBrowseStore(context).save(BrowseMemory("fixture-source#fixture-movie", 9001))
    DataStoreFilterStore(context).save(MovieFilter(lang = "", area = "原始中文", year = "1994", types = listOf("剧情", "动作")))
    CinemaSettings(context).rememberSource("合成来源", "fixture-source")
}

internal fun resetOwnedHandoffCandidate(context: Context) {
    check(File(context.noBackupFilesDir, LegacyHandoffRuntime.PROFILE).readText() == LegacyHandoffRuntime.PROFILE_TOKEN)
    check(InstrumentationRegistry.getArguments().getString("cinema.handoff") == "round07")
    listOf("", ".bak", ".new").forEach { File(context.noBackupFilesDir, LegacyHandoffRuntime.REQUIRED + it).delete() }
    context.deleteDatabase(LegacyHandoffRuntime.DATABASE)
}

class LegacyHandoffGateNativeTest {
    private lateinit var context: Context
    @Before fun setup() = runBlocking<Unit> { context = ownedHandoffContext(); resetOwnedHandoffCandidate(context); seedOwnedLegacy(context) }
    @After fun close() {
        File(context.noBackupFilesDir, LegacyHandoffRuntime.REQUIRED).delete()
        File(context.noBackupFilesDir, LegacyHandoffRuntime.CONSENT).delete()
    }
    @Test fun explicit_test_profile_can_be_audited_without_relaxing_round06_dirty_refusal() {
        assertTrue(LegacyHandoffRuntime.authorized(context))
        assertThrows(IllegalStateException::class.java) {
            com.ichichuang.cinema.feature.player.PlaybackProbeAuthorization.mode(context)
        }
    }
    @Test fun singleton_capture_includes_exact_progress_browse_filter_and_saved_source_types() = runBlocking<Unit> {
        val snapshot = LegacyHandoffRuntime.capture(context)
        assertEquals("0,0,2000,12000", snapshot.entries.single { it.key == "wp_fixture-source#fixture-movie" }.value.stringOrNull())
        assertEquals("int", snapshot.entries.single { it.key == "last_index" }.value.type)
        assertEquals("", snapshot.entries.single { it.key == "f_lang" }.value.stringOrNull())
        assertTrue(snapshot.entries.any { it.store.tag == "settings" && it.key == "saved_sources" && it.value.stringOrNull()!!.contains("合成来源") })
    }
    @Test fun missing_consent_never_authorizes_migration() {
        File(context.noBackupFilesDir, LegacyHandoffRuntime.CONSENT).delete()
        assertFalse(LegacyHandoffRuntime.authorized(context))
    }
    @Test fun durable_room_requirement_without_authority_refuses_all_legacy_writers() = runBlocking<Unit> {
        File(context.noBackupFilesDir, LegacyHandoffRuntime.REQUIRED).writeText("synthetic-missing-authority")
        val before = PlaybackProgress(context).get("fixture-source#fixture-movie")
        assertThrows(IllegalStateException::class.java) { runBlocking {
            PlaybackProgress(context).save("fixture-source#fixture-movie", WatchPoint(0, 0, 0, 0))
        } }
        assertEquals(before, PlaybackProgress(context).get("fixture-source#fixture-movie"))
        assertThrows(IllegalStateException::class.java) { runBlocking { DataStoreBrowseStore(context).save(BrowseMemory(null, 0)) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { DataStoreFilterStore(context).save(MovieFilter()) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { CinemaSettings(context).setSeekStep(30_000) } }
    }
}
