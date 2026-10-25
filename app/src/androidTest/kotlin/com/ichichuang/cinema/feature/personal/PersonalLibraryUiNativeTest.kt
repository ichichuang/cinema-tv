package com.ichichuang.cinema.feature.personal

import com.ichichuang.cinema.feature.browse.*
import com.ichichuang.cinema.feature.player.syntheticWav
import com.ichichuang.cinema.data.userdata.*
import com.ichichuang.cinema.data.settings.progressStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ichichuang.cinema.domain.model.userdata.*

import android.content.Context
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
import com.ichichuang.cinema.data.userdata.LegacyHandoffRuntime
import com.ichichuang.cinema.data.userdata.RoomBrowseStateStore
import com.ichichuang.cinema.domain.model.catalog.*
import com.ichichuang.cinema.domain.model.userdata.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

class PersonalLibraryUiNativeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val ids = (0 until 105).map { WorkId(UUID.nameUUIDFromBytes("round11-private-$it".toByteArray()).toString()) }
    private val query = CatalogQuery.create("测试查询-日本恐怖1994", ContentKind.MOVIE, LanguageScope.NON_CHINESE,
        setOf("JP"), setOf("horror"), 1994, 1994, CatalogSort.YEAR_DESCENDING, setOf(SourceId("test-source")))
    private val alternate = CatalogQuery.create("测试查询-日本恐怖1995", ContentKind.MOVIE, LanguageScope.NON_CHINESE,
        setOf("JP"), setOf("horror"), 1995, 1995, CatalogSort.YEAR_ASCENDING, setOf(SourceId("test-source")))
    private fun snapshot(top: BrowseTopLevel = BrowseTopLevel.LIBRARY, index: Int = 99) = BrowseSnapshot(
        top.contextKey(query), top.route, null, query, 1, ids[index], ids[(index / 5) * 5], 0f, 1)
    @Before fun ownedPrivateFixture() {
        check(BuildConfig.BROWSE_RESTORE_PROBE && BuildConfig.PLAYBACK_ROOM_PROBE)
        check(InstrumentationRegistry.getArguments().getString("cinema.browse") == "round11")
        check(InstrumentationRegistry.getArguments().getString("cinema.disposable") == "round06")
        check(InstrumentationRegistry.getArguments().getString("cinema.handoff") == "round07")
        until { BrowseProbeTestHooks.activeLeases.get() == 0 }
        File(context.noBackupFilesDir, LegacyHandoffRuntime.PROFILE).writeText(LegacyHandoffRuntime.PROFILE_TOKEN)
        File(context.noBackupFilesDir, LegacyHandoffRuntime.CONSENT).writeText(LegacyHandoffRuntime.CONSENT_TOKEN)
        File(context.noBackupFilesDir, "round06-playback-test.authorization").writeText("cinema-round06-disposable-local-media")
        check(!LegacyHandoffRuntime.present(context))
        context.deleteDatabase(DebugBrowseProbeGate.DATABASE)
        BrowseProbeTestHooks.holdWindowReady = false; BrowseProbeTestHooks.disableWorkFocus = false; BrowseProbeTestHooks.failedFocusRequests.set(0); BrowseProbeTestHooks.focusTrace.clear()
        BrowseProbeTestHooks.beforeLoad = null; BrowseProbeTestHooks.beforeTopLevelLoad = null; BrowseProbeTestHooks.blockPlacement = false
        check(BuildConfig.PERSONAL_LIBRARY_PROBE)
        check(InstrumentationRegistry.getArguments().getString("cinema.personal") == "round12")
        File(context.noBackupFilesDir, PersonalLibraryAuthorization.FILE).writeText(PersonalLibraryAuthorization.TOKEN)
        PersonalLibraryTestHooks.beforeProjection = null; PersonalLibraryTestHooks.projectionDeliveries.set(0)
        writeFixture()
    }
    private fun writeFixture(complete: Boolean = true, omit: Set<Int> = emptySet()) {
        val works = JSONArray()
        ids.forEachIndexed { i, id -> if (i !in omit) works.put(JSONObject().put("id", id.value)
            .put("title", if (i == 0 || i == 99) "同名合成作品" else "合成作品${i + 1}")
            .put("source", if (i == 104) "disabled-source" else "test-source")
            .put("year", if (i == 100) 1995 else 1994)
            .put("countries", JSONArray(listOf(if (i == 101) "US" else "JP")))
            .put("genres", JSONArray(listOf(if (i == 102) "drama" else "horror")))
            .put("kind", if (i == 103) "OTHER" else "MOVIE")
            .put("languages", JSONArray(listOf("ja"))).put("favorite", true).put("history", true)) }
        val root = JSONObject().put("initial", BrowseSnapshotCodec.encode(snapshot()))
            .put("alternative", BrowseSnapshotCodec.encode(snapshot().copy(contextKey = BrowseTopLevel.LIBRARY.contextKey(alternate),
                query = alternate, focusedWorkId = ids[100], anchorWorkId = ids[100])))
            .put("complete", complete).put("works", works).put("order", JSONArray(ids.map { it.value }))
        val media = JSONArray()
        listOf(98, 99).forEach { i -> (1..2).forEach { episode ->
            val file = File(context.filesDir, "round12-$i-$episode.wav").also(::syntheticWav)
            media.put(JSONObject().put("work", ids[i].value).put("episode", "round12-episode-$episode")
                .put("variant", "round12-pcm-original").put("uri", file.toURI().toString()))
        } }
        root.put("media", media)
        File(context.noBackupFilesDir, BrowseProbeFixture.FILE).writeText(root.toString())
    }
    private fun nodes(view: View): List<SemanticsNode> {
        fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        if (view is ViewRootForTest) return flatten(view.semanticsOwner.unmergedRootSemanticsNode)
        return if (view is ViewGroup) (0 until view.childCount).flatMap { nodes(view.getChildAt(it)) } else emptyList()
    }
    private data class Node(val tag: String, val focused: Boolean, val selected: Boolean, val text: String)
    private fun semantics(): List<Node> {
        var result = emptyList<Node>()
        scenario?.onActivity { result = nodes(it.window.decorView).map { node -> Node(
            node.config.getOrElse(SemanticsProperties.TestTag) { "" },
            node.config.getOrElse(SemanticsProperties.Focused) { false },
            node.config.getOrElse(SemanticsProperties.Selected) { false },
            node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString("") { it.text }) } }
        return result
    }
    private fun until(timeout: Long = 12_000, condition: () -> Boolean) {
        val end = android.os.SystemClock.uptimeMillis() + timeout
        while (!condition()) { if (android.os.SystemClock.uptimeMillis() > end) {
            var diagnostic = ""
            scenario?.onActivity { diagnostic = "窗口=${it.window.decorView.hasWindowFocus()} 尺寸=${it.window.decorView.width}x${it.window.decorView.height} rt=${it.browseProbe?.restoreToken} focus=${it.browseProbe?.focused} trace=${BrowseProbeTestHooks.focusTrace.toList()}" }
            fail("等待实际原生状态超时；$diagnostic；${if(scenario != null) semantics() else "无页面"}")
        }; Thread.sleep(25) }
    }
    private fun launch(waitFocus: Boolean = true) {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        until { semantics().any { it.tag == "候选浏览" } }
        if (waitFocus) focus(ids[99].value)
    }
    private fun runtime(action: (BrowseProbeRuntime) -> Unit) { scenario!!.onActivity { action(requireNotNull(it.browseProbe)) } }
    private fun focus(tag: String) = until {
        val nativeFocused = semantics().any { it.tag == tag && it.focused }
        if (ids.none { it.value == tag }) nativeFocused
        else {
            var currentGridFocused = false
            scenario!!.onActivity { activity ->
                val active = activity.browseProbe
                currentGridFocused = active != null && !active.isDetail && active.focused?.value == tag &&
                    active.items.any { it.id.value == tag }
            }
            nativeFocused && currentGridFocused
        }
    }
    private fun key(code: Int) {
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, code))
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, code))
        instrumentation.waitForIdleSync()
    }
    private fun assertUniqueFocus() { assertEquals(1, semantics().count { it.focused }) }
    private fun control(label: String) {
        repeat(22) {
            val current = semantics().firstOrNull { it.focused }?.tag
            if (current != "片库") {
                val index = ids.indexOfFirst { it.value == current }
                key(KeyEvent.KEYCODE_DPAD_UP)
                focus(if (index < 5) "片库" else ids[index - 5].value)
            }
        }
        focus("片库")
        val labels = listOf("片库", "收藏", "历史", "筛选", "列数", "刷新")
        repeat(labels.indexOf(label)) { index -> key(KeyEvent.KEYCODE_DPAD_RIGHT); focus(labels[index + 1]) }
        focus(label)
    }
    private fun openDetail() { key(KeyEvent.KEYCODE_DPAD_CENTER); until { semantics().any { it.tag == "候选详情" } }; focus("详情返回") }
    private fun seed(top: BrowseTopLevel, index: Int) = runBlocking {
        DebugBrowseProbeGate.withOwnedProfile(context) { RoomBrowseStateStore(it).saveActive(RestoreNavigationState(snapshot(top, index))) }
    }
    @After fun closeOwnedActivity() {
        PersonalLibraryTestHooks.beforeProjection?.complete(Unit); PersonalLibraryTestHooks.beforeProjection = null
        BrowseProbeTestHooks.beforeSave?.complete(Unit); BrowseProbeTestHooks.beforeSave = null
        scenario?.close(); scenario = null
        until { BrowseProbeTestHooks.activeLeases.get() == 0 }
        BrowseProbeTestHooks.holdWindowReady = false; BrowseProbeTestHooks.disableWorkFocus = false; BrowseProbeTestHooks.failedFocusRequests.set(0); BrowseProbeTestHooks.focusTrace.clear()
        BrowseProbeTestHooks.beforeLoad = null; BrowseProbeTestHooks.beforeTopLevelLoad = null; BrowseProbeTestHooks.blockPlacement = false
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
    private fun switchTop(top: BrowseTopLevel) {
        runtime { it.switch(top) }
        until { var ready = false; runtime { ready = it.top == top && !it.busy }; ready }
    }
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
            val session = "round12-ui-${UUID.randomUUID()}"
            val target = PlaybackTarget(ids[index], EpisodeId("round12-episode-2"), VariantId(variant))
            assertEquals(CommitResult.Committed, repository.rememberCard(ids[index].value, "合成作品${index + 1}", "test-source"))
            assertEquals(CommitResult.Committed, store.beginSession(target, session))
            assertEquals(CommitResult.Committed, repository.confirmPlayback(session))
            assertEquals(CommitResult.Committed, store.commit(PlaybackSnapshot(ids[index], target.episodeId, target.variantId,
                session, 1, position, 12_000, WatchState.IN_PROGRESS, 1)))
        }
    }
    private fun sql(statement: String) {
        var db: com.ichichuang.cinema.data.local.CinemaDatabase? = null
        runtime { db = it.personal!!.database }
        runBlocking(Dispatchers.IO) { db!!.openHelper.writableDatabase.execSQL(statement) }
    }
    private fun favoriteIds(): List<String> {
        var repository: RoomPersonalLibraryRepository? = null
        runtime { repository = it.personal!!.repository }
        return runBlocking { repository!!.favorites().map { it.workId } }
    }
    private fun screenshot() {
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(context.noBackupFilesDir, "round12-ui.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
    @Test fun dpad_reaches_remove_actions_without_menu_key() {
        launch(); openDetail()
        assertTrue(semantics().any { it.tag == "收藏操作" })
        key(KeyEvent.KEYCODE_DPAD_DOWN); focus("收藏操作")
        key(KeyEvent.KEYCODE_DPAD_DOWN); focus("播放本地测试媒体")
        key(KeyEvent.KEYCODE_DPAD_DOWN); focus("移出继续观看")
        key(KeyEvent.KEYCODE_DPAD_DOWN); focus("删除观看记录")
        key(KeyEvent.KEYCODE_DPAD_CENTER); focus("取消删除")
        key(KeyEvent.KEYCODE_BACK); focus("删除观看记录")
    }
    @Test fun focusable_favorite_opens_detail_and_returns() {
        launch(); openDetail(); action("收藏操作")
        until { favoriteIds() == listOf(ids[99].value) }
        key(KeyEvent.KEYCODE_BACK); focus(ids[99].value)
        switchTop(BrowseTopLevel.FAVORITES); focus(ids[99].value)
        openDetail(); key(KeyEvent.KEYCODE_BACK); focus(ids[99].value); screenshot()
        runtime { assertEquals(BrowseTopLevel.FAVORITES, it.top) }
    }
    @Test fun history_to_player_back_restores_history_context() {
        watched(); launch(); switchTop(BrowseTopLevel.HISTORY); focus(ids[99].value)
        openDetail(); action("播放本地测试媒体"); focus("播放暂停")
        runtime { assertTrue(it.isPlayer); assertFalse(it.personal!!.player.playWhenReady) }
        key(KeyEvent.KEYCODE_BACK); focus("详情返回")
        key(KeyEvent.KEYCODE_BACK); focus(ids[99].value)
        runtime { assertEquals(BrowseTopLevel.HISTORY, it.top); assertTrue(it.navigation!!.returnChain.isEmpty()) }
    }
    @Test fun continue_click_uses_committed_episode_and_pauses_on_restore() {
        watched(); launch(); switchTop(BrowseTopLevel.CONTINUE); focus(ids[99].value)
        key(KeyEvent.KEYCODE_DPAD_CENTER); focus("播放暂停")
        until { var ready = false; runtime { ready = it.personal!!.player.playbackState == androidx.media3.common.Player.STATE_READY }; ready }
        runtime {
            val personal = it.personal!!
            assertTrue(kotlin.math.abs(personal.player.currentPosition - 8_000) <= 100)
            assertEquals(File(context.filesDir, "round12-99-2.wav").canonicalPath, File(personal.player.currentMediaItem!!.localConfiguration!!.uri.path!!).canonicalPath)
            assertTrue(personal.player.currentMediaItem!!.mediaId.isNotBlank())
            assertEquals("round12-episode-2", personal.selectedMedia!!.target.episodeId!!.value)
            assertEquals("round12-pcm-original", personal.selectedMedia!!.target.variantId!!.value)
            assertEquals(8_000L, personal.requestedResumePosition)
            assertFalse(personal.player.isPlaying); assertFalse(personal.player.playWhenReady)
        }
        scenario!!.recreate(); focus("播放暂停")
        runtime { assertFalse(it.personal!!.player.isPlaying); assertEquals(8_000L, it.personal!!.requestedResumePosition) }
    }
    @Test fun empty_and_offline_chinese_states_are_truthful() {
        launch(); switchTop(BrowseTopLevel.FAVORITES)
        until { semantics().any { it.tag == "个人列表为空" } }
        assertTrue(favoriteIds().isEmpty())
        switchTop(BrowseTopLevel.LIBRARY); focus(ids[99].value)
        key(KeyEvent.KEYCODE_DPAD_LEFT); focus(ids[98].value)
        key(KeyEvent.KEYCODE_DPAD_LEFT); focus(ids[97].value)
        openDetail(); action("收藏操作")
        key(KeyEvent.KEYCODE_BACK); switchTop(BrowseTopLevel.FAVORITES); focus(ids[97].value)
        openDetail(); action("播放本地测试媒体")
        runtime { assertFalse(it.isPlayer); assertTrue(it.personal!!.message.contains("当前来源不可用")) }
    }
    @Test fun confirmation_back_cancels_destructive_action() {
        watched(); favorite(99); launch(); openDetail(); action("删除观看记录"); focus("取消删除")
        key(KeyEvent.KEYCODE_BACK); focus("删除观看记录")
        runtime { assertFalse(it.personal!!.confirmation); assertEquals(1, it.personal!!.history.size); assertEquals(1, it.personal!!.continuing.size) }
        assertEquals(listOf(ids[99].value), favoriteIds())
    }
    @Test fun favorite_failure_rolls_back_visible_state() {
        launch(); openDetail()
        sql("CREATE TRIGGER round12_fail_favorite BEFORE INSERT ON favorites BEGIN SELECT RAISE(ABORT, 'round12 deliberate storage failure'); END")
        action("收藏操作")
        until { var failed = false; runtime { failed = it.personal!!.message.contains("操作未保存") }; failed }
        assertTrue(favoriteIds().isEmpty()); focus("收藏操作")
        sql("DROP TRIGGER round12_fail_favorite")
    }
    @Test fun undo_unfavorite_requires_commit() {
        favorite(99); launch(); openDetail(); action("收藏操作")
        until { favoriteIds().isEmpty() }
        sql("CREATE TRIGGER round12_fail_undo BEFORE INSERT ON favorites BEGIN SELECT RAISE(ABORT, 'round12 undo storage failure'); END")
        action("撤销取消收藏")
        until { var failed = false; runtime { failed = it.personal!!.message.contains("操作未保存") }; failed }
        assertTrue(favoriteIds().isEmpty()); focus("撤销取消收藏")
        until {
            var visible = false
            scenario!!.onActivity { activity ->
                val node = nodes(activity.window.decorView).firstOrNull { it.config.getOrElse(SemanticsProperties.TestTag) { "" } == "撤销取消收藏" }
                val viewport = android.graphics.Rect()
                activity.findViewById<android.view.View>(android.R.id.content).getGlobalVisibleRect(viewport)
                val padding = 8f * activity.resources.displayMetrics.density
                visible = node?.boundsInWindow?.let { bounds ->
                    val fullHeight = node.size.height.toFloat()
                    fullHeight > 0 && bounds.height + .5f >= fullHeight &&
                        bounds.top - padding >= viewport.top && bounds.bottom + padding <= viewport.bottom
                } == true
            }
            visible
        }
        screenshot()
        sql("DROP TRIGGER round12_fail_undo")
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { favoriteIds() == listOf(ids[99].value) }; focus("收藏操作")
    }
    @Test fun unfavorite_focused_card_uses_valid_neighbor() {
        favorite(98); favorite(99); launch(); switchTop(BrowseTopLevel.FAVORITES); focus(ids[99].value)
        openDetail(); action("收藏操作"); until { favoriteIds() == listOf(ids[98].value) }
        key(KeyEvent.KEYCODE_BACK); focus(ids[98].value); assertUniqueFocus()
    }
    @Test fun personal_probe_requires_owned_profile() {
        File(context.noBackupFilesDir, PersonalLibraryAuthorization.FILE).delete()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        until { semantics().any { it.tag == "浏览测试不可用" } }
        assertFalse("缺少个人授权不能创建数据库", context.getDatabasePath(DebugBrowseProbeGate.DATABASE).exists())
    }
    @Test fun personal_probe_rejects_legacy_conflict() {
        val key = stringPreferencesKey("round12_existing_record")
        runBlocking { context.progressStore.edit { it[key] = "独立测试旧记录" } }
        val file = File(context.filesDir, "datastore/cinema_progress.preferences_pb")
        val before = file.readBytes()
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            until { semantics().any { it.tag == "浏览测试不可用" } }
            assertArrayEquals(before, file.readBytes())
            assertFalse(context.getDatabasePath(DebugBrowseProbeGate.DATABASE).exists())
        } finally { runBlocking { context.progressStore.edit { it.remove(key) } } }
    }
    @Test fun actual_media_play_creates_history_after_prepare_and_acknowledged_resume() {
        launch(); openDetail(); action("播放本地测试媒体"); focus("播放暂停")
        runtime { assertTrue(it.personal!!.history.isEmpty()); assertFalse(it.personal!!.player.isPlaying) }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var playing = false; runtime { playing = it.personal!!.player.isPlaying }; playing }
        until { var watched = false; runtime { watched = it.personal!!.history.size == 1 }; watched }
        until(15_000) { var committed = false; runtime { committed = it.personal!!.continuing.firstOrNull()?.snapshot?.positionMs?.let { p -> p >= 5_000 } == true }; committed }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var paused = false; runtime { paused = !it.personal!!.player.isPlaying }; paused }
        screenshot()
        runtime { assertEquals("round12-episode-1", it.personal!!.history.single().snapshot?.episodeId?.value); assertFalse(it.personal!!.player.playWhenReady) }
    }
    @Test fun user_input_cancels_pending_personal_restore() {
        favorite(99); seed(BrowseTopLevel.FAVORITES, 99)
        val release = CompletableDeferred<Unit>(); PersonalLibraryTestHooks.beforeProjection = release
        BrowseProbeTestHooks.holdWindowReady = true
        launch(false)
        until { var done = false; runtime { done = it.navigation != null }; done }
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        scenario!!.onActivity { BrowseProbeTestHooks.holdWindowReady = false }
        focus("片库"); release.complete(Unit)
        until { PersonalLibraryTestHooks.projectionDeliveries.get() > 0 }
        focus("片库"); assertFalse(semantics().any { it.tag == ids[99].value && it.focused })
        runtime { assertNull(it.restoreToken) }
    }
    @Test fun delayed_unchanged_personal_flow_preserves_pending_layout_restore() {
        favorite(99); seed(BrowseTopLevel.FAVORITES, 99)
        val release = CompletableDeferred<Unit>(); PersonalLibraryTestHooks.beforeProjection = release
        BrowseProbeTestHooks.holdWindowReady = true
        launch(false)
        until { var ready = false; runtime { ready = it.navigation != null && it.placedColumns == 5 }; ready }
        release.complete(Unit)
        until { PersonalLibraryTestHooks.projectionDeliveries.get() > 0 }
        scenario!!.onActivity { BrowseProbeTestHooks.holdWindowReady = false }
        focus(ids[99].value); assertUniqueFocus()
        runtime { assertEquals(it.result!!.snapshotRevision, it.layoutToken!!.resultRevision) }
    }
    @Test fun prepare_external_process_personal_restore() {
        favorite(98); watched(98)
        runBlocking {
            DebugBrowseProbeGate.withOwnedProfile(context) { db ->
                assertEquals(CommitResult.Committed, RoomPersonalLibraryRepository(db).deleteWatchHistory(ids[98].value))
            }
        }
        favorite(99); watched(); seed(BrowseTopLevel.HISTORY, 99)
        launch(); openDetail(); action("播放本地测试媒体"); focus("播放暂停")
        runtime {
            assertEquals(8_000L, it.personal!!.requestedResumePosition); assertFalse(it.personal!!.player.playWhenReady)
            assertEquals(setOf(ids[98].value, ids[99].value), it.personal!!.favorites.map { card -> card.workId }.toSet())
            assertEquals(listOf(ids[99].value), it.personal!!.history.map { card -> card.workId })
            assertEquals(listOf(ids[99].value), it.personal!!.continuing.map { card -> card.workId })
        }
        until { File(context.noBackupFilesDir, "round12-personal-trace.json").isFile }
    }
    @Test fun continue_toolbar_remove_preserves_other_personal_rows() {
        watched(); favorite(99); launch(); switchTop(BrowseTopLevel.CONTINUE); focus(ids[99].value)
        key(KeyEvent.KEYCODE_DPAD_UP); focus("片库")
        listOf("收藏", "历史", "继续观看", "筛选", "列数", "刷新", "移出继续观看").forEach { label -> key(KeyEvent.KEYCODE_DPAD_RIGHT); focus(label) }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var empty = false; runtime { empty = it.personal!!.continuing.isEmpty() }; empty }
        assertEquals(listOf(ids[99].value), favoriteIds())
        runtime { assertEquals(1, it.personal!!.history.size) }
        until { semantics().any { it.tag == "个人列表为空" } }
    }

    @Test fun watched_favorite_keeps_history_and_continue_episode_labels() {
        favorite(99); watched(); seed(BrowseTopLevel.HISTORY, 99); launch()
        assertTrue("收藏卡不得遮蔽真实历史剧集和进度", semantics().any { it.text.contains("round12-episode-2") && it.text.contains("8 秒") })
        switchTop(BrowseTopLevel.CONTINUE); focus(ids[99].value)
        assertTrue("续播卡必须显示该列表的已确认剧集", semantics().any { it.text.contains("round12-episode-2") && it.text.contains("8 秒") })
    }

    @Test fun confirmed_history_delete_allows_new_actual_play_in_same_process() {
        launch(); openDetail(); action("播放本地测试媒体"); focus("播放暂停")
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until(15_000) { var committed = false; runtime { committed = it.personal!!.continuing.firstOrNull()?.snapshot?.positionMs?.let { p -> p >= 5_000 } == true }; committed }
        var oldSession: String? = null
        runtime { oldSession = it.personal!!.player.currentMediaItem!!.mediaId }
        key(KeyEvent.KEYCODE_BACK); focus("详情返回")
        action("删除观看记录"); focus("取消删除"); action("确认删除")
        until { var empty = false; runtime { empty = it.personal!!.history.isEmpty() && it.personal!!.continuing.isEmpty() }; empty }
        action("播放本地测试媒体"); focus("播放暂停")
        runtime { assertNotEquals(oldSession, it.personal!!.player.currentMediaItem!!.mediaId); assertFalse(it.personal!!.player.playWhenReady) }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var played = false; runtime { played = it.personal!!.history.size == 1 && it.personal!!.player.isPlaying }; played }
    }

    @Test fun unknown_variant_requires_explicit_current_version_choice_without_mapping_position() {
        watched(variant = "round12-unknown-original-cut"); launch(); openDetail()
        action("播放本地测试媒体")
        until { var refused = false; runtime { refused = !it.isPlayer && it.personal!!.message.contains("时间轴尚未核实") }; refused }
        until { semantics().any { it.tag == "从头准备当前版本" } }
        assertTrue("未知时间轴须有明确可达的当前版本选择", semantics().any { it.tag == "从头准备当前版本" })
        action("从头准备当前版本"); focus("播放暂停")
        until { var ready = false; runtime { ready = it.personal!!.player.playbackState == androidx.media3.common.Player.STATE_READY }; ready }
        runtime {
            assertEquals("round12-episode-2", it.personal!!.selectedMedia!!.target.episodeId!!.value)
            assertEquals("round12-pcm-original", it.personal!!.selectedMedia!!.target.variantId!!.value)
            assertEquals(0L, it.personal!!.player.currentPosition); assertFalse(it.personal!!.player.playWhenReady)
        }
        var db: com.ichichuang.cinema.data.local.CinemaDatabase? = null
        runtime { db = it.personal!!.database }
        runBlocking {
            val original = db!!.playbackSnapshots().find(ids[99].value, "round12-episode-2", "round12-unknown-original-cut")!!.snapshot()
            assertEquals(8_000L, original.positionMs)
            assertEquals("round12-unknown-original-cut", RoomPlaybackProgressStore(db!!).loadResume(ids[99])!!.variantId!!.value)
        }
    }

    @Test fun unknown_variant_continue_entry_reaches_existing_detail_choice() {
        watched(variant = "round12-unknown-original-cut"); launch()
        switchTop(BrowseTopLevel.CONTINUE); focus(ids[99].value)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var refused = false; runtime { refused = !it.busy && it.personal!!.message.contains("时间轴尚未核实") }; refused }
        until { semantics().any { it.tag == "候选详情" } }
        assertTrue(semantics().any { it.tag == "从头准备当前版本" })
        action("从头准备当前版本"); focus("播放暂停")
        runtime { assertEquals(BrowseTopLevel.CONTINUE, it.top); assertFalse(it.personal!!.player.playWhenReady); assertEquals(0L, it.personal!!.player.currentPosition) }
        key(KeyEvent.KEYCODE_BACK); focus("详情返回")
        key(KeyEvent.KEYCODE_BACK); focus(ids[99].value)
        runtime { assertEquals(BrowseTopLevel.CONTINUE, it.top) }
    }
    @Test fun restored_player_with_removed_variant_reaches_existing_detail_choice() {
        watched()
        val file = File(context.noBackupFilesDir, BrowseProbeFixture.FILE)
        val fixture = JSONObject(file.readText()); val media = fixture.getJSONArray("media")
        (0 until media.length()).forEach { i -> if (media.getJSONObject(i).getString("work") == ids[99].value) media.getJSONObject(i).put("variant", "round12-explicit-new-cut") }
        file.writeText(fixture.toString())
        runBlocking {
            DebugBrowseProbeGate.withOwnedProfile(context) { db ->
                assertEquals(CommitResult.Committed, RoomBrowseStateStore(db).saveActive(RestoreNavigationState(
                    snapshot(BrowseTopLevel.HISTORY, 99), listOf(BrowseChildRoute.DETAIL, BrowseChildRoute.PLAYER))))
            }
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        until { var refused = false; scenario!!.onActivity { refused = it.browseProbe?.personal?.message?.contains("时间轴尚未核实") == true }; refused }
        until { semantics().any { it.tag == "候选详情" } }
        assertTrue(semantics().any { it.tag == "从头准备当前版本" })
        action("从头准备当前版本"); focus("播放暂停")
        runtime { assertEquals("round12-explicit-new-cut", it.personal!!.selectedMedia!!.target.variantId!!.value); assertFalse(it.personal!!.player.playWhenReady); assertEquals(0L, it.personal!!.player.currentPosition) }
        key(KeyEvent.KEYCODE_BACK); focus("详情返回")
        key(KeyEvent.KEYCODE_BACK); focus(ids[99].value)
        runtime { assertEquals(BrowseTopLevel.HISTORY, it.top) }
    }

}
