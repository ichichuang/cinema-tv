package com.ichichuang.cinema.domain.model.catalog

import java.util.Collections

enum class ContentKind { MOVIE, SERIES, VARIETY, ANIMATION, DOCUMENTARY, OTHER }
enum class LanguageScope { ALL, CHINESE, NON_CHINESE }
enum class CatalogSort { DEFAULT, YEAR_ASCENDING, YEAR_DESCENDING }

/** 年份包含两端，null 为无界；空来源集合不自动扩展为全部来源。 */
@ConsistentCopyVisibility
data class CatalogQuery private constructor(
    val queryId: String,
    val contentKind: ContentKind?,
    val languageScope: LanguageScope,
    val countryIds: Set<String>,
    val genreIds: Set<String>,
    val yearStart: Int?,
    val yearEnd: Int?,
    val sort: CatalogSort,
    val enabledSourceIds: Set<SourceId>,
) {
    init {
        require(queryId.isNotBlank()) { "queryId must not be blank" }
        require(countryIds.all { it.isNotBlank() } && genreIds.all { it.isNotBlank() }) { "Filter IDs must not be blank" }
        require(yearStart == null || yearStart > 0) { "yearStart must be positive or null" }
        require(yearEnd == null || yearEnd > 0) { "yearEnd must be positive or null" }
        require(yearStart == null || yearEnd == null || yearStart <= yearEnd) { "Year range must not be inverted" }
    }

    companion object {
        fun create(
            queryId: String,
            contentKind: ContentKind? = null,
            languageScope: LanguageScope = LanguageScope.ALL,
            countryIds: Set<String> = emptySet(),
            genreIds: Set<String> = emptySet(),
            yearStart: Int? = null,
            yearEnd: Int? = null,
            sort: CatalogSort = CatalogSort.DEFAULT,
            enabledSourceIds: Set<SourceId> = emptySet(),
        ): CatalogQuery = CatalogQuery(
            queryId, contentKind, languageScope,
            Collections.unmodifiableSet(LinkedHashSet(countryIds)),
            Collections.unmodifiableSet(LinkedHashSet(genreIds)),
            yearStart, yearEnd, sort,
            Collections.unmodifiableSet(LinkedHashSet(enabledSourceIds)),
        )
    }
}
