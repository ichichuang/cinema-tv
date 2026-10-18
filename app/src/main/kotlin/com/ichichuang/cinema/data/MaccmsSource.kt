package com.ichichuang.cinema.data

import com.ichichuang.cinema.data.remote.MaccmsApi
import com.ichichuang.cinema.data.remote.RemoteCategory
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl

/**
 * **单个**苹果CMS 片源的客户端：一个地址一个实例。
 *
 * 从 MaccmsMovieRepository 里拆出来，就是为了支持"同时启用多个片源"：
 * 分类树缓存、分页状态这些**每站一份**的东西跟着实例走，互不串味。
 *
 * ## 筛选怎么做的（对 25 个真实站在线实测过）
 * - 服务器唯一靠谱的筛片参数是 `t=`，而且**只认一个 id**：
 *   `t=6,7` 在实测的站上等价于 `t=6`（逗号多值一律被忽略）；
 *   `t=<顶层分类>` 也不返回下级 —— 各站只按叶子 id 精确匹配。
 * - 所以：
 *   - 选中**一个**类型/叶子（如 动作片）→ 直接 `t=id` 分页请求（最快最准）；
 *   - 选中一个**大类**（如 电影片，下辖 N 个题材）→ 拿它全部叶子做"轮转拉取"：
 *     每次并发拉 4 个叶子各自的页，攒个缓冲池，一页一页交付（内容真实、无漏无重）；
 *   - 平铺站（老模板）只有一层名字，按名字直接当叶子请求。
 * - 名字在**本站自己的分类树**里解析真实 type_id；解析不到 → 诚实返回空页并说明，
 *   **绝不**回退成全量或别的分类。
 * - 语言/地区/年份这类"标注"：服务器不认且站里没标注时，才走"放宽 + 人话说明"的老路；
 *   分类/类型永不放宽。
 */
class MaccmsSource(
    private val api: MaccmsApi,
    /** 用户看到/保存的原始地址（也是 Movie.origin 的取值） */
    val origin: String,
) {
    private val base: HttpUrl = normalizeBaseUrl(origin)
        ?: throw SourceException("接口地址格式不对：$origin")

    // 分类树基本不变：缓存住（避免每次切页都打接口）
    private var treeCache: List<RemoteCategory> = emptyList()

    /** 当前这批结果是否处于"放宽"状态（服务器端筛不出、退到本地核对/放开） */
    private var relaxed = false
    private var lastFilter: MovieFilter? = null

    /** 多叶子轮转的"缓冲池"状态（按站保存，翻页沿用它） */
    private var walk: Walk? = null

    /** 这个站的全量分类表（顶层 + 下级），名字全是接口原文 */
    suspend fun tree(): List<RemoteCategory> {
        if (treeCache.isEmpty()) treeCache = api.categories(base)
        return treeCache
    }

    suspend fun movies(filter: MovieFilter, page: Int, pageSize: Int): MoviePage {
        // 用户要的分类/类型 → 本站真实的 type_id；对不上就诚实跳过这个源
        val plan = buildPlan(filter)
        if (plan != null && plan.missing) {
            return MoviePage(
                movies = emptyList(),
                total = 0,
                skippedNoMatch = true,
                note = "这个片源没有「" + askedText(filter) + "」",
            )
        }

        // 筛选条件换了 → 分页状态全部从头来（放宽标记也一样）
        if (lastFilter != filter) {
            lastFilter = filter
            relaxed = false
            walk = null
        }
        // 每次从第 1 页重新取（用户重试/重载）→ 缓冲池也要重置，不然会重发旧内容
        if (page == 1) walk = null

        if (plan == null) return plain(filter, page)
        if (plan.leafIds.isEmpty()) {
            // 兜底（正常到不了这：解析不到任何叶子时 missing 已处理）
            return MoviePage(
                movies = emptyList(),
                total = 0,
                skippedNoMatch = true,
                note = "这个片源没有「" + askedText(filter) + "」",
            )
        }
        return if (plan.leafIds.size == 1) direct(filter, plan, page) else chunked(filter, plan, page, pageSize)
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

    // ── 分类/类型 → 本站真实 type_id ────────────────────────────

    /**
     * 一次筛选在**本站**的取片计划：
     * - [leafIds]：可直接交给服务器 `t=` 的叶子 id（大类的全部下级题材 / 选中的题材本身）
     * - [ids] / [names]：本地核对"是不是用户要的分类/类型"用的口径（含父与全部后代）
     * - [missing]：用户点名了分类/类型，但本站一个都对不上
     */
    private class Plan(
        val leafIds: List<String>,
        val ids: Set<String>,
        val names: Set<String>,
        val missing: Boolean,
    )

    private suspend fun buildPlan(filter: MovieFilter): Plan? {
        val wantedTypes = filter.types.map { it.trim() }.filter { it.isNotEmpty() }
        val wantedCat = filter.category.trim().takeIf { it.isNotEmpty() && it != CATEGORY_ALL }
        if (wantedTypes.isEmpty() && wantedCat == null) return null

        val all = tree()
        val ids = LinkedHashSet<String>()
        val names = LinkedHashSet<String>()
        val leaves = LinkedHashSet<String>()

        // 把一个分类节点吸收进计划：ids/names 收它和全部后代；叶子 = 没有下级的那些
        fun absorb(root: RemoteCategory) {
            ids += root.typeId
            names += root.name
            val descendants = ArrayList<RemoteCategory>()
            fun walkDown(parentId: String, depth: Int) {
                if (depth > 3) return
                all.filter { it.pid == parentId }.forEach { child ->
                    if (descendants.none { it.typeId == child.typeId }) {
                        descendants += child
                        walkDown(child.typeId, depth + 1)
                    }
                }
            }
            walkDown(root.typeId, 1)
            descendants.forEach {
                ids += it.typeId
                names += it.name
            }
            val leafNodes = descendants.filter { d -> all.none { it.pid == d.typeId } }
            val effective = if (leafNodes.isEmpty()) listOf(root) else leafNodes
            effective.forEach { leaves += it.typeId }
        }

        if (wantedTypes.isNotEmpty()) {
            // 类型多选：每个名字都在本站树里找真身（子类通常直接挂在顶层分类下）
            wantedTypes.forEach { name -> all.filter { it.name == name }.forEach { absorb(it) } }
        } else if (wantedCat != null) {
            // 顶层优先；平铺站没有层级，全表里找同名就行
            val hit = all.firstOrNull { it.isTop() && it.name == wantedCat }
                ?: all.firstOrNull { it.name == wantedCat }
            if (hit != null) absorb(hit)
        }

        return Plan(
            leafIds = leaves.toList(),
            ids = ids,
            names = names,
            missing = ids.isEmpty(),
        )
    }

    private fun RemoteCategory.isTop(): Boolean =
        pid == null || pid.isEmpty() || pid == "0" || pid == typeId

    private fun askedText(filter: MovieFilter): String {
        val types = filter.types.filter { it.isNotBlank() }
        return if (types.isNotEmpty()) types.joinToString("、") else filter.category
    }

    // ── 三种取数模式 ───────────────────────────────────────────

    /** ① 没点分类/类型：老逻辑（语言/地区/年份 服务器筛 + 抽查 + 放宽兜底） */
    private suspend fun plain(filter: MovieFilter, page: Int): MoviePage {
        val serverAsk = !filter.noServerFilter

        if (!relaxed) {
            val strict = api.list(
                base = base,
                typeIds = null,
                page = page,
                lang = filter.lang,
                area = filter.area,
                year = filter.year,
            )
            if (strict.items.isNotEmpty() || !serverAsk || page > 1) {
                if (page == 1 && serverAsk && strict.items.isNotEmpty()) {
                    // ★ 抽查：有的站把参数当空气，返回的还是全量（"筛选了却没有任何效果"）
                    if (strict.items.any { it.misfits(filter, null) }) {
                        relaxed = true
                        val good = strict.items.filter { it.matches(filter, null) }
                        if (good.isNotEmpty()) return strict.toPage(good).copy(relaxed = true)
                        // 本地一个都对不上 → 落到下面"放宽"逻辑（如实说明，不乱给）
                    } else {
                        return strict.toPage()
                    }
                } else {
                    return strict.toPage()
                }
            } else {
                // 第一页就是 0 条：多半是这站不认这几个参数 → 进入放宽模式
                relaxed = true
            }
        }

        // 放宽：语言/地区/年份站里没标注 → 宁可给内容 + 人话说明（沿用老规矩）
        val loose = api.list(base = base, typeIds = null, page = page)
        val matched = loose.items.filter { it.matches(filter, null) }
        return when {
            matched.isNotEmpty() -> loose.toPage(matched).copy(relaxed = true)
            page == 1 -> loose.toPage().copy(relaxed = true)
            else -> loose.toPage(emptyList())   // 翻到底了，正常收尾
        }
    }

    /** ② 单个叶子（下辖题材 = 0）：直接 t=id 分页，最准最快 */
    private suspend fun direct(filter: MovieFilter, plan: Plan, page: Int): MoviePage {
        val leafId = plan.leafIds.first()

        if (!relaxed) {
            val strict = api.list(
                base = base,
                typeIds = listOf(leafId),
                page = page,
                lang = filter.lang,
                area = filter.area,
                year = filter.year,
            )
            if (page > 1 || strict.items.isEmpty()) {
                // 第一页就 0 条：这个叶子在本站就是没货（单 id 过滤站站有效，无需再猜）
                return if (strict.items.isEmpty() && page == 1) {
                    relaxed = true
                    strict.toPage(emptyList()).copy(
                        skippedNoMatch = true,
                        note = EMPTY_NOTE,
                    )
                } else {
                    strict.toPage()
                }
            }
            // ★ 抽查（第一页、有结果）：站把参数当空气时会返回不相关的条目
            if (strict.items.none { it.misfits(filter, plan) }) return strict.toPage()
            relaxed = true
            val good = strict.items.filter { it.matches(filter, plan) }
            return if (good.isNotEmpty()) {
                strict.toPage(good).copy(relaxed = true)
            } else {
                strict.toPage(emptyList()).copy(skippedNoMatch = true, note = EMPTY_NOTE)
            }
        }

        // 站不认筛选参数（抽查发现的）→ 本地核对，分类/类型核不上就空，绝不放全量
        val r = api.list(base = base, typeIds = listOf(leafId), page = page)
        val good = r.items.filter { it.matches(filter, plan) }
        return when {
            good.isNotEmpty() -> r.toPage(good).copy(relaxed = true)
            page == 1 -> r.toPage(emptyList()).copy(skippedNoMatch = true, note = EMPTY_NOTE)
            else -> r.toPage(emptyList())
        }
    }

    /**
     * ③ 多个叶子（大类下的题材们 / 多选类型）：轮转拉取 + 缓冲池交付。
     *
     * 每轮并发拉 [WALK_BATCH] 个叶子各自的下一页；谁还有下一页就回到队尾。
     * 拉到的条目先本地核对（type_id 对不上 = 站不认 t=，直接丢弃），再进缓冲池；
     * 每次交付一页，池里不够就续拉。所有叶子翻完 = 到头。
     */
    private suspend fun chunked(filter: MovieFilter, plan: Plan, page: Int, pageSize: Int): MoviePage {
        val w = walk ?: Walk(
            queue = ArrayDeque(plan.leafIds.map { Fetch(it, 1) }),
            buffer = mutableListOf(),
            delivered = HashSet(),
            finished = false,
        ).also { walk = it }

        var rounds = 0
        while (!w.finished && w.buffer.size < pageSize && rounds < MAX_REFILL_ROUNDS) {
            rounds++
            val batch = ArrayList<Fetch>(WALK_BATCH)
            while (batch.size < WALK_BATCH && w.queue.isNotEmpty()) batch += w.queue.removeFirst()
            if (batch.isEmpty()) {
                w.finished = true
                break
            }
            val results = coroutineScope {
                batch.map { f ->
                    async {
                        f to runCatching {
                            api.list(
                                base = base,
                                typeIds = listOf(f.leafId),
                                page = f.page,
                                lang = filter.lang,
                                area = filter.area,
                                year = filter.year,
                            )
                        }
                    }
                }.awaitAll()
            }
            for ((f, res) in results) {
                val data = res.getOrNull()
                if (data == null) {
                    // 单个叶子这一轮没拉动：给它一次补考机会，再拿不动就放弃（不拖累整页）
                    if (!f.tried) w.queue += Fetch(f.leafId, f.page, tried = true)
                    continue
                }
                data.items.forEach { vod ->
                    if (vod.walkHit(f.leafId) && vod.walkMetaOk(filter) && w.delivered.add(vod.id)) {
                        w.buffer += vod
                    }
                }
                if (data.items.isNotEmpty()) w.queue += Fetch(f.leafId, f.page + 1)
            }
        }

        val take = minOf(pageSize, w.buffer.size)
        val picked = w.buffer.subList(0, take).toList()
        w.buffer.subList(0, take).clear()
        val movies = picked.map { it.toMovie() }.distinctBy { it.uniqueKey }
        return if (movies.isEmpty() && page == 1) {
            MoviePage(movies = emptyList(), total = 0, skippedNoMatch = true, note = EMPTY_NOTE)
        } else {
            MoviePage(movies = movies, total = 0)
        }
    }

    /** 轮转拉取的状态：待拉队列（叶子+页码）、缓冲池、已交付去重 */
    private class Walk(
        val queue: ArrayDeque<Fetch>,
        val buffer: MutableList<RemoteVod>,
        val delivered: HashSet<String>,
        var finished: Boolean,
    )

    private class Fetch(val leafId: String, val page: Int, val tried: Boolean = false)

    // ── 内部 ───────────────────────────────────────────────────

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

    // ── 本地核对 ────────────────────────────────────────────────

    private fun RemoteVod.matches(f: MovieFilter, plan: Plan?): Boolean {
        if (plan != null && !typeHit(plan)) return false
        if (f.lang.isNotBlank() && !matchesLang(f.lang)) return false
        if (f.area.isNotBlank() && !matchesArea(f.area)) return false
        if (f.year.isNotBlank() && year?.take(4) != f.year) return false
        return true
    }

    /**
     * 本地核对"是不是用户要的分类/类型"：
     * 优先按 type_id；只有名字的站按 type_name；两个字段都没有 = 核不了 = 不命中
     * （宁可空，也不让别的分类混进来 —— 这就是用户说的"禁止任何假的"）。
     */
    private fun RemoteVod.typeHit(plan: Plan): Boolean {
        typeId?.let { return it in plan.ids }
        typeName?.let { return it in plan.names }
        return false
    }

    /**
     * 轻量抽查：**字段有值却明显不符**才算"服务器没筛"。
     * 字段缺失（本站不提供这个标注）不算 —— 不然会把"筛得好好的站"误判成没筛。
     */
    private fun RemoteVod.misfits(f: MovieFilter, plan: Plan?): Boolean {
        if (plan != null && (typeId != null || typeName != null) && !typeHit(plan)) return true
        if (f.lang.isNotBlank() && !lang.isNullOrBlank() && !matchesLang(f.lang)) return true
        if (f.area.isNotBlank() && !area.isNullOrBlank() && !matchesArea(f.area)) return true
        if (f.year.isNotBlank() && !year.isNullOrBlank() && year.take(4) != f.year) return true
        return false
    }

    /** 轮转模式：条目必须属于请求的那个叶子（type_id 有值就必须相等；没值的站只能信服务器） */
    private fun RemoteVod.walkHit(leafId: String): Boolean {
        typeId?.let { return it == leafId }
        return true
    }

    /** 轮转模式的语言/地区/年份核对：站里没标的（字段空）不算不合格 */
    private fun RemoteVod.walkMetaOk(f: MovieFilter): Boolean {
        if (f.lang.isNotBlank() && !lang.isNullOrBlank() && !matchesLang(f.lang)) return false
        if (f.area.isNotBlank() && !area.isNullOrBlank() && !matchesArea(f.area)) return false
        if (f.year.isNotBlank() && !year.isNullOrBlank() && year.take(4) != f.year) return false
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
    private fun absolutize(url: String): String {
        val u = url.trim()
        return when {
            u.startsWith("http://", true) || u.startsWith("https://", true) -> u
            u.startsWith("//") -> "${base.scheme}:$u"
            u.startsWith("/") -> "${base.scheme}://${base.host}$u"
            else -> "${base.scheme}://${base.host}/$u"
        }
    }

    private companion object {
        /** 一轮并发拉几个叶子页 */
        const val WALK_BATCH = 4

        /** 一次调用最多续拉几轮（防呆，避免极端情况打太多请求） */
        const val MAX_REFILL_ROUNDS = 8

        const val EMPTY_NOTE = "按当前筛选，这个片源没找到结果"
    }
}
