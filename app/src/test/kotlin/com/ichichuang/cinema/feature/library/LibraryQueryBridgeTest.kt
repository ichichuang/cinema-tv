package com.ichichuang.cinema.feature.library

import com.ichichuang.cinema.data.catalog.QueryBudget
import com.ichichuang.cinema.data.catalog.QueryCoordinator
import com.ichichuang.cinema.domain.model.catalog.CatalogQuery
import com.ichichuang.cinema.domain.model.catalog.CatalogSort
import com.ichichuang.cinema.domain.model.catalog.ContentKind
import com.ichichuang.cinema.domain.model.catalog.EvidenceReference
import com.ichichuang.cinema.domain.model.catalog.FactIds
import com.ichichuang.cinema.domain.model.catalog.FieldEvidence
import com.ichichuang.cinema.domain.model.catalog.LanguageScope
import com.ichichuang.cinema.domain.model.catalog.LoadOutcome
import com.ichichuang.cinema.domain.model.catalog.QueryCoverage
import com.ichichuang.cinema.domain.model.catalog.SourceId
import com.ichichuang.cinema.domain.model.catalog.WorkId
import com.ichichuang.cinema.domain.filter.StrictWorkFacts
import com.ichichuang.cinema.feature.browse.BrowseProbeFixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * R14 查询桥接：真实 R09 `QueryCoordinator`（预算、代次、取消、部分覆盖）+
 * R10 严格评估经合成 AppleCMS 页的真实管线；服务端不过滤，匹配只由本地严格判定。
 */
class LibraryQueryBridgeTest {
    private val testSource = SourceId("test-source")
    private val secondSource = SourceId("second-source")
    private val reference = EvidenceReference(testSource, null, "测试私有文件", "round14-bridge")

    private fun work(id: String, source: SourceId = testSource, year: Int = 1994,
        countries: Set<String> = setOf("JP"), genres: Set<String> = setOf("horror"),
        languages: Set<String> = setOf("ja"), kind: ContentKind = ContentKind.MOVIE,
        title: String = "合成作品$id") = BrowseProbeFixture.Work(WorkId(id), title, source,
        StrictWorkFacts(
            FieldEvidence.Confirmed(year, reference),
            FieldEvidence.Confirmed(FactIds.of(countries), reference),
            FieldEvidence.Confirmed(FactIds.of(genres), reference),
            FieldEvidence.Confirmed(kind, reference),
            FieldEvidence.Confirmed(FactIds.of(languages), reference),
            FieldEvidence.Confirmed(FactIds.of(languages), reference)),
        favorite = false, history = false)

    private fun coordinator(works: List<BrowseProbeFixture.Work>,
        plan: BrowseProbeFixture.LibraryPlan = BrowseProbeFixture.LibraryPlan(),
        budget: QueryBudget = QueryBudget(maxElapsedMillis = 10_000)) =
        QueryCoordinator(LibraryCandidateStore(), LibraryFixtureTransport(works, plan), budget)

    @Test fun server_ignored_year_still_verified_locally() = runBlocking {
        // 服务端页从不按年份过滤（transport 返回全部行）；本地严格评估只放行已证 1994。
        val works = listOf(work("a", year = 1994), work("b", year = 1995), work("c", year = 1994))
        val query = CatalogQuery.create("q1", null, LanguageScope.ALL, emptySet(), emptySet(),
            1994, 1994, CatalogSort.DEFAULT, setOf(testSource))
        val c = coordinator(works)
        c.start(query)
        val outcome = c.loadMore("q1")
        assertTrue(outcome is LoadOutcome.Complete)
        val result = c.snapshot("q1").result
        assertEquals(listOf("a", "c"), result.items.map { it.workId.value })
        assertEquals(QueryCoverage.COMPLETE, result.coverage)
        assertEquals(2L, result.knownMatchedCount)
    }

    @Test fun zero_match_first_page_keeps_has_more_then_later_page_matches() = runBlocking {
        // second-source 首页全是 1995，第二页才有 1994：首页零匹配不宣告完成或最终空。
        val later = (0 until 3).map { work("l$it", source = secondSource, year = 1995) }
        val matches = (0 until 2).map { work("m$it", source = secondSource, year = 1994) }
        val works = later + matches
        val plan = BrowseProbeFixture.LibraryPlan(pageSize = 3)
        val query = CatalogQuery.create("q2", null, LanguageScope.ALL, emptySet(), emptySet(),
            1994, 1994, CatalogSort.DEFAULT, setOf(secondSource))
        val c = coordinator(works, plan, QueryBudget(maxRequests = 1, maxElapsedMillis = 10_000))
        c.start(query)
        val first = c.loadMore("q2")
        assertTrue(first is LoadOutcome.Partial)
        val firstResult = c.snapshot("q2").result
        assertTrue(firstResult.items.isEmpty())
        assertTrue(firstResult.hasMore)
        assertEquals(QueryCoverage.PARTIAL, firstResult.coverage)
        // 继续：第二页出现真实 1994 匹配，查询身份与游标不重置。
        val second = c.loadMore("q2")
        assertTrue(second is LoadOutcome.Complete)
        val secondResult = c.snapshot("q2").result
        assertEquals(listOf("m0", "m1"), secondResult.items.map { it.workId.value })
    }

    @Test fun one_source_failure_disclosed_with_surviving_results() = runBlocking {
        val works = listOf(work("a"), work("b"))
        val plan = BrowseProbeFixture.LibraryPlan(failingSources = setOf("broken-source"))
        val query = CatalogQuery.create("q3", null, LanguageScope.ALL, emptySet(), emptySet(),
            null, null, CatalogSort.DEFAULT, setOf(testSource, SourceId("broken-source")))
        val c = coordinator(works, plan)
        c.start(query)
        val outcome = c.loadMore("q3")
        assertTrue(outcome is LoadOutcome.Partial)
        val result = c.snapshot("q3").result
        assertEquals(listOf("a", "b"), result.items.map { it.workId.value })
        assertEquals(setOf(SourceId("broken-source")), result.failedSourceIds)
        assertEquals(QueryCoverage.PARTIAL, result.coverage)
        assertNull(result.knownMatchedCount)
    }

    @Test fun budget_exhausted_stays_incomplete_with_retryable_continue() = runBlocking {
        val works = (0 until 10).map { work("w$it") }
        val plan = BrowseProbeFixture.LibraryPlan(pageSize = 2)
        val c = coordinator(works, plan, QueryBudget(maxRequests = 2, maxElapsedMillis = 10_000))
        val query = CatalogQuery.create("q4", null, LanguageScope.ALL, emptySet(), emptySet(),
            null, null, CatalogSort.DEFAULT, setOf(testSource))
        c.start(query)
        val outcome = c.loadMore("q4")
        assertTrue(outcome is LoadOutcome.Partial && outcome.reason == com.ichichuang.cinema.domain.model.catalog.PartialReason.BUDGET_EXHAUSTED)
        val result = c.snapshot("q4").result
        assertTrue(result.items.size < 10)
        assertTrue(result.hasMore)
        // 预算停止后继续核对仍是合法下一步，最终可完整。
        var final: LoadOutcome = outcome
        var guard = 0
        while (final !is LoadOutcome.Complete && guard++ < 10) final = c.loadMore("q4")
        assertTrue(final is LoadOutcome.Complete)
        assertEquals(10, c.snapshot("q4").result.items.size)
    }

    @Test fun identity_dedup_retains_both_source_entries() = runBlocking {
        // 同一已证作品经镜像来源再发布：展示去重为一项，两条来源证据都保留可寻址。
        val shared = work("same", source = testSource)
        val works = listOf(shared)
        val plan = BrowseProbeFixture.LibraryPlan(mirrorSources = mapOf("mirror-source" to "test-source"))
        val query = CatalogQuery.create("q5", null, LanguageScope.ALL, emptySet(), emptySet(),
            null, null, CatalogSort.DEFAULT, setOf(testSource, SourceId("mirror-source")))
        val c = coordinator(works, plan)
        c.start(query)
        c.loadMore("q5")
        val snapshot = c.snapshot("q5")
        assertEquals(listOf("same"), snapshot.result.items.map { it.workId.value })
        assertEquals(2, snapshot.acceptedEntries.size)
        assertEquals(setOf(testSource, SourceId("mirror-source")),
            snapshot.acceptedEntries.map { it.key.sourceId }.toSet())
    }

    @Test fun conflicting_years_same_work_excluded_and_counted_unknown() = runBlocking {
        // 两来源对同一作品声明不同年份：整组年份冲突，不挑争议值进入精确结果。
        val id = UUID.nameUUIDFromBytes("conflicted".toByteArray()).toString()
        val works = listOf(work(id, source = testSource, year = 1994), work(id, source = secondSource, year = 1995))
        val query = CatalogQuery.create("q6", null, LanguageScope.ALL, emptySet(), emptySet(),
            1994, 1994, CatalogSort.DEFAULT, setOf(testSource, secondSource))
        val c = coordinator(works)
        c.start(query)
        c.loadMore("q6")
        val result = c.snapshot("q6").result
        assertTrue(result.items.isEmpty())
        assertTrue(result.unknownFieldCount > 0)
        assertNull(result.knownMatchedCount)
    }

    @Test fun stale_query_generation_ignores_late_pages_of_previous_query() = runBlocking {
        val works = (0 until 4).map { work("s$it", year = if (it < 2) 1994 else 1987) }
        val c = coordinator(works, BrowseProbeFixture.LibraryPlan(pageSize = 2))
        val first = CatalogQuery.create("g1", null, LanguageScope.ALL, emptySet(), emptySet(),
            1994, 1994, CatalogSort.DEFAULT, setOf(testSource))
        c.start(first)
        c.loadMore("g1")
        // 新查询身份替换观察后，旧查询的后续页不能再写入当前会话。
        val second = CatalogQuery.create("g2", null, LanguageScope.ALL, emptySet(), emptySet(),
            1987, 1987, CatalogSort.DEFAULT, setOf(testSource))
        c.start(second)
        assertEquals(LoadOutcome.Cancelled, c.loadMore("g1"))
        c.loadMore("g2")
        val result = c.snapshot("g2").result
        assertEquals(listOf("s2", "s3"), result.items.map { it.workId.value })
    }

    @Test fun honest_sort_applies_to_verified_matches_only() = runBlocking {
        val works = listOf(work("y3", year = 1999), work("y1", year = 1985), work("y2", year = 1994), work("n", year = 2001))
        val query = CatalogQuery.create("q7", null, LanguageScope.ALL, emptySet(), emptySet(),
            1980, 1999, CatalogSort.YEAR_ASCENDING, setOf(testSource))
        val c = coordinator(works)
        c.start(query)
        c.loadMore("q7")
        val result = c.snapshot("q7").result
        // 部分覆盖下排序只针对当前已核对集合，knownMatchedCount 不虚构。
        assertEquals(listOf("y1", "y2", "y3"), result.items.map { it.workId.value })
        assertEquals(QueryCoverage.COMPLETE, result.coverage)
    }

    @Test fun unknown_country_row_not_match_and_no_fabricated_facet() = runBlocking {
        // 无国家事实的行不能进入已选国家结果，也不能因缺失被当作符合。
        val unknown = work("u").let { it.copy(facts = it.facts.copy(countries = FieldEvidence.Unknown(reference))) }
        val query = CatalogQuery.create("q8", null, LanguageScope.ALL, setOf("JP"), emptySet(),
            null, null, CatalogSort.DEFAULT, setOf(testSource))
        val c = coordinator(listOf(unknown, work("k")))
        c.start(query)
        c.loadMore("q8")
        val result = c.snapshot("q8").result
        assertEquals(listOf("k"), result.items.map { it.workId.value })
        assertTrue(result.unknownFieldCount > 0)
    }
}
