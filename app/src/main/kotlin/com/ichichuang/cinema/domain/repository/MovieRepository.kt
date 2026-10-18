package com.ichichuang.cinema.domain.repository

import com.ichichuang.cinema.domain.model.MovieDetail
import com.ichichuang.cinema.domain.model.MovieFilter
import com.ichichuang.cinema.domain.model.MoviePage
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
     *  - 没填 → 首页显示"去设置里扫码配置"的引导，而不是报错
     *  - 填了/改了 → 自动重新加载，不需要重启应用
     *    （手机扫码保存后能"电视实时生效"，靠的就是这条流）
     */
    val config: Flow<String>

    /** 分类列表（如：全部 / 电影 / 电视剧 …）；未配置片源时返回空 */
    suspend fun categories(): List<String>

    /**
     * 按筛选条件分页取影片；page 从 1 开始。
     *
     * 契约里有一条重要的**软要求**：如果这个筛选项服务器端筛不出东西，
     * 实现应该尽量降级（本地筛 / 放开条件）而不是返回空 —— 见 MoviePage.relaxed。
     * 理由：电视上一片空白是最糟的体验，宁可给点东西 + 一句人话解释。
     */
    suspend fun movies(filter: MovieFilter, page: Int, pageSize: Int = 24): MoviePage

    /**
     * 影片详情（简介 + 播放线路）；取不到返回 null。
     *
     * [origin] = 影片来自哪个片源（Movie.origin）。多源聚合时用它把请求
     * 打到正确的站；单源/演示场景传空串即可。
     */
    suspend fun detail(id: String, origin: String = ""): MovieDetail?
}
