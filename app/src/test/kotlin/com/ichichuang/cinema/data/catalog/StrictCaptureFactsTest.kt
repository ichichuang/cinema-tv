package com.ichichuang.cinema.data.catalog

import com.ichichuang.cinema.domain.filter.*
import com.ichichuang.cinema.domain.model.catalog.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class StrictCaptureFactsTest {
    private fun capture(id: String="one", vararg fields: Pair<String,String>) = MaccmsCatalogNormalizer.normalize(SourceId("fixture"), buildJsonObject { put("vod_id",id); fields.forEach { (key,value)->put(key,value) } }, 1)
    private fun evaluate(captures: List<NormalizedCatalogCapture>, countries: Set<String> = emptySet(), genres: Set<String> = emptySet(), language: LanguageScope=LanguageScope.ALL, kind: ContentKind?=null) = StrictFilterEvaluator.evaluate(CatalogQuery.create("fixture",countryIds=countries,genreIds=genres,languageScope=language,contentKind=kind),StrictCaptureFacts.fromCaptures(captures))

    // Break: exact new mappings missing, unsafe broad aliases introduced, or provenance lost.
    @Test fun exact_extensions_preserve_versioned_references_without_broad_guesses() {
        val capture=capture("one","vod_area" to "泰国","vod_class" to "犯罪","vod_year" to "1994")
        val result=evaluate(listOf(capture),countries=setOf("JP","TH"),genres=setOf("crime","action"))
        assertEquals(MatchVerdict.MATCH,result.verdict)
        val proof=result.dimensions["countries"]!!.evidence as FieldEvidence.SourceReported<*>
        assertEquals("泰国",proof.reference.rawValue)
        assertEquals("strict-explicit-facets-2026-10-24",proof.reference.ruleVersion)
        assertEquals(SourceItemId("one"),proof.reference.sourceItemId)
        assertEquals(capture.year,StrictCaptureFacts.fromCaptures(listOf(capture)).year)
        assertTrue(capture.facts["countries"] is FieldEvidence.Unknown)
        assertEquals(MatchVerdict.INDETERMINATE,evaluate(listOf(capture("broad","vod_area" to "欧美")),countries=setOf("US")).verdict)
        assertEquals(MatchVerdict.MISMATCH,evaluate(listOf(capture("thrill","vod_class" to "惊悚")),genres=setOf("horror")).verdict)
    }
    // Break: scope marker from another capture establishes language completeness.
    @Test fun complete_scope_must_bind_to_actual_language_in_same_capture() {
        val bare=capture("bare","work_original_language_scope" to "complete")
        val partial=capture("partial","work_original_language" to "英语")
        assertEquals(MatchVerdict.INDETERMINATE,evaluate(listOf(bare,partial),language=LanguageScope.NON_CHINESE).verdict)
        val complete=capture("complete","work_original_language" to "英语","work_original_language_scope" to "complete")
        assertEquals(MatchVerdict.MATCH,evaluate(listOf(complete),language=LanguageScope.NON_CHINESE).verdict)
        val scopeProof=evaluate(listOf(complete),language=LanguageScope.NON_CHINESE).dimensions["originalLanguages"]!!.scopeEvidence as FieldEvidence.SourceReported<*>
        assertTrue(scopeProof.reference.rawValue.contains("complete"))
        assertEquals(SourceItemId("complete"),scopeProof.reference.sourceItemId)
        assertEquals(MatchVerdict.INDETERMINATE,evaluate(listOf(complete,capture("other","work_original_language" to "普通话")),language=LanguageScope.NON_CHINESE).verdict)
    }
    // Break: audio/subtitles or CMS type index invent work language/country/content kind.
    @Test fun explicit_work_fields_only_and_multilingual_facts_retained() {
        val playback=capture("playback","vod_lang" to "国语配音","subtitle_language" to "中文字幕","type_name" to "电影","type_id" to "1")
        assertEquals(MatchVerdict.INDETERMINATE,evaluate(listOf(playback),language=LanguageScope.CHINESE).verdict)
        assertEquals(MatchVerdict.INDETERMINATE,evaluate(listOf(playback),countries=setOf("CN")).verdict)
        assertEquals(MatchVerdict.INDETERMINATE,evaluate(listOf(playback),kind=ContentKind.MOVIE).verdict)
        val work=capture("work","work_original_language" to "普通话/英语","work_content_kind" to "movie","vod_area" to "日本/美国")
        assertEquals(MatchVerdict.MATCH,evaluate(listOf(work),countries=setOf("JP"),language=LanguageScope.CHINESE,kind=ContentKind.MOVIE).verdict)
        assertEquals(setOf("zh","en"),(StrictCaptureFacts.fromCaptures(listOf(work)).originalLanguages as FieldEvidence.SourceReported).value.values)
        assertEquals(MatchVerdict.MISMATCH,evaluate(listOf(work),kind=ContentKind.SERIES).verdict)
    }
}
