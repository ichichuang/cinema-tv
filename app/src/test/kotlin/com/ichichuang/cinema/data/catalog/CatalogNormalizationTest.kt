package com.ichichuang.cinema.data.catalog

import com.ichichuang.cinema.domain.model.catalog.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic metadata only; no real catalog, URL or media. */
class CatalogNormalizationTest {
    private fun normalize(text: String, source: String = "synthetic-source-A") =
        MaccmsCatalogNormalizer.normalize(SourceId(source), Json.parseToJsonElement(text).jsonObject, 123L)

    @Test fun source_year_retains_raw_value_provider_time_and_version_without_confirmation() {
        val n = normalize("""{"vod_id":17,"vod_name":"synthetic-work","vod_year":" 1987 "}""")
        val year = n.year as FieldEvidence.SourceReported
        assertEquals(1987, year.value)
        assertEquals(" 1987 ", year.reference.rawValue)
        assertEquals(SourceId("synthetic-source-A"), year.reference.sourceId)
        assertEquals(SourceItemId("17"), year.reference.sourceItemId)
        assertEquals("maccms-exact-2026-10-24", year.reference.ruleVersion)
        assertEquals(123L, n.observedAt)
        assertEquals(JsonPrimitive(" 1987 "), n.raw["vod_year"])
    }
    @Test fun malformed_year_remains_unknown_with_original_observation() {
        for (value in listOf("1994年", "0", "-1", "1994.5", "999999999999")) {
            val n = normalize("""{"vod_id":"17","vod_year":"$value"}""")
            assertEquals(value, (n.year as FieldEvidence.Unknown).observation!!.rawValue)
        }
    }
    @Test fun missing_null_and_known_empty_are_distinct_and_missing_id_is_not_a_title_hash() {
        val missing = normalize("""{"vod_name":"synthetic-work"}""")
        assertNull(missing.itemId)
        assertEquals(FieldEvidence.Unknown(), missing.facts.getValue("countries"))
        val empty = normalize("""{"vod_id":"17","vod_area":""}""")
        assertEquals(emptySet<String>(), (empty.facts.getValue("countries") as FieldEvidence.SourceReported).value.values)
        val nil = normalize("""{"vod_id":"17","vod_area":null}""")
        assertTrue(nil.facts.getValue("countries") is FieldEvidence.Unknown)
        assertTrue(nil.raw.containsKey("vod_area"))
        assertFalse(missing.raw.containsKey("vod_area"))
    }
    @Test fun work_language_audio_and_subtitles_are_separate_source_claims() {
        val n = normalize("""{"vod_id":"17","work_original_language":"日语","vod_lang":"国语配音","subtitle_language":"中文字幕"}""")
        assertEquals(setOf("ja"), (n.facts.getValue("originalLanguages") as FieldEvidence.SourceReported).value.values)
        assertEquals(setOf("zh"), (n.facts.getValue("audioLanguages") as FieldEvidence.SourceReported).value.values)
        assertEquals(setOf("zh"), (n.facts.getValue("subtitleLanguages") as FieldEvidence.SourceReported).value.values)
        val ambiguous = normalize("""{"vod_id":"17","vod_lang":"日语"}""")
        assertTrue(ambiguous.facts.getValue("originalLanguages") is FieldEvidence.Unknown)
        assertTrue(ambiguous.facts.getValue("audioLanguages") is FieldEvidence.Unknown)
        assertTrue(ambiguous.facts.getValue("countries") is FieldEvidence.Unknown)
    }
    @Test fun multiple_countries_and_genres_survive_without_expanding_vague_regions() {
        val n = normalize("""{"vod_id":"17","vod_area":"日本/中国","vod_class":"动作/惊悚"}""")
        assertEquals(setOf("JP", "CN"), (n.facts.getValue("countries") as FieldEvidence.SourceReported).value.values)
        assertEquals(setOf("action", "thriller"), (n.facts.getValue("genres") as FieldEvidence.SourceReported).value.values)
        assertTrue(normalize("""{"vod_id":"17","vod_area":"欧美"}""").facts.getValue("countries") is FieldEvidence.Unknown)
    }
    @Test fun aliases_are_exact_versioned_and_thriller_is_never_horror_or_numeric_category() {
        for (alias in listOf("动作片", "動作片", "动作")) {
            val fact = normalize("""{"vod_id":"17","vod_class":"$alias"}""").facts.getValue("genres") as FieldEvidence.SourceReported
            assertEquals(setOf("action"), fact.value.values)
            assertEquals(alias, fact.reference.rawValue)
        }
        assertTrue(normalize("""{"vod_id":"17","vod_class":"动作精选","type_id":"6"}""").facts.getValue("genres") is FieldEvidence.Unknown)
        val thriller = normalize("""{"vod_id":"17","vod_class":"惊悚"}""").facts.getValue("genres") as FieldEvidence.SourceReported
        assertFalse("horror" in thriller.value.values)
    }
    @Test fun object_values_and_unknown_fields_are_preserved_without_stringifying_into_facts() {
        val n = normalize("""{"vod_id":"17","vod_year":{"value":1994},"custom":{"untouched":[1,null,""]}}""")
        assertTrue(n.year is FieldEvidence.Unknown)
        assertEquals(Json.parseToJsonElement("""{"untouched":[1,null,""]}"""), n.raw["custom"])
    }
    @Test fun corrections_keep_both_year_claims_and_conflict_instead_of_latest_wins() {
        val old = normalize("""{"vod_id":"17","vod_year":"1994"}""").year
        val next = normalize("""{"vod_id":"17","vod_year":"1995"}""").year
        val merged = CatalogClaims.combine(listOf(old, next)) as FieldEvidence.Conflict
        assertEquals(listOf(1994, 1995), merged.claims.map { it.value })
        assertEquals(listOf("1994", "1995"), merged.claims.map { it.reference.rawValue })
    }
}
