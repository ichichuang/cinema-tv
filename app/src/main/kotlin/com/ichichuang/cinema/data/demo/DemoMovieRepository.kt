package com.ichichuang.cinema.data.demo

import com.ichichuang.cinema.data.settings.CinemaSettings
import com.ichichuang.cinema.domain.model.Episode
import com.ichichuang.cinema.domain.model.Movie
import com.ichichuang.cinema.domain.model.MovieDetail
import com.ichichuang.cinema.domain.model.PlayLine
import com.ichichuang.cinema.domain.repository.CATEGORY_ALL
import com.ichichuang.cinema.domain.repository.MovieRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * 演示实现：把内存里的假数据按仓库接口的契约返回。
 *
 * 用途有两个：
 *  1. 验证"接口抽象"站得住 —— 换片源不需要动 UI 一行
 *  2. 接口临时挂掉时，用它区分"是应用坏了"还是"是片源坏了"
 *     （设置里把接口地址填 `demo` 即可切到它）
 */
class DemoMovieRepository : MovieRepository {

    override val config: Flow<String> = flowOf(CinemaSettings.DEMO)

    override suspend fun categories(): List<String> = DEMO_CATEGORIES

    override suspend fun movies(category: String, page: Int, pageSize: Int): List<Movie> {
        val all = when (category) {
            CATEGORY_ALL -> DEMO_MOVIES
            else -> DEMO_MOVIES.filter { it.genre == category }
        }
        val from = (page - 1) * pageSize
        if (from >= all.size) return emptyList()
        return all.drop(from).take(pageSize)
    }

    override suspend fun search(keyword: String, page: Int, pageSize: Int): List<Movie> {
        if (keyword.isBlank()) return emptyList()
        return DEMO_MOVIES.filter { it.title.contains(keyword) }
    }

    override suspend fun detail(id: String): MovieDetail? {
        val movie = DEMO_MOVIES.firstOrNull { it.id == id } ?: return null
        return MovieDetail(
            movie = movie,
            synopsis = movie.synopsis,
            area = "演示数据",
            lang = "演示数据",
            lines = listOf(
                PlayLine(
                    name = "演示线路",
                    episodes = listOf(Episode("第1集", "http://demo.invalid/ep1.mp4")),
                ),
            ),
        )
    }
}
