package com.ichichuang.cinema.data

import com.ichichuang.cinema.data.remote.MaccmsApi
import com.ichichuang.cinema.data.remote.SourceException
import com.ichichuang.cinema.data.settings.CinemaSettings
import com.ichichuang.cinema.domain.model.Movie
import com.ichichuang.cinema.domain.model.MovieDetail
import com.ichichuang.cinema.domain.model.MovieFilter
import com.ichichuang.cinema.domain.model.MoviePage
import com.ichichuang.cinema.domain.repository.MovieRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * 真实片源实现：**苹果CMS V10 标准接口**，支持"同时启用多个片源"。
 *
 * - 只启用一个源（没多选过的，包括老用户）→ 行为和以前完全一样；
 * - 启用多个 → 每页并发取所有启用源，按顺序拼接、按「片名+年份」去重，
 *   首页就能看到"好多好多电影资源"。
 *
 * 每个源自己的状态（分类缓存、严格/放宽）跟着 MaccmsSource 实例走，互不串味。
 */
class MaccmsMovieRepository(
    private val api: MaccmsApi,
    private val settings: CinemaSettings,
) : MovieRepository {

    override val config: Flow<String> = settings.sourceSignature

    /** 一个地址一个 MaccmsSource，拿过就复用 */
    private val cache = LinkedHashMap<String, MaccmsSource>()

    private fun sourceOf(raw: String): MaccmsSource = synchronized(cache) {
        cache.getOrPut(raw) { MaccmsSource(api, raw) }
    }

    /** 当前"该取哪些源"：启用集合（含老版单源迁移），空 = 还没配置 */
    private suspend fun targets(): List<MaccmsSource> = settings.activeUrlsNow().map(::sourceOf)

    override suspend fun categories(): List<String> =
        // 分类列表以第一个启用源为准（各站的"电影/连续剧/综艺/动漫"大差不差）
        targets().firstOrNull()?.categories() ?: emptyList()

    override suspend fun movies(filter: MovieFilter, page: Int, pageSize: Int): MoviePage {
        val list = targets()
        if (list.isEmpty()) {
            throw SourceException("还没有配置影视接口地址：到「设置」里点「选择片源」，用手机扫码也可以")
        }
        if (list.size == 1) return list[0].movies(filter, page, pageSize)

        // 多源聚合：并发取每源的同一页；个别源挂了不拖垮整页
        val results = coroutineScope {
            list.map { source -> async { runCatching { source.movies(filter, page, pageSize) } } }.awaitAll()
        }
        val ok = results.mapNotNull { it.getOrNull() }
        if (ok.isEmpty()) {
            throw results.firstNotNullOfOrNull { it.exceptionOrNull() }
                ?: SourceException("所有片源都暂时取不到数据，过一会儿再试")
        }

        // 按"片名+年份"去重：同一部片在多个源都出现时，只留排在前面的源的版本
        val merged = LinkedHashMap<String, Movie>()
        ok.forEach { pageData ->
            pageData.movies.forEach { movie -> merged.putIfAbsent(movie.mergeKey, movie) }
        }
        return MoviePage(
            movies = merged.values.toList(),
            relaxed = ok.any { it.relaxed },
            total = ok.sumOf { it.total },
        )
    }

    override suspend fun detail(id: String, origin: String): MovieDetail? {
        if (origin.isNotBlank()) return sourceOf(origin).detail(id)
        // 没带来源（进程被杀后冷启动直接进详情）：逐个启用源试一遍
        for (source in targets()) {
            runCatching { source.detail(id) }.getOrNull()?.let { return it }
        }
        return null
    }
}
