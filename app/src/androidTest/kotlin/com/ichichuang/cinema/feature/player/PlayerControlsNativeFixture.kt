package com.ichichuang.cinema.feature.player

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.MainActivity
import com.ichichuang.cinema.core.navigation.DebugBrowseProbeGate
import com.ichichuang.cinema.data.userdata.LegacyHandoffRuntime
import com.ichichuang.cinema.data.userdata.PlaybackTarget
import com.ichichuang.cinema.data.userdata.RoomPersonalLibraryRepository
import com.ichichuang.cinema.data.userdata.RoomPlaybackProgressStore
import com.ichichuang.cinema.domain.model.catalog.CatalogQuery
import com.ichichuang.cinema.domain.model.catalog.CatalogSort
import com.ichichuang.cinema.domain.model.catalog.ContentKind
import com.ichichuang.cinema.domain.model.catalog.EpisodeId
import com.ichichuang.cinema.domain.model.catalog.LanguageScope
import com.ichichuang.cinema.domain.model.catalog.SourceId
import com.ichichuang.cinema.domain.model.catalog.VariantId
import com.ichichuang.cinema.domain.model.catalog.WorkId
import com.ichichuang.cinema.domain.model.userdata.CommitResult
import com.ichichuang.cinema.domain.model.userdata.PlaybackSnapshot
import com.ichichuang.cinema.domain.model.userdata.WatchState
import com.ichichuang.cinema.data.local.codec.BrowseSnapshotCodec
import com.ichichuang.cinema.domain.model.userdata.BrowseSnapshot
import com.ichichuang.cinema.feature.browse.BrowseProbeFixture
import com.ichichuang.cinema.feature.browse.BrowseProbeTestHooks
import com.ichichuang.cinema.feature.detail.WorkDetailTestHooks
import com.ichichuang.cinema.feature.home.visual.VisualHomeTestHooks
import com.ichichuang.cinema.feature.library.LibraryTestHooks
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import org.junit.Assert.assertEquals
import java.util.UUID

/**
 * R16 播放器控制原生测试共享夹具：八探针全开配对、一次性私有档案
 * （可拖动 MP4 / 直播式 HLS / WAV / 损坏媒体），身份与 R15 详情档案同源。
 */
internal object PlayerControlsNativeFixture {
    val w = (0..5).map { WorkId(UUID.nameUUIDFromBytes("round15-private-$it".toByteArray()).toString()) }

    const val VARIANT_A = "round15-variant-a"
    const val VARIANT_B = "round15-variant-b"
    const val VARIANT_E = "round15-variant-e"
    const val VARIANT_F = "round15-variant-f"
    const val VARIANT_H = "round15-variant-h"
    const val EPISODE_A1 = "round15-w0-a-1"   // 合成 M4A 音频（时长已知、可拖动）
    const val EPISODE_A2 = "round15-w0-a-2"   // 原始 ADTS 流（探针禁用常率 seek 推断：不可拖动、时长可知；时长未知媒体见报告 BLOCKED 披露）
    const val EPISODE_B1 = "round15-w0-b-1"   // WAV（跨版本身份核对）
    const val EPISODE_E1 = "round15-w1-1"
    const val EPISODE_F1 = "round15-w2-1"
    const val EPISODE_F2 = "round16-w2-corrupt" // 损坏媒体（真实解析错误）
    const val EPISODE_H1 = "round15-w5-1"   // 合成 MP4（真实视频帧证据）
    const val RESUME_POSITION_MS = 8_000L
    const val MP4_SECONDS = 24

    val query = CatalogQuery.create("测试查询-详情1994", ContentKind.MOVIE, LanguageScope.NON_CHINESE,
        setOf("JP"), setOf("horror"), 1994, 1994, CatalogSort.YEAR_DESCENDING, setOf(SourceId("test-source")))

    fun wav(context: Context, name: String) = File(context.filesDir, "round15-$name.wav")

    /** 真实进入播放层的最短路径：夹具直备 + R15 详情管线（库→详情→OK 播放）。 */
    fun setup(context: Context, seedResumeEpisode: String = EPISODE_A1, phaseAware: Boolean = false) {
        val arguments = InstrumentationRegistry.getArguments()
        check(com.ichichuang.cinema.BuildConfig.BROWSE_RESTORE_PROBE && com.ichichuang.cinema.BuildConfig.PLAYBACK_ROOM_PROBE)
        check(arguments.getString("cinema.disposable") == "round06")
        check(arguments.getString("cinema.handoff") == "round07")
        check(arguments.getString("cinema.browse") == "round11")
        check(arguments.getString("cinema.personal") == "round12")
        check(arguments.getString("cinema.home") == "round13")
        check(arguments.getString("cinema.library") == "round14")
        check(com.ichichuang.cinema.BuildConfig.LIBRARY_FILTER_PROBE)
        check(com.ichichuang.cinema.BuildConfig.DETAIL_PROBE)
        check(arguments.getString("cinema.detail") == "round15")
        if (com.ichichuang.cinema.BuildConfig.PLAYER_CONTROLS_PROBE)
            check(arguments.getString("cinema.player") == "round16")
        until { BrowseProbeTestHooks.activeLeases.get() == 0 && VisualHomeTestHooks.activeLeases.get() == 0 &&
            LibraryTestHooks.activeLeases.get() == 0 && WorkDetailTestHooks.activeLeases.get() == 0 }
        File(context.noBackupFilesDir, LegacyHandoffRuntime.PROFILE).writeText(LegacyHandoffRuntime.PROFILE_TOKEN)
        File(context.noBackupFilesDir, LegacyHandoffRuntime.CONSENT).writeText(LegacyHandoffRuntime.CONSENT_TOKEN)
        File(context.noBackupFilesDir, "round06-playback-test.authorization").writeText("cinema-round06-disposable-local-media")
        File(context.noBackupFilesDir, com.ichichuang.cinema.feature.personal.PersonalLibraryAuthorization.FILE)
            .writeText(com.ichichuang.cinema.feature.personal.PersonalLibraryAuthorization.TOKEN)
        File(context.noBackupFilesDir, com.ichichuang.cinema.feature.home.visual.VisualHomeAuthorization.FILE)
            .writeText(com.ichichuang.cinema.feature.home.visual.VisualHomeAuthorization.TOKEN)
        File(context.noBackupFilesDir, com.ichichuang.cinema.feature.library.LibraryAuthorization.FILE)
            .writeText(com.ichichuang.cinema.feature.library.LibraryAuthorization.TOKEN)
        File(context.noBackupFilesDir, com.ichichuang.cinema.feature.detail.WorkDetailAuthorization.FILE)
            .writeText(com.ichichuang.cinema.feature.detail.WorkDetailAuthorization.TOKEN)
        File(context.noBackupFilesDir, PlayerControlsAuthorization.FILE).writeText(PlayerControlsAuthorization.TOKEN)
        check(!LegacyHandoffRuntime.present(context))
        if (!phaseAware) {
            context.deleteDatabase(DebugBrowseProbeGate.DATABASE)
            File(context.noBackupFilesDir, "round15-detail-context.json").delete()
            File(context.noBackupFilesDir, com.ichichuang.cinema.feature.detail.WorkDetailFixture.FILE).delete()
        }
        BrowseProbeTestHooks.holdWindowReady = false; BrowseProbeTestHooks.disableWorkFocus = false
        BrowseProbeTestHooks.failedFocusRequests.set(0); BrowseProbeTestHooks.focusTrace.clear()
        BrowseProbeTestHooks.beforeLoad = null; BrowseProbeTestHooks.beforeTopLevelLoad = null; BrowseProbeTestHooks.blockPlacement = false
        VisualHomeTestHooks.holdWindowReady = false; VisualHomeTestHooks.disableCardFocus = false
        VisualHomeTestHooks.blockPlacement = false; VisualHomeTestHooks.failedFocusRequests.set(0)
        LibraryTestHooks.holdFirstLoad = null; LibraryTestHooks.blockPlacement = false
        LibraryTestHooks.disableWorkFocus = false; LibraryTestHooks.failedFocusRequests.set(0)
        LibraryTestHooks.focusTrace.clear()
        LibraryTestHooks.appliedQueries.set(0); LibraryTestHooks.continuedLoads.set(0)
        WorkDetailTestHooks.holdRouteRefresh = null; WorkDetailTestHooks.beforePlayDispatch = null
        WorkDetailTestHooks.dispatchTrace.clear(); WorkDetailTestHooks.focusTrace.clear()
        WorkDetailTestHooks.disableActionFocus = false; WorkDetailTestHooks.failedFocusRequests.set(0)
        PlayerControlsTestHooks.holdSwitch = null; PlayerControlsTestHooks.holdBoundary = null
        PlayerControlsTestHooks.keyTrace.clear(); PlayerControlsTestHooks.commandTrace.clear()
        PlayerControlsTestHooks.focusTrace.clear(); PlayerControlsTestHooks.failedFocusRequests.set(0)
        PlayerControlsTestHooks.forbidSeekCalls = false; PlayerControlsTestHooks.seekCalls.set(0)
        PlayerControlsTestHooks.disableControlFocus = false
        PlayerControlsTestHooks.suppressIdleHide = false
        writeBrowseFixture(context)
        writeDetailFixture(context)
        if (!phaseAware) seedPersonal(context, seedResumeEpisode)
    }

    /** 从 androidTest APK 资产（仪器上下文）复制宿主 ffmpeg 本地合成媒体到目标应用的 filesDir。 */
    private fun copyAsset(context: Context, name: String): File {
        val file = File(context.filesDir, "round16-$name")
        if (!(file.isFile && file.length() > 0L)) {
            InstrumentationRegistry.getInstrumentation().context.assets.open("round16/$name").use { input ->
                file.outputStream().use { output -> input.copyTo(output) } }
        }
        return file
    }

    fun writeBrowseFixture(context: Context) {
        fun workJson(index: Int, title: String): JSONObject = JSONObject()
            .put("id", w[index].value).put("title", title)
            .put("source", "test-source").put("year", 1994)
            .put("countries", JSONArray(listOf("JP"))).put("genres", JSONArray(listOf("horror")))
            .put("kind", "MOVIE").put("languages", JSONArray(listOf("ja")))
            .put("favorite", false).put("history", false)
            .put("artwork", JSONObject.NULL).put("artworkShape", JSONObject.NULL)
        val works = JSONArray()
        works.put(workJson(0, "同题异身"))
        works.put(workJson(1, "同题异身"))
        works.put(workJson(2, "证据不全的档案"))
        works.put(workJson(3, "无线路的作品"))
        works.put(workJson(4, "只剩停用线路的作品"))
        works.put(workJson(5, "很长很长的合成作品标题六号用于播放器大字号与长文案核对"))
        // 清掉早前设备端合成尝试的陈旧产物（历史中间态，绝不复用）
        listOf("round16-video.mp4", "round16-audio.mp4", "round16-live.m3u8", "round16-live.aac", "round16-live.ts", "round16-corrupt.wav")
            .forEach { File(context.filesDir, it).delete() }
        check(copyAsset(context, "audio.mp4").length() > 50_000L) { "合成音频缺媒体（已知时长）" }
        check(copyAsset(context, "live.aac").length() > 50_000L) { "合成 ADTS 流缺失（未知时长）" }
        val video = copyAsset(context, "video.mp4")
        val corrupt = copyAsset(context, "corrupt.wav")
        check(video.length() > 100_000L) { "合成 MP4 缺失或过小" }
        val media = JSONArray()
        fun entry(work: Int, episode: String, variant: String, uri: String) {
            media.put(JSONObject().put("work", w[work].value).put("episode", episode)
                .put("variant", variant).put("uri", uri))
        }
        val audio = File(context.filesDir, "round16-audio.mp4")
        val adts = File(context.filesDir, "round16-live.aac")
        entry(0, EPISODE_A1, VARIANT_A, audio.toURI().toString())
        entry(0, EPISODE_A2, VARIANT_A, adts.toURI().toString())
        entry(0, EPISODE_B1, VARIANT_B, wav(context, "w0-b-1").also(::syntheticWav).toURI().toString())
        entry(1, EPISODE_E1, VARIANT_E, wav(context, "w1-1").also(::syntheticWav).toURI().toString())
        entry(2, EPISODE_F1, VARIANT_F, wav(context, "w2-1").also(::syntheticWav).toURI().toString())
        entry(2, EPISODE_F2, VARIANT_F, corrupt.toURI().toString())
        entry(5, EPISODE_H1, VARIANT_H, video.toURI().toString())
        val snapshot = BrowseSnapshot("library:${query.queryId}", "library", null, query, 1, w[5], w[5], 0f, 1)
        val root = JSONObject().put("initial", BrowseSnapshotCodec.encode(snapshot))
            .put("alternative", BrowseSnapshotCodec.encode(snapshot))
            .put("complete", true).put("works", works).put("order", JSONArray(w.map { it.value }))
            .put("media", media)
            .put("library", JSONObject().put("pageSize", 25).put("failingSources", JSONArray()).put("mirrorSources", JSONObject()))
        File(context.noBackupFilesDir, BrowseProbeFixture.FILE).writeText(root.toString())
    }

    /** 与 R15 同源详情档案（同名线路/停用/离线/STALE 线路 + 超长标签）。 */
    fun writeDetailFixture(context: Context) {
        fun episode(id: String, label: String?, order: Int?, uri: String?) = JSONObject()
            .put("episodeId", id).put("label", label ?: JSONObject.NULL)
            .put("order", order ?: JSONObject.NULL).put("uri", uri ?: JSONObject.NULL)
        fun route(id: String, entry: String, source: String, itemId: String, label: String, state: String,
                  episodes: JSONArray): JSONObject = JSONObject()
            .put("routeId", id).put("entryId", entry).put("sourceId", source)
            .put("itemId", itemId).put("label", label).put("state", state).put("episodes", episodes)
            .put("observedAt", 5).put("ruleVersion", "round16")
        fun uri(name: String) = File(context.filesDir, "round15-$name.wav").toURI().toString()
        val mp4Uri = File(context.filesDir, "round16-video.mp4").toURI().toString()
        val liveUri = File(context.filesDir, "round16-live.m3u8").toURI().toString()
        val corruptUri = File(context.filesDir, "round16-corrupt.wav").toURI().toString()
        fun routesJson(vararg routes: JSONObject): JSONArray = JSONArray().apply { routes.forEach { put(it) } }
        val works = JSONArray().apply {
            put(JSONObject().put("workId", w[0].value).put("routes", routesJson(
                route(VARIANT_A, "entry-a", "test-source", "item-a", "同名线路", "AVAILABLE", JSONArray(listOf(
                    episode(EPISODE_A1, "第一夜", 1, mp4Uri), episode(EPISODE_A2, "第二夜", 2, liveUri)))),
                route(VARIANT_B, "entry-b", "mirror-source", "item-b", "同名线路", "AVAILABLE", JSONArray(listOf(
                    episode(EPISODE_B1, "第一夜", 1, uri("w0-b-1"))))),
                route("round15-variant-c", "entry-c", "disabled-source", "item-c", "已停用线路", "DISABLED", JSONArray(listOf(
                    episode("round15-w0-c-1", "第一夜", 1, null)))),
                route("round15-variant-d", "entry-d", "offline-source", "item-d", "离线线路", "UNREACHABLE", JSONArray(listOf(
                    episode("round15-w0-d-1", null, null, null)))))))
            put(JSONObject().put("workId", w[1].value).put("routes", routesJson(
                route(VARIANT_E, "entry-e", "test-source", "item-e", "唯一线路", "AVAILABLE", JSONArray(listOf(
                    episode(EPISODE_E1, "序章", 1, uri("w1-1"))))))))
            put(JSONObject().put("workId", w[2].value).put("routes", routesJson(
                route(VARIANT_F, "entry-f", "test-source", "item-f", "默认线路", "AVAILABLE", JSONArray(listOf(
                    episode(EPISODE_F1, null, null, uri("w2-1")), episode(EPISODE_F2, "损坏的一集", 2, corruptUri)))))))
            put(JSONObject().put("workId", w[3].value).put("routes", JSONArray()))
            put(JSONObject().put("workId", w[4].value).put("routes", routesJson(
                route("round15-variant-g", "entry-g", "disabled-source", "item-g", "停用线路", "DISABLED", JSONArray(listOf(
                    episode("round15-w4-g-1", "第一夜", 1, null)))))))
            put(JSONObject().put("workId", w[5].value).put("routes", routesJson(
                route(VARIANT_H, "entry-h", "test-source", "item-h", "特别长的线路名称用于核对播放器焦点与动作可达性", "AVAILABLE", JSONArray(listOf(
                    episode(EPISODE_H1, "很长很长的剧集名称第一夜之后还有后续情节待核对", 1, uri("w5-1"))))),
                route("round15-variant-i", "entry-i", "mirror-source", "item-i", "特别长的线路名称用于核对播放器焦点与动作可达性", "STALE", JSONArray(listOf(
                    episode("round15-w5-i-1", "第二夜", 2, null)))))))
        }
        File(context.noBackupFilesDir, com.ichichuang.cinema.feature.detail.WorkDetailFixture.FILE)
            .writeText(JSONObject().put("works", works).toString())
    }

    /** 种子个人记录：w1 收藏、w2 历史、w0 精确 (seedEpisode, variant-a, 8000ms) 续播。 */
    fun seedPersonal(context: Context, seedEpisode: String = EPISODE_A1) = runBlocking {
        DebugBrowseProbeGate.withOwnedProfile(context) { database ->
            val repository = RoomPersonalLibraryRepository(database)
            val store = RoomPlaybackProgressStore(database)
            assertEquals(CommitResult.Committed, repository.rememberCard(w[1].value, "同题异身", "test-source"))
            assertEquals(CommitResult.Committed, repository.setFavorite(w[1].value, true))
            val historySession = "round16-history-${UUID.randomUUID()}"
            val historyTarget = PlaybackTarget(w[2], EpisodeId(EPISODE_F1), VariantId(VARIANT_F))
            assertEquals(CommitResult.Committed, repository.rememberCard(w[2].value, "证据不全的档案", "test-source"))
            assertEquals(CommitResult.Committed, store.beginSession(historyTarget, historySession))
            assertEquals(CommitResult.Committed, repository.confirmPlayback(historySession))
            assertEquals(CommitResult.Committed, repository.rememberCard(w[0].value, "同题异身", "test-source"))
            val session = "round16-resume-${UUID.randomUUID()}"
            val target = PlaybackTarget(w[0], EpisodeId(seedEpisode), VariantId(VARIANT_A))
            assertEquals(CommitResult.Committed, store.beginSession(target, session))
            assertEquals(CommitResult.Committed, repository.confirmPlayback(session))
            assertEquals(CommitResult.Committed, store.commit(PlaybackSnapshot(w[0], target.episodeId,
                target.variantId, session, 1, RESUME_POSITION_MS, (MP4_SECONDS * 1000).toLong(), WatchState.IN_PROGRESS, 1)))
        }
    }

    fun teardown(context: Context) {
        File(context.noBackupFilesDir, com.ichichuang.cinema.feature.detail.WorkDetailFixture.FILE).delete()
        File(context.noBackupFilesDir, "round15-detail-context.json").delete()
    }

    @Volatile var scenarioRef: ActivityScenario<MainActivity>? = null

    fun until(timeout: Long = 12_000, condition: () -> Boolean) {
        val end = android.os.SystemClock.uptimeMillis() + timeout
        while (!condition()) {
            if (android.os.SystemClock.uptimeMillis() > end) {
                var diagnostic = ""
                scenarioRef?.onActivity { activity ->
                    val browse = activity.browseProbe
                    val player = activity.playerControlsProbe
                    val personal = browse?.personal
                    val snapshotInfo = personal?.let { p ->
                        runCatching {
                            val row = kotlinx.coroutines.runBlocking {
                                p.database.playbackSnapshots().find(w[0].value, EPISODE_A1, VARIANT_A) }
                            " snap=${row?.snapshot()?.positionMs ?: -1} msg=${p.message.take(40)}"
                        }.getOrDefault(" snap=read-fail")
                    } ?: " snap=no-personal"
                    diagnostic = "detail=${browse?.isDetail} player=${browse?.isPlayer}$snapshotInfo " +
                        (player?.let { "overlay=${it.controller.overlayVisible} picker=${it.controller.picker} " +
                            "focus=${it.controller.focusedControlId} display=${it.display?.let { d -> "${d.status.statusText}/${d.positionMs}" }} " +
                            "keys=${PlayerControlsTestHooks.keyTrace.toList().takeLast(14)} commands=${PlayerControlsTestHooks.commandTrace.toList().takeLast(10)}" }
                            ?: "无播放器控制运行时")
                }
                throw AssertionError("等待实际原生状态超时；$diagnostic；" +
                    (scenarioRef?.let { semantics(it).filter { node -> node.focused || node.tag.isNotBlank() }.take(28).toString() } ?: "无页面"))
            }
            Thread.sleep(25)
        }
    }

    data class Node(val tag: String, val focused: Boolean, val selected: Boolean,
        val text: String, val description: String, val bounds: String = "")

    fun semantics(scenario: ActivityScenario<MainActivity>): List<Node> {
        var result = emptyList<Node>()
        scenario.onActivity { activity ->
            result = nodes(activity.window.decorView).map { node -> Node(
                node.config.getOrElse(SemanticsProperties.TestTag) { "" },
                node.config.getOrElse(SemanticsProperties.Focused) { false },
                node.config.getOrElse(SemanticsProperties.Selected) { false },
                node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString("") { it.text },
                node.config.joinToString("|") { "${it.key.name}=${it.value}" },
                node.boundsInWindow.toString()) }
        }
        return result
    }

    private fun nodes(view: View): List<SemanticsNode> {
        fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        if (view is ViewRootForTest) return flatten(view.semanticsOwner.unmergedRootSemanticsNode)
        return if (view is ViewGroup) (0 until view.childCount).flatMap { nodes(view.getChildAt(it)) } else emptyList()
    }

    // ── 真实物理按键（经窗口回调层，与 R15 同对） ──────────────────

    fun key(instrumentation: android.app.Instrumentation, code: Int) {
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, code))
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, code))
        instrumentation.waitForIdleSync()
    }

    fun rawKey(instrumentation: android.app.Instrumentation, action: Int, code: Int, repeat: Int = 0, flags: Int = 0) {
        val now = android.os.SystemClock.uptimeMillis()
        instrumentation.sendKeySync(KeyEvent(now, now, action, code, repeat, 0, 0, 0, flags))
    }

    /** 首页导航行 → 片库 → 真实 D-pad 走格到作品卡 → OK 详情 → OK 精确续播 → 播放器层。 */
    fun enterPlayerFromLibrary(scenario: ActivityScenario<MainActivity>,
        instrumentation: android.app.Instrumentation, workIndex: Int) {
        until { semantics(scenario).any { it.tag == "候选首页" } }
        until { semantics(scenario).any { it.focused } }
        repeat(6) { key(instrumentation, KeyEvent.KEYCODE_DPAD_UP) }
        until { semantics(scenario).any { it.focused && it.tag.startsWith("nav:") } }
        val order = com.ichichuang.cinema.feature.home.visual.VisualHomeRuntime.NAV_ITEMS
        var steps = 0
        while (steps < 8) {
            val current = semantics(scenario).firstOrNull { it.focused && it.tag.startsWith("nav:") }?.tag
            if (current == "nav:片库") break
            val currentIndex = order.indexOf(current?.removePrefix("nav:"))
            check(currentIndex >= 0) { "导航行未聚焦：$current" }
            val targetIndex = order.indexOf("片库")
            key(instrumentation, if (targetIndex > currentIndex) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
            until(2_000) { semantics(scenario).firstOrNull { it.focused && it.tag.startsWith("nav:") }?.tag != current }
            steps++
        }
        key(instrumentation, KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics(scenario).any { it.tag == "候选片库" } }
        com.ichichuang.cinema.feature.library.LibraryNativeFixture.screenReach(scenario, w[workIndex].value)
        key(instrumentation, KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics(scenario).any { it.tag == "候选详情" } }
        until { semantics(scenario).any { it.focused } }
        key(instrumentation, KeyEvent.KEYCODE_DPAD_CENTER)
        until { var isPlayer = false; scenario.onActivity { isPlayer = requireNotNull(it.browseProbe).isPlayer }; isPlayer }
        until { var ready = false; scenario.onActivity {
            val runtime = it.playerControlsProbe
            ready = runtime == null || runtime.display?.variantId != null }; ready }
        // 媒体装载稳态（READY+暂停）：时长已知后才允许焦点/时间轴断言，避免装载竞态。
        until(20_000) { var settled = false; scenario.onActivity {
            val runtime = it.playerControlsProbe
            settled = runtime == null || runtime.display?.status?.statusText == "已暂停" }; settled }
    }

    /** 播放器 → 详情 → 片库（逐层真实 Back，每层等待落定）。 */
    fun backOutOfPlayer(scenario: ActivityScenario<MainActivity>,
        instrumentation: android.app.Instrumentation, viaOverlay: Boolean) {
        if (viaOverlay) {
            key(instrumentation, KeyEvent.KEYCODE_BACK) // 浮层可见时先收一层浮层
            until { var hidden = false; scenario.onActivity {
                hidden = !requireNotNull(it.playerControlsProbe).controller.overlayVisible }; hidden }
        }
        key(instrumentation, KeyEvent.KEYCODE_BACK)
        until { var isPlayer = true; scenario.onActivity { isPlayer = requireNotNull(it.browseProbe).isPlayer }; !isPlayer }
        until { semantics(scenario).any { it.tag == "候选详情" } }
        key(instrumentation, KeyEvent.KEYCODE_BACK)
        until { semantics(scenario).any { it.tag == "候选片库" } }
    }
}
