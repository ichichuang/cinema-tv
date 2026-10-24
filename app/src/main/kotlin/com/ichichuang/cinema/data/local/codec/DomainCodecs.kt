package com.ichichuang.cinema.data.local.codec

import com.ichichuang.cinema.domain.model.catalog.*
import com.ichichuang.cinema.domain.model.userdata.*
import kotlinx.serialization.json.*

private val contentKinds = mapOf(ContentKind.MOVIE to "movie", ContentKind.SERIES to "series",
    ContentKind.VARIETY to "variety", ContentKind.ANIMATION to "animation",
    ContentKind.DOCUMENTARY to "documentary", ContentKind.OTHER to "other")
private val languageScopes = mapOf(LanguageScope.ALL to "all", LanguageScope.CHINESE to "chinese",
    LanguageScope.NON_CHINESE to "non_chinese")
private val sorts = mapOf(CatalogSort.DEFAULT to "default", CatalogSort.YEAR_ASCENDING to "year_ascending",
    CatalogSort.YEAR_DESCENDING to "year_descending")
private val watchStates = mapOf(WatchState.NOT_STARTED to "not_started", WatchState.IN_PROGRESS to "in_progress",
    WatchState.COMPLETED to "completed")

private fun <T> JsonElement.tag(tags: Map<T, String>): T =
    tags.entries.firstOrNull { it.value == text() }?.key ?: invalidPayload()

internal object CatalogQueryCodec : JsonStorageCodec<CatalogQuery>("catalog_query") {
    override fun toJson(value: CatalogQuery) = queryJson(value)
    override fun fromJson(value: JsonObject) = value.query()
}

private fun queryJson(value: CatalogQuery): JsonObject = buildJsonObject {
    put("queryId", value.queryId)
    put("contentKind", stringJson(value.contentKind?.let(contentKinds::getValue)))
    put("languageScope", languageScopes.getValue(value.languageScope))
    put("countryIds", stringsJson(value.countryIds))
    put("genreIds", stringsJson(value.genreIds))
    put("yearStart", intJson(value.yearStart))
    put("yearEnd", intJson(value.yearEnd))
    put("sort", sorts.getValue(value.sort))
    put("enabledSourceIds", stringsJson(value.enabledSourceIds.map { it.value }.toSet()))
}

private fun JsonObject.query() = CatalogQuery.create(
    queryId = required("queryId").text(),
    contentKind = required("contentKind").nullable { it.tag(contentKinds) },
    languageScope = required("languageScope").tag(languageScopes),
    countryIds = required("countryIds").stringSet(),
    genreIds = required("genreIds").stringSet(),
    yearStart = required("yearStart").nullable { it.integer() },
    yearEnd = required("yearEnd").nullable { it.integer() },
    sort = required("sort").tag(sorts),
    enabledSourceIds = required("enabledSourceIds").stringSet().map(::SourceId).toSet(),
)

internal object BrowseSnapshotCodec : JsonStorageCodec<BrowseSnapshot>("browse_snapshot") {
    override fun toJson(value: BrowseSnapshot) = buildJsonObject {
        put("contextKey", value.contextKey)
        put("route", value.route)
        put("parentRoute", stringJson(value.parentRoute))
        put("query", value.query?.let(::queryJson) ?: JsonNull)
        put("resultRevision", longJson(value.resultRevision))
        put("focusedWorkId", stringJson(value.focusedWorkId?.value))
        put("anchorWorkId", stringJson(value.anchorWorkId?.value))
        put("relativeOffset", value.relativeOffset)
        put("savedAt", value.savedAt)
    }

    override fun fromJson(value: JsonObject) = with(value) {
        BrowseSnapshot(required("contextKey").text(), required("route").text(),
            required("parentRoute").nullable { it.text() }, required("query").nullable { it.jsonObject.query() },
            required("resultRevision").nullable { it.longNumber() },
            required("focusedWorkId").nullable { WorkId(it.text()) },
            required("anchorWorkId").nullable { WorkId(it.text()) },
            required("relativeOffset").floatNumber(), required("savedAt").longNumber())
    }
}

internal object PlaybackSnapshotCodec : JsonStorageCodec<PlaybackSnapshot>("playback_snapshot") {
    override fun toJson(value: PlaybackSnapshot) = buildJsonObject {
        put("workId", value.workId.value)
        put("episodeId", stringJson(value.episodeId?.value))
        put("variantId", stringJson(value.variantId?.value))
        put("sessionId", value.sessionId)
        put("operationSequence", value.operationSequence)
        put("positionMs", value.positionMs)
        put("durationMs", longJson(value.durationMs))
        put("watchState", watchStates.getValue(value.watchState))
        put("capturedAt", value.capturedAt)
    }

    override fun fromJson(value: JsonObject) = with(value) {
        PlaybackSnapshot(WorkId(required("workId").text()),
            required("episodeId").nullable { EpisodeId(it.text()) },
            required("variantId").nullable { VariantId(it.text()) }, required("sessionId").text(),
            required("operationSequence").longNumber(), required("positionMs").longNumber(),
            required("durationMs").nullable { it.longNumber() }, required("watchState").tag(watchStates),
            required("capturedAt").longNumber())
    }
}

internal object CatalogItemCodec : JsonStorageCodec<CatalogItem>("catalog_item") {
    override fun toJson(value: CatalogItem) = buildJsonObject {
        put("workId", value.workId.value)
        put("title", value.title)
        put("year", evidenceJson(value.year) { JsonPrimitive(it) })
        put("originalLanguages", evidenceJson(value.originalLanguages) { stringsJson(it.values) })
        put("productionCountries", evidenceJson(value.productionCountries) { stringsJson(it.values) })
    }

    override fun fromJson(value: JsonObject) = with(value) {
        CatalogItem(WorkId(required("workId").text()), required("title").text(),
            required("year").evidence { it.integer() },
            required("originalLanguages").evidence { FactIds.of(it.stringSet()) },
            required("productionCountries").evidence { FactIds.of(it.stringSet()) })
    }
}

internal object MediaVariantCodec : JsonStorageCodec<MediaVariant>("media_variant") {
    override fun toJson(value: MediaVariant) = buildJsonObject {
        put("variantId", value.variantId.value)
        put("workId", value.workId.value)
        put("sourceId", value.sourceId.value)
        put("sourceItemId", value.sourceItemId.value)
        put("audioLanguages", evidenceJson(value.audioLanguages) { stringsJson(it.values) })
    }

    override fun fromJson(value: JsonObject) = with(value) {
        MediaVariant(VariantId(required("variantId").text()), WorkId(required("workId").text()),
            SourceId(required("sourceId").text()), SourceItemId(required("sourceItemId").text()),
            required("audioLanguages").evidence { FactIds.of(it.stringSet()) })
    }
}

private fun referenceJson(reference: EvidenceReference) = buildJsonObject {
    put("sourceId", reference.sourceId.value)
    put("sourceItemId", stringJson(reference.sourceItemId?.value))
    put("rawValue", reference.rawValue)
    put("ruleVersion", reference.ruleVersion)
}

private fun JsonElement.reference(): EvidenceReference = with(jsonObject) {
    EvidenceReference(SourceId(required("sourceId").text()),
        required("sourceItemId").nullable { SourceItemId(it.text()) },
        required("rawValue").text(), required("ruleVersion").text())
}

internal fun <T> evidenceJson(evidence: FieldEvidence<T>, encode: (T) -> JsonElement): JsonObject = buildJsonObject {
    when (evidence) {
        is FieldEvidence.Unknown -> {
            put("state", "unknown")
            put("observation", evidence.observation?.let(::referenceJson) ?: JsonNull)
        }
        is FieldEvidence.SourceReported -> {
            put("state", "source_reported")
            put("value", encode(evidence.value))
            put("reference", referenceJson(evidence.reference))
        }
        is FieldEvidence.Confirmed -> {
            put("state", "confirmed")
            put("value", encode(evidence.value))
            put("reference", referenceJson(evidence.reference))
        }
        is FieldEvidence.Conflict -> {
            put("state", "conflict")
            put("claims", JsonArray(evidence.claims.map { claim -> buildJsonObject {
                put("value", encode(claim.value))
                put("reference", referenceJson(claim.reference))
            } }))
        }
    }
}

internal fun <T> JsonElement.evidence(decode: (JsonElement) -> T): FieldEvidence<T> = with(jsonObject) {
    when (required("state").text()) {
        "unknown" -> FieldEvidence.Unknown(required("observation").nullable { it.reference() })
        "source_reported" -> FieldEvidence.SourceReported(decode(required("value")), required("reference").reference())
        "confirmed" -> FieldEvidence.Confirmed(decode(required("value")), required("reference").reference())
        "conflict" -> FieldEvidence.Conflict.of(required("claims").jsonArray.map { element -> with(element.jsonObject) {
            FieldEvidence.SourceReported(decode(required("value")), required("reference").reference())
        } })
        else -> invalidPayload()
    }
}
