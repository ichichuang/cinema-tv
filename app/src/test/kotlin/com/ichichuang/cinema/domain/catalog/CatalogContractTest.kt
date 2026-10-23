package com.ichichuang.cinema.domain.catalog

import com.ichichuang.cinema.domain.model.catalog.*
import org.junit.Assert.*
import org.junit.Test

class CatalogContractTest {
    private val source = SourceId("source-a")
    private val work = WorkId("work-a")
    private fun reference(raw: String, sourceId: SourceId = source) =
        EvidenceReference(sourceId, SourceItemId("item-a"), raw, "catalog-rules")

    @Test fun year_before_2000_is_valid() {
        val query = CatalogQuery.create("query-a", yearStart = 1994, yearEnd = 1994)
        assertEquals(1994, query.yearStart)
        assertEquals(1994, query.yearEnd)
        val year = FieldEvidence.SourceReported(1994, reference("1994"))
        assertEquals(year, CatalogItem(work, "测试资料", year = year).year)
    }

    @Test fun year_range_is_inclusive() {
        // Bounds are stored verbatim. This is not an implementation of strict filtering.
        val query = CatalogQuery.create("query-a", yearStart = 1990, yearEnd = 1999)
        assertEquals(1990, query.yearStart)
        assertEquals(1999, query.yearEnd)
    }

    @Test fun inverted_year_range_is_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CatalogQuery.create("query-a", yearStart = 1999, yearEnd = 1990)
        }
    }

    @Test fun unknown_year_is_not_zero() {
        val query = CatalogQuery.create("query-a")
        assertNull(query.yearStart)
        assertNull(query.yearEnd)
        assertEquals(FieldEvidence.Unknown(), CatalogItem(work, "测试资料").year)
        for (bound in listOf(0, -1)) {
            assertThrows(IllegalArgumentException::class.java) {
                CatalogQuery.create("query-a", yearStart = bound)
            }
            assertThrows(IllegalArgumentException::class.java) {
                CatalogQuery.create("query-a", yearEnd = bound)
            }
            assertThrows(IllegalArgumentException::class.java) {
                CatalogItem(work, "测试资料", year = FieldEvidence.SourceReported(bound, reference("$bound")))
            }
        }
    }

    @Test fun language_country_and_audio_are_independent() {
        val scene = reference("仅一小段中文对白")
        val item = CatalogItem(
            work, "测试资料",
            originalLanguages = FieldEvidence.Unknown(scene),
            productionCountries = FieldEvidence.SourceReported(FactIds.of(setOf("country-jp")), reference("日本")),
        )
        val variant = MediaVariant(
            VariantId("variant-a"), work, source, SourceItemId("item-a"),
            audioLanguages = FieldEvidence.SourceReported(FactIds.of(setOf("language-zh")), reference("中文配音")),
        )
        assertEquals(FieldEvidence.Unknown(scene), item.originalLanguages)
        assertEquals(setOf("country-jp"), (item.productionCountries as FieldEvidence.SourceReported).value.values)
        assertEquals(setOf("language-zh"), (variant.audioLanguages as FieldEvidence.SourceReported).value.values)
        assertEquals(LanguageScope.ALL, CatalogQuery.create("query-a").languageScope)
        // Insufficient scene evidence stays unknown; this test does not classify any real work.
    }

    @Test fun missing_and_conflicting_evidence_remain_distinct() {
        val first = FieldEvidence.SourceReported(1994, reference("1994"))
        val second = FieldEvidence.SourceReported(1995, reference("1995", SourceId("source-b")))
        val conflict = FieldEvidence.Conflict.of(listOf(first, second))
        assertNotEquals(FieldEvidence.Unknown(), conflict)
        assertEquals(listOf(first, second), conflict.claims)
        assertEquals("1995", conflict.claims[1].reference.rawValue)
        assertEquals("catalog-rules", conflict.claims[0].reference.ruleVersion)
        assertNotEquals(first, FieldEvidence.Confirmed(1994, first.reference))
    }

    @Test fun empty_partial_result_is_not_complete() {
        val result = CatalogResult.create("query-a", emptyList(), QueryCoverage.PARTIAL, hasMore = true)
        assertTrue(result.items.isEmpty())
        assertEquals(QueryCoverage.PARTIAL, result.coverage)
        assertTrue(result.hasMore)
        assertNotEquals(LoadOutcome.Complete, LoadOutcome.Partial(PartialReason.BUDGET_EXHAUSTED))
    }

    @Test fun unknown_total_remains_null() {
        val result = CatalogResult.create("query-a", listOf(CatalogItem(work, "测试资料")), QueryCoverage.PARTIAL)
        assertNull(result.knownMatchedCount)
    }

    @Test fun source_failure_is_not_confirmed_no_match() {
        val failed = CatalogResult.create("query-a", emptyList(), QueryCoverage.PARTIAL, failedSourceIds = setOf(source))
        val noMatch = CatalogResult.create("query-b", emptyList(), QueryCoverage.COMPLETE, knownMatchedCount = 0)
        assertEquals(setOf(source), failed.failedSourceIds)
        assertNull(failed.knownMatchedCount)
        assertTrue(noMatch.failedSourceIds.isEmpty())
        assertEquals(0L, noMatch.knownMatchedCount)
        assertNotEquals(failed.coverage, noMatch.coverage)
        assertThrows(IllegalArgumentException::class.java) {
            CatalogResult.create("query-a", emptyList(), QueryCoverage.COMPLETE, failedSourceIds = setOf(source))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CatalogResult.create("query-a", emptyList(), QueryCoverage.COMPLETE, hasMore = true)
        }
    }

    @Test fun work_identity_is_independent_of_source_and_display_fields() {
        val item = CatalogItem(work, "原标题")
        val renamed = item.copy(title = "新标题")
        val first = MediaVariant(VariantId("variant-a"), work, source, SourceItemId("item-a"))
        val second = MediaVariant(VariantId("variant-b"), work, SourceId("source-b"), SourceItemId("item-a"))
        assertEquals(item.workId, renamed.workId)
        assertEquals(first.workId, second.workId)
        assertNotEquals(first.sourceId, second.sourceId)
        assertNotEquals(first.variantId, second.variantId)
        assertNotEquals(work as Any, source as Any)
    }

    @Test fun collection_inputs_cannot_rewrite_existing_snapshots() {
        val sourceIds = mutableSetOf(source)
        val countries = mutableSetOf("country-cn")
        val genres = mutableSetOf("genre-drama")
        val query = CatalogQuery.create("query-a", countryIds = countries, genreIds = genres, enabledSourceIds = sourceIds)
        val fact = FactIds.of(countries)
        val items = mutableListOf(CatalogItem(work, "测试资料"))
        val result = CatalogResult.create("query-a", items, QueryCoverage.PARTIAL, failedSourceIds = sourceIds)
        val claims = mutableListOf(
            FieldEvidence.SourceReported(1994, reference("1994")),
            FieldEvidence.SourceReported(1995, reference("1995")),
        )
        val conflict = FieldEvidence.Conflict.of(claims)
        sourceIds.clear(); countries.clear(); genres.clear(); items.clear(); claims.clear()
        assertEquals(setOf(source), query.enabledSourceIds)
        assertEquals(setOf("country-cn"), query.countryIds)
        assertEquals(setOf("genre-drama"), query.genreIds)
        assertEquals(setOf("country-cn"), fact.values)
        assertEquals(1, result.items.size)
        assertEquals(setOf(source), result.failedSourceIds)
        assertEquals(2, conflict.claims.size)
        assertThrows(UnsupportedOperationException::class.java) { (query.enabledSourceIds as MutableSet).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (result.items as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (fact.values as MutableSet).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (conflict.claims as MutableList).clear() }
    }

    @Test fun invalid_identity_counts_and_conflict_are_rejected() {
        assertThrows(IllegalArgumentException::class.java) { WorkId(" ") }
        assertThrows(IllegalArgumentException::class.java) { SourceId("") }
        assertThrows(IllegalArgumentException::class.java) { CatalogQuery.create("") }
        assertThrows(IllegalArgumentException::class.java) { FactIds.of(setOf("")) }
        assertThrows(IllegalArgumentException::class.java) { CatalogResult.create("q", emptyList(), QueryCoverage.PARTIAL, knownMatchedCount = -1) }
        assertThrows(IllegalArgumentException::class.java) { CatalogResult.create("q", emptyList(), QueryCoverage.PARTIAL, unknownFieldCount = -1) }
        assertThrows(IllegalArgumentException::class.java) { CatalogResult.create("q", emptyList(), QueryCoverage.PARTIAL, snapshotRevision = -1) }
        val claim = FieldEvidence.SourceReported(1994, reference("1994"))
        assertThrows(IllegalArgumentException::class.java) { FieldEvidence.Conflict.of(listOf(claim)) }
        assertThrows(IllegalArgumentException::class.java) { FieldEvidence.Conflict.of(listOf(claim, claim)) }
    }
}
