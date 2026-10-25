package com.ichichuang.cinema.feature.detail

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * R15 生命周期测试：Activity 重建恢复已提交详情身份与来源，未提交弹层不复活（N08）。
 * 重建后子层按 R11 权威回到已保存顶层来源（详情子层不跨重建驻留），已提交的
 * （作品, 线路, 剧集）上下文经一次性私有档案恢复。
 */
class WorkDetailLifecycleNativeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun ownedPrivateFixture() = WorkDetailNativeFixture.setup(context)

    @After fun restore() {
        scenario?.close(); scenario = null
        WorkDetailNativeFixture.teardown(context)
    }

    private fun semantics() = WorkDetailNativeFixture.semantics(scenario!!)
    private fun until(timeout: Long = 12_000, condition: () -> Boolean) = WorkDetailNativeFixture.until(timeout, condition)
    private fun key(code: Int) {
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, code))
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, code))
        instrumentation.waitForIdleSync()
    }
    private fun detail(action: (WorkDetailRuntime) -> Unit) { scenario!!.onActivity { action(requireNotNull(it.detailProbe)) } }
    private fun library(action: (com.ichichuang.cinema.feature.library.LibraryRuntime) -> Unit) {
        scenario!!.onActivity { action(requireNotNull(it.libraryProbe)) }
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        WorkDetailNativeFixture.scenarioRef = scenario
        until { semantics().any { it.tag == "候选首页" } }
    }

    private fun enterLibrary() {
        until { semantics().any { it.focused } }
        repeat(6) { key(KeyEvent.KEYCODE_DPAD_UP) }
        until { semantics().any { it.focused && it.tag.startsWith("nav:") } }
        var steps = 0
        val order = com.ichichuang.cinema.feature.home.visual.VisualHomeRuntime.NAV_ITEMS
        while (steps++ < 8) {
            val current = semantics().firstOrNull { it.focused && it.tag.startsWith("nav:") }?.tag
            if (current == "nav:片库") break
            key(KeyEvent.KEYCODE_DPAD_RIGHT)
            until(2_000) { semantics().firstOrNull { it.focused && it.tag.startsWith("nav:") }?.tag != current }
        }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选片库" } }
        until { var size = -1; library { size = it.display.items.size }; size == 6 }
    }

    // N08 重建后：回到已保存来源（片库查询/焦点），重开详情恢复已提交选择，弹层不复活
    @Test fun activity_recreate_restores_committed_detail_context_not_uncommitted_sheet() {
        launch(); enterLibrary()
        com.ichichuang.cinema.feature.library.LibraryNativeFixture.screenReach(scenario!!, WorkDetailNativeFixture.w[0].value)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选详情" } }
        detail { it.activate("线路:${WorkDetailNativeFixture.VARIANT_B}") }
        until { var route: String? = null; detail { route = it.controller.selectedRouteId }; route == WorkDetailNativeFixture.VARIANT_B }
        // 未提交状态：打开线路单再取消（弹层属于未提交 UI 状态，重建后不得复活）。
        detail { it.activate("选择线路") }
        until { semantics().any { it.tag == "线路单" } }
        key(KeyEvent.KEYCODE_BACK)
        until { semantics().none { it.tag == "线路单" } }
        // 返回片库来源后做 Activity 重建（R14 同法：子层不跨重建驻留的等价状态）。
        key(KeyEvent.KEYCODE_BACK)
        until { semantics().any { it.tag == "候选片库" } }
        Thread.sleep(900)
        scenario!!.recreate()
        until(25_000) { semantics().any { it.tag == "候选首页" } }
        var libraryReady = false
        scenario!!.onActivity { libraryReady = it.libraryProbe != null && it.homeProbe != null }
        assertTrue("重建后片库运行时应就位", libraryReady)
        // R11 权威：子层不跨重建驻留；重建后经首页既有入口重新进入片库（D-pad 导航已在
        // 其他用例覆盖），验证已保存来源与查询恢复。
        scenario!!.onActivity { requireNotNull(it.homeProbe).openTopDestination("片库") }
        until { semantics().any { it.tag == "候选片库" } }
        until { var size = -1; library { size = it.display.items.size }; size == 6 }
        // 重开同一作品：已提交（线路, 剧集）上下文恢复；弹层不复活。
        com.ichichuang.cinema.feature.library.LibraryNativeFixture.screenReach(scenario!!, WorkDetailNativeFixture.w[0].value)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选详情" } }
        until { var id: String? = null; detail { id = it.installedWorkId }; id == WorkDetailNativeFixture.w[0].value }
        detail {
            assertEquals(WorkDetailNativeFixture.VARIANT_B, it.controller.selectedRouteId)
            assertEquals(WorkDetailNativeFixture.EPISODE_B1, it.controller.selectedEpisodeId)
            assertNull(it.controller.sheet)
        }
        assertTrue(semantics().none { it.tag == "线路单" })
    }
}
