package com.ichichuang.cinema.domain.filter

import com.ichichuang.cinema.domain.model.catalog.*
import com.ichichuang.cinema.data.catalog.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class YearSelectorTest {
    // Break: selecting an exact year leaves query bounds unset and admits a foreign year.
    @Test fun exact_selection_sets_inclusive_bounds_and_excludes_foreign_year() {
        val query = CatalogQuery.create("year").withYearSelection(YearSelection.Exact(1994), "selected")
        assertEquals(1994, query.yearStart)
        assertEquals(1994, query.yearEnd)
        val reference = EvidenceReference(SourceId("synthetic"), SourceItemId("year"), "1995", "fixture")
        assertEquals(MatchVerdict.MISMATCH, StrictFilterEvaluator.evaluate(query, FieldEvidence.SourceReported(1995, reference)))
    }

    private val reference = EvidenceReference(SourceId("fixture"), SourceItemId("one"), "1994", "fixture")
    private fun evidence(year: Int): FieldEvidence<Int> = FieldEvidence.Confirmed(year, reference)
    private fun verdict(year: Int, selection: YearSelection) = YearFacetEvaluator.matchesYear(evidence(year), selection).verdict

    // Break: exact selection accepts neighboring years or rejects a proved year.
    @Test fun exact_year_is_strict() {
        assertEquals(MatchVerdict.MATCH, verdict(1994, YearSelection.Exact(1994)))
        assertEquals(MatchVerdict.MISMATCH, verdict(1995, YearSelection.Exact(1994)))
    }
    // Break: decade has exclusive endpoints or spills into neighboring decades.
    @Test fun decade_has_literal_inclusive_endpoints() {
        val selection = YearSelection.Decade(1980)
        assertEquals(MatchVerdict.MATCH, verdict(1980, selection))
        assertEquals(MatchVerdict.MATCH, verdict(1989, selection))
        assertEquals(MatchVerdict.MISMATCH, verdict(1979, selection))
        assertEquals(MatchVerdict.MISMATCH, verdict(1990, selection))
    }
    // Break: range excludes endpoints or silently expands bounds.
    @Test fun range_has_literal_inclusive_endpoints() {
        val selection = YearSelection.Range(1985, 1995)
        assertEquals(MatchVerdict.MATCH, verdict(1985, selection))
        assertEquals(MatchVerdict.MATCH, verdict(1995, selection))
        assertEquals(MatchVerdict.MISMATCH, verdict(1984, selection))
        assertEquals(MatchVerdict.MISMATCH, verdict(1996, selection))
    }
    // Break: selection is limited by indexed inventory, year 2000 or today's year.
    @Test fun positive_old_and_future_years_need_no_inventory() {
        for (year in listOf(1895, 1962, 1987, 2100, Int.MAX_VALUE)) {
            assertEquals(MatchVerdict.MATCH, verdict(year, YearSelection.Exact(year)))
        }
        val query = CatalogQuery.create("draft").withYearSelection(YearSelection.Decade(1890), "old")
        assertEquals(1890, query.yearStart)
        assertEquals(1899, query.yearEnd)
        assertEquals(MatchVerdict.MATCH, verdict(1895, YearSelection.Decade(1890)))
    }
    private fun invalid(reason: YearSelectionFailure, action: () -> Unit) {
        try { action(); fail("Expected typed failure $reason") }
        catch (failure: InvalidYearSelection) { assertEquals(reason, failure.reason) }
    }
    // Break: invalid years are clamped or throw only generic require failures.
    @Test fun invalid_bounds_have_typed_failures() {
        invalid(YearSelectionFailure.NON_POSITIVE) { YearSelection.Exact(0) }
        invalid(YearSelectionFailure.NON_POSITIVE) { YearSelection.Exact(-1) }
        invalid(YearSelectionFailure.NON_POSITIVE) { YearSelection.Range(0, 1994) }
        invalid(YearSelectionFailure.NON_POSITIVE) { YearSelection.Range(1994, -1) }
        invalid(YearSelectionFailure.NON_POSITIVE) { YearSelection.Decade(0) }
        invalid(YearSelectionFailure.INVERTED_RANGE) { YearSelection.Range(1995, 1985) }
        invalid(YearSelectionFailure.INVALID_DECADE) { YearSelection.Decade(1987) }
        invalid(YearSelectionFailure.OVERFLOW) { YearSelection.Decade(2147483640) }
    }
    // Break: changing/clearing years mutates another facet or reuses persisted session identity.
    @Test fun choosing_and_clearing_preserves_other_conditions_and_requires_new_identity() {
        val original = CatalogQuery.create("original", ContentKind.MOVIE, LanguageScope.CHINESE,
            setOf("CN", "JP"), setOf("crime"), 1985, 1995, CatalogSort.YEAR_ASCENDING, setOf(SourceId("source")))
        val selected = original.withYearSelection(YearSelection.Exact(1994), "selected")
        val cleared = selected.withYearSelection(null, "cleared")
        for (query in listOf(selected, cleared)) {
            assertEquals(ContentKind.MOVIE, query.contentKind)
            assertEquals(LanguageScope.CHINESE, query.languageScope)
            assertEquals(setOf("CN", "JP"), query.countryIds)
            assertEquals(setOf("crime"), query.genreIds)
            assertEquals(CatalogSort.YEAR_ASCENDING, query.sort)
            assertEquals(setOf(SourceId("source")), query.enabledSourceIds)
        }
        assertEquals(1985, original.yearStart)
        assertEquals(1995, original.yearEnd)
        assertEquals(1994, selected.yearStart)
        assertEquals(1994, selected.yearEnd)
        assertNull(cleared.yearStart)
        assertNull(cleared.yearEnd)
        assertEquals("selected", selected.queryId)
        assertEquals("cleared", cleared.queryId)
        invalid(YearSelectionFailure.QUERY_ID_REUSED) { original.withYearSelection(null, "original") }
    }

    private fun capture(id: String, vararg fields: Pair<String, JsonElement>) = MaccmsCatalogNormalizer.normalize(
        SourceId("year-fixture"), buildJsonObject { put("vod_id", id); fields.forEach { (key, value) -> put(key, value) } }, 1)

    // Break: unavailable evidence matches, or its empty/missing/malformed provenance is discarded.
    @Test fun unavailable_year_is_indeterminate_and_keeps_actual_observation() {
        val captures = listOf(
            capture("missing"), capture("null", "vod_year" to JsonNull),
            capture("empty", "vod_year" to JsonPrimitive("")),
            capture("malformed", "vod_year" to JsonPrimitive("1994 restored")))
        for ((index, capture) in captures.withIndex()) {
            val fact = StrictCaptureFacts.fromCaptures(listOf(capture)).year
            val assessment = YearFacetEvaluator.matchesYear(fact, YearSelection.Exact(1994))
            assertEquals(MatchVerdict.INDETERMINATE, assessment.verdict)
            assertSame(fact, assessment.evidence)
            assertEquals(if (index == 2) FacetReason.KNOWN_EMPTY else FacetReason.UNKNOWN, assessment.reason)
            val result = StrictFilterEvaluator.evaluate(CatalogQuery.create("exact", yearStart=1994, yearEnd=1994), StrictWorkFacts(year=fact))
            assertEquals(assessment, result.dimensions.getValue("year"))
        }
        assertNull((captures[0].year as FieldEvidence.Unknown).observation)
        assertEquals("null", (captures[1].year as FieldEvidence.Unknown).observation!!.rawValue)
        assertEquals("", (captures[2].year as FieldEvidence.Unknown).observation!!.rawValue)
        assertEquals("1994 restored", (captures[3].year as FieldEvidence.Unknown).observation!!.rawValue)
    }
    // Break: merging contradictory work evidence picks one year or strips original claim references.
    @Test fun contradictory_years_stay_indeterminate_after_work_fact_combination() {
        val first = capture("first", "vod_year" to JsonPrimitive("1994"))
        val second = capture("second", "vod_year" to JsonPrimitive("1995"))
        val fact = StrictCaptureFacts.fromCaptures(listOf(first, second)).year
        val result = YearFacetEvaluator.matchesYear(fact, YearSelection.Exact(1994))
        assertEquals(MatchVerdict.INDETERMINATE, result.verdict)
        assertEquals(FacetReason.CONFLICT, result.reason)
        assertSame(fact, result.evidence)
        assertEquals(listOf(first.year, second.year), (fact as FieldEvidence.Conflict).claims)
    }
    // Break: version/update dates supply work year, or a source declaration becomes confirmed metadata.
    @Test fun source_declared_year_preserves_provenance_and_ignores_version_dates() {
        val dates = arrayOf("vod_time" to JsonPrimitive("2026-10-24"),
            "digital_release_date" to JsonPrimitive("2026-01-01"), "restored_media_date" to JsonPrimitive("2025-01-01"))
        val capture = capture("declared", "vod_year" to JsonPrimitive("1994"), *dates)
        val fact = StrictCaptureFacts.fromCaptures(listOf(capture)).year
        assertEquals(capture.year, fact)
        val result = YearFacetEvaluator.matchesYear(fact, YearSelection.Exact(1994))
        assertEquals(MatchVerdict.MATCH, result.verdict)
        assertSame(fact, result.evidence)
        val reference = (fact as FieldEvidence.SourceReported).reference
        assertEquals(SourceId("year-fixture"), reference.sourceId)
        assertEquals(SourceItemId("declared"), reference.sourceItemId)
        assertEquals("1994", reference.rawValue)
        assertEquals("maccms-exact-2026-10-24", reference.ruleVersion)
        for (date in dates) {
            val absent = StrictCaptureFacts.fromCaptures(listOf(capture("date-only", date))).year
            assertEquals(MatchVerdict.INDETERMINATE, YearFacetEvaluator.matchesYear(absent, YearSelection.Exact(2026)).verdict)
        }
    }
}
