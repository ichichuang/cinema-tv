package com.ichichuang.cinema.feature.detail

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

/** R15 详情导航/焦点/返回原生测试：精确来源恢复、逐层 Back 与焦点陷阱（七探针配对）。 */
class WorkDetailNavigationNativeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun ownedPrivateFixture() = WorkDetailNativeFixture.setup(context)

    @After fun restore() {
        WorkDetailTestHooks.holdRouteRefresh?.complete(Unit); WorkDetailTestHooks.holdRouteRefresh = null
        WorkDetailTestHooks.beforePlayDispatch?.complete(Unit); WorkDetailTestHooks.beforePlayDispatch = null
        scenario?.close(); scenario = null
        WorkDetailNativeFixture.teardown(context)
    }

    private fun semantics() = WorkDetailNativeFixture.semantics(scenario!!)
    private fun until(timeout: Long = 12_000, condition: () -> Boolean) = WorkDetailNativeFixture.until(timeout, condition)
    private fun key(code: Int) {
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, code))
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, code))
        instrumentation.waitForIdleSync()
    }
    private fun detail(action: (WorkDetailRuntime) -> Unit) { scenario!!.onActivity { action(requireNotNull(it.detailProbe)) } }
    private fun library(action: (com.ichichuang.cinema.feature.library.LibraryRuntime) -> Unit) {
        scenario!!.onActivity { action(requireNotNull(it.libraryProbe)) }
    }
    private fun home(action: (com.ichichuang.cinema.feature.home.visual.VisualHomeRuntime) -> Unit) {
        scenario!!.onActivity { action(requireNotNull(it.homeProbe)) }
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        WorkDetailNativeFixture.scenarioRef = scenario
        com.ichichuang.cinema.feature.library.LibraryNativeFixture.scenarioRef = scenario
        until { semantics().any { it.tag == "候选首页" } }
    }

    private fun navTo(label: String) {
        until { semantics().any { it.focused } }
        repeat(6) { key(KeyEvent.KEYCODE_DPAD_UP) }
        until { semantics().any { it.focused && it.tag.startsWith("nav:") } }
        val order = com.ichichuang.cinema.feature.home.visual.VisualHomeRuntime.NAV_ITEMS
        var steps = 0
        while (steps++ < 8) {
            val current = semantics().firstOrNull { it.focused && it.tag.startsWith("nav:") }?.tag
            if (current == "nav:$label") break
            val index = order.indexOf(current?.removePrefix("nav:"))
            check(index >= 0) { "导航行未聚焦：$current" }
            key(if (order.indexOf(label) > index) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
            until(2_000) { semantics().firstOrNull { it.focused && it.tag.startsWith("nav:") }?.tag != current }
        }
        until { semantics().any { it.tag == "nav:$label" && it.focused } }
    }

    private fun enterLibrary() {
        navTo("片库"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选片库" } }
        until { var size = -1; library { size = it.display.items.size }; size == 6 }
    }

    private fun openDetailFromGrid(workTag: String) {
        com.ichichuang.cinema.feature.library.LibraryNativeFixture.screenReach(scenario!!, workTag)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选详情" } }
        until { var id: String? = null; detail { id = it.installedWorkId }; id != null }
        until { semantics().any { it.tag == "播放操作" && it.focused } }
    }

    // N01 四个来源各自精确返回
    @Test fun detail_back_returns_home_origin() {
        launch()
        // 首页英雄区：一次 DOWN 到续播按钮，再 RIGHT 到“查看详情”（非续播，不直达播放器）。
        until { semantics().any { it.focused } }
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { semantics().any { it.tag == "继续播放" && it.focused } }
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        until { semantics().any { it.tag == "查看详情" && it.focused } }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选详情" } }
        until { var id: String? = null; detail { id = it.installedWorkId }; id == WorkDetailNativeFixture.w[0].value }
        key(KeyEvent.KEYCODE_BACK)
        until { semantics().any { it.tag == "候选首页" } }
        home { assertEquals(com.ichichuang.cinema.feature.home.visual.HomeRootScreen.HOME, it.screen) }
    }

    @Test fun detail_back_returns_library_origin_with_query_and_anchor() {
        launch(); enterLibrary()
        // 先经用户意图安装深查询（1994/JP/恐怖）：证据不全作品（未知年份）被严格过滤 → 5 部。
        library { it.applyUserQuery(WorkDetailNativeFixture.query, null) }
        until { var size = -1; library { size = it.display.items.size }; size == 5 }
        // 运行时挂钩应用查询会同步清空 focused 并异步重派发（token 重装后的首结果焦点）；
        // 等待该次派发落地（运行时权威），再开始真实 D-pad 走格，避免走格后被迟到焦点抢回。
        until { var f: String? = null; library { f = it.focused }; f != null }
        com.ichichuang.cinema.feature.library.LibraryNativeFixture.screenReach(scenario!!, WorkDetailNativeFixture.w[0].value)
        // 用片库侧等待：超时转储含 panelOpen/controlTarget/userTarget/focused 真实运行时状态。
        com.ichichuang.cinema.feature.library.LibraryNativeFixture.until(12_000) {
            var focused: String? = null; library { focused = it.focused }; focused == WorkDetailNativeFixture.w[0].value
        }
        library { assertEquals(WorkDetailNativeFixture.w[0].value, it.focused) }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选详情" } }
        until { var id: String? = null; detail { id = it.installedWorkId }; id != null }
        detail { it.activate("线路:${WorkDetailNativeFixture.VARIANT_B}") }
        until { var route: String? = null; detail { route = it.controller.selectedRouteId }; route == WorkDetailNativeFixture.VARIANT_B }
        key(KeyEvent.KEYCODE_BACK)
        until { semantics().any { it.tag == "候选片库" } }
        // 恢复管线把焦点/锚点意图交还给原作品（运行时权威；精确 Compose 落点由 R14 回归相位
        // 的 deep_list_detail_back_restores_work_and_anchor 用例覆盖）。
        until { var target: String? = null; library { target = it.userTarget ?: it.focused }; target == WorkDetailNativeFixture.w[0].value }
        library {
            assertEquals(WorkDetailNativeFixture.w[0].value, it.userTarget ?: it.focused)
            assertEquals(WorkDetailNativeFixture.query.queryId, it.appliedQuery?.queryId)
            assertNotNull(it.navigation!!.snapshot.query)
        }
    }

    @Test fun detail_back_returns_favorites_origin() {
        launch()
        navTo("我的收藏"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选浏览" } }
        until { var size = -1; scenario!!.onActivity { size = requireNotNull(it.browseProbe).items.size }; size == 1 }
        until { semantics().any { it.focused && it.tag == WorkDetailNativeFixture.w[1].value } }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选详情" } }
        until { var id: String? = null; detail { id = it.installedWorkId }; id == WorkDetailNativeFixture.w[1].value }
        key(KeyEvent.KEYCODE_BACK)
        until { semantics().any { it.tag == "候选浏览" } }
        var top = ""
        scenario!!.onActivity { top = requireNotNull(it.browseProbe).top.name }
        assertEquals("FAVORITES", top)
    }

    @Test fun detail_back_returns_history_origin() {
        launch()
        navTo("最近观看"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选浏览" } }
        // 历史含 w0 与 w2：等待网格焦点后左右实走至 w2 再打开详情。
        until { var size = -1; scenario!!.onActivity { size = requireNotNull(it.browseProbe).items.size }; size == 2 }
        until { semantics().any { it.focused && (it.tag == WorkDetailNativeFixture.w[0].value || it.tag == WorkDetailNativeFixture.w[2].value) } }
        var guard = 0
        while (guard++ < 6) {
            val current = semantics().firstOrNull { it.focused }?.tag
            if (current == WorkDetailNativeFixture.w[2].value) break
            key(KeyEvent.KEYCODE_DPAD_RIGHT)
            until(2_000) { semantics().firstOrNull { it.focused }?.tag != current }
        }
        until { semantics().any { it.tag == WorkDetailNativeFixture.w[2].value && it.focused } }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选详情" } }
        until { var id: String? = null; detail { id = it.installedWorkId }; id == WorkDetailNativeFixture.w[2].value }
        key(KeyEvent.KEYCODE_BACK)
        until { semantics().any { it.tag == "候选浏览" } }
        var top = ""
        scenario!!.onActivity { top = requireNotNull(it.browseProbe).top.name }
        assertEquals("HISTORY", top)
    }

    // N05 逐层 Back：线路单 → 详情 → 播放器 → 详情 → 片库
    @Test fun back_unwinds_exactly_one_layer() {
        launch(); enterLibrary()
        openDetailFromGrid(WorkDetailNativeFixture.w[0].value)
        // 打开线路单并确认选择（关一层，焦点回到打开者“选择线路”）。
        detail { it.activate("选择线路") }
        until { semantics().any { it.tag == "线路单" } }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().none { it.tag == "线路单" } }
        until { semantics().any { it.tag == "选择线路" && it.focused } }
        assertTrue("确认选择后仍在详情", semantics().any { it.tag == "候选详情" })
        // 左移回播放操作再 OK：进入播放器；Back 应回详情而非片库。
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        until { semantics().any { it.tag == "播放操作" && it.focused } }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选播放器" } }
        key(KeyEvent.KEYCODE_BACK)
        until { semantics().any { it.tag == "候选详情" } }
        assertTrue(semantics().none { it.tag == "候选片库" })
        // 再 Back 才回片库。
        key(KeyEvent.KEYCODE_BACK)
        until { semantics().any { it.tag == "候选片库" } }
        assertTrue(semantics().none { it.tag == "候选详情" })
    }

    // N06 一个物理 OK 周期至多一次动作；长按不先执行短按
    @Test fun held_ok_one_action_and_long_ok_safe() {
        launch(); enterLibrary()
        openDetailFromGrid(WorkDetailNativeFixture.w[0].value)
        fun favoriteLabel(): String { var label = ""; detail { label = it.display!!.favoriteText }; return label }
        // 前置：显式收藏一次（真实提交），便于验证长按只取消一次。
        detail { it.activate("收藏操作") }
        until { favoriteLabel() == "取消收藏" }
        // activate 使焦点落在收藏操作：先左移回播放操作，再实走三步到收藏操作。
        key(KeyEvent.KEYCODE_DPAD_LEFT); key(KeyEvent.KEYCODE_DPAD_LEFT); key(KeyEvent.KEYCODE_DPAD_LEFT)
        until { semantics().any { it.tag == "播放操作" && it.focused } }
        key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_RIGHT)
        until { semantics().any { it.tag == "收藏操作" && it.focused } }
        // 长按（repeatCount>0）确认：恰好一次取消收藏，不双写不回弹。
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        instrumentation.sendKeySync(KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 1))
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        instrumentation.waitForIdleSync()
        until { favoriteLabel() == "收藏" }
        var toggles = 0
        detail { toggles = WorkDetailTestHooks.dispatchTrace.count { it.startsWith("play:") } }
        assertEquals(0, toggles)
        // 下一个完整 OK 周期执行一次（收藏），不叠加。
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { favoriteLabel() == "取消收藏" }
    }

    // N07 聚焦线路被移除：焦点回退到顶替位置的同列稳定线路
    @Test fun removed_route_focus_falls_back_to_replacement_neighbor() {
        launch(); enterLibrary()
        openDetailFromGrid(WorkDetailNativeFixture.w[0].value)
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { semantics().any { it.tag == "线路:${WorkDetailNativeFixture.VARIANT_A}" && it.focused } }
        key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_RIGHT)
        until { semantics().any { it.tag == "线路:round15-variant-c" && it.focused } }
        WorkDetailNativeFixture.writeDetailFixture(context, omitRouteC = true)
        detail { it.refreshRoutes() }
        until { var busy = true; detail { busy = it.routeRefreshBusy }; !busy }
        until { semantics().any { it.tag == "线路:round15-variant-d" && it.focused } }
        detail {
            assertEquals("round15-variant-d", it.controller.focusedKey?.substringAfter("线路:"))
            assertEquals(WorkDetailNativeFixture.VARIANT_A, it.controller.selectedRouteId)
        }
        assertEquals(1, semantics().count { it.focused })
    }

    // N04 用户输入撤销迟到的焦点恢复
    @Test fun user_input_cancels_late_focus_restore() {
        launch(); enterLibrary()
        openDetailFromGrid(WorkDetailNativeFixture.w[0].value)
        WorkDetailTestHooks.disableActionFocus = true
        detail { it.activate("选择线路") }
        until { semantics().any { it.tag == "线路单" } }
        key(KeyEvent.KEYCODE_BACK)
        until { var sheet: DetailSheetKind? = null; detail { sheet = it.controller.sheet }; sheet == null }
        // 恢复请求挂起（屏幕无法应用）；用户先按右键 → 恢复必须失效，焦点跟随用户。
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        WorkDetailTestHooks.disableActionFocus = false
        until { var focus: String? = null; detail { focus = it.controller.focusedKey }; focus == "选择集数" }
        var pending: String? = "sentinel"
        detail { pending = it.controller.pendingRestoreKey }
        assertEquals(null, pending)
        until { semantics().any { it.tag == "选择集数" && it.focused } }
    }

    // E10 迟到线路刷新不覆盖新选择、不抢焦点
    @Test fun stale_route_refresh_never_overwrites_choice_or_focus() {
        launch(); enterLibrary()
        openDetailFromGrid(WorkDetailNativeFixture.w[0].value)
        key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { semantics().any { it.focused && it.tag.startsWith("集数:") } }
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { semantics().any { it.tag == "集数:${WorkDetailNativeFixture.EPISODE_A2}" && it.focused } }
        key(KeyEvent.KEYCODE_DPAD_CENTER) // 直接选择 epA2
        until { var episode: String? = null; detail { episode = it.controller.selectedEpisodeId }; episode == WorkDetailNativeFixture.EPISODE_A2 }
        // 迟到刷新：旧令牌被新令牌取代 → 旧刷新被丢弃。
        var oldToken = -1L
        detail { oldToken = it.controller.beginRefresh() }
        detail { it.controller.beginRefresh() } // 新刷新取代旧令牌
        val stale = oldToken
        detail { assertTrue(it.controller.completeRefresh(stale, it.controller.routes) == null) }
        var episode: String? = null
        detail { episode = it.controller.selectedEpisodeId }
        assertEquals(WorkDetailNativeFixture.EPISODE_A2, episode)
        assertTrue(semantics().any { it.tag == "集数:${WorkDetailNativeFixture.EPISODE_A2}" && it.focused })
    }
}
