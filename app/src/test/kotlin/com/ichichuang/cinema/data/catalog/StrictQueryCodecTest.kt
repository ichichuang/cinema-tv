package com.ichichuang.cinema.data.catalog

import com.ichichuang.cinema.domain.model.catalog.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class StrictQueryCodecTest {
    private val source=SourceId("codec-source")
    private fun session()=StoredQuerySession(CatalogQuery.create("strict-codec",contentKind=ContentKind.MOVIE,
        languageScope=LanguageScope.NON_CHINESE,countryIds=setOf("JP","TH"),genreIds=setOf("crime"),
        yearStart=1985,yearEnd=1995,sort=CatalogSort.YEAR_ASCENDING,enabledSourceIds=setOf(source)),listOf(SourceCursorState(source,"1")))
    @Test fun full_selection_roundtrip_preserves_every_filter() {
        val saved=session()
        assertEquals(saved,QuerySessionCodec.decode(QuerySessionCodec.encode(saved)))
    }
    @Test fun malformed_present_full_query_never_falls_back_to_broader_legacy_selection() {
        val raw=Json.parseToJsonElement(QuerySessionCodec.encode(session())).jsonObject.toMutableMap()
        raw["fullQuery"]=JsonPrimitive("malformed")
        assertTrue(runCatching {QuerySessionCodec.decode(JsonObject(raw).toString())}.isFailure)
    }
    @Test fun genuine_legacy_year_source_payload_remains_readable() {
        val raw=Json.parseToJsonElement(QuerySessionCodec.encode(session())).jsonObject.toMutableMap()
        raw.remove("fullQuery")
        val query=QuerySessionCodec.decode(JsonObject(raw).toString()).query
        assertEquals(1985,query.yearStart);assertEquals(1995,query.yearEnd)
        assertEquals(setOf(source),query.enabledSourceIds);assertEquals(LanguageScope.ALL,query.languageScope)
    }
}
