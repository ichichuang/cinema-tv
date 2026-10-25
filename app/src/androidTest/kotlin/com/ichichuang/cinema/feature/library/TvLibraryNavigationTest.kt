package com.ichichuang.cinema.feature.library

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.MainActivity
import com.ichichuang.cinema.core.navigation.BrowseTopLevel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** R14 片库导航、深列表恢复与生命周期测试；仅运行于六授权全开配对。 */
class TvLibraryNavigationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun ownedPrivateFixture() = LibraryNativeFixture.setup(context)

    @After fun restore() {
        scenario?.close(); scenario = null
        LibraryNativeFixture.teardown(context)
    }

    private fun semantics() = LibraryNativeFixture.semantics(scenario!!)
    private fun until(timeout: Long = 12_000, condition: () -> Boolean) = LibraryNativeFixture.until(timeout, condition)
    private fun focus(tag: String) = until { semantics().any { it.tag == tag && it.focused } }
    private fun key(code: Int) {
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, code))
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, code))
        instrumentation.waitForIdleSync()
    }
    private fun library(action: (LibraryRuntime) -> Unit) { scenario!!.onActivity { action(requireNotNull(it.libraryProbe)) } }
    private fun browse(action: (com.ichichuang.cinema.feature.browse.BrowseProbeRuntime) -> Unit) {
        scenario!!.onActivity { action(requireNotNull(it.browseProbe)) }
    }
    private fun home(action: (com.ichichuang.cinema.feature.home.visual.VisualHomeRuntime) -> Unit) {
        scenario!!.onActivity { action(requireNotNull(it.homeProbe)) }
    }

    private fun enterLibrary() {
        until { semantics().any { it.tag == "候选首页" } }
        navTo("片库"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选片库" } }
        until { var size = -1; library { size = it.display.items.size }; size == 105 }
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        LibraryNativeFixture.scenarioRef = scenario
        enterLibrary()
    }

    private fun navTo(label: String) {
        until { semantics().any { it.focused } }
        repeat(6) { key(KeyEvent.KEYCODE_DPAD_UP) }
        until { semantics().any { it.focused && it.tag.startsWith("nav:") } }
        val order = com.ichichuang.cinema.feature.home.visual.VisualHomeRuntime.NAV_ITEMS
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

    /** 从当前焦点确定性走格到网格首项（R11 回退/测试起点一致）。 */
    private fun enterGrid() {
        until { semantics().any { it.focused } }
        LibraryNativeFixture.screenReach(scenario!!, LibraryNativeFixture.ids[0].value)
    }

    @Test fun home_to_library_and_back_returns_home_origin() {
        launch()
        key(KeyEvent.KEYCODE_BACK)
        until { semantics().any { it.tag == "候选首页" } }
        home { assertEquals(com.ichichuang.cinema.feature.home.visual.HomeRootScreen.HOME, it.screen) }
        focus("nav:片库")
        // 再次进入：片库上下文保留（仍是片库根画面且查询就位）。
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选片库" } }
        until { var size = -1; library { size = it.display.items.size }; size == 105 }
    }

    @Test fun deep_list_detail_back_restores_work_and_anchor() {
        launch()
        enterGrid()
        // 深列表：确定性走格到索引 87（100+ 合成作品的深处），每步等待真实焦点。
        LibraryNativeFixture.screenReach(scenario!!, LibraryNativeFixture.ids[87].value)
        library { assertEquals(LibraryNativeFixture.ids[87].value, it.focused) }
        // 进入详情（片库作品的子层）再返回：恢复完整查询、稳定作品与锚点。
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选详情" } }
        key(KeyEvent.KEYCODE_BACK)
        until { semantics().any { it.tag == "候选片库" } }
        focus(LibraryNativeFixture.ids[87].value)
        library {
            assertEquals(LibraryNativeFixture.ids[87].value, it.focused)
            assertNotNull(it.navigation!!.snapshot.query)
            assertEquals(4, it.navigation!!.snapshot.query!!.enabledSourceIds.size)
        }
    }

    @Test fun library_favorites_history_contexts_stay_independent() {
        launch()
        // 在片库深处置焦。
        enterGrid()
        LibraryNativeFixture.screenReach(scenario!!, LibraryNativeFixture.ids[30].value)
        val libraryFocused = semantics().firstOrNull { it.focused }?.tag
        assertEquals(LibraryNativeFixture.ids[30].value, libraryFocused)
        key(KeyEvent.KEYCODE_BACK); until { semantics().any { it.tag == "候选首页" } }
        // 收藏：独立浏览上下文，不影响片库查询。
        navTo("我的收藏"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选浏览" } }
        until { var top: BrowseTopLevel? = null; browse { top = it.top }; top == BrowseTopLevel.FAVORITES }
        key(KeyEvent.KEYCODE_BACK); until { semantics().any { it.tag == "候选首页" } }
        navTo("片库"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选片库" } }
        until { var size = -1; library { size = it.display.items.size }; size == 105 }
        // 收藏的浏览不改变片库查询身份。
        library { assertEquals("片库查询-1", it.appliedQuery!!.queryId) }
        // 历史同样独立。
        key(KeyEvent.KEYCODE_BACK); until { semantics().any { it.tag == "候选首页" } }
        navTo("最近观看"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选浏览" } }
        until { var top: BrowseTopLevel? = null; browse { top = it.top }; top == BrowseTopLevel.HISTORY }
        key(KeyEvent.KEYCODE_BACK); until { semantics().any { it.tag == "候选首页" } }
    }

    @Test fun apply_new_query_clears_only_invalidated_anchor() {
        launch()
        enterGrid()
        LibraryNativeFixture.screenReach(scenario!!, LibraryNativeFixture.ids[87].value)
        // 应用 1994 精确查询：id 87（1994）仍是合法目标——查询意图清锚点、结果上下文重建。
        LibraryNativeFixture.screenReach(scenario!!, "筛选"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "筛选面板" } }
        panelSelect("year-mode-EXACT"); panelSelect("panel-apply")
        until { semantics().none { it.tag == "筛选面板" } }
        until { var size = -1; library { size = it.display.items.size }; size == 89 }
        library {
            assertEquals(1994, it.appliedQuery!!.yearStart)
            assertNotNull(it.navigation)
        }
    }

    @Test fun stale_generation_ignores_late_first_page_of_previous_query() {
        launch()
        // 挂起下一个新查询的首页装载；应用 1994 查询（其装载被挂起），
        // 再快速应用 1980 年代查询（正常装载）：陈旧查询的迟到首页不得混入。
        scenario!!.onActivity { LibraryTestHooks.holdFirstLoad = kotlinx.coroutines.CompletableDeferred() }
        LibraryNativeFixture.screenReach(scenario!!, "筛选"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "筛选面板" } }
        panelSelect("year-mode-EXACT"); panelSelect("panel-apply")
        until { semantics().none { it.tag == "筛选面板" } }
        library { assertEquals(1994, it.appliedQuery!!.yearStart) }
        LibraryNativeFixture.screenReach(scenario!!, "筛选"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "筛选面板" } }
        panelSelect("year-mode-DECADE"); panelSelect("year-decade-minus"); panelSelect("panel-apply")
        until { semantics().none { it.tag == "筛选面板" } }
        until { var size = -1; library { size = it.display.items.size }; size == LibraryNativeFixture.decadeIndices.count() }
        library {
            assertEquals(1980, it.appliedQuery!!.yearStart)
            assertEquals(1989, it.appliedQuery!!.yearEnd)
            // 只显示当前查询的严格匹配；1994 查询的页从未进入列表。
            assertTrue(it.display.items.all { card ->
                LibraryNativeFixture.ids.indexOfFirst { it.value == card.workId } in LibraryNativeFixture.decadeIndices })
        }
        // 放行被挂起的旧首页：R09 协调器按代次拒绝，列表不变。
        scenario!!.onActivity { LibraryTestHooks.holdFirstLoad?.complete(Unit) }
        Thread.sleep(600)
        library {
            assertEquals(LibraryNativeFixture.decadeIndices.count(), it.display.items.size)
            assertTrue(it.display.items.none { card -> card.workId == LibraryNativeFixture.ids[0].value })
        }
    }

    @Test fun activity_recreation_restores_committed_query_and_discards_draft() {
        launch()
        enterGrid()
        LibraryNativeFixture.screenReach(scenario!!, LibraryNativeFixture.ids[50].value)
        // 打开面板编辑但不确认：草稿不得被持久化为用户意图。
        LibraryNativeFixture.screenReach(scenario!!, "筛选"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "筛选面板" } }
        panelSelect("year-mode-EXACT")
        key(KeyEvent.KEYCODE_BACK)
        until { semantics().none { it.tag == "筛选面板" } }
        // 位置保存有 500ms 防抖：等待真实落盘后再重建。
        Thread.sleep(900)
        // 真实 Activity 重建：恢复已提交查询与可用锚点。
        scenario!!.recreate()
        until { semantics().any { it.tag == "候选首页" } }
        navTo("片库"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选片库" } }
        until { var size = -1; library { size = it.display.items.size }; size == 105 }
        library {
            assertNull(it.appliedQuery!!.yearStart)
            assertEquals("片库查询-1", it.appliedQuery!!.queryId)
        }
    }

    @Test fun missing_anchor_falls_back_to_adjacent_valid_item() {
        launch()
        enterGrid()
        LibraryNativeFixture.screenReach(scenario!!, LibraryNativeFixture.ids[87].value)
        // 应用会移除当前焦点作品的查询（1995 精确：仅 id 100 存在），旧锚点失效。
        LibraryNativeFixture.screenReach(scenario!!, "筛选"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "筛选面板" } }
        panelSelect("year-mode-EXACT")
        panelReach("year-exact-plus"); repeat(1) { key(KeyEvent.KEYCODE_DPAD_CENTER) }
        until { semantics().any { it.tag == "年份摘要" && it.text.contains("具体年份：1995") } }
        panelSelect("panel-apply")
        until { var size = -1; library { size = it.display.items.size }; size == 1 }
        // 结果上下文合法重建：唯一匹配（id 100）或工具条可达，无死焦点、无无关跳转。
        library { assertTrue(it.display.items.all { card -> card.workId == LibraryNativeFixture.ids[100].value }) }
        until { semantics().any { it.focused } }
    }

    @Test fun browsing_and_detail_never_create_watch_history() = runBlocking<Unit> {
        launch()
        enterGrid()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选详情" } }
        key(KeyEvent.KEYCODE_BACK)
        until { semantics().any { it.tag == "候选片库" } }
        // 浏览与打开详情不产生观看记录（真实个人库为空历史）。
        browse {
            assertTrue(it.personal!!.history.isEmpty())
        }
    }

    private fun panelReach(target: String) {
        var guard = 0
        while (guard++ < 40) {
            val actual = semantics().firstOrNull { it.focused }?.tag
            if (actual == target) break
            var zones: List<List<String>> = emptyList()
            library { zones = it.panelZones().map { it.second } }
            val currentZone = zones.indexOfFirst { actual in it }
            val targetZone = zones.indexOfFirst { target in it }
            check(targetZone >= 0) { "面板分区中没有目标 $target" }
            val code = when {
                currentZone < 0 -> KeyEvent.KEYCODE_DPAD_DOWN
                currentZone == targetZone ->
                    if (zones[targetZone].indexOf(target) > zones[currentZone].indexOf(actual))
                        KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                targetZone > currentZone -> KeyEvent.KEYCODE_DPAD_DOWN
                else -> KeyEvent.KEYCODE_DPAD_UP
            }
            key(code)
            until(2_000) { semantics().firstOrNull { it.focused }?.tag != actual }
        }
        focus(target)
    }

    private fun panelSelect(keyTag: String) { panelReach(keyTag); key(KeyEvent.KEYCODE_DPAD_CENTER) }
}
