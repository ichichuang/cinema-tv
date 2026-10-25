package com.ichichuang.cinema.feature.detail

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
import com.ichichuang.cinema.data.local.codec.BrowseSnapshotCodec
import com.ichichuang.cinema.data.userdata.LegacyHandoffRuntime
import com.ichichuang.cinema.data.userdata.RoomPersonalLibraryRepository
import com.ichichuang.cinema.data.userdata.RoomPlaybackProgressStore
import com.ichichuang.cinema.domain.model.catalog.CatalogQuery
import com.ichichuang.cinema.domain.model.catalog.ContentKind
import com.ichichuang.cinema.domain.model.catalog.EpisodeId
import com.ichichuang.cinema.domain.model.catalog.LanguageScope
import com.ichichuang.cinema.domain.model.catalog.CatalogSort
import com.ichichuang.cinema.domain.model.catalog.SourceId
import com.ichichuang.cinema.domain.model.catalog.VariantId
import com.ichichuang.cinema.domain.model.catalog.WorkId
import com.ichichuang.cinema.data.userdata.PlaybackTarget
import com.ichichuang.cinema.domain.model.userdata.BrowseSnapshot
import com.ichichuang.cinema.domain.model.userdata.CommitResult
import com.ichichuang.cinema.domain.model.userdata.PlaybackSnapshot
import com.ichichuang.cinema.domain.model.userdata.WatchState
import org.junit.Assert.assertEquals
import com.ichichuang.cinema.feature.browse.BrowseProbeFixture
import com.ichichuang.cinema.feature.browse.BrowseProbeTestHooks
import com.ichichuang.cinema.feature.home.visual.VisualHomeTestHooks
import com.ichichuang.cinema.feature.library.LibraryTestHooks
import com.ichichuang.cinema.feature.player.syntheticWav
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * R15 作品详情原生测试的共享夹具：七探针全开配对、一次性私有档案
 * （同名线路/同题异身/证据缺失/停用与离线线路/精确 8000ms 续播）。
 * 全部作品与线路为合成数据，仅存在于私有测试档案。
 */
internal object WorkDetailNativeFixture {
    val w = (0..5).map { WorkId(UUID.nameUUIDFromBytes("round15-private-$it".toByteArray()).toString()) }

    const val VARIANT_A = "round15-variant-a"
    const val VARIANT_B = "round15-variant-b"
    const val VARIANT_E = "round15-variant-e"
    const val VARIANT_F = "round15-variant-f"
    const val VARIANT_H = "round15-variant-h"
    const val EPISODE_A1 = "round15-w0-a-1"
    const val EPISODE_A2 = "round15-w0-a-2"
    const val EPISODE_B1 = "round15-w0-b-1"
    const val EPISODE_E1 = "round15-w1-1"
    const val EPISODE_F1 = "round15-w2-1"
    const val EPISODE_H1 = "round15-w5-1"
    const val RESUME_POSITION_MS = 8_000L

    val query = CatalogQuery.create("测试查询-详情1994", ContentKind.MOVIE, LanguageScope.NON_CHINESE,
        setOf("JP"), setOf("horror"), 1994, 1994, CatalogSort.YEAR_DESCENDING, setOf(SourceId("test-source")))

    fun wav(context: Context, name: String) = File(context.filesDir, "round15-$name.wav")

    fun setup(context: Context, seedResume: Boolean = true, seedFavoritesAndHistory: Boolean = true, phaseAware: Boolean = false) {
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
        File(context.noBackupFilesDir, WorkDetailAuthorization.FILE).writeText(WorkDetailAuthorization.TOKEN)
        check(!LegacyHandoffRuntime.present(context))
        if (!phaseAware) {
            context.deleteDatabase(DebugBrowseProbeGate.DATABASE)
            File(context.noBackupFilesDir, "round15-detail-context.json").delete()
            File(context.noBackupFilesDir, WorkDetailFixture.FILE).delete()
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
        writeBrowseFixture(context)
        writeDetailFixture(context)
        // 阶段 B 只保留阶段 A 已提交的记录：绝不重新播种个人数据。
        if (!phaseAware && seedFavoritesAndHistory) seedPersonal(context, seedResume)
    }

    fun writeBrowseFixture(context: Context, artwork: Map<Int, Pair<String, String>> = emptyMap()) {
        fun workJson(index: Int, title: String, block: (JSONObject) -> Unit = {}): JSONObject {
            val json = JSONObject().put("id", w[index].value).put("title", title)
                .put("source", "test-source").put("year", 1994)
                .put("countries", JSONArray(listOf("JP"))).put("genres", JSONArray(listOf("horror")))
                .put("kind", "MOVIE").put("languages", JSONArray(listOf("ja")))
                .put("favorite", false).put("history", false)
                .put("artwork", JSONObject.NULL).put("artworkShape", JSONObject.NULL)
            artwork[index]?.let { (uri, shape) -> json.put("artwork", uri).put("artworkShape", shape) }
            block(json); return json
        }
        val works = JSONArray()
        works.put(workJson(0, "同题异身"))
        works.put(workJson(1, "同题异身"))
        works.put(workJson(2, "证据不全的档案") { json ->
            json.put("yearEvidence", "unknown").put("languageClaims", JSONArray(listOf(JSONArray(listOf("ja")), JSONArray(listOf("ko")))))
        })
        works.put(workJson(3, "无线路的作品"))
        works.put(workJson(4, "只剩停用线路的作品"))
        works.put(workJson(5, "很长很长的合成作品标题六号用于详情长文案与大字号可达性核对"))
        val media = JSONArray()
        fun entry(work: Int, episode: String, variant: String, name: String) {
            val file = wav(context, name).also(::syntheticWav)
            media.put(JSONObject().put("work", w[work].value).put("episode", episode)
                .put("variant", variant).put("uri", file.toURI().toString()))
        }
        entry(0, EPISODE_A1, VARIANT_A, "w0-a-1"); entry(0, EPISODE_A2, VARIANT_A, "w0-a-2")
        entry(0, EPISODE_B1, VARIANT_B, "w0-b-1")
        entry(1, EPISODE_E1, VARIANT_E, "w1-1")
        entry(2, EPISODE_F1, VARIANT_F, "w2-1")
        entry(5, EPISODE_H1, VARIANT_H, "w5-1")
        val snapshot = BrowseSnapshot("library:${query.queryId}", "library", null, query, 1, w[5], w[5], 0f, 1)
        val root = JSONObject().put("initial", BrowseSnapshotCodec.encode(snapshot))
            .put("alternative", BrowseSnapshotCodec.encode(snapshot))
            .put("complete", true).put("works", works).put("order", JSONArray(w.map { it.value }))
            .put("media", media)
            .put("library", JSONObject().put("pageSize", 25).put("failingSources", JSONArray()).put("mirrorSources", JSONObject()))
        File(context.noBackupFilesDir, BrowseProbeFixture.FILE).writeText(root.toString())
    }

    fun writeDetailFixture(context: Context, routeAState: String = "AVAILABLE", omitRouteC: Boolean = false) {
        fun episode(id: String, label: String?, order: Int?, uri: String?) = JSONObject()
            .put("episodeId", id).put("label", label ?: JSONObject.NULL)
            .put("order", order ?: JSONObject.NULL).put("uri", uri ?: JSONObject.NULL)
        fun route(id: String, entry: String, source: String, itemId: String, label: String, state: String,
                  episodes: JSONArray, audio: Set<String> = emptySet()): JSONObject {
            val json = JSONObject().put("routeId", id).put("entryId", entry).put("sourceId", source)
                .put("itemId", itemId).put("label", label).put("state", state).put("episodes", episodes)
                .put("observedAt", 5).put("ruleVersion", "round15")
            if (audio.isNotEmpty()) json.put("audio", JSONArray(audio)); return json
        }
        fun uri(name: String) = wav(context, name).toURI().toString()
        val routes0 = mutableListOf(
            route(VARIANT_A, "entry-a", "test-source", "item-a", "同名线路", routeAState, JSONArray(listOf(
                episode(EPISODE_A1, "第一夜", 1, uri("w0-a-1")), episode(EPISODE_A2, "第二夜", 2, uri("w0-a-2"))))),
            route(VARIANT_B, "entry-b", "mirror-source", "item-b", "同名线路", "AVAILABLE", JSONArray(listOf(
                episode(EPISODE_B1, "第一夜", 1, uri("w0-b-1"))))))
        if (!omitRouteC) routes0.add(route("round15-variant-c", "entry-c", "disabled-source", "item-c", "已停用线路", "DISABLED", JSONArray(listOf(
            episode("round15-w0-c-1", "第一夜", 1, null)))))
        routes0.add(route("round15-variant-d", "entry-d", "offline-source", "item-d", "离线线路", "UNREACHABLE", JSONArray(listOf(
            episode("round15-w0-d-1", null, null, null)))))
        fun routesJson(vararg routes: JSONObject): JSONArray = JSONArray().apply { routes.forEach { put(it) } }
        val works = JSONArray().apply {
            put(JSONObject().put("workId", w[0].value).put("routes", routesJson(*routes0.toTypedArray())))
            put(JSONObject().put("workId", w[1].value).put("routes", routesJson(
                route(VARIANT_E, "entry-e", "test-source", "item-e", "唯一线路", "AVAILABLE", JSONArray(listOf(
                    episode(EPISODE_E1, "序章", 1, uri("w1-1"))))))))
            put(JSONObject().put("workId", w[2].value).put("routes", routesJson(
                route(VARIANT_F, "entry-f", "test-source", "item-f", "默认线路", "AVAILABLE", JSONArray(listOf(
                    episode(EPISODE_F1, null, null, uri("w2-1")))), audio = setOf("zh")))))
            put(JSONObject().put("workId", w[3].value).put("routes", JSONArray()))
            put(JSONObject().put("workId", w[4].value).put("routes", routesJson(
                route("round15-variant-g", "entry-g", "disabled-source", "item-g", "停用线路", "DISABLED", JSONArray(listOf(
                    episode("round15-w4-g-1", "第一夜", 1, null)))))))
            put(JSONObject().put("workId", w[5].value).put("routes", routesJson(
                route(VARIANT_H, "entry-h", "test-source", "item-h", "特别长的线路名称用于核对焦点与动作可达性", "AVAILABLE", JSONArray(listOf(
                    episode(EPISODE_H1, "很长很长的剧集名称第一夜之后还有后续情节待核对", 1, uri("w5-1"))))),
                route("round15-variant-i", "entry-i", "mirror-source", "item-i", "特别长的线路名称用于核对焦点与动作可达性", "STALE", JSONArray(listOf(
                    episode("round15-w5-i-1", "第二夜", 2, null)))))))
        }
        File(context.noBackupFilesDir, WorkDetailFixture.FILE).writeText(JSONObject().put("works", works).toString())
    }

    /** 种子个人记录：w1 收藏、w2 观看历史、w0 精确 (epA2, variant-a, 8000ms) 续播。 */
    fun seedPersonal(context: Context, seedResume: Boolean) = runBlocking {
        DebugBrowseProbeGate.withOwnedProfile(context) { database ->
            val repository = RoomPersonalLibraryRepository(database)
            val store = RoomPlaybackProgressStore(database)
            assertEquals(CommitResult.Committed, repository.rememberCard(w[1].value, "同题异身", "test-source"))
            assertEquals(CommitResult.Committed, repository.setFavorite(w[1].value, true))
            val historySession = "round15-history-${UUID.randomUUID()}"
            val historyTarget = PlaybackTarget(w[2], EpisodeId(EPISODE_F1), VariantId(VARIANT_F))
            assertEquals(CommitResult.Committed, repository.rememberCard(w[2].value, "证据不全的档案", "test-source"))
            assertEquals(CommitResult.Committed, store.beginSession(historyTarget, historySession))
            assertEquals(CommitResult.Committed, repository.confirmPlayback(historySession))
            if (seedResume) {
                assertEquals(CommitResult.Committed, repository.rememberCard(w[0].value, "同题异身", "test-source"))
                val session = "round15-resume-${UUID.randomUUID()}"
                val target = PlaybackTarget(w[0], EpisodeId(EPISODE_A2), VariantId(VARIANT_A))
                assertEquals(CommitResult.Committed, store.beginSession(target, session))
                assertEquals(CommitResult.Committed, repository.confirmPlayback(session))
                assertEquals(CommitResult.Committed, store.commit(PlaybackSnapshot(w[0], target.episodeId,
                    target.variantId, session, 1, RESUME_POSITION_MS, 12_000, WatchState.IN_PROGRESS, 1)))
            }
        }
    }

    fun teardown(context: Context) {
        File(context.noBackupFilesDir, WorkDetailFixture.FILE).delete()
        File(context.noBackupFilesDir, "round15-detail-context.json").delete()
    }

    @Volatile var scenarioRef: ActivityScenario<MainActivity>? = null

    fun until(timeout: Long = 12_000, condition: () -> Boolean) {
        val end = android.os.SystemClock.uptimeMillis() + timeout
        while (!condition()) {
            if (android.os.SystemClock.uptimeMillis() > end) {
                var diagnostic = ""
                scenarioRef?.onActivity { activity ->
                    val detail = activity.detailProbe
                    diagnostic = "detail=${activity.browseProbe?.isDetail} player=${activity.browseProbe?.isPlayer} " +
                        (detail?.let { "work=${it.installedWorkId} route=${it.controller.selectedRouteId} episode=${it.controller.selectedEpisodeId} " +
                            "focus=${it.controller.focusedKey} sheet=${it.controller.sheet} msg=${it.message} " +
                            "trace=${WorkDetailTestHooks.dispatchTrace.toList().takeLast(12)} focusTrace=${WorkDetailTestHooks.focusTrace.toList().takeLast(12)}" } ?: "无详情运行时")
                }
                throw AssertionError("等待实际原生状态超时；$diagnostic；" +
                    (scenarioRef?.let { semantics(it).filter { it.focused || it.tag.isNotBlank() }.take(28).toString() } ?: "无页面"))
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
}
