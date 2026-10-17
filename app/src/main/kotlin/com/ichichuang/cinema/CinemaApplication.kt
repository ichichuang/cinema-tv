package com.ichichuang.cinema

import android.app.Application
import com.ichichuang.cinema.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * 应用入口。只做一件事：装配依赖（Koin）。
 *
 * 为什么用 Koin 而不是 Hilt：Koin 没有注解处理器（KSP/kapt），
 * 构建更快、出错信息更好读，对"云端构建 + 快速迭代"这条路线更合适。
 */
class CinemaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@CinemaApplication)
            modules(appModule)
        }
    }
}
