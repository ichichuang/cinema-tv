package com.ichichuang.cinema.feature.player

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.MainActivity
import com.ichichuang.cinema.core.navigation.DebugBrowseProbeGate
import com.ichichuang.cinema.domain.model.catalog.EpisodeId
import com.ichichuang.cinema.domain.model.catalog.VariantId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * R16 进度生命周期原生测试（P01–P06/P08）：真实暂停提交、缓冲不写假零、
 * 后台保存失败不困 Back、旧会话写入被拒、返回不自动出声、Activity 重建
 * 恢复最后已提交、未提交进度不冒充持久。
 */
class PlayerControlsLifecycleNativeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun ownedPrivateFixture() = PlayerControlsNativeFixture.setup(context)
    @After fun restore() {
        PlayerControlsTestHooks.holdBoundary?.complete(Unit); PlayerControlsTestHooks.holdBoundary = null
        scenario?.close(); scenario = null
        PlayerControlsNativeFixture.teardown(context)
    }

    private fun semantics() = PlayerControlsNativeFixture.semantics(scenario!!)
    private fun until(timeout: Long = 12_000, condition: () -> Boolean) = PlayerControlsNativeFixture.until(timeout, condition)
    private fun key(code: Int) = PlayerControlsNativeFixture.key(instrumentation, code)
    private fun player(action: (PlayerControlsRuntime) -> Unit) {
        scenario!!.onActivity { action(requireNotNull(it.playerControlsProbe)) }
    }
    private fun personal(action: (com.ichichuang.cinema.feature.personal.PersonalLibraryRuntime) -> Unit) {
        scenario!!.onActivity { action(requireNotNull(it.browseProbe).personal!!) }
    }

    private fun enter(workIndex: Int = 0) {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        PlayerControlsNativeFixture.scenarioRef = scenario
        PlayerControlsNativeFixture.enterPlayerFromLibrary(scenario!!, instrumentation, workIndex)
    }

    private fun snapshotFor(episode: String = PlayerControlsNativeFixture.EPISODE_A1,
        variant: String = PlayerControlsNativeFixture.VARIANT_A) = runBlocking {
        var db: com.ichichuang.cinema.data.local.CinemaDatabase? = null
        personal { db = it.database }
        db!!.playbackSnapshots().find(PlayerControlsNativeFixture.w[0].value, episode, variant)?.snapshot()
    }

    // P01 真实暂停经既有检查点协调者提交实际位置
    @Test fun pause_commits_actual_position() {
        enter()
        PlayerControlsTestHooks.suppressIdleHide = true // 空闲隐藏由 U03 专项验证
        // 确定性建立已提交值（SEEK 提交路径）：预览 +15s → OK
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        until { var focus: String? = null; player { focus = it.controller.focusedControlId }; focus == "时间轴" }
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        until { var preview: Long? = null; player { preview = it.controller.previewMs }; preview == 23_000L }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until(45_000) { val snap = snapshotFor(); snap != null && snap.positionMs >= 22_000 }
        val committedBySeek = snapshotFor()!!.positionMs
        // 真实播放后暂停：暂停边界只会推进（或保持）已提交位置，绝不回退/写零
        key(KeyEvent.KEYCODE_DPAD_UP)
        until { var focus: String? = null; player { focus = it.controller.focusedControlId }; focus == "播放控制" }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var playing = false; player { playing = it.display!!.facts.isPlaying }; playing }
        until(12_000) { var position = 0L; player { position = it.display!!.positionMs }; position >= 23_300 }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var paused = false; player { paused = !it.display!!.facts.isPlaying }; paused }
        Thread.sleep(2_000)
        val snap = snapshotFor()
        assertNotNull("真实暂停必须经既有检查点协调者保留进度", snap)
        assertTrue("暂停不得回退或写零已提交位置（seek 提交 $committedBySeek）", snap!!.positionMs >= committedBySeek)
    }

    // P02 缓冲/装载的瞬态零不得破坏已提交续播点
    @Test fun buffering_does_not_commit_fake_zero() {
        enter()
        PlayerControlsTestHooks.suppressIdleHide = true // 空闲隐藏由 U03 专项验证
        // 确定性建立已提交值（SEEK 提交路径，不依赖 ≥5s 播放计费）
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        until { var focus: String? = null; player { focus = it.controller.focusedControlId }; focus == "时间轴" }
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        until { var preview: Long? = null; player { preview = it.controller.previewMs }; preview == 23_000L }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until(45_000) { val snap = snapshotFor(); snap != null && snap.positionMs >= 22_000 }
        val committedBeforeReload = snapshotFor()!!.positionMs
        // 触发真实重装载（与“重试播放”同一权威路径 probe.retry → 重新 prepare → 缓冲瞬态）
        runBlocking {
            var p: com.ichichuang.cinema.feature.personal.PersonalLibraryRuntime? = null
            scenario!!.onActivity { p = requireNotNull(it.browseProbe).personal }
            // 播放器访问必须在其应用线程上执行
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { p!!.retry() }
        }
        // 缓冲瞬态后检查点不被写零（不低于重装载前的已提交值）
        Thread.sleep(1_200)
        val snap = snapshotFor()
        assertNotNull(snap)
        assertTrue("瞬态零不得覆盖已提交位置", snap!!.positionMs >= committedBeforeReload)
    }

    // P03 后台/保存失败如实呈现且不无限困住 Back（真实 SQLite 独占锁）
    @Test fun background_commit_failure_exit_not_trapped() {
        enter()
        PlayerControlsTestHooks.suppressIdleHide = true // 空闲隐藏由 U03 专项验证
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until(15_000) { var position = 0L; player { position = it.display!!.positionMs }; position >= 14_000 }
        val databasePath = context.getDatabasePath(DebugBrowseProbeGate.DATABASE).absolutePath
        val blocker = SQLiteDatabase.openDatabase(databasePath, null, SQLiteDatabase.OPEN_READWRITE)
        blocker.execSQL("BEGIN EXCLUSIVE")
        var messageDuringLock = ""
        try {
            // 锁定期间真实提交失败必须如实浮出（脉冲/边界走同一队列，被锁即失败）
            until(30_000) { var message = ""; personal { message = it.message }
                message.contains("未保存") || message.contains("未完成") }
            scenario!!.onActivity { activity -> messageDuringLock = requireNotNull(activity.browseProbe).personal!!.message }
        } finally {
            blocker.execSQL("COMMIT"); blocker.close()
        }
        assertTrue("锁定期间必须如实呈现未保存", messageDuringLock.isNotBlank())
        // 解锁后：退出不得被困住（边界最终完成或按既有语义放行）
        key(KeyEvent.KEYCODE_BACK) // 收浮层
        until { var hidden = false; player { hidden = !it.controller.overlayVisible }; hidden }
        key(KeyEvent.KEYCODE_BACK) // 退出
        until(30_000) { var isPlayer = true; scenario!!.onActivity { isPlayer = requireNotNull(it.browseProbe).isPlayer }; !isPlayer }
        until { semantics().any { it.tag == "候选详情" } }
    }

    // P04 换集后旧会话的迟到操作不得写进新目标（真实 Room 行分立）
    @Test fun old_session_operation_rejected_after_switch() {
        enter()
        PlayerControlsTestHooks.suppressIdleHide = true // 空闲隐藏由 U03 专项验证
        // 确定性建立旧集已提交值（SEEK 提交路径）：预览 +15s → OK
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        until { var focus: String? = null; player { focus = it.controller.focusedControlId }; focus == "时间轴" }
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        until { var preview: Long? = null; player { preview = it.controller.previewMs }; preview == 23_000L }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until(45_000) { val old = snapshotFor(); old != null && old.positionMs >= 22_000 }
        // 换到 epA2：旧集检查点分立，新集从 0 开始（不被旧会话写入）
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { var focus: String? = null; player { focus = it.controller.focusedControlId }; focus == "选集" }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var focus: String? = null; player { focus = it.controller.pickerFocusedId }; focus != null }
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { var focus: String? = null; player { focus = it.controller.pickerFocusedId }
            focus == "picker:${PlayerControlsNativeFixture.EPISODE_A2}" }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var episode: String? = null; player { episode = it.display!!.episodeId }
            episode == PlayerControlsNativeFixture.EPISODE_A2 }
        val oldRow = snapshotFor()
        val newSnap = snapshotFor(PlayerControlsNativeFixture.EPISODE_A2, PlayerControlsNativeFixture.VARIANT_A)
        assertNotNull("旧集检查点分立存在", oldRow)
        assertTrue("旧集保持已提交值", oldRow!!.positionMs >= 22_000)
        assertTrue("新集不被旧会话写入（从 0 开始）", newSnap == null || newSnap.positionMs < 2_000)
    }

    // P05 返回后重进播放器：保持暂停直到显式播放，绝不自动出声
    @Test fun no_automatic_audio_on_return() {
        enter()
        PlayerControlsTestHooks.suppressIdleHide = true // 空闲隐藏由 U03 专项验证
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var playing = false; player { playing = it.display!!.facts.isPlaying }; playing }
        key(KeyEvent.KEYCODE_DPAD_CENTER) // 暂停
        until { var paused = false; player { paused = !it.display!!.facts.isPlaying }; paused }
        key(KeyEvent.KEYCODE_BACK) // 收浮层
        key(KeyEvent.KEYCODE_BACK) // 退出到详情
        until { var isPlayer = true; scenario!!.onActivity { isPlayer = requireNotNull(it.browseProbe).isPlayer }; !isPlayer }
        until { semantics().any { it.tag == "候选详情" } }
        // 从详情再次进入播放器（同一详情管线）
        until { semantics().any { it.focused } }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var isPlayer = false; scenario!!.onActivity { isPlayer = requireNotNull(it.browseProbe).isPlayer }; isPlayer }
        player { runtime ->
            assertFalse("重进后必须保持暂停", runtime.display!!.facts.isPlaying)
            assertFalse(runtime.display!!.facts.playWhenReady)
        }
    }

    // P06 真实 Activity 重建：从最后已提交恢复（身份/位置），无投机 seek
    @Test fun activity_recreation_uses_last_committed() {
        enter()
        PlayerControlsTestHooks.suppressIdleHide = true // 空闲隐藏由 U03 专项验证
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until(15_000) { var position = 0L; player { position = it.display!!.positionMs }; position >= 14_000 }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until(45_000) { val snap = snapshotFor(); snap != null && snap.positionMs >= 13_000 }
        val committed = snapshotFor()!!.positionMs
        val episode = runBlocking { var e: String? = null; player { e = it.display!!.episodeId }; e }
        scenario!!.recreate()
        PlayerControlsNativeFixture.scenarioRef = scenario
        until(20_000) { var rebuilt = false; scenario!!.onActivity {
            rebuilt = it.playerControlsProbe?.display?.variantId != null }; rebuilt }
        until { var isPlayer = false; scenario!!.onActivity { isPlayer = it.browseProbe?.isPlayer == true }; isPlayer }
        player { runtime ->
            assertEquals("重建恢复精确剧集", episode, runtime.display!!.episodeId)
            val restored = runtime.display!!.positionMs
            assertTrue("恢复到已提交位置（无投机 seek）", restored in (committed - 800)..(committed + 2_000))
            assertFalse("重建后保持暂停", runtime.display!!.facts.isPlaying)
        }
    }

    // P08 未提交的进度不冒充持久：报告只承诺最后已提交值
    @Test fun uncommitted_progress_not_claimed_durable() {
        enter()
        PlayerControlsTestHooks.suppressIdleHide = true // 空闲隐藏由 U03 专项验证
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var playing = false; player { playing = it.display!!.facts.isPlaying }; playing }
        val committedAtStart = snapshotFor()?.positionMs ?: PlayerControlsNativeFixture.RESUME_POSITION_MS
        // 播放 <5s（不足检查点间隔）即强退（不做边界保存的进程内近似：直接退出层但锁住落盘）
        val databasePath = context.getDatabasePath(DebugBrowseProbeGate.DATABASE).absolutePath
        val blocker = SQLiteDatabase.openDatabase(databasePath, null, SQLiteDatabase.OPEN_READWRITE)
        blocker.execSQL("BEGIN EXCLUSIVE")
        try {
            Thread.sleep(1_200)
            key(KeyEvent.KEYCODE_BACK)
            key(KeyEvent.KEYCODE_BACK)
            until { var isPlayer = true; scenario!!.onActivity { isPlayer = requireNotNull(it.browseProbe).isPlayer }; !isPlayer }
        } finally {
            blocker.execSQL("COMMIT"); blocker.close()
        }
        // 未提交帧未被持久：检查点仍是最后已提交值（未变大的投机值）
        val after = snapshotFor()?.positionMs
        assertTrue("未提交进度不冒充持久", after == null || after <= committedAtStart + 6_000)
    }
}
