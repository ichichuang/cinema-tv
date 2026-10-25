package com.ichichuang.cinema.data.catalog

import androidx.room.withTransaction
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.BuildConfig
import com.ichichuang.cinema.data.local.*
import com.ichichuang.cinema.data.local.codec.Utf16KeyConverter
import com.ichichuang.cinema.domain.model.catalog.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** All rows, endpoints and metadata are synthetic. Every DB is a real private file. */
class WorkIdentityStoreInstrumentedTest : NativeStorageFixture() {
    private val a = SourceId("synthetic-A"); private val b = SourceId("synthetic-B")
    private fun key(source: SourceId) = SourceItemKey(source, SourceItemId("17"))
    private val identifier = CatalogIdentifier("synthetic.namespace", "verified-17")
    private fun policy() = CatalogIdentityProofs(mapOf("verified-AB" to VerifiedCatalogAssociation(key(a), key(b), identifier)),
        mapOf("authorized-alias" to VerifiedSourceAlias(a, "fixture:endpoint-A", "fixture:relocated-A")))
    private var sequence = 0
    private fun store(proofs: CatalogIdentityProofs = policy()) = CandidateCatalogStore(database, proofs) { "synthetic-opaque-${++sequence}" }
    private fun raw(vararg fields: Pair<String, JsonElement>) = JsonObject(linkedMapOf<String, JsonElement>(
        "vod_id" to JsonPrimitive("17"), "vod_name" to JsonPrimitive("synthetic-work"), "vod_year" to JsonPrimitive("1994"),
        "vod_play_from" to JsonPrimitive("synthetic-track"), "vod_play_url" to JsonPrimitive("synthetic-episode${'$'}fixture:media")) + fields.toMap())
    private fun capture(source: SourceId = a, vararg fields: Pair<String, JsonElement>) = MaccmsCatalogNormalizer.normalize(source, raw(*fields), 123)
    private fun entry(suffix: String) = listOf(DeclaredCatalogEntry("synthetic-entry-$suffix", EpisodeId("synthetic-episode-$suffix"), VariantId("synthetic-version-$suffix"), 0, 0))
    private suspend fun seedPair(s: CandidateCatalogStore) {
        s.registerSource(a, "fixture:endpoint-A"); s.registerSource(b, "fixture:endpoint-B")
        assertNotNull(s.ingest("capture-A", capture(a), entry("A")))
        assertNotNull(s.ingest("capture-B", capture(b), entry("B")))
    }
    private suspend fun scalar(sql: String) = withContext(Dispatchers.IO) { database.openHelper.writableDatabase.query(sql).use { it.moveToFirst(); it.getLong(0) } }
    private fun rowSnapshot(query: (String) -> Cursor): Map<String,List<List<String>>> {
        val tables = listOf("work_references","work_metadata_cache","favorites","browse_snapshots","playback_snapshots","legacy_import_batches",
            "legacy_import_records","playback_sessions","playback_record_order","playback_resume","legacy_handoff_state")
        return tables.associateWith { table -> query("SELECT * FROM `$table`").use { c ->
            buildList { while(c.moveToNext()) add((0 until c.columnCount).map { i -> when(c.getType(i)) {
                Cursor.FIELD_TYPE_NULL -> "null"; Cursor.FIELD_TYPE_BLOB -> c.getBlob(i).joinToString("") { "%02x".format(it) }; else -> c.getString(i)
            } }) }.sortedBy { it.toString() }
        } }
    }
    private suspend fun oldRows(): Map<String,List<List<String>>> = withContext(Dispatchers.IO) { rowSnapshot { database.openHelper.writableDatabase.query(it) } }
    @Test fun id01_same_numeric_id_is_scoped_to_source_and_survives_reopen() = runBlocking<Unit> {
        seedPair(store()); reopen(); val s=store()
        assertNotEquals(s.workFor(key(a)), s.workFor(key(b)))
        assertEquals(s.workFor(key(a)), s.groupFor(key(a)))
    }
    @Test fun id02_title_and_year_only_create_nonbinding_candidate() = runBlocking<Unit> {
        val s=store(); seedPair(s)
        assertEquals(AssociationDecision.CANDIDATE,s.associate(key(a),key(b),"no-proof",1))
        assertNotEquals(s.groupFor(key(a)),s.groupFor(key(b)))
    }
    @Test fun id03_verified_link_preserves_both_entries_and_durable_relation() = runBlocking<Unit> {
        val s=store(); seedPair(s); assertEquals(AssociationDecision.LINKED,s.associate(key(a),key(b),"verified-AB",1))
        reopen(); val reopened=store()
        assertEquals(reopened.groupFor(key(a)),reopened.groupFor(key(b)))
        assertEquals(listOf("synthetic-entry-A"),reopened.entries(key(a)))
        assertEquals(listOf("synthetic-entry-B"),reopened.entries(key(b)))
        assertEquals(AssociationDecision.LINKED,reopened.associate(key(a),key(b),"verified-AB",2))
        assertEquals(1L,scalar("SELECT count(*) FROM catalog_association_history WHERE action='linked'"))
    }
    @Test fun id04_raw_external_identifier_and_wrong_proof_scope_are_untrusted() = runBlocking<Unit> {
        val s=store(); seedPair(s)
        s.ingest("raw-claim",capture(b,"external_id" to JsonPrimitive("verified-17")))
        assertEquals(AssociationDecision.CANDIDATE,s.associate(key(a),key(b),"raw-claim",1))
        assertNotEquals(s.groupFor(key(a)),s.groupFor(key(b)))
    }
    @Test fun id05_transitive_conflicting_namespace_preserves_previous_groups_and_records_conflict() = runBlocking<Unit> {
        val c=SourceId("synthetic-C"); val d=SourceId("synthetic-D")
        val proofs=CatalogIdentityProofs(mapOf("AB" to VerifiedCatalogAssociation(key(a),key(b),identifier),
            "CD" to VerifiedCatalogAssociation(key(c),key(d),CatalogIdentifier(identifier.namespace,"different")),
            "BC" to VerifiedCatalogAssociation(key(b),key(c),CatalogIdentifier("other.namespace","bridge"))))
        val s=store(proofs); seedPair(s)
        for (source in listOf(c,d)) { s.registerSource(source,"fixture:${source.value}"); s.ingest("capture-${source.value}",capture(source)) }
        assertEquals(AssociationDecision.LINKED,s.associate(key(a),key(b),"AB",1))
        assertEquals(AssociationDecision.LINKED,s.associate(key(c),key(d),"CD",2))
        val before=s.groupFor(key(c))
        assertEquals(AssociationDecision.CONFLICT,s.associate(key(b),key(c),"BC",3))
        reopen(); assertEquals(before,store(proofs).groupFor(key(c)))
        assertEquals(4L,scalar("SELECT count(*) FROM catalog_items"))
        assertEquals(1L,scalar("SELECT count(*) FROM catalog_association_history WHERE action='conflict'"))
    }
    @Test fun id06_only_exact_authorized_alias_preserves_source_identity() = runBlocking<Unit> {
        val s=store(); seedPair(s)
        assertFalse(s.alias(a,"fixture:endpoint-A","fixture:endpoint-A-similar","no-proof"))
        assertTrue(s.alias(a,"fixture:endpoint-A","fixture:relocated-A","authorized-alias"))
        val before=s.workFor(key(a)); reopen(); assertEquals(before,store().workFor(key(a)))
        assertEquals(3L,scalar("SELECT count(*) FROM catalog_endpoints"))
    }
    @Test fun id07_reingest_is_idempotent_and_reused_capture_token_cannot_overwrite() = runBlocking<Unit> {
        val s=store(); seedPair(s); val before=s.workFor(key(a))
        assertEquals(before,s.ingest("capture-A",capture(a),entry("A")))
        assertEquals(2L,scalar("SELECT count(*) FROM catalog_captures"))
        try { s.ingest("capture-A",capture(a,"vod_year" to JsonPrimitive("1995"))) ; fail("token overwrite") } catch (_: IllegalArgumentException) { }
        assertEquals("1994",s.captures(key(a)).single().raw["vod_year"]!!.jsonPrimitive.content)
    }
    @Test fun id08_year_correction_preserves_claims_and_conflict_after_reopen() = runBlocking<Unit> {
        val s=store(); seedPair(s); s.ingest("corrected-A",capture(a,"vod_year" to JsonPrimitive("1995")))
        reopen(); val years=store().captures(key(a)).map { it.year }
        assertEquals(setOf(1994,1995),(CatalogClaims.combine(years) as FieldEvidence.Conflict).claims.map { it.value }.toSet())
    }
    @Test fun id09_missing_null_and_known_empty_survive_file_reopen() = runBlocking<Unit> {
        val s=store(); s.registerSource(a,"fixture:endpoint-A")
        s.ingest("missing",capture(a)); s.ingest("empty",capture(a,"vod_area" to JsonPrimitive("")))
        s.ingest("null",capture(a,"vod_area" to JsonNull)); reopen()
        val captures=store().captures(key(a)); assertEquals(3,captures.size)
        assertEquals(1,captures.count { !it.raw.containsKey("vod_area") })
        assertEquals(1,captures.count { it.facts.getValue("countries") is FieldEvidence.SourceReported })
        assertEquals(1,captures.count { it.raw["vod_area"] === JsonNull })
    }
    @Test fun id10_original_language_audio_and_subtitle_claims_stay_separate() = runBlocking<Unit> {
        val s=store(); s.registerSource(a,"fixture:endpoint-A")
        s.ingest("languages",capture(a,"work_original_language" to JsonPrimitive("日语"),"vod_lang" to JsonPrimitive("国语配音"),"subtitle_language" to JsonPrimitive("中文字幕")))
        reopen(); val facts=store().captures(key(a)).single().facts
        assertEquals(setOf("ja"),(facts.getValue("originalLanguages") as FieldEvidence.SourceReported).value.values)
        assertEquals(setOf("zh"),(facts.getValue("audioLanguages") as FieldEvidence.SourceReported).value.values)
        assertEquals(setOf("zh"),(facts.getValue("subtitleLanguages") as FieldEvidence.SourceReported).value.values)
    }
    @Test fun id11_multi_country_and_vague_region_are_retained_without_inference() = runBlocking<Unit> {
        val s=store(); s.registerSource(a,"fixture:endpoint-A")
        s.ingest("countries",capture(a,"vod_area" to JsonPrimitive("日本/中国")))
        s.ingest("vague",capture(a,"vod_area" to JsonPrimitive("欧美"))); reopen()
        val captures=store().captures(key(a))
        assertEquals(setOf("CN","JP"),(captures.first { it.raw["vod_area"] == JsonPrimitive("日本/中国") }.facts.getValue("countries") as FieldEvidence.SourceReported).value.values)
        assertTrue(captures.first { it.raw["vod_area"] == JsonPrimitive("欧美") }.facts.getValue("countries") is FieldEvidence.Unknown)
    }
    @Test fun id12_exact_genre_aliases_keep_thriller_distinct_and_raw_labels() = runBlocking<Unit> {
        val s=store(); s.registerSource(a,"fixture:endpoint-A")
        s.ingest("genres",capture(a,"vod_class" to JsonPrimitive("動作片/惊悚"))); reopen()
        val fact=store().captures(key(a)).single().facts.getValue("genres") as FieldEvidence.SourceReported
        assertEquals(setOf("action","thriller"),fact.value.values); assertEquals("動作片/惊悚",fact.reference.rawValue)
    }
    private suspend fun seedPersonal(workId: WorkId) = withContext(Dispatchers.IO) {
        val sql=database.openHelper.writableDatabase; val converter=Utf16KeyConverter(); val work=converter.encode(workId.value)
        sql.execSQL("INSERT INTO favorites VALUES (?)",arrayOf<Any>(work))
        sql.execSQL("INSERT INTO browse_snapshots VALUES (?,?)",arrayOf<Any>(converter.encode("synthetic-context"),"synthetic-original-browse"))
        sql.execSQL("INSERT INTO playback_snapshots VALUES (?,?,?,?)",arrayOf<Any>(work,converter.encode("synthetic-episode"),converter.encode("synthetic-version"),"synthetic-original-checkpoint"))
        sql.execSQL("INSERT INTO legacy_import_batches VALUES (1,'synthetic-digest',123,1,0,1,0)")
        sql.execSQL("INSERT INTO legacy_import_records VALUES (1,'synthetic-digest','progress',?,'string','0,1,2000,0','unresolved','missing_identity',0,1,2000,NULL)",arrayOf<Any>(converter.encode("wp_17")))
    }
    @Test fun id13_false_link_split_retains_old_work_keys_and_all_personal_rows() = runBlocking<Unit> {
        val s=store(); seedPair(s); seedPersonal(s.workFor(key(a))!!); val before=oldRows()
        s.associate(key(a),key(b),"verified-AB",1); s.split(key(a),"synthetic-correction",2)
        reopen(); val reopened=store()
        assertEquals(before,oldRows()); assertNotEquals(reopened.groupFor(key(a)),reopened.groupFor(key(b)))
        assertEquals(2L,scalar("SELECT count(*) FROM catalog_association_history WHERE action='split'"))
        assertEquals(2L,scalar("SELECT count(*) FROM catalog_verified_identifiers WHERE revoked=1"))
        assertEquals(2L,scalar("SELECT count(*) FROM catalog_entries"))
        assertEquals(AssociationDecision.CONFLICT,reopened.associate(key(a),key(b),"verified-AB",3))
    }
    @Test fun id14_unavailable_source_does_not_purge_catalog_or_user_records() = runBlocking<Unit> {
        val s=store(); seedPair(s); seedPersonal(s.workFor(key(a))!!); val before=oldRows();s.unavailable(a);reopen()
        assertEquals(before,oldRows()); assertNotNull(store().workFor(key(a)))
        assertEquals(1L,scalar("SELECT count(*) FROM catalog_sources WHERE available=0"))
    }
    @Test fun id15_schema_three_migration_preserves_every_old_row_then_new_state_reopens() = runBlocking<Unit> {
        database.close(); context.deleteDatabase(databaseName)
        val schema=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("com.ichichuang.cinema.data.local.CinemaDatabase/3.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val beforeMigration=withContext(Dispatchers.IO) { SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName),null).use { sql ->
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()) { val entity=entities.getJSONObject(i); val table=entity.getString("tableName")
                sql.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table)); val indices=entity.optJSONArray("indices")
                for(j in 0 until (indices?.length() ?: 0)) sql.execSQL(indices!!.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table)) }
            val setup=schema.getJSONArray("setupQueries");for(i in 0 until setup.length()) sql.execSQL(setup.getString(i));sql.version=3
            sql.execSQL("INSERT INTO work_references VALUES (?)",arrayOf<Any>(Utf16KeyConverter().encode("synthetic-old-work")))
            sql.execSQL("INSERT INTO favorites VALUES (?)",arrayOf<Any>(Utf16KeyConverter().encode("synthetic-old-work")))
            sql.execSQL("INSERT INTO browse_snapshots VALUES (?,?)",arrayOf<Any>(Utf16KeyConverter().encode("synthetic-old-context"),"old-exact-payload"))
            val cv=Utf16KeyConverter();val w=cv.encode("synthetic-old-work");val e=cv.encode("synthetic-old-episode");val v=cv.encode("synthetic-old-version")
            sql.execSQL("INSERT INTO work_metadata_cache VALUES (?,?)",arrayOf<Any>(w,"old-exact-cache"))
            sql.execSQL("INSERT INTO playback_snapshots VALUES (?,?,?,?)",arrayOf<Any>(w,e,v,"old-exact-checkpoint"))
            sql.execSQL("INSERT INTO legacy_import_batches VALUES (1,'synthetic-old-digest',-123,1,0,1,0)")
            sql.execSQL("INSERT INTO legacy_import_records VALUES (1,'synthetic-old-digest','progress',?,'string','0,1,2000,0','unresolved','missing_identity',0,1,2000,NULL)",arrayOf<Any>(cv.encode("wp_old")))
            sql.execSQL("INSERT INTO playback_sessions VALUES (1,?,?,?,?,?,?)",arrayOf<Any>(cv.encode("old-session"),w,e,v,7,"old-session-payload"))
            sql.execSQL("INSERT INTO playback_record_order VALUES (?,?,?,1)",arrayOf<Any>(w,e,v))
            sql.execSQL("INSERT INTO playback_resume VALUES (?,1,?)",arrayOf<Any>(w,"old-resume-payload"))
            sql.execSQL("INSERT INTO legacy_handoff_state VALUES (1,'verified',1,'synthetic-old-digest','old-mapping-digest',1)")
            rowSnapshot { sql.rawQuery(it,null) }
        } }
        database=CinemaDatabaseFactory.open(context,databaseName)
        assertEquals(6L,scalar("PRAGMA user_version")); assertEquals(beforeMigration,oldRows());val before=oldRows();seedPair(store());reopen()
        assertEquals(before.getValue("favorites"),oldRows().getValue("favorites"))
        assertEquals(before.getValue("browse_snapshots"),oldRows().getValue("browse_snapshots"))
        assertEquals(beforeMigration.filterKeys { it != "work_references" },oldRows().filterKeys { it != "work_references" })
        assertNotNull(store().workFor(key(a)))
    }
    @Test fun id16_list_detail_enrichment_keeps_prior_raw_and_route_versions() = runBlocking<Unit> {
        val s=store(); seedPair(s)
        s.ingest("detail",capture(a,"vod_content" to JsonPrimitive("synthetic-detail"),"vod_play_url" to JsonPrimitive("synthetic-episode${'$'}fixture:replacement")),entry("A"))
        reopen(); val captures=store().captures(key(a))
        assertEquals(2,captures.size);assertEquals(1,captures.count { !it.raw.containsKey("vod_content") })
        assertEquals(listOf("synthetic-entry-A"),store().entries(key(a)))
        assertEquals(3L,scalar("SELECT count(*) FROM catalog_routes"))
    }
    @Test fun id17_ordinary_gate_off_keeps_candidate_explicit() = runBlocking<Unit> {
        assertFalse(BuildConfig.PLAYBACK_ROOM_PROBE)
        assertEquals(0L,scalar("SELECT count(*) FROM catalog_sources"))
        assertEquals(0L,scalar("SELECT count(*) FROM legacy_handoff_state"))
    }
    @Test fun id18_missing_legacy_origin_stays_unresolved_and_missing_new_id_is_retained_pending() = runBlocking<Unit> {
        val s=store();seedPair(s);seedPersonal(s.workFor(key(a))!!);val before=oldRows()
        val noId=MaccmsCatalogNormalizer.normalize(a,JsonObject(raw()-"vod_id"),5)
        assertNull(s.ingest("pending",noId));reopen()
        assertEquals(1,store().pending(a).size);assertEquals(before,oldRows())
        assertEquals(1L,scalar("SELECT count(*) FROM legacy_import_records WHERE status='unresolved'"))
    }
    @Test fun cancelled_ingest_cannot_add_partial_records() = runBlocking<Unit> {
        val s=store();s.registerSource(a,"fixture:endpoint-A")
        val entered=CompletableDeferred<Unit>()
        val task=launch { database.withTransaction { s.ingest("cancelled",capture(a),entry("A")); entered.complete(Unit); awaitCancellation() } }
        withTimeout(5_000) { entered.await() };task.cancelAndJoin()
        assertNull(s.workFor(key(a)));assertEquals(0L,scalar("SELECT count(*) FROM catalog_captures"))
        assertEquals(0L,scalar("SELECT count(*) FROM catalog_entries"))
    }
    @Test fun route_identity_collision_rolls_back_capture_and_new_item() = runBlocking<Unit> {
        val s=store();seedPair(s);val c=SourceId("synthetic-C");s.registerSource(c,"fixture:endpoint-C")
        try { s.ingest("collision",capture(c),entry("A"));fail("entry reassignment") } catch (_: IllegalArgumentException) { }
        assertNull(s.workFor(key(c)));assertEquals(2L,scalar("SELECT count(*) FROM catalog_captures"))
    }
    @Test fun registration_cannot_bypass_alias_or_take_another_sources_endpoint() = runBlocking<Unit> {
        val s=store();seedPair(s)
        try { s.registerSource(a,"fixture:unauthorized-relocation");fail("alias bypass") } catch (_: IllegalArgumentException) { }
        val c=SourceId("synthetic-C")
        try { s.registerSource(c,"fixture:endpoint-A");fail("endpoint takeover") } catch (_: IllegalArgumentException) { }
        val spoof=CatalogIdentityProofs(aliases=mapOf("spoof" to VerifiedSourceAlias(a,"fixture:endpoint-A","fixture:endpoint-B")))
        assertFalse(store(spoof).alias(a,"fixture:endpoint-A","fixture:endpoint-B","spoof"))
        assertEquals(2L,scalar("SELECT count(*) FROM catalog_sources"))
    }
    @Test fun configured_foreign_keys_and_uniqueness_reject_orphan_and_duplicate_rows() = runBlocking<Unit> {
        val s=store();seedPair(s)
        val row=database.catalogIdentity().item(Utf16KeyConverter().encode(a.value),Utf16KeyConverter().encode("17"))!!
        try { database.catalogIdentity().item(row);fail("duplicate item") } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        try { database.catalogIdentity().entry(com.ichichuang.cinema.data.local.entity.CatalogEntryEntity("orphan","unknown","17","episode","version"));fail("orphan entry") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(2L,scalar("SELECT count(*) FROM catalog_items"));assertEquals(2L,scalar("SELECT count(*) FROM catalog_entries"))
    }
    @Test fun cache_eviction_preserves_identity_evidence_routes_and_personal_records() = runBlocking<Unit> {
        val s=store();seedPair(s);seedPersonal(s.workFor(key(a))!!);s.associate(key(a),key(b),"verified-AB",1)
        for (source in listOf(a,b)) database.metadataCache().upsert(com.ichichuang.cinema.data.local.entity.WorkMetadataCacheEntity(
            s.workFor(key(source))!!.value,"synthetic-disposable-cache-${source.value}"))
        suspend fun routeRows() = withContext(Dispatchers.IO) {
            database.openHelper.writableDatabase.query("SELECT hex(captureId),hex(entryId),payload FROM catalog_routes ORDER BY captureId,entryId").use { cursor ->
                buildList { while(cursor.moveToNext()) add(listOf(cursor.getString(0),cursor.getString(1),cursor.getString(2))) }
            }
        }
        val capturesBefore=listOf(a,b).associateWith { s.captures(key(it)) }
        val routesBefore=routeRows();assertEquals(2,routesBefore.size)
        val before=oldRows();assertEquals(2,before.getValue("work_metadata_cache").size)
        assertEquals(2,database.metadataCache().evictAll());reopen()
        assertTrue(oldRows().getValue("work_metadata_cache").isEmpty())
        assertEquals(before.filterKeys { it != "work_metadata_cache" },oldRows().filterKeys { it != "work_metadata_cache" })
        assertEquals(store().groupFor(key(a)),store().groupFor(key(b)))
        assertEquals(1,store().captures(key(a)).size);assertEquals(listOf("synthetic-entry-A"),store().entries(key(a)))
        assertEquals(2L,scalar("SELECT count(*) FROM catalog_verified_identifiers WHERE revoked=0"))
        assertEquals(capturesBefore,listOf(a,b).associateWith { store().captures(key(it)) })
        assertEquals(routesBefore,routeRows())
    }
    @Test fun three_member_split_revokes_all_proofs_and_retains_original_personal_references_after_reopen() = runBlocking<Unit> {
        val c=SourceId("synthetic-C")
        val proofs=CatalogIdentityProofs(mapOf("AB" to VerifiedCatalogAssociation(key(a),key(b),identifier),
            "BC" to VerifiedCatalogAssociation(key(b),key(c),identifier)))
        val s=store(proofs);seedPair(s);s.registerSource(c,"fixture:endpoint-C");s.ingest("capture-C",capture(c),entry("C"))
        val homes=listOf(a,b,c).associateWith { s.workFor(key(it))!! }
        seedPersonal(homes.getValue(a))
        for(source in listOf(b,c)) withContext(Dispatchers.IO) {
            val cv=Utf16KeyConverter();val w=cv.encode(homes.getValue(source).value)
            database.openHelper.writableDatabase.execSQL("INSERT INTO favorites VALUES (?)",arrayOf<Any>(w))
            database.openHelper.writableDatabase.execSQL("INSERT INTO playback_snapshots VALUES (?,?,?,?)",
                arrayOf<Any>(w,cv.encode("episode-${source.value}"),cv.encode("version-${source.value}"),"original-checkpoint-${source.value}"))
        }
        val before=oldRows()
        assertEquals(AssociationDecision.LINKED,s.associate(key(a),key(b),"AB",1))
        assertEquals(AssociationDecision.LINKED,s.associate(key(b),key(c),"BC",2))
        assertEquals(1,listOf(a,b,c).map { s.groupFor(key(it)) }.distinct().size)
        s.split(key(b),"synthetic-three-member-correction",3);reopen();val reopened=store(proofs)
        assertEquals(before,oldRows());assertEquals(3,homes.values.distinct().size)
        for(source in listOf(a,b,c)) {
            assertEquals(homes.getValue(source),reopened.workFor(key(source)))
            assertEquals(homes.getValue(source),reopened.groupFor(key(source)))
            assertEquals(1,reopened.captures(key(source)).size)
            assertEquals(listOf("synthetic-entry-${source.value.last()}"),reopened.entries(key(source)))
        }
        assertEquals(4L,scalar("SELECT count(*) FROM catalog_verified_identifiers WHERE revoked=1"))
        assertEquals(0L,scalar("SELECT count(*) FROM catalog_verified_identifiers WHERE revoked=0"))
        assertEquals(3L,scalar("SELECT count(*) FROM catalog_association_history WHERE action='split'"))
        assertEquals(AssociationDecision.CONFLICT,reopened.associate(key(a),key(b),"AB",4))
        assertEquals(AssociationDecision.CONFLICT,reopened.associate(key(b),key(c),"BC",5))
        assertEquals(3,listOf(a,b,c).map { reopened.groupFor(key(it)) }.distinct().size)
    }

}
