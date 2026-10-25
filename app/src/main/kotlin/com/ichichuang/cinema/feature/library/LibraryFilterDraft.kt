package com.ichichuang.cinema.feature.library

import com.ichichuang.cinema.domain.filter.YearSelection
import com.ichichuang.cinema.domain.model.catalog.CatalogQuery
import com.ichichuang.cinema.domain.model.catalog.CatalogSort
import com.ichichuang.cinema.domain.model.catalog.ContentKind
import com.ichichuang.cinema.domain.model.catalog.LanguageScope
import com.ichichuang.cinema.domain.model.catalog.SourceId

/** 年份入口的四种编辑意图；NONE 表示清除年份上下界。 */
internal enum class LibraryYearMode { NONE, EXACT, DECADE, RANGE }

/**
 * 筛选草稿：完整复制已应用 `CatalogQuery` 的每个字段后独立编辑；
 * 确认时仅在真实变化下生成新 queryId 的完整查询，取消即整体丢弃。
 * 草稿不直接执行放宽、不写持久化、不触碰已应用查询与结果。
 */
internal data class LibraryFilterDraft(
    val base: CatalogQuery,
    val contentKind: ContentKind?,
    val languageScope: LanguageScope,
    val countryIds: Set<String>,
    val genreIds: Set<String>,
    val yearMode: LibraryYearMode,
    val exactYear: Int,
    val decadeStart: Int,
    val rangeStart: Int,
    val rangeEnd: Int,
    val sort: CatalogSort,
    val enabledSourceIds: Set<String>,
) {
    /** 无效草稿给出中文可读原因；有效返回 null。应用层据此拒绝确认且不改动已应用查询。 */
    fun yearError(): String? = when (yearMode) {
        LibraryYearMode.NONE -> null
        LibraryYearMode.EXACT -> if (exactYear <= 0) "具体年份需为正数" else null
        LibraryYearMode.DECADE -> when {
            decadeStart <= 0 -> "年代起点需为正数"
            decadeStart % 10 != 0 -> "年代起点需为整十年，如 1980"
            decadeStart > Int.MAX_VALUE - 9 -> "年代范围超出可表示范围"
            else -> null
        }
        LibraryYearMode.RANGE -> when {
            rangeStart <= 0 || rangeEnd <= 0 -> "起止年份需为正数"
            rangeStart > rangeEnd -> "起始年份不能晚于结束年份"
            else -> null
        }
    }

    /** NONE 返回 null 表示清除年份上下界；无效草稿由调用方先以 [yearError] 拒绝。 */
    fun yearSelection(): YearSelection? = when (yearMode) {
        LibraryYearMode.NONE -> null
        LibraryYearMode.EXACT -> YearSelection.Exact(exactYear)
        LibraryYearMode.DECADE -> YearSelection.Decade(decadeStart)
        LibraryYearMode.RANGE -> YearSelection.Range(rangeStart, rangeEnd)
    }

    /** 与编辑起点相比是否存在真实变化；无变化确认不得生成新查询身份。 */
    fun changedFromBase(): Boolean = yearSelection().let { selection ->
        base.contentKind != contentKind || base.languageScope != languageScope ||
            base.countryIds != countryIds || base.genreIds != genreIds ||
            base.sort != sort || base.enabledSourceIds.map(SourceId::value).toSet() != enabledSourceIds ||
            base.yearStart != selection?.startInclusive || base.yearEnd != selection?.endInclusive
    }

    fun toQuery(queryId: String): CatalogQuery {
        require(yearError() == null) { "草稿年份无效，不能构造查询" }
        val selection = yearSelection()
        return CatalogQuery.create(queryId, contentKind, languageScope, countryIds, genreIds,
            selection?.startInclusive, selection?.endInclusive, sort, enabledSourceIds.map(::SourceId).toSet())
    }
}

/** 草稿与已应用查询之间的显式意图；查询身份只在用户真实变化时更换。 */
internal object LibraryFilterController {
    sealed interface ApplyOutcome {
        /** 无变化确认：沿用原查询身份，不重启结果，不清锚点。 */
        data class Unchanged(val query: CatalogQuery) : ApplyOutcome
        /** 真实变化：新 queryId 的完整查询 + 中文审计说明。 */
        data class Applied(val query: CatalogQuery, val auditReason: String) : ApplyOutcome
        /** 年份等草稿无效：保持面板与已应用查询不变。 */
        data class Invalid(val reason: String) : ApplyOutcome
    }

    fun begin(applied: CatalogQuery): LibraryFilterDraft {
        val mode = when {
            applied.yearStart == null && applied.yearEnd == null -> LibraryYearMode.NONE
            applied.yearStart != null && applied.yearStart == applied.yearEnd -> LibraryYearMode.EXACT
            applied.yearStart != null && applied.yearEnd != null &&
                applied.yearStart % 10 == 0 && applied.yearEnd == applied.yearStart + 9 -> LibraryYearMode.DECADE
            else -> LibraryYearMode.RANGE
        }
        return LibraryFilterDraft(
            base = applied,
            contentKind = applied.contentKind,
            languageScope = applied.languageScope,
            countryIds = applied.countryIds,
            genreIds = applied.genreIds,
            yearMode = mode,
            exactYear = applied.yearStart ?: 1994,
            decadeStart = ((applied.yearStart ?: 1990) / 10) * 10,
            rangeStart = applied.yearStart ?: 1980,
            rangeEnd = applied.yearEnd ?: 1999,
            sort = applied.sort,
            enabledSourceIds = applied.enabledSourceIds.map(SourceId::value).toSet(),
        )
    }

    fun apply(draft: LibraryFilterDraft, nextQueryId: () -> String): ApplyOutcome {
        draft.yearError()?.let { return ApplyOutcome.Invalid(it) }
        if (!draft.changedFromBase()) return ApplyOutcome.Unchanged(draft.base)
        val changed = mutableListOf<String>()
        if (draft.base.contentKind != draft.contentKind) changed += "内容类别"
        if (draft.base.languageScope != draft.languageScope) changed += "语言范围"
        if (draft.base.countryIds != draft.countryIds) changed += "国家地区"
        if (draft.base.genreIds != draft.genreIds) changed += "类型"
        if (draft.yearSelection().let { draft.base.yearStart != it?.startInclusive || draft.base.yearEnd != it?.endInclusive }) changed += "年份"
        if (draft.base.sort != draft.sort) changed += "排序"
        if (draft.base.enabledSourceIds.map(SourceId::value).toSet() != draft.enabledSourceIds) changed += "参与来源"
        return ApplyOutcome.Applied(draft.toQuery(nextQueryId()),
            "筛选已按用户操作更新：" + changed.joinToString("、"))
    }

    /** 删除单个筛选片：立即生成新查询，仅清除该字段，其余字段逐字保留。 */
    fun removeChip(applied: CatalogQuery, chip: LibraryFilterChipUi, nextQueryId: () -> String): CatalogQuery {
        val value = requireNotNull(chip.valueId) { "筛选片缺少规范取值" }
        return CatalogQuery.create(nextQueryId(),
            if (chip.field == "kind") null else applied.contentKind,
            if (chip.field == "language") LanguageScope.ALL else applied.languageScope,
            if (chip.field == "country") applied.countryIds - value else applied.countryIds,
            if (chip.field == "genre") applied.genreIds - value else applied.genreIds,
            if (chip.field == "year") null else applied.yearStart,
            if (chip.field == "year") null else applied.yearEnd,
            if (chip.field == "sort") CatalogSort.DEFAULT else applied.sort,
            if (chip.field == "source") applied.enabledSourceIds - SourceId(value)
            else applied.enabledSourceIds)
    }

    /** 全部重置为默认查询（默认来源集合由调用方给出）；字段已与默认一致时不换查询身份。 */
    fun reset(applied: CatalogQuery, defaultSources: Set<String>, nextQueryId: () -> String): CatalogQuery {
        val alreadyDefault = applied.contentKind == null && applied.languageScope == LanguageScope.ALL &&
            applied.countryIds.isEmpty() && applied.genreIds.isEmpty() &&
            applied.yearStart == null && applied.yearEnd == null && applied.sort == CatalogSort.DEFAULT &&
            applied.enabledSourceIds.map(SourceId::value).toSet() == defaultSources
        if (alreadyDefault) return applied
        return CatalogQuery.create(nextQueryId(), null, LanguageScope.ALL, emptySet(), emptySet(),
            null, null, CatalogSort.DEFAULT, defaultSources.map(::SourceId).toSet())
    }
}

/** 规范取值 → 中文显示的唯一映射；未收录取值如实显示原始 ID，不虚构词条。 */
internal object LibraryFilterLabels {
    val countries = mapOf("JP" to "日本", "TH" to "泰国", "US" to "美国", "CN" to "中国", "FR" to "法国")
    val genres = mapOf("horror" to "恐怖", "thriller" to "惊悚", "crime" to "犯罪", "action" to "动作", "comedy" to "喜剧")
    val kinds = mapOf(
        "movie" to "电影", "series" to "剧集", "variety" to "综艺", "animation" to "动画",
        "documentary" to "纪录片", "other" to "其他")
    val sorts = mapOf(
        "default" to "默认排序", "ascending" to "年份从早到晚", "descending" to "年份从晚到早")

    fun country(id: String) = countries[id] ?: id
    fun genre(id: String) = genres[id] ?: id
    fun kind(id: String) = kinds[id] ?: id
    fun sort(sort: CatalogSort) = when (sort) {
        CatalogSort.DEFAULT -> "默认排序"; CatalogSort.YEAR_ASCENDING -> "年份从早到晚"; CatalogSort.YEAR_DESCENDING -> "年份从晚到早"
    }

    /** 已应用查询的条件摘要；key 为规范 字段:取值，不把本地化文案当查询键。 */
    fun chips(query: CatalogQuery): List<LibraryFilterChipUi> = buildList {
        if (query.yearStart != null || query.yearEnd != null) {
            val value = when {
                query.yearStart != null && query.yearStart == query.yearEnd -> "${query.yearStart} 年"
                query.yearStart != null && query.yearEnd != null -> "${query.yearStart}–${query.yearEnd} 年"
                query.yearStart != null -> "${query.yearStart} 年及以后"
                else -> "${query.yearEnd} 年及以前"
            }
            add(LibraryFilterChipUi("year:${query.yearStart ?: ""}-${query.yearEnd ?: ""}", "year",
                "${query.yearStart ?: ""}..${query.yearEnd ?: ""}", value))
        }
        if (query.languageScope != LanguageScope.ALL) add(LibraryFilterChipUi(
            "language:${query.languageScope.name}", "language", query.languageScope.name,
            if (query.languageScope == LanguageScope.CHINESE) "华语" else "非华语"))
        query.countryIds.forEach { add(LibraryFilterChipUi("country:$it", "country", it, country(it))) }
        query.genreIds.forEach { add(LibraryFilterChipUi("genre:$it", "genre", it, genre(it))) }
        query.contentKind?.let { kind ->
            add(LibraryFilterChipUi("kind:${kind.name}", "kind", kind.name, kind(kind.name.lowercase())))
        }
        if (query.sort != CatalogSort.DEFAULT) add(LibraryFilterChipUi(
            "sort:${query.sort.name}", "sort", query.sort.name, sort(query.sort)))
        query.enabledSourceIds.forEach { source ->
            add(LibraryFilterChipUi("source:${source.value}", "source", source.value, "来源 ${source.value}"))
        }
    }
}
