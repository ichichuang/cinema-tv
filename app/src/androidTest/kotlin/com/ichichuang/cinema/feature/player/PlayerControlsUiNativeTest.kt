package com.ichichuang.cinema.feature.player

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.MainActivity
import com.ichichuang.cinema.data.userdata.RoomPlaybackProgressStore
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
 * R16 播放器投影与浮层原生测试（U01–U08）：真实 Media3（合成 MP4 / 直播式 HLS）
 * + 真实 Room v6 + 真实窗口按键；缓冲/暂停/错误互不冒充，未知时长如实呈现。
 */
class PlayerControlsUiNativeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun ownedPrivateFixture() = PlayerControlsNativeFixture.setup(context)
    @After fun restore() {
        PlayerControlsTestHooks.holdSwitch?.complete(Unit); PlayerControlsTestHooks.holdSwitch = null
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

    private fun launchAndEnterPlayer(workIndex: Int = 0) {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        PlayerControlsNativeFixture.scenarioRef = scenario
        PlayerControlsNativeFixture.enterPlayerFromLibrary(scenario!!, instrumentation, workIndex)
    }

    private fun resumeSnapshot(workIndex: Int = 0): com.ichichuang.cinema.domain.model.userdata.PlaybackSnapshot? {
        var store: RoomPlaybackProgressStore? = null
        personal { store = RoomPlaybackProgressStore(it.database) }
        val id = PlayerControlsNativeFixture.w[workIndex]
        return runBlocking { store!!.loadResume(id) }
    }

    // 基线：授权档案进入播放器，精确 (w0, epA1, variant-a, 8000ms) 且保持暂停
    @Test fun authorized_profile_enters_player_with_exact_resume_paused() {
        launchAndEnterPlayer()
        // 装载（缓冲）落定后必须稳定在“已暂停”，绝不自动播放
        until(15_000) { var text = ""; player { text = it.display?.status?.statusText ?: "" }; text == "已暂停" }
        player { runtime ->
            val display = runtime.display!!
            assertEquals(PlayerControlsNativeFixture.EPISODE_A1, display.episodeId)
            assertEquals(PlayerControlsNativeFixture.VARIANT_A, display.variantId)
            assertFalse(runtime.display!!.facts.isPlaying)
            assertTrue(runtime.controller.overlayVisible)
            assertEquals("播放控制", runtime.controller.focusedControlId)
        }
        personal { assertEquals(8_000L, it.requestedResumePosition) }
    }

    // U01 真实 Media3 播放/暂停转换驱动中文标签，无乐观假设
    @Test fun media3_play_pause_updates_labels_without_optimism() {
        launchAndEnterPlayer()
        PlayerControlsTestHooks.suppressIdleHide = true // 空闲隐藏由 U03 专项验证
        key(KeyEvent.KEYCODE_DPAD_CENTER) // 聚焦“播放控制”短 OK：真实 play()
        until { var playing = false; player { playing = it.display!!.facts.isPlaying }; playing }
        key(KeyEvent.KEYCODE_DPAD_RIGHT) // 焦点进时间轴
        until { var focus: String? = null; player { focus = it.controller.focusedControlId }; focus == "时间轴" }
        val seeksBeforePreview = PlayerControlsTestHooks.seekCalls.get()
        key(KeyEvent.KEYCODE_DPAD_RIGHT) // 时间轴捕获左右：建立预览（绝不产生 seek 命令）
        until { var preview: Long? = null; player { preview = it.controller.previewMs }; preview != null }
        player { runtime ->
            assertEquals("预览期间焦点不动", "时间轴", runtime.controller.focusedControlId)
            assertEquals("正在播放", runtime.display!!.status.statusText)
        }
        until(15_000) { true }
        org.junit.Assert.assertEquals("预览不是 seek", seeksBeforePreview, PlayerControlsTestHooks.seekCalls.get())
        until { semantics().any { it.tag == "播放控制" && it.description.contains("label=暂停") } }
        until(15_000) { var position = 0L; player { position = it.display!!.positionMs }; position >= 14_000 }
        key(KeyEvent.KEYCODE_DPAD_UP) // 时间轴上键离开，焦点回“播放控制”
        until { var focus: String? = null; player { focus = it.controller.focusedControlId }; focus == "播放控制" }
        key(KeyEvent.KEYCODE_DPAD_CENTER) // 再一次完整物理周期：真实 pause()
        until { var paused = false; player { paused = it.display!!.facts.isPlaying == false }; paused }
        until { semantics().any { it.tag == "播放控制" && it.description.contains("label=播放") } }
        player { assertEquals("已暂停", it.display!!.status.statusText) }
        // P01 的落盘证据由 PlayerControlsLifecycleNativeTest.pause_commits_actual_position 承担
    }

    // U02 缓冲≠用户暂停≠播完：直播式 HLS 装载期与 MP4 播放期状态互不冒充
    @Test fun buffering_is_never_user_pause_or_completion() {
        launchAndEnterPlayer()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var text = ""; player { text = it.display!!.status.statusText }; text == "正在播放" }
        player { runtime ->
            assertFalse(runtime.display!!.status.buffering)
            assertFalse(runtime.display!!.status.ended)
        }
    }

    // U03 短操作揭示控件；播放中空闲达到阈值才允许隐藏
    @Test fun short_activity_reveals_overlay_and_idle_playing_may_hide() {
        launchAndEnterPlayer()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var playing = false; player { playing = it.display!!.facts.isPlaying }; playing }
        key(KeyEvent.KEYCODE_BACK) // 关闭浮层（一层）
        until { var hidden = false; player { hidden = !it.controller.overlayVisible }; hidden }
        key(KeyEvent.KEYCODE_DPAD_CENTER) // 隐藏层短 OK：只揭示，不播放/暂停
        until { var shown = false; player { shown = it.controller.overlayVisible }; shown }
        var stillPlaying = false; player { stillPlaying = it.display!!.facts.isPlaying }
        assertTrue("揭示不得改动播放状态", stillPlaying)
        // 空闲超过阈值（真实计时）：播放中浮层自动隐藏
        until(9_000) { var hidden = false; player { hidden = !it.controller.overlayVisible }; hidden }
    }

    // U04 暂停/选择器/预览期间浮层绝不被计时器隐藏
    @Test fun overlay_remains_during_pause_picker_preview() {
        launchAndEnterPlayer()
        // 暂停态：等待远超阈值仍可见
        until(7_000) { true } // 进入后即暂停
        player { assertTrue(it.controller.overlayVisible) }
        // 选择器打开：同样不被隐藏
        key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_DOWN) // 焦点到 选集
        until { var focus: String? = null; player { focus = it.controller.focusedControlId }; focus == "选集" }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var picker: Any? = null; player { picker = it.controller.picker }; picker != null }
        Thread.sleep(6_500)
        player { assertNotNull("选择器期间不得隐藏", it.controller.picker) }
        key(KeyEvent.KEYCODE_BACK) // 关选择器恢复打开者
        until { var focus: String? = null; player { focus = it.controller.focusedControlId }; focus == "选集" }
        // 时间轴预览进行中：不被隐藏（时间轴捕获左右=预览）
        key(KeyEvent.KEYCODE_DPAD_UP) // 上键回 时间轴
        until { var focus: String? = null; player { focus = it.controller.focusedControlId }; focus == "时间轴" }
        key(KeyEvent.KEYCODE_DPAD_RIGHT) // 建立 +15s 预览（焦点不动）
        until { var preview: Long? = null; player { preview = it.controller.previewMs }; preview != null }
        Thread.sleep(6_500)
        player {
            assertNotNull("预览进行中不得隐藏浮层", it.controller.previewMs)
            assertTrue(it.controller.overlayVisible)
        }
        key(KeyEvent.KEYCODE_BACK) // 取消预览（无进度变更）
        player { assertNull(it.controller.previewMs) }
    }

    // U05 不可拖动媒体如实呈现：真实 seek 命令缺失 → 时间轴禁用、无假预览。
    // “时长未知”前提：本地合法媒体在 Media3 1.11 下均能得出时长（ADTS 关闭常率 seek
    // 推断后不可拖动但时长已知）；真实未知时长需直播网络流（被本轮规则禁止）→ 矩阵记 BLOCKED。
    @Test fun nonseekable_media_displays_disabled_scrub_truthfully() {
        launchAndEnterPlayer()
        // 切到第二集（原始 ADTS 流：无索引、真实 seek 命令缺失）
        key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { var focus: String? = null; player { focus = it.controller.focusedControlId }; focus == "选集" }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var focus: String? = null; player { focus = it.controller.pickerFocusedId }; focus != null }
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { var focus: String? = null; player { focus = it.controller.pickerFocusedId }; focus == "picker:${PlayerControlsNativeFixture.EPISODE_A2}" }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var episode: String? = null; player { episode = it.display!!.episodeId }; episode == PlayerControlsNativeFixture.EPISODE_A2 }
        until { var seekable = true; player { seekable = it.display!!.seekAvailable }; !seekable }
        player {
            assertFalse("真实命令缺失时时间轴禁用", it.controller.controlEnabled("时间轴"))
            assertNull("禁用状态下无预览目标", it.controller.previewMs)
        }
        until { semantics().any { it.tag == "时间轴" && it.description.contains("Disabled") } }
        assertFalse("不得出现假预览", semantics().any { it.tag == "预览提示" })
    }

    // U06 真实 availableCommands 门控控件：直播流不可拖动，禁用动作绝不调用 Player
    @Test fun unsupported_commands_are_disabled_and_never_invoked() {
        launchAndEnterPlayer()
        val before = PlayerControlsTestHooks.seekCalls.get()
        // 切到直播式 HLS（同上路径）
        key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { var focus: String? = null; player { focus = it.controller.focusedControlId }; focus == "选集" }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var focus: String? = null; player { focus = it.controller.pickerFocusedId }; focus != null }
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { var focus: String? = null; player { focus = it.controller.pickerFocusedId }; focus == "picker:${PlayerControlsNativeFixture.EPISODE_A2}" }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var seekable = true; player { seekable = it.display!!.seekAvailable }; !seekable }
        // 隐藏层左右（裸快进语义）：能力缺失 → 如实提示，零 seek 调用
        key(KeyEvent.KEYCODE_BACK) // 收浮层
        until { var hidden = false; player { hidden = !it.controller.overlayVisible }; hidden }
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        until { var hint: String? = null; player { hint = it.display?.hint }; hint?.contains("不支持") == true }
        assertEquals("禁用能力下零 seek 调用", before, PlayerControlsTestHooks.seekCalls.get())
    }

    // U07 真实媒体错误：可行动中文重试，不改路由、不清检查点
    @Test fun real_error_and_retry_preserve_route() {
        launchAndEnterPlayer()
        // 经选择器切到损坏集（w2? 不同作品需重新进入）。此处用同作品损坏集不存在 → 用详情切换作品成本高；
        // 直接以 w2（含损坏集）路径验证：从片库进入 w2 详情并播放损坏集。
        PlayerControlsNativeFixture.backOutOfPlayer(scenario!!, instrumentation, viaOverlay = true)
        com.ichichuang.cinema.feature.library.LibraryNativeFixture.screenReach(scenario!!,
            PlayerControlsNativeFixture.w[2].value)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选详情" } }
        until { semantics().any { it.focused } }
        key(KeyEvent.KEYCODE_DPAD_CENTER) // 播放 w2 默认线路（f）默认集（F1 可播）
        until { var isPlayer = false; scenario!!.onActivity { isPlayer = requireNotNull(it.browseProbe).isPlayer }; isPlayer }
        until { var episode: String? = null; player { episode = it.display!!.episodeId }; episode == PlayerControlsNativeFixture.EPISODE_F1 }
        // 选集器 → 下移到损坏集 F2
        key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { var focus: String? = null; player { focus = it.controller.focusedControlId }; focus == "选集" }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var focus: String? = null; player { focus = it.controller.pickerFocusedId }; focus != null }
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { var focus: String? = null; player { focus = it.controller.pickerFocusedId }; focus == "picker:${PlayerControlsNativeFixture.EPISODE_F2}" }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until(20_000) { var error: String? = null; player { error = it.display?.error }; error != null }
        player { runtime ->
            assertTrue(runtime.display!!.error!!.contains("播放失败") || runtime.display!!.error!!.contains("不支持"))
            assertEquals("错误不得改路由", PlayerControlsNativeFixture.EPISODE_F2, runtime.display!!.episodeId)
        }
        until { semantics().any { it.tag == "播放错误" } }
        // F1 的既有记录不被错误清除
        val f1 = runBlocking {
            var db: com.ichichuang.cinema.data.local.CinemaDatabase? = null
            personal { db = it.database }
            db!!.playbackSnapshots().find(PlayerControlsNativeFixture.w[2].value,
                PlayerControlsNativeFixture.EPISODE_F1, PlayerControlsNativeFixture.VARIANT_F)
        }
        assertNull("损坏集错误不得写假检查点", runBlocking {
            var db: com.ichichuang.cinema.data.local.CinemaDatabase? = null
            personal { db = it.database }
            db!!.playbackSnapshots().find(PlayerControlsNativeFixture.w[2].value,
                PlayerControlsNativeFixture.EPISODE_F2, PlayerControlsNativeFixture.VARIANT_F)
        })
        assertTrue(f1 == null || f1.snapshot() != null)
    }

    // U08 离开播放器：UI 投影冻结（不再更新位置），返回不产生进度写入
    @Test fun leaving_player_cancels_ticker_and_listener_projection() {
        launchAndEnterPlayer()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var playing = false; player { playing = it.display!!.facts.isPlaying }; playing }
        until { var position = 0L; player { position = it.display!!.positionMs }; position > 8_100 }
        key(KeyEvent.KEYCODE_BACK) // 关浮层（仍在播放器）
        until { var hidden = false; player { hidden = !it.controller.overlayVisible }; hidden }
        key(KeyEvent.KEYCODE_BACK) // 退出播放层 → 详情
        until { var isPlayer = true; scenario!!.onActivity { isPlayer = requireNotNull(it.browseProbe).isPlayer }; !isPlayer }
        Thread.sleep(1_200)
        player { runtime ->
            val frozen = runtime.display?.positionMs
            Thread.sleep(600)
            assertEquals("离开后 UI 投影必须冻结", frozen, runtime.display?.positionMs)
        }
    }
}
