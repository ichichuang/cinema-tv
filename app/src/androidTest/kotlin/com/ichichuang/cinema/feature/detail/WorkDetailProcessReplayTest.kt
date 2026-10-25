package com.ichichuang.cinema.feature.detail

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.MainActivity
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * R15 两阶段真实进程实验（N09）：阶段 A 提交详情上下文（作品/线路/剧集）后宿主
 * force-stop；阶段 B 以新 PID 复原：仅保证已提交数据 —— 来源上下文、已提交线路
 * 剧集与 8000ms 暂停续播保持，未提交弹层不复活。
 */
class WorkDetailProcessReplayTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val phase = InstrumentationRegistry.getArguments().getString("cinema.detail.phase")

    @Before fun ownedPrivateFixture() {
        // 阶段 B 保留既有数据库与档案（phaseAware）；阶段 A 清库重写。
        WorkDetailNativeFixture.setup(context, phaseAware = phase == "b")
    }

    @After fun restore() {
        scenario?.close(); scenario = null
        // 两阶段实验共享档案与已提交上下文：任一阶段结束时都不清理（由驱动脚本最终卸载清场）。
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
        // 新进程首帧入口双路径：先首页既有入口；3 秒内未达片库再走真实 D-pad 导航。
        until { var ready = false; scenario!!.onActivity { ready = it.homeProbe != null }; ready }
        scenario!!.onActivity { requireNotNull(it.homeProbe).openTopDestination("片库") }
        runCatching { until(3_000) { semantics().any { it.tag == "候选片库" } } }
            .onFailure {
                until { semantics().any { it.focused } }
                repeat(6) { key(KeyEvent.KEYCODE_DPAD_UP) }
                until { semantics().any { it.focused && it.tag.startsWith("nav:") } }
                var steps = 0
                while (steps++ < 8) {
                    val current = semantics().firstOrNull { it.focused && it.tag.startsWith("nav:") }?.tag
                    if (current == "nav:片库") break
                    key(KeyEvent.KEYCODE_DPAD_RIGHT)
                    until(2_000) { semantics().firstOrNull { it.focused && it.tag.startsWith("nav:") }?.tag != current }
                }
                key(KeyEvent.KEYCODE_DPAD_CENTER)
            }
        until { semantics().any { it.tag == "候选片库" } }
        until { var size = -1; library { size = it.display.items.size }; size == 6 }
    }

    @Test fun phase_a_commit_detail_context_for_process_replay() {
        check(phase == "a") { "本方法仅限阶段 A" }
        launch(); enterLibrary()
        com.ichichuang.cinema.feature.library.LibraryNativeFixture.screenReach(scenario!!, WorkDetailNativeFixture.w[0].value)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选详情" } }
        detail { it.activate("线路:${WorkDetailNativeFixture.VARIANT_B}") }
        until { var route: String? = null; detail { route = it.controller.selectedRouteId }; route == WorkDetailNativeFixture.VARIANT_B }
        var pidA = 0
        scenario!!.onActivity { pidA = android.os.Process.myPid() }
        File(context.noBackupFilesDir, "round15-process-pids.json").writeText(JSONObject().put("a", pidA).toString())
        // 关闭场景前让检查点/上下文落盘（详情返回一层走真实保存路径）。
        detail { it.activate("详情返回") }
        until { semantics().any { it.tag == "候选片库" } }
        assertTrue(pidA > 0)
    }

    @Test fun phase_b_new_process_restores_committed_context_and_paused_resume() {
        check(phase == "b") { "本方法仅限阶段 B" }
        launch()
        var pidB = 0
        scenario!!.onActivity { pidB = android.os.Process.myPid() }
        val pidA = JSONObject(File(context.noBackupFilesDir, "round15-process-pids.json").readText()).getLong("a").toInt()
        assertNotEquals("必须真实新进程", pidA, pidB)
        // 续播数据跨进程保留的直接证据：英雄区精确显示 epA2 · 0:08 / 0:12。
        until { semantics().any { it.tag == "hero/${WorkDetailNativeFixture.w[0].value}" && it.description.contains("0:08") } }
        assertTrue(semantics().none { it.tag == "线路单" })
        // 自首页英雄区“查看详情”重开同一作品：已提交（线路, 剧集）上下文恢复且保持精确派发。
        until { semantics().any { it.focused } }
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { semantics().any { it.tag == "继续播放" && it.focused } }
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        until { semantics().any { it.tag == "查看详情" && it.focused } }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选详情" } }
        until { var id: String? = null; detail { id = it.installedWorkId }; id == WorkDetailNativeFixture.w[0].value }
        detail {
            assertEquals(WorkDetailNativeFixture.VARIANT_B, it.controller.selectedRouteId)
            assertEquals(WorkDetailNativeFixture.EPISODE_B1, it.controller.selectedEpisodeId)
        }
        detail { it.activate("播放操作") }
        until { semantics().any { it.tag == "候选播放器" } }
        scenario!!.onActivity { activity ->
            val owner = requireNotNull(activity.browseProbe?.personal)
            assertEquals("换线目标从 0 开始", 0L, owner.requestedResumePosition)
            assertEquals(false, owner.playerPlaying)
        }
        // 8000ms 续播数据跨进程保留：回详情切回线路 A 并选 epA2，继续播放仍精确恢复且暂停。
        key(KeyEvent.KEYCODE_BACK)
        until { semantics().any { it.tag == "候选详情" } }
        detail { it.activate("线路:${WorkDetailNativeFixture.VARIANT_A}") }
        until { var route: String? = null; detail { route = it.controller.selectedRouteId }; route == WorkDetailNativeFixture.VARIANT_A }
        detail {
            val badge = it.display!!.episodes.firstOrNull { e -> e.episodeId == WorkDetailNativeFixture.EPISODE_A2 }?.resumeText
            assertNotNull("epA2 徽标必须保留", badge)
        }
        detail { it.activate("集数:${WorkDetailNativeFixture.EPISODE_A2}") }
        until { var episode: String? = null; detail { episode = it.controller.selectedEpisodeId }; episode == WorkDetailNativeFixture.EPISODE_A2 }
        detail { it.activate("播放操作") }
        until { semantics().any { it.tag == "候选播放器" } }
        scenario!!.onActivity { activity ->
            val owner = requireNotNull(activity.browseProbe?.personal)
            assertEquals(WorkDetailNativeFixture.RESUME_POSITION_MS, owner.requestedResumePosition)
            assertEquals(false, owner.playerPlaying)
        }
        assertNotNull(pidB)
    }
}
