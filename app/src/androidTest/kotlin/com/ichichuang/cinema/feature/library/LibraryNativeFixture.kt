package com.ichichuang.cinema.feature.library

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
import com.ichichuang.cinema.feature.browse.BrowseProbeFixture
import com.ichichuang.cinema.feature.browse.BrowseProbeTestHooks
import com.ichichuang.cinema.feature.home.visual.VisualHomeTestHooks
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * R14 片库/筛选原生测试的共享夹具：六探针全开配对、一次性私有 fixture
 * （含分页/失败/镜像来源计划）。全部作品为合成数据，仅存在于私有测试档案。
 */
internal object LibraryNativeFixture {
    val ids = (0 until 105).map { com.ichichuang.cinema.domain.model.catalog.WorkId(
        UUID.nameUUIDFromBytes("round11-private-$it".toByteArray()).toString()) }

    /** 1980 年代作品（i=30..44）跨第二页：年代筛选首页零匹配仍可继续核对的实证。 */
    val decadeIndices = 30..44

    fun workYear(i: Int) = when {
        i == 100 -> 1995
        i in decadeIndices -> 1987
        else -> 1994
    }

    /** phaseAware=false 清库重写档案（常规类）；true 保留既有数据库与档案（两阶段进程实验的阶段 B）。 */
    fun setup(context: Context, phaseAware: Boolean = false) {
        val arguments = InstrumentationRegistry.getArguments()
        check(com.ichichuang.cinema.BuildConfig.BROWSE_RESTORE_PROBE && com.ichichuang.cinema.BuildConfig.PLAYBACK_ROOM_PROBE)
        check(arguments.getString("cinema.browse") == "round11")
        check(arguments.getString("cinema.disposable") == "round06")
        check(arguments.getString("cinema.handoff") == "round07")
        check(arguments.getString("cinema.personal") == "round12")
        check(arguments.getString("cinema.home") == "round13")
        check(com.ichichuang.cinema.BuildConfig.LIBRARY_FILTER_PROBE)
        check(arguments.getString("cinema.library") == "round14")
        until { BrowseProbeTestHooks.activeLeases.get() == 0 && VisualHomeTestHooks.activeLeases.get() == 0 && LibraryTestHooks.activeLeases.get() == 0 }
        File(context.noBackupFilesDir, LegacyHandoffRuntime.PROFILE).writeText(LegacyHandoffRuntime.PROFILE_TOKEN)
        File(context.noBackupFilesDir, LegacyHandoffRuntime.CONSENT).writeText(LegacyHandoffRuntime.CONSENT_TOKEN)
        File(context.noBackupFilesDir, "round06-playback-test.authorization").writeText("cinema-round06-disposable-local-media")
        File(context.noBackupFilesDir, com.ichichuang.cinema.feature.personal.PersonalLibraryAuthorization.FILE)
            .writeText(com.ichichuang.cinema.feature.personal.PersonalLibraryAuthorization.TOKEN)
        File(context.noBackupFilesDir, com.ichichuang.cinema.feature.home.visual.VisualHomeAuthorization.FILE)
            .writeText(com.ichichuang.cinema.feature.home.visual.VisualHomeAuthorization.TOKEN)
        File(context.noBackupFilesDir, LibraryAuthorization.FILE).writeText(LibraryAuthorization.TOKEN)
        check(!LegacyHandoffRuntime.present(context))
        if (!phaseAware) context.deleteDatabase(DebugBrowseProbeGate.DATABASE)
        BrowseProbeTestHooks.holdWindowReady = false; BrowseProbeTestHooks.disableWorkFocus = false
        BrowseProbeTestHooks.failedFocusRequests.set(0); BrowseProbeTestHooks.focusTrace.clear()
        BrowseProbeTestHooks.beforeLoad = null; BrowseProbeTestHooks.beforeTopLevelLoad = null
        VisualHomeTestHooks.holdWindowReady = false; VisualHomeTestHooks.disableCardFocus = false
        VisualHomeTestHooks.blockPlacement = false; VisualHomeTestHooks.failedFocusRequests.set(0)
        LibraryTestHooks.holdFirstLoad = null; LibraryTestHooks.blockPlacement = false
        LibraryTestHooks.disableWorkFocus = false; LibraryTestHooks.failedFocusRequests.set(0)
        LibraryTestHooks.focusTrace.clear()
        LibraryTestHooks.appliedQueries.set(0); LibraryTestHooks.continuedLoads.set(0)
        // 两阶段实验共享同一份档案：仅阶段 A 重写。
        if (!phaseAware || !File(context.noBackupFilesDir, BrowseProbeFixture.FILE).isFile) writeFixture(context)
    }

    fun writeFixture(context: Context, works: JSONArray = defaultWorks()) {
        val root = JSONObject()
            .put("initial", defaultBrowseSnapshot())
            .put("alternative", defaultBrowseSnapshot())
            .put("complete", true)
            .put("works", works)
            .put("order", JSONArray(ids.map { it.value }))
            .put("library", JSONObject()
                .put("pageSize", 25)
                .put("failingSources", JSONArray(listOf("broken-source")))
                .put("mirrorSources", JSONObject().put("mirror-source", "test-source")))
        File(context.noBackupFilesDir, BrowseProbeFixture.FILE).writeText(root.toString())
    }

    private fun defaultBrowseSnapshot(): String {
        // 浏览占位上下文保持 R11 结构（initial 由 codec 编码），片库不消费它。
        val query = com.ichichuang.cinema.domain.model.catalog.CatalogQuery.create(
            "测试查询-日本恐怖1994", com.ichichuang.cinema.domain.model.catalog.ContentKind.MOVIE,
            com.ichichuang.cinema.domain.model.catalog.LanguageScope.NON_CHINESE,
            setOf("JP"), setOf("horror"), 1994, 1994,
            com.ichichuang.cinema.domain.model.catalog.CatalogSort.YEAR_DESCENDING,
            setOf(com.ichichuang.cinema.domain.model.catalog.SourceId("test-source")))
        val snapshot = com.ichichuang.cinema.domain.model.userdata.BrowseSnapshot(
            "library:测试查询-日本恐怖1994", "library", null, query, 1, ids[99], ids[95], 0f, 1)
        return com.ichichuang.cinema.data.local.codec.BrowseSnapshotCodec.encode(snapshot)
    }

    private fun defaultWorks(): JSONArray {
        val works = JSONArray()
        ids.forEachIndexed { i, id ->
            works.put(JSONObject()
                .put("id", id.value)
                .put("title", if (i == 87) "很长的合成作品标题第八十八号用于长文案核对" else "合成作品${i + 1}")
                .put("source", if (i == 104) "disabled-source" else "test-source")
                .put("year", workYear(i))
                .put("countries", JSONArray(listOf(if (i == 101) "US" else "JP")))
                .put("genres", JSONArray(listOf(if (i == 102) "drama" else "horror")))
                .put("kind", if (i == 103) "OTHER" else "MOVIE")
                .put("languages", JSONArray(listOf("ja")))
                .put("favorite", false).put("history", false)
                .put("artwork", JSONObject.NULL)
                .put("artworkShape", JSONObject.NULL))
        }
        return works
    }

    fun teardown(context: Context) {
        File(context.noBackupFilesDir, LibraryAuthorization.FILE).delete()
        File(context.noBackupFilesDir, BrowseProbeFixture.FILE).delete()
    }

    fun until(timeout: Long = 12_000, condition: () -> Boolean) {
        val end = android.os.SystemClock.uptimeMillis() + timeout
        while (!condition()) {
            if (android.os.SystemClock.uptimeMillis() > end) {
                var diagnostic = ""
                scenarioRef?.onActivity { activity ->
                    val library = activity.libraryProbe
                    val home = activity.homeProbe
                    diagnostic = "window=${activity.window.decorView.hasWindowFocus()} " +
                        (home?.let { "screen=${it.screen} control=${it.controlTarget} focused=${it.focusedControl} " +
                            "browseBusy=${it.browse.busy} detail=${it.browse.isDetail} " +
                            "homeTrace=${com.ichichuang.cinema.feature.home.visual.VisualHomeTestHooks.focusTrace.toList().takeLast(14)}" } ?: "无首页") +
                        (library?.let { " 库query=${it.appliedQuery?.queryId} items=${it.display.items.size} panel=${it.panelOpen} panelTarget=${it.panelTarget} control=${it.controlTarget} user=${it.userTarget} focused=${it.focused} trace=${LibraryTestHooks.focusTrace.toList().takeLast(24)}" } ?: " 无片库")
                }
                throw AssertionError("等待实际原生状态超时；$diagnostic；" +
                    (scenarioRef?.let { ref -> semantics(ref).filter { it.focused || it.tag.isNotBlank() }.take(24).toString() } ?: "无页面"))
            }
            Thread.sleep(25)
        }
    }

    /** 诊断钩子：测试类把当前 scenario 登记进来，超时信息带上真实运行时状态。 */
    @Volatile var scenarioRef: ActivityScenario<MainActivity>? = null

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

    /** 片库根画面走格：以运行时焦点区为同一来源做确定性 D-pad 到达（网格/工具条/条件片/状态动作）。 */
    fun screenReach(scenario: ActivityScenario<MainActivity>, target: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun key(code: Int) {
            instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, code))
            instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, code))
            instrumentation.waitForIdleSync()
        }
        var guard = 0
        // 上限覆盖 105 项网格的逐格深走（真实 D-pad 序列，非传送）。
        while (guard++ < 220) {
            val actual = semantics(scenario).firstOrNull { it.focused }?.tag
            if (actual == target) break
            var zones: List<List<String>> = emptyList()
            scenario.onActivity { zones = requireNotNull(it.libraryProbe).focusZones().map { it.second } }
            val currentZone = zones.indexOfFirst { actual in it }
            val targetZone = zones.indexOfFirst { target in it }
            check(targetZone >= 0) { "片库焦点区中没有目标 $target：$zones" }
            val code = when {
                currentZone < 0 -> KeyEvent.KEYCODE_DPAD_UP
                currentZone == targetZone ->
                    if (zones[targetZone].indexOf(target) > zones[currentZone].indexOf(actual))
                        KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                targetZone < currentZone -> KeyEvent.KEYCODE_DPAD_UP
                else -> KeyEvent.KEYCODE_DPAD_DOWN
            }
            key(code)
            until(2_000) { semantics(scenario).firstOrNull { it.focused }?.tag != actual }
        }
        until { semantics(scenario).any { it.tag == target && it.focused } }
    }
}
