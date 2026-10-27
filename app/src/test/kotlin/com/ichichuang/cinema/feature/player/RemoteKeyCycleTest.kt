package com.ichichuang.cinema.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R16 物理按键周期状态机（K03/K04/K05/S04 的 JVM 层）：
 * 每个物理周期（DOWN…UP/CANCEL）至多一次激活；
 * 短按只在完整短周期结束时触发，长按绝不先触发短按；
 * 方向键允许有界的自动重复，滞留键/孤儿 UP/CANCEL 不得产生幽灵点击。
 */
class RemoteKeyCycleTest {
    private val ok = 23        // KEYCODE_DPAD_CENTER
    private val back = 4       // KEYCODE_BACK
    private val left = 21      // KEYCODE_DPAD_LEFT
    private val enter = 66     // KEYCODE_ENTER

    private fun machine() = RemoteKeyCycle(longPressThresholdMs = 400L, repeatDelayMs = 400L, repeatIntervalMs = 200L)

    private fun down(key: Int, at: Long, repeat: Int = 0) =
        RemoteKeyEvent(RemoteKeyEvent.ACTION_DOWN, key, repeat, at)

    private fun up(key: Int, at: Long) = RemoteKeyEvent(RemoteKeyEvent.ACTION_UP, key, 0, at)

    private fun cancel(key: Int, at: Long) = RemoteKeyEvent(RemoteKeyEvent.ACTION_CANCEL, key, 0, at)

    @Test
    fun short_ok_fires_once_on_up_and_nothing_on_down() {
        val m = machine()
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(down(ok, 0)))
        assertEquals(listOf(RemoteKeyActivation.PRESS), m.onEvent(up(ok, 120)))
    }

    @Test
    fun enter_key_behaves_like_ok_single_activation_per_cycle() {
        val m = machine()
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(down(enter, 0)))
        assertEquals(listOf(RemoteKeyActivation.PRESS), m.onEvent(up(enter, 90)))
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(up(enter, 95)))
    }

    @Test
    fun long_ok_by_platform_repeat_fires_once_and_never_short() {
        val m = machine()
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(down(ok, 0)))
        assertEquals(listOf(RemoteKeyActivation.LONG_PRESS), m.onEvent(down(ok, 410, repeat = 1)))
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(down(ok, 460, repeat = 2)))
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(up(ok, 500)))
    }

    @Test
    fun long_ok_by_elapsed_threshold_fires_once_without_platform_repeat() {
        val m = machine()
        m.onEvent(down(ok, 0))
        assertEquals(emptyList<RemoteKeyActivation>(), m.checkTimeouts(399))
        assertEquals(listOf(RemoteKeyActivation.LONG_PRESS), m.checkTimeouts(401))
        assertEquals(emptyList<RemoteKeyActivation>(), m.checkTimeouts(600))
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(up(ok, 620)))
    }

    /** K04 的关键形态：首个 DOWN 的 repeatCount 为 0（尚未长按）时，长按也绝不先出短按。 */
    @Test
    fun long_ok_with_zero_initial_repeat_count_never_fires_short_first() {
        val m = machine()
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(down(ok, 0, repeat = 0)))
        assertEquals(listOf(RemoteKeyActivation.LONG_PRESS), m.checkTimeouts(450))
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(up(ok, 470)))
    }

    @Test
    fun repeated_ok_down_events_do_not_double_long_press() {
        val m = machine()
        m.onEvent(down(ok, 0))
        m.onEvent(down(ok, 410, repeat = 1))
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(down(ok, 430, repeat = 2)))
    }

    @Test
    fun directional_press_fires_on_down_with_bounded_repeat() {
        val m = machine()
        assertEquals(listOf(RemoteKeyActivation.PRESS), m.onEvent(down(left, 0)))
        // 节奏内的重复检查不发射；超过间隔后每次检查至多一次。
        assertEquals(emptyList<RemoteKeyActivation>(), m.checkTimeouts(300))
        assertEquals(listOf(RemoteKeyActivation.REPEAT), m.checkTimeouts(401))
        assertEquals(emptyList<RemoteKeyActivation>(), m.checkTimeouts(402))
        assertEquals(listOf(RemoteKeyActivation.REPEAT), m.checkTimeouts(601))
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(up(left, 700)))
    }

    @Test
    fun directional_platform_repeat_events_are_rate_bounded() {
        val m = machine()
        m.onEvent(down(left, 0))
        assertEquals(listOf(RemoteKeyActivation.REPEAT), m.onEvent(down(left, 410, repeat = 1)))
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(down(left, 420, repeat = 2)))
        assertEquals(listOf(RemoteKeyActivation.REPEAT), m.onEvent(down(left, 615, repeat = 3)))
    }

    @Test
    fun orphan_up_without_down_is_ignored() {
        val m = machine()
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(up(ok, 0)))
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(up(back, 10)))
    }

    @Test
    fun mismatched_up_does_not_end_other_key_cycle() {
        val m = machine()
        m.onEvent(down(ok, 0))
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(up(back, 50)))
        assertEquals(listOf(RemoteKeyActivation.PRESS), m.onEvent(up(ok, 90)))
    }

    @Test
    fun action_cancel_revokes_cycle_without_activation() {
        val m = machine()
        m.onEvent(down(ok, 0))
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(cancel(ok, 100)))
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(up(ok, 120)))
    }

    /** 丢失 UP 后同键再来一次真实 DOWN：不得累计出两次激活（幽灵点击）。 */
    @Test
    fun lost_up_then_new_down_does_not_ghost_double_activation() {
        val m = machine()
        m.onEvent(down(ok, 0))
        // UP 丢失：新的物理 DOWN（repeatCount 归零重新开始）静默作废旧周期。
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(down(ok, 300)))
        assertEquals(listOf(RemoteKeyActivation.PRESS), m.onEvent(up(ok, 380)))
    }

    @Test
    fun held_back_key_single_activation_only() {
        val m = machine()
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(down(back, 0)))
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(down(back, 100, repeat = 1)))
        assertEquals(listOf(RemoteKeyActivation.PRESS), m.onEvent(up(back, 150)))
    }

    @Test
    fun revoke_clears_held_state_and_later_up_does_not_activate() {
        val m = machine()
        m.onEvent(down(ok, 0))
        m.revoke()
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(up(ok, 50)))
        assertEquals(emptyList<RemoteKeyActivation>(), m.checkTimeouts(500))
    }

    @Test
    fun second_key_down_while_one_held_is_ignored() {
        val m = machine()
        m.onEvent(down(ok, 0))
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(down(back, 20)))
        assertEquals(listOf(RemoteKeyActivation.PRESS), m.onEvent(up(ok, 60)))
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(up(back, 70)))
    }

    @Test
    fun multi_action_events_are_ignored() {
        val m = machine()
        assertEquals(emptyList<RemoteKeyActivation>(),
            m.onEvent(RemoteKeyEvent(RemoteKeyEvent.ACTION_MULTIPLE, ok, 0, 0)))
    }

    // ── R17 目标 A：长按在 ticker 心跳之前松开，绝不误发短按 ─────────────
    // 关键竞态：UP 到达时 elapsed >= threshold，但 checkTimeouts() 从未运行
    //（longFired 仍为 false）。此时必须补发 LONG_PRESS，绝不能当作短按。

    @Test
    fun long_release_before_ticker_1ms_above_threshold_emits_long_not_short() {
        val m = machine()
        m.onEvent(down(ok, 0))
        // 401ms 松开：超过 400ms 阈值，但 ticker 从未执行 → longFired 仍为 false
        assertEquals(listOf(RemoteKeyActivation.LONG_PRESS), m.onEvent(up(ok, 401)))
    }

    @Test
    fun long_release_exactly_at_threshold_emits_long_not_short() {
        val m = machine()
        m.onEvent(down(ok, 0))
        assertEquals(listOf(RemoteKeyActivation.LONG_PRESS), m.onEvent(up(ok, 400)))
    }

    @Test
    fun release_1ms_below_threshold_still_emits_short() {
        val m = machine()
        m.onEvent(down(ok, 0))
        assertEquals(listOf(RemoteKeyActivation.PRESS), m.onEvent(up(ok, 399)))
    }

    @Test
    fun long_back_release_before_ticker_never_emits_short_back() {
        val m = machine()
        m.onEvent(down(back, 0))
        // 长 Back 松开（阈值以上）：补发 LONG_PRESS，绝不误发短 Back。
        assertEquals(listOf(RemoteKeyActivation.LONG_PRESS), m.onEvent(up(back, 500)))
    }

    @Test
    fun long_release_after_ticker_already_fired_is_silent() {
        val m = machine()
        m.onEvent(down(ok, 0))
        assertEquals(listOf(RemoteKeyActivation.LONG_PRESS), m.checkTimeouts(450)) // ticker 已发长按
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(up(ok, 470)))     // 补发路径不重复
    }

    @Test
    fun long_release_before_ticker_then_platform_repeat_does_not_double() {
        val m = machine()
        m.onEvent(down(ok, 0))
        // 平台 repeat 事件先到（repeatCount>0 且阈值已过）：长按在 DOWN 阶段发出
        assertEquals(listOf(RemoteKeyActivation.LONG_PRESS), m.onEvent(down(ok, 410, repeat = 1)))
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(up(ok, 450)))     // UP 不再补发/不发短按
    }

    @Test
    fun consecutive_long_cycles_each_emit_exactly_one_long_press() {
        val m = machine()
        m.onEvent(down(ok, 0))
        assertEquals(listOf(RemoteKeyActivation.LONG_PRESS), m.onEvent(up(ok, 500)))
        m.onEvent(down(ok, 600))
        assertEquals(listOf(RemoteKeyActivation.LONG_PRESS), m.onEvent(up(ok, 1100)))
        m.onEvent(down(ok, 1200))
        assertEquals(listOf(RemoteKeyActivation.LONG_PRESS), m.onEvent(up(ok, 1700)))
    }

    @Test
    fun lost_up_then_long_release_does_not_ghost_extra_activation() {
        val m = machine()
        m.onEvent(down(ok, 0))
        m.onEvent(down(ok, 300))          // UP 丢失，新物理 DOWN 重开周期（downAtMs 重置为 300）
        assertEquals(listOf(RemoteKeyActivation.LONG_PRESS), m.onEvent(up(ok, 800))) // 新周期 500ms，恰好一次长按
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(up(ok, 810)))       // 孤儿 UP 无激活
    }

    @Test
    fun long_release_after_revoke_is_ignored() {
        val m = machine()
        m.onEvent(down(ok, 0))
        m.revoke()                          // 窗口失焦：作废周期
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(up(ok, 500)))
    }

    @Test
    fun cancel_mapped_up_never_emits_long_press() {
        val m = machine()
        m.onEvent(down(ok, 0))
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(cancel(ok, 100)))  // ACTION_CANCEL
        assertEquals(emptyList<RemoteKeyActivation>(), m.onEvent(up(ok, 500)))       // 后续 UP 无激活
    }
}
