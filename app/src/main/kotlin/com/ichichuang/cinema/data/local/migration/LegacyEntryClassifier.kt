package com.ichichuang.cinema.data.local.migration

import kotlinx.serialization.json.Json
import com.ichichuang.cinema.data.local.codec.integer

internal enum class LegacyEntryStatus(val tag: String) { RECOGNIZED("recognized"), UNRESOLVED("unresolved"), MALFORMED("malformed") }
internal data class LegacyCoordinates(val lineIndex: Int, val episodeIndex: Int, val positionMs: Long, val durationMs: Long)
internal data class LegacyEntryAnalysis(val status: LegacyEntryStatus, val reason: String, val coordinates: LegacyCoordinates? = null)

internal object LegacyEntryClassifier {
    fun classify(entry: LegacyEntry): LegacyEntryAnalysis = when (entry.store) {
        LegacyStoreKind.PROGRESS -> if (entry.key.startsWith("wp_")) progress(entry) else unresolved()
        LegacyStoreKind.BROWSE -> when (entry.key) {
            "last_movie_id" -> if (entry.value.type == "string") unresolved("legacy_identity_unresolved") else malformed("wrong_value_type")
            "last_index" -> if (entry.value.type != "int") malformed("wrong_value_type")
                else if (Json.parseToJsonElement(entry.value.encoded).integer() < 0) malformed("invalid_browse_index")
                else unresolved("legacy_index_only")
            else -> unresolved()
        }
        LegacyStoreKind.FILTERS -> if (entry.key in setOf("f_category", "f_lang", "f_area", "f_year", "f_types")) {
            if (entry.value.type == "string") LegacyEntryAnalysis(LegacyEntryStatus.RECOGNIZED, "legacy_filter_only")
            else malformed("wrong_value_type")
        } else unresolved()
        LegacyStoreKind.SETTINGS -> LegacyEntryAnalysis(LegacyEntryStatus.RECOGNIZED, "configuration_preserved_only")
    }

    private fun progress(entry: LegacyEntry): LegacyEntryAnalysis {
        val raw = entry.value.stringOrNull() ?: return malformed("wrong_value_type")
        if (entry.key.removePrefix("wp_").isBlank()) return malformed("empty_legacy_identity")
        val parts = raw.split(',')
        if (parts.size != 4) return malformed("invalid_progress_coordinates")
        val line = parts[0].toIntOrNull()
        val episode = parts[1].toIntOrNull()
        val position = parts[2].toLongOrNull()
        val duration = parts[3].toLongOrNull()
        if (line == null || episode == null || position == null || duration == null ||
            line < 0 || episode < 0 || position < 0 || duration < 0) return malformed("invalid_progress_coordinates")
        return LegacyEntryAnalysis(LegacyEntryStatus.UNRESOLVED, "canonical_identity_unresolved",
            LegacyCoordinates(line, episode, position, duration))
    }

    private fun unresolved(reason: String = "unknown_legacy_key") = LegacyEntryAnalysis(LegacyEntryStatus.UNRESOLVED, reason)
    private fun malformed(reason: String) = LegacyEntryAnalysis(LegacyEntryStatus.MALFORMED, reason)
}
