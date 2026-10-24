package com.ichichuang.cinema.data.catalog

import com.ichichuang.cinema.domain.model.catalog.*

internal enum class StrictFacet { YEAR, COUNTRY, GENRE, CONTENT_KIND, WORK_LANGUAGE }
internal data class ExplicitFacetRemoval(val originalQueryId:String,val selection:CatalogQuery,val facet:StrictFacet,val reason:String)
internal fun CatalogQuery.explicitRemove(facet:StrictFacet,newQueryId:String):ExplicitFacetRemoval {
    require(newQueryId!=queryId && newQueryId.isNotBlank()) { "Facet removal requires a new query identity" }
    require(when(facet) {
        StrictFacet.YEAR -> yearStart!=null || yearEnd!=null
        StrictFacet.COUNTRY -> countryIds.isNotEmpty()
        StrictFacet.GENRE -> genreIds.isNotEmpty()
        StrictFacet.CONTENT_KIND -> contentKind!=null
        StrictFacet.WORK_LANGUAGE -> languageScope!=LanguageScope.ALL
    }) { "Cannot remove an unselected facet" }
    val selected=CatalogQuery.create(newQueryId,
        contentKind=if(facet==StrictFacet.CONTENT_KIND) null else contentKind,
        languageScope=if(facet==StrictFacet.WORK_LANGUAGE) LanguageScope.ALL else languageScope,
        countryIds=if(facet==StrictFacet.COUNTRY) emptySet() else countryIds,
        genreIds=if(facet==StrictFacet.GENRE) emptySet() else genreIds,
        yearStart=if(facet==StrictFacet.YEAR) null else yearStart,
        yearEnd=if(facet==StrictFacet.YEAR) null else yearEnd,sort=sort,enabledSourceIds=enabledSourceIds)
    val name=when(facet) {StrictFacet.YEAR->"年份";StrictFacet.COUNTRY->"制作国家或地区";StrictFacet.GENRE->"类型";
        StrictFacet.CONTENT_KIND->"内容类别";StrictFacet.WORK_LANGUAGE->"作品原始语言"}
    return ExplicitFacetRemoval(queryId,selected,facet,"已按明确操作移除${name}条件")
}

internal enum class StrictSortScope { OBSERVED_ONLY, EXHAUSTED_SELECTED_SOURCES }
/** Counts are scoped to selected sources and observed evidence, never the global catalog. */
internal data class StrictQueryProjection(val selection:CatalogQuery,val verifiedObservedMatchCount:Int,
    val knownScopedMatchCount:Long?,val unresolvedWorkCount:Int,val sortScope:StrictSortScope,val displayState:String) {
    val globalTotal:Long? get() = null
}
