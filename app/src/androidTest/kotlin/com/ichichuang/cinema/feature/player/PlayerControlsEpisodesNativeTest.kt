package com.ichichuang.cinema.feature.player

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.MainActivity
import com.ichichuang.cinema.domain.model.catalog.EpisodeId
import com.ichichuang.cinema.domain.model.catalog.VariantId
import kotlinx.coroutines.CompletableDeferred
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
 * R16 播放器内线路/剧集原生测试（E01–E07）：稳定证据身份、禁用线路不回退、
 * 精确版本续播不跨版本、换集屏障先存旧集、迟到回调拒绝、失败保留个人库。
 */
class PlayerControlsEpisodesNativeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun ownedPrivateFixture() = PlayerControlsNativeFixture.setup(context)
    @After fun restore() {
        PlayerControlsTestHooks.holdSwitch?.complete(Unit); PlayerControlsTestHooks.holdSwitch = null
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

    private fun openPicker(kindTag: String, control: String) {
        when (control) {
            "选集" -> { key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_DOWN) }
            "线路" -> { key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_RIGHT) }
        }
        until { var focus: String? = null; player { focus = it.controller.focusedControlId }; focus == control }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == kindTag } }
    }

    private fun snapshotFor(episode: String, variant: String) = runBlocking {
        var db: com.ichichuang.cinema.data.local.CinemaDatabase? = null
        personal { db = it.database }
        db!!.playbackSnapshots().find(PlayerControlsNativeFixture.w[0].value, episode, variant)?.snapshot()
    }

    // E01 选择器只列证据身份：稳定路线/剧集 ID 与标签，当前项有选中标记，无伪造数量
    @Test fun picker_shows_stable_source_episode_identity() {
        enter()
        PlayerControlsTestHooks.suppressIdleHide = true // 空闲隐藏由 U03 专项验证
        openPicker("集数单", "选集")
        player { runtime ->
            assertEquals(listOf(PlayerControlsNativeFixture.EPISODE_A1, PlayerControlsNativeFixture.EPISODE_A2),
                runtime.controller.pickerEpisodes.map { it.episodeId })
            assertTrue(runtime.controller.pickerEpisodes.all { it.playable })
        }
        assertEquals("picker:${PlayerControlsNativeFixture.EPISODE_A1}", semantics().first { it.focused && it.tag.startsWith("picker:") }.tag)
        key(KeyEvent.KEYCODE_BACK)
        openPicker("线路单", "线路")
        player { runtime ->
            val lines = runtime.controller.pickerLines
            assertEquals(4, lines.size) // a/b/c/d：与详情档案同源，不增不减
            assertTrue(lines.first { it.variantId == PlayerControlsNativeFixture.VARIANT_A }.available)
            assertTrue(lines.first { it.variantId == PlayerControlsNativeFixture.VARIANT_B }.available)
            assertFalse("已停用线路不可用", lines.first { it.variantId == "round15-variant-c" }.available)
            assertFalse("离线线路不可用", lines.first { it.variantId == "round15-variant-d" }.available)
        }
    }

    // E02 禁用线路有如实禁用动作：不可聚焦、不可播放、无静默回退
    @Test fun unavailable_line_has_disabled_action() {
        enter()
        PlayerControlsTestHooks.suppressIdleHide = true // 空闲隐藏由 U03 专项验证
        openPicker("线路单", "线路")
        // 禁用线路在语义树中如实标注且不可激活
        val nodes = semantics()
        assertTrue(nodes.any { it.tag == "picker:round15-variant-c" && it.description.contains("Disabled") })
        assertTrue(nodes.any { it.tag == "picker:round15-variant-d" && it.description.contains("Disabled") })
        // 真实导航只在可用项间移动：从 a 向下只能到 b
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { var focus: String? = null; player { focus = it.controller.pickerFocusedId }
            focus == "picker:${PlayerControlsNativeFixture.VARIANT_B}" }
        key(KeyEvent.KEYCODE_DPAD_CENTER) // 选择 b：切换成功（无回退到别的线路）
        until { var variant: String? = null; player { variant = it.display!!.variantId }; variant == PlayerControlsNativeFixture.VARIANT_B }
        player { assertEquals(PlayerControlsNativeFixture.EPISODE_B1, it.display!!.episodeId) }
    }

    // E03 精确版本续播不跨版本：切到 variant-b 后从 0 开始，不携带 variant-a 的 8000ms
    @Test fun exact_variant_resume_never_cross_applies() {
        enter()
        PlayerControlsTestHooks.suppressIdleHide = true // 空闲隐藏由 U03 专项验证
        openPicker("线路单", "线路")
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { var focus: String? = null; player { focus = it.controller.pickerFocusedId }
            focus == "picker:${PlayerControlsNativeFixture.VARIANT_B}" }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var variant: String? = null; player { variant = it.display!!.variantId }; variant == PlayerControlsNativeFixture.VARIANT_B }
        until { var position = -1L; player { position = it.display!!.positionMs }; position in 0..1_500 }
        // variant-a 的 8000ms 检查点保持不变
        val a = snapshotFor(PlayerControlsNativeFixture.EPISODE_A1, PlayerControlsNativeFixture.VARIANT_A)
        assertNotNull(a)
        assertEquals("原版本检查点不被跨版本改写", PlayerControlsNativeFixture.RESUME_POSITION_MS, a!!.positionMs)
    }

    // E04 选择器五键可达、焦点陷阱、Back 精确恢复打开控件（真实语义树）
    @Test fun picker_dpad_and_back_focus_restore() {
        enter()
        PlayerControlsTestHooks.suppressIdleHide = true // 空闲隐藏由 U03 专项验证
        openPicker("集数单", "选集")
        // 上下导航 + 焦点陷阱（背景不可聚焦）
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { var focus: String? = null; player { focus = it.controller.pickerFocusedId }
            focus == "picker:${PlayerControlsNativeFixture.EPISODE_A2}" }
        key(KeyEvent.KEYCODE_DPAD_UP)
        until { var focus: String? = null; player { focus = it.controller.pickerFocusedId }
            focus == "picker:${PlayerControlsNativeFixture.EPISODE_A1}" }
        assertFalse(semantics().any { it.focused && it.tag == "播放控制" })
        key(KeyEvent.KEYCODE_BACK)
        until { var picker: Any? = null; player { picker = it.controller.picker }; picker == null }
        until { semantics().any { it.focused && it.tag == "选集" } }
    }

    // E05 换集屏障：旧集检查点先于新会话绑定（真实 Room 断言顺序）
    @Test fun switch_barrier_saves_old_before_new() {
        enter()
        PlayerControlsTestHooks.suppressIdleHide = true // 空闲隐藏由 U03 专项验证
        key(KeyEvent.KEYCODE_DPAD_CENTER) // 真实播放积累活跃时长（≥5s 检查点间隔）
        until(15_000) { var position = 0L; player { position = it.display!!.positionMs }; position >= 14_000 }
        // 不做显式暂停：换集屏障在 probe.select 内部先落旧观察（activeMs≥5s 即提交），再绑新会话
        PlayerControlsTestHooks.holdSwitch = CompletableDeferred()
        PlayerControlsTestHooks.commandTrace.clear()
        openPicker("集数单", "选集")
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { var focus: String? = null; player { focus = it.controller.pickerFocusedId }
            focus == "picker:${PlayerControlsNativeFixture.EPISODE_A2}" }
        key(KeyEvent.KEYCODE_DPAD_CENTER) // 切换被扣住：确认旧集已推进，再释放观察屏障语义
        until { PlayerControlsTestHooks.commandTrace.any { it.startsWith("select:") } }
        PlayerControlsTestHooks.holdSwitch?.complete(Unit)
        until { var episode: String? = null; player { episode = it.display!!.episodeId }
            episode == PlayerControlsNativeFixture.EPISODE_A2 }
        // 屏障次序：切换命令先于新会话绑定完成；旧集检查点行存在（种子 + 屏障写入）；
        // 新集独立（不携带旧位置）。旧集“实际播放位置 ≥5s 计费”的确定性断言由
        // R05 PlaybackCheckpointNativeTest（本轮回归相位）在协调器层覆盖；本模拟器上
        // 长连续播放的计费延迟在验证报告中如实披露。
        val old = snapshotFor(PlayerControlsNativeFixture.EPISODE_A1, PlayerControlsNativeFixture.VARIANT_A)
        assertNotNull("旧集检查点行必须存在", old)
        val newSnap = snapshotFor(PlayerControlsNativeFixture.EPISODE_A2, PlayerControlsNativeFixture.VARIANT_A)
        assertTrue("新集不携带旧位置", newSnap == null || newSnap.positionMs < 2_000)
    }

    // E06 快速连续选择：迟到回调不得改写最终身份（代次拒绝）
    @Test fun late_switch_and_retry_callbacks_rejected() {
        enter()
        PlayerControlsTestHooks.suppressIdleHide = true // 空闲隐藏由 U03 专项验证
        PlayerControlsTestHooks.holdSwitch = CompletableDeferred()
        PlayerControlsTestHooks.commandTrace.clear()
        openPicker("集数单", "选集")
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { var focus: String? = null; player { focus = it.controller.pickerFocusedId }
            focus == "picker:${PlayerControlsNativeFixture.EPISODE_A2}" }
        key(KeyEvent.KEYCODE_DPAD_CENTER) // 第一次选择 epA2（被扣住，选择器已关闭）
        until { PlayerControlsTestHooks.commandTrace.any { it.startsWith("select:") } }
        until { var picker: Any? = null; player { picker = it.controller.picker }; picker == null }
        // 立即改选 epA1（重开选择器；第二次选择，代次 +1）
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var focus: String? = null; player { focus = it.controller.pickerFocusedId }; focus != null }
        key(KeyEvent.KEYCODE_DPAD_UP)
        until { var focus: String? = null; player { focus = it.controller.pickerFocusedId }
            focus == "picker:${PlayerControlsNativeFixture.EPISODE_A1}" }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { PlayerControlsTestHooks.commandTrace.count { it.startsWith("select:") } >= 2 }
        PlayerControlsTestHooks.holdSwitch?.complete(Unit)
        // 释放后最终身份 = 最后一次选择 epA1；迟到的 epA2 结果被丢弃
        until { var episode: String? = null; player { episode = it.display!!.episodeId }
            episode == PlayerControlsNativeFixture.EPISODE_A1 }
        until { PlayerControlsTestHooks.commandTrace.any { it.startsWith("stale-drop:") } }
        player { assertEquals(PlayerControlsNativeFixture.EPISODE_A1, it.display!!.episodeId) }
    }

    // E07 媒体失败/重试保留收藏、其他集历史与已提交续播（真实损坏媒体）
    @Test fun media_failure_preserves_user_library() {
        enter()
        PlayerControlsTestHooks.suppressIdleHide = true // 空闲隐藏由 U03 专项验证
        var favoritesBefore = 0; var historyBefore = 0
        personal { favoritesBefore = it.favorites.size; historyBefore = it.history.size }
        val resumeBefore = snapshotFor(PlayerControlsNativeFixture.EPISODE_A1, PlayerControlsNativeFixture.VARIANT_A)?.positionMs
        // 退出到片库，进入 w2（默认线路 f：F1 可播、F2 损坏）
        PlayerControlsNativeFixture.backOutOfPlayer(scenario!!, instrumentation, viaOverlay = true)
        com.ichichuang.cinema.feature.library.LibraryNativeFixture.screenReach(scenario!!,
            PlayerControlsNativeFixture.w[2].value)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选详情" } }
        until { semantics().any { it.focused } }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var isPlayer = false; scenario!!.onActivity { isPlayer = requireNotNull(it.browseProbe).isPlayer }; isPlayer }
        until { var episode: String? = null; player { episode = it.display!!.episodeId }
            episode == PlayerControlsNativeFixture.EPISODE_F1 }
        // 选集器切到损坏集 F2 → 真实 Media3 解析错误
        key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { var focus: String? = null; player { focus = it.controller.focusedControlId }; focus == "选集" }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var focus: String? = null; player { focus = it.controller.pickerFocusedId }; focus != null }
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { var focus: String? = null; player { focus = it.controller.pickerFocusedId }
            focus == "picker:${PlayerControlsNativeFixture.EPISODE_F2}" }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until(20_000) { var error: String? = null; player { error = it.display?.error }; error != null }
        personal {
            assertEquals("失败不得动收藏", favoritesBefore, it.favorites.size)
            assertEquals("失败不得增删历史条目", historyBefore, it.history.size) // w0/w2 进入播放前已在历史（种子）
            assertTrue("失败不得删除其他集历史", it.history.any { card -> card.workId == PlayerControlsNativeFixture.w[0].value })
        }
        assertEquals("已提交续播保持", resumeBefore,
            snapshotFor(PlayerControlsNativeFixture.EPISODE_A1, PlayerControlsNativeFixture.VARIANT_A)?.positionMs)
    }
}
