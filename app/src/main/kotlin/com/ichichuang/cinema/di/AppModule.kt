package com.ichichuang.cinema.di

import com.ichichuang.cinema.data.MaccmsMovieRepository
import com.ichichuang.cinema.data.SwitchableMovieRepository
import com.ichichuang.cinema.data.demo.DemoMovieRepository
import com.ichichuang.cinema.data.remote.MaccmsApi
import com.ichichuang.cinema.data.remote.buildHttpClient
import com.ichichuang.cinema.data.settings.CinemaSettings
import com.ichichuang.cinema.data.settings.DataStoreFilterStore
import com.ichichuang.cinema.data.settings.PlaybackProgress
import com.ichichuang.cinema.data.update.GitHubUpdater
import com.ichichuang.cinema.domain.repository.FilterStore
import com.ichichuang.cinema.domain.repository.MovieRepository
import com.ichichuang.cinema.domain.repository.Updater
import com.ichichuang.cinema.feature.home.HomeViewModel
import com.ichichuang.cinema.feature.player.PlayerViewModel
import com.ichichuang.cinema.feature.settings.SettingsViewModel
import com.ichichuang.cinema.feature.update.UpdateCenter
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * 依赖装配总表 —— 全应用只有这一处知道"具体实现是谁"。
 * 换片源格式（苹果CMS → 别的）、换 http 实现，都只动这个文件。
 */
val appModule = module {

    // ── 基础设施 ─────────────────────────────────────────────
    single { CinemaSettings(androidContext()) }
    single { PlaybackProgress(androidContext()) }
    /** 筛选记忆：默认国语，用户改过就记住他改的 */
    single<FilterStore> { DataStoreFilterStore(androidContext()) }
    single { buildHttpClient() }
    single {
        Json {
            ignoreUnknownKeys = true   // 各家接口字段千奇百怪，不认识就跳过
            isLenient = true           // 容忍不规范 JSON
        }
    }

    // ── 片源 ─────────────────────────────────────────────────
    single { MaccmsApi(http = get(), json = get()) }
    single { MaccmsMovieRepository(api = get(), settings = get()) }
    single { DemoMovieRepository() }

    // UI 拿到的永远是"能根据设置自动切换"的那一个
    single<MovieRepository> {
        SwitchableMovieRepository(settings = get(), remote = get(), demo = get())
    }

    // ── 在线更新 ─────────────────────────────────────────────
    single<Updater> { GitHubUpdater(context = androidContext(), http = get(), json = get(), settings = get()) }
    /**
     * 单例而不是 viewModel：首页（金标 + 浮层）和设置页（检查更新）必须共享同一份状态。
     * 各建一个 ViewModel 会出现"首页说没新版、设置页说有新版"的鬼故事。
     */
    single { UpdateCenter(context = androidContext(), updater = get()) }

    // ── 页面 ─────────────────────────────────────────────────
    viewModel { HomeViewModel(repository = get(), progress = get(), filterStore = get()) }
    viewModel { SettingsViewModel(get()) }
    // 播放器要 Context 建 ExoPlayer —— 用 application context，不会漏 Activity
    viewModel { PlayerViewModel(repository = get(), progress = get(), context = androidContext()) }
}
