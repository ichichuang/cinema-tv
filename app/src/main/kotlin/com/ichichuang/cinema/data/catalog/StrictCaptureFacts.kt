package com.ichichuang.cinema.data.catalog

import com.ichichuang.cinema.domain.filter.StrictWorkFacts
import com.ichichuang.cinema.domain.model.catalog.*

/** Pure adapter over saved immutable captures; caller must supply only one proven work group. */
internal object StrictCaptureFacts {
    const val RULE_VERSION = "strict-explicit-facets-2026-10-24"

    fun fromCaptures(captures: List<NormalizedCatalogCapture>): StrictWorkFacts {
        fun <T> combine(field: (NormalizedCatalogCapture) -> FieldEvidence<T>): FieldEvidence<T> =
            CatalogClaims.combine(captures.map(field))
        fun original(capture: NormalizedCatalogCapture): FieldEvidence<FactIds> =
            capture.facts["originalLanguages"] ?: FieldEvidence.Unknown()
        return StrictWorkFacts(
            year = combine { it.year },
            countries = combine { mapped(it, "countries", "vod_area", TaxonomyRuleMapper.countries + ("泰国" to "TH")) },
            genres = combine { mapped(it, "genres", "vod_class", TaxonomyRuleMapper.genres + ("犯罪" to "crime")) },
            contentKind = combine { capture ->
                val raw = capture.raw["work_content_kind"].scalar()
                val reference = reference(capture, "work_content_kind")
                val kind = ContentKind.entries.firstOrNull { it.name.lowercase() == raw }
                if (kind == null) FieldEvidence.Unknown(reference) else FieldEvidence.SourceReported(kind, reference!!)
            },
            originalLanguages = combine(::original),
            completeOriginalLanguages = combine { capture ->
                val claim = original(capture)
                val value = when (claim) {
                    is FieldEvidence.SourceReported -> claim.value
                    is FieldEvidence.Confirmed -> claim.value
                    else -> null
                }
                // A bare scope marker must never attest to another capture's language claim.
                if (capture.raw["work_original_language_scope"].scalar() == "complete" && value != null) {
                    FieldEvidence.SourceReported(value, EvidenceReference(capture.sourceId, capture.itemId,
                        "work_original_language=${capture.raw["work_original_language"]};work_original_language_scope=${capture.raw["work_original_language_scope"]}", RULE_VERSION))
                } else FieldEvidence.Unknown(reference(capture, "work_original_language_scope"))
            },
        )
    }

    private fun reference(capture: NormalizedCatalogCapture, field: String): EvidenceReference? = capture.raw[field]?.let {
        EvidenceReference(capture.sourceId, capture.itemId, it.scalar() ?: it.toString(), RULE_VERSION)
    }

    private fun mapped(capture: NormalizedCatalogCapture, fact: String, field: String, aliases: Map<String, String>): FieldEvidence<FactIds> {
        val saved = capture.facts[fact] ?: FieldEvidence.Unknown()
        // Preserve all existing claims and disputes. Only extend an unavailable exact mapping.
        if (saved !is FieldEvidence.Unknown || capture.fieldNames[fact] != field) return saved
        val reference = reference(capture, field)
        val raw = capture.raw[field].scalar() ?: return FieldEvidence.Unknown(reference)
        val values = if (raw.isEmpty()) emptyList() else raw.trim().split(Regex("[/,、;|]")).map {
            aliases[it.trim()] ?: return FieldEvidence.Unknown(reference)
        }
        return FieldEvidence.SourceReported(FactIds.of(values.toSet()), reference!!)
    }
}
