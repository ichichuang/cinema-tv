package com.ichichuang.cinema.data

import com.ichichuang.cinema.data.remote.MaccmsApi
import com.ichichuang.cinema.data.remote.RemotePage
import com.ichichuang.cinema.data.remote.RemoteVod
import com.ichichuang.cinema.data.remote.SourceException
import com.ichichuang.cinema.data.remote.normalizeBaseUrl
import com.ichichuang.cinema.data.remote.stripHtml
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

    /** 当前这批结果是否处于"放宽"状态（服务器端筛不出、退到本地或放开） */
    private var relaxed = false
    private var lastFilter: MovieFilter? = null

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

    override suspend fun movies(filter: MovieFilter, page: Int, pageSize: Int): MoviePage {
        val raw = requireBase()
        val base = baseUrl(raw)
        // 分类名 → type_id。取不到就当作"全部"（不分类），比报错友好
        val typeId = if (filter.category == CATEGORY_ALL) null else resolveTypeId(raw, filter.category, base)

        // 筛选条件换了 → 重新判断要不要放宽（放宽状态要沿用到翻页，不然第 2 页会突然变空）
        if (lastFilter != filter) {
            lastFilter = filter
            relaxed = false
        }

        // ① 先让服务器筛（最省流量、最准确）
        if (!relaxed) {
            val strict = api.list(
                base = base,
                typeId = typeId,
                page = page,
                lang = filter.lang,
                area = filter.area,
                year = filter.year,
            )
            // 有结果 / 本来就没提要求 / 翻页中 → 就是它了
            if (strict.items.isNotEmpty() || filter.noServerFilter || page > 1) {
                return strict.toPage(base)
            }
            // ② 第一页就是 0 条：多半是这站不认这几个参数 → 进入放宽模式
            relaxed = true
        }

        // ③ 放宽：只按分类取一页，再在本地按接口自己的字段筛
        val loose = api.list(base = base, typeId = typeId, page = page)
        val matched = loose.items.filter { it.matches(filter) }
        return when {
            matched.isNotEmpty() -> loose.toPage(base, matched)
            // 本地也筛不出来（这站根本没标语言/地区）→ 宁可给全部，也不给你一片空白
            page == 1 -> loose.toPage(base).copy(relaxed = true)
            else -> loose.toPage(base, emptyList())   // 翻到底了，正常收尾
        }
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
        usableBase() ?: throw SourceException("还没有配置影视接口地址：到「设置」里点「扫码配置」，用手机扫码填一下")

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
        lang = lang.orEmpty(),
        area = area.orEmpty(),
    )

    private fun RemotePage.toPage(base: HttpUrl, items: List<RemoteVod> = this.items): MoviePage =
        MoviePage(
            movies = items.map { it.toMovie(base) },
            total = total,
        )

    // ── 本地兜底筛选（站不支持接口筛选时用）──────────────────────
    // 原则：字段是空的**不算命中**（宁可少筛，也不要让没标语言的片混进来）

    private fun RemoteVod.matches(f: MovieFilter): Boolean {
        if (f.lang.isNotBlank() && !matchesLang(f.lang)) return false
        if (f.area.isNotBlank() && !matchesArea(f.area)) return false
        if (f.year.isNotBlank() && year?.take(4) != f.year) return false
        return true
    }

    /**
     * "国语"要能命中 普通话/中文/汉语/华语 —— 各站标签写法不统一。
     * 反过来也一样：别的语言按字面包含判断即可。
     */
    private fun RemoteVod.matchesLang(want: String): Boolean {
        val actual = lang?.trim().orEmpty()
        if (actual.isEmpty()) return false
        if (actual.contains(want)) return true
        return want == "国语" && listOf("普通话", "中文", "汉语", "华语").any { actual.contains(it) }
    }

    /** "大陆"要能命中 内地/中国大陆（同样是各家写法不统一） */
    private fun RemoteVod.matchesArea(want: String): Boolean {
        val actual = area?.trim().orEmpty()
        if (actual.isEmpty()) return false
        if (actual.contains(want)) return true
        return want == "大陆" && listOf("内地", "中国").any { actual.contains(it) }
    }

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
