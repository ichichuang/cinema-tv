package com.ichichuang.cinema.feature.home.visual

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.MainActivity
import com.ichichuang.cinema.core.navigation.DebugBrowseProbeGate
import com.ichichuang.cinema.data.userdata.LegacyHandoffRuntime
import com.ichichuang.cinema.data.userdata.RoomPersonalLibraryRepository
import com.ichichuang.cinema.data.userdata.RoomPlaybackProgressStore
import com.ichichuang.cinema.domain.model.catalog.EpisodeId
import com.ichichuang.cinema.domain.model.catalog.SourceId
import com.ichichuang.cinema.domain.model.catalog.VariantId
import com.ichichuang.cinema.domain.model.catalog.WorkId
import com.ichichuang.cinema.domain.model.userdata.PlaybackSnapshot
import com.ichichuang.cinema.data.userdata.PlaybackTarget
import com.ichichuang.cinema.domain.model.userdata.CommitResult
import com.ichichuang.cinema.domain.model.userdata.WatchState
import com.ichichuang.cinema.feature.browse.BrowseProbeFixture
import com.ichichuang.cinema.feature.browse.BrowseProbeTestHooks
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * R13-D 视觉证据：真实 AVD 渲染截图（1280x720 / 1920x1080 / 大字 / 长标题 /
 * 竖版画 / 缺图 / 未配置 / 减动效），全部来自实际 Android 屏幕，非合成拼图。
 * 截图存放在应用外部文件目录，随后由证据脚本 adb pull 导出。
 */
class VisualHomeScreenshotTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val ids = (0 until 105).map { WorkId(UUID.nameUUIDFromBytes("round11-private-$it".toByteArray()).toString()) }

    @Before fun ownedFixture() {
        VisualHomeUiNativeFixture.setup(context)
    }

    @After fun restoreEnvironment() {
        scenario?.close(); scenario = null
        VisualHomeUiNativeFixture.teardown(context)
        instrumentation.uiAutomation.executeShellCommand("wm size reset").close()
        instrumentation.uiAutomation.executeShellCommand("settings put system font_scale 1.0").close()
        instrumentation.uiAutomation.executeShellCommand("settings put global window_animation_scale 1.0").close()
        instrumentation.uiAutomation.executeShellCommand("settings put global animator_duration_scale 1.0").close()
    }

    private fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).close() }

    private fun shot(name: String) {
        // 等待界面稳定后截屏；来源为真实 Android 屏幕。
        Thread.sleep(600)
        val file = File(context.filesDir, "round13-$name.png")
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        assertTrue("截图必须落盘：$name", file.length() > 0)
    }

    private fun launchHome() {
        shell("wm size ${if (VisualHomeUiNativeFixture.wide == true) "1920x1080" else "1280x720"}")
        Thread.sleep(400)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        VisualHomeUiNativeFixture.awaitHome(scenario!!)
    }

    @Test fun home_1280x720_real_render_with_records() {
        VisualHomeUiNativeFixture.seedRecords(context, ids)
        launchHome()
        shot("home-1280x720")
    }

    @Test fun home_1920x1080_real_render() {
        VisualHomeUiNativeFixture.wide = true
        VisualHomeUiNativeFixture.seedRecords(context, ids)
        launchHome()
        shot("home-1920x1080")
    }

    @Test fun home_large_font_long_title() {
        shell("settings put system font_scale 1.3")
        Thread.sleep(400)
        VisualHomeUiNativeFixture.seedRecords(context, ids)
        launchHome()
        shot("home-large-font")
    }

    @Test fun home_unconfigured_truthful_state() {
        VisualHomeUiNativeFixture.writeFixture(context, ids, emptyWorks = true)
        launchHome()
        VisualHomeUiNativeFixture.awaitText(scenario!!, "尚未配置任何来源")
        shot("home-unconfigured")
    }

    @Test fun home_reduced_motion_keeps_focus_feedback() {
        shell("settings put global window_animation_scale 0.0")
        shell("settings put global animator_duration_scale 0.0")
        Thread.sleep(400)
        VisualHomeUiNativeFixture.seedRecords(context, ids)
        launchHome()
        // 减动效下方向键仍可达内容行，焦点反馈不依赖动画。
        VisualHomeUiNativeFixture.press(scenario!!, KeyEvent.KEYCODE_DPAD_DOWN)
        VisualHomeUiNativeFixture.press(scenario!!, KeyEvent.KEYCODE_DPAD_DOWN)
        VisualHomeUiNativeFixture.awaitText(scenario!!, "继续播放")
        shot("home-reduced-motion")
    }
}

/** 截图与 UI 测试共用的夹具：授权令牌、fixture、种子记录与等待工具。 */
internal object VisualHomeUiNativeFixture {
    var wide: Boolean? = null
    fun setup(context: Context) {
        runBlocking {
            until { BrowseProbeTestHooks.activeLeases.get() == 0 }
            File(context.noBackupFilesDir, LegacyHandoffRuntime.PROFILE).writeText(LegacyHandoffRuntime.PROFILE_TOKEN)
            File(context.noBackupFilesDir, LegacyHandoffRuntime.CONSENT).writeText(LegacyHandoffRuntime.CONSENT_TOKEN)
            File(context.noBackupFilesDir, "round06-playback-test.authorization").writeText("cinema-round06-disposable-local-media")
            context.deleteDatabase(DebugBrowseProbeGate.DATABASE)
            File(context.noBackupFilesDir, com.ichichuang.cinema.feature.personal.PersonalLibraryAuthorization.FILE)
                .writeText(com.ichichuang.cinema.feature.personal.PersonalLibraryAuthorization.TOKEN)
            File(context.noBackupFilesDir, VisualHomeAuthorization.FILE).writeText(VisualHomeAuthorization.TOKEN)
            writeFixture(context, (0 until 105).map {
                WorkId(UUID.nameUUIDFromBytes("round11-private-$it".toByteArray()).toString())
            })
        }
    }
    fun teardown(context: Context) { wide = null }

    fun writeFixture(context: Context, ids: List<WorkId>, emptyWorks: Boolean = false) {
        val query = com.ichichuang.cinema.domain.model.catalog.CatalogQuery.create("测试查询-日本恐怖1994",
            com.ichichuang.cinema.domain.model.catalog.ContentKind.MOVIE,
            com.ichichuang.cinema.domain.model.catalog.LanguageScope.NON_CHINESE,
            setOf("JP"), setOf("horror"), 1994, 1994,
            com.ichichuang.cinema.domain.model.catalog.CatalogSort.YEAR_DESCENDING, setOf(SourceId("test-source")))
        val works = org.json.JSONArray()
        if (!emptyWorks) ids.forEachIndexed { i, id ->
            val entry = org.json.JSONObject().put("id", id.value)
                .put("title", when (i) {
                    0 -> "这是一个用于大字与长标题验收的合成作品标题（合成测试）"
                    99 -> "同名合成作品"
                    else -> "合成作品${i + 1}"
                })
                .put("source", if (i == 104) "disabled-source" else "test-source")
                .put("year", if (i == 100) 1995 else 1994)
                .put("countries", org.json.JSONArray(listOf(if (i == 101) "US" else "JP")))
                .put("genres", org.json.JSONArray(listOf(if (i == 102) "drama" else "horror")))
                .put("kind", if (i == 103) "OTHER" else "MOVIE")
                .put("languages", org.json.JSONArray(listOf("ja")))
                .put("favorite", true).put("history", true)
            if (i == 1) entry.put("artwork", syntheticPortrait(context)).put("artworkShape", "portrait")
            if (i == 2) entry.put("artwork", syntheticLandscape(context)).put("artworkShape", "landscape")
            works.put(entry)
        }
        val root = org.json.JSONObject()
            .put("initial", com.ichichuang.cinema.data.local.codec.BrowseSnapshotCodec.encode(
                com.ichichuang.cinema.domain.model.userdata.BrowseSnapshot(
                    com.ichichuang.cinema.core.navigation.BrowseTopLevel.LIBRARY.contextKey(query),
                    com.ichichuang.cinema.core.navigation.BrowseTopLevel.LIBRARY.route, null, query, 1,
                    ids[99], ids[99], 0f, 1)))
            .put("alternative", com.ichichuang.cinema.data.local.codec.BrowseSnapshotCodec.encode(
                com.ichichuang.cinema.domain.model.userdata.BrowseSnapshot(
                    com.ichichuang.cinema.core.navigation.BrowseTopLevel.LIBRARY.contextKey(query),
                    com.ichichuang.cinema.core.navigation.BrowseTopLevel.LIBRARY.route, null, query, 1,
                    ids[99], ids[99], 0f, 1)))
            .put("complete", true).put("works", works).put("order", org.json.JSONArray(ids.map { it.value }))
        if (!emptyWorks) {
            val media = org.json.JSONArray()
            listOf(98, 99).forEach { i -> (1..2).forEach { episode ->
                val file = File(context.filesDir, "shot-$i-$episode.wav").also { com.ichichuang.cinema.feature.player.syntheticWav(it) }
                media.put(org.json.JSONObject().put("work", ids[i].value).put("episode", "round12-episode-$episode")
                    .put("variant", "round12-pcm-original").put("uri", file.toURI().toString()))
            } }
            root.put("media", media)
        } else root.put("media", org.json.JSONArray())
        File(context.noBackupFilesDir, BrowseProbeFixture.FILE).writeText(root.toString())
    }

    private fun syntheticPortrait(context: Context): String = syntheticPng(context, "portrait", 120, 180)
    private fun syntheticLandscape(context: Context): String = syntheticPng(context, "landscape", 320, 180)
    private fun syntheticPng(context: Context, name: String, width: Int, height: Int): String {
        val file = File(context.filesDir, "shot-art-$name.png")
        if (file.isFile) return file.toURI().toString()
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bitmap).apply {
            drawColor(android.graphics.Color.rgb(0x17, 0x1E, 0x27))
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(0xF5, 0xC6, 0x93); textSize = width / 5f; textAlign = android.graphics.Paint.Align.CENTER }
            drawText("合成测试", width / 2f, height / 2f, paint)
        }
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file.toURI().toString()
    }

    fun seedRecords(context: Context, ids: List<WorkId>) {
        runBlocking {
            DebugBrowseProbeGate.withOwnedProfile(context) { db ->
                val repository = RoomPersonalLibraryRepository(db)
                val store = RoomPlaybackProgressStore(db)
                val session = "shot-${UUID.randomUUID()}"
                val target = PlaybackTarget(ids[99], EpisodeId("round12-episode-2"), VariantId("round12-pcm-original"))
                assertEquals(CommitResult.Committed, repository.rememberCard(ids[99].value, "同名合成作品", "test-source"))
                assertEquals(CommitResult.Committed, repository.setFavorite(ids[99].value, true))
                assertEquals(CommitResult.Committed, store.beginSession(target, session))
                assertEquals(CommitResult.Committed, repository.confirmPlayback(session))
                assertEquals(CommitResult.Committed, store.commit(PlaybackSnapshot(ids[99], target.episodeId, target.variantId,
                    session, 1, 8_000, 12_000, WatchState.IN_PROGRESS, 1)))
            }
        }
    }

    fun press(scenario: ActivityScenario<MainActivity>, code: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, code))
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, code))
        instrumentation.waitForIdleSync()
    }

    fun awaitHome(scenario: ActivityScenario<MainActivity>): Boolean {
        val end = android.os.SystemClock.uptimeMillis() + 12_000
        while (true) {
            val tags = nodes(scenario)
            if (tags.any { it == "候选首页" }) return true
            if (android.os.SystemClock.uptimeMillis() > end) {
                var diagnostic = ""
                scenario.onActivity { diagnostic = "home=${it.homeProbe != null} browse=${it.browseProbe != null}" }
                error("等待候选首页超时；$diagnostic；tags=${tags.take(20)}")
            }
            Thread.sleep(25)
        }
    }
    fun awaitText(scenario: ActivityScenario<MainActivity>, contained: String) =
        until { texts(scenario).any { it.contains(contained) } }

    private fun nodes(scenario: ActivityScenario<MainActivity>): List<String> {
        var result = emptyList<String>()
        scenario.onActivity { activity ->
            result = flattenTags(activity)
        }
        return result
    }
    private fun texts(scenario: ActivityScenario<MainActivity>): List<String> {
        var result = emptyList<String>()
        scenario.onActivity { activity ->
            result = com.ichichuang.cinema.feature.home.visual.VisualHomeScreenshotTexts.of(activity)
        }
        return result
    }
    private fun flattenTags(activity: MainActivity): List<String> {
        val view = activity.window.decorView
        val nodes = com.ichichuang.cinema.feature.home.visual.VisualHomeScreenshotDump.tags(view)
        return nodes
    }

    fun until(timeout: Long = 12_000, condition: () -> Boolean) {
        val end = android.os.SystemClock.uptimeMillis() + timeout
        while (!condition()) {
            if (android.os.SystemClock.uptimeMillis() > end) error("等待超时")
            Thread.sleep(25)
        }
    }
}
