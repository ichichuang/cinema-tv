package com.ichichuang.cinema.feature.home.visual

import com.ichichuang.cinema.feature.browse.*
import com.ichichuang.cinema.feature.player.syntheticWav
import com.ichichuang.cinema.data.userdata.*
import com.ichichuang.cinema.domain.model.userdata.*

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.MainActivity
import com.ichichuang.cinema.BuildConfig
import com.ichichuang.cinema.core.navigation.*
import com.ichichuang.cinema.data.local.codec.BrowseSnapshotCodec
import com.ichichuang.cinema.domain.model.catalog.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

/** R13 首页视觉切片的真实原生 UI 测试；仅运行于五授权全开的 Debug/DebugAndroidTest 配对。 */
class VisualHomeUiNativeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val ids = (0 until 105).map { WorkId(UUID.nameUUIDFromBytes("round11-private-$it".toByteArray()).toString()) }
    private val query = CatalogQuery.create("测试查询-日本恐怖1994", ContentKind.MOVIE, LanguageScope.NON_CHINESE,
        setOf("JP"), setOf("horror"), 1994, 1994, CatalogSort.YEAR_DESCENDING, setOf(SourceId("test-source")))
    private fun snapshot(top: BrowseTopLevel = BrowseTopLevel.LIBRARY, index: Int = 99) = BrowseSnapshot(
        top.contextKey(query), top.route, null, query, 1, ids[index], ids[(index / 5) * 5], 0f, 1)

    @Before fun ownedPrivateFixture() {
        check(BuildConfig.BROWSE_RESTORE_PROBE && BuildConfig.PLAYBACK_ROOM_PROBE)
        check(InstrumentationRegistry.getArguments().getString("cinema.browse") == "round11")
        check(InstrumentationRegistry.getArguments().getString("cinema.disposable") == "round06")
        check(InstrumentationRegistry.getArguments().getString("cinema.handoff") == "round07")
        until { BrowseProbeTestHooks.activeLeases.get() == 0 && VisualHomeTestHooks.activeLeases.get() == 0 }
        File(context.noBackupFilesDir, LegacyHandoffRuntime.PROFILE).writeText(LegacyHandoffRuntime.PROFILE_TOKEN)
        File(context.noBackupFilesDir, LegacyHandoffRuntime.CONSENT).writeText(LegacyHandoffRuntime.CONSENT_TOKEN)
        File(context.noBackupFilesDir, "round06-playback-test.authorization").writeText("cinema-round06-disposable-local-media")
        check(!LegacyHandoffRuntime.present(context))
        context.deleteDatabase(DebugBrowseProbeGate.DATABASE)
        BrowseProbeTestHooks.holdWindowReady = false; BrowseProbeTestHooks.disableWorkFocus = false
        BrowseProbeTestHooks.failedFocusRequests.set(0); BrowseProbeTestHooks.focusTrace.clear()
        BrowseProbeTestHooks.beforeLoad = null; BrowseProbeTestHooks.beforeTopLevelLoad = null; BrowseProbeTestHooks.blockPlacement = false
        check(BuildConfig.PERSONAL_LIBRARY_PROBE)
        check(InstrumentationRegistry.getArguments().getString("cinema.personal") == "round12")
        File(context.noBackupFilesDir, com.ichichuang.cinema.feature.personal.PersonalLibraryAuthorization.FILE)
            .writeText(com.ichichuang.cinema.feature.personal.PersonalLibraryAuthorization.TOKEN)
        com.ichichuang.cinema.feature.personal.PersonalLibraryTestHooks.beforeProjection = null
        com.ichichuang.cinema.feature.personal.PersonalLibraryTestHooks.projectionDeliveries.set(0)
        com.ichichuang.cinema.feature.personal.PersonalLibraryTestHooks.lostReceiptAcknowledgements.set(0)
        check(BuildConfig.HOME_VISUAL_PROBE)
        check(InstrumentationRegistry.getArguments().getString("cinema.home") == "round13")
        File(context.noBackupFilesDir, VisualHomeAuthorization.FILE).writeText(VisualHomeAuthorization.TOKEN)
        VisualHomeTestHooks.holdWindowReady = false; VisualHomeTestHooks.disableCardFocus = false
        VisualHomeTestHooks.blockPlacement = false; VisualHomeTestHooks.failedFocusRequests.set(0)
        VisualHomeTestHooks.focusTrace.clear(); VisualHomeTestHooks.beforeLoad = null
        writeFixture()
    }

    /** 合成测试图：仅测试代码生成，画面带“合成测试”水印，绝不进入生产目录。 */
    private fun syntheticArt(name: String, width: Int, height: Int): String {
        val file = File(context.filesDir, "round13-$name.png")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(0x17, 0x1E, 0x27))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xF5, 0xC6, 0x93); textSize = width / 5f; textAlign = Paint.Align.CENTER }
            drawText("合成测试", width / 2f, height / 2f, paint)
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file.toURI().toString()
    }

    private fun writeFixture(complete: Boolean = true, emptyWorks: Boolean = false) {
        val works = JSONArray()
        if (!emptyWorks) ids.forEachIndexed { i, id ->
            works.put(JSONObject().put("id", id.value)
                .put("title", if (i == 0 || i == 99) "同名合成作品" else "合成作品${i + 1}")
                .put("source", if (i == 104) "disabled-source" else "test-source")
                .put("year", if (i == 100) 1995 else 1994)
                .put("countries", JSONArray(listOf(if (i == 101) "US" else "JP")))
                .put("genres", JSONArray(listOf(if (i == 102) "drama" else "horror")))
                .put("kind", if (i == 103) "OTHER" else "MOVIE")
                .put("languages", JSONArray(listOf("ja"))).put("favorite", true).put("history", true)
                .put("artwork", when (i) {
                    0 -> syntheticArt("landscape-0", 320, 180)
                    1 -> syntheticArt("portrait-1", 120, 180)
                    2 -> syntheticArt("landscape-2", 320, 180)
                    else -> JSONObject.NULL
                })
                .put("artworkShape", when (i) { 1 -> "portrait"; 0, 2 -> "landscape"; else -> JSONObject.NULL }))
        }
        val root = JSONObject().put("initial", BrowseSnapshotCodec.encode(snapshot()))
            .put("alternative", BrowseSnapshotCodec.encode(snapshot().copy(
                contextKey = BrowseTopLevel.LIBRARY.contextKey(alternateQuery()), query = alternateQuery(), focusedWorkId = ids[100])))
            .put("complete", complete).put("works", works).put("order", JSONArray(ids.map { it.value }))
        if (!emptyWorks) {
            val media = JSONArray()
            listOf(98, 99).forEach { i -> (1..2).forEach { episode ->
                val file = File(context.filesDir, "round12-$i-$episode.wav").also(::syntheticWav)
                media.put(JSONObject().put("work", ids[i].value).put("episode", "round12-episode-$episode")
                    .put("variant", "round12-pcm-original").put("uri", file.toURI().toString()))
            } }
            root.put("media", media)
        } else root.put("media", JSONArray())
        File(context.noBackupFilesDir, BrowseProbeFixture.FILE).writeText(root.toString())
    }
    private fun alternateQuery() = CatalogQuery.create("测试查询-日本恐怖1995", ContentKind.MOVIE, LanguageScope.NON_CHINESE,
        setOf("JP"), setOf("horror"), 1995, 1995, CatalogSort.YEAR_ASCENDING, setOf(SourceId("test-source")))

    private fun nodes(view: View): List<SemanticsNode> {
        fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        if (view is ViewRootForTest) return flatten(view.semanticsOwner.unmergedRootSemanticsNode)
        return if (view is ViewGroup) (0 until view.childCount).flatMap { nodes(view.getChildAt(it)) } else emptyList()
    }
    private data class Node(val tag: String, val focused: Boolean, val selected: Boolean, val text: String,
        val description: String)
    private fun semantics(): List<Node> {
        var result = emptyList<Node>()
        scenario?.onActivity { result = nodes(it.window.decorView).map { node -> Node(
            node.config.getOrElse(SemanticsProperties.TestTag) { "" },
            node.config.getOrElse(SemanticsProperties.Focused) { false },
            node.config.getOrElse(SemanticsProperties.Selected) { false },
            node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString("") { it.text },
            node.config.joinToString("|") { "${it.key.name}=${it.value}" }) } }
        return result
    }
    private fun until(timeout: Long = 12_000, condition: () -> Boolean) {
        val end = android.os.SystemClock.uptimeMillis() + timeout
        while (!condition()) { if (android.os.SystemClock.uptimeMillis() > end) {
            var diagnostic = ""
            scenario?.onActivity {
                val home = it.homeProbe
                diagnostic = "窗口=${it.window.decorView.hasWindowFocus()} home=${it.homeProbe != null} browse=${it.browseProbe != null}" +
                    (home?.let { h -> " screen=${h.screen} focused=${h.focusedControl} user=${h.userTarget} control=${h.controlTarget} token=${h.restoreToken != null} top=${h.browse.top} busy=${h.browse.busy} detail=${h.browse.isDetail} status=${h.browse.status} trace=${VisualHomeTestHooks.focusTrace.toList().takeLast(40)}" } ?: "")
            }
            fail("等待实际原生状态超时；$diagnostic；${if (scenario != null) semantics().take(40) else "无页面"}")
        }; Thread.sleep(25) }
    }
    private fun launch(waitNavFocus: Boolean = true) {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        until { semantics().any { it.tag == "候选首页" } }
        if (waitNavFocus) focus("nav:首页")
    }
    private fun home(action: (VisualHomeRuntime) -> Unit) { scenario!!.onActivity { action(requireNotNull(it.homeProbe)) } }
    private fun runtime(action: (BrowseProbeRuntime) -> Unit) { scenario!!.onActivity { action(requireNotNull(it.browseProbe)) } }
    private fun focus(tag: String) = until { semantics().any { it.tag == tag && it.focused } }
    private fun key(code: Int) {
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, code))
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, code))
        instrumentation.waitForIdleSync()
    }
    private fun assertUniqueFocus() { assertEquals(1, semantics().count { it.focused }) }
    private fun nav(label: String) {
        repeat(6) { key(KeyEvent.KEYCODE_DPAD_UP) }
        until { semantics().any { it.focused && it.tag.startsWith("nav:") } }
        val order = VisualHomeRuntime.NAV_ITEMS
        var steps = 0
        while (steps < 8) {
            val current = semantics().firstOrNull { it.focused && it.tag.startsWith("nav:") }?.tag
            if (current == "nav:$label") break
            val currentIndex = order.indexOf(current?.removePrefix("nav:"))
            check(currentIndex >= 0) { "导航行未聚焦：$current" }
            val targetIndex = order.indexOf(label)
            key(if (targetIndex > currentIndex) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
            until(2_000) { semantics().firstOrNull { it.focused && it.tag.startsWith("nav:") }?.tag != current }
            steps++
        }
        focus("nav:$label")
    }
    /** 设置列表行走：每次按键后等待焦点真实变化（键连发时焦点回报可能滞后）。 */
    private fun settingsItem(label: String) {
        var steps = 0
        while (steps < 8) {
            val current = semantics().firstOrNull { it.focused }?.tag
            if (current != null && current.startsWith(label)) break
            key(KeyEvent.KEYCODE_DPAD_DOWN)
            until(2_000) { semantics().firstOrNull { it.focused }?.tag != current }
            steps++
        }
        until { semantics().any { it.focused && it.tag.startsWith(label) } }
    }
    private fun cardTag(row: String, index: Int) = VisualHomeRuntime.cardKey(row, ids[index].value)
    private fun favorite(index: Int) = runBlocking {
        DebugBrowseProbeGate.withOwnedProfile(context) { db ->
            val repository = RoomPersonalLibraryRepository(db)
            assertEquals(CommitResult.Committed, repository.rememberCard(ids[index].value, "合成作品${index + 1}", "test-source"))
            assertEquals(CommitResult.Committed, repository.setFavorite(ids[index].value, true))
        }
    }
    private fun watched(index: Int = 99, position: Long = 8_000, variant: String = "round12-pcm-original") = runBlocking {
        DebugBrowseProbeGate.withOwnedProfile(context) { db ->
            val repository = RoomPersonalLibraryRepository(db)
            val store = RoomPlaybackProgressStore(db)
            val session = "round13-ui-${UUID.randomUUID()}"
            val target = PlaybackTarget(ids[index], EpisodeId("round12-episode-2"), VariantId(variant))
            assertEquals(CommitResult.Committed, repository.rememberCard(ids[index].value, "合成作品${index + 1}", "test-source"))
            assertEquals(CommitResult.Committed, store.beginSession(target, session))
            assertEquals(CommitResult.Committed, repository.confirmPlayback(session))
            assertEquals(CommitResult.Committed, store.commit(PlaybackSnapshot(ids[index], target.episodeId, target.variantId,
                session, 1, position, 12_000, WatchState.IN_PROGRESS, 1)))
        }
    }
    private fun favoriteIds(): List<String> {
        var repository: RoomPersonalLibraryRepository? = null
        runtime { repository = it.personal!!.repository }
        return runBlocking { repository!!.favorites().map { it.workId } }
    }
    private fun action(label: String) {
        repeat(12) {
            until {
                val actual = semantics().firstOrNull { it.focused }?.tag
                var ready = false
                runtime { ready = !it.busy && !it.personal!!.actionBusy && actual == it.personal!!.detailFocus && actual in it.detailActions() }
                ready
            }
            val current = semantics().firstOrNull { it.focused }?.tag
            if (current == label) return@repeat
            var actions = emptyList<String>()
            runtime { actions = it.detailActions() }
            val position = actions.indexOf(current)
            val destination = actions.indexOf(label)
            check(destination >= 0) { "当前页面没有动作 $label：$actions" }
            val direction = if (position > destination) -1 else 1
            key(if (direction < 0) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN)
            focus(actions[(position + direction).coerceIn(0, actions.lastIndex)])
        }
        focus(label); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var busy = false; runtime { busy = it.personal!!.actionBusy }; !busy }
    }

    @After fun closeOwnedActivity() {
        com.ichichuang.cinema.feature.personal.PersonalLibraryTestHooks.beforeProjection?.complete(Unit)
        com.ichichuang.cinema.feature.personal.PersonalLibraryTestHooks.beforeProjection = null
        BrowseProbeTestHooks.beforeSave?.complete(Unit); BrowseProbeTestHooks.beforeSave = null
        VisualHomeTestHooks.beforeLoad?.complete(Unit); VisualHomeTestHooks.beforeLoad = null
        scenario?.close(); scenario = null
        until { BrowseProbeTestHooks.activeLeases.get() == 0 && VisualHomeTestHooks.activeLeases.get() == 0 }
        BrowseProbeTestHooks.holdWindowReady = false; VisualHomeTestHooks.holdWindowReady = false
        VisualHomeTestHooks.disableCardFocus = false; VisualHomeTestHooks.blockPlacement = false
    }

    @Test fun authorized_profile_opens_home_root_with_five_destinations() {
        launch()
        listOf("首页", "片库", "我的收藏", "最近观看", "设置").forEach { label ->
            assertTrue("导航缺少 $label", semantics().any { it.tag == "nav:$label" })
        }
        assertTrue(semantics().any { it.tag == "首页状态" })
        home { assertEquals(HomeRootScreen.HOME, it.screen) }
        assertUniqueFocus()
    }

    @Test fun home_probe_requires_owned_profile_token() {
        File(context.noBackupFilesDir, VisualHomeAuthorization.FILE).delete()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        until { semantics().any { it.tag == "浏览测试不可用" } }
        assertFalse("缺少首页授权不能创建数据库", context.getDatabasePath(DebugBrowseProbeGate.DATABASE).exists())
    }

    @Test fun unconfigured_catalog_shows_truthful_state_without_fake_posters() {
        writeFixture(emptyWorks = true)
        launch()
        until { semantics().any { it.text.contains("尚未配置任何来源") } }
        assertTrue(semantics().any { it.tag == "action:前往设置" })
        assertFalse("未配置来源不得出现目录卡", semantics().any { it.tag.startsWith("片库/") })
        home { assertEquals(HomeContentStatus.UNCONFIGURED, it.display.contentStatus) }
    }

    @Test fun unconfigured_catalog_retains_personal_records() {
        writeFixture(emptyWorks = true)
        favorite(99); watched(99)
        launch()
        until { semantics().any { it.tag == cardTag(VisualHomeRuntime.RECENT_ROW, 99) } }
        // 卡节点的内容描述聚合已保存标题（R12 记录以保存时的标题为准）与已确认进度。
        val card = semantics().first { it.tag == cardTag(VisualHomeRuntime.RECENT_ROW, 99) }
        assertTrue("续播记录必须保留真实标题", card.description.contains("合成作品100"))
        assertTrue("续播进度来自已确认检查点", semantics().any { it.text.contains("0:08") })
        home { assertEquals(HomeContentStatus.UNCONFIGURED, it.display.contentStatus); assertTrue(it.display.continueCards.isNotEmpty()) }
    }

    @Test fun offline_home_retains_saved_personal_rows() {
        watched(99)
        // UiAutomation 以仪器权限关闭模拟器网络；宿主网络不受影响。
        instrumentation.uiAutomation.executeShellCommand("svc wifi disable").close()
        instrumentation.uiAutomation.executeShellCommand("svc data disable").close()
        try {
            Thread.sleep(4_000) // 等待系统真正拆掉默认网络再启动
            launch()
            until(20_000) { semantics().any { it.text.contains("离线状态") } }
            home {
                assertEquals(HomeContentStatus.OFFLINE, it.display.contentStatus)
                assertTrue(it.display.continueCards.isNotEmpty())
                assertEquals(listOf(ids[99].value), it.display.continueCards.map { c -> c.workId })
            }
        } finally {
            instrumentation.uiAutomation.executeShellCommand("svc wifi enable").close()
            instrumentation.uiAutomation.executeShellCommand("svc data enable").close()
        }
    }

    @Test fun continue_first_when_real_committed_record_exists() {
        watched(99)
        launch()
        assertTrue("有真实续播记录时必须有首行续播区", semantics().any { it.tag == VisualHomeRuntime.HERO_RESUME })
        assertTrue(semantics().any { it.tag == VisualHomeRuntime.HERO_DETAIL })
        home { assertEquals(ids[99].value, it.display.continueCards.firstOrNull()?.workId) }
    }

    @Test fun no_continue_hides_resume_hero() {
        launch()
        assertFalse("无检查点不得出现续播首行", semantics().any { it.tag == VisualHomeRuntime.HERO_RESUME })
        assertFalse(semantics().any { it.tag == VisualHomeRuntime.HERO_DETAIL })
        home { assertTrue(it.display.continueCards.isEmpty()) }
    }

    @Test fun browsing_never_creates_watch_history() {
        launch()
        key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_DOWN)
        focus(cardTag(VisualHomeRuntime.CATALOG_ROW, 0))
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选详情" } }
        key(KeyEvent.KEYCODE_BACK)
        until { semantics().any { it.tag == "候选首页" } }
        home { assertTrue("仅浏览不得产生观看历史", it.browse.personal!!.history.isEmpty()) }
    }

    @Test fun all_five_top_destinations_reachable_by_dpad() {
        launch()
        nav("片库"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选浏览" } }
        waitTop(BrowseTopLevel.LIBRARY)
        key(KeyEvent.KEYCODE_BACK); until { semantics().any { it.tag == "候选首页" } }; focus("nav:片库")
        nav("我的收藏"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选浏览" } }
        waitTop(BrowseTopLevel.FAVORITES)
        key(KeyEvent.KEYCODE_BACK); until { semantics().any { it.tag == "候选首页" } }
        nav("最近观看"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选浏览" } }
        waitTop(BrowseTopLevel.HISTORY)
        key(KeyEvent.KEYCODE_BACK); until { semantics().any { it.tag == "候选首页" } }
        nav("设置"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选设置" } }
        key(KeyEvent.KEYCODE_BACK); until { semantics().any { it.tag == "候选首页" } }
        home { assertEquals(HomeRootScreen.HOME, it.screen) }
    }
    private fun waitTop(top: BrowseTopLevel) = until { var ok = false; runtime { ok = it.top == top && !it.busy }; ok }

    @Test fun home_detail_back_restores_work_and_anchor() {
        launch()
        key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_DOWN)
        focus(cardTag(VisualHomeRuntime.CATALOG_ROW, 0))
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选详情" } }
        runtime { assertTrue(it.isDetail) }
        key(KeyEvent.KEYCODE_BACK)
        until { semantics().any { it.tag == "候选首页" } }
        until { semantics().any { it.focused && it.tag == cardTag(VisualHomeRuntime.CATALOG_ROW, 0) } }
        home { assertEquals(HomeRootScreen.HOME, it.screen) }
    }

    @Test fun home_to_library_preserves_strict_query() {
        launch()
        nav("片库"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选浏览" } }
        runtime {
            assertEquals(BrowseTopLevel.LIBRARY, it.top)
            assertEquals(query, it.navigation!!.snapshot.query)
            assertEquals(1994, it.navigation!!.snapshot.query?.yearStart)
        }
    }

    @Test fun one_ok_press_does_not_double_navigate() {
        launch()
        key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_DOWN)
        focus(cardTag(VisualHomeRuntime.CATALOG_ROW, 0))
        key(KeyEvent.KEYCODE_DPAD_CENTER); key(KeyEvent.KEYCODE_DPAD_CENTER)
        // R11 规则：迟到确认取消未执行的导航或落在详情动作上；两种结果都不得叠加第二层详情。
        until { var ready = false; runtime { ready = !it.busy }; ready }
        runtime { assertTrue("连续确认不得叠加详情层", it.navigation!!.returnChain.size <= 1) }
        if (runtime2detail()) { key(KeyEvent.KEYCODE_BACK) }
        until { semantics().any { it.tag == "候选首页" } }
    }
    private fun runtime2detail(): Boolean {
        var ok = false; runtime { ok = it.isDetail }; return ok
    }

    @Test fun home_resume_uses_committed_checkpoint_and_stays_paused() {
        watched(99)
        launch()
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        focus(VisualHomeRuntime.HERO_RESUME)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选播放器" } }
        until { var ready = false; runtime { ready = it.personal!!.player.playbackState == androidx.media3.common.Player.STATE_READY }; ready }
        runtime {
            val personal = it.personal!!
            assertTrue(kotlin.math.abs(personal.player.currentPosition - 8_000) <= 100)
            assertEquals("round12-episode-2", personal.selectedMedia!!.target.episodeId!!.value)
            assertEquals("round12-pcm-original", personal.selectedMedia!!.target.variantId!!.value)
            assertFalse(personal.player.isPlaying); assertFalse(personal.player.playWhenReady)
        }
        key(KeyEvent.KEYCODE_BACK)
        until { semantics().any { it.tag == "候选首页" } }
        home { assertEquals(HomeRootScreen.HOME, it.screen) }
    }

    @Test fun home_favorites_history_contexts_stay_independent() {
        favorite(99); watched(98); watched(99)
        launch()
        nav("我的收藏"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选浏览" } }
        focus(ids[99].value)
        key(KeyEvent.KEYCODE_BACK); until { semantics().any { it.tag == "候选首页" } }
        nav("最近观看"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选浏览" } }
        waitTop(BrowseTopLevel.HISTORY)
        runtime { assertEquals(query, it.navigation!!.snapshot.query) }
        key(KeyEvent.KEYCODE_BACK); until { semantics().any { it.tag == "候选首页" } }
        home { assertEquals(HomeRootScreen.HOME, it.screen) }
    }

    @Test fun missing_artwork_shows_focusable_placeholder_with_real_title() {
        launch()
        key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_DOWN)
        focus(cardTag(VisualHomeRuntime.CATALOG_ROW, 0))
        repeat(3) {
            val current = semantics().firstOrNull { it.focused }?.tag
            key(KeyEvent.KEYCODE_DPAD_RIGHT)
            until(2_000) { semantics().firstOrNull { it.focused }?.tag != current }
        }
        focus(cardTag(VisualHomeRuntime.CATALOG_ROW, 3))
        val node = semantics().first { it.tag == cardTag(VisualHomeRuntime.CATALOG_ROW, 3) }
        assertTrue("缺图卡必须保留真实标题语义", node.description.contains("合成作品4"))
        assertUniqueFocus()
    }

    @Test fun portrait_artwork_keeps_portrait_containment() {
        launch()
        key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_DOWN)
        focus(cardTag(VisualHomeRuntime.CATALOG_ROW, 0))
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        focus(cardTag(VisualHomeRuntime.CATALOG_ROW, 1))
        val node = semantics().first { it.tag == cardTag(VisualHomeRuntime.CATALOG_ROW, 1) }
        assertTrue("竖版画必须走收纳路径并保留可读标题", node.description.contains("竖版画") && node.description.contains("合成作品2"))
        assertUniqueFocus()
    }

    @Test fun selected_and_focused_nav_are_distinct() {
        launch()
        nav("片库")
        val focused = semantics().first { it.focused && it.tag == "nav:片库" }
        val selected = semantics().first { it.tag == "nav:首页" }
        assertTrue(focused.focused); assertFalse(focused.selected)
        assertTrue(selected.selected); assertFalse(selected.focused)
    }

    @Test fun settings_actions_have_real_effects() {
        launch()
        nav("设置"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选设置" } }
        until { semantics().any { it.focused && it.tag.startsWith("大字模式") } }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.text.contains("大字模式：开") } }
        home { assertTrue(it.largeText) }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.text.contains("大字模式：关") } }
        settingsItem("列数")
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        runtime { assertEquals(3, it.columns) }
        key(KeyEvent.KEYCODE_BACK); until { semantics().any { it.tag == "候选首页" } }
    }

    @Test fun unconfigured_empty_state_action_opens_settings() {
        writeFixture(emptyWorks = true)
        launch()
        until { semantics().any { it.tag == "action:前往设置" } }
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        focus("action:前往设置")
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选设置" } }
    }

    @Test fun ack_lost_after_commit_ui_retry_has_true_state() {
        launch()
        key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_DOWN)
        focus(cardTag(VisualHomeRuntime.CATALOG_ROW, 0))
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选详情" } }
        // R12-M1：仓储真实提交成功，但 UI 回执被丢弃一次；重试必须幂等收敛且不伪造永久失败。
        com.ichichuang.cinema.feature.personal.PersonalLibraryTestHooks.lostReceiptAcknowledgements.set(1)
        action("收藏操作")
        until { var failed = false; runtime { failed = it.personal!!.message.contains("操作未保存") }; failed }
        until { favoriteIds() == listOf(ids[0].value) }
        action("收藏操作")
        until { var ok = false; runtime { ok = it.personal!!.message.contains("已收藏") }; ok }
        assertEquals("重试后仍只有一条真实收藏", listOf(ids[0].value), favoriteIds())
        focus("收藏操作")
        assertTrue(semantics().any { it.focused && it.tag == "收藏操作" })
    }

    @Test fun rapid_tab_switch_aborts_stale_home_restore() {
        // 第一次运行把首页焦点落到目录卡并落盘。
        launch()
        key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_DOWN)
        val card = cardTag(VisualHomeRuntime.CATALOG_ROW, 0)
        focus(card)
        scenario!!.close(); scenario = null
        until { BrowseProbeTestHooks.activeLeases.get() == 0 && VisualHomeTestHooks.activeLeases.get() == 0 }
        VisualHomeTestHooks.holdWindowReady = true
        scenario = ActivityScenario.launch(MainActivity::class.java)
        until { semantics().any { it.tag == "候选首页" } }
        key(KeyEvent.KEYCODE_DPAD_RIGHT) // 用户先行移动到“片库”
        scenario!!.onActivity { VisualHomeTestHooks.holdWindowReady = false }
        focus("nav:片库")
        until { semantics().any { it.tag == card } }
        assertFalse("迟到恢复不得把焦点拽回旧目录卡", semantics().any { it.tag == card && it.focused })
        home { assertNull(it.restoreToken) }
    }

    @Test fun image_completion_does_not_steal_focus() {
        launch()
        key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_DOWN)
        focus(cardTag(VisualHomeRuntime.CATALOG_ROW, 0)) // 带合成图，真实异步装载
        instrumentation.waitForIdleSync(); Thread.sleep(700)
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        focus(cardTag(VisualHomeRuntime.CATALOG_ROW, 1))
        Thread.sleep(700); instrumentation.waitForIdleSync()
        assertTrue("图片完成回调不得抢焦点", semantics().any { it.focused && it.tag == cardTag(VisualHomeRuntime.CATALOG_ROW, 1) })
        assertUniqueFocus()
    }

    @Test fun home_restore_from_saved_context_focuses_saved_card() {
        // 预置真实首页上下文（等价于上次会话落盘后重开），恢复必须聚焦保存的卡片。
        val card = cardTag(VisualHomeRuntime.CATALOG_ROW, 2)
        runBlocking {
            DebugBrowseProbeGate.withOwnedProfile(context) { db ->
                val store = HomeSnapshotStore(db, BrowseTopLevel.HOME.contextKey(query))
                store.saveActive(RestoreNavigationState(
                    BrowseSnapshot(BrowseTopLevel.HOME.contextKey(query), BrowseTopLevel.HOME.route, null,
                        query, 1, WorkId(card), WorkId(card), 0f, 1)))
                assertNotNull("预置的首页上下文必须可回读", store.load())
            }
        }
        launch(waitNavFocus = false)
        until { semantics().any { it.focused && it.tag == card } }
        home { assertEquals(HomeContentStatus.READY, it.display.contentStatus) }
    }

    @Test fun home_catalog_row_truthful_when_partial() {
        writeFixture(complete = false)
        launch()
        until { semantics().any { it.text.contains("部分来源未查询完成") } }
        home {
            assertEquals(HomeContentStatus.PARTIAL, it.display.contentStatus)
            assertNull(it.display.totalKnown)
        }
    }
}
