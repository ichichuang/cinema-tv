package com.ichichuang.cinema.feature.player

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.MainActivity
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * G02（播放器控制门禁 OFF 侧）：仅既有七探针配对（playerControls OFF）时，
 * 播放器层保持 R12 简易形态，不出现 R16 控制浮层；普通（全 OFF）路径由
 * 既有 BrowseProbeOff/LegacyPlayback 回归继续覆盖。
 * 本类在 r16-player-off 相位执行（构建参数不带 playerControlsProbe）。
 */
class PlayerControlsOffNativeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun ownedPrivateFixture() {
        assertFalse(com.ichichuang.cinema.BuildConfig.PLAYER_CONTROLS_PROBE)
        PlayerControlsNativeFixture.setup(context)
    }
    @After fun restore() {
        scenario?.close(); scenario = null
        PlayerControlsNativeFixture.teardown(context)
    }

    private fun semantics() = PlayerControlsNativeFixture.semantics(scenario!!)
    private fun until(timeout: Long = 12_000, condition: () -> Boolean) = PlayerControlsNativeFixture.until(timeout, condition)
    private fun key(code: Int) = PlayerControlsNativeFixture.key(instrumentation, code)

    @Test fun player_controls_off_keeps_r12_player_layer_and_never_surfaces_controls() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        PlayerControlsNativeFixture.scenarioRef = scenario
        PlayerControlsNativeFixture.enterPlayerFromLibrary(scenario!!, instrumentation, 0)
        // R12 简易播放器层真实出现
        until { semantics().any { it.tag == "实际播放目标" } }
        // R16 控制浮层绝不出现；控制运行时不构造
        scenario!!.onActivity { assertNull(it.playerControlsProbe) }
        assertFalse(semantics().any { it.tag == "控制浮层" || it.tag == "时间轴" || it.tag == "控件行" })
        // 既有 R12 按键路径仍工作（详情动作列表）
        assertTrue(semantics().any { it.tag == "播放暂停" || it.text.contains("播放") })
    }
}
