package com.ichichuang.cinema.data.catalog

import com.ichichuang.cinema.domain.model.catalog.SourceId
import com.ichichuang.cinema.domain.model.catalog.FieldEvidence
import kotlinx.serialization.json.JsonNull
import java.util.Collections

/** Caller supplies validated identity/revisions. Endpoint resemblance is never authorization. */
internal data class SourceCapabilityContext(
    val sourceId: SourceId,
    val endpoint: String,
    val adapterRevision: String,
    val protocolRevision: String,
    val authorizationRevision: String,
) {
    init { require(listOf(endpoint, adapterRevision, protocolRevision, authorizationRevision).all { it.isNotBlank() }) }
}

internal data class SourceCategoryKey(val sourceId: SourceId, val categoryId: String) {
    init { require(categoryId.isNotBlank()) }
}

internal enum class ProbeHealth { SUCCESS, TIMEOUT, TRANSPORT_FAILURE, PROTOCOL_FAILURE }
internal enum class RemoteFilterEvidence { UNKNOWN, CONTRADICTED, OBSERVED_ON_SAMPLE }
internal data class RawFieldAvailability(val present: Int, val nonNull: Int, val sampleSize: Int) {
    init { require(sampleSize >= 0 && present in 0..sampleSize && nonNull in 0..present) }
}

internal data class SourceCapabilityObservation(
    val context: SourceCapabilityContext,
    val observedAt: Long,
    val expiresAt: Long,
    val probeRevision: String,
    val health: ProbeHealth,
    val requestedYear: Int?,
    val requestedCategory: SourceCategoryKey?,
    val fields: Map<String, RawFieldAvailability>,
    val yearFiltering: RemoteFilterEvidence,
    val categoryFiltering: RemoteFilterEvidence,
    val sampleItems: Set<SourceItemKey>,
    val controlItems: Set<SourceItemKey>,
) {
    init {
        require(observedAt >= 0 && expiresAt > observedAt && probeRevision.isNotBlank())
        require(requestedYear == null || requestedYear > 0)
        require(requestedCategory == null || requestedCategory.sourceId == context.sourceId)
        require(requestedYear == null || requestedCategory == null)
        require((sampleItems + controlItems).all { it.sourceId == context.sourceId })
        require(fields.size <= 8 && fields.keys.all { it.isNotBlank() })
        require(fields.values.all { it.sampleSize <= SourceCapabilityProbe.MAX_SAMPLE_ITEMS })
        require(sampleItems.size <= SourceCapabilityProbe.MAX_SAMPLE_ITEMS && controlItems.size <= SourceCapabilityProbe.MAX_SAMPLE_ITEMS)
        require(yearFiltering == RemoteFilterEvidence.UNKNOWN || requestedYear != null)
        require(categoryFiltering == RemoteFilterEvidence.UNKNOWN || requestedCategory != null)
        require(health == ProbeHealth.SUCCESS || (fields.isEmpty() && sampleItems.isEmpty() && controlItems.isEmpty() &&
            yearFiltering == RemoteFilterEvidence.UNKNOWN && categoryFiltering == RemoteFilterEvidence.UNKNOWN))
        require((yearFiltering != RemoteFilterEvidence.OBSERVED_ON_SAMPLE && categoryFiltering != RemoteFilterEvidence.OBSERVED_ON_SAMPLE) ||
            (sampleItems.isNotEmpty() && controlItems.any { it !in sampleItems }))
    }
}

/** Pure candidate probe. Observed-on-sample is evidence about this bounded sample, never a universal promise. */
internal object SourceCapabilityProbe {
    const val REVISION = "maccms-capability-2026-10-24"
    fun observe(
        context: SourceCapabilityContext,
        observedAt: Long,
        ttlMillis: Long,
        captures: List<NormalizedCatalogCapture>,
        requestedYear: Int? = null,
        requestedCategory: SourceCategoryKey? = null,
        health: ProbeHealth = ProbeHealth.SUCCESS,
        unfilteredControl: List<NormalizedCatalogCapture> = emptyList(),
    ): SourceCapabilityObservation {
        require(observedAt >= 0 && ttlMillis > 0 && observedAt <= Long.MAX_VALUE - ttlMillis)
        require(requestedYear == null || requestedYear > 0)
        require(requestedCategory == null || requestedCategory.sourceId == context.sourceId)
        // Probe one dimension at a time so the unfiltered control cannot be confounded by another filter.
        require(requestedYear == null || requestedCategory == null)
        require(captures.size <= MAX_SAMPLE_ITEMS && unfilteredControl.size <= MAX_SAMPLE_ITEMS)
        require((captures + unfilteredControl).all { it.sourceId == context.sourceId && it.observedAt in 0..observedAt })
        require(health == ProbeHealth.SUCCESS || (captures.isEmpty() && unfilteredControl.isEmpty()))
        val fields = RAW_FIELDS.mapNotNull { field ->
            val present = captures.count { field in it.raw }
            if (present == 0) null else field to RawFieldAvailability(present,
                captures.count { it.raw[field] != null && it.raw[field] !== JsonNull }, captures.size)
        }.toMap()
        fun assess(matches: (NormalizedCatalogCapture) -> Boolean?): RemoteFilterEvidence {
            if (health != ProbeHealth.SUCCESS) return RemoteFilterEvidence.UNKNOWN
            if (captures.any { matches(it) == false }) return RemoteFilterEvidence.CONTRADICTED
            if (captures.isNotEmpty() && captures.all { it.itemId != null && matches(it) == true } &&
                unfilteredControl.any { control -> control.itemId != null && matches(control) == false &&
                    captures.none { it.itemId == control.itemId } }) return RemoteFilterEvidence.OBSERVED_ON_SAMPLE
            return RemoteFilterEvidence.UNKNOWN
        }
        fun yearMatches(capture: NormalizedCatalogCapture): Boolean? = when (val year = capture.year) {
            is FieldEvidence.SourceReported -> year.value == requestedYear
            is FieldEvidence.Confirmed -> year.value == requestedYear
            is FieldEvidence.Unknown, is FieldEvidence.Conflict -> null
        }
        fun categoryMatches(capture: NormalizedCatalogCapture): Boolean? =
            capture.raw["type_id"].scalar()?.takeIf { it.isNotBlank() }?.let { it == requestedCategory?.categoryId }
        fun keys(rows: List<NormalizedCatalogCapture>): Set<SourceItemKey> = Collections.unmodifiableSet(
            rows.mapNotNull { row -> row.itemId?.let { SourceItemKey(row.sourceId, it) } }.toSet())
        return SourceCapabilityObservation(context, observedAt, observedAt + ttlMillis, REVISION, health,
            requestedYear, requestedCategory, Collections.unmodifiableMap(fields),
            if (requestedYear == null) RemoteFilterEvidence.UNKNOWN else assess(::yearMatches),
            if (requestedCategory == null) RemoteFilterEvidence.UNKNOWN else assess(::categoryMatches), keys(captures), keys(unfilteredControl))
    }
    const val MAX_SAMPLE_ITEMS = 200
    private val RAW_FIELDS = listOf("vod_id", "vod_year", "type_id", "vod_area", "vod_class",
        "work_original_language", "vod_lang", "subtitle_language")
}
