package com.ichichuang.cinema.feature.detail

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.MainActivity
import com.ichichuang.cinema.core.navigation.DebugBrowseProbeGate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** R15 详情 UI 原生测试：身份诚实性、线路/剧集呈现与弹层（七探针配对）。 */
class WorkDetailUiNativeTest {
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
    private fun focus(tag: String) = until { semantics().any { it.tag == tag && it.focused } }
    private fun detail(action: (WorkDetailRuntime) -> Unit) { scenario!!.onActivity { action(requireNotNull(it.detailProbe)) } }
    private fun browse(action: (com.ichichuang.cinema.feature.browse.BrowseProbeRuntime) -> Unit) {
        scenario!!.onActivity { action(requireNotNull(it.browseProbe)) }
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        WorkDetailNativeFixture.scenarioRef = scenario
        until { semantics().any { it.tag == "候选首页" } }
    }

    private fun navTo(label: String) {
        until { semantics().any { it.focused } }
        repeat(6) { key(KeyEvent.KEYCODE_DPAD_UP) }
        until { semantics().any { it.focused && it.tag.startsWith("nav:") } }
        val order = com.ichichuang.cinema.feature.home.visual.VisualHomeRuntime.NAV_ITEMS
        var steps = 0
        while (steps < 8) {
            val current = semantics().firstOrNull { it.focused && it.tag.startsWith("nav:") }?.tag
            if (current == "nav:$label") break
            val currentIndex = order.indexOf(current?.removePrefix("nav:"))
            check(currentIndex >= 0) { "导航行未聚焦：$current" }
            val targetIndex = order.indexOf(label)
            key(if (targetIndex > currentIndex) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
            until(2_000) { semantics().firstOrNull { it.focused && it.tag.startsWith("nav:") }?.tag != current }
            steps++
        }
        focus("nav:$label")
    }

    private fun enterLibrary() {
        navTo("片库"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选片库" } }
        until { var size = -1; scenario!!.onActivity { size = requireNotNull(it.libraryProbe).display.items.size }; size == 6 }
    }

    private fun openDetailFromLibrary(workTag: String) {
        // 复用 R14 的确定性走格（真实 D-pad 序列，按焦点区导航）。
        com.ichichuang.cinema.feature.library.LibraryNativeFixture.screenReach(scenario!!, workTag)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选详情" } }
        until { var id: String? = null; detail { id = it.installedWorkId }; id != null }
        // 主操作被禁用（如无线路作品）时按钮不可聚焦：等待任意详情节点获得焦点即可。
        until { semantics().any { it.focused } }
    }

    // G03 授权档案经 R14 片库到达新详情层
    @Test fun authorized_profile_opens_candidate_detail_from_library() {
        launch(); enterLibrary()
        openDetailFromLibrary(WorkDetailNativeFixture.w[0].value)
        detail { runtime ->
            assertEquals(WorkDetailNativeFixture.w[0].value, runtime.installedWorkId)
            assertEquals(4, runtime.controller.routes.size)
            assertEquals(WorkDetailNativeFixture.VARIANT_A, runtime.controller.selectedRouteId)
            assertEquals(WorkDetailNativeFixture.EPISODE_A2, runtime.controller.selectedEpisodeId)
            assertTrue("主操作应为精确续播", runtime.display!!.primaryActionText.contains("继续播放"))
            assertTrue(runtime.display!!.primaryActionEnabled)
        }
        // 详情组合稳定后：四条线路 chip 与主操作都在语义树中（等待稳定态，避免瞬时竞态）
        until {
            val tags = semantics().map { it.tag }
            listOf("线路:${WorkDetailNativeFixture.VARIANT_A}", "线路:${WorkDetailNativeFixture.VARIANT_B}",
                "线路:round15-variant-c", "线路:round15-variant-d", "播放操作").all { it in tags }
        }
        assertTrue(semantics().any { it.tag == "播放操作" })
    }

    // E03 超长中文线路名不截断焦点/动作：真实 D-pad 聚焦并 OK 激活该稳定线路身份（独立评审 Minor-1 补强）。
    @Test fun long_route_name_focusable_and_selectable_by_stable_id() {
        launch(); enterLibrary()
        openDetailFromLibrary(WorkDetailNativeFixture.w[5].value)
        detail { runtime ->
            assertEquals(WorkDetailNativeFixture.VARIANT_H, runtime.controller.selectedRouteId)
        }
        // 焦点从主操作向下进入线路行：超长名线路必须真实可聚焦。
        key(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
        until { semantics().any { it.focused && it.tag == "线路:${WorkDetailNativeFixture.VARIANT_H}" } }
        val chip = semantics().first { it.tag == "线路:${WorkDetailNativeFixture.VARIANT_H}" }
        assertTrue("长名线路语义标签完整（不截断身份）", chip.description.contains("特别长的线路名称"))
        // OK 真实激活同一稳定线路身份（同名 STALE 线路 i 绝不合并误选），且线路 chip 激活不开弹层。
        key(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        until { var route: String? = null; detail { route = it.controller.selectedRouteId }; route == WorkDetailNativeFixture.VARIANT_H }
        detail { runtime ->
            assertEquals(WorkDetailNativeFixture.VARIANT_H, runtime.controller.selectedRouteId)
            assertNull("线路 chip 激活不得打开弹层", runtime.sheet)
            assertTrue(runtime.display!!.routes.first { it.routeId == WorkDetailNativeFixture.VARIANT_H }.available)
        }
    }

    // G04 缺少详情授权档案：候选界面整体拒绝（不出现候选屏、不消费档案）
    @Test fun unauthorized_profile_refuses_candidate_detail() {
        File(context.noBackupFilesDir, WorkDetailAuthorization.FILE).delete()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        WorkDetailNativeFixture.scenarioRef = scenario
        until { semantics().any { it.tag == "浏览测试不可用" } }
        scenario!!.onActivity { activity ->
            assertNull(activity.browseProbe); assertNull(activity.detailProbe); assertNull(activity.homeProbe)
        }
        assertFalse(semantics().any { it.tag == "候选详情" || it.tag == "候选首页" || it.tag == "线路单" })
    }

    // P01 同题同年两部作品：稳定身份分开
    @Test fun same_title_year_opens_distinct_stable_works() {
        launch(); enterLibrary()
        openDetailFromLibrary(WorkDetailNativeFixture.w[0].value)
        detail { assertEquals(WorkDetailNativeFixture.w[0].value, it.installedWorkId) }
        key(KeyEvent.KEYCODE_BACK)
        until { semantics().any { it.tag == "候选片库" } }
        openDetailFromLibrary(WorkDetailNativeFixture.w[1].value)
        detail {
            assertEquals(WorkDetailNativeFixture.w[1].value, it.installedWorkId)
            assertEquals(WorkDetailNativeFixture.VARIANT_E, it.controller.selectedRouteId)
        }
        assertTrue(semantics().any { it.text == "同题异身" })
    }

    /** 浏览网格（收藏/历史）内确定性走到目标作品：等待网格焦点后左右实走。 */
    private fun reachBrowseItem(workTag: String) {
        until { semantics().any { it.focused && it.focusedTagIsGridItem() } }
        var guard = 0
        while (guard++ < 8) {
            val current = semantics().firstOrNull { it.focused }?.tag
            if (current == workTag) break
            key(KeyEvent.KEYCODE_DPAD_RIGHT)
            until(2_000) { semantics().firstOrNull { it.focused }?.tag != current }
        }
        until { semantics().any { it.tag == workTag && it.focused } }
    }

    private fun WorkDetailNativeFixture.Node.focusedTagIsGridItem() = tag.isNotBlank() &&
        WorkDetailNativeFixture.w.any { it.value == tag }

    // P03/P04 未知年份与冲突语言如实呈现（自历史来源打开证据不全作品）
    @Test fun unknown_year_and_conflict_language_truthful() {
        launch()
        navTo("最近观看"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选浏览" } }
        // 历史含 w0（续播种子的确认播放）与 w2（显式历史种子）：确定性走到 w2。
        until { var size = -1; browse { size = it.items.size }; size == 2 }
        reachBrowseItem(WorkDetailNativeFixture.w[2].value)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选详情" } }
        until { var id: String? = null; detail { id = it.installedWorkId }; id == WorkDetailNativeFixture.w[2].value }
        until { semantics().any { it.tag == "作品事实" } }
        val facts = semantics().first { it.tag == "作品事实" }.text
        assertTrue("应含未知年份：$facts", facts.contains("年份未知"))
        assertTrue("应含语言冲突：$facts", facts.contains("语言证据冲突") || facts.contains("证据冲突"))
        assertTrue(semantics().none { it.text.contains("评分") || it.text.contains("豆瓣") })
    }

    // P06/E01 同作品全部线路独立可寻址（同名线路不合并），选择按稳定身份
    @Test fun same_label_distinct_routes_selectable_by_stable_id() {
        launch(); enterLibrary()
        openDetailFromLibrary(WorkDetailNativeFixture.w[0].value)
        var sameLabel = 0
        detail { sameLabel = it.display!!.routes.count { route -> route.label == "同名线路" } }
        assertEquals(2, sameLabel)
        // 直接 OK 在第二条同名线路：选择切到 VARIANT_B，焦点不变语义可辨。
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        focus("线路:${WorkDetailNativeFixture.VARIANT_A}")
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        focus("线路:${WorkDetailNativeFixture.VARIANT_B}")
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var route: String? = null; detail { route = it.controller.selectedRouteId }; route == WorkDetailNativeFixture.VARIANT_B }
        until { semantics().any { it.tag == "集数:${WorkDetailNativeFixture.EPISODE_B1}" } }
    }

    // E02/P08 停用与离线线路可见、如实说明、不可作为可播
    @Test fun disabled_and_unreachable_routes_truthful() {
        launch(); enterLibrary()
        openDetailFromLibrary(WorkDetailNativeFixture.w[0].value)
        var cText = ""; var dText = ""
        detail {
            cText = it.display!!.routes.first { route -> route.routeId == "round15-variant-c" }.stateText
            dText = it.display!!.routes.first { route -> route.routeId == "round15-variant-d" }.stateText
        }
        assertTrue(cText.contains("已被来源停用"))
        assertTrue(dText.contains("不可达"))
        assertTrue(semantics().any { it.tag == "线路:round15-variant-c" })
        assertTrue(semantics().any { it.tag == "线路:round15-variant-d" })
        // 选择停用线路：播放禁用并说明原因，不自动回退其他线路。
        detail { it.activate("线路:round15-variant-c") }
        until { var route: String? = null; detail { route = it.controller.selectedRouteId }; route == "round15-variant-c" }
        until { semantics().first { it.tag == "播放操作" }.description.contains("Disabled") }
        val play = semantics().first { it.tag == "播放操作" }
        assertTrue("停用线路下播放应禁用", play.description.contains("Disabled"))
        until { semantics().first { it.tag == "播放说明" }.text.contains("停用") }
        var dispatched = false
        detail { dispatched = WorkDetailTestHooks.dispatchTrace.any { it.startsWith("play:") } }
        assertFalse(dispatched)
    }

    // E05 未知集数不捏造序号；无线路作品不出“第 1 集”
    @Test fun unknown_episode_and_empty_routes_no_fabrication() {
        launch(); enterLibrary()
        openDetailFromLibrary(WorkDetailNativeFixture.w[0].value)
        detail { it.activate("线路:round15-variant-d") }
        until { semantics().any { it.tag == "集数:round15-w0-d-1" } }
        detail {
            val episode = it.display!!.episodes.single()
            assertEquals("未提供集数名", episode.label)
            assertEquals(null, episode.orderText)
        }
        key(KeyEvent.KEYCODE_BACK)
        until { semantics().any { it.tag == "候选片库" } }
        openDetailFromLibrary(WorkDetailNativeFixture.w[3].value)
        until { var id: String? = null; detail { id = it.installedWorkId }; id == WorkDetailNativeFixture.w[3].value }
        detail {
            assertTrue(it.display!!.episodes.isEmpty())
            assertFalse(it.display!!.primaryActionEnabled)
            assertTrue(it.display!!.availabilityText.contains("线路"))
            assertTrue(it.display!!.primaryActionExplanation!!.contains("线路"))
        }
        assertTrue(semantics().none { it.tag.startsWith("集数:") })
        assertTrue(semantics().first { it.tag == "线路总状态" }.text.contains("线路"))
        val play = semantics().first { it.tag == "播放操作" }
        assertTrue(play.description.contains("Disabled"))
        assertTrue(semantics().first { it.tag == "播放说明" }.text.contains("线路"))
    }

    // E06/N03 弹层 D-pad 陷阱与打开者恢复
    @Test fun episode_picker_dpad_and_back_trap() {
        launch(); enterLibrary()
        openDetailFromLibrary(WorkDetailNativeFixture.w[0].value)
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        focus("选择线路")
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "线路单" } }
        var guard = 0
        while (guard++ < 6) {
            val focused = semantics().filter { it.focused }
            assertEquals("弹层内唯一焦点", 1, focused.size)
            assertTrue("焦点必须在线路单内：${focused.first().tag}", focused.first().tag.startsWith("线路单:"))
            key(KeyEvent.KEYCODE_DPAD_DOWN)
        }
        // 左右键被陷阱吸收：焦点仍在线路单内。
        key(KeyEvent.KEYCODE_DPAD_LEFT); key(KeyEvent.KEYCODE_DPAD_RIGHT)
        until { semantics().firstOrNull { it.focused }?.tag?.startsWith("线路单:") == true }
        key(KeyEvent.KEYCODE_BACK)
        until { semantics().none { it.tag == "线路单" } }
        focus("选择线路")
    }

    // 焦点与选择分立：聚焦第二条线路不改变选择
    @Test fun focus_does_not_change_selection() {
        launch(); enterLibrary()
        openDetailFromLibrary(WorkDetailNativeFixture.w[0].value)
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        focus("线路:${WorkDetailNativeFixture.VARIANT_A}")
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        focus("线路:${WorkDetailNativeFixture.VARIANT_B}")
        detail {
            assertEquals(WorkDetailNativeFixture.VARIANT_B, it.controller.focusedKey?.substringAfter("线路:"))
            assertEquals(WorkDetailNativeFixture.VARIANT_A, it.controller.selectedRouteId)
        }
        assertEquals(1, semantics().count { it.focused })
    }

    // A03/G06 浏览详情（含聚焦/换选/弹层取消）不写任何用户记录
    @Test fun detail_browse_does_not_write_personal_records() {
        launch(); enterLibrary()
        fun counts(): List<Int> {
            var database: com.ichichuang.cinema.data.local.CinemaDatabase? = null
            scenario!!.onActivity { database = requireNotNull(it.browseProbe).personal!!.database }
            return runBlocking {
                val db = database!!
                listOf(db.userRecords().favoriteCount(), db.personalLibrary().history().size,
                    db.playbackSnapshots().count(), db.personalLibrary().resumes().size)
            }
        }
        val before = counts()
        openDetailFromLibrary(WorkDetailNativeFixture.w[0].value)
        key(KeyEvent.KEYCODE_DPAD_DOWN); focus("线路:${WorkDetailNativeFixture.VARIANT_A}")
        key(KeyEvent.KEYCODE_DPAD_RIGHT); focus("线路:${WorkDetailNativeFixture.VARIANT_B}")
        detail { it.activate("线路:${WorkDetailNativeFixture.VARIANT_B}") } // 换选仅提交 UI 上下文
        until { var route: String? = null; detail { route = it.controller.selectedRouteId }; route == WorkDetailNativeFixture.VARIANT_B }
        // 自线路行上移回动作行（同列索引）：选择线路 → 选择集数。
        key(KeyEvent.KEYCODE_DPAD_UP); focus("选择线路")
        key(KeyEvent.KEYCODE_DPAD_RIGHT); focus("选择集数")
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "集数单" } }
        key(KeyEvent.KEYCODE_BACK)
        until { semantics().none { it.tag == "集数单" } }
        focus("选择集数")
        assertEquals(before, counts())
        var dispatched = false
        detail { dispatched = WorkDetailTestHooks.dispatchTrace.any { it.startsWith("play:") } }
        assertFalse("浏览与换线绝不派发播放", dispatched)
    }

    // P09 看过最新集不宣称全剧完结/总数（全局文本诚实性）
    @Test fun watched_latest_episode_not_series_finality() {
        launch(); enterLibrary()
        openDetailFromLibrary(WorkDetailNativeFixture.w[0].value)
        val texts = semantics().map { it.text }.filter { it.isNotBlank() } +
            run { var all = emptyList<String>(); detail { all = it.display!!.allTexts() }; all }
        assertTrue(texts.none { it.contains("看完") || it.contains("完结") || it.contains("评分") })
        assertTrue(texts.any { it.contains("本线路 2 集") })
    }

    // 集数弹层按当前所选线路呈现，且恢复到精确打开者
    @Test fun episode_sheet_lists_selected_route_and_restores_opener() {
        launch(); enterLibrary()
        openDetailFromLibrary(WorkDetailNativeFixture.w[0].value)
        detail { it.activate("线路:${WorkDetailNativeFixture.VARIANT_B}") }
        until { var route: String? = null; detail { route = it.controller.selectedRouteId }; route == WorkDetailNativeFixture.VARIANT_B }
        // activate 后焦点在线路行：上移回动作行（同列索引=选择线路），再右移到选择集数。
        key(KeyEvent.KEYCODE_DPAD_UP); focus("选择线路")
        key(KeyEvent.KEYCODE_DPAD_RIGHT); focus("选择集数")
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "集数单" } }
        assertEquals(listOf("集数单:${WorkDetailNativeFixture.EPISODE_B1}"),
            semantics().filter { it.tag.startsWith("集数单:") }.map { it.tag })
        key(KeyEvent.KEYCODE_BACK)
        until { semantics().none { it.tag == "集数单" } }
        focus("选择集数")
    }

    // L06 语义中文、无品牌/诊断信息
    @Test fun ui_copy_is_chinese_without_brand_or_diagnostics() {
        launch(); enterLibrary()
        openDetailFromLibrary(WorkDetailNativeFixture.w[0].value)
        val texts = semantics().map { it.text }.filter { it.isNotBlank() }
        assertTrue(texts.isNotEmpty())
        assertTrue(texts.none { it.contains("cinema-tv") || it.contains("Debug") || it.contains("probe")
            || it.contains("round15") || it.contains("app") || it.contains("软件") })
    }
}
