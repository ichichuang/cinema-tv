package com.ichichuang.cinema.data.subscription

import android.database.Cursor
import androidx.room.withTransaction
import com.ichichuang.cinema.data.local.NativeStorageFixture
import com.ichichuang.cinema.data.local.codec.Utf16KeyConverter
import com.ichichuang.cinema.domain.model.catalog.SourceId
import com.ichichuang.cinema.domain.model.subscription.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.security.KeyPairGenerator
import java.security.Signature

/** File-backed candidate Room and generated synthetic keys only. Exchange/DNS never open a socket. */
class SubscriptionRefreshNativeTest : NativeStorageFixture() {
    companion object {
        private val key by lazy { KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair() }
        private const val CAT = "synthetic-native-catalog"
        private const val ORIGIN = "https://synthetic-native-directory.example"
        private val source = DirectorySource(SourceId("synthetic-native-stable-source"), "合成原生来源", "https://synthetic-native-source.example/api")
        private val publicIp = InetAddress.getByAddress(byteArrayOf(8,8,8,8))
    }
    private var now = 10_000L
    private var bytes = signed()
    private var exchange: suspend () -> DirectoryResponse = { DirectoryResponse(200, bytes) }
    private fun signed(revision: Long = 1, sources: List<DirectorySource> = listOf(source)): ByteArray {
        val payload = DirectoryValidator.canonical(CAT, ORIGIN, "synthetic-native-owner", revision, 1_000, 100_000, sources)
        val signature = Signature.getInstance("SHA256withRSA").apply { initSign(key.private); update(payload) }.sign()
        return JsonObject(Json.parseToJsonElement(payload.decodeToString()).jsonObject +
            ("signature" to JsonPrimitive(DirectoryValidator.hex(signature)))).toString().toByteArray()
    }
    private fun store(persistence: SubscriptionPersistence = RoomSubscriptionPersistence(database)) = CandidateSubscriptionStore(persistence) { now }
    private fun manager(store: CandidateSubscriptionStore) = SubscriptionRefreshCoordinator(store,
        DirectoryValidator(listOf(CatalogTrustRule(CAT, ORIGIN, "synthetic-native-owner", key.public))),
        PublicDirectoryFetcher(DirectoryResolver { listOf(publicIp) }, DirectoryExchange { _, pins, _ ->
            assertEquals(listOf(publicIp), pins); exchange()
        }), mapOf(CAT to "$ORIGIN/catalog.json"), { now })
    private fun request(consent: Long? = 1, selected: Set<SourceId> = setOf(source.id)) =
        SubscriptionRefreshRequest.create(RefreshReason.USER_REQUEST, CAT, selected, consent)
    private suspend fun consent(store: CandidateSubscriptionStore, manager: SubscriptionRefreshCoordinator, revision: Long = 1,
        selected: Set<SourceId> = setOf(source.id)) {
        store.grantVerifiedConsent((manager.inspect(CAT) as DirectoryValidation.Trusted).directory, revision, selected)
    }
    private val oldTables = listOf("work_references", "favorites", "browse_snapshots", "playback_snapshots", "legacy_import_batches", "legacy_import_records")
    private suspend fun seedPersonalRows() = withContext(Dispatchers.IO) {
        val sql = database.openHelper.writableDatabase; val cv = Utf16KeyConverter(); val w = cv.encode("synthetic-native-user-work")
        sql.execSQL("INSERT INTO work_references VALUES (?)", arrayOf<Any>(w))
        sql.execSQL("INSERT INTO favorites VALUES (?)", arrayOf<Any>(w))
        sql.execSQL("INSERT INTO browse_snapshots VALUES (?,?)", arrayOf<Any>(cv.encode("synthetic-native-browse"), "exact-old-browse"))
        sql.execSQL("INSERT INTO playback_snapshots VALUES (?,?,?,?)", arrayOf<Any>(w, cv.encode("e"), cv.encode("v"), "exact-old-checkpoint"))
        sql.execSQL("INSERT INTO legacy_import_batches VALUES (1,'synthetic-native-digest',-123,1,0,1,0)")
        sql.execSQL("INSERT INTO legacy_import_records VALUES (1,'synthetic-native-digest','progress',?,'string','0,1,2000,0','unresolved','missing_identity',0,1,2000,NULL)",
            arrayOf<Any>(cv.encode("wp_synthetic")))
    }
    private suspend fun oldRows(): Map<String, List<List<String>>> = withContext(Dispatchers.IO) {
        oldTables.associateWith { table -> database.openHelper.writableDatabase.query("SELECT * FROM `$table`").use { cursor ->
            buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map { i -> when (cursor.getType(i)) {
                Cursor.FIELD_TYPE_NULL -> "NULL"
                Cursor.FIELD_TYPE_BLOB -> DirectoryValidator.hex(cursor.getBlob(i))
                else -> cursor.getString(i)
            } }) }.sortedBy { it.toString() }
        } }
    }

    @Test fun subscription01_valid_signature_and_explicit_bound_consent_config_reopen_without_touching_user_rows() = runBlocking<Unit> {
        seedPersonalRows(); val before = oldRows(); val s = store(); val m = manager(s)
        assertEquals(SubscriptionState.Failed("CONSENT_REQUIRED"), m.refresh(request(null)).toList().last())
        assertNull(s.raw(CAT)); consent(s,m)
        val reviewed = s.snapshot(CAT); assertNotNull(reviewed.consent); assertNull(reviewed.configuration)
        assertEquals(SubscriptionState.Configured(1), m.refresh(request()).toList().last())
        val raw = s.raw(CAT); assertNotNull(raw); reopen()
        val restored = store().snapshot(CAT)
        assertEquals(raw, store().raw(CAT)); assertEquals(reviewed.consent, restored.consent)
        assertEquals(1L, restored.configuration!!.revision); assertTrue(restored.configuration.sources.single().enabled)
        assertEquals(before, oldRows())
    }
    @Test fun subscription02_network_signature_replay_and_clock_failure_preserve_exact_row_after_reopen() = runBlocking<Unit> {
        seedPersonalRows(); val before = oldRows(); val s = store(); val m = manager(s); consent(s,m); m.refresh(request()).toList()
        val raw = s.raw(CAT)
        exchange = { throw java.io.IOException("synthetic-only") }
        assertEquals(SubscriptionState.Failed("NETWORK"), m.refresh(request()).toList().last())
        assertEquals(raw, s.raw(CAT)); exchange = { DirectoryResponse(200, bytes) }
        assertEquals(SubscriptionState.Failed("REPLAY"), m.refresh(request()).toList().last()); assertEquals(raw,s.raw(CAT))
        bytes = signed().decodeToString().replace("合成原生来源", "签名已变").toByteArray()
        assertEquals(SubscriptionState.Failed("SIGNATURE"), m.refresh(request()).toList().last()); assertEquals(raw,s.raw(CAT))
        bytes = signed(); now = 9_999
        assertEquals(SubscriptionState.Failed("CLOCK_ROLLBACK"), m.refresh(request()).toList().last()); assertEquals(raw,s.raw(CAT))
        reopen(); assertEquals(raw,store().raw(CAT)); assertEquals(before,oldRows())
    }
    @Test fun subscription03_revoked_consent_and_disabled_tombstone_are_rechecked_at_real_commit() = runBlocking<Unit> {
        seedPersonalRows(); val before = oldRows(); val s=store();val m=manager(s);consent(s,m)
        var entered=CompletableDeferred<Unit>();var release=CompletableDeferred<Unit>()
        exchange={entered.complete(Unit);withTimeout(2_000){release.await()};DirectoryResponse(200,bytes)}
        val stale=async{m.refresh(request()).toList()};withTimeout(2_000){entered.await()}
        s.revokeConsent(CAT,2);val revoked=s.raw(CAT);release.complete(Unit)
        assertEquals(SubscriptionState.Failed("CONSENT_REQUIRED"),stale.await().last());assertEquals(revoked,s.raw(CAT))
        exchange={DirectoryResponse(200,bytes)};consent(s,m,3)
        entered=CompletableDeferred();release=CompletableDeferred()
        exchange={entered.complete(Unit);withTimeout(2_000){release.await()};DirectoryResponse(200,bytes)}
        val disabled=async{m.refresh(request(3)).toList()};withTimeout(2_000){entered.await()}
        s.disableSources(CAT,setOf(source.id));release.complete(Unit)
        assertEquals(SubscriptionState.Configured(1),disabled.await().last());reopen()
        assertFalse(store().snapshot(CAT).configuration!!.sources.single().enabled)
        assertTrue(source.id in store().snapshot(CAT).disabled);assertEquals(before,oldRows())
    }
    @Test fun subscription04_cancellation_after_real_upsert_rolls_back_consent_config_and_user_rows() = runBlocking<Unit> {
        seedPersonalRows();val before=oldRows();val s=store();val m=manager(s);consent(s,m);val raw=s.raw(CAT)
        val entered=CompletableDeferred<Unit>()
        val delegate = RoomSubscriptionPersistence(database)
        val cancelling = store(object : SubscriptionPersistence {
            override suspend fun read(catalogId: String) = delegate.read(catalogId)
            override suspend fun update(catalogId: String, transform: (String?) -> String) {
                database.withTransaction {
                    delegate.update(catalogId, transform)
                    entered.complete(Unit)
                    awaitCancellation()
                }
            }
        })
        val work=launch{manager(cancelling).refresh(request()).toList()}
        withTimeout(2_000){entered.await()};work.cancelAndJoin();assertEquals(raw,s.raw(CAT))
        reopen();assertEquals(raw,store().raw(CAT));assertNull(store().snapshot(CAT).configuration);assertEquals(before,oldRows())
    }
    @Test fun subscription05_real_sql_abort_rolls_back_without_losing_prior_consent_or_user_rows() = runBlocking<Unit> {
        seedPersonalRows();val before=oldRows();val s=store();val m=manager(s);consent(s,m);val raw=s.raw(CAT)
        withContext(Dispatchers.IO){database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER synthetic_subscription_failure BEFORE UPDATE ON candidate_subscription_state BEGIN SELECT RAISE(ABORT,'synthetic'); END")}
        assertEquals(SubscriptionState.Failed("STORAGE"),m.refresh(request()).toList().last());assertEquals(raw,s.raw(CAT))
        reopen();assertEquals(raw,store().raw(CAT));assertEquals(before,oldRows())
    }
    @Test fun subscription06_older_overlap_cannot_replace_newer_committed_candidate_and_removed_source_is_retained() = runBlocking<Unit> {
        seedPersonalRows();val before=oldRows();val s=store();val m=manager(s);consent(s,m)
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();var count=0
        exchange={if(++count==1){entered.complete(Unit);withTimeout(2_000){release.await()}};DirectoryResponse(200,bytes)}
        val old=async{m.refresh(request()).toList()};withTimeout(2_000){entered.await()}
        assertEquals(SubscriptionState.Configured(1),m.refresh(request()).toList().last());val latest=s.raw(CAT);release.complete(Unit)
        assertEquals(SubscriptionState.Failed("SUPERSEDED"),old.await().last());assertEquals(latest,s.raw(CAT))
        val next=source.copy(id=SourceId("synthetic-native-other"),endpoint="https://synthetic-other.example/api")
        bytes=signed(2,listOf(next));exchange={DirectoryResponse(200,bytes)};consent(s,m,2,setOf(next.id))
        assertEquals(SubscriptionState.Configured(2),m.refresh(request(2,setOf(next.id))).toList().last());reopen()
        val restored=store().snapshot(CAT).configuration!!
        assertTrue(restored.sources.first{it.source.id==source.id}.removed)
        assertFalse(restored.sources.first{it.source.id==source.id}.enabled);assertEquals(before,oldRows())
    }
    @Test fun subscription07_sixty_four_catalog_capacity_survives_reopen_and_existing_rows_remain_updatable() = runBlocking<Unit> {
        seedPersonalRows(); val before = oldRows(); val s = store(); val m = manager(s); consent(s,m)
        val raw = s.raw(CAT)!!
        val persistence = RoomSubscriptionPersistence(database)
        repeat(63) { index -> persistence.update("synthetic-native-capacity-$index") { raw } }
        assertEquals(64, database.querySubscription().subscriptionCount())
        try { persistence.update("synthetic-native-over-capacity") { raw }; fail("capacity must reject") }
        catch (e: SubscriptionException) { assertEquals(SubscriptionFailure.STORAGE,e.failure) }
        assertEquals(64,database.querySubscription().subscriptionCount()); assertNull(persistence.read("synthetic-native-over-capacity"))
        assertEquals(SubscriptionState.Configured(1),m.refresh(request()).toList().last())
        val configured = s.raw(CAT); reopen()
        assertEquals(64,database.querySubscription().subscriptionCount()); assertEquals(configured,store().raw(CAT))
        assertEquals(before,oldRows())
    }

}
