package com.ichichuang.cinema.domain.filter

import com.ichichuang.cinema.domain.model.catalog.*
import java.util.Collections

/** Immutable work facts, never playback audio or subtitles. */
data class StrictWorkFacts(
    val year: FieldEvidence<Int> = FieldEvidence.Unknown(),
    val countries: FieldEvidence<FactIds> = FieldEvidence.Unknown(),
    val genres: FieldEvidence<FactIds> = FieldEvidence.Unknown(),
    val contentKind: FieldEvidence<ContentKind> = FieldEvidence.Unknown(),
    val originalLanguages: FieldEvidence<FactIds> = FieldEvidence.Unknown(),
    /** Complete language claims must bind to actual language evidence in the same capture. */
    val completeOriginalLanguages: FieldEvidence<FactIds> = FieldEvidence.Unknown(),
)

enum class FacetReason { UNSELECTED, SUPPORTED, EXCLUDED, UNKNOWN, KNOWN_EMPTY, CONFLICT, INCOMPLETE_SCOPE }
data class FacetVerdict(val verdict: MatchVerdict, val reason: FacetReason, val evidence: FieldEvidence<*>, val scopeEvidence: FieldEvidence<*>? = null)
class StrictEvaluation(val verdict: MatchVerdict, dimensions: Map<String, FacetVerdict>) {
    val dimensions: Map<String, FacetVerdict> = Collections.unmodifiableMap(LinkedHashMap(dimensions))
}
