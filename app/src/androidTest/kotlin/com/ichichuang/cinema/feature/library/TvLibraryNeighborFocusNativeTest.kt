package com.ichichuang.cinema.feature.library

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.MainActivity
import com.ichichuang.cinema.core.navigation.BrowseRestoreCoordinator
import com.ichichuang.cinema.core.navigation.RestoreOutcome
import com.ichichuang.cinema.domain.model.catalog.CatalogQuery
import com.ichichuang.cinema.domain.model.catalog.LanguageScope
import com.ichichuang.cinema.domain.model.catalog.CatalogSort
import com.ichichuang.cinema.domain.model.catalog.SourceId
import com.ichichuang.cinema.domain.model.catalog.WorkId
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * R15 新增（N10）：补强 R14 N09 的直接原生断言。真实移除深列表聚焦作品：
 * 走格聚焦第 87 号（真实 D-pad + 真实焦点），经用户意图应用恐怖查询将其真实移除
 * （103/105 在场、87 缺席），然后把真实运行时的 observation（真实条目/全量保存序）
 * 喂给真实协调器，断言回退判定精确落到“原行最近存活邻居”86 号这一特定稳定身份，
 * 而非 R14 的“存在某个合法焦点”弱断言；并保留屏幕层合法焦点核验。
 */
class TvLibraryNeighborFocusNativeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null

    /** 恐怖类型查询（其余维度不限；不含失败来源——回退判定要求完整覆盖）。 */
    private val horrorOnly = CatalogQuery.create("测试查询-恐怖核对", contentKind = null,
        languageScope = LanguageScope.ALL, countryIds = emptySet(), genreIds = setOf("horror"),
        yearStart = null, yearEnd = null, sort = CatalogSort.DEFAULT,
        enabledSourceIds = setOf(SourceId("test-source"), SourceId("mirror-source"),
            SourceId("disabled-source")))

    @Before fun ownedPrivateFixture() {
        LibraryNativeFixture.setup(context)
        File(context.noBackupFilesDir, com.ichichuang.cinema.feature.detail.WorkDetailAuthorization.FILE)
            .writeText(com.ichichuang.cinema.feature.detail.WorkDetailAuthorization.TOKEN)
        // R14 默认 105 作品，唯一差异：索引 87 改为 drama（可被类型查询真实移除）。
        val works = JSONArray()
        LibraryNativeFixture.ids.forEachIndexed { i, id ->
            works.put(JSONObject().put("id", id.value)
                .put("title", "合成作品${i + 1}")
                .put("source", if (i == 104) "disabled-source" else "test-source")
                .put("year", LibraryNativeFixture.workYear(i))
                .put("countries", JSONArray(listOf(if (i == 101) "US" else "JP")))
                .put("genres", JSONArray(listOf(if (i == 102 || i == 87) "drama" else "horror")))
                .put("kind", if (i == 103) "OTHER" else "MOVIE")
                .put("languages", JSONArray(listOf("ja")))
                .put("favorite", false).put("history", false)
                .put("artwork", JSONObject.NULL).put("artworkShape", JSONObject.NULL))
        }
        LibraryNativeFixture.writeFixture(context, works)
    }

    @After fun restore() {
        scenario?.close(); scenario = null
        LibraryNativeFixture.teardown(context)
        File(context.noBackupFilesDir, com.ichichuang.cinema.feature.detail.WorkDetailAuthorization.FILE).delete()
    }

    private fun semantics() = LibraryNativeFixture.semantics(scenario!!)
    private fun until(timeout: Long = 12_000, condition: () -> Boolean) = LibraryNativeFixture.until(timeout, condition)
    private fun key(code: Int) {
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, code))
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, code))
        instrumentation.waitForIdleSync()
    }
    private fun library(action: (LibraryRuntime) -> Unit) { scenario!!.onActivity { action(requireNotNull(it.libraryProbe)) } }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        LibraryNativeFixture.scenarioRef = scenario
        until { semantics().any { it.tag == "候选首页" } }
        repeat(6) { key(KeyEvent.KEYCODE_DPAD_UP) }
        until { semantics().any { it.focused && it.tag.startsWith("nav:") } }
        var steps = 0
        while (steps++ < 8) {
            val current = semantics().firstOrNull { it.focused && it.tag.startsWith("nav:") }?.tag
            if (current == "nav:片库") break
            key(KeyEvent.KEYCODE_DPAD_RIGHT)
            until(2_000) { semantics().firstOrNull { it.focused && it.tag.startsWith("nav:") }?.tag != current }
        }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选片库" } }
        until { var size = -1; library { size = it.display.items.size }; size == 105 }
    }

    @Test fun removed_deep_anchor_focus_lands_on_specific_adjacent_stable_work() {
        launch()
        // 深列表第 87 号（网格第 18 行）：确定性走格到位并等待真实焦点。
        LibraryNativeFixture.screenReach(scenario!!, LibraryNativeFixture.ids[87].value)
        library { assertEquals(LibraryNativeFixture.ids[87].value, it.focused) }
        // 捕获真实用户状态（聚焦 87 的已保存上下文），经用户意图应用恐怖查询真实移除 87。
        var savedSnapshot: com.ichichuang.cinema.domain.model.userdata.BrowseSnapshot? = null
        library { savedSnapshot = requireNotNull(it.navigation).snapshot }
        assertEquals(LibraryNativeFixture.ids[87].value, savedSnapshot!!.focusedWorkId?.value)
        library { it.applyUserQuery(horrorOnly, null) }
        until { var size = -1; library { size = it.display.items.size }; size == 103 }
        assertTrue(semantics().none { it.tag == LibraryNativeFixture.ids[87].value })
        // 真实组件直接断言：以保存查询上下文 + 真实移除后的条目集合/全量保存序构造
        // observation，交给真实协调器——回退判定必须精确落到原行（85..89）存活邻居
        // 85、86、88、89 中与 87 最近者 86（协调器按上下文拒绝跨查询，故用保存查询）。
        var outcome: RestoreOutcome? = null
        var positioningWorkId: WorkId? = null
        library { runtime ->
            val coordinator = BrowseRestoreCoordinator()
            val token = coordinator.begin(savedSnapshot!!)
            val revision = maxOf(savedSnapshot!!.resultRevision ?: 0L, runtime.currentResultRevision)
            val pass = coordinator.advanceLayout(token, revision)
            assertNotNull(pass)
            val items = runtime.display.items.map { WorkId(it.workId) }
            assertEquals(103, items.size)
            val original = LibraryNativeFixture.ids.map { it.value }
            val savedIndex = original.indexOf(LibraryNativeFixture.ids[87].value)
            val row = original.drop((savedIndex / 5) * 5).take(5).map(::WorkId)
            val observation = com.ichichuang.cinema.core.navigation.RestoreObservation(
                savedSnapshot!!.query, revision,
                mapOf("原行" to items.filter { it in row }, "其余" to items.filter { it !in row }),
                true, items.toSet(), true, "原行", row, pass!!)
            positioningWorkId = coordinator.positioning(token, observation)?.workId
            outcome = coordinator.observe(token, observation)
        }
        assertEquals(RestoreOutcome.Fallback(LibraryNativeFixture.ids[86]), outcome)
        assertEquals(LibraryNativeFixture.ids[86], positioningWorkId)
        // 屏幕层：新查询下存在合法焦点（不得死焦点），且不落在已移除作品上。
        until { semantics().any { it.focused } }
        assertTrue(semantics().none { it.focused && it.tag == LibraryNativeFixture.ids[87].value })
        assertNotEquals("不得默认跳到无关首项之外再断言（弱断言仅作补充）",
            LibraryNativeFixture.ids[87].value, semantics().firstOrNull { it.focused }?.tag)
    }
}
