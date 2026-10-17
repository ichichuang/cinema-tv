package com.ichichuang.cinema.nav

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ichichuang.cinema.core.design.Motion
import com.ichichuang.cinema.domain.model.Movie
import com.ichichuang.cinema.feature.home.HomeScreen
import com.ichichuang.cinema.feature.player.PlayerScreen
import com.ichichuang.cinema.feature.settings.SettingsScreen

/**
 * 全部路由。只有 3 个页面 —— 这是刻意的：
 * 首页（含常驻详情）→ 播放 → 设置。
 * 极简不是少写代码，是少让用户做选择。
 */
private object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val PLAYER = "player/{movieId}?title={title}"

    fun player(movie: Movie) =
        "player/${Uri.encode(movie.id)}?title=${Uri.encode(movie.title)}"
}

/**
 * 导航图。转场用统一的动效规范：
 * 进入 = 淡入 + 轻微右移推入（Apple TV 式的"翻页感"），退出 = 快速淡出。
 */
@Composable
fun CinemaNavHost() {
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
                onPlay = { movie -> nav.navigate(Routes.player(movie)) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }

        composable(
            route = Routes.PLAYER,
            arguments = listOf(
                navArgument("movieId") { type = NavType.StringType },
                navArgument("title") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            PlayerScreen(
                movieId = entry.arguments?.getString("movieId").orEmpty(),
                title = entry.arguments?.getString("title").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
    }
}
