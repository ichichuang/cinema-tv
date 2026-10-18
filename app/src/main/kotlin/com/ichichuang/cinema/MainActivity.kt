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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
