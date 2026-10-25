package com.ichichuang.cinema.feature.library

import com.ichichuang.cinema.domain.model.catalog.CatalogItem
import com.ichichuang.cinema.domain.model.catalog.CatalogQuery
import com.ichichuang.cinema.domain.model.catalog.CatalogResult
import com.ichichuang.cinema.domain.model.catalog.EvidenceReference
import com.ichichuang.cinema.domain.model.catalog.FieldEvidence
import com.ichichuang.cinema.domain.model.catalog.QueryCoverage
import com.ichichuang.cinema.domain.model.catalog.SourceId
import com.ichichuang.cinema.domain.model.catalog.WorkId
import com.ichichuang.cinema.feature.home.visual.HomeImageShape
import org.junit.Assert.*
import org.junit.Test

/** R14 片库投影：六种真话状态、部分覆盖不虚构总数、零匹配未完成不算最终空。 */
class LibraryProjectionTest {
    private val source = SourceId("test-source")
    private val reference = EvidenceReference(source, null, "测试私有文件", "round14-projection")
    private val query = CatalogQuery.create("片库查询-1", null,
        com.ichichuang.cinema.domain.model.catalog.LanguageScope.ALL, emptySet(), emptySet(),
        null, null, com.ichichuang.cinema.domain.model.catalog.CatalogSort.DEFAULT, setOf(source))

    private fun item(id: String, year: FieldEvidence<Int> = FieldEvidence.Confirmed(1994, reference)) =
        CatalogItem(WorkId(id), "合成作品$id", year)

    private fun result(items: List<CatalogItem>, coverage: QueryCoverage = QueryCoverage.PARTIAL,
        failed: Set<SourceId> = emptySet(), hasMore: Boolean = false,
        total: Long? = null, revision: Long = 1) =
        CatalogResult.create(query.queryId, items, coverage, failed, 0, total, hasMore, revision)

    private fun project(result: CatalogResult?, configured: Boolean = true, loading: Boolean = false,
        offline: Boolean = false, loadFailed: Boolean = false, budgetExhausted: Boolean = false) =
        LibraryProjection.project(query, result,
            works = result?.items?.associate { it.workId.value to LibraryProjection.WorkDisplay(it.title, null, HomeImageShape.UNKNOWN) } ?: emptyMap(),
            sourcesConfigured = configured, loading = loading, offline = offline,
            loadFailed = loadFailed, budgetExhausted = budgetExhausted)

    @Test fun unconfigured_loading_failed_offline_partial_complete_empty_distinct_states() {
        assertEquals(LibraryContentStatus.UNCONFIGURED, project(result(emptyList(), QueryCoverage.COMPLETE), configured = false).status)
        assertEquals(LibraryContentStatus.LOADING, project(null, loading = true).status)
        assertEquals(LibraryContentStatus.FAILED, project(null, loadFailed = true).status)
        assertEquals(LibraryContentStatus.OFFLINE, project(result(emptyList(), QueryCoverage.COMPLETE), offline = true).status)
        assertEquals(LibraryContentStatus.PARTIAL, project(result(emptyList())).status)
        assertEquals(LibraryContentStatus.COMPLETE_EMPTY, project(result(emptyList(), QueryCoverage.COMPLETE)).status)
        assertEquals(LibraryContentStatus.READY, project(result(listOf(item("a")), QueryCoverage.COMPLETE)).status)
        // 六种状态的中文文案互不相同且不含品牌或全量计数。
        val texts = listOf(
            project(result(emptyList(), QueryCoverage.COMPLETE), configured = false).statusText,
            project(null, loading = true).statusText,
            project(null, loadFailed = true).statusText,
            project(result(emptyList(), QueryCoverage.COMPLETE), offline = true).statusText,
            project(result(emptyList())).statusText,
            project(result(emptyList(), QueryCoverage.COMPLETE)).statusText)
        assertEquals(6, texts.distinct().size)
        // 六种非 READY 状态都不出现“共 N 部”式全量计数。
        texts.forEach { text -> assertFalse("不应出现计数: $text", text.contains("共 ")) }
    }

    @Test fun zero_match_first_page_with_has_more_keeps_continue_not_final_empty() {
        val state = project(result(emptyList(), QueryCoverage.PARTIAL, hasMore = true))
        assertEquals(LibraryContentStatus.PARTIAL, state.status)
        assertTrue(state.canContinue)
        assertFalse(state.statusText.contains("暂无匹配影片"))
        assertFalse(state.coverage.complete)
        assertTrue(state.coverage.hasMore)
    }

    @Test fun one_source_failure_disclosed_with_surviving_results() {
        val failed = setOf(SourceId("broken-source"))
        val state = project(result(listOf(item("a"), item("b")), QueryCoverage.PARTIAL, failed = failed, hasMore = true))
        assertEquals(LibraryContentStatus.PARTIAL, state.status)
        assertEquals(setOf("broken-source"), state.coverage.failedSourceIds)
        assertEquals(2, state.coverage.verifiedLocalCount)
        assertTrue(state.statusText.contains("1 个失败"))
        assertTrue(state.statusText.contains("不代表全部片库"))
        assertTrue(state.canContinue)
    }

    @Test fun partial_unknown_total_never_renders_global_count() {
        val state = project(result(listOf(item("a")), QueryCoverage.PARTIAL, hasMore = true, total = null))
        assertNull(state.coverage.scopedTotal)
        assertFalse(state.statusText.contains("共 "))
    }

    @Test fun complete_known_total_is_scoped_not_global() {
        val state = project(result(listOf(item("a"), item("b")), QueryCoverage.COMPLETE, total = 2))
        assertEquals(LibraryContentStatus.READY, state.status)
        assertEquals(2L, state.coverage.scopedTotal)
        assertTrue(state.statusText.contains("当前筛选与来源范围已完整核对"))
        assertTrue(state.statusText.contains("共 2 部"))
        assertFalse(state.statusText.contains("全库"))
    }

    @Test fun budget_exhausted_shows_incomplete_with_continue() {
        val state = project(result(listOf(item("a")), QueryCoverage.PARTIAL, hasMore = true), budgetExhausted = true)
        assertTrue(state.coverage.budgetExhausted)
        assertTrue(state.statusText.contains("预算上限"))
        assertTrue(state.statusText.contains("不完整"))
        assertTrue(state.canContinue)
    }

    @Test fun unknown_or_conflicted_year_renders_unknown_not_fabricated() {
        val unknown = item("u", FieldEvidence.Unknown(reference))
        val conflict = item("c", FieldEvidence.Conflict.of(listOf(
            FieldEvidence.SourceReported(1994, reference), FieldEvidence.SourceReported(1995, reference))))
        val state = project(result(listOf(unknown, conflict, item("k"))))
        assertNull(state.items.first { it.workId == "u" }.yearText)
        assertNull(state.items.first { it.workId == "c" }.yearText)
        assertEquals("1994", state.items.first { it.workId == "k" }.yearText)
    }

    @Test fun filter_chips_use_canonical_keys_and_chinese_labels() {
        val applied = CatalogQuery.create("带条件查询", com.ichichuang.cinema.domain.model.catalog.ContentKind.MOVIE,
            com.ichichuang.cinema.domain.model.catalog.LanguageScope.NON_CHINESE,
            setOf("JP", "TH"), setOf("horror"), 1994, 1994,
            com.ichichuang.cinema.domain.model.catalog.CatalogSort.YEAR_DESCENDING,
            setOf(SourceId("test-source"), SourceId("mirror-source")))
        val chips = LibraryFilterLabels.chips(applied)
        assertEquals("year:1994-1994", chips.first { it.field == "year" }.key)
        assertEquals("1994 年", chips.first { it.field == "year" }.label)
        assertEquals("华语范围不应该出现", "非华语", chips.first { it.field == "language" }.label)
        assertEquals(setOf("日本", "泰国"), chips.filter { it.field == "country" }.map { it.label }.toSet())
        assertEquals("恐怖", chips.first { it.field == "genre" }.label)
        assertEquals("电影", chips.first { it.field == "kind" }.label)
        assertEquals("年份从晚到早", chips.first { it.field == "sort" }.label)
        // 未收录 ID 如实显示原始值，不虚构中文词条。
        val odd = LibraryFilterLabels.chips(CatalogQuery.create("生僻", null,
            com.ichichuang.cinema.domain.model.catalog.LanguageScope.ALL, setOf("XX"), emptySet(), null, null,
            com.ichichuang.cinema.domain.model.catalog.CatalogSort.DEFAULT, setOf(SourceId("s"))))
        assertEquals("XX", odd.first { it.field == "country" }.label)
    }

    @Test fun offline_with_saved_rows_still_browseable_without_playable_claim() {
        val state = project(result(listOf(item("a"), item("b")), QueryCoverage.PARTIAL, hasMore = true), offline = true)
        assertEquals(LibraryContentStatus.OFFLINE, state.status)
        assertEquals(2, state.items.size)
        assertFalse(state.canContinue)
        assertTrue(state.statusText.contains("仍可查看"))
        assertFalse(state.statusText.contains("离线可播放"))
    }
}
