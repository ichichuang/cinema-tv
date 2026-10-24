package com.ichichuang.cinema.feature.browse

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

class BrowseNavigationNativeTest {
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
        File(context.noBackupFilesDir, BrowseProbeFixture.FILE).writeText(root.toString())
    }
    private fun nodes(view: View): List<SemanticsNode> {
        fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        if (view is ViewRootForTest) return flatten(view.semanticsOwner.unmergedRootSemanticsNode)
        return if (view is ViewGroup) (0 until view.childCount).flatMap { nodes(view.getChildAt(it)) } else emptyList()
    }
    private data class Node(val tag: String, val focused: Boolean, val selected: Boolean)
    private fun semantics(): List<Node> {
        var result = emptyList<Node>()
        scenario?.onActivity { result = nodes(it.window.decorView).map { node -> Node(
            node.config.getOrElse(SemanticsProperties.TestTag) { "" },
            node.config.getOrElse(SemanticsProperties.Focused) { false },
            node.config.getOrElse(SemanticsProperties.Selected) { false }) } }
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
    private fun focus(tag: String) = until { semantics().any { it.tag == tag && it.focused } }
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
        BrowseProbeTestHooks.beforeSave?.complete(Unit); BrowseProbeTestHooks.beforeSave = null
        scenario?.close(); scenario = null
        until { BrowseProbeTestHooks.activeLeases.get() == 0 }
        BrowseProbeTestHooks.holdWindowReady = false; BrowseProbeTestHooks.disableWorkFocus = false; BrowseProbeTestHooks.failedFocusRequests.set(0); BrowseProbeTestHooks.focusTrace.clear()
        BrowseProbeTestHooks.beforeLoad = null; BrowseProbeTestHooks.beforeTopLevelLoad = null; BrowseProbeTestHooks.blockPlacement = false
    }
    @Test fun library_deep_item_detail_back_returns_same_item() {
        launch(); assertUniqueFocus()
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(context.noBackupFilesDir, "round11-route.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        runtime { assertEquals(query, it.navigation!!.snapshot.query); assertEquals(100, it.items.size) }
        assertEquals("同名合成作品", BrowseProbeFixture(context).works.first().title)
        assertEquals(BrowseProbeFixture(context).works.first().title, BrowseProbeFixture(context).works[99].title)
        var beforeAnchor: WorkId? = null; var beforeOffset = 0f
        runtime { beforeAnchor = it.visibleAnchor; beforeOffset = it.visibleOffset }
        assertNotNull(beforeAnchor)
        openDetail(); key(KeyEvent.KEYCODE_BACK); focus(ids[99].value); assertUniqueFocus()
        until { var same = false; runtime { same = it.visibleAnchor == beforeAnchor && kotlin.math.abs(it.visibleOffset - beforeOffset) < .01f }; same }
        runtime { assertEquals(beforeAnchor, it.navigation!!.snapshot.anchorWorkId); assertNotEquals(ids[0], beforeAnchor); assertEquals(query, it.navigation!!.snapshot.query) }
    }
    @Test fun switch_tabs_preserves_independent_focus() {
        seed(BrowseTopLevel.FAVORITES, 24); seed(BrowseTopLevel.HISTORY, 54); seed(BrowseTopLevel.LIBRARY, 99)
        launch(); runtime { it.switch(BrowseTopLevel.FAVORITES) }; focus(ids[24].value)
        runtime { it.switch(BrowseTopLevel.HISTORY) }; focus(ids[54].value)
        runtime { it.switch(BrowseTopLevel.LIBRARY) }; focus(ids[99].value); assertUniqueFocus()
    }
    @Test fun modal_traps_focus_and_returns_invoker() {
        launch(); control("筛选"); key(KeyEvent.KEYCODE_DPAD_CENTER); focus("关闭筛选")
        key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_RIGHT); focus("关闭筛选"); assertUniqueFocus()
        key(KeyEvent.KEYCODE_BACK); focus("筛选"); assertUniqueFocus()
    }
    @Test fun modal_removed_invoker_returns_same_row_control() {
        launch(); control("筛选"); key(KeyEvent.KEYCODE_DPAD_CENTER); focus("关闭筛选")
        runtime { it.removeModalInvoker() }; key(KeyEvent.KEYCODE_BACK); focus("列数"); assertUniqueFocus()
    }
    @Test fun user_input_cancels_late_focus_restore() {
        val release = CompletableDeferred<Unit>(); BrowseProbeTestHooks.beforeLoad = release
        launch(false); key(KeyEvent.KEYCODE_DPAD_LEFT); focus("片库")
        release.complete(Unit)
        until { var loaded = false; runtime { loaded = it.navigation != null }; loaded }
        focus("片库"); assertFalse(semantics().any { it.tag == ids[99].value && it.focused }); assertUniqueFocus()
    }
    @Test fun user_input_cancels_delayed_top_level_restore() {
        seed(BrowseTopLevel.FAVORITES, 24); seed(BrowseTopLevel.LIBRARY, 99)
        launch(); control("收藏")
        val release = CompletableDeferred<Unit>(); BrowseProbeTestHooks.beforeTopLevelLoad = release
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        release.complete(Unit)
        until { var loaded = false; runtime { loaded = it.top == BrowseTopLevel.FAVORITES }; loaded }
        assertFalse(semantics().any { it.tag == ids[24].value && it.focused })
        until { semantics().any { it.focused && it.tag in listOf("片库", "收藏", "历史") } }
        assertUniqueFocus()
    }
    @Test fun deep_list_waits_for_anchor_and_layout() {
        BrowseProbeTestHooks.blockPlacement = true; launch(false)
        until { var loaded = false; runtime { loaded = it.navigation != null }; loaded }
        assertFalse(semantics().any { it.focused && it.tag in ids.map { id -> id.value } })
        scenario!!.onActivity { BrowseProbeTestHooks.blockPlacement = false }
        focus(ids[99].value); assertUniqueFocus()
    }
    @Test fun layout_change_preserves_work_not_column_index() {
        launch(); runtime { it.changeColumns() }
        until { var placed = false; runtime { placed = it.placedColumns == 3 }; placed }
        focus(ids[99].value)
        runtime { assertEquals(3, it.columns); assertEquals(ids[99], it.navigation!!.snapshot.focusedWorkId) }; assertUniqueFocus()
    }
    @Test fun refresh_does_not_steal_focus() {
        launch(); key(KeyEvent.KEYCODE_DPAD_LEFT); focus(ids[98].value)
        runtime { it.refresh() }; focus(ids[98].value); assertUniqueFocus()
    }
    @Test fun refresh_without_prior_user_input_keeps_actual_focus() {
        launch(); runtime { it.refresh() }; focus(ids[99].value); assertUniqueFocus()
    }
    @Test fun back_returns_true_origin() {
        seed(BrowseTopLevel.HISTORY, 54); launch(false); focus(ids[54].value)
        openDetail(); key(KeyEvent.KEYCODE_BACK); focus(ids[54].value)
        runtime { assertEquals(BrowseTopLevel.HISTORY, it.top); assertTrue(it.navigation!!.returnChain.isEmpty()) }
        assertUniqueFocus()
    }
    @Test fun return_uses_origin_context_not_home_default() {
        seed(BrowseTopLevel.FAVORITES, 24); launch(false); focus(ids[24].value)
        openDetail(); key(KeyEvent.KEYCODE_BACK); focus(ids[24].value)
        runtime { assertEquals(BrowseTopLevel.FAVORITES, it.top); assertEquals(query, it.navigation!!.snapshot.query) }
    }
    @Test fun incomplete_result_does_not_prove_target_removed() {
        writeFixture(false, setOf(99)); launch(false)
        until { var partial = false; runtime { partial = it.result?.coverage == QueryCoverage.PARTIAL }; partial }
        assertFalse(semantics().any { it.focused && it.tag in ids.map { id -> id.value } })
        runtime { assertEquals(null, it.result!!.knownMatchedCount); assertTrue(it.status.contains("部分")) }
        key(KeyEvent.KEYCODE_DPAD_RIGHT); focus("片库")
    }
    @Test fun removed_complete_target_uses_same_row_neighbor() {
        writeFixture(true, setOf(99)); launch(false); focus(ids[98].value); assertUniqueFocus()
    }
    @Test fun selection_is_distinct_from_focus() {
        launch(); assertFalse(semantics().single { it.tag == ids[99].value }.selected)
        openDetail(); key(KeyEvent.KEYCODE_BACK); focus(ids[99].value)
        key(KeyEvent.KEYCODE_DPAD_LEFT); focus(ids[98].value)
        assertTrue(semantics().single { it.tag == ids[99].value }.selected)
        assertFalse(semantics().single { it.tag == ids[98].value }.selected); assertUniqueFocus()
    }
    @Test fun explicit_query_switch_preserves_old_precise_context() {
        launch(); runtime { it.changeQuery() }; focus(ids[100].value)
        runtime { assertEquals(alternate, it.navigation!!.snapshot.query); assertEquals(1, it.items.size) }
        openDetail(); key(KeyEvent.KEYCODE_BACK); focus(ids[100].value)
        runtime { assertEquals(alternate, it.navigation!!.snapshot.query) }
    }
    @Test fun rapid_inputs_and_physical_repeats_do_not_duplicate_routes() {
        launch(); key(KeyEvent.KEYCODE_DPAD_LEFT); key(KeyEvent.KEYCODE_DPAD_RIGHT); focus(ids[99].value)
        val down = android.os.SystemClock.uptimeMillis()
        instrumentation.sendKeySync(KeyEvent(down, down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
        instrumentation.sendKeySync(KeyEvent(down, down + 1, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 1))
        instrumentation.sendKeySync(KeyEvent(down, down + 2, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0))
        until { semantics().any { it.tag == "候选详情" } }
        runtime { assertEquals(listOf(BrowseChildRoute.DETAIL), it.navigation!!.returnChain) }
        val backDown = android.os.SystemClock.uptimeMillis()
        instrumentation.sendKeySync(KeyEvent(backDown, backDown, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0))
        instrumentation.sendKeySync(KeyEvent(backDown, backDown + 1, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 1))
        instrumentation.sendKeySync(KeyEvent(backDown, backDown + 2, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0))
        focus(ids[99].value); assertUniqueFocus()
        assertEquals(androidx.lifecycle.Lifecycle.State.RESUMED, scenario!!.state)
    }
    @Test fun rapid_dpad_keeps_all_requested_steps() {
        launch()
        repeat(20) {
            instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
            instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT))
        }
        focus(ids[79].value); assertUniqueFocus()
    }
    @Test fun malformed_active_snapshot_closes_candidate_route() {
        runBlocking {
            DebugBrowseProbeGate.withOwnedProfile(context) { db ->
                db.browseSnapshots().upsert(com.ichichuang.cinema.data.local.entity.BrowseSnapshotEntity(
                    RoomBrowseStateStore.ACTIVE_NAVIGATION_KEY, "{}"))
            }
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        until { semantics().any { it.tag == "候选浏览" || it.tag == "浏览测试不可用" } }
        until { BrowseProbeTestHooks.activeLeases.get() == 0 }
        scenario!!.onActivity { assertNull("损坏快照不能留下已关闭数据库的候选页面", it.browseProbe) }
    }
    @Test fun window_readiness_blocks_focus_delivery() {
        BrowseProbeTestHooks.holdWindowReady = true; launch(false)
        until { var loaded = false; runtime { loaded = it.navigation != null && it.placedColumns == 5 }; loaded }
        assertFalse(semantics().any { it.focused && it.tag in ids.map { id -> id.value } })
        scenario!!.onActivity { BrowseProbeTestHooks.holdWindowReady = false }
        focus(ids[99].value); assertUniqueFocus()
    }
    @Test fun failed_focus_request_retries_only_current_token() {
        BrowseProbeTestHooks.disableWorkFocus = true; launch(false)
        until { BrowseProbeTestHooks.failedFocusRequests.get() > 0 }
        assertFalse(semantics().any { it.focused && it.tag in ids.map { id -> id.value } })
        scenario!!.onActivity { BrowseProbeTestHooks.disableWorkFocus = false }
        focus(ids[99].value); assertUniqueFocus()
    }
    @Test fun failed_focus_retry_is_cancelled_by_real_input() {
        BrowseProbeTestHooks.disableWorkFocus = true; launch(false)
        until { BrowseProbeTestHooks.failedFocusRequests.get() > 0 }
        key(KeyEvent.KEYCODE_DPAD_LEFT); focus("片库")
        scenario!!.onActivity { BrowseProbeTestHooks.disableWorkFocus = false }
        instrumentation.waitForIdleSync()
        focus("片库"); assertFalse(semantics().any { it.focused && it.tag == ids[99].value })
    }
    @Test fun viewport_resize_repositions_pending_deep_anchor() {
        BrowseProbeTestHooks.blockPlacement = true; launch(false)
        scenario!!.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        until { var portrait = false; runtime { portrait = it.viewportHeight > it.viewportWidth && it.navigation != null }; portrait }
        scenario!!.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        until { var landscape = false; runtime { landscape = it.viewportWidth > it.viewportHeight && it.viewportHeight > 0 }; landscape }
        scenario!!.onActivity { BrowseProbeTestHooks.blockPlacement = false }
        focus(ids[99].value); assertUniqueFocus()
        runtime { assertNotEquals(ids[0], it.visibleAnchor) }
    }
    private fun holdNextSave(): CompletableDeferred<Unit> {
        val hold = CompletableDeferred<Unit>()
        scenario!!.onActivity { BrowseProbeTestHooks.beforeSave = hold }
        runtime { it.positioned(it.visibleAnchor, it.visibleOffset) }
        return hold
    }
    @Test fun back_during_detail_entry_flush_prevents_late_detail() {
        launch(); val hold = holdNextSave()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var waiting = false; runtime { waiting = it.busy && BrowseProbeTestHooks.waitingSaves.get() > 0 }; waiting }
        key(KeyEvent.KEYCODE_BACK)
        scenario!!.onActivity { BrowseProbeTestHooks.beforeSave = null; hold.complete(Unit) }
        until { scenario!!.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
    }
    @Test fun direction_during_detail_entry_flush_moves_and_cancels_entry() {
        launch(); val hold = holdNextSave()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var waiting = false; runtime { waiting = it.busy && BrowseProbeTestHooks.waitingSaves.get() > 0 }; waiting }
        key(KeyEvent.KEYCODE_DPAD_LEFT); focus(ids[98].value)
        scenario!!.onActivity { BrowseProbeTestHooks.beforeSave = null; hold.complete(Unit) }
        until { var done = false; runtime { done = !it.busy }; done }
        assertTrue(semantics().any { it.tag == "候选浏览" }); focus(ids[98].value)
        runtime { assertFalse(it.isDetail) }
    }
    @Test fun direction_during_detail_back_flush_cannot_revive_saved_focus() {
        launch(); openDetail(); val hold = holdNextSave()
        key(KeyEvent.KEYCODE_BACK)
        until { var waiting = false; runtime { waiting = it.busy && BrowseProbeTestHooks.waitingSaves.get() > 0 }; waiting }
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        scenario!!.onActivity { BrowseProbeTestHooks.beforeSave = null; hold.complete(Unit) }
        until { semantics().any { it.tag == "候选浏览" } }
        focus("片库")
        assertFalse(semantics().any { it.tag == ids[99].value && it.focused })
        runtime { assertNull(it.restoreToken) }
    }
    @Test fun rejected_focus_then_resize_requires_new_placement_pass() {
        BrowseProbeTestHooks.disableWorkFocus = true; launch(false)
        until { BrowseProbeTestHooks.failedFocusRequests.get() > 0 }
        var oldPass: RestoreLayoutToken? = null
        runtime { oldPass = it.layoutToken }
        scenario!!.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        until { var portrait = false; runtime { portrait = it.viewportHeight > it.viewportWidth }; portrait }
        runtime { assertNotEquals("实际尺寸变化必须废除拒绝焦点时的旧pass", oldPass, it.layoutToken) }
        scenario!!.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        until { var landscape = false; runtime { landscape = it.viewportWidth > it.viewportHeight }; landscape }
        scenario!!.onActivity { BrowseProbeTestHooks.disableWorkFocus = false }
        focus(ids[99].value); assertUniqueFocus()
    }
    @Test fun held_ok_repeat_during_flush_keeps_single_accepted_entry() {
        launch(); val hold = holdNextSave()
        val down = android.os.SystemClock.uptimeMillis()
        instrumentation.sendKeySync(KeyEvent(down, down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
        until { var waiting = false; runtime { waiting = it.busy && BrowseProbeTestHooks.waitingSaves.get() > 0 }; waiting }
        instrumentation.sendKeySync(KeyEvent(down, down + 1, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 1))
        instrumentation.sendKeySync(KeyEvent(down, down + 2, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0))
        scenario!!.onActivity { BrowseProbeTestHooks.beforeSave = null; hold.complete(Unit) }
        until { semantics().any { it.tag == "候选详情" } }
        focus("详情返回")
        runtime { assertEquals(listOf(BrowseChildRoute.DETAIL), it.navigation!!.returnChain) }
        key(KeyEvent.KEYCODE_BACK); focus(ids[99].value); assertUniqueFocus()
    }
    @Test fun final_back_exits_activity() {
        launch(); key(KeyEvent.KEYCODE_BACK)
        until { scenario!!.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
    }
}
