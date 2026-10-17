package com.ichichuang.cinema

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ichichuang.cinema.core.design.CinemaTheme
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
 * 在线更新的状态中枢（UpdateCenter）在这里取一次，往导航图里传：
 * 首页（小金标 + 更新浮层）和设置页（检查更新）用的是同一个实例。
 */
class MainActivity : ComponentActivity(), KoinComponent {

    private val updateCenter: UpdateCenter by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CinemaTheme {
                CinemaNavHost(updateCenter = updateCenter)
            }
        }
    }
}
