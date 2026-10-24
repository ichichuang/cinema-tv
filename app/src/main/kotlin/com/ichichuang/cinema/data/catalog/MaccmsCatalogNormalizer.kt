package com.ichichuang.cinema.data.catalog

import com.ichichuang.cinema.domain.model.catalog.*
import kotlinx.serialization.json.*

/** All values are source claims. Raw JSON preserves missing, null, empty and malformed fields. */
internal data class NormalizedCatalogCapture(val sourceId: SourceId, val itemId: SourceItemId?, val raw: JsonObject,
    val observedAt: Long, val ruleVersion: String, val year: FieldEvidence<Int>, val facts: Map<String, FieldEvidence<FactIds>>, val fieldNames: Map<String, String> = emptyMap()) {
    init {
        require(ruleVersion.isNotBlank())
        val years=when(year) {
            is FieldEvidence.SourceReported -> listOf(year.value)
            is FieldEvidence.Confirmed -> listOf(year.value)
            is FieldEvidence.Conflict -> year.claims.map { it.value }
            is FieldEvidence.Unknown -> emptyList()
        }
        require(years.all { it > 0 })
    }
}

internal object MaccmsCatalogNormalizer {
    const val RULE_VERSION = "maccms-exact-2026-10-24"
    fun normalize(sourceId: SourceId, raw: JsonObject, observedAt: Long): NormalizedCatalogCapture {
        val snapshot = Json.parseToJsonElement(raw.toString()).jsonObject
        val itemId = snapshot["vod_id"].scalar()?.takeIf { it.isNotBlank() }?.let(::SourceItemId)
        fun reference(field: String) = snapshot[field]?.let {
            EvidenceReference(sourceId, itemId, it.scalar() ?: it.toString(), RULE_VERSION)
        }
        fun facts(field: String, aliases: Map<String, String>): FieldEvidence<FactIds> {
            val value = snapshot[field].scalar() ?: return FieldEvidence.Unknown(reference(field))
            val tokens = value.trim().split(Regex("[/,、;|]" )).map { it.trim() }
            val mapped = if (value.isEmpty()) emptyList() else tokens.map { aliases[it] ?: return FieldEvidence.Unknown(reference(field)) }
            return FieldEvidence.SourceReported(FactIds.of(mapped.toSet()), reference(field)!!)
        }
        val year = snapshot["vod_year"].scalar()?.trim()?.takeIf { it.matches(Regex("[0-9]+")) }?.toIntOrNull()?.takeIf { it > 0 }
        val yearEvidence = if (year == null) FieldEvidence.Unknown(reference("vod_year")) else FieldEvidence.SourceReported(year, reference("vod_year")!!)
        return NormalizedCatalogCapture(sourceId, itemId, snapshot, observedAt, RULE_VERSION, yearEvidence, linkedMapOf(
            "countries" to facts("vod_area", TaxonomyRuleMapper.countries),
            "genres" to facts("vod_class", TaxonomyRuleMapper.genres),
            // Explicit extension claim only. Bare vod_lang has no original-work-language semantics.
            "originalLanguages" to facts("work_original_language", TaxonomyRuleMapper.languages),
            "audioLanguages" to facts("vod_lang", TaxonomyRuleMapper.audioLabels),
            "subtitleLanguages" to facts("subtitle_language", TaxonomyRuleMapper.subtitleLabels)), mapOf(
                "year" to "vod_year", "countries" to "vod_area", "genres" to "vod_class",
                "originalLanguages" to "work_original_language", "audioLanguages" to "vod_lang", "subtitleLanguages" to "subtitle_language"))
    }
}

internal object TaxonomyRuleMapper {
    val countries = mapOf("日本" to "JP", "中国" to "CN", "中國" to "CN", "美国" to "US", "美國" to "US", "法国" to "FR")
    val languages = mapOf("日语" to "ja", "日語" to "ja", "普通话" to "zh", "英语" to "en")
    val audioLabels = mapOf("国语配音" to "zh", "國語配音" to "zh", "日语原声" to "ja")
    val subtitleLabels = mapOf("中文字幕" to "zh", "日文字幕" to "ja", "英文字幕" to "en")
    val genres = mapOf("动作片" to "action", "動作片" to "action", "动作" to "action", "惊悚" to "thriller", "恐怖" to "horror", "喜剧" to "comedy")
}

internal fun JsonElement?.scalar(): String? = (this as? JsonPrimitive)?.takeIf { it !== JsonNull && (it.isString || it.content.toLongOrNull() != null) }?.content

internal object CatalogClaims {
    fun <T> combine(claims: List<FieldEvidence<T>>): FieldEvidence<T> {
        val reported = claims.flatMap { when (it) {
            is FieldEvidence.SourceReported -> listOf(it)
            is FieldEvidence.Confirmed -> listOf(FieldEvidence.SourceReported(it.value, it.reference))
            is FieldEvidence.Conflict -> it.claims
            is FieldEvidence.Unknown -> emptyList()
        } }.distinct()
        return when {
            reported.map { it.value }.distinct().size > 1 -> FieldEvidence.Conflict.of(reported)
            reported.isNotEmpty() -> reported.first()
            else -> claims.firstOrNull() ?: FieldEvidence.Unknown()
        }
    }
}
