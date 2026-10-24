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
    private var probeError by mutableStateOf<String?>(null)

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (BuildConfig.BROWSE_RESTORE_PROBE && browseProbe?.key(event) { finish() } == true) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        browseProbe?.background()
        super.onStop()
    }

    override fun onDestroy() {
        probeScope?.cancel()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.BROWSE_RESTORE_PROBE) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            probeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope ->
                scope.launch {
                    BrowseProbeTestHooks.activeLeases.incrementAndGet()
                    try {
                        DebugBrowseProbeGate.withOwnedProfile(this@MainActivity) { database ->
                            coroutineScope {
                                val runtime = BrowseProbeRuntime(this@MainActivity, RoomBrowseStateStore(database), this,
                                    withContext(Dispatchers.IO) { BrowseProbeFixture(this@MainActivity) })
                                browseProbe = runtime
                                try { runtime.start(); awaitCancellation() }
                                finally { withContext(NonCancellable) { runtime.shutdown() } }
                            }
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { browseProbe = null; probeError = "无法进入浏览测试，请检查独立测试授权与本地资料" }
                    finally { BrowseProbeTestHooks.activeLeases.decrementAndGet() }
                }
            }
            setContent {
                CinemaTheme {
                    val runtime = browseProbe
                    if (runtime != null) CinemaNavHost(runtime, onExit = { finish() })
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
