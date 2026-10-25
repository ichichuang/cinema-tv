package com.ichichuang.cinema.nav

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ichichuang.cinema.core.design.Motion
import com.ichichuang.cinema.domain.model.Movie
import com.ichichuang.cinema.feature.detail.DetailScreen
import com.ichichuang.cinema.feature.home.HomeScreen
import com.ichichuang.cinema.feature.home.HomeViewModel
import com.ichichuang.cinema.feature.player.PlayerScreen
import com.ichichuang.cinema.feature.settings.SettingsScreen
import com.ichichuang.cinema.feature.update.UpdateCenter

/**
 * 全部路由 —— 只有 4 个页面，这是刻意的：
 * 首页 → 详情 → 播放 → 设置。
 * 极简不是少写代码，是少让用户做选择（搜索已在 v0.7.0 按用户要求整体移除）。
 */
private object Routes {
    const val HOME = "home"
    const val DETAIL = "detail/{movieId}?title={title}&poster={poster}"
    const val SETTINGS = "settings"
    const val PLAYER = "player/{movieId}?title={title}&origin={origin}"

    /**
     * 详情路由带上标题和海报：
     * 万一是"进程被杀后直接恢复到详情页"，列表还没回来 ——
     * 这两个参数能让画面立刻有东西，而不是一片黑等网络。
     */
    fun detail(movie: Movie) = "detail/${Uri.encode(movie.id)}" +
        "?title=${Uri.encode(movie.title)}" +
        "&poster=${Uri.encode(movie.posterUrl.orEmpty())}"

    /**
     * 播放路由带上 origin（影片来自哪个片源）：
     * 多源聚合时同一个 id 可能属于不同的站，播放页必须知道该找谁要地址。
     */
    fun player(movie: Movie) =
        "player/${Uri.encode(movie.id)}" +
            "?title=${Uri.encode(movie.title)}" +
            "&origin=${Uri.encode(movie.origin)}"
}

/**
 * 导航图。转场用统一的动效规范：
 * - 首页 ↔ 设置 / 播放：淡入 + 轻微右移推入（Apple TV 式的"翻页感"）
 * - **首页 → 详情：从屏幕右侧整屏滑入**（用户点名的效果），返回时原路滑出
 *
 * [homeViewModel] 是**首页和详情页共享的同一个实例**（从 Activity 传进来）：
 * 详情的数据（简介 / 线路 / 上次进度）在首页卡片被聚焦时就已经拉好了，
 * 共享之后点开详情是"瞬间有内容"，返回时首页的焦点和滚动位置也原样还在。
 *
 * [updateCenter] 同理 —— 首页（金标 + 浮层）和设置页必须是同一份更新状态。
 */
@Composable
fun CinemaNavHost(
    updateCenter: UpdateCenter,
    homeViewModel: HomeViewModel,
) {
    val nav = rememberNavController()

    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = { fadeIn(Motion.enter()) + slideInHorizontally { it / 14 } },
        exitTransition = { fadeOut(Motion.quick()) },
        popEnterTransition = { fadeIn(Motion.base()) },
        popExitTransition = { fadeOut(Motion.quick()) },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = homeViewModel,
                updateCenter = updateCenter,
                onOpenDetail = { movie -> nav.navigate(Routes.detail(movie)) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument("movieId") { type = NavType.StringType },
                navArgument("title") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("poster") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
            // 整屏从右侧滑入 / 返回时滑回去（其余页面保持默认的淡入淡出）
            enterTransition = {
                slideInHorizontally(animationSpec = Motion.enter()) { it } + fadeIn(Motion.quick())
            },
            popExitTransition = {
                slideOutHorizontally(animationSpec = Motion.enter()) { it } + fadeOut(Motion.quick())
            },
        ) { entry ->
            DetailScreen(
                movieId = entry.arguments?.getString("movieId").orEmpty(),
                title = entry.arguments?.getString("title").orEmpty(),
                posterUrl = entry.arguments?.getString("poster")?.takeIf { it.isNotBlank() },
                viewModel = homeViewModel,
                onPlay = { movie -> nav.navigate(Routes.player(movie)) },
                onBack = { nav.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                updateCenter = updateCenter,
            )
        }

        composable(
            route = Routes.PLAYER,
            arguments = listOf(
                navArgument("movieId") { type = NavType.StringType },
                navArgument("title") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("origin") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
            // 播放页：纯淡入淡出（像影院熄灯那种感觉），不做滑动 —— 滑进来的画面会分神
            enterTransition = { fadeIn(Motion.enter()) },
            popExitTransition = { fadeOut(Motion.quick()) },
        ) { entry ->
            PlayerScreen(
                movieId = entry.arguments?.getString("movieId").orEmpty(),
                title = entry.arguments?.getString("title").orEmpty(),
                origin = entry.arguments?.getString("origin").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
    }
}

/** Candidate seam selected before legacy lazy injection; unreachable in ordinary runtime. */
@Composable
internal fun CinemaNavHost(
    probe: com.ichichuang.cinema.feature.browse.BrowseProbeRuntime,
    onExit: () -> Unit,
    home: com.ichichuang.cinema.feature.home.visual.VisualHomeRuntime? = null,
) {
    check(com.ichichuang.cinema.BuildConfig.BROWSE_RESTORE_PROBE)
    val nav = rememberNavController()
    // 候选根画面：R13 首页存在时以“候选首页”为根，浏览/设置成为根级目的地；
    // 详情与播放器始终是浏览运行时的子层，叠在任何根画面之上。
    val root = if (home != null) "候选首页" else "候选浏览"
    val settingsActive = home != null && home.screen == com.ichichuang.cinema.feature.home.visual.HomeRootScreen.SETTINGS
    val browseActive = home != null && home.screen == com.ichichuang.cinema.feature.home.visual.HomeRootScreen.BROWSE
    // R14 片库切片仅在片库运行时就位（探针配对）时成为独立根目的地。
    val libraryActive = home != null && home.library != null &&
        home.screen == com.ichichuang.cinema.feature.home.visual.HomeRootScreen.LIBRARY
    val destination = when {
        probe.isPlayer -> "候选播放器"
        probe.isDetail -> "候选详情"
        settingsActive -> "候选设置"
        libraryActive -> "候选片库"
        browseActive -> "候选浏览"
        else -> root
    }
    androidx.compose.runtime.LaunchedEffect(destination) {
        if (nav.currentDestination?.route != destination) {
            when (destination) {
                "候选播放器" -> {
                    if (com.ichichuang.cinema.core.navigation.BrowseChildRoute.DETAIL in probe.navigation!!.returnChain && nav.currentDestination?.route == root) nav.navigate("候选详情")
                    if (nav.currentDestination?.route != destination) nav.navigate(destination)
                }
                else -> if (!nav.popBackStack(destination, false)) nav.navigate(destination)
            }
        }
    }
    NavHost(nav, startDestination = root, enterTransition = { androidx.compose.animation.EnterTransition.None },
        exitTransition = { androidx.compose.animation.ExitTransition.None }) {
        if (home != null) {
            composable("候选首页") { com.ichichuang.cinema.feature.home.visual.TvHomeScreen(home, onExit) }
            composable("候选设置") { com.ichichuang.cinema.feature.home.visual.CandidateSettingsScreen(home, onExit) }
            if (home.library != null) composable("候选片库") {
                com.ichichuang.cinema.feature.library.TvLibraryScreen(home.library, onExit)
            }
        }
        composable("候选浏览") { com.ichichuang.cinema.feature.browse.BrowseProbeScreen(probe, onExit) }
        composable("候选详情") { com.ichichuang.cinema.feature.browse.BrowseProbeDetail(probe, onExit) }
        composable("候选播放器") { com.ichichuang.cinema.feature.personal.PersonalLibraryPlayer(probe, onExit) }
    }
}
