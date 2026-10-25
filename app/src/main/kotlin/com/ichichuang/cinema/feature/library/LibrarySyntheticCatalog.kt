package com.ichichuang.cinema.feature.library

import com.ichichuang.cinema.data.catalog.CandidateQueryStore
import com.ichichuang.cinema.data.catalog.CandidateQueryTransport
import com.ichichuang.cinema.data.catalog.CommittedQueryState
import com.ichichuang.cinema.data.catalog.MaccmsCatalogNormalizer
import com.ichichuang.cinema.data.catalog.NormalizedCatalogCapture
import com.ichichuang.cinema.data.catalog.QueryBudget
import com.ichichuang.cinema.data.catalog.QueryCatalogEntry
import com.ichichuang.cinema.data.catalog.QueryStateLimitException
import com.ichichuang.cinema.data.catalog.SourceItemKey
import com.ichichuang.cinema.data.catalog.SourceCursorState
import com.ichichuang.cinema.data.catalog.StoredQuerySession
import com.ichichuang.cinema.data.catalog.StrictCaptureFacts
import com.ichichuang.cinema.data.catalog.TaxonomyRuleMapper
import com.ichichuang.cinema.data.catalog.scalar
import com.ichichuang.cinema.domain.model.catalog.CatalogQuery
import com.ichichuang.cinema.domain.model.catalog.ContentKind
import com.ichichuang.cinema.domain.model.catalog.FactIds
import com.ichichuang.cinema.domain.model.catalog.FieldEvidence
import com.ichichuang.cinema.domain.model.catalog.SourceId
import com.ichichuang.cinema.domain.model.catalog.SourceItemId
import com.ichichuang.cinema.domain.model.catalog.WorkId
import com.ichichuang.cinema.feature.browse.BrowseProbeFixture
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException

/**
 * R14 片库合成查询管线：把既有 R11 私有 fixture 作品按页渲染成 AppleCMS 原始行，
 * 经真实 `MaccmsCatalogNormalizer` 归一、`StrictCaptureFacts` 事实化，交给既有
 * R09 `QueryCoordinator` 预算分页与 R10 严格评估。仅存在于授权测试路径：
 * 无网络、无第二 Room 写入者、无新的查询/游标权威；服务端不过滤，筛选只由本地严格判定。
 */
internal class LibraryFixtureTransport(
    private val works: List<BrowseProbeFixture.Work>,
    private val plan: BrowseProbeFixture.LibraryPlan = BrowseProbeFixture.LibraryPlan(),
) : CandidateQueryTransport {
    /** 供测试观察的合成页请求轨迹；不属于查询状态。 */
    val requestedPages = mutableListOf<Pair<String, Int>>()

    override suspend fun fetch(source: SourceId, cursor: String, query: CatalogQuery, maxBytes: Int): ByteArray {
        if (source.value in plan.failingSources) throw IOException("来源响应失败")
        val page = cursor.toIntOrNull()?.takeIf { it > 0 } ?: throw IOException("来源游标无效")
        val mirrorOrigin = plan.mirrorSources[source.value]
        val scoped = if (mirrorOrigin != null) works.filter { it.source.value == mirrorOrigin }
        else works.filter { it.source == source }
        requestedPages += source.value to page
        // 空来源以显式耗尽响应：空列表本身不证明其它来源，也不推导全库结论。
        if (scoped.isEmpty()) return applePage(emptyList(), next = null, exhausted = true)
        val from = (page - 1) * plan.pageSize
        if (from >= scoped.size) throw IOException("来源游标越界")
        val slice = scoped.drop(from).take(plan.pageSize)
        val last = from + slice.size >= scoped.size
        val rows = slice.map { work ->
            val itemId = if (mirrorOrigin != null) "mirror-${work.id.value}" else "row-${work.id.value}"
            appleRow(itemId, work.id.value, work.title, work.facts)
        }
        return applePage(rows, next = if (last) null else (page + 1).toString(), exhausted = last)
    }
}

/** AppleCMS 风格分页响应；exhausted 只在来源显式声明最后一页时为 true。 */
private fun applePage(rows: List<JsonObject>, next: String?, exhausted: Boolean): ByteArray =
    buildJsonObject {
        put("list", kotlinx.serialization.json.JsonArray(rows))
        next?.let { put("next", it) }
        put("exhausted", exhausted)
    }.toString().toByteArray()

/** 单条合成来源行：缺失事实保持字段缺失；配音/字幕字段与作品语言分离。 */
private fun appleRow(itemId: String, workRef: String, title: String, facts: com.ichichuang.cinema.domain.filter.StrictWorkFacts): JsonObject =
    buildJsonObject {
        put("vod_id", itemId)
        put("vod_name", title)
        put("work_ref", workRef)
        (facts.year as? FieldEvidence.SourceReported)?.value?.let { put("vod_year", it.toString()) }
            ?: (facts.year as? FieldEvidence.Confirmed)?.value?.let { put("vod_year", it.toString()) }
        factIds(facts.countries)?.let { put("vod_area", it.joinToString("、") { id -> areaLabel(id) }) }
        factIds(facts.genres)?.let { put("vod_class", it.joinToString("、") { id -> classLabel(id) }) }
        factIds(facts.originalLanguages)?.let { put("work_original_language", it.joinToString("、") { id -> languageLabel(id) }) }
        (facts.contentKind as? FieldEvidence.SourceReported)?.value?.let { put("work_content_kind", it.name.lowercase()) }
            ?: (facts.contentKind as? FieldEvidence.Confirmed)?.value?.let { put("work_content_kind", it.name.lowercase()) }
        if (facts.completeOriginalLanguages is FieldEvidence.SourceReported || facts.completeOriginalLanguages is FieldEvidence.Confirmed)
            put("work_original_language_scope", "complete")
        // 配音与字幕只描述播放版本，绝不参与作品语言或国家判定。
        put("vod_lang", "国语配音")
        put("subtitle_language", "中文字幕")
    }

private fun factIds(evidence: FieldEvidence<FactIds>): Set<String>? = when (evidence) {
    is FieldEvidence.SourceReported -> evidence.value.values
    is FieldEvidence.Confirmed -> evidence.value.values
    else -> null
}

private val areaLabels = TaxonomyRuleMapper.countries.entries.associate { (label, id) -> id to label } +
    mapOf("TH" to "泰国")
private val classLabels = TaxonomyRuleMapper.genres.entries.associate { (label, id) -> id to label } +
    mapOf("crime" to "犯罪")
private val languageLabels = TaxonomyRuleMapper.languages.entries.associate { (label, id) -> id to label }
private fun areaLabel(id: String) = areaLabels[id] ?: id
private fun classLabel(id: String) = classLabels[id] ?: id
private fun languageLabel(id: String) = languageLabels[id] ?: id

/**
 * 内存候选存储：会话与来源证据仅在授权租约内存在，进程重启后重新核对（确定性合成数据）。
 * 持久化的用户/浏览权威仍是既有 Room v6 browse_snapshots 与个人记录表。
 */
internal class LibraryCandidateStore : CandidateQueryStore {
    private val sessions = LinkedHashMap<String, StoredQuerySession>()
    private val captures = LinkedHashMap<SourceItemKey, NormalizedCatalogCapture>()

    override suspend fun load(queryId: String): StoredQuerySession? = sessions[queryId]

    override suspend fun entries(keys: List<SourceItemKey>): List<QueryCatalogEntry> = keys.mapNotNull { key ->
        val capture = captures[key] ?: return@mapNotNull null
        val workRef = capture.raw["work_ref"].scalar() ?: throw IOException("合成行缺少作品引用")
        val title = capture.raw["vod_name"].scalar() ?: workRef
        val workId = WorkId(workRef)
        QueryCatalogEntry(key, workId, workId, title, StrictCaptureFacts.fromCaptures(listOf(capture)), emptyList())
    }

    override suspend fun commit(
        session: StoredQuerySession,
        newCaptures: List<NormalizedCatalogCapture>,
        budget: QueryBudget,
        guard: () -> Unit,
    ): CommittedQueryState {
        guard()
        if (session.query.queryId !in sessions && sessions.size >= budget.maxSessions)
            throw QueryStateLimitException("候选查询会话超出上限")
        newCaptures.forEach { capture ->
            val itemId = capture.itemId ?: return@forEach
            captures[SourceItemKey(capture.sourceId, itemId)] = capture
        }
        sessions[session.query.queryId] = session
        guard()
        return CommittedQueryState(session, entries(session.evidenceKeys))
    }
}

/** R14 片库默认查询：启用 fixture 中出现的全部来源（含失败/镜像来源，如实进入覆盖状态）。 */
internal fun libraryDefaultQuery(works: List<BrowseProbeFixture.Work>,
    plan: BrowseProbeFixture.LibraryPlan = BrowseProbeFixture.LibraryPlan(), queryId: String): CatalogQuery {
    val sources = works.map { it.source.value }.toSet() + plan.failingSources + plan.mirrorSources.keys
    return CatalogQuery.create(queryId, null, com.ichichuang.cinema.domain.model.catalog.LanguageScope.ALL,
        emptySet(), emptySet(), null, null, com.ichichuang.cinema.domain.model.catalog.CatalogSort.DEFAULT,
        sources.map(::SourceId).toSet())
}

/** 已配置来源是否有任何合成内容或显式失败来源；全部为空时为未配置真话状态。 */
internal fun librarySourcesConfigured(works: List<BrowseProbeFixture.Work>,
    plan: BrowseProbeFixture.LibraryPlan, query: CatalogQuery): Boolean =
    query.enabledSourceIds.any { it.value in plan.failingSources || it.value in plan.mirrorSources.keys } ||
        works.any { it.source in query.enabledSourceIds }
