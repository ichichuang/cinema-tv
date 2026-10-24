package com.ichichuang.cinema.data.subscription

import androidx.room.withTransaction
import com.ichichuang.cinema.data.local.CinemaDatabase
import com.ichichuang.cinema.data.local.codec.Utf16KeyConverter
import com.ichichuang.cinema.data.local.entity.CandidateSubscriptionStateEntity
import com.ichichuang.cinema.domain.model.catalog.SourceId
import com.ichichuang.cinema.domain.model.subscription.SubscriptionRefreshRequest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** Read and replacement are one real transaction. A throwing transform must preserve the exact old row. */
internal interface SubscriptionPersistence {
    suspend fun read(catalogId: String): String?
    suspend fun update(catalogId: String, transform: (String?) -> String): Unit
}
internal class RoomSubscriptionPersistence(private val database: CinemaDatabase) : SubscriptionPersistence {
    private val converter = Utf16KeyConverter()
    override suspend fun read(catalogId: String): String? = database.querySubscription().subscription(converter.encode(catalogId))?.payload
    override suspend fun update(catalogId: String, transform: (String?) -> String) {
        database.withTransaction {
            val old = database.querySubscription().subscription(converter.encode(catalogId))?.payload
            if (old == null && database.querySubscription().subscriptionCount() >= 64) reject(SubscriptionFailure.STORAGE)
            val payload = transform(old)
            currentCoroutineContext().ensureActive()
            database.querySubscription().subscription(CandidateSubscriptionStateEntity(catalogId, payload))
            currentCoroutineContext().ensureActive()
        }
    }
}

internal data class CandidateConsent(
    val revision: Long, val catalogRevision: Long, val digest: String, val origin: String,
    val selected: Set<SourceId>, val grantedAt: Long,
)
internal data class CandidateConfiguredSource(val source: DirectorySource, val enabled: Boolean, val removed: Boolean)
internal data class CandidateConfiguration(val revision: Long, val digest: String, val origin: String,
    val acceptedAt: Long, val sources: List<CandidateConfiguredSource>)
internal data class CandidateSubscriptionSnapshot(
    val consentSequence: Long = -1, val consent: CandidateConsent? = null,
    val configuration: CandidateConfiguration? = null, val disabled: Set<SourceId> = emptySet(),
    val clockHighWater: Long = 0,
)

/** Explicit owner of a candidate store, unbound from startup/DI. No method fetches source endpoints. */
internal class CandidateSubscriptionStore(private val persistence: SubscriptionPersistence, private val clock: () -> Long) {
    private val gate = Mutex()
    private val generations = mutableMapOf<String, Long>()
    suspend fun snapshot(catalogId: String): CandidateSubscriptionSnapshot = decode(persistence.read(catalogId))
    suspend fun raw(catalogId: String): String? = persistence.read(catalogId)

    /** The controlled caller invokes this only after actual user review of this exact verified directory/selection. */
    suspend fun grantVerifiedConsent(directory: VerifiedDirectory, revision: Long, selected: Set<SourceId>) {
        val frozen = selected.toSet()
        gate.withLock {
            persistence.update(directory.catalogId) { raw ->
                val previous = decode(raw); val now = clock()
                validTime(directory, now, previous)
                if (revision < 0 || revision <= previous.consentSequence) reject(SubscriptionFailure.STALE_CONSENT)
                if (!directory.sources.map { it.id }.toSet().containsAll(frozen)) reject(SubscriptionFailure.STALE_CONSENT)
                encode(previous.copy(clockHighWater = now, consentSequence = revision, consent = CandidateConsent(revision, directory.revision,
                    directory.digest, directory.origin, frozen, now)))
            }
        }
    }
    suspend fun revokeConsent(catalogId: String, revision: Long) = gate.withLock {
        persistence.update(catalogId) { raw ->
            val previous = decode(raw); val now = clock()
            if (now < previous.clockHighWater) reject(SubscriptionFailure.CLOCK_ROLLBACK)
            if (revision <= previous.consentSequence) reject(SubscriptionFailure.STALE_CONSENT)
            encode(previous.copy(clockHighWater = now, consentSequence = revision, consent = null))
        }
    }
    /** A tombstone survives refresh and directory removal; refresh has no authority to re-enable it. */
    suspend fun disableSources(catalogId: String, sourceIds: Set<SourceId>) {
        val ids = sourceIds.toSet()
        gate.withLock { persistence.update(catalogId) { raw ->
            val previous = decode(raw)
            encode(previous.copy(disabled = previous.disabled + ids, configuration = previous.configuration?.let { config ->
                config.copy(sources = config.sources.map { if (it.source.id in ids) it.copy(enabled = false) else it })
            }))
        } }
    }
    internal suspend fun begin(catalogId: String): Long = gate.withLock {
        if (generations.size >= 64 && catalogId !in generations) reject(SubscriptionFailure.STORAGE)
        val token = (generations[catalogId] ?: 0) + 1
        generations[catalogId] = token; token
    }
    internal suspend fun commit(request: SubscriptionRefreshRequest, directory: VerifiedDirectory, token: Long): Long = gate.withLock {
        currentCoroutineContext().ensureActive()
        if (generations[request.catalogId] != token) reject(SubscriptionFailure.SUPERSEDED)
        persistence.update(request.catalogId) { raw ->
            val previous = decode(raw); val now = clock()
            validTime(directory, now, previous)
            val consent = previous.consent ?: reject(SubscriptionFailure.CONSENT_REQUIRED)
            if (request.catalogId != directory.catalogId || consent.revision != request.consentRevision ||
                consent.catalogRevision != directory.revision || consent.digest != directory.digest ||
                consent.origin != directory.origin || consent.selected != request.enabledSourceIds)
                reject(SubscriptionFailure.STALE_CONSENT)
            if (previous.configuration != null && directory.revision <= previous.configuration.revision) reject(SubscriptionFailure.REPLAY)
            val present = directory.sources.map { it.id }.toSet()
            if (!present.containsAll(consent.selected)) reject(SubscriptionFailure.STALE_CONSENT)
            val disabled = previous.disabled + previous.configuration?.sources.orEmpty()
                .filter { !it.enabled }.map { it.source.id }
            val sources = directory.sources.map { source -> CandidateConfiguredSource(source,
                source.id in consent.selected && source.id !in disabled, false) } +
                previous.configuration?.sources.orEmpty().filter { it.source.id !in present }
                    .map { it.copy(enabled = false, removed = true) }
            encode(previous.copy(clockHighWater = now, disabled = disabled + sources.filter { !it.enabled }.map { it.source.id },
                configuration = CandidateConfiguration(directory.revision, directory.digest, directory.origin, now, sources)))
        }
        directory.revision
    }
    private fun validTime(directory: VerifiedDirectory, now: Long, previous: CandidateSubscriptionSnapshot) {
        if (now < 0 || now < previous.clockHighWater || now < (previous.configuration?.acceptedAt ?: 0) || now < (previous.consent?.grantedAt ?: 0))
            reject(SubscriptionFailure.CLOCK_ROLLBACK)
        if (directory.issuedAt > now || directory.expiresAt <= now) reject(SubscriptionFailure.EXPIRED)
    }
    companion object {
        internal fun encode(state: CandidateSubscriptionSnapshot): String {
            if (state.disabled.size > 128 || (state.configuration?.sources?.size ?: 0) > 128) reject(SubscriptionFailure.STORAGE)
            val encoded = buildJsonObject {
                put("kind", "candidate-subscription"); put("version", 1); put("consentSequence", state.consentSequence)
                put("clockHighWater", state.clockHighWater)
                put("consent", state.consent?.let { consent -> buildJsonObject {
                    put("revision", consent.revision); put("catalogRevision", consent.catalogRevision)
                    put("digest", consent.digest); put("origin", consent.origin); put("grantedAt", consent.grantedAt)
                    put("selected", buildJsonArray { consent.selected.sortedBy { it.value }.forEach { add(it.value) } })
                } } ?: JsonNull)
                put("configuration", state.configuration?.let { config -> buildJsonObject {
                    put("revision", config.revision); put("digest", config.digest); put("origin", config.origin); put("acceptedAt", config.acceptedAt)
                    put("sources", buildJsonArray { config.sources.sortedBy { it.source.id.value }.forEach { source -> add(buildJsonObject {
                        put("id", source.source.id.value); put("name", source.source.name); put("endpoint", source.source.endpoint)
                        put("enabled", source.enabled); put("removed", source.removed)
                    }) } })
                } } ?: JsonNull)
                put("disabled", buildJsonArray { state.disabled.sortedBy { it.value }.forEach { add(it.value) } })
            }.toString()
            if (encoded.toByteArray(Charsets.UTF_8).size > DirectoryValidator.MAX_BYTES) reject(SubscriptionFailure.STORAGE)
            return encoded
        }
        internal fun decode(raw: String?): CandidateSubscriptionSnapshot {
            if (raw == null) return CandidateSubscriptionSnapshot()
            try {
                if (raw.length > DirectoryValidator.MAX_BYTES) reject(SubscriptionFailure.STORAGE)
                val root = DirectoryJson.parse(raw.toByteArray(Charsets.UTF_8))
                root.fields("kind", "version", "consentSequence", "consent", "configuration", "disabled", "clockHighWater")
                if (root.string("kind") != "candidate-subscription" || root.number("version") != 1L) reject(SubscriptionFailure.STORAGE)
                fun ids(value: JsonElement?): Set<SourceId> {
                    val list = value as? JsonArray ?: reject(SubscriptionFailure.STORAGE)
                    val ids = list.map { val p = it as? JsonPrimitive ?: reject(SubscriptionFailure.STORAGE)
                        if (!p.isString) reject(SubscriptionFailure.STORAGE); SourceId(p.content) }
                    if (ids.toSet().size != ids.size) reject(SubscriptionFailure.STORAGE)
                    return ids.toSet()
                }
                val consent = root["consent"].takeUnless { it == JsonNull }?.let {
                    val value = it as? JsonObject ?: reject(SubscriptionFailure.STORAGE)
                    value.fields("revision", "catalogRevision", "digest", "origin", "grantedAt", "selected")
                    CandidateConsent(value.number("revision"), value.number("catalogRevision"), value.string("digest"),
                        value.string("origin"), ids(value["selected"]), value.number("grantedAt"))
                }
                val config = root["configuration"].takeUnless { it == JsonNull }?.let {
                    val value = it as? JsonObject ?: reject(SubscriptionFailure.STORAGE)
                    value.fields("revision", "digest", "origin", "acceptedAt", "sources")
                    val sources = (value["sources"] as? JsonArray ?: reject(SubscriptionFailure.STORAGE)).map { element ->
                        val source = element as? JsonObject ?: reject(SubscriptionFailure.STORAGE)
                        source.fields("id", "name", "endpoint", "enabled", "removed")
                        CandidateConfiguredSource(DirectorySource(SourceId(source.string("id")), source.string("name"), source.string("endpoint")),
                            (source["enabled"] as? JsonPrimitive)?.booleanOrNull ?: reject(SubscriptionFailure.STORAGE),
                            (source["removed"] as? JsonPrimitive)?.booleanOrNull ?: reject(SubscriptionFailure.STORAGE))
                    }
                    if (sources.map { it.source.id }.toSet().size != sources.size) reject(SubscriptionFailure.STORAGE)
                    CandidateConfiguration(value.number("revision"), value.string("digest"), value.string("origin"), value.number("acceptedAt"), sources)
                }
                val sequence = root.number("consentSequence")
                if (sequence < -1 || consent != null && (consent.revision < 0 || consent.revision != sequence || consent.catalogRevision < 0) ||
                    config != null && config.revision < 0) reject(SubscriptionFailure.STORAGE)
                val highWater = root.number("clockHighWater")
                if (highWater < 0 || consent != null && (consent.grantedAt < 0 || consent.grantedAt > highWater) ||
                    config != null && (config.acceptedAt < 0 || config.acceptedAt > highWater)) reject(SubscriptionFailure.STORAGE)
                return CandidateSubscriptionSnapshot(sequence, consent, config, ids(root["disabled"]), highWater)
            } catch (_: SubscriptionException) { reject(SubscriptionFailure.STORAGE) }
            catch (_: Exception) { reject(SubscriptionFailure.STORAGE) }
        }
    }
}
