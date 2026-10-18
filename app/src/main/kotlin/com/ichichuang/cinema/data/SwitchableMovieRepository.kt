package com.ichichuang.cinema.data

import com.ichichuang.cinema.data.demo.DemoMovieRepository
import com.ichichuang.cinema.data.settings.CinemaSettings
import com.ichichuang.cinema.domain.model.MovieDetail
import com.ichichuang.cinema.domain.model.MovieFilter
import com.ichichuang.cinema.domain.model.MoviePage
import com.ichichuang.cinema.domain.repository.MovieRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * 片源路由：按用户填的接口地址决定用哪个实现。
 *
 * 为什么不直接在 DI 里二选一？因为地址是**运行时可变的**（用户随时改），
 * 而 DI 只在启动时装配一次。所以把"选谁"这件事也放进数据流：
 * 地址一变，下一次取数据就自动走新实现，不用重启应用。
 */
class SwitchableMovieRepository(
    private val settings: CinemaSettings,
    private val remote: MaccmsMovieRepository,
    private val demo: DemoMovieRepository,
) : MovieRepository {

    // 配置签名：启用集合/主源/demo 任一变化 → 首页整墙重载
    override val config: Flow<String> = settings.sourceSignature

    private suspend fun target(): MovieRepository =
        if (settings.apiBase.first().trim() == CinemaSettings.DEMO) demo else remote

    override suspend fun categories(): List<String> = target().categories()

    override suspend fun movies(filter: MovieFilter, page: Int, pageSize: Int): MoviePage =
        target().movies(filter, page, pageSize)

    override suspend fun detail(id: String, origin: String): MovieDetail? = target().detail(id, origin)
}
