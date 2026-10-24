package com.ichichuang.cinema.data.catalog

import com.ichichuang.cinema.domain.model.catalog.*
import org.junit.Assert.*
import org.junit.Test

class StrictProjectionTest {
    @Test fun explicit_removal_changes_only_named_facet_and_records_chinese_audit() {
        val before=CatalogQuery.create("before",contentKind=ContentKind.MOVIE,languageScope=LanguageScope.CHINESE,
            countryIds=setOf("JP"),genreIds=setOf("crime"),yearStart=1985,yearEnd=1995,
            sort=CatalogSort.YEAR_ASCENDING,enabledSourceIds=setOf(SourceId("fixture")))
        val action=before.explicitRemove(StrictFacet.YEAR,"after")
        assertEquals("after",action.selection.queryId);assertNull(action.selection.yearStart);assertNull(action.selection.yearEnd)
        assertEquals(before.countryIds,action.selection.countryIds);assertEquals(before.genreIds,action.selection.genreIds)
        assertEquals(before.languageScope,action.selection.languageScope);assertEquals(before.contentKind,action.selection.contentKind)
        assertEquals(before.sort,action.selection.sort);assertEquals(before.enabledSourceIds,action.selection.enabledSourceIds)
        assertEquals(1985,before.yearStart);assertEquals(StrictFacet.YEAR,action.facet)
        assertTrue(action.reason.contains("年份"));assertEquals("before",action.originalQueryId)
        assertTrue(runCatching {before.explicitRemove(StrictFacet.YEAR,"before")}.isFailure)
    }
    @Test fun explicit_removal_rejects_absent_facet_instead_of_audit_without_selection_change() {
        assertTrue(runCatching {CatalogQuery.create("empty").explicitRemove(StrictFacet.YEAR,"next")}.isFailure)
    }
}
