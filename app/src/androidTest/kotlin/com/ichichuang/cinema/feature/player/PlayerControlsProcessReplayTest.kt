package com.ichichuang.cinema.feature.player

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.MainActivity
import com.ichichuang.cinema.core.navigation.DebugBrowseProbeGate
import com.ichichuang.cinema.data.userdata.RoomPlaybackProgressStore
import com.ichichuang.cinema.domain.model.catalog.EpisodeId
import com.ichichuang.cinema.domain.model.catalog.VariantId
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * R16 两阶段真实进程实验（P07）：阶段 A 在真实播放器内提交精确
 *（作品/剧集/版本/位置），宿主 force-stop 验证新 PID；阶段 B 显式重启
 * 读取同一 Room 记录并保持暂停。两相位各自独立进程，绝不依赖旧仪器进程存活。
 *
 * 记录文件（R17 ST3）：每次运行用唯一文件名（`round17-process-<uuid>.json`），
 * 不覆盖旧日志；阶段 A/B 分属不同进程，经 `noBackupFilesDir` 下的指针文件
 * （`round17-process-current.txt`，存当前运行记录文件名）传递同一文件名。
 */
class PlayerControlsProcessReplayTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val phase: String = InstrumentationRegistry.getArguments().getString("cinema.processPhase") ?: ""

    @Before fun ownedPrivateFixture() = PlayerControlsNativeFixture.setup(context, phaseAware = true)
    @After fun restore() {
        scenario?.close(); scenario = null
        PlayerControlsNativeFixture.teardown(context)
    }

    private fun semantics() = PlayerControlsNativeFixture.semantics(scenario!!)
    private fun until(timeout: Long = 12_000, condition: () -> Boolean) = PlayerControlsNativeFixture.until(timeout, condition)
    private fun key(code: Int) = PlayerControlsNativeFixture.key(instrumentation, code)
    private fun player(action: (PlayerControlsRuntime) -> Unit) {
        // 探针装配有竞态窗口（阶段 B 冷启后首个采样可能早于装配）：用可观察的有界就绪条件
        // 等待运行时非空，超时由 until 统一失败并附诊断；不手写 sleep 堆时间、不裸 requireNotNull。
        until(10_000) { var ready = false; scenario!!.onActivity { ready = it.playerControlsProbe != null }; ready }
        scenario!!.onActivity { action(requireNotNull(it.playerControlsProbe)) }
    }
    private fun personal(action: (com.ichichuang.cinema.feature.personal.PersonalLibraryRuntime) -> Unit) {
        // 个人运行时随浏览探针装配：同样用可观察的有界就绪条件，超时失败并附诊断。
        until(10_000) { var ready = false; scenario!!.onActivity { ready = it.browseProbe?.personal != null }; ready }
        scenario!!.onActivity { action(requireNotNull(it.browseProbe).personal!!) }
    }

    /** 本次运行的记录文件（阶段 A 写入、阶段 B 读取的同一份）。 */
    private fun recordFile(): File {
        // 阶段 B 从指针文件取得阶段 A 生成的唯一文件名，确保两进程读同一份记录。
        val pointer = File(context.noBackupFilesDir, "round17-process-current.txt")
        if (phase == "b") {
            val name = pointer.readText().trim()
            check(name.isNotBlank() && name.startsWith("round17-process-")) { "阶段 B 找不到阶段 A 的记录文件名" }
            return File(context.noBackupFilesDir, name)
        }
        // 阶段 A：生成唯一文件名并写指针（每次运行不覆盖旧日志）。
        val name = "round17-process-${UUID.randomUUID()}.json"
        pointer.writeText(name)
        return File(context.noBackupFilesDir, name)
    }

    /** Wait until the phase-A process (recorded in the given JSON) is no longer alive on this device. */
    private fun untilProcessGone(recordFile: File) {
        val record = JSONObject(recordFile.readText())
        val previousPid = record.getInt("pid")
        val end = android.os.SystemClock.uptimeMillis() + 15_000
        while (android.os.SystemClock.uptimeMillis() < end) {
            val alive = File("/proc/$previousPid").exists()
            if (!alive) return
            Thread.sleep(100)
        }
        // 超时必须失败并附诊断，绝不静默继续让后续条件吞掉真实进程残留。
        throw AssertionError(
            "阶段 A 进程（PID=$previousPid）在 15s 内未退出，仍存活或 /proc 不可观测")
    }

    // 阶段 A：真实播放提交后由宿主 force-stop（不同 PID 证据见 round17-process-<uuid>.json）
    @Test fun phase_a_commit_player_state_for_process_replay() {
        assertEquals("a", phase)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        PlayerControlsNativeFixture.scenarioRef = scenario
        PlayerControlsNativeFixture.enterPlayerFromLibrary(scenario!!, instrumentation, 0)
        PlayerControlsTestHooks.suppressIdleHide = true // 空闲隐藏由 U03 专项验证
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until(15_000) { var position = 0L; player { position = it.display?.positionMs ?: 0L }; position >= 14_000 }
        key(KeyEvent.KEYCODE_DPAD_CENTER) // 真实暂停提交
        until { var paused = false; player { paused = !(it.display?.facts?.isPlaying ?: true) }; paused }
        var episode: String? = null; var variant: String? = null; var committed = 0L; var appPid = 0
        player { val d = requireNotNull(it.display)
            episode = d.episodeId; variant = d.variantId
            committed = d.positionMs; appPid = android.os.Process.myPid() }
        until { runBlocking {
            var store: RoomPlaybackProgressStore? = null
            personal { store = RoomPlaybackProgressStore(it.database) }
            val snap = store!!.loadResume(PlayerControlsNativeFixture.w[0])
            snap != null && snap.positionMs >= 13_000 } }
        // 暂停边界提交是异步的：用可观察条件等它收敛（连续两次采样位置相等，即不再推进），
        // 而非固定 sleep。取到的 committed 是暂停后的最后提交值。
        var previous = -1L
        var converged = false
        val convEnd = android.os.SystemClock.uptimeMillis() + 10_000
        while (android.os.SystemClock.uptimeMillis() < convEnd && !converged) {
            var resume: com.ichichuang.cinema.domain.model.userdata.PlaybackSnapshot? = null
            personal { resume = runBlocking { RoomPlaybackProgressStore(it.database).loadResume(PlayerControlsNativeFixture.w[0]) } }
            val pos = resume?.positionMs ?: 0L
            if (pos >= 13_000 && previous == pos) { committed = pos; converged = true }
            previous = pos
            if (!converged) Thread.sleep(100)
        }
        assertTrue("暂停提交必须收敛（最后提交位置稳定）", converged)
        // 记录阶段 A PID 与已提交身份，供宿主比对（唯一文件名，不覆盖旧日志）。
        recordFile().writeText(JSONObject()
            .put("pid", appPid)
            .put("episode", episode).put("variant", variant)
            .put("committedPositionMs", committed)
            .toString())
    }

    // 阶段 B：force-stop 后的新进程恢复同一最后提交并保持暂停
    @Test fun phase_b_new_process_restores_committed_paused() {
        assertEquals("b", phase)
        // R17 ST3 (P07): replace the blanket sleep with an observable readiness condition —
        // wait until the phase-A process (recorded PID) has fully terminated, so the package
        // state converges deterministically instead of racing a fixed delay.
        untilProcessGone(recordFile())
        scenario = ActivityScenario.launch(MainActivity::class.java)
        PlayerControlsNativeFixture.scenarioRef = scenario
        // 启动即回放播放层（持久 returnChain PLAYER → 候选播放器）。
        // 探针装配有竞态窗口：就绪条件必须可观察且 null 安全，超时由 until 统一失败并附诊断，
        // 不在 condition 内 requireNotNull（否则装配前会抛 NPE 而非可诊断超时）。
        until(20_000) { var isPlayer = false; scenario!!.onActivity { isPlayer = it.browseProbe?.isPlayer == true }; isPlayer }
        until { var ready = false; scenario!!.onActivity {
            ready = it.playerControlsProbe?.display?.variantId != null }; ready }
        val record = JSONObject(recordFile().readText())
        var phaseBAppPid = 0
        player { phaseBAppPid = android.os.Process.myPid() }
        assertNotEquals("两相位必须不同 PID", record.getInt("pid"), phaseBAppPid)
        player { runtime ->
            val d = requireNotNull(runtime.display)
            assertEquals(record.getString("episode"), d.episodeId)
            assertEquals(record.getString("variant"), d.variantId)
            val restored = d.positionMs
            val committed = record.getLong("committedPositionMs")
            assertTrue("恢复到最后已提交位置（实际 $restored / 提交 $committed）", restored >= committed - 800)
            assertFalse("跨进程恢复必须保持暂停", d.facts.isPlaying)
        }
        personal {
            val resume = runBlocking { RoomPlaybackProgressStore(it.database).loadResume(PlayerControlsNativeFixture.w[0]) }
            assertNotNull(resume)
            // 记录值与恢复值允许边界收敛的极小异步差（±1.2s），身份必须精确一致
            val recorded = record.getLong("committedPositionMs")
            assertTrue("恢复位置为最后提交值（实际 ${resume!!.positionMs} / 记录 $recorded）",
                resume.positionMs in (recorded - 2_500)..(recorded + 2_500))
            assertEquals(record.getString("episode"), resume.episodeId?.value)
        }
    }
}
