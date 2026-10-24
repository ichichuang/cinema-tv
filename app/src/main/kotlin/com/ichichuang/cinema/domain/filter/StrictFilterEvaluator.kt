package com.ichichuang.cinema.domain.filter

import com.ichichuang.cinema.domain.model.catalog.*

enum class MatchVerdict { MATCH, MISMATCH, INDETERMINATE }

/** 显式候选入口；不读取或写入旧筛选、来源或个人状态。 */
object StrictFilterEvaluator {
    /** Compatibility boundary for callers with year evidence only; other selected facts are unknown. */
    fun evaluate(query: CatalogQuery, year: FieldEvidence<Int>): MatchVerdict =
        evaluate(query, StrictWorkFacts(year = year)).verdict

    fun evaluate(query: CatalogQuery, facts: StrictWorkFacts): StrictEvaluation {
        val dimensions = linkedMapOf(
            "year" to YearFacetEvaluator.assess(facts.year, query.yearStart, query.yearEnd),
            "countries" to membership(facts.countries, query.countryIds),
            "genres" to membership(facts.genres, query.genreIds),
            "contentKind" to assess(facts.contentKind, query.contentKind != null) { it == query.contentKind },
            "originalLanguages" to language(facts, query.languageScope),
        )
        val verdict = when {
            dimensions.values.any { it.verdict == MatchVerdict.MISMATCH } -> MatchVerdict.MISMATCH
            dimensions.values.any { it.verdict == MatchVerdict.INDETERMINATE } -> MatchVerdict.INDETERMINATE
            else -> MatchVerdict.MATCH
        }
        return StrictEvaluation(verdict, dimensions)
    }

    private fun membership(evidence: FieldEvidence<FactIds>, selected: Set<String>): FacetVerdict =
        assess(evidence, selected.isNotEmpty()) { ids -> selected.any { it in ids.values } }

    private fun <T> assess(evidence: FieldEvidence<T>, selected: Boolean, matches: (T) -> Boolean): FacetVerdict {
        if (!selected) return FacetVerdict(MatchVerdict.MATCH, FacetReason.UNSELECTED, evidence)
        val value = when (evidence) {
            is FieldEvidence.Unknown -> return FacetVerdict(MatchVerdict.INDETERMINATE, FacetReason.UNKNOWN, evidence)
            is FieldEvidence.Conflict -> return FacetVerdict(MatchVerdict.INDETERMINATE, FacetReason.CONFLICT, evidence)
            is FieldEvidence.SourceReported -> evidence.value
            is FieldEvidence.Confirmed -> evidence.value
        }
        if (value is FactIds && value.values.isEmpty()) return FacetVerdict(MatchVerdict.INDETERMINATE, FacetReason.KNOWN_EMPTY, evidence)
        return if (matches(value)) FacetVerdict(MatchVerdict.MATCH, FacetReason.SUPPORTED, evidence)
        else FacetVerdict(MatchVerdict.MISMATCH, FacetReason.EXCLUDED, evidence)
    }

    private fun language(facts: StrictWorkFacts, selected: LanguageScope): FacetVerdict {
        val observed = assess(facts.originalLanguages, selected != LanguageScope.ALL) { "zh" in it.values }
        if (selected == LanguageScope.ALL || observed.verdict == MatchVerdict.INDETERMINATE) return observed
        if (observed.verdict == MatchVerdict.MATCH) return if (selected == LanguageScope.CHINESE) observed
            else observed.copy(verdict = MatchVerdict.MISMATCH, reason = FacetReason.EXCLUDED)
        val complete = assess(facts.completeOriginalLanguages, true) { scoped ->
            val actual = when (val original = facts.originalLanguages) {
                is FieldEvidence.SourceReported -> original.value
                is FieldEvidence.Confirmed -> original.value
                else -> null
            }
            scoped == actual
        }
        if (complete.verdict != MatchVerdict.MATCH) return observed.copy(
            verdict = MatchVerdict.INDETERMINATE, reason = FacetReason.INCOMPLETE_SCOPE,
            scopeEvidence = facts.completeOriginalLanguages,
        )
        return observed.copy(
            verdict = if (selected == LanguageScope.NON_CHINESE) MatchVerdict.MATCH else MatchVerdict.MISMATCH,
            reason = if (selected == LanguageScope.NON_CHINESE) FacetReason.SUPPORTED else FacetReason.EXCLUDED,
            scopeEvidence = facts.completeOriginalLanguages,
        )
    }
}
