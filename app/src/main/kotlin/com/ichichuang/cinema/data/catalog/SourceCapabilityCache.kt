package com.ichichuang.cinema.data.catalog

import com.ichichuang.cinema.data.local.codec.*
import com.ichichuang.cinema.domain.model.catalog.SourceId
import com.ichichuang.cinema.domain.model.catalog.SourceItemId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.util.Collections

internal interface SourceCapabilityPersistence {
    suspend fun load(): String?
    /** Atomically replace the complete bounded snapshot, or throw without committing. */
    suspend fun save(snapshot: String)
}
internal data class SourceCapabilityRecord(
    val latest: SourceCapabilityObservation,
    val lastSuccessful: SourceCapabilityObservation?,
    val requiredAliasContext: SourceCapabilityContext? = null,
) {
    val aliasInvalidated: Boolean get() = requiredAliasContext != null
    init {
        require(lastSuccessful == null || (lastSuccessful.health == ProbeHealth.SUCCESS &&
            lastSuccessful.context.sourceId == latest.context.sourceId && lastSuccessful.observedAt <= latest.observedAt))
        require(requiredAliasContext == null || requiredAliasContext.sourceId == latest.context.sourceId)
    }
}
internal enum class CapabilityFreshness { FRESH, UNPROVEN, EXPIRED, CONTEXT_CHANGED, PROBE_CHANGED, CLOCK_ROLLBACK, ALIAS_INVALIDATED }
internal data class SourceCapabilityView(val record: SourceCapabilityRecord, val freshness: CapabilityFreshness)

/** Explicit candidate instance; no startup/DI binding. A timeout records health without renewing the last successful proof. */
internal class SourceCapabilityCache private constructor(
    private val maxRecords: Int,
    private val persistence: SourceCapabilityPersistence,
    initial: List<SourceCapabilityRecord>,
) {
    private val mutex = Mutex()
    private var records = initial.associateBy { it.latest.context.sourceId }

    companion object {
        suspend fun open(maxRecords: Int, persistence: SourceCapabilityPersistence): SourceCapabilityCache {
            require(maxRecords in 1..SourceCapabilityCacheCodec.MAX_RECORDS)
            val loaded = persistence.load()?.let(SourceCapabilityCacheCodec::decode) ?: emptyList()
            val bounded = loaded.sortedBy { it.latest.observedAt }.takeLast(maxRecords)
            if (loaded.size != bounded.size) persistence.save(SourceCapabilityCacheCodec.encode(bounded))
            return SourceCapabilityCache(maxRecords, persistence, bounded)
        }
    }

    suspend fun record(observation: SourceCapabilityObservation) = mutex.withLock {
        val source = observation.context.sourceId
        val old = records[source]
        // Wall clock rollback and racing old observations cannot replace newer evidence.
        if (old != null && observation.observedAt <= old.latest.observedAt) return@withLock
        val aliasTarget = old?.requiredAliasContext
        val successful = observation.health == ProbeHealth.SUCCESS && (aliasTarget == null || aliasTarget == observation.context)
        val record = SourceCapabilityRecord(observation, if (successful) observation else old?.lastSuccessful,
            if (successful) null else aliasTarget)
        val next = (records + (source to record)).values.sortedBy { it.latest.observedAt }.takeLast(maxRecords)
        save(next)
    }

    suspend fun lookup(context: SourceCapabilityContext, now: Long): SourceCapabilityView? = mutex.withLock {
        require(now >= 0)
        val record = records[context.sourceId] ?: return@withLock null
        val capability = record.lastSuccessful
        val freshness = when {
            record.aliasInvalidated -> CapabilityFreshness.ALIAS_INVALIDATED
            capability == null -> CapabilityFreshness.UNPROVEN
            capability.context != context -> CapabilityFreshness.CONTEXT_CHANGED
            capability.probeRevision != SourceCapabilityProbe.REVISION -> CapabilityFreshness.PROBE_CHANGED
            now < record.latest.observedAt -> CapabilityFreshness.CLOCK_ROLLBACK
            now >= capability.expiresAt -> CapabilityFreshness.EXPIRED
            else -> CapabilityFreshness.FRESH
        }
        SourceCapabilityView(record, freshness)
    }

    suspend fun invalidateForAuthorizedAlias(previous: SourceCapabilityContext, next: SourceCapabilityContext,
        proofId: String, proofs: CatalogIdentityProofs): Boolean = mutex.withLock {
        if (previous.sourceId != next.sourceId || previous.endpoint == next.endpoint ||
            !proofs.alias(proofId, previous.sourceId, previous.endpoint, next.endpoint)) return@withLock false
        val record = records[previous.sourceId] ?: return@withLock false
        if (record.latest.context != previous) return@withLock false
        save((records + (previous.sourceId to record.copy(requiredAliasContext = next))).values.toList())
        true
    }

    private suspend fun save(next: List<SourceCapabilityRecord>) {
        val encoded = SourceCapabilityCacheCodec.encode(next)
        // Freeze all caller-supplied collections before publishing or crossing the persistence boundary.
        val frozen = SourceCapabilityCacheCodec.decode(encoded)
        persistence.save(encoded)
        records = frozen.associateBy { it.latest.context.sourceId }
    }
}

internal object SourceCapabilityCacheCodec : JsonStorageCodec<List<SourceCapabilityRecord>>("source_capability_cache") {
    const val MAX_RECORDS = 256
    private const val MAX_SNAPSHOT_CHARS = 1_048_576
    override fun toJson(value: List<SourceCapabilityRecord>): JsonObject {
        require(value.size <= MAX_RECORDS && value.map { it.latest.context.sourceId }.distinct().size == value.size)
        val json = buildJsonObject {
            put("records", JsonArray(value.sortedBy { it.latest.context.sourceId.value }.map { record -> buildJsonObject {
                put("latest", observationJson(record.latest))
                put("lastSuccessful", record.lastSuccessful?.let(::observationJson) ?: JsonNull)
                put("requiredAliasContext", record.requiredAliasContext?.let(::contextJson) ?: JsonNull)
            } }))
        }
        require(json.storageJson().length <= MAX_SNAPSHOT_CHARS)
        return json
    }
    override fun fromJson(value: JsonObject): List<SourceCapabilityRecord> {
        require(value.storageJson().length <= MAX_SNAPSHOT_CHARS)
        val rows = value.required("records").jsonArray
        require(rows.size <= MAX_RECORDS)
        val records = rows.map { row -> row.jsonObject.let {
            SourceCapabilityRecord(observation(it.required("latest").jsonObject),
                it.required("lastSuccessful").nullable { item -> observation(item.jsonObject) },
                it.required("requiredAliasContext").nullable { item -> context(item.jsonObject) })
        } }
        require(records.map { it.latest.context.sourceId }.distinct().size == records.size)
        return Collections.unmodifiableList(records)
    }

    private fun contextJson(value: SourceCapabilityContext) = buildJsonObject {
        put("sourceId", value.sourceId.value); put("endpoint", value.endpoint)
        put("adapterRevision", value.adapterRevision); put("protocolRevision", value.protocolRevision)
        put("authorizationRevision", value.authorizationRevision)
    }
    private fun context(value: JsonObject) = SourceCapabilityContext(SourceId(value.required("sourceId").text()),
        value.required("endpoint").text(), value.required("adapterRevision").text(),
        value.required("protocolRevision").text(), value.required("authorizationRevision").text())
    private fun keysJson(value: Set<SourceItemKey>) = JsonArray(value.sortedBy { it.itemId.value }.map { buildJsonObject {
        put("sourceId", it.sourceId.value); put("itemId", it.itemId.value)
    } })
    private fun keys(value: JsonElement): Set<SourceItemKey> {
        val rows = value.jsonArray
        require(rows.size <= SourceCapabilityProbe.MAX_SAMPLE_ITEMS)
        val keys = rows.map { SourceItemKey(SourceId(it.jsonObject.required("sourceId").text()), SourceItemId(it.jsonObject.required("itemId").text())) }
        require(keys.distinct().size == keys.size)
        return Collections.unmodifiableSet(keys.toSet())
    }
    private fun observationJson(value: SourceCapabilityObservation) = buildJsonObject {
        put("context", contextJson(value.context)); put("observedAt", value.observedAt); put("expiresAt", value.expiresAt)
        put("probeRevision", value.probeRevision); put("health", value.health.name)
        put("requestedYear", intJson(value.requestedYear)); put("requestedCategory", value.requestedCategory?.let { buildJsonObject {
            put("sourceId", it.sourceId.value); put("categoryId", it.categoryId)
        } } ?: JsonNull)
        put("fields", buildJsonObject { value.fields.toSortedMap().forEach { (field, counts) -> put(field, buildJsonObject {
            put("present", counts.present); put("nonNull", counts.nonNull); put("sampleSize", counts.sampleSize)
        }) } })
        put("yearFiltering", value.yearFiltering.name); put("categoryFiltering", value.categoryFiltering.name)
        put("sampleItems", keysJson(value.sampleItems)); put("controlItems", keysJson(value.controlItems))
    }
    private fun observation(value: JsonObject) = SourceCapabilityObservation(
        context(value.required("context").jsonObject), value.required("observedAt").longNumber(), value.required("expiresAt").longNumber(),
        value.required("probeRevision").text(), ProbeHealth.valueOf(value.required("health").text()),
        value.required("requestedYear").nullable { it.integer() }, value.required("requestedCategory").nullable {
            SourceCategoryKey(SourceId(it.jsonObject.required("sourceId").text()), it.jsonObject.required("categoryId").text()) },
        Collections.unmodifiableMap(value.required("fields").jsonObject.mapValues { (_, counts) -> counts.jsonObject.let {
            RawFieldAvailability(it.required("present").integer(), it.required("nonNull").integer(), it.required("sampleSize").integer()) } }),
        RemoteFilterEvidence.valueOf(value.required("yearFiltering").text()), RemoteFilterEvidence.valueOf(value.required("categoryFiltering").text()),
        keys(value.required("sampleItems")), keys(value.required("controlItems")))
}
