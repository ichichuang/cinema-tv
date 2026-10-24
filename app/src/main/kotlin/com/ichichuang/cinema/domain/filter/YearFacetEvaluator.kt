package com.ichichuang.cinema.domain.filter

import com.ichichuang.cinema.domain.model.catalog.FieldEvidence

/** Shared year predicate for typed selections and legacy nullable CatalogQuery bounds. */
object YearFacetEvaluator {
    fun matchesYear(evidence: FieldEvidence<Int>, selection: YearSelection): FacetVerdict =
        assess(evidence, selection.startInclusive, selection.endInclusive)

    internal fun assess(evidence: FieldEvidence<Int>, startInclusive: Int?, endInclusive: Int?): FacetVerdict {
        if (startInclusive == null && endInclusive == null) {
            return FacetVerdict(MatchVerdict.MATCH, FacetReason.UNSELECTED, evidence)
        }
        val year = when (evidence) {
            is FieldEvidence.Unknown -> return FacetVerdict(MatchVerdict.INDETERMINATE,
                if (evidence.observation?.rawValue == "") FacetReason.KNOWN_EMPTY else FacetReason.UNKNOWN, evidence)
            is FieldEvidence.Conflict -> return FacetVerdict(MatchVerdict.INDETERMINATE, FacetReason.CONFLICT, evidence)
            is FieldEvidence.SourceReported -> evidence.value
            is FieldEvidence.Confirmed -> evidence.value
        }
        return if ((startInclusive == null || year >= startInclusive) && (endInclusive == null || year <= endInclusive)) {
            FacetVerdict(MatchVerdict.MATCH, FacetReason.SUPPORTED, evidence)
        } else FacetVerdict(MatchVerdict.MISMATCH, FacetReason.EXCLUDED, evidence)
    }
}
