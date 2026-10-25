package com.ichichuang.cinema.feature.detail

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.ichichuang.cinema.BuildConfig
import com.ichichuang.cinema.MainActivity
import com.ichichuang.cinema.core.navigation.DebugBrowseProbeGate
import com.ichichuang.cinema.data.userdata.LegacyHandoffRuntime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 仅运行于普通（全探针 OFF）Debug/DebugAndroidTest 配对：普通路径不得出现候选详情。 */
class DetailOffNativeTest {
    @Test fun ordinary_debug_keeps_legacy_detail_and_never_surfaces_candidate_detail() = runBlocking<Unit> {
        check(!BuildConfig.BROWSE_RESTORE_PROBE && !BuildConfig.PLAYBACK_ROOM_PROBE &&
            !BuildConfig.HOME_VISUAL_PROBE && !BuildConfig.LIBRARY_FILTER_PROBE && !BuildConfig.DETAIL_PROBE)
        val context: Context = ApplicationProvider.getApplicationContext()
        check(context.packageName == "com.ichichuang.cinema.debug")
        context.deleteDatabase(DebugBrowseProbeGate.DATABASE)
        listOf(LegacyHandoffRuntime.PROFILE, LegacyHandoffRuntime.CONSENT,
            WorkDetailAuthorization.FILE,
            com.ichichuang.cinema.feature.home.visual.VisualHomeAuthorization.FILE,
            com.ichichuang.cinema.feature.library.LibraryAuthorization.FILE,
            "round06-playback-test.authorization",
            WorkDetailFixture.FILE, "round15-detail-context.json").forEach { File(context.noBackupFilesDir, it).delete() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(1_500)
            scenario.onActivity { activity ->
                assertNull(activity.browseProbe)
                assertNull(activity.homeProbe)
                assertNull(activity.libraryProbe)
                assertNull(activity.detailProbe)
                fun nodes(view: View): List<SemanticsNode> {
                    fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
                    if (view is ViewRootForTest) return flatten(view.semanticsOwner.unmergedRootSemanticsNode)
                    return if (view is ViewGroup) (0 until view.childCount).flatMap { nodes(view.getChildAt(it)) } else emptyList()
                }
                val tree = nodes(activity.window.decorView)
                val tags = tree.map { it.config.getOrElse(SemanticsProperties.TestTag) { "" } }
                assertFalse("普通路径不得出现候选详情", tags.any { it == "候选详情" })
                assertFalse("普通路径不得出现候选线路单", tags.any { it == "线路单" || it == "集数单" })
                assertFalse("普通路径不得出现候选播放线路", tags.any { it.startsWith("线路:") || it.startsWith("集数:") })
                assertTrue("普通路径应保持旧首页", tree.any {
                    it.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { text -> text.text == "还没有内容" } })
            }
            assertFalse(context.getDatabasePath(DebugBrowseProbeGate.DATABASE).exists())
            assertFalse("普通路径不得写详情上下文档案", File(context.noBackupFilesDir, "round15-detail-context.json").exists())
        }
    }
}
