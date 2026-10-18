package com.ichichuang.cinema.data.demo

import com.ichichuang.cinema.data.settings.CinemaSettings
import com.ichichuang.cinema.domain.model.CategoryTree
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
 * 筛选行为也照着真实实现的契约来（分类/类型按真实的树核对、筛不出就诚实说明），
 * 这样"填 demo 先看界面"看到的手感跟真片源一致。
 */
class DemoMovieRepository : MovieRepository {

    override val config: Flow<String> = flowOf(CinemaSettings.DEMO)

    override suspend fun categoryTree(): CategoryTree = DEMO_TREE

    override suspend fun movies(filter: MovieFilter, page: Int, pageSize: Int): MoviePage {
        val types = filter.types.filter { it.isNotBlank() }
        val byCategory = DEMO_MOVIES.filter { it.inCategory(filter.category) }
        val matched = byCategory.filter { it.matches(filter, types) }

        // 语言/地区/年份滤空 → 放宽（跟真实片源同一套规矩：宁可给内容 + 说明，不给空白）；
        // 但分类/类型是用户点名要的 —— 筛空就诚实空，绝不放宽成"全量"
        val askedTypes = types.isNotEmpty() || filter.category != CATEGORY_ALL
        val hitRelaxed = matched.isEmpty() && page == 1 && !askedTypes && !filter.noServerFilter
        val all = if (hitRelaxed) byCategory else matched

        val from = (page - 1) * pageSize
        val items = if (from >= all.size) emptyList() else all.drop(from).take(pageSize)
        val note = when {
            items.isEmpty() && page == 1 && askedTypes -> "按当前筛选，演示数据里没找到结果"
            else -> null
        }
        return MoviePage(movies = items, total = all.size, relaxed = hitRelaxed, note = note)
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

    /** 演示数据里用 genre 当"下级类型"字段；顶层分类按树核对 */
    private fun Movie.inCategory(category: String): Boolean {
        if (category == CATEGORY_ALL) return true
        val kids = DEMO_TREE.children[category]
        if (kids != null) return genre in kids
        return genre == category
    }

    /** 本地筛选：字段是空的算不命中（跟真实实现一致，不糊弄） */
    private fun Movie.matches(f: MovieFilter, types: List<String>): Boolean {
        if (types.isNotEmpty() && genre !in types) return false
        if (f.lang.isNotBlank() && !lang.contains(f.lang)) return false
        if (f.area.isNotBlank() && !area.contains(f.area)) return false
        if (f.year.isNotBlank() && year.toString() != f.year) return false
        return true
    }
}
