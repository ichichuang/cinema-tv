package com.ichichuang.cinema.data

import com.ichichuang.cinema.data.remote.MaccmsApi
import com.ichichuang.cinema.data.remote.RemoteVod
import com.ichichuang.cinema.data.remote.SourceException
import com.ichichuang.cinema.data.remote.normalizeBaseUrl
import com.ichichuang.cinema.data.remote.stripHtml
import com.ichichuang.cinema.data.settings.CinemaSettings
import com.ichichuang.cinema.domain.model.Episode
import com.ichichuang.cinema.domain.model.Movie
import com.ichichuang.cinema.domain.model.MovieDetail
import com.ichichuang.cinema.domain.model.PlayLine
import com.ichichuang.cinema.domain.repository.CATEGORY_ALL
import com.ichichuang.cinema.domain.repository.MovieRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl

/**
 * 真实片源实现：**苹果CMS V10 标准接口**。
 *
 * 这个类只做一件事：把接口的 vod_* 字段翻译成干净的领域模型。
 * 所有"接口长什么样"的知识都锁在这里（+ MaccmsApi），外面的代码完全不知道。
 */
class MaccmsMovieRepository(
    private val api: MaccmsApi,
    private val settings: CinemaSettings,
) : MovieRepository {

    override val config: Flow<String> = settings.apiBase

    // 分类树基本不变：缓存住，地址变了才重新取（避免每次切页都打接口）
    private var cachedCategoriesFor: String? = null
    private var cachedNames: List<String> = emptyList()
    private var cachedIds: Map<String, String> = emptyMap()

    override suspend fun categories(): List<String> {
        val raw = usableBase() ?: return emptyList()
        if (cachedCategoriesFor == raw && cachedNames.isNotEmpty()) return cachedNames
        val remote = api.categories(baseUrl(raw))
        val names = listOf(CATEGORY_ALL) + remote.map { it.name }
        cachedCategoriesFor = raw
        cachedNames = names
        cachedIds = remote.associate { it.name to it.typeId }
        return names
    }

    override suspend fun movies(category: String, page: Int, pageSize: Int): List<Movie> {
        val raw = requireBase()
        val base = baseUrl(raw)
        // 分类名 → type_id。取不到就当作"全部"（不分类），比报错友好
        val typeId = if (category == CATEGORY_ALL) null else resolveTypeId(raw, category, base)
        return api.list(base = base, typeId = typeId, page = page).items.map { it.toMovie(base) }
    }

    override suspend fun search(keyword: String, page: Int, pageSize: Int): List<Movie> {
        if (keyword.isBlank()) return emptyList()
        val raw = requireBase()
        val base = baseUrl(raw)
        return api.list(base = base, typeId = null, page = page, keyword = keyword)
            .items.map { it.toMovie(base) }
    }

    override suspend fun detail(id: String): MovieDetail? {
        val raw = requireBase()
        val base = baseUrl(raw)
        val vod = api.detail(base, id) ?: return null
        return MovieDetail(
            movie = vod.toMovie(base),
            synopsis = vod.content?.stripHtml().orEmpty(),
            area = vod.area.orEmpty(),
            lang = vod.lang.orEmpty(),
            lines = vod.playSources.map { source ->
                PlayLine(
                    name = source.name,
                    episodes = source.episodes.map { Episode(it.name, it.url) },
                )
            },
        )
    }

    // ── 内部 ───────────────────────────────────────────────────

    /** 还没配置（或处于演示模式）时返回 null —— 调用方决定怎么表现 */
    private suspend fun usableBase(): String? =
        settings.apiBase.first().trim().takeIf { it.isNotEmpty() && it != CinemaSettings.DEMO }

    private suspend fun requireBase(): String =
        usableBase() ?: throw SourceException("还没有配置影视接口地址：请到「设置」里填写")

    private fun baseUrl(raw: String): HttpUrl =
        normalizeBaseUrl(raw) ?: throw SourceException("接口地址格式不对：$raw")

    private suspend fun resolveTypeId(raw: String, category: String, base: HttpUrl): String? {
        if (cachedCategoriesFor != raw) categories()
        cachedIds[category]?.let { return it }
        if (cachedCategoriesFor != raw) {
            // 分类缓存对不上（用户刚改地址）：再取一次
            api.categories(base).let { remote ->
                cachedCategoriesFor = raw
                cachedNames = listOf(CATEGORY_ALL) + remote.map { it.name }
                cachedIds = remote.associate { it.name to it.typeId }
            }
        }
        return cachedIds[category]
    }

    private fun RemoteVod.toMovie(base: HttpUrl): Movie = Movie(
        id = id,
        title = name,
        year = year?.take(4)?.toIntOrNull() ?: 0,
        score = score?.toDoubleOrNull() ?: 0.0,
        genre = typeName.orEmpty(),
        minutes = 0,      // 列表接口不提供时长（详情里也没有，字段留 0，UI 会隐藏）
        synopsis = "",    // 简介走 detail() 异步取，列表页不为此多等一次请求
        posterUrl = pic?.let { absolutize(it, base) },
        remarks = remarks,
    )

    /** 接口返回的图片地址可能是相对路径，补成绝对地址 */
    private fun absolutize(url: String, base: HttpUrl): String {
        val u = url.trim()
        return when {
            u.startsWith("http://", true) || u.startsWith("https://", true) -> u
            u.startsWith("//") -> "${base.scheme}:$u"
            u.startsWith("/") -> "${base.scheme}://${base.host}$u"
            else -> "${base.scheme}://${base.host}/$u"
        }
    }
}
