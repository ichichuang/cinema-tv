package com.ichichuang.cinema.data

import com.ichichuang.cinema.data.remote.MaccmsApi
import com.ichichuang.cinema.data.remote.RemotePage
import com.ichichuang.cinema.data.remote.RemoteVod
import com.ichichuang.cinema.data.remote.SourceException
import com.ichichuang.cinema.data.remote.normalizeBaseUrl
import com.ichichuang.cinema.data.remote.stripHtml
import com.ichichuang.cinema.domain.model.Episode
import com.ichichuang.cinema.domain.model.Movie
import com.ichichuang.cinema.domain.model.MovieDetail
import com.ichichuang.cinema.domain.model.MovieFilter
import com.ichichuang.cinema.domain.model.MoviePage
import com.ichichuang.cinema.domain.model.PlayLine
import com.ichichuang.cinema.domain.repository.CATEGORY_ALL
import okhttp3.HttpUrl

/**
 * **单个**苹果CMS 片源的客户端：一个地址一个实例。
 *
 * 从 MaccmsMovieRepository 里拆出来，就是为了支持"同时启用多个片源"：
 * 分类缓存、严格/放宽状态这些**每站一份**的东西跟着实例走，互不串味。
 *
 * 这里也是"筛选为什么没效果"的修复点：有的站会把不认识的筛选参数
 * 当空气（原样返回全部）—— 第 1 页会抽查一下，抽到不合格的就地改本地筛。
 */
class MaccmsSource(
    private val api: MaccmsApi,
    /** 用户看到/保存的原始地址（也是 Movie.origin 的取值） */
    val origin: String,
) {
    private val base: HttpUrl = normalizeBaseUrl(origin)
        ?: throw SourceException("接口地址格式不对：$origin")

    // 分类树基本不变：缓存住（避免每次切页都打接口）
    private var cachedNames: List<String> = emptyList()
    private var cachedIds: Map<String, String> = emptyMap()

    /** 当前这批结果是否处于"放宽"状态（服务器端筛不出、退到本地或放开） */
    private var relaxed = false
    private var lastFilter: MovieFilter? = null

    suspend fun categories(): List<String> {
        if (cachedNames.isNotEmpty()) return cachedNames
        refreshCategories()
        return cachedNames
    }

    suspend fun movies(filter: MovieFilter, page: Int, pageSize: Int): MoviePage {
        val typeId = if (filter.category == CATEGORY_ALL) null else resolveTypeId(filter.category)

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
                movieClass = filter.movieClass,
            )
            // 有结果 / 本来就没提要求 / 翻页中 → 就是它了
            if (strict.items.isNotEmpty() || filter.noServerFilter || page > 1) {
                if (page == 1 && !filter.noServerFilter && strict.items.isNotEmpty()) {
                    // ★ 抽查：有的站把参数当空气，返回的还是全量（"筛选了却没有任何效果"）。
                    //   抽到明确不合格的，这一页就地改本地筛，后面的页也走本地筛。
                    if (strict.items.any { it.misfits(filter) }) {
                        relaxed = true
                        val good = strict.items.filter { it.matches(filter) }
                        // 本地筛过的结果要如实标记"已放宽"，界面才会给用户交代
                        if (good.isNotEmpty()) return strict.toPage(good).copy(relaxed = true)
                        // 本地一个都对不上 → 落到下面"放宽"逻辑（如实给内容 + 人话说明）
                    } else {
                        return strict.toPage()
                    }
                } else {
                    return strict.toPage()
                }
            } else {
                // ② 第一页就是 0 条：多半是这站不认这几个参数 → 进入放宽模式
                relaxed = true
            }
        }

        // ③ 放宽：只按分类取一页，再在本地按接口自己的字段筛
        val loose = api.list(base = base, typeId = typeId, page = page)
        val matched = loose.items.filter { it.matches(filter) }
        return when {
            matched.isNotEmpty() -> loose.toPage(matched).copy(relaxed = true)
            // 本地也筛不出来（这站根本没标语言/地区）→ 宁可给全部，也不给你一片空白
            page == 1 -> loose.toPage().copy(relaxed = true)
            else -> loose.toPage(emptyList())   // 翻到底了，正常收尾
        }
    }

    suspend fun detail(id: String): MovieDetail? {
        val vod = api.detail(base, id) ?: return null
        return MovieDetail(
            movie = vod.toMovie(),
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

    private suspend fun refreshCategories() {
        val remote = api.categories(base)
        cachedNames = listOf(CATEGORY_ALL) + remote.map { it.name }
        cachedIds = remote.associate { it.name to it.typeId }
    }

    private suspend fun resolveTypeId(category: String): String? {
        if (cachedNames.isEmpty()) refreshCategories()
        cachedIds[category]?.let { return it }
        // 分类缓存对不上（刚换过站/清单更新）→ 再取一次
        refreshCategories()
        return cachedIds[category]
    }

    private fun RemoteVod.toMovie(): Movie = Movie(
        id = id,
        title = name,
        year = year?.take(4)?.toIntOrNull() ?: 0,
        score = score?.toDoubleOrNull() ?: 0.0,
        genre = typeName.orEmpty(),
        minutes = 0,      // 列表接口不提供时长（详情里也没有，字段留 0，UI 会隐藏）
        synopsis = "",    // 简介走 detail() 异步取，列表页不为此多等一次请求
        posterUrl = pic?.let { absolutize(it) },
        remarks = remarks,
        lang = lang.orEmpty(),
        area = area.orEmpty(),
        origin = origin,
    )

    private fun RemotePage.toPage(items: List<RemoteVod> = this.items): MoviePage =
        // distinctBy：同一页出现重复 id 时兜一下底，绝不让列表 key 撞车（那是会闪退的）
        MoviePage(movies = items.map { it.toMovie() }.distinctBy { it.uniqueKey }, total = total)

    // ── 本地兜底筛选（站不支持接口筛选时用）──────────────────────
    // 原则：字段是空的**不算命中**（宁可少筛，也不要让没标语言的片混进来）

    private fun RemoteVod.matches(f: MovieFilter): Boolean {
        if (f.lang.isNotBlank() && !matchesLang(f.lang)) return false
        if (f.area.isNotBlank() && !matchesArea(f.area)) return false
        if (f.year.isNotBlank() && year?.take(4) != f.year) return false
        if (f.movieClass.isNotBlank() && !matchesClass(f.movieClass)) return false
        return true
    }

    /**
     * 轻量抽查：**字段有值却明显不符**才算"服务器没筛"。
     * 字段缺失（本站不提供这个标注）不算 —— 不然会把"筛得好好的站"误判成没筛。
     */
    private fun RemoteVod.misfits(f: MovieFilter): Boolean {
        if (f.lang.isNotBlank() && !lang.isNullOrBlank() && !matchesLang(f.lang)) return true
        if (f.area.isNotBlank() && !area.isNullOrBlank() && !matchesArea(f.area)) return true
        if (f.year.isNotBlank() && !year.isNullOrBlank() && year.take(4) != f.year) return true
        if (f.movieClass.isNotBlank() && !classes.isNullOrBlank() && !matchesClass(f.movieClass)) return true
        return false
    }

    /** "喜剧"要能命中 "喜剧片"/"喜剧 爱情" 这类写法 */
    private fun RemoteVod.matchesClass(want: String): Boolean {
        val actual = classes?.trim().orEmpty()
        return actual.isNotEmpty() && actual.contains(want)
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
    private fun absolutize(url: String): String {
        val u = url.trim()
        return when {
            u.startsWith("http://", true) || u.startsWith("https://", true) -> u
            u.startsWith("//") -> "${base.scheme}:$u"
            u.startsWith("/") -> "${base.scheme}://${base.host}$u"
            else -> "${base.scheme}://${base.host}/$u"
        }
    }
}
