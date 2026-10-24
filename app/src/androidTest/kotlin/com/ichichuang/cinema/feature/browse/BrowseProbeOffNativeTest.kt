package com.ichichuang.cinema.feature.browse

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
import com.ichichuang.cinema.data.settings.browsePrefs
import com.ichichuang.cinema.data.settings.CinemaSettings
import com.ichichuang.cinema.data.settings.DataStoreBrowseStore
import com.ichichuang.cinema.data.userdata.LegacyHandoffRuntime
import com.ichichuang.cinema.domain.model.UpdateInfo
import com.ichichuang.cinema.domain.repository.BrowseMemory
import com.ichichuang.cinema.domain.repository.Updater
import com.ichichuang.cinema.feature.home.HomeViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Run ONLY in the ordinary browse=false/playback=false ARM64 instrumentation pair. */
class BrowseProbeOffNativeTest {
    @Test fun ordinary_activity_uses_legacy_browse_and_never_opens_candidate() = runBlocking<Unit> {
        check(!BuildConfig.BROWSE_RESTORE_PROBE && !BuildConfig.PLAYBACK_ROOM_PROBE)
        val context: Context = ApplicationProvider.getApplicationContext()
        check(context.packageName == "com.ichichuang.cinema.debug")
        check(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("cinema.browse") == "round11")
        // The owned profile may retain a candidate file from the ON phase. Remove only R11's file.
        context.deleteDatabase(DebugBrowseProbeGate.DATABASE)
        listOf(LegacyHandoffRuntime.PROFILE, LegacyHandoffRuntime.CONSENT).forEach { File(context.noBackupFilesDir, it).delete() }
        check(!LegacyHandoffRuntime.present(context))
        val settings = CinemaSettings(context)
        assertEquals("", settings.apiBase.first()); assertTrue(settings.activeUrls.first().isEmpty())
        val calls = AtomicInteger()
        // Explicit test-only updater boundary prevents the ordinary startup check reaching GitHub.
        val isolatedUpdater = module { single<Updater> { object : Updater {
            override suspend fun check(currentVersionCode: Int, force: Boolean): UpdateInfo? { calls.incrementAndGet(); return null }
            override suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = error("测试禁止下载")
        } } }
        val originalBrowse = context.browsePrefs.data.first()
        loadKoinModules(isolatedUpdater)
        try {
            val memory = BrowseMemory("测试旧浏览位置", 1994)
            val store = DataStoreBrowseStore(context); store.save(memory); assertEquals(memory, store.load())
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val end = android.os.SystemClock.uptimeMillis() + 10_000
                while (calls.get() == 0 && android.os.SystemClock.uptimeMillis() < end) Thread.sleep(25)
                assertEquals(1, calls.get())
                scenario.onActivity { activity ->
                    assertNull(activity.browseProbe)
                    fun nodes(view: View): List<SemanticsNode> {
                        fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
                        if (view is ViewRootForTest) return flatten(view.semanticsOwner.unmergedRootSemanticsNode)
                        return if (view is ViewGroup) (0 until view.childCount).flatMap { nodes(view.getChildAt(it)) } else emptyList()
                    }
                    val tree = nodes(activity.window.decorView)
                    assertFalse(tree.any { it.config.getOrElse(SemanticsProperties.TestTag) { "" } == "候选浏览" })
                    assertTrue(tree.any { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { it.text == "还没有内容" } })
                    assertTrue(GlobalContext.get().get<HomeViewModel>().state.value.needsSetup)
                }
                assertEquals(memory, store.load()); assertFalse(context.getDatabasePath(DebugBrowseProbeGate.DATABASE).exists())
            }
        } finally { context.browsePrefs.updateData { originalBrowse }; unloadKoinModules(isolatedUpdater) }
    }
}
