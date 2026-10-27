package com.ichichuang.cinema.feature.browse

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.MainActivity
import com.ichichuang.cinema.core.navigation.*
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.withTransaction
import com.ichichuang.cinema.data.local.CinemaDatabaseFactory
import com.ichichuang.cinema.data.local.codec.*
import com.ichichuang.cinema.data.local.entity.*
import com.ichichuang.cinema.data.settings.*
import com.ichichuang.cinema.data.userdata.*
import com.ichichuang.cinema.domain.model.catalog.*
import com.ichichuang.cinema.domain.model.userdata.*
import com.ichichuang.cinema.domain.repository.BrowseMemory
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import com.ichichuang.cinema.data.userdata.RoomBrowseStateStore
import com.ichichuang.cinema.domain.model.userdata.CommitResult
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class BrowsePersistenceNativeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val fixtureOwner = BrowseNavigationNativeTest()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val fixture get() = BrowseProbeFixture(context)
    private val target get() = requireNotNull(fixture.initial.focusedWorkId)
    private fun nodes(view: View): List<SemanticsNode> {
        fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        if (view is ViewRootForTest) return flatten(view.semanticsOwner.unmergedRootSemanticsNode)
        return if (view is ViewGroup) (0 until view.childCount).flatMap { nodes(view.getChildAt(it)) } else emptyList()
    }
    private fun focused(): List<String> {
        var tags = emptyList<String>()
        scenario?.onActivity { activity -> tags = nodes(activity.window.decorView).filter {
            it.config.getOrElse(SemanticsProperties.Focused) { false }
        }.map { it.config.getOrElse(SemanticsProperties.TestTag) { "" } } }
        return tags
    }
    private fun until(condition: () -> Boolean) {
        val end = android.os.SystemClock.uptimeMillis() + 15_000
        while (!condition()) { if (android.os.SystemClock.uptimeMillis() > end) {
            var detail = ""
            scenario?.onActivity { detail = "state=${it.browseProbe?.navigation} token=${it.browseProbe?.restoreToken} published=${it.browseProbe != null} ${BrowseProbeTestHooks.focusTrace.toList()}" }
            fail("等待原生持久化状态超时：${focused()} $detail")
        }; Thread.sleep(25) }
    }
    private fun focus(id: String) { until { focused() == listOf(id) } }
    private fun launch(waitFocus: Boolean = true, expected: String = target.value) { scenario = ActivityScenario.launch(MainActivity::class.java); if (waitFocus) focus(expected) }
    private fun runtime(action: (BrowseProbeRuntime) -> Unit) { scenario!!.onActivity { action(requireNotNull(it.browseProbe)) } }
    /**
     * R17.1: MainActivity publishes browseProbe from an asynchronous init coroutine, so a test that
     * launches without waiting for focus must first observe publication instead of dereferencing a
     * still-null probe inside a polling condition. Bounded by until()'s existing 15s timeout; a failed
     * init still fails the test because probeError leaves browseProbe null and the timeout reports it.
     */
    private fun awaitBrowseProbe() = until {
        var present = false
        scenario!!.onActivity { present = it.browseProbe != null }
        present
    }
    private fun key(code: Int) {
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, code)); instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, code))
        instrumentation.waitForIdleSync()
    }
    @Before fun prepare() { fixtureOwner.ownedPrivateFixture() }
    @After fun close() {
        BrowseProbeTestHooks.beforeSave?.complete(Unit); BrowseProbeTestHooks.beforeSave = null
        scenario?.close(); scenario = null
        fixtureOwner.closeOwnedActivity()
        runBlocking { context.browsePrefs.edit { it.remove(stringPreferencesKey("round11-existing-legacy")) } }
        File(context.noBackupFilesDir, LegacyHandoffRuntime.PROFILE).writeText(LegacyHandoffRuntime.PROFILE_TOKEN)
        File(context.noBackupFilesDir, LegacyHandoffRuntime.CONSENT).writeText(LegacyHandoffRuntime.CONSENT_TOKEN)
    }
    @Test fun debounced_room_failure_is_visible_and_retryable() = runBlocking<Unit> {
        DebugBrowseProbeGate.withOwnedProfile(context) { db -> withContext(Dispatchers.IO) {
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_r11_browse BEFORE INSERT ON browse_snapshots BEGIN SELECT RAISE(ABORT, '合成事务故障'); END")
        } }
        launch()
        // The actual debounce is 500ms; allow its transaction to finish without calling flush.
        delay(1_200)
        runtime { assertTrue("稳定焦点防抖提交失败必须显示可恢复状态", it.status.contains("尚未保存")) }
        val faultConnection = CinemaDatabaseFactory.open(context, DebugBrowseProbeGate.DATABASE)
        try { withContext(Dispatchers.IO) { faultConnection.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_r11_browse") } }
        finally { faultConnection.close() }
        acknowledged()
        runtime { assertFalse("重试成功之后不应继续显示保存失败", it.status.contains("尚未保存")) }
    }
    @Test fun external_observer_receives_whole_snapshot_and_stable_focused_id() {
        launch()
        val trace = JSONObject(File(context.noBackupFilesDir, "round11-browse-trace.json").readText())
        assertTrue("独立新进程观察必须包含可解码的完整查询快照", trace.has("snapshot"))
        assertEquals(fixture.initial.query, BrowseSnapshotCodec.decode(trace.getString("snapshot")).query)
        fun focusedIds(node: android.view.accessibility.AccessibilityNodeInfo): List<String> =
            (if (node.isFocused) listOfNotNull(node.viewIdResourceName) else emptyList()) +
                (0 until node.childCount).flatMap { node.getChild(it)?.let(::focusedIds) ?: emptyList() }
        instrumentation.uiAutomation.waitForIdle(100, 3_000)
        assertEquals(listOf(target.value), focusedIds(requireNotNull(instrumentation.uiAutomation.rootInActiveWindow)))
    }

    private fun mutateFixture(action: (JSONObject) -> Unit) {
        val file = File(context.noBackupFilesDir, BrowseProbeFixture.FILE)
        val root = JSONObject(file.readText()); action(root); file.writeText(root.toString())
    }
    private fun hashes(): Map<String, String> = File(context.filesDir, "datastore").listFiles().orEmpty()
        .filter { it.isFile }.associate { it.name to MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString("") { b -> "%02x".format(b) } }
    private fun recordEvidence(name: String, value: JSONObject) {
        instrumentation.sendStatus(2, android.os.Bundle().apply { putString("round11.$name", value.toString()) })
    }
    private fun closeActivity() { scenario!!.close(); scenario = null; until { BrowseProbeTestHooks.activeLeases.get() == 0 } }
    private fun acknowledged() {
        val done = CompletableDeferred<CommitResult>()
        runtime { rt -> rt.scope.launch { done.complete(rt.flush()) } }
        assertEquals(CommitResult.Committed, runBlocking { withTimeout(10_000) { done.await() } })
    }
    @Test fun file_reopen_preserves_three_exact_contexts_and_existing_user_rows() = runBlocking<Unit> {
        val before = LegacyHandoffRuntime.writers.exclusive { LegacyHandoffRuntime.capture(context); hashes() }
        val states = BrowseTopLevel.entries.mapIndexed { index, top -> RestoreNavigationState(fixture.initial.copy(
            contextKey = top.contextKey(requireNotNull(fixture.initial.query)), route = top.route,
            parentRoute = "测试父页面", focusedWorkId = fixture.works[99 - index * 20].id,
            anchorWorkId = fixture.works[95 - index * 20].id, relativeOffset = -.125f, savedAt = 123 + index.toLong())) }
        val playback = PlaybackSnapshot(target, null, VariantId("测试版本"), "测试会话", 8, 1234, 9999, WatchState.IN_PROGRESS, 100)
        val capture = CatalogCaptureEntity("测试原始捕获", "测试私有来源", null, "原始资料\u0000\uD800", "[]")
        DebugBrowseProbeGate.withOwnedProfile(context) { db ->
            assertEquals(CommitResult.Committed, RoomFavoritesStore(db).setFavorite(target.value, true))
            db.playbackSnapshots().upsert(PlaybackSnapshotEntity.from(playback))
            db.catalogIdentity().source(CatalogSourceEntity("测试私有来源", true)); db.catalogIdentity().capture(capture)
            db.metadataCache().upsert(WorkMetadataCacheEntity(target.value, CatalogItemCodec.encode(CatalogItem(target, "可清理资料"))))
            states.forEach { assertEquals(CommitResult.Committed, RoomBrowseStateStore(db).saveActive(it)) }
            assertEquals(1, db.metadataCache().evictAll())
        }
        DebugBrowseProbeGate.withOwnedProfile(context) { db ->
            assertEquals(6, withContext(Dispatchers.IO) { db.openHelper.readableDatabase.version })
            states.forEach { expected ->
                val store = RoomBrowseStateStore(db)
                assertEquals(expected.snapshot, store.load(expected.snapshot.contextKey))
                assertEquals(expected, store.loadLatest(BrowseTopLevel.entries.single { it.route == expected.snapshot.route }))
                assertEquals(BrowseSnapshotCodec.encode(expected.snapshot), db.browseSnapshots().find(expected.snapshot.contextKey)!!.payload)
            }
            assertEquals(states.last(), RoomBrowseStateStore(db).loadActive())
            assertNotNull(db.userRecords().favorite(target.value)); assertEquals(1, db.userRecords().referenceCount())
            assertEquals(playback, db.playbackSnapshots().find(target.value, "", "测试版本")!!.snapshot())
            assertEquals(capture, db.catalogIdentity().capture(Utf16KeyConverter().encode("测试原始捕获")))
            assertNull(db.metadataCache().find(target.value))
        }
        val after = LegacyHandoffRuntime.writers.exclusive { hashes() }
        recordEvidence("preservation", JSONObject().put("before", JSONObject(before)).put("after", JSONObject(after)).put("schema", 6))
        assertEquals(before, after)
    }
    @Test fun activity_recreation_same_pid_preserves_query_focus_and_drops_modal() {
        launch(); val pid = android.os.Process.myPid(); acknowledged()
        runtime { it.openModal() }; focus("关闭筛选")
        scenario!!.recreate(); focus(target.value)
        runtime { assertFalse(it.modal); assertFalse(it.isDetail); assertEquals(fixture.initial.query, it.navigation!!.snapshot.query) }
        assertEquals(pid, android.os.Process.myPid())
    }
    @Test fun source_year_sort_switch_roundtrips_after_activity_recreation() {
        val old = fixture.initial
        val alternate = requireNotNull(fixture.alternative.query)
        val changed = CatalogQuery.create("测试独立来源1995", alternate.contentKind, alternate.languageScope,
            alternate.countryIds, alternate.genreIds, 1995, 1995, CatalogSort.YEAR_ASCENDING, setOf(SourceId("test-other-source")))
        mutateFixture { root ->
            val next = fixture.alternative.copy(contextKey = BrowseTopLevel.LIBRARY.contextKey(changed), query = changed)
            root.put("alternative", BrowseSnapshotCodec.encode(next)); root.getJSONArray("works").getJSONObject(100).put("source", "test-other-source")
        }
        launch(); runtime { it.changeQuery() }; focus(requireNotNull(fixture.alternative.focusedWorkId).value)
        acknowledged(); scenario!!.recreate(); focus(requireNotNull(fixture.alternative.focusedWorkId).value)
        runtime { assertEquals(changed, it.navigation!!.snapshot.query); assertEquals(1, it.items.size) }
        closeActivity()
        runBlocking { DebugBrowseProbeGate.withOwnedProfile(context) { db -> assertEquals(old.query, RoomBrowseStateStore(db).load(old.contextKey)!!.query) } }
    }
    @Test fun offline_partial_local_snapshot_and_metadata_completion_preserve_query_focus() {
        val title = "这是一部用于验证中文长标题在五列与三列布局以及活动重建之后仍保留稳定身份和完整查询的合成作品"
        mutateFixture { root -> root.put("complete", false); root.getJSONArray("works").getJSONObject(99).put("title", title) }
        launch(); runtime { assertEquals(QueryCoverage.PARTIAL, it.result!!.coverage); assertNull(it.result!!.knownMatchedCount); assertEquals(fixture.initial.query, it.navigation!!.snapshot.query) }
        runtime { it.changeColumns() }; focus(target.value); acknowledged()
        var visibleTitle = false
        scenario!!.onActivity { visibleTitle = nodes(it.window.decorView).any { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { it.text == title } } }
        assertTrue(visibleTitle)
        // Local metadata completion is deliberately not a remote image-pipeline test.
        mutateFixture { it.put("complete", true) }
        scenario!!.recreate(); focus(target.value)
        runtime { assertEquals(QueryCoverage.COMPLETE, it.result!!.coverage); assertEquals(100L, it.result!!.knownMatchedCount); assertEquals(fixture.initial.query, it.navigation!!.snapshot.query) }
    }
    @Test fun cancelled_transaction_before_commit_rolls_back_all_navigation_pointers() = runBlocking<Unit> {
        DebugBrowseProbeGate.withOwnedProfile(context) { db ->
            val store = RoomBrowseStateStore(db); val old = RestoreNavigationState(fixture.initial)
            assertEquals(CommitResult.Committed, store.saveActive(old))
            val entered = CompletableDeferred<Unit>(); val block = CompletableDeferred<Unit>()
            val job = launch { db.withTransaction {
                db.browseSnapshots().upsert(BrowseSnapshotEntity(old.snapshot.contextKey, "未提交的合成资料"))
                entered.complete(Unit); block.await()
            } }
            withTimeout(3_000) { entered.await() }; job.cancelAndJoin()
            assertEquals(old, store.loadActive()); assertEquals(old.snapshot, store.load(old.snapshot.contextKey))
        }
    }
    @Test fun cancellation_after_acknowledged_commit_preserves_last_successful_snapshot() = runBlocking<Unit> {
        val expected = RestoreNavigationState(fixture.initial)
        DebugBrowseProbeGate.withOwnedProfile(context) { db ->
            val committed = CompletableDeferred<Unit>(); val block = CompletableDeferred<Unit>()
            val job = launch { assertEquals(CommitResult.Committed, RoomBrowseStateStore(db).saveActive(expected)); committed.complete(Unit); block.await() }
            withTimeout(3_000) { committed.await() }; job.cancelAndJoin()
        }
        DebugBrowseProbeGate.withOwnedProfile(context) { assertEquals(expected, RoomBrowseStateStore(it).loadActive()) }
    }
    @Test fun failing_room_transaction_keeps_old_active_and_retry_pending() = runBlocking<Unit> {
        DebugBrowseProbeGate.withOwnedProfile(context) { db ->
            val store = RoomBrowseStateStore(db); val old = RestoreNavigationState(fixture.initial)
            assertEquals(CommitResult.Committed, store.saveActive(old))
            val checkpointScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val coordinator = BrowseCheckpointCoordinator(store, checkpointScope)
                val next = old.copy(snapshot = old.snapshot.copy(focusedWorkId = fixture.works[98].id))
                val token = coordinator.begin(next)
                val keyHex = Utf16KeyConverter().encode(RoomBrowseStateStore.ACTIVE_NAVIGATION_KEY).joinToString("") { "%02x".format(it) }
                withContext(Dispatchers.IO) { db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_r11_active BEFORE INSERT ON browse_snapshots WHEN NEW.contextKey=X'$keyHex' BEGIN SELECT RAISE(ABORT, '合成存储故障'); END") }
                assertEquals(CommitResult.Failed("storage_sql"), coordinator.flush(token)); assertTrue(coordinator.hasPending)
                assertEquals(old, store.loadActive()); assertEquals(old.snapshot, store.load(old.snapshot.contextKey))
                withContext(Dispatchers.IO) { db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_r11_active") }
                assertEquals(CommitResult.Committed, coordinator.flush(token)); assertFalse(coordinator.hasPending); assertEquals(next, store.loadActive())
            } finally { checkpointScope.cancel() }
        }
    }
    @Test fun cancelled_checkpoint_before_commit_keeps_retryable_state() = runBlocking<Unit> {
        DebugBrowseProbeGate.withOwnedProfile(context) { db ->
            val store = RoomBrowseStateStore(db); val expected = RestoreNavigationState(fixture.initial)
            val checkpointScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val entered = CompletableDeferred<Unit>(); val hold = CompletableDeferred<Unit>()
                val delayed = object : BrowseNavigationStore by store {
                    override suspend fun saveActive(state: RestoreNavigationState): CommitResult { entered.complete(Unit); hold.await(); return store.saveActive(state) }
                }
                val coordinator = BrowseCheckpointCoordinator(delayed, checkpointScope); val token = coordinator.begin(expected)
                val job = launch { coordinator.flush(token) }; withTimeout(3_000) { entered.await() }; job.cancelAndJoin()
                assertTrue(coordinator.hasPending); assertNull(coordinator.lastResult); assertNull(store.loadActive())
                hold.complete(Unit); assertEquals(CommitResult.Committed, coordinator.flush(token)); assertEquals(expected, store.loadActive())
            } finally { checkpointScope.cancel() }
        }
    }
    @Test fun two_rapid_page_changes_and_background_flush_keep_new_context() {
        launch(); val hold = CompletableDeferred<Unit>()
        runtime { BrowseProbeTestHooks.beforeSave = hold; it.positioned(it.visibleAnchor, it.visibleOffset); it.background() }
        until { BrowseProbeTestHooks.waitingSaves.get() > 0 }
        runtime { it.switch(BrowseTopLevel.FAVORITES); it.switch(BrowseTopLevel.HISTORY) }
        runtime { BrowseProbeTestHooks.beforeSave = null; hold.complete(Unit) }
        until { var history = false; runtime { history = it.top == BrowseTopLevel.HISTORY && !it.busy }; history }
        // An unsaved history context has no poster target; actual page-control focus is honest.
        focus("片库"); acknowledged(); closeActivity()
        runBlocking { DebugBrowseProbeGate.withOwnedProfile(context) { db -> assertEquals("history", RoomBrowseStateStore(db).loadActive()!!.snapshot.route) } }
    }
    @Test fun missing_or_corrupt_profile_and_consent_fail_before_database_creation() = runBlocking<Unit> {
        for (name in listOf(LegacyHandoffRuntime.PROFILE, LegacyHandoffRuntime.CONSENT)) {
            val file = File(context.noBackupFilesDir, name); val saved = file.readText()
            for (corrupt in listOf(false, true)) {
                if (corrupt) file.writeText("损坏的合成授权") else file.delete()
                var entered = false
                try { DebugBrowseProbeGate.withOwnedProfile(context) { entered = true }; fail("缺少授权不应进入候选") }
                catch (_: IllegalStateException) { }
                assertFalse(entered); assertFalse(context.getDatabasePath(DebugBrowseProbeGate.DATABASE).exists())
            }
            file.writeText(saved)
        }
    }
    @Test fun existing_legacy_record_refuses_candidate_and_preserves_exact_file_hashes() = runBlocking<Unit> {
        context.browsePrefs.edit { it[stringPreferencesKey("round11-existing-legacy")] = "旧资料必须保留\u0000" }
        val before = LegacyHandoffRuntime.writers.exclusive { LegacyHandoffRuntime.capture(context); hashes() }
        var entered = false
        try { DebugBrowseProbeGate.withOwnedProfile(context) { entered = true }; fail("非空旧资料必须关闭候选") } catch (_: IllegalStateException) { }
        assertFalse(entered); assertFalse(context.getDatabasePath(DebugBrowseProbeGate.DATABASE).exists()); assertEquals(before, hashes())
        recordEvidence("legacy-refusal", JSONObject().put("before", JSONObject(before)).put("after", JSONObject(hashes())))
    }
    @Test fun corrupt_database_fails_closed_without_replacement() = runBlocking<Unit> {
        val file = context.getDatabasePath(DebugBrowseProbeGate.DATABASE); file.parentFile!!.mkdirs(); file.writeText("损坏的合成数据库")
        val before = file.readBytes()
        val sidecars = listOf("-wal", "-shm", "-journal").associateWith { suffix -> File(file.path + suffix).let { if (it.exists()) it.readBytes().toList() else null } }
        var entered = false
        try { DebugBrowseProbeGate.withOwnedProfile(context) { entered = true }; fail("损坏数据库不应进入候选") } catch (_: android.database.sqlite.SQLiteException) { }
        assertFalse(entered); assertArrayEquals(before, file.readBytes())
        sidecars.forEach { (suffix, bytes) -> assertEquals(bytes, File(file.path + suffix).let { if (it.exists()) it.readBytes().toList() else null }) }
        fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        recordEvidence("corrupt-preservation", JSONObject().put("before", digest(before)).put("after", digest(file.readBytes())).put("sidecars", JSONObject(sidecars)))
    }
    @Test fun candidate_lease_serializes_other_candidate_and_refuses_legacy_writer() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); val secondEntered = CompletableDeferred<Unit>()
        val first = launch { DebugBrowseProbeGate.withOwnedProfile(context) { entered.complete(Unit); release.await() } }
        withTimeout(3_000) { entered.await() }
        val second = launch { DebugBrowseProbeGate.withOwnedProfile(context) { secondEntered.complete(Unit) } }
        delay(100); assertFalse(secondEntered.isCompleted)
        try { DataStoreBrowseStore(context).save(BrowseMemory("合成被拒绝", 9)); fail("候选ON禁止旧writer") } catch (_: IllegalStateException) { }
        release.complete(Unit); first.join(); withTimeout(3_000) { secondEntered.await() }; second.join()
    }
    @Test fun bounded_external_save_delay_exposes_uncommitted_boundary() {
        mutateFixture { it.put("saveDelayMillis", 2_000) }
        launch()
        val end = android.os.SystemClock.uptimeMillis() + 3_000
        var waiting = false
        while (!waiting && android.os.SystemClock.uptimeMillis() < end) {
            waiting = JSONObject(File(context.noBackupFilesDir, "round11-browse-trace.json").readText()).optBoolean("waitingSave")
            Thread.sleep(20)
        }
        assertTrue("有限私有夹具保存暂停必须向外部标明未提交边界", waiting)
    }
    @Test fun sqlite_page_limit_failure_without_disk_fill_keeps_last_commit() = runBlocking<Unit> {
        DebugBrowseProbeGate.withOwnedProfile(context) { db ->
            val store = RoomBrowseStateStore(db); val old = RestoreNavigationState(fixture.initial)
            assertEquals(CommitResult.Committed, store.saveActive(old))
            db.withTransaction {
                // Pin the actual writer connection; an unpinned PRAGMA uses a read-pool connection.
                val sql = db.openHelper.writableDatabase
                val count = sql.query("PRAGMA page_count").use { it.moveToFirst(); it.getLong(0) }
                sql.query("PRAGMA max_page_count=$count").use { assertTrue(it.moveToFirst()); assertEquals(count, it.getLong(0)) }
            }
            val oversized = old.copy(snapshot = old.snapshot.copy(parentRoute = "合成存储上限".repeat(20_000)))
            assertEquals(CommitResult.Failed("storage_sql"), store.saveActive(oversized))
            assertEquals(old, store.loadActive()); assertEquals(old.snapshot, store.load(old.snapshot.contextKey))
        }
    }
    @Test fun prepare_external_lifecycle() {
        launch(); instrumentation.waitForIdleSync(); acknowledged()
        var stable: BrowseSnapshot? = null
        runtime { stable = it.navigation!!.snapshot }
        closeActivity()
        runBlocking { DebugBrowseProbeGate.withOwnedProfile(context) { db ->
            val saved = RoomBrowseStateStore(db).loadActive()!!.snapshot
            assertEquals(stable!!.anchorWorkId, saved.anchorWorkId); assertEquals(stable!!.relativeOffset, saved.relativeOffset, .002f)
            assertEquals(target, saved.focusedWorkId); assertEquals(fixture.initial.query, saved.query)
            assertTrue(saved.anchorWorkId in fixture.works.drop(75).map { it.id })
            File(context.noBackupFilesDir, "round11-external-expected.json").writeText(BrowseSnapshotCodec.encode(saved))
        } }
    }
    @Test fun prepare_external_uncommitted_delay() {
        prepare_external_lifecycle()
        mutateFixture { it.put("saveDelayMillis", 10_000) }
    }

    @Test fun shutdown_cannot_replace_last_stable_viewport_checkpoint() {
        launch(); instrumentation.waitForIdleSync(); acknowledged()
        var stable: BrowseSnapshot? = null; var width = 0; var height = 0
        runtime { stable = it.navigation!!.snapshot; width = it.viewportWidth; height = it.viewportHeight }
        recordEvidence("before-stop", JSONObject().put("width", width).put("height", height).put("snapshot", BrowseSnapshotCodec.encode(stable!!)))
        closeActivity()
        runBlocking { DebugBrowseProbeGate.withOwnedProfile(context) { db ->
            val saved = RoomBrowseStateStore(db).loadActive()!!.snapshot
            recordEvidence("after-stop", JSONObject().put("snapshot", BrowseSnapshotCodec.encode(saved)))
            assertEquals("停止时的窗口变化不能覆盖已确认的稳定锚点", stable!!.anchorWorkId, saved.anchorWorkId)
            assertEquals(stable!!.relativeOffset, saved.relativeOffset, .002f)
        } }
    }
    @Test fun recreation_same_geometry_preserves_exact_anchor_and_relative_offset() {
        launch(); instrumentation.waitForIdleSync(); acknowledged()
        var anchor: WorkId? = null; var offset = 0f; var width = 0; var height = 0
        runtime { anchor = it.visibleAnchor; offset = it.visibleOffset; width = it.viewportWidth; height = it.viewportHeight }
        scenario!!.recreate(); focus(target.value)
        until { var same = false; runtime { same = it.viewportWidth == width && it.viewportHeight == height }; same }
        runtime { assertEquals(anchor, it.visibleAnchor); assertEquals(offset, it.visibleOffset, .002f) }
    }
    @Test fun first_nonzero_grid_measure_restores_nonzero_offset_before_focus() {
        val desired = fixture.initial.copy(focusedWorkId = fixture.works[54].id, anchorWorkId = fixture.works[45].id, relativeOffset = -.15f)
        runBlocking { DebugBrowseProbeGate.withOwnedProfile(context) { assertEquals(CommitResult.Committed, RoomBrowseStateStore(it).saveActive(RestoreNavigationState(desired))) } }
        val load = CompletableDeferred<Unit>(); BrowseProbeTestHooks.beforeLoad = load
        launch(false)
        awaitBrowseProbe()
        scenario!!.onActivity { it.window.setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT, 150) }
        BrowseProbeTestHooks.blockPlacement = true
        runtime { BrowseProbeTestHooks.beforeLoad = null; load.complete(Unit) }
        until { var zero = false; runtime { zero = it.navigation != null && it.viewportWidth > 0 && it.viewportHeight == 0 }; zero }
        instrumentation.waitForIdleSync()
        scenario!!.onActivity { it.window.setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.MATCH_PARENT) }
        until { var measured = false; runtime { measured = it.viewportHeight > 0 }; measured }
        runtime { BrowseProbeTestHooks.blockPlacement = false }
        focus(fixture.works[54].id.value)
        runtime { assertEquals(desired.anchorWorkId, it.visibleAnchor); assertEquals(desired.relativeOffset, it.visibleOffset, .002f) }
    }
    @Test fun old_initial_query_load_cannot_replace_new_explicit_query_and_layout() {
        val oldLoad = CompletableDeferred<Unit>(); BrowseProbeTestHooks.beforeLoad = oldLoad
        launch(false)
        until { var present = false; scenario!!.onActivity { present = it.browseProbe != null }; present }
        runtime { it.changeQuery() }; focus(requireNotNull(fixture.alternative.focusedWorkId).value)
        runtime { BrowseProbeTestHooks.beforeLoad = null; oldLoad.complete(Unit) }
        repeat(20) {
            Thread.sleep(50)
            runtime { assertEquals("迟到Q1不能覆盖显式Q2", fixture.alternative.query, it.navigation!!.snapshot.query) }
            assertEquals(listOf(requireNotNull(fixture.alternative.focusedWorkId).value), focused())
        }
    }
    @Test fun removed_modal_invoker_returns_same_toolbar_neighbor() {
        launch(); runtime { it.controlFocused("筛选"); it.openModal() }; focus("关闭筛选")
        runtime { it.removeModalInvoker() }; key(KeyEvent.KEYCODE_BACK); focus("列数")
    }
    @Test fun stable_focus_burst_writes_exactly_one_real_room_transaction() = runBlocking<Unit> {
        DebugBrowseProbeGate.withOwnedProfile(context) { db -> withContext(Dispatchers.IO) {
            val sql = db.openHelper.writableDatabase
            sql.execSQL("CREATE TABLE round11_test_write_audit(count INTEGER NOT NULL)")
            sql.execSQL("INSERT INTO round11_test_write_audit VALUES(0)")
            val keyHex = Utf16KeyConverter().encode(RoomBrowseStateStore.ACTIVE_NAVIGATION_KEY).joinToString("") { "%02x".format(it) }
            for (operation in listOf("INSERT", "UPDATE")) sql.execSQL("CREATE TRIGGER round11_audit_$operation AFTER $operation ON browse_snapshots WHEN NEW.contextKey=X'$keyHex' BEGIN UPDATE round11_test_write_audit SET count=count+1; END")
        } }
        val release = CompletableDeferred<Unit>(); BrowseProbeTestHooks.beforeSave = release
        launch()
        repeat(20) {
            instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
            instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT))
        }
        focus(fixture.works[79].id.value)
        until { BrowseProbeTestHooks.waitingSaves.get() > 0 }
        fun actualWrites(): Int {
            val db = CinemaDatabaseFactory.open(context, DebugBrowseProbeGate.DATABASE)
            return try { db.openHelper.readableDatabase.query("SELECT count FROM round11_test_write_audit").use { it.moveToFirst(); it.getInt(0) } } finally { db.close() }
        }
        assertEquals(0, actualWrites())
        runtime { BrowseProbeTestHooks.beforeSave = null; release.complete(Unit) }
        until { actualWrites() > 0 }
        assertEquals(1, actualWrites())
        runtime { assertEquals(fixture.works[79].id, it.navigation!!.snapshot.focusedWorkId) }
        val verification = CinemaDatabaseFactory.open(context, DebugBrowseProbeGate.DATABASE)
        try { assertEquals(fixture.works[79].id, RoomBrowseStateStore(verification).loadActive()!!.snapshot.focusedWorkId) }
        finally { verification.close() }
    }
    @Test fun pause_resume_does_not_revive_cancelled_poster_restore() {
        BrowseProbeTestHooks.disableWorkFocus = true; launch(false)
        until { BrowseProbeTestHooks.failedFocusRequests.get() > 0 }
        key(KeyEvent.KEYCODE_DPAD_LEFT); focus("片库")
        scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        runtime { assertFalse(it.foreground); BrowseProbeTestHooks.disableWorkFocus = false }
        scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        focus("片库")
        runtime { assertTrue(it.foreground); assertNull(it.restoreToken) }
        assertFalse(focused().contains(target.value))
    }
    @Test fun old_pending_layout_cannot_focus_after_new_query_is_installed() {
        BrowseProbeTestHooks.blockPlacement = true; launch(false)
        awaitBrowseProbe()
        until { var loaded = false; runtime { loaded = it.navigation?.snapshot?.query == fixture.initial.query }; loaded }
        runtime { it.changeQuery() }
        until { var changed = false; runtime { changed = it.navigation?.snapshot?.query == fixture.alternative.query }; changed }
        assertFalse(focused().contains(target.value))
        runtime { BrowseProbeTestHooks.blockPlacement = false }
        focus(requireNotNull(fixture.alternative.focusedWorkId).value)
        runtime { assertEquals(fixture.alternative.query, it.navigation!!.snapshot.query) }
        assertFalse(focused().contains(target.value))
    }
    private fun coldSnapshotWithRemovedWorks(removed: Set<Int>) {
        val original = fixture.initial
        runBlocking { DebugBrowseProbeGate.withOwnedProfile(context) { db ->
            assertEquals(CommitResult.Committed, RoomBrowseStateStore(db).saveActive(RestoreNavigationState(original)))
        } }
        mutateFixture { root ->
            val works = root.getJSONArray("works")
            removed.sortedDescending().forEach { works.remove(it) }
        }
    }
    private fun assertColdDeepRemovalRestores(removed: Set<Int>, expectedIndex: Int) {
        val original = fixture.initial
        val expected = fixture.works[expectedIndex].id
        coldSnapshotWithRemovedWorks(removed)
        BrowseProbeTestHooks.blockPlacement = true; launch(false)
        until { var loaded = false; scenario!!.onActivity { loaded = it.browseProbe?.navigation != null && it.browseProbe?.result?.coverage == QueryCoverage.COMPLETE }; loaded }
        assertTrue("首次冷恢复完成布局前不能抢焦点到首项", focused().none { it in fixture.works.map { work -> work.id.value } })
        runtime { BrowseProbeTestHooks.blockPlacement = false }
        focus(expected.value)
        runtime {
            assertEquals(original.query, it.navigation!!.snapshot.query)
            assertEquals(expected, it.navigation!!.snapshot.focusedWorkId)
            assertTrue("恢复目标必须来自真实深层视口", it.items.indexOfFirst { work -> work.id == it.visibleAnchor } >= 80)
        }
        assertEquals(listOf(expected.value), focused())
    }
    @Test fun cold_complete_removed_anchor_keeps_existing_deep_target() {
        assertColdDeepRemovalRestores(setOf(95), 99)
    }
    @Test fun cold_complete_removed_anchor_and_target_restores_same_row_neighbor() {
        assertColdDeepRemovalRestores(setOf(95, 99), 98)
    }
    @Test fun background_flush_and_navigation_share_same_room_acknowledgement() {
        launch(); instrumentation.waitForIdleSync(); acknowledged()
        val release = CompletableDeferred<Unit>()
        runtime { BrowseProbeTestHooks.beforeSave = release; it.positioned(it.visibleAnchor, it.visibleOffset); it.background() }
        until { BrowseProbeTestHooks.waitingSaves.get() == 1 }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var busy = false; runtime { busy = it.busy }; busy }
        runtime { BrowseProbeTestHooks.beforeSave = null; release.complete(Unit) }
        focus("详情返回")
        runtime { assertTrue(it.isDetail); assertEquals(CommitResult.Committed, it.lastCommit); assertFalse(it.status.contains("尚未保存")) }
        closeActivity()
        runBlocking { DebugBrowseProbeGate.withOwnedProfile(context) { db ->
            val saved = RoomBrowseStateStore(db).loadActive()!!
            assertEquals(listOf(BrowseChildRoute.DETAIL), saved.returnChain)
            assertEquals(target, saved.snapshot.focusedWorkId); assertEquals(fixture.initial.query, saved.snapshot.query)
        } }
    }
}
