package com.ichichuang.cinema.feature.player

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.MainActivity
import com.ichichuang.cinema.data.userdata.RoomPlaybackProgressStore
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
 * R16 时间轴/进度原生测试（S01–S08）：真实 Media3 seek（合成 MP4 可拖动、
 * 本地直播式 HLS 不可拖动）+ 真实 Room v6 检查点核对。
 */
class PlayerControlsSeekNativeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun ownedPrivateFixture() = PlayerControlsNativeFixture.setup(context)
    @After fun restore() {
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

    /** 测试辅助：焦点复位到首个控件（不触发任何命令）。 */
    private fun controllerFocusResetToFirst() {
        scenario!!.onActivity { requireNotNull(it.playerControlsProbe).controller.overrideFocus("播放控制") }
    }

    private fun focusTimeline() {
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        until { var focus: String? = null; player { focus = it.controller.focusedControlId }; focus == "时间轴" }
    }

    private fun switchToEpisode2() {
        key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { var focus: String? = null; player { focus = it.controller.focusedControlId }; focus == "选集" }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var focus: String? = null; player { focus = it.controller.pickerFocusedId }; focus != null }
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { var focus: String? = null; player { focus = it.controller.pickerFocusedId }
            focus == "picker:${PlayerControlsNativeFixture.EPISODE_A2}" }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var episode: String? = null; player { episode = it.display!!.episodeId }
            episode == PlayerControlsNativeFixture.EPISODE_A2 }
    }

    private fun snapshotFor(episode: String, variant: String = PlayerControlsNativeFixture.VARIANT_A):
        com.ichichuang.cinema.domain.model.userdata.PlaybackSnapshot? = runBlocking {
        var db: com.ichichuang.cinema.data.local.CinemaDatabase? = null
        personal { db = it.database }
        val row = db!!.playbackSnapshots().find(PlayerControlsNativeFixture.w[0].value, episode, variant)
        row?.snapshot()
    }

    private fun resumeOfWork0(): com.ichichuang.cinema.domain.model.userdata.PlaybackSnapshot? = runBlocking {
        var store: RoomPlaybackProgressStore? = null
        personal { store = RoomPlaybackProgressStore(it.database) }
        store!!.loadResume(PlayerControlsNativeFixture.w[0])
    }

    // S01 聚焦时间轴左右预览、OK 恰好一次真实 seekTo 并退出预览
    @Test fun confirmed_scrub_seeks_once() {
        enter()
        PlayerControlsTestHooks.suppressIdleHide = true // 空闲隐藏由 U03 专项验证
        focusTimeline()
        val before = PlayerControlsTestHooks.seekCalls.get()
        key(KeyEvent.KEYCODE_DPAD_RIGHT) // 预览 = 8000 + 15000 = 23000
        until { var preview: Long? = null; player { preview = it.controller.previewMs }; preview == 23_000L }
        key(KeyEvent.KEYCODE_DPAD_CENTER) // 确认：恰好一次 seek
        until { var calls = 0; calls = PlayerControlsTestHooks.seekCalls.get(); calls == before + 1 }
        player { assertNull("确认后退出预览", it.controller.previewMs) }
        until { var position = 0L; player { position = it.display!!.positionMs }; position >= 22_000 }
        // 真实 seek 经既有检查点管线落盘（RuntimeIntent.SEEK → coordinator.seek → commit）
        until { val snap = snapshotFor(PlayerControlsNativeFixture.EPISODE_A1); snap != null && snap.positionMs >= 22_000 }
    }

    // S02 Back 取消预览：真实播放位置与 Room 检查点都不变
    @Test fun scrub_back_cancels_without_persisting() {
        enter()
        PlayerControlsTestHooks.suppressIdleHide = true // 空闲隐藏由 U03 专项验证
        val committedBefore = snapshotFor(PlayerControlsNativeFixture.EPISODE_A1)?.positionMs
        focusTimeline()
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        until { var preview: Long? = null; player { preview = it.controller.previewMs }; preview == 23_000L }
        key(KeyEvent.KEYCODE_BACK)
        player { assertNull(it.controller.previewMs) }
        Thread.sleep(700)
        val committedAfter = snapshotFor(PlayerControlsNativeFixture.EPISODE_A1)?.positionMs
        assertEquals("取消预览不得写检查点", committedBefore, committedAfter)
    }

    // S03 真实后退 seek 与显式归零都提交新值，不并合到最大位置
    @Test fun backward_seek_and_intentional_zero_commit() {
        enter()
        PlayerControlsTestHooks.suppressIdleHide = true // 空闲隐藏由 U03 专项验证
        // 前进提交（SEEK 提交路径确定性落盘）：预览 +15s → OK 恰好一次 seekTo
        focusTimeline()
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        until { var preview: Long? = null; player { preview = it.controller.previewMs }; preview == 23_000L }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var position = 0L; player { position = it.display!!.positionMs }; position >= 22_000 }
        until(45_000) { val snap = snapshotFor(PlayerControlsNativeFixture.EPISODE_A1, PlayerControlsNativeFixture.VARIANT_A)
            snap != null && snap.positionMs >= 22_000 }
        // 隐藏层后退（−45s → 钳制 0）与显式归零：真实提交新值，不并合到最大位置
        key(KeyEvent.KEYCODE_BACK) // 收浮层
        until { var hidden = false; player { hidden = !it.controller.overlayVisible }; hidden }
        key(KeyEvent.KEYCODE_DPAD_LEFT); key(KeyEvent.KEYCODE_DPAD_LEFT); key(KeyEvent.KEYCODE_DPAD_LEFT)
        until { var position = 0L; player { position = it.display!!.positionMs }; position <= 300 }
        until(45_000) { val snap = snapshotFor(PlayerControlsNativeFixture.EPISODE_A1, PlayerControlsNativeFixture.VARIANT_A)
            snap != null && snap.positionMs <= 300 }
        val snap = snapshotFor(PlayerControlsNativeFixture.EPISODE_A1, PlayerControlsNativeFixture.VARIANT_A)
        assertNotNull(snap)
        assertTrue("显式零值必须如实提交", snap!!.positionMs <= 300)
        assertTrue("时长保持真实", snap.durationMs != null && snap.durationMs in 23_000..25_000)
    }

    // S04 长按/连按方向键：预览有界可打断，不负、不越真实时长
    @Test fun repeated_seek_is_bounded_and_interruptible() {
        enter()
        PlayerControlsTestHooks.suppressIdleHide = true // 空闲隐藏由 U03 专项验证
        focusTimeline()
        repeat(8) { key(KeyEvent.KEYCODE_DPAD_RIGHT) } // 8×15s = 120s → 钳到 24000
        player {
            val preview = it.controller.previewMs
            assertNotNull(preview)
            assertTrue("预览不得超过真实时长", preview!! <= 24_000L)
            assertTrue("预览不得为负", preview >= 0)
        }
        repeat(5) { key(KeyEvent.KEYCODE_DPAD_LEFT) }
        player { assertTrue(it.controller.previewMs!! >= 0) }
        // Back 打断预览后可立即重新预览（无排队残压）
        key(KeyEvent.KEYCODE_BACK)
        player { assertNull(it.controller.previewMs) }
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        until { var preview: Long? = null; player { preview = it.controller.previewMs }; preview != null }
    }

    // S05 不可拖动媒体：禁用的时间轴 + 零次 seek 调用（真实 availableCommands 缺失）
    @Test fun nonseekable_media_no_unsupported_calls() {
        enter()
        PlayerControlsTestHooks.suppressIdleHide = true // 空闲隐藏由 U03 专项验证
        switchToEpisode2()
        until { var seekable = true; player { seekable = it.display!!.seekAvailable }; !seekable }
        val before = PlayerControlsTestHooks.seekCalls.get()
        // 可见层：时间轴禁用（导航跳过）——从 播放控制 右移直接落在 选集
        controllerFocusResetToFirst()
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        until { var focus: String? = null; player { focus = it.controller.focusedControlId }; focus == "选集" }
        // 隐藏层：左右裸快进被拒（如实提示）
        key(KeyEvent.KEYCODE_BACK)
        until { var hidden = false; player { hidden = !it.controller.overlayVisible }; hidden }
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        until { var hint: String? = null; player { hint = it.display?.hint }; hint?.contains("不支持") == true }
        assertEquals("不可拖动媒体零 seek 调用", before, PlayerControlsTestHooks.seekCalls.get())
    }

    // S06 不可拖动媒体（真实 seek 命令缺失）：绝对拖动禁用；相对快进只在命令允许时执行
    @Test fun nonseekable_media_relative_seek_is_honest() {
        enter()
        PlayerControlsTestHooks.suppressIdleHide = true // 空闲隐藏由 U03 专项验证
        switchToEpisode2()
        until { var seekable = true; player { seekable = it.display!!.seekAvailable }; !seekable }
        player {
            assertFalse("真实命令缺失时禁用绝对拖动", it.controller.controlEnabled("时间轴"))
            assertNull(it.controller.previewMs)
        }
        assertFalse("无预览可进行", semantics().any { it.tag == "预览提示" })
        val before = PlayerControlsTestHooks.seekCalls.get()
        key(KeyEvent.KEYCODE_BACK)
        until { var hidden = false; player { hidden = !it.controller.overlayVisible }; hidden }
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        until { var hint: String? = null; player { hint = it.display?.hint }; hint?.contains("不支持") == true }
        assertEquals("不可拖动媒体零 seek 调用", before, PlayerControlsTestHooks.seekCalls.get())
    }

    // S07 预览、prepare、聚焦都不产生观看事件或改动续播指针（Room 计数前后一致）
    @Test fun preview_and_preload_not_counted_watched() {
        enter()
        PlayerControlsTestHooks.suppressIdleHide = true // 空闲隐藏由 U03 专项验证
        var historyBefore = 0
        personal { historyBefore = it.history.size }
        focusTimeline()
        key(KeyEvent.KEYCODE_DPAD_RIGHT) // 仅预览
        until { var preview: Long? = null; player { preview = it.controller.previewMs }; preview != null }
        key(KeyEvent.KEYCODE_BACK) // 取消（不确认）
        Thread.sleep(700)
        personal {
            assertEquals("预览不得产生观看事件", historyBefore, it.history.size)
            val card = it.continuing.firstOrNull { card -> card.workId == PlayerControlsNativeFixture.w[0].value }
            assertNotNull("续播卡片不得消失", card)
            assertEquals("续播指针保持 8000ms 起点", PlayerControlsNativeFixture.RESUME_POSITION_MS, card!!.snapshot?.positionMs)
        }
    }

    // S08 seek 失败/错误不得伪造已提交：旧检查点保持有效
    @Test fun seek_failure_preserves_last_committed_position() {
        enter()
        PlayerControlsTestHooks.suppressIdleHide = true // 空闲隐藏由 U03 专项验证
        // 先确认一次真实 seek 建立 committed 值
        focusTimeline()
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { val snap = snapshotFor(PlayerControlsNativeFixture.EPISODE_A1); snap != null && snap.positionMs >= 22_000 }
        val committed = snapshotFor(PlayerControlsNativeFixture.EPISODE_A1)!!.positionMs
        // 切到损坏集触发真实错误（w2 不便切换，这里用同作品断言：错误注入用 forbidSeekCalls 制造 seek 派发失败形态）
        PlayerControlsTestHooks.forbidSeekCalls = true
        focusTimeline()
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        key(KeyEvent.KEYCODE_DPAD_CENTER) // seek 被拒：不得伪造成功
        Thread.sleep(700)
        PlayerControlsTestHooks.forbidSeekCalls = false
        val snapAfter = snapshotFor(PlayerControlsNativeFixture.EPISODE_A1)
        assertNotNull(snapAfter)
        assertEquals("失败的 seek 不得改写已提交位置", committed, snapAfter!!.positionMs)
    }
}
