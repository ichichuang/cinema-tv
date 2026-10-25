package com.ichichuang.cinema.feature.library

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.MainActivity
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * R14 片库进程边界测试；仅运行于六授权全开配对。外部 force-stop 实验由宿主脚本
 * 单独执行并按 PROC 记录，不混入仪器化方法计数。
 */
class TvLibraryRecoveryTest {
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

    private fun enterLibrary() {
        until { semantics().any { it.tag == "候选首页" } }
        navTo("片库"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选片库" } }
        until { var size = -1; library { size = it.display.items.size }; size == 105 }
    }

    private fun enterGrid() {
        until { semantics().any { it.focused } }
        LibraryNativeFixture.screenReach(scenario!!, LibraryNativeFixture.ids[0].value)
    }

    private fun panelSelect(keyTag: String) {
        // 面板走格（与 UI 测试同源的确定性导航）。
        var guard = 0
        while (guard++ < 40) {
            val actual = semantics().firstOrNull { it.focused }?.tag
            if (actual == keyTag) break
            var zones: List<List<String>> = emptyList()
            library { zones = it.panelZones().map { it.second } }
            val currentZone = zones.indexOfFirst { actual in it }
            val targetZone = zones.indexOfFirst { keyTag in it }
            check(targetZone >= 0) { "面板分区中没有目标 $keyTag" }
            val code = when {
                currentZone < 0 -> KeyEvent.KEYCODE_DPAD_DOWN
                currentZone == targetZone ->
                    if (zones[targetZone].indexOf(keyTag) > zones[currentZone].indexOf(actual))
                        KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                targetZone > currentZone -> KeyEvent.KEYCODE_DPAD_DOWN
                else -> KeyEvent.KEYCODE_DPAD_UP
            }
            key(code)
            until(2_000) { semantics().firstOrNull { it.focused }?.tag != actual }
        }
        focus(keyTag); key(KeyEvent.KEYCODE_DPAD_CENTER)
    }

    /** 已提交查询经真实 Room v6 打开-关闭-重开往返：全部字段逐字保留（F06 原生侧）。 */
    @Test fun room_close_reopen_preserves_full_committed_query() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        LibraryNativeFixture.scenarioRef = scenario
        enterLibrary()
        // 应用多维查询：1994 + 非华语 + 日本 + 恐怖 + 从早到晚排序 + 两来源。
        LibraryNativeFixture.screenReach(scenario!!, "筛选"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "筛选面板" } }
        panelSelect("year-mode-EXACT")
        panelSelect("lang-NON_CHINESE")
        panelSelect("country-JP")
        panelSelect("genre-horror")
        panelSelect("sort-YEAR_ASCENDING")
        panelSelect("source-broken-source")
        panelSelect("panel-apply")
        until { semantics().none { it.tag == "筛选面板" } }
        // 1994 + 非华语 + 日本 + 恐怖：排除 1995/1987、美国（101）与剧情（102）→ 87 项。
        until { var size = -1; library { size = it.display.items.size }; size == 87 }
        val applied = arrayOfNulls<com.ichichuang.cinema.domain.model.catalog.CatalogQuery>(1)
        library { applied[0] = it.appliedQuery }
        Thread.sleep(900) // 等待位置提交真实落盘
        // Activity 重建 = 租约内数据库真实关闭重开（MainActivity 销毁即关库）。
        scenario!!.recreate()
        until { semantics().any { it.tag == "候选首页" } }
        navTo("片库"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选片库" } }
        until { var size = -1; library { size = it.display.items.size }; size == 87 }
        library {
            val restored = it.appliedQuery!!
            assertEquals(applied[0]!!.queryId, restored.queryId)
            assertEquals(applied[0]!!.yearStart, restored.yearStart)
            assertEquals(applied[0]!!.yearEnd, restored.yearEnd)
            assertEquals(applied[0]!!.languageScope, restored.languageScope)
            assertEquals(applied[0]!!.countryIds, restored.countryIds)
            assertEquals(applied[0]!!.genreIds, restored.genreIds)
            assertEquals(applied[0]!!.sort, restored.sort)
            assertEquals(applied[0]!!.enabledSourceIds, restored.enabledSourceIds)
        }
    }

    /** 真实进程终止（am kill，仅后台进程）：重开恢复最近一次已提交查询。 */
    @Test fun am_kill_reopens_committed_query() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        LibraryNativeFixture.scenarioRef = scenario
        enterLibrary()
        LibraryNativeFixture.screenReach(scenario!!, "筛选"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "筛选面板" } }
        panelSelect("year-mode-DECADE")
        panelSelect("year-decade-minus")
        panelSelect("panel-apply")
        until { var size = -1; library { size = it.display.items.size }; size == LibraryNativeFixture.decadeIndices.count() }
        Thread.sleep(900)
        // 后台化后被系统 am kill：不得与仪器化进程冲突。
        scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        instrumentation.uiAutomation.executeShellCommand("am kill com.ichichuang.cinema.debug").close()
        Thread.sleep(800)
        scenario!!.close(); scenario = null
        scenario = ActivityScenario.launch(MainActivity::class.java)
        LibraryNativeFixture.scenarioRef = scenario
        until { semantics().any { it.tag == "候选首页" } }
        navTo("片库"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选片库" } }
        until { var size = -1; library { size = it.display.items.size }; size == LibraryNativeFixture.decadeIndices.count() }
        library {
            assertEquals(1980, it.appliedQuery!!.yearStart)
            assertEquals(1989, it.appliedQuery!!.yearEnd)
        }
    }

    /** 重建后草稿丢弃：未确认的编辑不成为持久化用户意图（N10）。 */
    @Test fun uncommitted_draft_discarded_across_recreate() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        LibraryNativeFixture.scenarioRef = scenario
        enterLibrary()
        LibraryNativeFixture.screenReach(scenario!!, "筛选"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "筛选面板" } }
        panelSelect("lang-CHINESE")
        scenario!!.recreate()
        until { semantics().any { it.tag == "候选首页" } }
        navTo("片库"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选片库" } }
        until { var size = -1; library { size = it.display.items.size }; size == 105 }
        library {
            assertEquals(com.ichichuang.cinema.domain.model.catalog.LanguageScope.ALL, it.appliedQuery!!.languageScope)
            assertFalse(it.panelOpen)
        }
    }
}
