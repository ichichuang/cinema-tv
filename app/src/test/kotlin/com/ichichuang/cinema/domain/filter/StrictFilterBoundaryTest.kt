package com.ichichuang.cinema.domain.filter

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ichichuang.cinema.domain.model.DefaultMovieFilter
import com.ichichuang.cinema.domain.model.MovieFilter
import com.ichichuang.cinema.domain.model.catalog.*
import java.nio.file.Files
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class StrictFilterBoundaryTest {
    private val query = CatalogQuery.create("fixture-exact-1994", yearStart = 1994, yearEnd = 1994)
    private val reference = EvidenceReference(SourceId("fixture-source"), SourceItemId("fixture-item"), "1994", "fixture-year-rule")

    // Break caught: treating unavailable evidence as a successful exact-year match.
    @Test fun unknown_year_does_not_match_exact_1994() {
        assertEquals(MatchVerdict.INDETERMINATE, StrictFilterEvaluator.evaluate(query, FieldEvidence.Unknown()))
    }

    // Break caught: resolving a disputed year by choosing one supporting claim.
    @Test fun conflicting_year_remains_indeterminate() {
        val conflict = FieldEvidence.Conflict.of(listOf(
            FieldEvidence.SourceReported(1994, reference),
            FieldEvidence.SourceReported(1995, reference.copy(rawValue = "1995")),
        ))
        assertEquals(MatchVerdict.INDETERMINATE, StrictFilterEvaluator.evaluate(query, conflict))
    }

    // Break caught: silently ignoring a selected dimension when only year evidence is supplied.
    @Test
    fun selected_facet_without_facts_is_indeterminate() {
        assertEquals(MatchVerdict.INDETERMINATE, StrictFilterEvaluator.evaluate(CatalogQuery.create("fixture-country", countryIds = setOf("JP")), FieldEvidence.Unknown()))
    }

    // Break caught: requiring an unselected year dimension to have known evidence.
    @Test fun unselected_year_is_neutral() {
        assertEquals(MatchVerdict.MATCH, StrictFilterEvaluator.evaluate(CatalogQuery.create("fixture-all"), FieldEvidence.Unknown()))
    }

    // Break caught: accepting the wrong year or silently weakening an unchanged query.
    @Test fun candidate_result_preserves_legacy_filter_and_persisted_preferences() = runBlocking {
        val folder = Files.createTempDirectory("strict-filter-fixture").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { folder.resolve("cinema_filters.preferences_pb") })
        val yearKey = stringPreferencesKey("f_year")
        val langKey = stringPreferencesKey("f_lang")
        val legacy = MovieFilter(lang = "国语", year = "1994")
        try {
            store.edit { it[yearKey] = "1994"; it[langKey] = "国语" }
            assertEquals(MatchVerdict.MATCH, StrictFilterEvaluator.evaluate(query, FieldEvidence.SourceReported(1994, reference)))
            assertEquals(MatchVerdict.MISMATCH, StrictFilterEvaluator.evaluate(query, FieldEvidence.SourceReported(1995, reference.copy(rawValue = "1995"))))
            assertEquals("1994", legacy.year)
            assertEquals("国语", legacy.lang)
            assertEquals("国语", DefaultMovieFilter.lang)
            assertEquals(1994, query.yearStart)
            assertEquals(1994, query.yearEnd)
            val persisted = store.data.first()
            assertEquals("1994", persisted[yearKey])
            assertEquals("国语", persisted[langKey])
        } finally {
            scope.cancel()
            scope.coroutineContext[Job]!!.join()
            folder.deleteRecursively()
        }
    }
}
