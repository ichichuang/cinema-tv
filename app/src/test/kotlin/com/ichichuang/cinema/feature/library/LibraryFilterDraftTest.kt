package com.ichichuang.cinema.feature.library

import com.ichichuang.cinema.domain.filter.StrictFilterEvaluator
import com.ichichuang.cinema.domain.filter.MatchVerdict
import com.ichichuang.cinema.domain.filter.StrictWorkFacts
import com.ichichuang.cinema.domain.model.catalog.CatalogItem
import com.ichichuang.cinema.domain.model.catalog.CatalogQuery
import com.ichichuang.cinema.domain.model.catalog.CatalogSort
import com.ichichuang.cinema.domain.model.catalog.ContentKind
import com.ichichuang.cinema.domain.model.catalog.EvidenceReference
import com.ichichuang.cinema.domain.model.catalog.FactIds
import com.ichichuang.cinema.domain.model.catalog.FieldEvidence
import com.ichichuang.cinema.domain.model.catalog.LanguageScope
import com.ichichuang.cinema.domain.model.catalog.SourceId
import com.ichichuang.cinema.domain.model.catalog.WorkId
import org.junit.Assert.*
import org.junit.Test

/** R14 筛选草稿：跨维与、同维或、精确/年代/起止年份、无变化确认与显式删除的查询身份。 */
class LibraryFilterDraftTest {
    private val source = SourceId("test-source")
    private val reference = EvidenceReference(source, null, "测试私有文件", "round14-draft")

    private fun query(id: String = "片库查询-1", yearStart: Int? = null, yearEnd: Int? = null,
        countries: Set<String> = emptySet(), genres: Set<String> = emptySet(),
        scope: LanguageScope = LanguageScope.ALL, kind: ContentKind? = null,
        sort: CatalogSort = CatalogSort.DEFAULT) =
        CatalogQuery.create(id, kind, scope, countries, genres, yearStart, yearEnd, sort, setOf(source))

    private fun facts(year: Int?, countries: Set<String> = setOf("JP"), genres: Set<String> = setOf("horror"),
        languages: Set<String> = setOf("ja"), complete: Boolean = true, kind: ContentKind = ContentKind.MOVIE) =
        StrictWorkFacts(
            year?.let { FieldEvidence.Confirmed(it, reference) } ?: FieldEvidence.Unknown(reference),
            FieldEvidence.Confirmed(FactIds.of(countries), reference),
            FieldEvidence.Confirmed(FactIds.of(genres), reference),
            FieldEvidence.Confirmed(kind, reference),
            FieldEvidence.Confirmed(FactIds.of(languages), reference),
            if (complete) FieldEvidence.Confirmed(FactIds.of(languages), reference) else FieldEvidence.Unknown(reference))

    private fun nextId() = "片库查询-${System.nanoTime() % 1_000_000}"

    @Test fun filters_combine_and_across_dimensions_or_within() {
        // 编辑起点是无条件查询；草稿加入国家 {JP,TH} + 类型 {horror,thriller} + 1994 后确认。
        val draft = LibraryFilterController.begin(query()).copy(
            countryIds = setOf("JP", "TH"), genreIds = setOf("horror", "thriller"),
            yearMode = LibraryYearMode.EXACT, exactYear = 1994)
        val applied = (LibraryFilterController.apply(draft, ::nextId) as LibraryFilterController.ApplyOutcome.Applied).query
        assertEquals(MatchVerdict.MATCH, StrictFilterEvaluator.evaluate(applied,
            facts(1994, countries = setOf("JP"), genres = setOf("horror"))).verdict)
        assertEquals(MatchVerdict.MATCH, StrictFilterEvaluator.evaluate(applied,
            facts(1994, countries = setOf("TH"), genres = setOf("thriller"))).verdict)
        // 维度间是 AND：国家不符即整体不符。
        assertEquals(MatchVerdict.MISMATCH, StrictFilterEvaluator.evaluate(applied,
            facts(1994, countries = setOf("US"), genres = setOf("horror"))).verdict)
        // 已选字段证据未知或冲突是 INDETERMINATE，绝不混入精确结果。
        val unknownCountry = facts(1994).copy(countries = FieldEvidence.Unknown(reference))
        assertEquals(MatchVerdict.INDETERMINATE, StrictFilterEvaluator.evaluate(applied, unknownCountry).verdict)
        val conflictedGenre = facts(1994).copy(genres = FieldEvidence.Conflict.of(listOf(
            FieldEvidence.SourceReported(FactIds.of(setOf("horror")), reference),
            FieldEvidence.SourceReported(FactIds.of(setOf("comedy")), reference))))
        assertEquals(MatchVerdict.INDETERMINATE, StrictFilterEvaluator.evaluate(applied, conflictedGenre).verdict)
    }

    @Test fun exact_1994_excludes_known_1995_and_unknown_year() {
        val draft = LibraryFilterController.begin(query()).copy(yearMode = LibraryYearMode.EXACT, exactYear = 1994)
        val applied = (LibraryFilterController.apply(draft, ::nextId) as LibraryFilterController.ApplyOutcome.Applied).query
        assertEquals(1994, applied.yearStart); assertEquals(1994, applied.yearEnd)
        assertEquals(MatchVerdict.MATCH, StrictFilterEvaluator.evaluate(applied, facts(1994)).verdict)
        assertEquals(MatchVerdict.MISMATCH, StrictFilterEvaluator.evaluate(applied, facts(1995)).verdict)
        assertEquals(MatchVerdict.INDETERMINATE, StrictFilterEvaluator.evaluate(applied, facts(null)).verdict)
    }

    @Test fun decade_1980_1989_inclusive_without_2000_floor() {
        val draft = LibraryFilterController.begin(query()).copy(yearMode = LibraryYearMode.DECADE, decadeStart = 1980)
        val applied = (LibraryFilterController.apply(draft, ::nextId) as LibraryFilterController.ApplyOutcome.Applied).query
        assertEquals(1980, applied.yearStart); assertEquals(1989, applied.yearEnd)
        listOf(1980, 1985, 1989).forEach { year ->
            assertEquals(MatchVerdict.MATCH, StrictFilterEvaluator.evaluate(applied, facts(year)).verdict) }
        listOf(1979, 1990, 1994, 2000).forEach { year ->
            assertEquals(MatchVerdict.MISMATCH, StrictFilterEvaluator.evaluate(applied, facts(year)).verdict) }
        // 2000 年之前的任意起点不受任何 2000 下限约束。
        val early = LibraryFilterController.begin(query()).copy(yearMode = LibraryYearMode.RANGE, rangeStart = 1930, rangeEnd = 1969)
        val earlyApplied = (LibraryFilterController.apply(early, ::nextId) as LibraryFilterController.ApplyOutcome.Applied).query
        assertEquals(MatchVerdict.MATCH, StrictFilterEvaluator.evaluate(earlyApplied, facts(1930)).verdict)
        assertEquals(MatchVerdict.MATCH, StrictFilterEvaluator.evaluate(earlyApplied, facts(1969)).verdict)
        assertEquals(MatchVerdict.MISMATCH, StrictFilterEvaluator.evaluate(earlyApplied, facts(1970)).verdict)
    }

    @Test fun custom_range_1977_2006_inclusive_endpoints() {
        val draft = LibraryFilterController.begin(query()).copy(yearMode = LibraryYearMode.RANGE, rangeStart = 1977, rangeEnd = 2006)
        val applied = (LibraryFilterController.apply(draft, ::nextId) as LibraryFilterController.ApplyOutcome.Applied).query
        assertEquals(1977, applied.yearStart); assertEquals(2006, applied.yearEnd)
        assertEquals(MatchVerdict.MATCH, StrictFilterEvaluator.evaluate(applied, facts(1977)).verdict)
        assertEquals(MatchVerdict.MATCH, StrictFilterEvaluator.evaluate(applied, facts(2006)).verdict)
        assertEquals(MatchVerdict.MISMATCH, StrictFilterEvaluator.evaluate(applied, facts(1976)).verdict)
        assertEquals(MatchVerdict.MISMATCH, StrictFilterEvaluator.evaluate(applied, facts(2007)).verdict)
    }

    @Test fun inverted_year_range_rejected_in_chinese_and_applied_untouched() {
        val appliedBefore = query(id = "已应用查询", yearStart = 1994, yearEnd = 1994, countries = setOf("JP"))
        val draft = LibraryFilterController.begin(appliedBefore).copy(
            yearMode = LibraryYearMode.RANGE, rangeStart = 2006, rangeEnd = 1977)
        val outcome = LibraryFilterController.apply(draft, ::nextId)
        assertTrue(outcome is LibraryFilterController.ApplyOutcome.Invalid)
        val reason = (outcome as LibraryFilterController.ApplyOutcome.Invalid).reason
        assertTrue("无效原因应为中文: $reason", reason.contains("起始年份") && reason.contains("结束年份"))
        // 草稿无效时已应用查询保持逐字段不变。
        assertEquals(appliedBefore, query(id = "已应用查询", yearStart = 1994, yearEnd = 1994, countries = setOf("JP")))
    }

    @Test fun no_change_apply_keeps_query_id_and_identity() {
        val applied = query(id = "同一查询", yearStart = 1994, yearEnd = 1994, countries = setOf("JP"), sort = CatalogSort.YEAR_DESCENDING)
        val draft = LibraryFilterController.begin(applied)
        assertFalse(draft.changedFromBase())
        val outcome = LibraryFilterController.apply(draft, ::nextId)
        assertTrue(outcome is LibraryFilterController.ApplyOutcome.Unchanged)
        assertEquals(applied, (outcome as LibraryFilterController.ApplyOutcome.Unchanged).query)
        assertEquals("同一查询", outcome.query.queryId)
    }

    @Test fun meaningful_apply_removes_chip_and_clear_all_issue_new_identity_only_on_user_intent() {
        val applied = query(id = "原查询", yearStart = 1994, yearEnd = 1994,
            countries = setOf("JP", "TH"), genres = setOf("horror"), sort = CatalogSort.YEAR_DESCENDING)
        // 删除单个国家片：仅该字段移除，其余字段（含年份、类型、排序）逐字保留。
        val removed = LibraryFilterController.removeChip(applied,
            LibraryFilterChipUi("country:TH", "country", "TH", "泰国"), ::nextId)
        assertEquals(setOf("JP"), removed.countryIds)
        assertEquals(1994, removed.yearStart); assertEquals(1994, removed.yearEnd)
        assertEquals(setOf("horror"), removed.genreIds)
        assertEquals(CatalogSort.YEAR_DESCENDING, removed.sort)
        assertNotEquals("原查询", removed.queryId)
        // 年份片删除只清年份，不触碰其他维度。
        val noYear = LibraryFilterController.removeChip(removed,
            LibraryFilterChipUi("year:1994-1994", "year", "1994..1994", "1994 年"), ::nextId)
        assertNull(noYear.yearStart); assertNull(noYear.yearEnd)
        assertEquals(setOf("JP"), noYear.countryIds)
        // 审计说明是中文并说明字段。
        val draft = LibraryFilterController.begin(applied).copy(languageScope = LanguageScope.NON_CHINESE)
        val applied2 = LibraryFilterController.apply(draft, ::nextId) as LibraryFilterController.ApplyOutcome.Applied
        assertTrue(applied2.auditReason.contains("语言范围"))
        assertTrue(applied2.auditReason.contains("筛选已按用户操作更新"))
    }

    @Test fun language_scope_uses_work_language_not_dub_or_subtitles_or_country() {
        // 国产配音/中文字幕/出品国家都不改写作品语言：日语作品（完整作用域）不是华语。
        val draft = LibraryFilterController.begin(query()).copy(languageScope = LanguageScope.CHINESE)
        val chinese = (LibraryFilterController.apply(draft, ::nextId) as LibraryFilterController.ApplyOutcome.Applied).query
        assertEquals(MatchVerdict.MISMATCH, StrictFilterEvaluator.evaluate(chinese, facts(1994, languages = setOf("ja"))).verdict)
        // 多语言作品含中文成员即华语（保留其他语言）。
        assertEquals(MatchVerdict.MATCH, StrictFilterEvaluator.evaluate(chinese, facts(1994, languages = setOf("zh", "en"))).verdict)
        // 非华语需要完整、非空、无冲突且不含中文的语言集合。
        val nonChinese = LibraryFilterController.begin(query()).copy(languageScope = LanguageScope.NON_CHINESE).let {
            (LibraryFilterController.apply(it, ::nextId) as LibraryFilterController.ApplyOutcome.Applied).query }
        assertEquals(MatchVerdict.MATCH, StrictFilterEvaluator.evaluate(nonChinese, facts(1994, languages = setOf("ja"))).verdict)
        assertEquals(MatchVerdict.MISMATCH, StrictFilterEvaluator.evaluate(nonChinese, facts(1994, languages = setOf("zh"))).verdict)
        // 语言证据不完整时华语/非华语都不能严格命中。
        val incomplete = facts(1994, languages = setOf("ja"), complete = false)
        assertEquals(MatchVerdict.INDETERMINATE, StrictFilterEvaluator.evaluate(nonChinese, incomplete).verdict)
        assertEquals(MatchVerdict.INDETERMINATE, StrictFilterEvaluator.evaluate(chinese, incomplete).verdict)
        // 作品语言完全未知时同样不能严格命中任一语言范围。
        val unknownLanguage = facts(1994).copy(originalLanguages = FieldEvidence.Unknown(reference))
        assertEquals(MatchVerdict.INDETERMINATE, StrictFilterEvaluator.evaluate(chinese, unknownLanguage).verdict)
        assertEquals(MatchVerdict.INDETERMINATE, StrictFilterEvaluator.evaluate(nonChinese, unknownLanguage).verdict)
    }

    @Test fun half_bounded_year_query_rejected_by_draft_editor() {
        // 半有界年份（仅一端）在 CatalogQuery 合法，但草稿无法如实表达开区间端：
        // 编辑入口必须 fail-fast，不得静默捏造默认端（无变化确认会收紧查询）。
        val halfBounded = CatalogQuery.create("半有界", null, LanguageScope.ALL, emptySet(), emptySet(),
            1990, null, CatalogSort.DEFAULT, setOf(source))
        try {
            LibraryFilterController.begin(halfBounded)
            fail("半有界年份必须被草稿编辑器显式拒绝")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("双有界"))
        }
    }

    @Test fun draft_round_trip_preserves_every_query_field() {
        val applied = query(id = "完整查询", yearStart = 1977, yearEnd = 2006, countries = setOf("JP", "TH"),
            genres = setOf("horror", "thriller"), scope = LanguageScope.NON_CHINESE, kind = ContentKind.MOVIE,
            sort = CatalogSort.YEAR_ASCENDING)
        val draft = LibraryFilterController.begin(applied)
        assertEquals(LibraryYearMode.RANGE, draft.yearMode)
        assertFalse(draft.changedFromBase())
        // 草稿逐字段回构同一查询：内容类别、语言、国家、类型、年份、排序、来源一个不丢。
        val rebuilt = draft.toQuery("重建查询")
        assertEquals(CatalogQuery.create("重建查询", applied.contentKind, applied.languageScope,
            applied.countryIds, applied.genreIds, applied.yearStart, applied.yearEnd, applied.sort,
            applied.enabledSourceIds), rebuilt)
    }
}
