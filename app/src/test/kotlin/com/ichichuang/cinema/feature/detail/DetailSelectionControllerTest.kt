package com.ichichuang.cinema.feature.detail

import com.ichichuang.cinema.domain.model.catalog.EpisodeId
import com.ichichuang.cinema.domain.model.catalog.SourceId
import com.ichichuang.cinema.domain.model.catalog.VariantId
import com.ichichuang.cinema.domain.model.catalog.WorkId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R15 选择控制器 JVM 断言：焦点/选择分立、弹层陷阱、迟到刷新与回退（E/N 行 JVM 侧证据）。 */
class DetailSelectionControllerTest {
    private fun episode(id: String) = DetailFixtureEpisode(EpisodeId(id), "第$id", 1, "file:///tmp/$id.wav")
    private fun route(id: String, state: DetailRouteState = DetailRouteState.AVAILABLE, episodes: List<String> = listOf("ep-1")) =
        DetailFixtureRoute(VariantId(id), "entry-$id", SourceId("test-source"), "item-$id", "线路$id", state,
            episodes.map(::episode), observedAt = 5, ruleVersion = "round15")

    private fun installed(vararg routes: DetailFixtureRoute): DetailSelectionController =
        DetailSelectionController().apply {
            install(WorkId("w-1"), routes.toList(), routes.first().routeId.value, "ep-1", "播放操作")
        }

    // E06 弹层打开后陷阱内移动，背景键不可达
    @Test fun sheet_traps_focus_and_background_unreachable() {
        val controller = installed(route("ra", episodes = listOf("ep-1", "ep-2")), route("rb"))
        assertEquals(DetailCommand.OpenSheet(DetailSheetKind.LINES), controller.confirmOn("选择线路"))
        assertEquals(DetailSheetKind.LINES, controller.sheet)
        assertEquals(listOf("线路单:ra", "线路单:rb"), controller.sheetKeys())
        assertTrue(controller.move(DetailDirection.DOWN))
        assertEquals("线路单:rb", controller.sheetFocusedKey)
        assertTrue(controller.move(DetailDirection.LEFT))
        assertEquals("线路单:rb", controller.sheetFocusedKey)
        assertTrue(controller.move(DetailDirection.RIGHT))
        assertEquals("线路单:rb", controller.sheetFocusedKey)
    }

    // E06/N03 Back 关闭弹层并精确回到打开者
    @Test fun sheet_back_restores_opener() {
        val controller = installed(route("ra"), route("rb"))
        controller.confirmOn("选择线路")
        controller.move(DetailDirection.DOWN)
        assertEquals(DetailCommand.SheetDismissed, controller.back())
        assertNull(controller.sheet)
        assertEquals("选择线路", controller.pendingRestoreKey)
        assertEquals("选择线路", controller.consumeRestore())
        assertNull(controller.pendingRestoreKey)
    }

    // 弹层 OK 确认一次：选择改变、弹层关闭、焦点回打开者
    @Test fun sheet_confirm_selects_and_closes() {
        val controller = installed(route("ra"), route("rb"))
        controller.confirmOn("选择线路")
        controller.move(DetailDirection.DOWN)
        assertEquals(DetailCommand.SelectRoute("rb"), controller.confirm())
        assertNull(controller.sheet)
        assertEquals("rb", controller.selectedRouteId)
        assertEquals("选择线路", controller.consumeRestore())
    }

    // 焦点与选择分立：移动焦点不改变已选
    @Test fun focus_does_not_change_selection() {
        val controller = installed(route("ra"), route("rb"))
        controller.move(DetailDirection.DOWN)
        assertEquals("线路:ra", controller.focusedKey)
        assertEquals("ra", controller.selectedRouteId)
        controller.move(DetailDirection.RIGHT)
        assertEquals("线路:rb", controller.focusedKey)
        assertEquals("ra", controller.selectedRouteId)
    }

    // 直接在线路上 OK 即选择该线路（按稳定身份，不看标签）
    @Test fun line_ok_selects_by_stable_id() {
        val controller = installed(route("ra"), route("rb"))
        controller.move(DetailDirection.DOWN)
        controller.move(DetailDirection.RIGHT)
        assertEquals(DetailCommand.SelectRoute("rb"), controller.confirm())
        assertEquals("rb", controller.selectedRouteId)
    }

    // 无选择时播放动作不派发
    @Test fun play_without_selection_is_noop() {
        val controller = DetailSelectionController().apply { install(WorkId("w-1"), emptyList(), null, null, "播放操作") }
        assertEquals(DetailCommand.None, controller.confirm())
    }

    // A04/E10 派发时读取当前选择
    @Test fun play_uses_current_selection() {
        val controller = installed(route("ra", episodes = listOf("ep-1", "ep-2")))
        controller.move(DetailDirection.DOWN)
        controller.move(DetailDirection.DOWN)
        controller.move(DetailDirection.DOWN)
        assertEquals(DetailCommand.SelectEpisode("ep-2"), controller.confirm())
        assertEquals(DetailCommand.RequestPlay("ra", "ep-2"), controller.confirmOn("播放操作"))
    }

    // E10 被更新的刷新令牌取代的迟到刷新被丢弃
    @Test fun stale_refresh_token_dropped() {
        val controller = installed(route("ra"), route("rb"))
        val first = controller.beginRefresh()
        val second = controller.beginRefresh()
        assertNotEquals(first, second)
        assertNull(controller.completeRefresh(first, listOf(route("ra"))))
        assertEquals(2, controller.routes.size)
        assertEquals(listOf("ra"), controller.completeRefresh(second, listOf(route("ra")))!!.let { controller.routes }.map { it.routeId.value })
    }

    // E10 应用刷新不覆盖用户新选择与焦点
    @Test fun refresh_never_overwrites_user_choice() {
        val controller = installed(route("ra", episodes = listOf("ep-1", "ep-2")), route("rb"))
        controller.selectEpisode("ep-2")
        controller.move(DetailDirection.DOWN)
        controller.move(DetailDirection.DOWN)
        controller.move(DetailDirection.DOWN)
        assertEquals("集数:ep-2", controller.focusedKey)
        val token = controller.beginRefresh()
        controller.completeRefresh(token, listOf(
            route("ra", episodes = listOf("ep-3", "ep-1", "ep-2")), route("rb")))
        assertEquals("ra", controller.selectedRouteId)
        assertEquals("ep-2", controller.selectedEpisodeId)
        assertEquals("集数:ep-2", controller.focusedKey)
    }

    // N07 选中线路被移除：回退到顶替其位置的同列稳定线路，不跳无关位置
    @Test fun removed_route_falls_back_to_replacement_neighbor() {
        val controller = installed(route("ra"), route("rb"), route("rc"))
        controller.selectRoute("rb")
        controller.move(DetailDirection.DOWN)
        controller.move(DetailDirection.RIGHT)
        assertEquals("线路:rb", controller.focusedKey)
        val token = controller.beginRefresh()
        val removed = controller.completeRefresh(token, listOf(route("ra"), route("rc")))
        assertEquals("rc", removed?.nearestRouteId)
        assertEquals("rc", controller.selectedRouteId)
        assertEquals("线路:rc", controller.consumeRestore())
    }

    // N04 用户输入撤销迟到焦点恢复
    @Test fun user_input_cancels_late_restore() {
        val controller = installed(route("ra"), route("rb"))
        controller.confirmOn("选择线路")
        controller.back()
        assertEquals("选择线路", controller.pendingRestoreKey)
        val epoch = controller.inputEpoch
        assertTrue(controller.move(DetailDirection.RIGHT))
        assertNull(controller.pendingRestoreKey)
        assertTrue(controller.inputEpoch > epoch)
    }

    // 集数弹层按当前所选线路呈现
    @Test fun episode_sheet_lists_selected_route_episodes() {
        val controller = installed(route("ra", episodes = listOf("ep-1", "ep-2")), route("rb", episodes = listOf("ep-9")))
        controller.selectRoute("rb")
        assertEquals(DetailCommand.OpenSheet(DetailSheetKind.EPISODES), controller.confirmOn("选择集数"))
        assertEquals(listOf("集数单:ep-9"), controller.sheetKeys())
    }

    // 动作行左右可达全部基本遥控操作
    @Test fun actions_reachable_by_dpad() {
        val controller = installed(route("ra"))
        assertEquals("播放操作", controller.focusedKey)
        controller.move(DetailDirection.RIGHT)
        assertEquals("选择线路", controller.focusedKey)
        controller.move(DetailDirection.RIGHT)
        assertEquals("选择集数", controller.focusedKey)
        controller.move(DetailDirection.RIGHT)
        assertEquals("收藏操作", controller.focusedKey)
        controller.move(DetailDirection.RIGHT)
        assertEquals("详情返回", controller.focusedKey)
        controller.move(DetailDirection.RIGHT)
        assertEquals("详情返回", controller.focusedKey)
        controller.move(DetailDirection.UP)
        assertEquals("详情返回", controller.focusedKey)
    }
}
