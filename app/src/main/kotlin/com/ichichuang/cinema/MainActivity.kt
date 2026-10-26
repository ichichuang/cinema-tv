package com.ichichuang.cinema

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ichichuang.cinema.core.design.CinemaTheme
import com.ichichuang.cinema.feature.home.HomeViewModel
import com.ichichuang.cinema.feature.update.UpdateCenter
import com.ichichuang.cinema.nav.CinemaNavHost
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.platform.testTag
import com.ichichuang.cinema.core.navigation.DebugBrowseProbeGate
import com.ichichuang.cinema.data.userdata.RoomBrowseStateStore
import com.ichichuang.cinema.feature.browse.*
import kotlinx.coroutines.*

/**
 * 唯一的 Activity。
 *
 * 整屏 UI 结构：CinemaTheme（色板 + 动效规范 + 电视尺寸适配）→ CinemaNavHost（导航）。
 * 除此之外什么都不做 —— 逻辑全在 ViewModel，样式全在 core/design。
 *
 * 两个"跨页面共享"的实例在这里取一次，往导航图里传（而不是各页面自己 inject）：
 * - UpdateCenter：首页（金标 + 更新浮层）和设置页必须共享同一份状态，
 *   各建一个会出现"首页说没新版、设置页说有新版"的鬼故事
 * - HomeViewModel：首页和详情页共享 —— 详情数据在首页聚焦时就已经拉好，
 *   点开详情才能"瞬间有内容"，返回时列表位置也原样还在
 *   （⚠️ Koin 里声明成 single，不能是 viewModel：viewModel 会按页面各建一个）
 */
class MainActivity : ComponentActivity(), KoinComponent {

    private val updateCenter: UpdateCenter by inject()
    private val homeViewModel: HomeViewModel by inject()

    private var probeScope: CoroutineScope? = null
    internal var browseProbe by mutableStateOf<BrowseProbeRuntime?>(null)
        private set
    internal var homeProbe by mutableStateOf<com.ichichuang.cinema.feature.home.visual.VisualHomeRuntime?>(null)
        private set
    internal var libraryProbe by mutableStateOf<com.ichichuang.cinema.feature.library.LibraryRuntime?>(null)
        private set
    internal var detailProbe by mutableStateOf<com.ichichuang.cinema.feature.detail.WorkDetailRuntime?>(null)
        private set
    internal var playerControlsProbe by mutableStateOf<com.ichichuang.cinema.feature.player.PlayerControlsRuntime?>(null)
        private set
    private var probeError by mutableStateOf<String?>(null)

    override fun onPause() {
        // Freeze candidate observations before stop-time window/inset changes arrive.
        homeProbe?.pause()
        browseProbe?.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        browseProbe?.resume()
        homeProbe?.resume()
    }

    override fun onDestroy() {
        probeScope?.cancel()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.BROWSE_RESTORE_PROBE) {
            val ordinaryCallback = requireNotNull(window.callback)
            window.callback = object : android.view.Window.Callback by ordinaryCallback {
                override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
                    // 播放器是浏览运行时的最上层子画面：先由 R16 播放器控制运行时处理；
                    // 详情次之；首页是候选根画面：再由首页处理；浏览/子层按键最后交浏览运行时。
                    if (playerControlsProbe?.key(event) { finish() } == true) return true
                    if (detailProbe?.key(event) { finish() } == true) return true
                    if (homeProbe?.key(event) { finish() } == true) return true
                    if (browseProbe?.key(event) { homeProbe?.returnToHome() ?: finish() } == true) return true
                    return ordinaryCallback.dispatchKeyEvent(event)
                }
            }
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            probeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope ->
                scope.launch {
                    BrowseProbeTestHooks.activeLeases.incrementAndGet()
                    com.ichichuang.cinema.feature.home.visual.VisualHomeTestHooks.activeLeases.incrementAndGet()
                    com.ichichuang.cinema.feature.library.LibraryTestHooks.activeLeases.incrementAndGet()
                    com.ichichuang.cinema.feature.detail.WorkDetailTestHooks.activeLeases.incrementAndGet()
                    try {
                        if (BuildConfig.PERSONAL_LIBRARY_PROBE) com.ichichuang.cinema.feature.personal.PersonalLibraryAuthorization.requireOwned(this@MainActivity)
                        if (BuildConfig.HOME_VISUAL_PROBE) com.ichichuang.cinema.feature.home.visual.VisualHomeAuthorization.requireOwned(this@MainActivity)
                        if (BuildConfig.LIBRARY_FILTER_PROBE) com.ichichuang.cinema.feature.library.LibraryAuthorization.requireOwned(this@MainActivity)
                        if (BuildConfig.DETAIL_PROBE) com.ichichuang.cinema.feature.detail.WorkDetailAuthorization.requireOwned(this@MainActivity)
                        if (BuildConfig.PLAYER_CONTROLS_PROBE) com.ichichuang.cinema.feature.player.PlayerControlsAuthorization.requireOwned(this@MainActivity)
                        DebugBrowseProbeGate.withOwnedProfile(this@MainActivity) { database ->
                            coroutineScope {
                                val fixture = withContext(Dispatchers.IO) { BrowseProbeFixture(this@MainActivity) }
                                val runtime = BrowseProbeRuntime(this@MainActivity, RoomBrowseStateStore(database), this,
                                    fixture, personalDatabase = if (BuildConfig.PERSONAL_LIBRARY_PROBE) database else null)
                                // R14 片库切片：挂在既有授权链上（首页+个人库必须同开），不新增激活入口。
                                val library = if (BuildConfig.LIBRARY_FILTER_PROBE && BuildConfig.HOME_VISUAL_PROBE && BuildConfig.PERSONAL_LIBRARY_PROBE)
                                    com.ichichuang.cinema.feature.library.LibraryRuntime(this@MainActivity, runtime, database, this, fixture) else null
                                val home = if (BuildConfig.HOME_VISUAL_PROBE && BuildConfig.PERSONAL_LIBRARY_PROBE)
                                    com.ichichuang.cinema.feature.home.visual.VisualHomeRuntime(this@MainActivity, runtime, database, this, fixture, library) else null
                                // R15 详情切片：链在全部既有探针授权之上（缺一即不构造），不新增激活入口。
                                val detail = if (BuildConfig.DETAIL_PROBE && BuildConfig.LIBRARY_FILTER_PROBE &&
                                    BuildConfig.HOME_VISUAL_PROBE && BuildConfig.PERSONAL_LIBRARY_PROBE)
                                    com.ichichuang.cinema.feature.detail.WorkDetailRuntime(this@MainActivity, runtime, this, fixture) else null
                                // R16 播放器控制切片：链在全部既有探针授权之上（含详情探针），不新增激活入口；
                                // 复用既有唯一播放器/检查点权威（PersonalLibraryRuntime），不建第二个播放器。
                                val playerControls = if (BuildConfig.PLAYER_CONTROLS_PROBE && BuildConfig.DETAIL_PROBE &&
                                    BuildConfig.LIBRARY_FILTER_PROBE && BuildConfig.HOME_VISUAL_PROBE && BuildConfig.PERSONAL_LIBRARY_PROBE)
                                    com.ichichuang.cinema.feature.player.PlayerControlsRuntime(this@MainActivity, runtime, this, fixture) else null
                                // 个人记录变化既通知首页重投影，也让 R15 详情按钮/徽标反映已提交状态。
                                if (detail != null) {
                                    val priorChanged = runtime.onPersonalChanged
                                    runtime.onPersonalChanged = { priorChanged?.invoke(); detail.personalRecordsChanged() }
                                }
                                // 先就位首页再发布浏览运行时，导航图一次成型（候选首页为根）。
                                homeProbe = home
                                libraryProbe = library
                                detailProbe = detail
                                playerControlsProbe = playerControls
                                browseProbe = runtime
                                try {
                                    runtime.start()
                                    library?.start()
                                    home?.start()
                                    playerControls?.start()
                                    awaitCancellation()
                                }
                                finally {
                                    withContext(NonCancellable) {
                                        playerControls?.close()
                                        home?.shutdown()
                                        library?.shutdown()
                                        runtime.shutdown()
                                    }
                                }
                            }
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { homeProbe = null; libraryProbe = null; detailProbe = null; playerControlsProbe = null; browseProbe = null; probeError = "无法进入浏览测试，请检查独立测试授权与本地资料" }
                    finally {
                        com.ichichuang.cinema.feature.detail.WorkDetailTestHooks.activeLeases.decrementAndGet()
                        com.ichichuang.cinema.feature.library.LibraryTestHooks.activeLeases.decrementAndGet()
                        com.ichichuang.cinema.feature.home.visual.VisualHomeTestHooks.activeLeases.decrementAndGet()
                        BrowseProbeTestHooks.activeLeases.decrementAndGet()
                    }
                }
            }
            setContent {
                CinemaTheme {
                    val runtime = browseProbe
                    if (runtime != null) CinemaNavHost(runtime, onExit = { finish() }, home = homeProbe, detail = detailProbe,
                        playerControls = playerControlsProbe)
                    else Text(probeError ?: "正在核验独立浏览测试环境", modifier = androidx.compose.ui.Modifier.then(
                        if (probeError != null) androidx.compose.ui.Modifier.testTag("浏览测试不可用") else androidx.compose.ui.Modifier))
                }
            }
            return
        }
        setContent {
            CinemaTheme {
                CinemaNavHost(
                    updateCenter = updateCenter,
                    homeViewModel = homeViewModel,
                )
            }
        }
    }
}
