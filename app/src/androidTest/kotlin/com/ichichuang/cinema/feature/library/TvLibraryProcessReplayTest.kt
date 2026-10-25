package com.ichichuang.cinema.feature.library

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.MainActivity
import com.ichichuang.cinema.domain.model.catalog.CatalogQuery
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * 两阶段真实进程实验（R12 宿主编排惯例）：phase_a 在真实 UI 应用 1994 查询并提交，
 * 宿主随后 am force-stop 结束该进程；phase_b 在全新进程核验最近一次已提交查询恢复、
 * 浏览不产生观看记录。两方法必须由宿主脚本分开执行，不与其它类混跑。
 */
class TvLibraryProcessReplayTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun ownedPrivateFixture() {
        // 阶段 B 保留阶段 A 的数据库与档案（真实进程死亡后恢复的正是这份现场）。
        val phaseB = File(context.noBackupFilesDir, "round14-process.expected.json").isFile
        LibraryNativeFixture.setup(context, phaseAware = phaseB)
        if (phaseB) File(context.noBackupFilesDir, "round14-phase-b.marker").writeText("1")
    }

    @After fun restore() {
        scenario?.close(); scenario = null
        // 进程实验不清理私有档案：宿主在两相位间保留数据；全部相位结束后由宿主卸载清场。
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

    private fun enterLibrary(expectedItems: Int = 105) {
        until { semantics().any { it.tag == "候选首页" } }
        navTo("片库"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选片库" } }
        until { var size = -1; library { size = it.display.items.size }; size == expectedItems }
    }

    private fun panelSelect(keyTag: String) {
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

    /** 阶段 A：真实 UI 应用 1994 精确查询，等待提交落盘并记录 PID 与期望查询。宿主随后结束本进程。 */
    @Test fun phase_a_commit_library_query_for_process_replay() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        LibraryNativeFixture.scenarioRef = scenario
        enterLibrary()
        LibraryNativeFixture.screenReach(scenario!!, "筛选"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "筛选面板" } }
        panelSelect("year-mode-EXACT")
        panelSelect("panel-apply")
        until { var size = -1; library { size = it.display.items.size }; size == 89 }
        var committed: CatalogQuery? = null
        library { committed = it.appliedQuery }
        Thread.sleep(900) // 位置提交防抖落盘
        var lastCommit: com.ichichuang.cinema.domain.model.userdata.CommitResult? = null
        library { lastCommit = it.lastCommit }
        File(context.noBackupFilesDir, "round14-process.pid").writeText(android.os.Process.myPid().toString())
        File(context.noBackupFilesDir, "round14-process.expected.json").writeText(JSONObject()
            .put("queryId", committed!!.queryId)
            .put("yearStart", committed!!.yearStart)
            .put("yearEnd", committed!!.yearEnd)
            .put("sources", org.json.JSONArray(committed!!.enabledSourceIds.map { it.value }))
            .toString())
        println("ROUND14_PHASE_A pid=${android.os.Process.myPid()} commit=$lastCommit query=${committed!!.queryId} year=${committed!!.yearStart}")
        // 不在本方法内模拟重开：宿主在本进程结束后进入阶段 B。
    }

    /** 阶段 B：全新进程重开片库，恢复阶段 A 已提交查询；仅浏览不产生观看记录。 */
    @Test fun phase_b_new_process_restores_committed_query_without_history() {
        val previousPid = File(context.noBackupFilesDir, "round14-process.pid").readText().toInt()
        assertNotEquals("必须运行在真实新进程", previousPid, android.os.Process.myPid())
        val expected = JSONObject(File(context.noBackupFilesDir, "round14-process.expected.json").readText())
        scenario = ActivityScenario.launch(MainActivity::class.java)
        LibraryNativeFixture.scenarioRef = scenario
        // 阶段 A 已提交 1994 查询：新进程直接以其恢复（89 项），不再是默认 105 项。
        enterLibrary(expectedItems = 89)
        library {
            assertEquals(expected.getString("queryId"), it.appliedQuery!!.queryId)
            assertEquals(expected.get("yearStart") as Int, it.appliedQuery!!.yearStart)
            assertEquals(expected.get("yearEnd") as Int, it.appliedQuery!!.yearEnd)
        }
        // 重建后浏览/开面板不产生观看记录（仅浏览不写入历史）。
        LibraryNativeFixture.screenReach(scenario!!, "筛选"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "筛选面板" } }
        key(KeyEvent.KEYCODE_BACK)
        until { semantics().none { it.tag == "筛选面板" } }
        scenario!!.onActivity { activity ->
            assertTrue("片库浏览不得产生观看记录",
                requireNotNull(activity.browseProbe).personal!!.history.isEmpty())
        }
        println("ROUND14_PHASE_B pid=${android.os.Process.myPid()} previous=$previousPid restored=${expected.getString("queryId")}")
    }
}
