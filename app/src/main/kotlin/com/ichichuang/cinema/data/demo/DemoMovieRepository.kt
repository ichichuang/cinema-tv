package com.ichichuang.cinema.data.demo

import com.ichichuang.cinema.data.settings.CinemaSettings
import com.ichichuang.cinema.domain.model.Episode
import com.ichichuang.cinema.domain.model.Movie
import com.ichichuang.cinema.domain.model.MovieDetail
import com.ichichuang.cinema.domain.model.MovieFilter
import com.ichichuang.cinema.domain.model.MoviePage
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
 *
 * 筛选行为也照着真实实现的契约来（本地筛 + 筛不出就放宽并如实标记），
 * 这样"填 demo 先看界面"看到的手感跟真片源一致。
 */
class DemoMovieRepository : MovieRepository {

    override val config: Flow<String> = flowOf(CinemaSettings.DEMO)

    override suspend fun categories(): List<String> = DEMO_CATEGORIES

    override suspend fun movies(filter: MovieFilter, page: Int, pageSize: Int): MoviePage {
        val byCategory = DEMO_MOVIES.filter {
            filter.category == CATEGORY_ALL || it.genre == filter.category
        }
        val matched = byCategory.filter { it.matches(filter) }

        // 本地也筛不出来 → 放宽（跟真实片源同一套规矩：宁可给内容 + 说明，不给空白）
        val hitRelaxed = matched.isEmpty() && page == 1 && !filter.noServerFilter
        val all = if (hitRelaxed) byCategory else matched

        val from = (page - 1) * pageSize
        val items = if (from >= all.size) emptyList() else all.drop(from).take(pageSize)
        return MoviePage(movies = items, total = all.size, relaxed = hitRelaxed)
    }

    override suspend fun detail(id: String, origin: String): MovieDetail? {
        val movie = DEMO_MOVIES.firstOrNull { it.id == id } ?: return null
        return MovieDetail(
            movie = movie,
            synopsis = movie.synopsis,
            area = movie.area.ifBlank { "演示数据" },
            lang = movie.lang.ifBlank { "演示数据" },
            lines = listOf(
                PlayLine(
                    name = "演示线路",
                    episodes = listOf(Episode("第1集", "http://demo.invalid/ep1.mp4")),
                ),
            ),
        )
    }

    /** 演示数据的本地筛选：字段是空的算不命中（跟真实实现一致，不糊弄） */
    private fun Movie.matches(f: MovieFilter): Boolean {
        if (f.lang.isNotBlank() && !lang.contains(f.lang)) return false
        if (f.area.isNotBlank() && !area.contains(f.area)) return false
        if (f.year.isNotBlank() && year.toString() != f.year) return false
        if (f.movieClass.isNotBlank() && !genre.contains(f.movieClass)) return false
        return true
    }
}
