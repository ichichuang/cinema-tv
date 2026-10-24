package com.ichichuang.cinema.data.catalog

import com.ichichuang.cinema.domain.model.catalog.SourceId
import com.ichichuang.cinema.domain.model.catalog.SourceItemId
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic metadata and an in-memory persistence boundary only. No network or media. */
class SourceCapabilityTest {
    private val source = SourceId("synthetic-A")
    private val context = SourceCapabilityContext(source, "synthetic:endpoint-A", "adapter-A", "protocol-A", "consent-A")
    private fun capture(id: String, year: String? = null, category: String? = null, sourceId: SourceId = source) =
        MaccmsCatalogNormalizer.normalize(sourceId, buildJsonObject {
            put("vod_id", id)
            year?.let { put("vod_year", it) }
            category?.let { put("type_id", it) }
        }, 100)
    private fun probe(year: Int? = 1994, rows: List<NormalizedCatalogCapture> = listOf(capture("1", "1994")),
        control: List<NormalizedCatalogCapture> = listOf(capture("2", "1995")),
        at: Long = 100, ctx: SourceCapabilityContext = context, health: ProbeHealth = ProbeHealth.SUCCESS,
        category: SourceCategoryKey? = null) = SourceCapabilityProbe.observe(ctx, at, 50, rows, year, category, health, control)
    private class MemoryPersistence : SourceCapabilityPersistence {
        var snapshot: String? = null
        var fail = false
        override suspend fun load() = snapshot
        override suspend fun save(snapshot: String) { check(!fail); this.snapshot = snapshot }
    }

    @Test fun ignored_year_is_contradicted_while_raw_year_is_supplied() {
        val result = probe(rows = listOf(capture("1", "1995"), capture("2")))
        assertEquals(RemoteFilterEvidence.CONTRADICTED, result.yearFiltering)
        assertEquals(RawFieldAvailability(1, 1, 2), result.fields["vod_year"])
        assertEquals(setOf(SourceItemKey(source, SourceItemId("1")), SourceItemKey(source, SourceItemId("2"))), result.sampleItems)
    }
    @Test fun matching_sample_requires_control_counterexample_and_unknown_never_proves_filtering() {
        assertEquals(RemoteFilterEvidence.OBSERVED_ON_SAMPLE, probe().yearFiltering)
        assertEquals(RemoteFilterEvidence.UNKNOWN, probe(control = emptyList()).yearFiltering)
        assertEquals(RemoteFilterEvidence.UNKNOWN, probe(rows = listOf(capture("1"))).yearFiltering)
        assertEquals(RemoteFilterEvidence.UNKNOWN, probe(rows = emptyList()).yearFiltering)
    }
    @Test fun genuine_category_ids_are_source_scoped_and_ignored_category_is_contradicted() {
        val key = SourceCategoryKey(source, "6")
        val good = probe(null, listOf(capture("1", category = "6")), listOf(capture("2", category = "7")), category = key)
        assertEquals(RemoteFilterEvidence.OBSERVED_ON_SAMPLE, good.categoryFiltering)
        assertEquals(key, good.requestedCategory)
        assertNotEquals(key, SourceCategoryKey(SourceId("synthetic-B"), "6"))
        assertEquals(RemoteFilterEvidence.CONTRADICTED, probe(null, listOf(capture("1", category = "7")), category = key).categoryFiltering)
        assertEquals(RemoteFilterEvidence.UNKNOWN, probe(null, emptyList(), category = key).categoryFiltering)
    }
    @Test fun null_empty_and_missing_fields_remain_distinct_from_filter_support() {
        val row = MaccmsCatalogNormalizer.normalize(source,
            Json.parseToJsonElement("""{"vod_id":"1","vod_year":null,"vod_area":""}""").jsonObject, 100)
        val result = probe(rows = listOf(row))
        assertEquals(RawFieldAvailability(1, 0, 1), result.fields["vod_year"])
        assertEquals(RawFieldAvailability(1, 1, 1), result.fields["vod_area"])
        assertNull(result.fields["vod_lang"])
        assertEquals(RemoteFilterEvidence.UNKNOWN, result.yearFiltering)
    }
    @Test fun fresh_capability_expires_and_revision_or_endpoint_changes_do_not_reuse_claims() = runBlocking {
        val cache = SourceCapabilityCache.open(2, MemoryPersistence())
        cache.record(probe())
        assertEquals(CapabilityFreshness.FRESH, cache.lookup(context, 120)!!.freshness)
        assertEquals(CapabilityFreshness.EXPIRED, cache.lookup(context, 150)!!.freshness)
        assertEquals(CapabilityFreshness.CLOCK_ROLLBACK, cache.lookup(context, 99)!!.freshness)
        for (changed in listOf(context.copy(endpoint = "synthetic:endpoint-B"), context.copy(adapterRevision = "adapter-B"),
            context.copy(protocolRevision = "protocol-B"), context.copy(authorizationRevision = "consent-B"))) {
            assertEquals(CapabilityFreshness.CONTEXT_CHANGED, cache.lookup(changed, 120)!!.freshness)
        }
    }
    @Test fun timeout_updates_health_without_erasing_or_extending_previous_proof() = runBlocking {
        val cache = SourceCapabilityCache.open(2, MemoryPersistence())
        cache.record(probe())
        cache.record(probe(at = 140, health = ProbeHealth.TIMEOUT, rows = emptyList(), control = emptyList()))
        val view = cache.lookup(context, 145)!!
        assertEquals(ProbeHealth.TIMEOUT, view.record.latest.health)
        assertEquals(RemoteFilterEvidence.OBSERVED_ON_SAMPLE, view.record.lastSuccessful!!.yearFiltering)
        assertEquals(150L, view.record.lastSuccessful.expiresAt)
        assertEquals(CapabilityFreshness.EXPIRED, cache.lookup(context, 151)!!.freshness)
    }
    @Test fun authorized_alias_keeps_identity_but_requires_new_probe_and_similarity_never_authorizes() = runBlocking {
        val cache = SourceCapabilityCache.open(2, MemoryPersistence())
        cache.record(probe())
        val next = context.copy(endpoint = "synthetic:endpoint-A-new")
        assertFalse(cache.invalidateForAuthorizedAlias(context, next, "missing", CatalogIdentityProofs()))
        assertEquals(CapabilityFreshness.FRESH, cache.lookup(context, 120)!!.freshness)
        val proofs = CatalogIdentityProofs(aliases = mapOf("proof-A" to VerifiedSourceAlias(source, context.endpoint, next.endpoint)))
        assertTrue(cache.invalidateForAuthorizedAlias(context, next, "proof-A", proofs))
        assertEquals(CapabilityFreshness.ALIAS_INVALIDATED, cache.lookup(context, 120)!!.freshness)
        assertEquals(CapabilityFreshness.ALIAS_INVALIDATED, cache.lookup(next, 120)!!.freshness)
        cache.record(probe(at = 130, ctx = next))
        assertEquals(CapabilityFreshness.FRESH, cache.lookup(next, 140)!!.freshness)
    }
    @Test fun cache_is_bounded_and_roundtrips_health_fields_identity_and_expiry() = runBlocking {
        val persistence = MemoryPersistence()
        val cache = SourceCapabilityCache.open(1, persistence)
        cache.record(probe())
        cache.record(probe(at = 130, health = ProbeHealth.TIMEOUT, rows = emptyList(), control = emptyList()))
        val reopened = SourceCapabilityCache.open(1, persistence)
        assertEquals(cache.lookup(context, 140), reopened.lookup(context, 140))
        assertNotNull(reopened.lookup(context, 140))
        val other = context.copy(sourceId = SourceId("synthetic-B"))
        reopened.record(probe(at = 140, ctx = other, rows = emptyList(), control = emptyList()))
        assertNull(reopened.lookup(context, 145))
        assertNotNull(SourceCapabilityCache.open(1, persistence).lookup(other, 145))
    }
    @Test fun failed_persistence_does_not_publish_uncommitted_capability() = runBlocking {
        val persistence = MemoryPersistence()
        val cache = SourceCapabilityCache.open(2, persistence)
        cache.record(probe())
        persistence.fail = true
        var failed = false
        try { cache.record(probe(at = 130)) } catch (_: IllegalStateException) { failed = true }
        assertTrue(failed)
        assertEquals(100L, cache.lookup(context, 140)!!.record.latest.observedAt)
    }
    @Test fun failed_first_probe_is_unproven_and_older_observation_cannot_replace_newer_evidence() = runBlocking {
        val cache = SourceCapabilityCache.open(2, MemoryPersistence())
        cache.record(probe(health = ProbeHealth.TIMEOUT, rows = emptyList(), control = emptyList()))
        assertEquals(CapabilityFreshness.UNPROVEN, cache.lookup(context, 110)!!.freshness)
        cache.record(probe(at = 130))
        cache.record(probe(at = 120, rows = listOf(capture("3", "1995"))))
        assertEquals(130L, cache.lookup(context, 140)!!.record.latest.observedAt)
        assertEquals(RemoteFilterEvidence.OBSERVED_ON_SAMPLE, cache.lookup(context, 140)!!.record.lastSuccessful!!.yearFiltering)
    }
    @Test fun source_mismatch_and_future_sample_are_rejected_before_claims_are_created() {
        for (rows in listOf(listOf(capture("1", "1994", sourceId = SourceId("synthetic-B"))),
            listOf(capture("1", "1994").copy(observedAt = 101)))) {
            var rejected = false
            try { probe(rows = rows) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        }
    }
    @Test fun invalidated_alias_survives_reopen_and_old_endpoint_probe_cannot_restore_it() = runBlocking {
        val persistence = MemoryPersistence()
        val cache = SourceCapabilityCache.open(2, persistence)
        cache.record(probe())
        val next = context.copy(endpoint = "synthetic:endpoint-B")
        val proofs = CatalogIdentityProofs(aliases = mapOf("proof" to VerifiedSourceAlias(source, context.endpoint, next.endpoint)))
        assertTrue(cache.invalidateForAuthorizedAlias(context, next, "proof", proofs))
        val reopened = SourceCapabilityCache.open(2, persistence)
        reopened.record(probe(at = 130))
        assertEquals(CapabilityFreshness.ALIAS_INVALIDATED, reopened.lookup(next, 140)!!.freshness)
        reopened.record(probe(at = 135, ctx = next))
        assertEquals(CapabilityFreshness.FRESH, reopened.lookup(next, 140)!!.freshness)
    }

    @Test fun control_with_conflicting_year_for_same_item_does_not_prove_server_filtering() {
        assertEquals(RemoteFilterEvidence.UNKNOWN, probe(control = listOf(capture("1", "1995"))).yearFiltering)
    }
    @Test fun inconsistent_capability_payload_is_rejected_instead_of_restored_as_fresh() {
        val encoded = SourceCapabilityCacheCodec.encode(listOf(SourceCapabilityRecord(probe(), probe())))
        val corrupted = encoded.replace("\"requestedYear\":1994", "\"requestedYear\":null")
        var rejected = false
        try { SourceCapabilityCacheCodec.decode(corrupted) } catch (_: com.ichichuang.cinema.data.local.codec.StorageReadException) { rejected = true }
        assertTrue(rejected)
    }

    @Test fun restored_positive_filter_with_no_distinct_control_item_is_rejected() = runBlocking {
        assertCorruptedSnapshotRejected { observation ->
            JsonObject(observation + ("controlItems" to observation.getValue("sampleItems")))
        }
    }
    @Test fun restored_observation_requesting_both_filter_dimensions_is_rejected() = runBlocking {
        assertCorruptedSnapshotRejected { observation ->
            JsonObject(observation + mapOf(
                "requestedCategory" to buildJsonObject { put("sourceId", "synthetic-A"); put("categoryId", "6") },
                "categoryFiltering" to JsonPrimitive("OBSERVED_ON_SAMPLE")))
        }
    }
    @Test fun reopened_cache_does_not_revive_expired_proof_after_rollback_behind_later_timeout() = runBlocking {
        val persistence = MemoryPersistence()
        val cache = SourceCapabilityCache.open(2, persistence)
        cache.record(probe()) // Successful observation at 100, expires at 150.
        cache.record(probe(at = 200, health = ProbeHealth.TIMEOUT, rows = emptyList(), control = emptyList()))
        val reopened = SourceCapabilityCache.open(2, persistence)
        assertEquals(CapabilityFreshness.CLOCK_ROLLBACK, reopened.lookup(context, 125)!!.freshness)
        assertEquals(CapabilityFreshness.EXPIRED, reopened.lookup(context, 225)!!.freshness)
        assertEquals(150L, reopened.lookup(context, 225)!!.record.lastSuccessful!!.expiresAt)
    }
    private suspend fun assertCorruptedSnapshotRejected(rewrite: (JsonObject) -> JsonObject) {
        val encoded = SourceCapabilityCacheCodec.encode(listOf(SourceCapabilityRecord(probe(), probe())))
        val envelope = Json.parseToJsonElement(encoded).jsonObject
        val value = envelope.getValue("value").jsonObject
        val records = value.getValue("records").jsonArray.map { element ->
            val record = element.jsonObject
            JsonObject(record + mapOf("latest" to rewrite(record.getValue("latest").jsonObject),
                "lastSuccessful" to rewrite(record.getValue("lastSuccessful").jsonObject)))
        }
        val persistence = MemoryPersistence()
        persistence.snapshot = JsonObject(envelope + ("value" to JsonObject(value + ("records" to JsonArray(records))))).toString()
        var restored: SourceCapabilityView? = null
        var rejected = false
        try {
            val reopened = SourceCapabilityCache.open(2, persistence)
            restored = reopened.lookup(context, 120)
        } catch (_: com.ichichuang.cinema.data.local.codec.StorageReadException) { rejected = true }
        assertTrue("Corrupted observation must be rejected, restored freshness was ${restored?.freshness}", rejected)
    }

}
