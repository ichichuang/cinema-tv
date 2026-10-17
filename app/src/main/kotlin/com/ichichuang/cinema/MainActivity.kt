package com.ichichuang.cinema

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ichichuang.cinema.core.design.CinemaTheme
import com.ichichuang.cinema.nav.CinemaNavHost

/**
 * 唯一的 Activity。
 *
 * 整屏 UI 结构：CinemaTheme（色板 + 动效规范 + 电视尺寸适配）→ CinemaNavHost（导航）。
 * 除此之外什么都不做 —— 逻辑全在 ViewModel，样式全在 core/design。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CinemaTheme {
                CinemaNavHost()
            }
        }
    }
}
