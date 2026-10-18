package com.ichichuang.cinema.data

import com.ichichuang.cinema.data.remote.MaccmsApi
import com.ichichuang.cinema.data.remote.RemoteCategory
import com.ichichuang.cinema.data.remote.SourceException
import com.ichichuang.cinema.data.settings.CinemaSettings
import com.ichichuang.cinema.domain.model.CategoryTree
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

    override suspend fun categoryTree(): CategoryTree {
        val list = targets()
        if (list.isEmpty()) return CategoryTree()
        // 多源合并：每个站各拉一次自己的树（拿不动的不拖累别的），并集展示
        val trees = coroutineScope {
            list.map { source -> async { runCatching { source.tree() } } }.awaitAll()
        }
        return CategoryTree.merge(
            trees.mapNotNull { it.getOrNull() }.map { toTree(it) },
        )
    }

    /** 单站的原始分类数组 → 分类树（顶层 / 各自的下级 / 平铺站的原样名字） */
    private fun toTree(all: List<RemoteCategory>): CategoryTree {
        val hasStructure = all.any { !it.isTopLevel() }
        if (!hasStructure) {
            // 平铺站（老模板不给 type_pid）：名字既当分类也当类型
            return CategoryTree(tops = all.map { it.name }.distinct(), flat = all.map { it.name }.distinct())
        }
        val tops = all.filter { it.isTopLevel() }.map { it.name }.distinct()
        val children = LinkedHashMap<String, MutableList<String>>()
        all.filterNot { it.isTopLevel() }.forEach { child ->
            val parent = all.firstOrNull { it.typeId == child.pid } ?: return@forEach
            val list = children.getOrPut(parent.name) { mutableListOf() }
            if (child.name !in list) list += child.name
        }
        return CategoryTree(tops, children)
    }

    private fun RemoteCategory.isTopLevel(): Boolean =
        pid == null || pid.isEmpty() || pid == "0" || pid == typeId

    override suspend fun movies(filter: MovieFilter, page: Int, pageSize: Int): MoviePage {
        val list = targets()
        if (list.isEmpty()) {
            throw SourceException("还没有添加资源：到「设置」里点「选择资源」，用手机扫码也可以")
        }
        if (list.size == 1) return list[0].movies(filter, page, pageSize)

        // 多源聚合：并发取每源的同一页；个别源挂了不拖垮整页
        val results = coroutineScope {
            list.map { source -> async { runCatching { source.movies(filter, page, pageSize) } } }.awaitAll()
        }
        val ok = results.mapNotNull { it.getOrNull() }
        if (ok.isEmpty()) {
            throw results.firstNotNullOfOrNull { it.exceptionOrNull() }
                ?: SourceException("所有资源都暂时取不到内容，过一会儿再试")
        }

        // 按"片名+年份"去重：同一部片在多个源都出现时，只留排在前面的源的版本
        val merged = LinkedHashMap<String, Movie>()
        ok.forEach { pageData ->
            pageData.movies.forEach { movie -> merged.putIfAbsent(movie.mergeKey, movie) }
        }
        // 诚实说明：几个源没有这个分类（被跳过）/ 结果是空的时候为什么空
        val skipCount = ok.count { it.skippedNoMatch }
        val note = when {
            merged.isEmpty() && skipCount > 0 && skipCount == ok.size ->
                "启用的资源里都没有这个分类或类型"
            merged.isEmpty() && ok.isNotEmpty() && ok.all { it.movies.isEmpty() } ->
                "按当前筛选条件，都没找到结果"
            skipCount > 0 -> "有 $skipCount 个资源没有这个分类/类型，已跳过"
            else -> null
        }
        return MoviePage(
            movies = merged.values.toList(),
            relaxed = ok.any { it.relaxed },
            total = ok.sumOf { it.total },
            note = note,
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
