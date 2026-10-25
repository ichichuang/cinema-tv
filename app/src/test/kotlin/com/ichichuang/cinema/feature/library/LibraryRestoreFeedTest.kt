package com.ichichuang.cinema.feature.library

import com.ichichuang.cinema.core.navigation.BrowseRestoreCoordinator
import com.ichichuang.cinema.core.navigation.RestoreLayoutToken
import com.ichichuang.cinema.core.navigation.RestoreObservation
import com.ichichuang.cinema.core.navigation.RestoreOutcome
import com.ichichuang.cinema.core.navigation.RestoreToken
import com.ichichuang.cinema.domain.model.catalog.CatalogQuery
import com.ichichuang.cinema.domain.model.catalog.CatalogSort
import com.ichichuang.cinema.domain.model.catalog.LanguageScope
import com.ichichuang.cinema.domain.model.catalog.SourceId
import com.ichichuang.cinema.domain.model.catalog.WorkId
import com.ichichuang.cinema.domain.model.userdata.BrowseSnapshot
import org.junit.Assert.*
import org.junit.Test

/**
 * N09 中间档的协调器实证：片库 observation() 以全量已知作品序（含已从结果消失的锚点）
 * 喂给既有 R11 恢复协调器时，锚点缺失回退到原行最近存活邻居，而非页面默认或无关跳转。
 * （查询变更本身是新上下文、清锚点属 N05 语义，由 apply_new_query 原生用例覆盖。）
 */
class LibraryRestoreFeedTest {
    private val query = CatalogQuery.create("片库查询-日本", null, LanguageScope.ALL, setOf("JP"),
        emptySet(), null, null, CatalogSort.DEFAULT, setOf(SourceId("test-source")))
    private val allIds = (0 until 105).map { WorkId("w$it") }

    /** 与 LibraryRuntime.observation 同构：保存序=全量已知作品，行=存活项∩原行。 */
    /** complete=true 才证明“已查完且不存在”：部分覆盖下协调器对缺失锚点正确地等待。 */
    private fun observation(saved: BrowseSnapshot, live: List<WorkId>, ready: Set<WorkId>,
        pass: RestoreLayoutToken, complete: Boolean): RestoreObservation {
        val columns = 5
        val savedIndex = allIds.indexOf(saved.focusedWorkId ?: WorkId("w0")).coerceAtLeast(0)
        val row = allIds.drop((savedIndex / columns) * columns).take(columns)
        return RestoreObservation(saved.query, 3,
            mapOf("原行" to live.filter { it in row }, "其余" to live.filter { it !in row }),
            complete, ready, true, "原行", row, pass)
    }

    @Test fun missing_anchor_falls_back_to_nearest_live_neighbor_in_saved_row() {
        val coordinator = BrowseRestoreCoordinator()
        // 已保存上下文：焦点 = w101（US 作品，不在 JP 结果中），行 100..104。
        val saved = BrowseSnapshot("library-ui:片库查询-日本", "library", null, query, 3,
            WorkId("w101"), WorkId("w100"), 0f, 1)
        val token: RestoreToken = coordinator.begin(saved)
        val revision = 3L
        val pass: RestoreLayoutToken = coordinator.advanceLayout(token, revision)!!
        val live = allIds.filter { it.value != "w101" }
        val ready = live.filter { it.value in setOf("w100", "w102", "w103", "w104") }.toSet()
        // 部分覆盖：锚点可能仍在未查询页中，协调器必须等待而非回退。
        val waiting = coordinator.observe(token, observation(saved, live, ready, pass, complete = false))
        assertTrue("部分覆盖下缺失锚点应等待未查页，实际 $waiting", waiting is RestoreOutcome.WaitingForItem)
        // 覆盖完成：锚点已证不存在，回退原行最近存活邻居（等距取先者 w100）。
        val outcome = coordinator.observe(token, observation(saved, live, ready, pass, complete = true))
        assertTrue("锚点缺失应回退邻居，实际 $outcome", outcome is RestoreOutcome.Fallback)
        val fallback = outcome as RestoreOutcome.Fallback
        assertEquals("应落在原行最近存活邻居（等距取先者）", WorkId("w100"), fallback.workId)
    }

    @Test fun missing_anchor_with_empty_saved_row_falls_back_to_section_default() {
        val coordinator = BrowseRestoreCoordinator()
        // 行内全部消失（仅存远处一项）：回退页面默认（null→控件），不跳无关项。
        val saved = BrowseSnapshot("library-ui:片库查询-日本", "library", null, query, 3,
            WorkId("w101"), WorkId("w100"), 0f, 1)
        val token = coordinator.begin(saved)
        val pass = coordinator.advanceLayout(token, 3L)!!
        val live = listOf(WorkId("w7"))
        val outcome = coordinator.observe(token, observation(saved, live, setOf(WorkId("w7")), pass, complete = true))
        assertTrue(outcome is RestoreOutcome.Fallback)
        assertNull("原行无存活项时回退页面默认（控件层接管），不跳无关项",
            (outcome as RestoreOutcome.Fallback).workId)
    }

    @Test fun live_anchor_restores_directly_without_fallback() {
        val coordinator = BrowseRestoreCoordinator()
        val saved = BrowseSnapshot("library-ui:片库查询-日本", "library", null, query, 3,
            WorkId("w87"), WorkId("w85"), 0f, 1)
        val token = coordinator.begin(saved)
        val pass = coordinator.advanceLayout(token, 3L)!!
        val live = allIds
        val outcome = coordinator.observe(token, observation(saved, live, setOf(WorkId("w87"), WorkId("w85")), pass, complete = true))
        assertTrue(outcome is RestoreOutcome.ReadyToFocus)
        assertEquals(WorkId("w87"), (outcome as RestoreOutcome.ReadyToFocus).workId)
    }
}
