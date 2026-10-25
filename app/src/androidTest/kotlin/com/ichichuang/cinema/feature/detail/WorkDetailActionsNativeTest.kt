package com.ichichuang.cinema.feature.detail

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.MainActivity
import com.ichichuang.cinema.core.navigation.DebugBrowseProbeGate
import com.ichichuang.cinema.data.userdata.RoomPersonalLibraryRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** R15 详情动作原生测试：精确续播、确认派发、收藏事务与真实本地 Media3（七探针配对）。 */
class WorkDetailActionsNativeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun ownedPrivateFixture() = WorkDetailNativeFixture.setup(context)

    @After fun restore() {
        WorkDetailTestHooks.beforePlayDispatch?.complete(Unit); WorkDetailTestHooks.beforePlayDispatch = null
        scenario?.close(); scenario = null
        WorkDetailNativeFixture.teardown(context)
    }

    private fun semantics() = WorkDetailNativeFixture.semantics(scenario!!)
    private fun until(timeout: Long = 12_000, condition: () -> Boolean) = WorkDetailNativeFixture.until(timeout, condition)
    private fun key(code: Int) {
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, code))
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, code))
        instrumentation.waitForIdleSync()
    }
    private fun detail(action: (WorkDetailRuntime) -> Unit) { scenario!!.onActivity { action(requireNotNull(it.detailProbe)) } }
    private fun personal(action: (com.ichichuang.cinema.feature.personal.PersonalLibraryRuntime) -> Unit) {
        scenario!!.onActivity { action(requireNotNull(requireNotNull(it.browseProbe).personal)) }
    }
    private fun repository(): RoomPersonalLibraryRepository {
        var repository: RoomPersonalLibraryRepository? = null
        personal { repository = it.repository }
        return requireNotNull(repository)
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        WorkDetailNativeFixture.scenarioRef = scenario
        until { semantics().any { it.tag == "候选首页" } }
    }

    private fun enterLibrary() {
        until { semantics().any { it.focused } }
        repeat(6) { key(KeyEvent.KEYCODE_DPAD_UP) }
        until { semantics().any { it.focused && it.tag.startsWith("nav:") } }
        val order = com.ichichuang.cinema.feature.home.visual.VisualHomeRuntime.NAV_ITEMS
        while (true) {
            val current = semantics().firstOrNull { it.focused && it.tag.startsWith("nav:") }?.tag
            if (current == "nav:片库") break
            val index = order.indexOf(current?.removePrefix("nav:"))
            check(index >= 0)
            key(if (order.indexOf("片库") > index) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
            until(2_000) { semantics().firstOrNull { it.focused && it.tag.startsWith("nav:") }?.tag != current }
        }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选片库" } }
        until { var size = -1; scenario!!.onActivity { size = requireNotNull(it.libraryProbe).display.items.size }; size == 6 }
    }

    private fun openDetailOfWork0() {
        com.ichichuang.cinema.feature.library.LibraryNativeFixture.screenReach(scenario!!, WorkDetailNativeFixture.w[0].value)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选详情" } }
        until { var id: String? = null; detail { id = it.installedWorkId }; id != null }
        until { semantics().any { it.tag == "播放操作" && it.focused } }
    }

    private fun selectRoute(routeId: String) {
        detail { it.activate("线路:$routeId") }
        until { var route: String? = null; detail { route = it.controller.selectedRouteId }; route == routeId }
        // activate 后焦点在所选线路行：上移回动作行（同列索引），再逐键左移到播放操作。
        key(KeyEvent.KEYCODE_DPAD_UP)
        until { semantics().any { it.focused && (it.tag == "选择线路" || it.tag == "选择集数" || it.tag == "收藏操作" || it.tag == "详情返回") } }
        var guard = 0
        while (guard++ < 6 && semantics().none { it.tag == "播放操作" && it.focused }) {
            val current = semantics().firstOrNull { it.focused }?.tag
            key(KeyEvent.KEYCODE_DPAD_LEFT)
            until(2_000) { semantics().firstOrNull { it.focused }?.tag != current }
        }
        until { semantics().any { it.tag == "播放操作" && it.focused } }
    }

    // A01/A02 精确 (作品,剧集,版本) 续播 8000ms，恢复后保持暂停
    @Test fun resume_targets_exact_episode_variant_position_and_stays_paused() {
        launch(); enterLibrary(); openDetailOfWork0()
        key(KeyEvent.KEYCODE_DPAD_CENTER) // 播放操作：继续播放
        until { semantics().any { it.tag == "候选播放器" } }
        personal { owner ->
            val media = requireNotNull(owner.selectedMedia)
            assertEquals(WorkDetailNativeFixture.w[0].value, media.target.workId.value)
            assertEquals(WorkDetailNativeFixture.EPISODE_A2, media.target.episodeId?.value)
            assertEquals(WorkDetailNativeFixture.VARIANT_A, media.target.variantId?.value)
            assertEquals(WorkDetailNativeFixture.RESUME_POSITION_MS, owner.requestedResumePosition)
            assertFalse("恢复后必须保持暂停", owner.playerPlaying)
        }
        var dispatched = ""
        detail { dispatched = WorkDetailTestHooks.dispatchTrace.lastOrNull { it.startsWith("play:") } ?: "" }
        assertEquals("play:${WorkDetailNativeFixture.VARIANT_A}/${WorkDetailNativeFixture.EPISODE_A2}", dispatched)
        // 显式 OK 后才真正开始播放（真实本地 Media3）。
        until { semantics().any { it.tag == "播放暂停" && it.focused } }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var playing = false; personal { playing = it.playerPlaying }; playing }
    }

    // A04/E07 换线播放派发所选精确目标，绝不携带其他版本 8000ms 检查点
    @Test fun explicit_play_uses_selected_verified_route_without_cross_variant_resume() {
        launch(); enterLibrary(); openDetailOfWork0()
        selectRoute(WorkDetailNativeFixture.VARIANT_B)
        // 派发确认（不经 waitForIdleSync：层切换期间的空闲同步会被系统看门狗打断）。
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        until { var ready = false; personal { owner ->
            ready = owner.selectedMedia?.target?.episodeId?.value == WorkDetailNativeFixture.EPISODE_B1 &&
                owner.selectedMedia?.target?.variantId?.value == WorkDetailNativeFixture.VARIANT_B }
            ready }
        personal { assertEquals("换线必须从 0 开始", 0L, it.requestedResumePosition) }
        // 显式 OK“播放”（真实起播，不经空闲同步）：Media3 推进 + R05 管线为精确 (epB1, variant-b) 落库。
        until { semantics().any { it.tag == "播放暂停" && it.focused } }
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        // 位置 >0 证明真实播放；<8000 证明从头开始而非携带旧版本检查点。
        until(30_000) {
            var committed = false
            runBlocking { committed = repository().continueWatching().any { card ->
                card.workId == WorkDetailNativeFixture.w[0].value && card.episodeId?.value == WorkDetailNativeFixture.EPISODE_B1 &&
                    card.variantId?.value == WorkDetailNativeFixture.VARIANT_B &&
                    ((card.snapshot?.positionMs ?: 0) > 0) && ((card.snapshot?.positionMs ?: 0) < WorkDetailNativeFixture.RESUME_POSITION_MS) } }
            committed
        }
        // 回到详情：若播放器层仍在（看门狗未介入），显式 Back 关一层；徽标只出现在精确 (epB1, variant-b)。
        if (semantics().any { it.tag == "候选播放器" }) key(KeyEvent.KEYCODE_BACK)
        until(30_000) { semantics().any { it.tag == "候选详情" } }
        until { var badge: String? = null
            detail { badge = it.display!!.episodes.firstOrNull { e -> e.episodeId == WorkDetailNativeFixture.EPISODE_B1 }?.resumeText }
            badge != null }
        detail {
            // 线路 b 的清单不含 epA2：绝不跨清单捏造其他版本的徽标。
            assertTrue(it.display!!.episodes.none { e -> e.episodeId == WorkDetailNativeFixture.EPISODE_A2 })
        }
    }

    // A09 本地媒体读取失败：不换线、不清检查点、不标已看
    @Test fun media_error_preserves_resume_and_route() {
        launch(); enterLibrary(); openDetailOfWork0()
        assertTrue(WorkDetailNativeFixture.wav(context, "w0-a-2").delete())
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { var message = ""; detail { message = it.message }; message.contains("播放资料读取失败") ||
            message.contains("原记录保持") || message.contains("未核实") }
        until { semantics().none { it.tag == "候选播放器" } }
        detail { assertEquals(WorkDetailNativeFixture.VARIANT_A, it.controller.selectedRouteId) }
        val resume = runBlocking { repository().continueWatching().firstOrNull { it.workId == WorkDetailNativeFixture.w[0].value } }
        assertNotNull("检查点必须保留", resume)
        assertEquals(WorkDetailNativeFixture.RESUME_POSITION_MS, resume?.snapshot?.positionMs)
    }

    // A06 收藏只作用于规范作品身份，离线/停用线路不影响收藏保留
    @Test fun favorite_toggles_only_canonical_work_and_survives_disabled_route() {
        launch(); enterLibrary(); openDetailOfWork0()
        val historyBefore = runBlocking { repository().recentHistory().size }
        detail { it.activate("收藏操作") } // 语义/指针确认路径
        until { runBlocking { repository().favorites().any { it.workId == WorkDetailNativeFixture.w[0].value } } }
        detail { it.activate("线路:round15-variant-c") } // 换到停用线路（仍保留收藏）
        until { var route: String? = null; detail { route = it.controller.selectedRouteId }; route == "round15-variant-c" }
        until { runBlocking { repository().favorites().any { it.workId == WorkDetailNativeFixture.w[0].value } } }
        assertEquals("收藏不得改写历史", historyBefore, runBlocking { repository().recentHistory().size })
        detail { it.activate("收藏操作") } // 再显式取消
        until { runBlocking { repository().favorites().none { it.workId == WorkDetailNativeFixture.w[0].value } } }
    }

    // A07 提交失败回滚界面并保留库内状态（真实 SQLite 独占写锁注入）
    @Test fun favorite_sql_failure_rolls_back_ui_and_permits_retry() {
        launch(); enterLibrary(); openDetailOfWork0()
        val databasePath = context.getDatabasePath(DebugBrowseProbeGate.DATABASE).absolutePath
        val blocker = SQLiteDatabase.openDatabase(databasePath, null, SQLiteDatabase.OPEN_READWRITE)
        blocker.execSQL("BEGIN EXCLUSIVE")
        try {
            detail { it.activate("收藏操作") }
            // 独占锁期间任何读取都会失败：只观察运行时状态行。
            until(25_000) { var message = ""; detail { message = it.currentStatus() }; message.contains("未保存") || message.contains("未完成") }
        } finally {
            blocker.execSQL("COMMIT"); blocker.close()
        }
        // 解锁后核实：库内没有半提交状态，界面不声称已收藏。
        until { runBlocking { repository().favorites().none { it.workId == WorkDetailNativeFixture.w[0].value } } }
        var favoriteText = ""
        detail { favoriteText = it.display!!.favoriteText }
        assertEquals("界面不得声称已收藏", "收藏", favoriteText)
        // 重试收敛为一次真实收藏（同一幂等目标）。
        detail { it.activate("收藏操作") }
        until { runBlocking { repository().favorites().singleOrNull { it.workId == WorkDetailNativeFixture.w[0].value } != null } }
    }

    // A08 回执丢失后重试幂等收敛，绝不反向切换
    @Test fun favorite_lost_ack_retry_idempotent() {
        launch(); enterLibrary(); openDetailOfWork0()
        com.ichichuang.cinema.feature.personal.PersonalLibraryTestHooks.lostReceiptAcknowledgements.set(1)
        detail { it.activate("收藏操作") }
        until { runBlocking { repository().favorites().any { it.workId == WorkDetailNativeFixture.w[0].value } } }
        until { var message = ""; personal { message = it.message }; message.contains("重试") }
        detail { it.activate("收藏操作") } // 重试：同一目标值，不反向
        until(15_000) { var cleared = false; personal { cleared = it.message.isBlank() || !it.message.contains("重试") }; cleared }
        val favorites = runBlocking { repository().favorites().map { it.workId } }
        assertEquals("w0 恰好一次收藏且未反向", 1, favorites.count { it == WorkDetailNativeFixture.w[0].value })
        assertTrue("既有 w1 收藏不受影响", favorites.contains(WorkDetailNativeFixture.w[1].value))
    }

    // A04/A05 派发时点校验：派发被扣住期间所选线路被证实停用，确认不得启动陈旧目标
    @Test fun play_dispatch_validates_at_point_of_dispatch() {
        launch(); enterLibrary(); openDetailOfWork0()
        WorkDetailTestHooks.beforePlayDispatch = kotlinx.coroutines.CompletableDeferred()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { WorkDetailTestHooks.dispatchTrace.any { it.startsWith("play:${WorkDetailNativeFixture.VARIANT_A}") } }
        // 派发被扣住期间线路证据刷新为停用：释放后必须拒绝且不自动改选其他线路。
        WorkDetailNativeFixture.writeDetailFixture(context, routeAState = "DISABLED")
        detail { it.refreshRoutes() }
        until { var busy = true; detail { busy = it.routeRefreshBusy }; !busy }
        WorkDetailTestHooks.beforePlayDispatch?.complete(Unit)
        until { var message = ""; detail { message = it.message }; message.contains("不可用") || message.contains("未核实") }
        until(4_000) { semantics().none { it.tag == "候选播放器" } }
        detail { assertEquals(WorkDetailNativeFixture.VARIANT_A, it.controller.selectedRouteId) }
    }
}
