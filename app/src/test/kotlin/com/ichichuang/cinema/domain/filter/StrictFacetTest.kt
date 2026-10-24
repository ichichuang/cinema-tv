package com.ichichuang.cinema.domain.filter

import com.ichichuang.cinema.domain.model.catalog.*
import org.junit.Assert.*
import org.junit.Test

class StrictFacetTest {
    private fun ids(vararg values: String) = FieldEvidence.SourceReported(FactIds.of(values.toSet()), EvidenceReference(SourceId("fixture"), SourceItemId("one"), values.joinToString("/"), "fixture-explicit"))
    private fun verdict(facts: StrictWorkFacts, countries: Set<String> = emptySet(), genres: Set<String> = emptySet(), language: LanguageScope = LanguageScope.ALL, kind: ContentKind? = null) = StrictFilterEvaluator.evaluate(CatalogQuery.create("fixture", countryIds=countries, genreIds=genres, languageScope=language, contentKind=kind), facts)

    // Break: missing selected country must be explained, not ignored or thrown.
    @Test fun missing_country_is_indeterminate() {
        val result=verdict(StrictWorkFacts(), countries=setOf("JP"))
        assertEquals(MatchVerdict.INDETERMINATE,result.verdict)
        assertEquals(FacetReason.UNKNOWN,result.dimensions["countries"]?.reason)
    }
    // Break: OR becomes AND, coproduction loses membership, conflict chooses a favorable claim.
    @Test fun country_or_coproduction_and_conflict() {
        assertEquals(MatchVerdict.MATCH,verdict(StrictWorkFacts(countries=ids("JP","US")),countries=setOf("JP","TH")).verdict)
        assertEquals(MatchVerdict.MATCH,verdict(StrictWorkFacts(countries=ids("TH")),countries=setOf("JP","TH")).verdict)
        assertEquals(MatchVerdict.MISMATCH,verdict(StrictWorkFacts(countries=ids("FR")),countries=setOf("JP")).verdict)
        val conflict=FieldEvidence.Conflict.of(listOf(ids("JP"),ids("US")))
        val result=verdict(StrictWorkFacts(countries=conflict),countries=setOf("JP"))
        assertEquals(MatchVerdict.INDETERMINATE,result.verdict)
        assertEquals(conflict,result.dimensions["countries"]?.evidence)
        assertEquals(FacetReason.CONFLICT,result.dimensions["countries"]?.reason)
    }
    // Break: genres AND, thriller promoted to horror, or content kind inferred from genre.
    @Test fun genre_or_and_separate_content_kind() {
        assertEquals(MatchVerdict.MATCH,verdict(StrictWorkFacts(genres=ids("crime")),genres=setOf("action","crime")).verdict)
        assertEquals(MatchVerdict.MISMATCH,verdict(StrictWorkFacts(genres=ids("thriller")),genres=setOf("horror")).verdict)
        assertEquals(MatchVerdict.INDETERMINATE,verdict(StrictWorkFacts(genres=ids("action")),kind=ContentKind.MOVIE).verdict)
    }
    @Test fun japan_or_thailand_horror_in_nineties_requires_every_selected_dimension() {
        val query = CatalogQuery.create("facet03",countryIds=setOf("JP","TH"),genreIds=setOf("horror"))
            .withYearSelection(YearSelection.Decade(1990),"facet03-nineties")
        val year = FieldEvidence.SourceReported(1994,EvidenceReference(SourceId("fixture"),SourceItemId("fac03"),"1994","fixture-explicit"))
        val matching = StrictWorkFacts(year=year,countries=ids("JP"),genres=ids("horror"))
        assertEquals(MatchVerdict.MATCH,StrictFilterEvaluator.evaluate(query,matching).verdict)
        assertEquals(MatchVerdict.MATCH,StrictFilterEvaluator.evaluate(query,matching.copy(countries=ids("TH"))).verdict)
        for (mismatch in listOf(matching.copy(countries=ids("US")),matching.copy(genres=ids("thriller")),
            matching.copy(year=FieldEvidence.SourceReported(1989,year.reference.copy(rawValue="1989"))))) {
            assertEquals(MatchVerdict.MISMATCH,StrictFilterEvaluator.evaluate(query,mismatch).verdict)
        }
        assertEquals(MatchVerdict.INDETERMINATE,StrictFilterEvaluator.evaluate(query,matching.copy(year=FieldEvidence.Unknown())).verdict)
    }
    // Break: unscoped absence of Chinese becomes proof of non-Chinese or Chinese exclusion.
    @Test fun language_positive_and_scoped_negative_proof() {
        val mixed=StrictWorkFacts(originalLanguages=ids("zh","en"))
        assertEquals(MatchVerdict.MATCH,verdict(mixed,language=LanguageScope.CHINESE).verdict)
        assertEquals(MatchVerdict.MISMATCH,verdict(mixed,language=LanguageScope.NON_CHINESE).verdict)
        val partial=StrictWorkFacts(originalLanguages=ids("en"))
        assertEquals(MatchVerdict.INDETERMINATE,verdict(partial,language=LanguageScope.NON_CHINESE).verdict)
        assertEquals(MatchVerdict.INDETERMINATE,verdict(partial,language=LanguageScope.CHINESE).verdict)
        val complete=partial.copy(completeOriginalLanguages=ids("en"))
        assertEquals(MatchVerdict.MATCH,verdict(complete,language=LanguageScope.NON_CHINESE).verdict)
        assertEquals(MatchVerdict.MISMATCH,verdict(complete,language=LanguageScope.CHINESE).verdict)
    }
    // Break: empty and conflicting languages get classified by default.
    @Test fun empty_and_conflict_stay_indeterminate() {
        val conflict=FieldEvidence.Conflict.of(listOf(ids("en"),ids("zh")))
        for (language in listOf(LanguageScope.CHINESE,LanguageScope.NON_CHINESE)) {
            assertEquals(MatchVerdict.INDETERMINATE,verdict(StrictWorkFacts(originalLanguages=ids(),completeOriginalLanguages=ids()),language=language).verdict)
            assertEquals(MatchVerdict.INDETERMINATE,verdict(StrictWorkFacts(originalLanguages=conflict,completeOriginalLanguages=ids("en")),language=language).verdict)
        }
        assertEquals(FacetReason.KNOWN_EMPTY,verdict(StrictWorkFacts(countries=ids()),countries=setOf("JP")).dimensions["countries"]?.reason)
    }
    // Break: unknown overrides explicit AND mismatch, or unused dimensions exclude records.
    @Test fun and_absorbs_mismatch_and_unselected_is_neutral() {
        assertEquals(MatchVerdict.MISMATCH,verdict(StrictWorkFacts(countries=ids("FR")),countries=setOf("JP"),genres=setOf("horror")).verdict)
        assertEquals(MatchVerdict.INDETERMINATE,verdict(StrictWorkFacts(countries=ids("JP")),countries=setOf("JP"),genres=setOf("horror")).verdict)
        assertEquals(MatchVerdict.MATCH,verdict(StrictWorkFacts()).verdict)
    }
    @Test fun or_unknown_alternative_and_mismatching_alternative_remain_indeterminate() {
        val conflict=FieldEvidence.Conflict.of(listOf(ids("JP"),ids("US")))
        val result=verdict(StrictWorkFacts(countries=conflict),countries=setOf("JP","TH"))
        assertEquals(MatchVerdict.INDETERMINATE,result.verdict)
        assertEquals(conflict,result.dimensions.getValue("countries").evidence)
        assertEquals(FacetReason.CONFLICT,result.dimensions.getValue("countries").reason)
    }

}
