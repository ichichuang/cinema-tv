package com.ichichuang.cinema.domain.model

import com.ichichuang.cinema.domain.repository.CATEGORY_ALL

/**
 * 筛选条件。
 *
 * 约定：**空串 = 该维度不过滤**（界面上对应"全部"）。
 * 这样"有没有筛选"不需要额外的布尔量，`isBlank()` 就是答案。
 */
data class MovieFilter(
    val category: String = CATEGORY_ALL,
    /** 语言，如 国语/粤语/英语（苹果CMS 的 lang 参数） */
    val lang: String = "",
    /** 地区，如 大陆/香港/美国（苹果CMS 的 area 参数） */
    val area: String = "",
    /** 年份，如 "2025"（苹果CMS 的 year 参数） */
    val year: String = "",
) {
    /** 三个可选维度都是空的 → 服务器端不需要任何过滤参数 */
    val noServerFilter: Boolean get() = lang.isBlank() && area.isBlank() && year.isBlank()

    /** 生效中的可选维度（摘要里显示用） */
    val activeParts: List<String>
        get() = listOf(lang, area, year).filter { it.isNotBlank() }
}

/**
 * 首次运行（或用户点"重置"）时的默认筛选：**只看国语**。
 * 这是用户明确要的默认行为；他改过之后会被记住，不会每次都被改回来。
 */
val DefaultMovieFilter = MovieFilter(lang = "国语")

/**
 * 一页影片 + 这次结果的诚实说明。
 *
 * [relaxed] = 为了不给你一片空白，服务器/本地放宽了筛选条件（界面会用人话解释）。
 */
data class MoviePage(
    val movies: List<Movie>,
    val relaxed: Boolean = false,
    /** 接口给的总数（有些站不返回，此时为 0，界面就不显示"共 N 部"） */
    val total: Int = 0,
)
