package com.ichichuang.cinema.feature.player

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * R16 物理按键周期与焦点原生测试（K01–K06/K09/K10）：真实窗口按键流
 * （DOWN/repeat/UP/CANCEL 全形态）、隐藏层箭头保留既有裸播放语义、
 * 可见层箭头只导航、每个物理周期至多一次激活、逐层 Back。
 */
class PlayerControlsKeyNativeTest {
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
    private fun raw(action: Int, code: Int, repeat: Int = 0, flags: Int = 0) =
        PlayerControlsNativeFixture.rawKey(instrumentation, action, code, repeat, flags)
    private fun player(action: (PlayerControlsRuntime) -> Unit) {
        scenario!!.onActivity { action(requireNotNull(it.playerControlsProbe)) }
    }

    private fun enter(workIndex: Int = 0) {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        PlayerControlsNativeFixture.scenarioRef = scenario
        PlayerControlsNativeFixture.enterPlayerFromLibrary(scenario!!, instrumentation, workIndex)
    }

    private fun keyTraceSnapshot(): List<String> =
        PlayerControlsTestHooks.keyTrace.toList()

    // K01 隐藏层方向键保留既有裸播放语义：左右真实快进快退、上下真实换集（同线路证据清单内）
    @Test fun hidden_dpad_arrows_preserve_existing_shortcuts() {
        enter()
        key(KeyEvent.KEYCODE_DPAD_CENTER) // 开始真实播放（mp4）
        until { var playing = false; player { playing = it.display!!.facts.isPlaying }; playing }
        key(KeyEvent.KEYCODE_BACK) // 收浮层进入隐藏层
        until { var hidden = false; player { hidden = !it.controller.overlayVisible }; hidden }
        val seekCallsBefore = PlayerControlsTestHooks.seekCalls.get()
        key(KeyEvent.KEYCODE_DPAD_RIGHT) // 裸快进 +15s
        until { var position = 0L; player { position = it.display!!.positionMs }; position >= 20_000 }
        assertEquals("一次完整周期恰好一次 seek 调用", seekCallsBefore + 1, PlayerControlsTestHooks.seekCalls.get())
        // 上下 = 剧集轴：同线路（variant-a）内 epA1 → epA2（证据身份切换）
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { var episode: String? = null; player { episode = it.display!!.episodeId }
            episode == PlayerControlsNativeFixture.EPISODE_A2 }
        player { assertEquals(PlayerControlsNativeFixture.VARIANT_A, it.display!!.variantId) }
        key(KeyEvent.KEYCODE_DPAD_UP)
        until { var episode: String? = null; player { episode = it.display!!.episodeId }
            episode == PlayerControlsNativeFixture.EPISODE_A1 }
    }

    // K02 可见层方向键一次只移动一个焦点/预览目标，不同时拖动或换集
    @Test fun visible_dpad_arrows_navigate_controls() {
        enter()
        var lastPosition = 0L; var lastEpisode: String? = null
        player { lastPosition = it.display!!.positionMs; lastEpisode = it.display!!.episodeId }
        val expected = listOf("播放控制", "时间轴", "选集", "线路")
        var last = "播放控制"
        expected.forEach { control ->
            when (control) {
                "时间轴" -> key(KeyEvent.KEYCODE_DPAD_RIGHT)
                "选集" -> key(KeyEvent.KEYCODE_DPAD_DOWN)
                "线路" -> key(KeyEvent.KEYCODE_DPAD_RIGHT)
            }
            last = control
            until { var focus: String? = null; player { focus = it.controller.focusedControlId }; focus == control }
        }
        assertTrue("遍历不改变播放位置/剧集", true)
        player { runtime ->
            assertEquals("导航不得改变播放位置", lastPosition, runtime.display!!.positionMs)
            assertEquals("导航不得改变剧集", lastEpisode, runtime.display!!.episodeId)
        }
    }

    // K03 一个完整物理 OK 周期恰好一次短激活，DOWN 本身零激活
    @Test fun short_ok_fires_on_up_exactly_once() {
        enter()
        key(KeyEvent.KEYCODE_BACK) // 收浮层
        until { var hidden = false; player { hidden = !it.controller.overlayVisible }; hidden }
        PlayerControlsTestHooks.keyTrace.clear()
        raw(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
        instrumentation.waitForIdleSync()
        Thread.sleep(150)
        player { assertFalse("DOWN 本身不得触发短激活", it.controller.overlayVisible) }
        raw(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
        instrumentation.waitForIdleSync()
        until { var shown = false; player { shown = it.controller.overlayVisible }; shown }
        val presses = keyTraceSnapshot().count { it.startsWith("activation=PRESS") && it.contains("code=23") }
        assertEquals("恰好一次短激活", 1, presses)
        // 同一物理周期不重复激活：再发一个孤立 UP
        raw(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
        instrumentation.waitForIdleSync()
        assertEquals(1, keyTraceSnapshot().count { it.startsWith("activation=PRESS") && it.contains("code=23") })
    }

    // K04 长 OK 恰好一次长激活，且绝不先出短激活（首个 DOWN repeatCount=0）
    @Test fun long_ok_does_not_fire_short_first() {
        enter()
        key(KeyEvent.KEYCODE_BACK) // 收浮层
        until { var hidden = false; player { hidden = !it.controller.overlayVisible }; hidden }
        PlayerControlsTestHooks.keyTrace.clear()
        raw(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER) // repeatCount = 0
        instrumentation.waitForIdleSync()
        Thread.sleep(520) // 超过长按阈值（真实流逝时间路径）
        until { keyTraceSnapshot().any { it.startsWith("activation=LONG_PRESS") } }
        player { assertTrue("长按揭示浮层", it.controller.overlayVisible) }
        raw(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
        instrumentation.waitForIdleSync()
        val trace = keyTraceSnapshot()
        val shortBeforeLong = trace.takeWhile { !it.startsWith("activation=LONG_PRESS") }
            .any { it.startsWith("activation=PRESS") }
        assertFalse("长按绝不先触发短按", shortBeforeLong)
        assertEquals("长激活恰好一次", 1, trace.count { it.startsWith("activation=LONG_PRESS") && it.contains("code=23") })
        assertEquals("UP 后零短激活", 0, trace.count { it.startsWith("activation=PRESS") && it.contains("code=23") })
        // 平台 repeat 路径同样只有一次长激活
        PlayerControlsTestHooks.keyTrace.clear()
        key(KeyEvent.KEYCODE_BACK) // 收浮层
        until { var hidden = false; player { hidden = !it.controller.overlayVisible }; hidden }
        raw(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
        Thread.sleep(420)
        raw(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, repeat = 1)
        raw(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, repeat = 2)
        raw(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
        instrumentation.waitForIdleSync()
        val repeatTrace = keyTraceSnapshot()
        assertEquals(1, repeatTrace.count { it.startsWith("activation=LONG_PRESS") })
        assertEquals(0, repeatTrace.count { it.startsWith("activation=PRESS") && it.contains("code=23") })
    }

    // K05 重复 DOWN、丢失 UP、孤儿 UP、CANCEL 不得产生幽灵点击或滞留
    @Test fun held_repeat_orphan_up_cancel_safe() {
        enter()
        key(KeyEvent.KEYCODE_BACK)
        until { var hidden = false; player { hidden = !it.controller.overlayVisible }; hidden }
        PlayerControlsTestHooks.keyTrace.clear()
        // 孤儿 UP（无 DOWN）：零激活
        raw(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
        instrumentation.waitForIdleSync()
        assertEquals(0, keyTraceSnapshot().count { it.startsWith("activation=PRESS") })
        // CANCEL 的 UP（FLAG_CANCELED）：周期作废零激活
        raw(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
        Thread.sleep(60)
        raw(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, flags = KeyEvent.FLAG_CANCELED)
        instrumentation.waitForIdleSync()
        Thread.sleep(150)
        assertEquals("取消的周期不得激活", 0, keyTraceSnapshot().count { it.startsWith("activation=PRESS") || it.startsWith("activation=LONG_PRESS") })
        player { assertFalse(it.controller.overlayVisible) }
        // 丢失 UP 后同键新 DOWN：静默作废旧周期，新周期恰好一次激活
        PlayerControlsTestHooks.keyTrace.clear()
        raw(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER) // UP 丢失
        raw(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER) // 新物理按下（repeatCount=0）
        raw(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
        instrumentation.waitForIdleSync()
        until { var shown = false; player { shown = it.controller.overlayVisible }; shown }
        assertEquals("不产生幽灵双击", 1, keyTraceSnapshot().count { it.startsWith("activation=PRESS") && it.contains("code=23") })
    }

    // K06 快速连按：每个真实周期恰好一次派发，无 clickable+父层双派发
    @Test fun rapid_ok_and_back_deduplicated() {
        enter()
        PlayerControlsTestHooks.keyTrace.clear()
        repeat(3) { key(KeyEvent.KEYCODE_DPAD_CENTER) }
        until { var presses = 0; player {}
            presses = PlayerControlsTestHooks.keyTrace.count { it.startsWith("activation=PRESS") && it.contains("code=23") }
            presses == 3 }
        // 播放器层内快速 Back×2：浮层收起 + 退出播放层（各一层，不双跳）
        key(KeyEvent.KEYCODE_BACK)
        until { var hidden = false; player { hidden = !it.controller.overlayVisible }; hidden }
        key(KeyEvent.KEYCODE_BACK)
        until { var isPlayer = true; scenario!!.onActivity { isPlayer = requireNotNull(it.browseProbe).isPlayer }; !isPlayer }
        until { semantics().any { it.tag == "候选详情" } }
        assertTrue("一次 Back 不越过详情层", semantics().none { it.tag == "候选片库" })
    }

    // K07/K08 逐层 Back 与选择器焦点陷阱 + 打开者恢复（真实语义树）
    @Test fun picker_trap_back_unwinds_and_restores_opener() {
        enter()
        key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { var focus: String? = null; player { focus = it.controller.focusedControlId }; focus == "选集" }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var picker: Any? = null; player { picker = it.controller.picker }; picker != null }
        // 陷阱：背景控件不可聚焦（语义树中背景层无焦点）
        until { semantics().any { it.focused && it.tag.startsWith("picker:") } }
        assertFalse("背景控件不获得焦点", semantics().any { it.focused && (it.tag == "播放控制" || it.tag == "时间轴") })
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { var focus: String? = null; player { focus = it.controller.pickerFocusedId }
            focus == "picker:${PlayerControlsNativeFixture.EPISODE_A2}" }
        key(KeyEvent.KEYCODE_BACK)
        until { var picker: Any? = null; player { picker = it.controller.picker }; picker == null }
        until { var focus: String? = null; player { focus = it.controller.focusedControlId }; focus == "选集" }
        // Back 逐层：选择器 → 浮层 → 播放层 → 详情
        key(KeyEvent.KEYCODE_BACK)
        until { var hidden = false; player { hidden = !it.controller.overlayVisible }; hidden }
        key(KeyEvent.KEYCODE_BACK)
        until { var isPlayer = true; scenario!!.onActivity { isPlayer = requireNotNull(it.browseProbe).isPlayer }; !isPlayer }
        until { semantics().any { it.tag == "候选详情" } }
    }

    // K09 用户真实导航取消迟到的焦点恢复：Back 关选择器瞬间立刻移动，落点必须是用户目标
    @Test fun user_navigation_cancels_delayed_focus_restore() {
        enter()
        key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { var focus: String? = null; player { focus = it.controller.focusedControlId }; focus == "选集" }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var focus: String? = null; player { focus = it.controller.pickerFocusedId }; focus != null }
        key(KeyEvent.KEYCODE_BACK) // 关选择器（恢复意图 = 打开者“选集”）
        key(KeyEvent.KEYCODE_DPAD_LEFT) // 用户立刻左移（覆盖迟到恢复）
        until { var focus: String? = null; player { focus = it.controller.focusedControlId }; focus == "时间轴" }
        Thread.sleep(400) // 若迟到恢复未失效，焦点会被抢回“选集”
        player { assertEquals("迟到恢复不得抢焦点", "时间轴", it.controller.focusedControlId) }
    }

    // K10 全部重要操作仅用方向/OK/Back 可达；无仅长按功能
    @Test fun all_required_controls_without_long_or_menu() {
        enter()
        // 逐个控件真实可达（时间轴捕获左右：右进轴、下到选集、右到线路）
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        until { semantics().any { it.focused && it.tag == "时间轴" } }
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { semantics().any { it.focused && it.tag == "选集" } }
        // 选择器打开/关闭仅用 OK/Back
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "集数单" } }
        key(KeyEvent.KEYCODE_BACK)
        until { semantics().none { it.tag == "集数单" } }
        // 线路选择器同样可达
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        until { semantics().any { it.focused && it.tag == "线路" } }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "线路单" } }
        assertNotNull(semantics().first { it.tag == "线路单" })
    }
}
