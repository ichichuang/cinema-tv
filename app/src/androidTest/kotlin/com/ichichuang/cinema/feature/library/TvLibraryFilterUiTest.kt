package com.ichichuang.cinema.feature.library

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.MainActivity
import com.ichichuang.cinema.domain.model.catalog.CatalogQuery
import com.ichichuang.cinema.domain.model.catalog.LanguageScope
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** R14 片库筛选面板的真实原生 UI 测试；仅运行于六授权全开的 Debug/DebugAndroidTest 配对。 */
class TvLibraryFilterUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun ownedPrivateFixture() {
        LibraryNativeFixture.setup(context)
        LibraryNativeFixture.scenarioRef = scenario
    }

    @After fun restore() {
        scenario?.close(); scenario = null
        LibraryNativeFixture.teardown(context)
    }

    private fun semantics() = LibraryNativeFixture.semantics(scenario!!)
    private fun until(timeout: Long = 12_000, condition: () -> Boolean) = LibraryNativeFixture.until(timeout, condition)
    private fun focus(tag: String) = until { semantics().any { it.tag == tag && it.focused } }
    private fun key(code: Int) {
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, code))
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, code))
        instrumentation.waitForIdleSync()
    }
    private fun library(action: (LibraryRuntime) -> Unit) { scenario!!.onActivity { action(requireNotNull(it.libraryProbe)) } }
    private fun assertUniqueFocus() = assertEquals(1, semantics().count { it.focused })

    private fun launchAndEnterLibrary() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        LibraryNativeFixture.scenarioRef = scenario
        until { semantics().any { it.tag == "候选首页" } }
        navTo("片库"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选片库" } }
        until { semantics().any { it.focused } }
        // 默认查询装载完成后：105 部去重作品（镜像来源去重）+ 失败来源的如实部分覆盖。
        until { var size = -1; library { size = it.display.items.size }; size == 105 }
        library { assertEquals(4, it.appliedQuery!!.enabledSourceIds.size) }
        // 新上下文无保存焦点：R11 回退策略聚焦首个作品；测试从这里走格到筛选入口。
        LibraryNativeFixture.screenReach(scenario!!, "筛选")
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

    private fun openPanel() {
        LibraryNativeFixture.screenReach(scenario!!, "筛选"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "筛选面板" } }
        until { semantics().any { it.focused && it.tag.startsWith("year-mode-") } }
    }

    /** 面板走格：以运行时分区为同一来源做确定性 UP/DOWN/LEFT/RIGHT 到达。 */
    private fun panelReach(target: String) {
        var guard = 0
        while (guard++ < 40) {
            val actual = semantics().firstOrNull { it.focused }?.tag
            if (actual == target) break
            var zones: List<List<String>> = emptyList()
            library { zones = it.panelZones().map { it.second } }
            val currentZone = zones.indexOfFirst { actual in it }
            val targetZone = zones.indexOfFirst { target in it }
            check(targetZone >= 0) { "面板分区中没有目标 $target" }
            val code = when {
                currentZone < 0 -> KeyEvent.KEYCODE_DPAD_DOWN
                currentZone == targetZone ->
                    if (zones[targetZone].indexOf(target) > zones[currentZone].indexOf(actual))
                        KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                targetZone > currentZone -> KeyEvent.KEYCODE_DPAD_DOWN
                else -> KeyEvent.KEYCODE_DPAD_UP
            }
            key(code)
            until(2_000) { semantics().firstOrNull { it.focused }?.tag != actual }
        }
        focus(target)
    }

    private fun select(keyTag: String) { panelReach(keyTag); key(KeyEvent.KEYCODE_DPAD_CENTER) }

    private fun applyPanel() { select("panel-apply") }

    @Test fun authorized_library_opens_with_filter_entry_and_honest_partial_status() {
        launchAndEnterLibrary()
        library {
            assertEquals(LibraryContentStatus.PARTIAL, it.display.status)
            assertTrue(it.display.coverage.failedSourceIds.contains("broken-source"))
            assertEquals(105, it.display.coverage.verifiedLocalCount)
            assertTrue(it.display.statusText.contains("1 个失败"))
            assertTrue(it.display.canContinue)
        }
        assertTrue(semantics().any { it.tag == "chip:source:broken-source" })
        assertTrue(semantics().any { it.tag == "chip:source:mirror-source" })
        assertUniqueFocus()
    }

    @Test fun dpad_reaches_all_panel_choices_including_pre2000_years() {
        launchAndEnterLibrary()
        openPanel()
        // 年代 1980–1989：方式切“年代”，步进一次到 1980（2000 年之前，全程无键盘）。
        select("year-mode-DECADE")
        select("year-decade-minus")
        until { semantics().any { it.tag == "年份摘要" && it.text.contains("1980–1989") } }
        // 各分区均可由 D-pad 到达：语言、内容类别、国家、类型、排序、来源、操作。
        listOf("lang-CHINESE", "kind-MOVIE", "country-JP", "genre-horror",
            "sort-YEAR_ASCENDING", "source-mirror-source", "panel-cancel").forEach { focusTarget ->
            panelReach(focusTarget)
        }
        applyPanel()
        until { semantics().none { it.tag == "筛选面板" } }
        until { var size = -1; library { size = it.display.items.size }; size == LibraryNativeFixture.decadeIndices.count() }
        LibraryNativeFixture.screenReach(scenario!!, "筛选")
    }

    @Test fun exact_1994_excludes_known_1995() {
        launchAndEnterLibrary()
        openPanel()
        select("year-mode-EXACT")
        until { semantics().any { it.tag == "年份摘要" && it.text.contains("具体年份：1994") } }
        applyPanel()
        until { var size = -1; library { size = it.display.items.size }; size == 89 }
        // 已知 1995（id 100）不得混入；1987 年代作品也不属于 1994。
        library {
            assertFalse(it.display.items.any { card -> card.workId == LibraryNativeFixture.ids[100].value })
            assertFalse(it.display.items.any { card -> card.workId == LibraryNativeFixture.ids[30].value })
        }
        val yearChip = semantics().first { it.tag == "chip:year:1994-1994" }
        assertTrue("年份片应带中文可读描述：${yearChip.description}", yearChip.description.contains("1994 年"))
    }

    @Test fun inverted_range_rejected_in_chinese_and_applied_query_untouched() {
        launchAndEnterLibrary()
        openPanel()
        select("year-mode-RANGE")
        // 起点 1980 → 2006（26 次 +1），晚于终点 1999：草稿无效。
        panelReach("year-start-plus")
        repeat(26) { key(KeyEvent.KEYCODE_DPAD_CENTER) }
        until { semantics().any { it.tag == "年份摘要" && it.text.contains("2006–1999") } }
        assertTrue(semantics().any { it.tag == "年份错误" && it.text.contains("起始年份") })
        applyPanel()
        // 面板保留、错误仍可见、已应用查询逐字不变（不放宽、不执行）。
        until { semantics().any { it.tag == "筛选面板" } }
        assertTrue(semantics().any { it.tag == "年份错误" })
        library {
            assertNull(it.appliedQuery!!.yearStart)
            assertEquals(4, it.appliedQuery!!.enabledSourceIds.size)
        }
        // 取消后面板关闭，原查询仍在。
        select("panel-cancel")
        until { semantics().none { it.tag == "筛选面板" } }
        library { assertNull(it.appliedQuery!!.yearStart) }
    }

    @Test fun cancel_after_editing_year_and_language_keeps_query_and_opener() {
        launchAndEnterLibrary()
        openPanel()
        select("year-mode-EXACT")
        select("lang-CHINESE")
        select("panel-cancel")
        until { semantics().none { it.tag == "筛选面板" } }
        // 取消：草稿整体丢弃，已应用查询不变，焦点回到打开者（筛选入口）。
        library {
            assertNull(it.appliedQuery!!.yearStart)
            assertEquals(LanguageScope.ALL, it.appliedQuery!!.languageScope)
            assertEquals(105, it.display.items.size)
        }
        focus("筛选")
        assertUniqueFocus()
    }

    @Test fun same_apply_is_no_op_keeps_query_id_and_items() {
        launchAndEnterLibrary()
        val before = libraryQuery()
        openPanel()
        applyPanel() // 未做任何修改的确认
        until { semantics().none { it.tag == "筛选面板" } }
        library {
            assertEquals(before, it.appliedQuery)
            assertEquals(before!!.queryId, it.appliedQuery!!.queryId)
            assertEquals(0, LibraryTestHooks.appliedQueries.get())
        }
        focus("筛选")
    }

    @Test fun remove_chip_creates_new_query_and_preserves_other_fields() {
        launchAndEnterLibrary()
        openPanel()
        select("year-mode-EXACT")
        select("lang-NON_CHINESE")
        select("country-JP")
        applyPanel()
        // 1994 + 非华语 + 日本：排除 1995/1987 作品与美国作品（id 101）→ 88 项。
        until { var size = -1; library { size = it.display.items.size }; size == 88 }
        library {
            assertEquals(LanguageScope.NON_CHINESE, it.appliedQuery!!.languageScope)
            assertEquals(setOf("JP"), it.appliedQuery!!.countryIds)
        }
        // 删除国家片：新查询身份，语言范围与年份逐字保留，美国 1994 作品回到结果。
        LibraryNativeFixture.screenReach(scenario!!, "chip:country:JP"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var empty = false; library { empty = it.appliedQuery?.countryIds?.isEmpty() == true }; empty }
        until { var size = -1; library { size = it.display.items.size }; size == 89 }
        library {
            assertEquals(1994, it.appliedQuery!!.yearStart)
            assertEquals(LanguageScope.NON_CHINESE, it.appliedQuery!!.languageScope)
        }
    }

    @Test fun panel_traps_focus_and_back_closes_only_panel() {
        launchAndEnterLibrary()
        openPanel()
        // 面板打开：背景（筛选入口/条件片/网格）不可聚焦，唯一焦点在面板内。
        assertTrue(semantics().any { it.focused && it.tag.startsWith("year-mode-") })
        assertFalse(semantics().any { it.focused && it.tag == "筛选" })
        key(KeyEvent.KEYCODE_BACK)
        until { semantics().none { it.tag == "筛选面板" } }
        // 返回只关面板：仍处片库根画面，焦点回到打开者。
        assertTrue(semantics().any { it.tag == "候选片库" })
        focus("筛选")
        assertUniqueFocus()
    }

    @Test fun one_ok_cycle_one_apply_and_long_ok_no_short_action() {
        launchAndEnterLibrary()
        openPanel()
        select("year-mode-EXACT")
        // 长按（repeatCount>0）确认不先执行/不重复执行任何动作。
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        instrumentation.sendKeySync(KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 1))
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        instrumentation.waitForIdleSync()
        applyPanel()
        until { semantics().none { it.tag == "筛选面板" } }
        assertEquals(1, LibraryTestHooks.appliedQueries.get())
        // 确认键不会叠加第二个查询代次。
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, LibraryTestHooks.appliedQueries.get())
    }

    @Test fun language_scope_honors_work_language_not_dub_or_subtitle() {
        launchAndEnterLibrary()
        // 合成行的配音=国语配音、字幕=中文字幕，但作品语言是日语且完整：
        // “华语”不因配音/字幕命中任何作品；如实零匹配且不宣告最终空（失败来源仍在核对中）。
        openPanel()
        select("lang-CHINESE")
        applyPanel()
        until { var size = -1; library { size = it.display.items.size }; size == 0 }
        library {
            assertEquals(LibraryContentStatus.PARTIAL, it.display.status)
            assertTrue(it.display.statusText.contains("不代表全部片库"))
            assertFalse(it.display.statusText.contains("暂无匹配影片"))
            assertTrue(it.display.canContinue)
        }
        // “非华语”以作品语言证据命中（ja + 完整作用域）。
        openPanel()
        select("lang-NON_CHINESE")
        applyPanel()
        until { var size = -1; library { size = it.display.items.size }; size == 105 }
    }

    @Test fun chinese_labels_no_brand_and_no_fake_counts() {
        launchAndEnterLibrary()
        val texts = semantics().map { it.text }.filter { it.isNotBlank() }
        assertTrue(texts.any { it.contains("片库") })
        assertTrue(texts.any { it.contains("筛选") })
        // 部分覆盖不出现全量“共 N 部”；无品牌/水印/评分/调试字样。
        assertFalse(texts.any { it.contains("共 ") && it.contains(" 部") })
        assertFalse(texts.any { it.contains("评分") || it.contains("DEBUG") || it.contains("cinema-tv") })
        openPanel()
        val panelTexts = semantics().map { it.text }.filter { it.isNotBlank() }
        assertTrue(panelTexts.any { it.contains("年份方式") })
        assertTrue(panelTexts.any { it.contains("语言范围") })
        assertTrue(panelTexts.any { it.contains("参与来源") })
    }

    private fun libraryQuery(): CatalogQuery? {
        var query: CatalogQuery? = null
        library { query = it.appliedQuery }
        return query
    }
}
