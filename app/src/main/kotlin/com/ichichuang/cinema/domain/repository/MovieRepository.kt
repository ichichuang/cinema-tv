package com.ichichuang.cinema.domain.repository

import com.ichichuang.cinema.domain.model.Movie
import com.ichichuang.cinema.domain.model.MovieDetail
import kotlinx.coroutines.flow.Flow

/**
 * 片源仓库 —— 整个应用唯一的"数据入口抽象"。
 *
 * UI / ViewModel 只认这个接口，不认任何具体的接口格式。
 * 苹果CMS、以后可能的 Jellyfin/本地文件，都只是它的不同实现。
 */
interface MovieRepository {

    /**
     * 片源配置（接口地址）的变化流。空串 = 用户还没填。
     *
     * UI 靠它做两件事：
     *  - 没填 → 首页显示"去设置里填接口地址"的引导，而不是报错
     *  - 填了/改了 → 自动重新加载，不需要重启应用
     */
    val config: Flow<String>

    /** 分类列表（如：全部 / 电影 / 电视剧 …）；未配置片源时返回空 */
    suspend fun categories(): List<String>

    /** 按分类分页取影片；page 从 1 开始 */
    suspend fun movies(category: String, page: Int, pageSize: Int = 24): List<Movie>

    /** 按关键词搜索 */
    suspend fun search(keyword: String, page: Int, pageSize: Int = 24): List<Movie>

    /** 影片详情（简介 + 播放线路）；取不到返回 null */
    suspend fun detail(id: String): MovieDetail?
}
