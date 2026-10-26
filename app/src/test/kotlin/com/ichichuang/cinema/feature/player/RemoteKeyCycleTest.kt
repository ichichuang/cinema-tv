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
}
