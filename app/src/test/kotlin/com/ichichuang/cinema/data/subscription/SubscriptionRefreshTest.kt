package com.ichichuang.cinema.data.subscription

import com.ichichuang.cinema.domain.model.catalog.SourceId
import com.ichichuang.cinema.domain.model.subscription.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.security.KeyPairGenerator
import java.security.Signature

class SubscriptionRefreshTest {
    companion object {
        private val key by lazy { KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair() }
        private const val CAT = "synthetic-catalog"
        private const val ORIGIN = "https://synthetic-directory.example"
        private const val URL = "$ORIGIN/directory.json"
        private const val PUBLISHER = "synthetic-owner"
        private val source = DirectorySource(SourceId("synthetic-stable-source"), "合成测试来源", "https://synthetic-source.example/api")
        private val publicIp = InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))
        private fun signed(revision: Long = 1, issued: Long = 1_000, expires: Long = 100_000,
            sources: List<DirectorySource> = listOf(source)): ByteArray {
            val payload = DirectoryValidator.canonical(CAT, ORIGIN, PUBLISHER, revision, issued, expires, sources)
            val signature = Signature.getInstance("SHA256withRSA").apply { initSign(key.private); update(payload) }.sign()
            val obj = Json.parseToJsonElement(payload.decodeToString()).jsonObject
            return JsonObject(obj + ("signature" to JsonPrimitive(DirectoryValidator.hex(signature)))).toString().toByteArray()
        }
        private fun validator() = DirectoryValidator(listOf(CatalogTrustRule(CAT, ORIGIN, PUBLISHER, key.public)))
        private fun request(revision: Long? = 1, enabled: Set<SourceId> = setOf(source.id)) =
            SubscriptionRefreshRequest.create(RefreshReason.USER_REQUEST, CAT, enabled, revision)
    }
    private class Memory : SubscriptionPersistence {
        var row: String? = null
        val gate = Mutex()
        var fail = false
        override suspend fun read(catalogId: String) = gate.withLock { row }
        override suspend fun update(catalogId: String, transform: (String?) -> String) = gate.withLock {
            val next = transform(row)
            if (fail) {
                val location = "credential https://secret.invalid/"
                val query = "?token=secret"
                throw java.io.IOException(location + query)
            }
            row = next
        }
    }
    private class Fixture {
        var now = 10_000L
        var response = signed()
        var exchange: suspend () -> DirectoryResponse = { DirectoryResponse(200, response) }
        var executions = 0
        val memory = Memory()
        val store = CandidateSubscriptionStore(memory) { now }
        val manager = SubscriptionRefreshCoordinator(store, validator(), PublicDirectoryFetcher(
            DirectoryResolver { listOf(publicIp) }, DirectoryExchange { _, pins, _ ->
                assertEquals(listOf(publicIp), pins); executions++; exchange()
            }), mapOf(CAT to URL), { now })
        suspend fun consent(revision: Long = 1, enabled: Set<SourceId> = setOf(source.id)) {
            val verified = (manager.inspect(CAT) as DirectoryValidation.Trusted).directory
            store.grantVerifiedConsent(verified, revision, enabled)
        }
        suspend fun refresh(req: SubscriptionRefreshRequest = request()) = manager.refresh(req).toList()
    }
    private fun failure(expected: SubscriptionFailure, block: () -> Unit) {
        try { block(); fail("Expected ${expected.name}") }
        catch (e: SubscriptionException) { assertEquals(expected, e.failure); assertEquals(expected.name, e.message); assertNull(e.cause) }
    }

    @Test fun exact_empty_default_reports_empty_without_trust_or_write() = runBlocking {
        val f = Fixture(); f.response = "{\"sources\":[]}".toByteArray()
        assertEquals(listOf(SubscriptionState.Checking, SubscriptionState.CatalogEmpty), f.refresh())
        assertNull(f.memory.row)
    }
    @Test fun valid_signed_fixture_needs_real_consent_then_configures_candidate_only() = runBlocking {
        val f = Fixture()
        assertEquals(SubscriptionState.Failed("CONSENT_REQUIRED"), f.refresh(request(null)).last())
        assertNull(f.memory.row)
        f.consent()
        val before = f.store.snapshot(CAT)
        assertNotNull(before.consent); assertNull(before.configuration)
        assertEquals(listOf(SubscriptionState.Checking, SubscriptionState.CandidatesFound(1), SubscriptionState.Saving,
            SubscriptionState.Configured(1)), f.refresh())
        val configured = f.store.snapshot(CAT)
        assertEquals(before.consent, configured.consent)
        assertEquals(source.id, configured.configuration!!.sources.single().source.id)
        assertTrue(configured.configuration.sources.single().enabled)
    }
    @Test fun malformed_duplicate_script_executable_bad_utf8_depth_and_oversize_are_rejected() {
        val scriptLocation = "https://secret.invalid/"
        val scriptQuery = "?token=hidden"
        val script = "{\"sources\":[],\"script\":\"${scriptLocation + scriptQuery}\"}"
        listOf("<script>alert('secret')</script>", "{\"sources\":[],\"sources\":[]}",
            "{\"sources\":[],\"\\u0073ources\":[]}", script,
            "{\"sources\":${"[".repeat(20)}${"]".repeat(20)}}", "{\"sources\":[],\"signature\":3}")
            .forEach { raw -> failure(SubscriptionFailure.MALFORMED) { validator().validate(raw.toByteArray(), CAT, ORIGIN, 10_000) } }
        failure(SubscriptionFailure.MALFORMED) { validator().validate(byteArrayOf(0xc3.toByte(), 0x28), CAT, ORIGIN, 10_000) }
        failure(SubscriptionFailure.TOO_LARGE) { validator().validate(ByteArray(65_537), CAT, ORIGIN, 10_000) }
    }
    @Test fun duplicate_ids_endpoints_and_unknown_source_fields_are_rejected() {
        val raw = signed(sources = listOf(source, source.copy(name = "不同名字")))
        failure(SubscriptionFailure.MALFORMED) { validator().validate(raw, CAT, ORIGIN, 10_000) }
        val duplicateEndpoint = signed(sources = listOf(source, source.copy(id = SourceId("synthetic-other"))))
        failure(SubscriptionFailure.MALFORMED) { validator().validate(duplicateEndpoint, CAT, ORIGIN, 10_000) }
        val text = signed().decodeToString().replace("\"name\":", "\"module\":\"x\",\"name\":")
        failure(SubscriptionFailure.MALFORMED) { validator().validate(text.toByteArray(), CAT, ORIGIN, 10_000) }
    }
    @Test fun owner_rule_binds_catalog_origin_publisher_and_strong_key() {
        failure(SubscriptionFailure.UNTRUSTED) { DirectoryValidator(emptyList()).validate(signed(), CAT, ORIGIN, 10_000) }
        failure(SubscriptionFailure.UNTRUSTED) { validator().validate(signed(), "other", ORIGIN, 10_000) }
        failure(SubscriptionFailure.UNTRUSTED) { validator().validate(signed(), CAT, "https://other.example", 10_000) }
        val weak = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        failure(SubscriptionFailure.UNTRUSTED) { DirectoryValidator(listOf(CatalogTrustRule(CAT, ORIGIN, PUBLISHER, weak.public)))
            .validate(signed(), CAT, ORIGIN, 10_000) }
        val changed = signed().decodeToString().replace("合成测试来源", "篡改")
        failure(SubscriptionFailure.SIGNATURE) { validator().validate(changed.toByteArray(), CAT, ORIGIN, 10_000) }
    }
    @Test fun signed_time_expiry_future_and_unbounded_validity_fail_closed() {
        listOf(signed(expires = 9_999), signed(issued = 11_000), signed(expires = Long.MAX_VALUE)).forEach {
            failure(SubscriptionFailure.EXPIRED) { validator().validate(it, CAT, ORIGIN, 10_000) }
        }
    }
    @Test fun replay_lower_changed_same_revision_and_clock_rollback_preserve_exact_row() = runBlocking {
        val f = Fixture(); f.consent(); f.refresh(); val old = f.memory.row
        assertEquals(SubscriptionState.Failed("REPLAY"), f.refresh().last()); assertEquals(old, f.memory.row)
        f.response = signed(revision = 0)
        assertEquals(SubscriptionState.Failed("STALE_CONSENT"), f.refresh().last()); assertEquals(old, f.memory.row)
        f.response = signed(sources = listOf(source.copy(name = "changed")))
        assertEquals(SubscriptionState.Failed("STALE_CONSENT"), f.refresh().last()); assertEquals(old, f.memory.row)
        f.response = signed(); f.now = 9_999
        assertEquals(SubscriptionState.Failed("CLOCK_ROLLBACK"), f.refresh().last()); assertEquals(old, f.memory.row)
    }
    @Test fun failure_and_empty_directory_preserve_prior_consent_config_and_redact_diagnostics() = runBlocking {
        val f = Fixture(); f.consent(); f.refresh(); val old = f.memory.row
        val location = "https://127.0.0.1/private"
        val query = "?token=secret response-body"
        f.exchange = { throw java.io.IOException(location + query) }
        val result = f.refresh().last(); assertEquals(SubscriptionState.Failed("NETWORK"), result)
        assertEquals(old, f.memory.row); assertFalse(result.toString().contains("secret"))
        f.exchange = { DirectoryResponse(200, "{\"sources\":[]}".toByteArray()) }
        assertEquals(SubscriptionState.CatalogEmpty, f.refresh().last()); assertEquals(old, f.memory.row)
    }
    @Test fun transactional_write_failure_preserves_exact_previous_row() = runBlocking {
        val f = Fixture(); f.consent(); val old = f.memory.row; f.memory.fail = true
        assertEquals(SubscriptionState.Failed("STORAGE"), f.refresh().last()); assertEquals(old, f.memory.row)
    }
    @Test fun disabled_tombstone_is_read_at_commit_and_removed_source_is_retained() = runBlocking {
        val f = Fixture(); f.consent()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        f.exchange = { entered.complete(Unit); withTimeout(2_000) { release.await() }; DirectoryResponse(200, f.response) }
        val work = async { f.refresh() }; withTimeout(2_000) { entered.await() }
        f.store.disableSources(CAT, setOf(source.id)); release.complete(Unit)
        assertEquals(SubscriptionState.Configured(1), work.await().last())
        assertFalse(f.store.snapshot(CAT).configuration!!.sources.single().enabled)
        val other = source.copy(id = SourceId("synthetic-other"), endpoint = "https://other-source.example/api")
        f.exchange = { DirectoryResponse(200, f.response) }; f.response = signed(2, sources = listOf(other))
        f.consent(2, setOf(other.id)); assertEquals(SubscriptionState.Configured(2), f.refresh(request(2, setOf(other.id))).last())
        val restored = f.store.snapshot(CAT)
        assertTrue(restored.configuration!!.sources.first { it.source.id == source.id }.removed)
        assertTrue(source.id in restored.disabled)
    }
    @Test fun consent_revoked_while_fetching_is_checked_at_atomic_commit() = runBlocking {
        val f = Fixture(); f.consent()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        f.exchange = { entered.complete(Unit); withTimeout(2_000) { release.await() }; DirectoryResponse(200, f.response) }
        val work = async { f.refresh() }; withTimeout(2_000) { entered.await() }
        f.store.revokeConsent(CAT, 2); val revoked = f.memory.row; release.complete(Unit)
        assertEquals(SubscriptionState.Failed("CONSENT_REQUIRED"), work.await().last()); assertEquals(revoked, f.memory.row)
    }
    @Test fun cancelled_refresh_propagates_and_preserves_row() = runBlocking {
        val f = Fixture(); f.consent(); val old = f.memory.row
        val entered = CompletableDeferred<Unit>(); var cancelled = false
        f.exchange = { entered.complete(Unit); try { awaitCancellation() } finally { cancelled = true } }
        val work = launch { f.refresh() }; withTimeout(2_000) { entered.await() }; work.cancelAndJoin()
        assertTrue(cancelled); assertEquals(old, f.memory.row)
    }
    @Test fun overlapping_older_response_cannot_commit_after_newer_selection() = runBlocking {
        val f = Fixture(); f.consent(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var n = 0
        f.exchange = { if (++n == 1) { entered.complete(Unit); withTimeout(2_000) { release.await() } }; DirectoryResponse(200, f.response) }
        val first = async { f.refresh() }; withTimeout(2_000) { entered.await() }
        assertEquals(SubscriptionState.Configured(1), f.refresh().last()); val newest = f.memory.row
        release.complete(Unit)
        assertEquals(SubscriptionState.Failed("SUPERSEDED"), first.await().last()); assertEquals(newest, f.memory.row)
    }
    @Test fun public_url_and_all_special_address_classes_never_execute_request() = runBlocking {
        var requests = 0
        val exchange = DirectoryExchange { _, _, _ -> requests++; DirectoryResponse(200, byteArrayOf()) }
        val blocked = listOf("0.1.2.3", "10.0.0.1", "100.64.0.1", "127.0.0.1", "169.254.169.254", "172.16.0.1", "192.168.0.1",
            "192.0.0.1", "192.0.2.1", "198.18.0.1", "198.51.100.1", "203.0.113.1", "224.0.0.1", "255.255.255.255",
            "::", "::1", "::ffff:127.0.0.1", "fc00::1", "fe80::1", "ff00::1", "2001:db8::1", "2002:7f00:1::")
        for (ip in blocked) {
            val address = InetAddress.getByName(ip)
            assertFalse(ip, PublicDirectoryPolicy.public(address))
            val fetcher = PublicDirectoryFetcher(DirectoryResolver { listOf(address) }, exchange)
            try { fetcher.fetch(URL); fail(ip) } catch (e: SubscriptionException) { assertEquals(SubscriptionFailure.DESTINATION, e.failure) }
        }
        val scheme = "https://"
        val credentials = "user:secret"
        val host = "@public.example/a"
        val queryLocation = "https://public.example/a"
        val query = "?token=secret"
        listOf("http://public.example/a", "https://public.example:444/a", scheme + credentials + host,
            queryLocation + query, "https://public.example/a#x", "https://127.0.0.1/a", "https://[::ffff:127.0.0.1]/a",
            "https://host.local/a", "https://localhost/a").forEach { raw ->
            try { PublicDirectoryFetcher(DirectoryResolver { listOf(publicIp) }, exchange).fetch(raw); fail(raw) }
            catch (e: SubscriptionException) { assertEquals(SubscriptionFailure.DESTINATION, e.failure); assertFalse(e.toString().contains("secret")) }
        }
        assertEquals(0, requests)
    }
    @Test fun mixed_dns_answer_rebind_redirect_and_byte_cap_are_rejected_without_followup() = runBlocking {
        var resolves = 0; var requests = 0
        val resolver = DirectoryResolver { resolves++; if (resolves == 1) listOf(publicIp) else listOf(InetAddress.getByAddress(byteArrayOf(127,0,0,1))) }
        val fetcher = PublicDirectoryFetcher(resolver, DirectoryExchange { _, pins, _ ->
            requests++; assertEquals(listOf(publicIp), pins); DirectoryResponse(302, byteArrayOf())
        })
        try { fetcher.fetch(URL); fail() } catch (e: SubscriptionException) { assertEquals(SubscriptionFailure.DESTINATION, e.failure) }
        assertEquals(1, resolves); assertEquals(1, requests)
        try { fetcher.fetch(URL); fail() } catch (e: SubscriptionException) { assertEquals(SubscriptionFailure.DESTINATION, e.failure) }
        assertEquals(2, resolves); assertEquals(1, requests)
        val mixed = PublicDirectoryFetcher(DirectoryResolver { listOf(publicIp, InetAddress.getByAddress(byteArrayOf(10,0,0,1))) },
            DirectoryExchange { _, _, _ -> fail("must not execute"); DirectoryResponse(200, byteArrayOf()) })
        try { mixed.fetch(URL); fail() } catch (e: SubscriptionException) { assertEquals(SubscriptionFailure.DESTINATION, e.failure) }
        val huge = PublicDirectoryFetcher(DirectoryResolver { listOf(publicIp) }, DirectoryExchange { _, _, _ -> DirectoryResponse(200, ByteArray(65_537)) })
        try { huge.fetch(URL); fail() } catch (e: SubscriptionException) { assertEquals(SubscriptionFailure.TOO_LARGE, e.failure) }
    }
    @Test fun slow_dns_and_exchange_have_finite_timeout_and_cancel_work() = runBlocking {
        var exchangeCalled = false
        val fetcher = PublicDirectoryFetcher(DirectoryResolver { awaitCancellation() }, DirectoryExchange { _, _, _ ->
            exchangeCalled = true; DirectoryResponse(200, byteArrayOf()) }, timeoutMillis = 50)
        try { withTimeout(2_000) { fetcher.fetch(URL) }; fail() }
        catch (e: SubscriptionException) { assertEquals(SubscriptionFailure.TIMEOUT, e.failure) }
        assertFalse(exchangeCalled)
        var cancelled = false
        val slow = PublicDirectoryFetcher(DirectoryResolver { listOf(publicIp) }, DirectoryExchange { _, _, _ ->
            try { awaitCancellation() } finally { cancelled = true } }, timeoutMillis = 50)
        try { withTimeout(2_000) { slow.fetch(URL) }; fail() }
        catch (e: SubscriptionException) { assertEquals(SubscriptionFailure.TIMEOUT, e.failure) }
        assertTrue(cancelled)
    }
    @Test fun downstream_cancellation_and_exception_are_not_wrapped_as_failed() = runBlocking {
        val f = Fixture(); var emissions = 0
        try { f.manager.refresh(request()).collect { emissions++; throw IllegalStateException("collector") }; fail() }
        catch (e: IllegalStateException) { assertEquals("collector", e.message) }
        assertEquals(1, emissions); assertEquals(0, f.executions)
    }
    @Test fun clock_rollback_after_revocation_cannot_issue_new_consent() = runBlocking {
        val f = Fixture(); f.consent(); f.store.revokeConsent(CAT, 2); val old = f.memory.row
        f.now = 9_999
        try { f.consent(3); fail("rollback must fail closed") }
        catch (e: SubscriptionException) { assertEquals(SubscriptionFailure.CLOCK_ROLLBACK, e.failure) }
        assertEquals(old, f.memory.row)
    }
    @Test fun even_empty_userinfo_and_public_ipv4_mapped_ipv6_are_rejected_before_exchange() = runBlocking {
        var requests = 0
        val fetcher = PublicDirectoryFetcher(DirectoryResolver { listOf(publicIp) }, DirectoryExchange { _, _, _ ->
            requests++; DirectoryResponse(200, byteArrayOf()) })
        for (url in listOf("https://@public.example/a", "https://[::ffff:8.8.8.8]/a", "https://[0:0:0:0:0:ffff:0808:0808]/a")) {
            try { fetcher.fetch(url); fail("must reject") }
            catch (e: SubscriptionException) { assertEquals(SubscriptionFailure.DESTINATION, e.failure) }
        }
        assertEquals(0, requests)
    }

    @Test fun manager_caps_active_exchange_concurrency_and_supersedes_queued_old_work() = runBlocking {
        val f = Fixture(); f.consent(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var active = 0; var maximum = 0; var calls = 0
        f.exchange = {
            active++; calls++; maximum = maxOf(maximum, active); if (calls == 2) entered.complete(Unit)
            try { withTimeout(2_000) { release.await() }; DirectoryResponse(200, f.response) } finally { active-- }
        }
        val tasks = (1..3).map { async { f.refresh() } }
        withTimeout(2_000) { entered.await() }; yield(); assertEquals(2, calls); assertEquals(2, maximum)
        release.complete(Unit)
        val outcomes = withTimeout(2_000) { tasks.map { it.await().last() } }
        assertEquals(1, outcomes.count { it is SubscriptionState.Configured }); assertTrue(maximum <= 2)
    }

    @Test fun shipped_empty_directory_with_declarative_notice_reports_empty_without_consent_or_save() = runBlocking {
        // Exact unchanged update/sources.json content: notice is declarative, never publisher trust or consent.
        val shipped = """{
  "sources": [],
  "notice": "项目不提供默认媒体源。请在应用设置中自行配置有权访问的服务；不会修改设备已保存的来源。"
}
""".toByteArray()
        val f = Fixture(); f.response = shipped
        assertEquals(listOf(SubscriptionState.Checking, SubscriptionState.CatalogEmpty), f.refresh())
        assertNull(f.memory.row)
        assertEquals(DirectoryValidation.Empty, DirectoryValidator(emptyList()).validate(shipped, CAT, ORIGIN, f.now))
        listOf("{\"sources\":[],\"notice\":3}", "{\"sources\":[],\"notice\":{},\"script\":\"module\"}",
            "{\"sources\":[],\"notice\":\"declarative\",\"module\":\"code\"}",
            "{\"sources\":[{}],\"notice\":\"declarative\"}",
            "{\"sources\":[],\"notice\":\"${"x".repeat(2049)}\"}").forEach { raw ->
            failure(SubscriptionFailure.MALFORMED) { validator().validate(raw.toByteArray(), CAT, ORIGIN, f.now) }
        }
    }

}
