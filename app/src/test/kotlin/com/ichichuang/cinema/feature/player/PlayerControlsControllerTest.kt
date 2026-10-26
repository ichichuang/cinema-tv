package com.ichichuang.cinema.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R16 控制器（浮层/焦点/时间轴预览/选择器）的 JVM 层：
 * U03–U06、K02、S01–S06、E01/E02/E04 的纯逻辑判据；
 * 真实 Compose/Media3/Room 断言在原生测试里执行。
 */
class PlayerControlsControllerTest {

    private fun lines(vararg ids: String) = ids.map { PlayerPickerLine(it, "线路$it", available = true, episodeCount = 2) }
    private fun episodes(vararg ids: String) = ids.map { PlayerPickerEpisode(it, "第${ids.indexOf(it) + 1}集", playable = true) }

    private fun controller(
        durationMs: Long? = 60_000L,
        seekAvailable: Boolean = true,
        routeLines: List<PlayerPickerLine> = lines("variant-a", "variant-b"),
        routeEpisodes: List<PlayerPickerEpisode> = episodes("ep-1", "ep-2"),
    ) = PlayerControlsController().also {
        it.install(durationMs, seekAvailable, routeLines, routeEpisodes,
            currentVariantId = "variant-a", currentEpisodeId = "ep-1", positionMs = 10_000L)
    }

    // ── 浮层揭示与自动隐藏资格（U03/U04） ─────────────────────────

    @Test
    fun reveal_shows_controls_and_focuses_first_enabled_control() {
        val c = controller()
        assertTrue(c.revealByUser())
        assertTrue(c.overlayVisible)
        assertEquals("播放控制", c.focusedControlId)
    }

    @Test
    fun hide_is_refused_while_picker_open_or_seek_preview_active() {
        val c = controller()
        c.revealByUser()
        c.openPicker(PlayerPickerKind.EPISODES)
        assertFalse(c.hideAllowed())
        c.closePicker()
        assertTrue(c.hideAllowed())
        c.focusControl("时间轴")
        c.beginPreview(10_000L)
        assertFalse(c.hideAllowed())
        c.cancelPreview()
        assertTrue(c.hideAllowed())
    }

    // ── 可见层方向键只导航，不同时拖动/切集（K02） ────────────────

    @Test
    fun visible_arrows_move_exactly_one_focus_target_and_never_seek() {
        val c = controller()
        c.revealByUser()
        assertEquals(PlayerControlCommand.FocusMoved, c.move(PlayerDirection.RIGHT))
        assertEquals("时间轴", c.focusedControlId)
        // 时间轴聚焦后左右是预览（焦点不移动、无 seek 命令）；下键离开到选集
        assertEquals(PlayerControlCommand.FocusMoved, c.move(PlayerDirection.RIGHT))
        assertEquals("时间轴", c.focusedControlId)
        assertEquals(25_000L, c.previewMs)
        c.cancelPreview()
        assertEquals(PlayerControlCommand.FocusMoved, c.move(PlayerDirection.DOWN))
        assertEquals("选集", c.focusedControlId)
        assertEquals(PlayerControlCommand.FocusMoved, c.move(PlayerDirection.RIGHT))
        assertEquals("线路", c.focusedControlId)
        // 上键从时间轴离开回到播放控制
        c.overrideFocus("时间轴")
        assertEquals(PlayerControlCommand.FocusMoved, c.move(PlayerDirection.UP))
        assertEquals("播放控制", c.focusedControlId)
    }

    @Test
    fun move_beyond_edge_stays_on_last_control_without_wrapping() {
        val c = controller()
        c.revealByUser()
        c.move(PlayerDirection.RIGHT)                    // → 时间轴（捕获左右）
        c.move(PlayerDirection.DOWN); c.move(PlayerDirection.RIGHT)  // → 选集 → 线路
        repeat(4) { c.move(PlayerDirection.RIGHT) }
        assertEquals("线路", c.focusedControlId)
        c.move(PlayerDirection.LEFT); c.move(PlayerDirection.LEFT); c.move(PlayerDirection.UP)
        assertEquals("播放控制", c.focusedControlId)
    }

    // ── 禁用控件不聚焦、不陷阱（U06/U05/S06） ────────────────────

    @Test
    fun unknown_duration_disables_timeline_and_navigation_skips_it() {
        val c = controller(durationMs = null)
        c.revealByUser()
        assertEquals("播放控制", c.focusedControlId)
        assertEquals(PlayerControlCommand.FocusMoved, c.move(PlayerDirection.RIGHT))
        // 时间轴在时长未知时禁用：焦点必须跳过它到“选集”。
        assertEquals("选集", c.focusedControlId)
        assertFalse(c.controlEnabled("时间轴"))
        assertNull(c.previewMs)
    }

    @Test
    fun nonseekable_media_disables_timeline_even_with_known_duration() {
        val c = controller(seekAvailable = false)
        c.revealByUser()
        c.move(PlayerDirection.RIGHT)
        assertEquals("选集", c.focusedControlId)
        assertFalse(c.controlEnabled("时间轴"))
    }

    @Test
    fun confirm_on_disabled_focused_control_is_none() {
        val c = controller(durationMs = null)
        c.revealByUser()
        c.overrideFocus("时间轴")
        assertEquals(PlayerControlCommand.None, c.confirm())
    }

    // ── 时间轴预览：有界、可打断、确认一次、取消无副作用（S01–S04） ──

    @Test
    fun preview_adjusts_bounded_by_real_duration_and_zero() {
        val c = controller()
        c.revealByUser()
        c.focusControl("时间轴")
        c.beginPreview(58_000L)
        c.adjustPreview(+15_000L)
        assertEquals(60_000L, c.previewMs)   // 不得超过真实时长
        c.adjustPreview(-200_000L)
        assertEquals(0L, c.previewMs)        // 不得为负
    }

    @Test
    fun preview_cannot_start_without_duration_or_command() {
        val c = controller(durationMs = null)
        c.revealByUser()
        c.focusControl("时间轴")
        assertFalse(c.beginPreview(1_000L))
        assertNull(c.previewMs)
    }

    @Test
    fun confirm_preview_emits_single_seek_and_exits_preview() {
        val c = controller()
        c.revealByUser()
        c.focusControl("时间轴")
        c.beginPreview(10_000L)
        c.adjustPreview(+15_000L)
        assertEquals(PlayerControlCommand.SeekTo(25_000L), c.confirm())
        assertNull(c.previewMs)
        assertEquals(PlayerControlCommand.None, c.confirm()) // 再按 OK：时间轴无预览时不再产生第二次 seek
    }

    @Test
    fun cancel_preview_clears_target_without_mutation() {
        val c = controller()
        c.revealByUser()
        c.focusControl("时间轴")
        c.beginPreview(10_000L)
        c.adjustPreview(+15_000L)
        assertEquals(PlayerControlCommand.PreviewCancelled, c.back())
        assertNull(c.previewMs)
    }

    // ── 选择器：证据身份、禁用线路、焦点陷阱、Back 恢复打开者（E01/E02/E04/K07/K08） ──

    @Test
    fun picker_lists_only_evidence_identities_with_current_selected() {
        val c = controller()
        c.revealByUser()
        c.focusControl("选集")
        assertEquals(PlayerControlCommand.PickerOpened, c.confirm())
        assertEquals(PlayerPickerKind.EPISODES, c.picker)
        assertEquals(listOf("ep-1", "ep-2"), c.pickerEpisodes.map { it.episodeId })
        assertEquals("picker:ep-1", c.pickerFocusedId)
    }

    @Test
    fun picker_navigation_traps_in_layer_and_back_restores_opener_exactly() {
        val c = controller()
        c.revealByUser()
        c.focusControl("选集")
        c.confirm()
        assertEquals("picker:ep-2", c.pickerMove(PlayerDirection.DOWN))
        assertEquals("picker:ep-2", c.pickerFocusedId)
        assertEquals(PlayerControlCommand.PickerDismissed, c.back())
        assertNull(c.picker)
        assertEquals("选集", c.focusedControlId)
    }

    @Test
    fun picker_skips_disabled_lines_and_refuses_to_activate_them() {
        val disabled = listOf(
            PlayerPickerLine("variant-a", "可用线路", available = true, episodeCount = 2),
            PlayerPickerLine("variant-x", "已停用线路", available = false, episodeCount = 1))
        val c = controller(routeLines = disabled)
        c.revealByUser()
        c.focusControl("线路")
        c.confirm()
        // 禁用线路不获得焦点（请求被拒，保持可用项）。
        assertEquals("picker:variant-a", c.pickerFocused("picker:variant-x"))
        // 唯一可用项下方导航不落入禁用项。
        c.pickerMove(PlayerDirection.DOWN)
        assertEquals("picker:variant-a", c.pickerFocusedId)
        assertEquals(PlayerPickerKind.LINES, c.picker)
    }

    @Test
    fun picker_confirm_selects_stable_identity_and_closes_layer() {
        val c = controller()
        c.revealByUser()
        c.focusControl("线路")
        c.confirm()
        c.pickerMove(PlayerDirection.DOWN)
        assertEquals(PlayerControlCommand.SelectLine("variant-b"), c.confirm())
        assertNull(c.picker)
    }

    @Test
    fun picker_episodes_confirm_selects_stable_episode_id() {
        val c = controller()
        c.revealByUser()
        c.focusControl("选集")
        c.confirm()
        c.pickerMove(PlayerDirection.DOWN)
        assertEquals(PlayerControlCommand.SelectEpisode("ep-2"), c.confirm())
    }

    // ── 逐层 Back（K07）与迟到焦点恢复取消（K09） ──────────────────

    @Test
    fun back_unwinds_picker_then_controls_then_player_layer() {
        val c = controller()
        c.revealByUser()
        c.focusControl("选集")
        c.confirm()
        assertEquals(PlayerControlCommand.PickerDismissed, c.back())
        assertEquals(PlayerControlCommand.OverlayDismissed, c.back())
        assertEquals(PlayerControlCommand.ExitLayer, c.back())
    }

    @Test
    fun user_move_cancels_pending_focus_restore() {
        val c = controller()
        c.revealByUser()
        c.requestRestore("线路")
        assertEquals("线路", c.consumeRestore())
        c.requestRestore("选集")
        c.move(PlayerDirection.RIGHT) // 用户真实移动
        assertNull(c.consumeRestore())
    }

    @Test
    fun error_state_shows_retry_control_and_keeps_others_enabled() {
        val c = controller()
        c.install(60_000L, true, lines("variant-a", "variant-b"), episodes("ep-1", "ep-2"),
            "variant-a", "ep-1", 10_000L, retryAvailable = true)
        c.revealByUser()
        assertTrue(c.controlEnabled("播放控制"))
        assertTrue(c.controlEnabled("重试"))
        // 错误态“重试”可达（时间轴捕获左右，经 选集→线路→重试）；默认（无错误）时不在可用行内。
        c.move(PlayerDirection.RIGHT); c.move(PlayerDirection.DOWN)
        c.move(PlayerDirection.RIGHT); c.move(PlayerDirection.RIGHT)
        assertEquals("重试", c.focusedControlId)
        val quiet = controller()
        quiet.revealByUser()
        quiet.move(PlayerDirection.RIGHT); quiet.move(PlayerDirection.DOWN); quiet.move(PlayerDirection.RIGHT)
        assertEquals("线路", quiet.focusedControlId)
    }
}
