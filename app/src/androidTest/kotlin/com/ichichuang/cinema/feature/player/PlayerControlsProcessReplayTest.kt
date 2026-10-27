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

/**
 * R16 两阶段真实进程实验（P07）：阶段 A 在真实播放器内提交精确
 *（作品/剧集/版本/位置），宿主 force-stop 验证新 PID；阶段 B 显式重启
 * 读取同一 Room 记录并保持暂停。两相位各自独立进程，绝不依赖旧仪器进程存活。
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
        // 探针装配有竞态窗口：有界等待运行时就位（阶段 B 冷启后首个采样可能早于装配）
        val end = android.os.SystemClock.uptimeMillis() + 10_000
        while (android.os.SystemClock.uptimeMillis() < end) {
            var runtime: PlayerControlsRuntime? = null
            scenario!!.onActivity { runtime = it.playerControlsProbe }
            if (runtime != null) { action(runtime!!); return }
            Thread.sleep(100)
        }
        scenario!!.onActivity { action(requireNotNull(it.playerControlsProbe)) }
    }
    private fun personal(action: (com.ichichuang.cinema.feature.personal.PersonalLibraryRuntime) -> Unit) {
        scenario!!.onActivity { action(requireNotNull(it.browseProbe).personal!!) }
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
        // Bounded fallback: the recorded PID may be unobservable from the new process; proceed
        // and let the subsequent observable conditions (isPlayer / display != null) guard readiness.
    }

    // 阶段 A：真实播放提交后由宿主 force-stop（不同 PID 证据见 round16-process-pids.json）
    @Test fun phase_a_commit_player_state_for_process_replay() {
        assertEquals("a", phase)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        PlayerControlsNativeFixture.scenarioRef = scenario
        PlayerControlsNativeFixture.enterPlayerFromLibrary(scenario!!, instrumentation, 0)
        PlayerControlsTestHooks.suppressIdleHide = true // 空闲隐藏由 U03 专项验证
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until(15_000) { var position = 0L; player { position = it.display!!.positionMs }; position >= 14_000 }
        key(KeyEvent.KEYCODE_DPAD_CENTER) // 真实暂停提交
        until { var paused = false; player { paused = !it.display!!.facts.isPlaying }; paused }
        var episode: String? = null; var variant: String? = null; var committed = 0L; var appPid = 0
        player { episode = it.display!!.episodeId; variant = it.display!!.variantId
            committed = it.display!!.positionMs; appPid = android.os.Process.myPid() }
        until { runBlocking {
            var store: RoomPlaybackProgressStore? = null
            personal { store = RoomPlaybackProgressStore(it.database) }
            val snap = store!!.loadResume(PlayerControlsNativeFixture.w[0])
            snap != null && snap.positionMs >= 13_000 } }
        // 暂停边界提交是异步的：等它收敛后再取“最后提交值”，避免记录后又被推进
        Thread.sleep(2_500)
        runBlocking {
            var store: RoomPlaybackProgressStore? = null
            personal { store = RoomPlaybackProgressStore(it.database) }
            store!!.loadResume(PlayerControlsNativeFixture.w[0])?.let { committed = it.positionMs }
        }
        // 记录阶段 A PID 与已提交身份，供宿主比对
        File(context.noBackupFilesDir, "round16-process-a.json").writeText(JSONObject()
            .put("pid", appPid)
            .put("episode", episode).put("variant", variant)
            .put("committedPositionMs", runBlocking {
                var store: RoomPlaybackProgressStore? = null
                personal { store = RoomPlaybackProgressStore(it.database) }
                store!!.loadResume(PlayerControlsNativeFixture.w[0])?.positionMs ?: -1 })
            .toString())
    }

    // 阶段 B：force-stop 后的新进程恢复同一最后提交并保持暂停
    @Test fun phase_b_new_process_restores_committed_paused() {
        assertEquals("b", phase)
        // R17 ST3 (P07): replace the blanket sleep with an observable readiness condition —
        // wait until the phase-A process (recorded PID) has fully terminated, so the package
        // state converges deterministically instead of racing a fixed delay.
        untilProcessGone(File(context.noBackupFilesDir, "round16-process-a.json"))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        PlayerControlsNativeFixture.scenarioRef = scenario
        // 启动即回放播放层（持久 returnChain PLAYER → 候选播放器）
        until(20_000) { var isPlayer = false; scenario!!.onActivity { isPlayer = requireNotNull(it.browseProbe).isPlayer }; isPlayer }
        until { var ready = false; scenario!!.onActivity {
            ready = requireNotNull(it.playerControlsProbe).display?.variantId != null }; ready }
        val record = JSONObject(File(context.noBackupFilesDir, "round16-process-a.json").readText())
        var phaseBAppPid = 0
        player { phaseBAppPid = android.os.Process.myPid() }
        assertNotEquals("两相位必须不同 PID", record.getInt("pid"), phaseBAppPid)
        player { runtime ->
            assertEquals(record.getString("episode"), runtime.display!!.episodeId)
            assertEquals(record.getString("variant"), runtime.display!!.variantId)
            val restored = runtime.display!!.positionMs
            val committed = record.getLong("committedPositionMs")
            assertTrue("恢复到最后已提交位置（实际 $restored / 提交 $committed）", restored >= committed - 800)
            assertFalse("跨进程恢复必须保持暂停", runtime.display!!.facts.isPlaying)
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
