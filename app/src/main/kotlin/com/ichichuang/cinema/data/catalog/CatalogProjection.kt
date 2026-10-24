package com.ichichuang.cinema.data.catalog

import com.ichichuang.cinema.domain.model.catalog.*
import com.ichichuang.cinema.data.remote.*

internal data class DeclaredCatalogEntry(val entryId: String, val episodeId: EpisodeId, val variantId: VariantId, val lineIndex: Int, val episodeIndex: Int)
internal data class ProjectedCatalogItem(val remote: RemoteVod, val entries: List<DeclaredCatalogEntry>)
internal object CatalogProjection {
    fun project(capture: NormalizedCatalogCapture, entries: List<DeclaredCatalogEntry>): ProjectedCatalogItem? {
        val id = capture.itemId ?: return null
        val raw = capture.raw
        val remote = RemoteVod(id.value, raw["vod_name"].scalar().orEmpty(), raw["vod_pic"].scalar(), raw["vod_remarks"].scalar(),
            raw["vod_year"].scalar(), raw["vod_area"].scalar(), raw["vod_lang"].scalar(), raw["vod_score"].scalar(),
            raw["type_id"].scalar(), raw["type_name"].scalar(), raw["vod_class"].scalar(), raw["vod_content"].scalar(),
            parsePlaySources(raw["vod_play_from"].scalar(), raw["vod_play_url"].scalar()))
        require(entries.map { it.entryId }.distinct().size == entries.size)
        require(entries.map { it.lineIndex to it.episodeIndex }.distinct().size == entries.size)
        entries.forEach {
            require(it.entryId.isNotBlank() && it.lineIndex >= 0 && it.episodeIndex >= 0)
            require(remote.playSources.getOrNull(it.lineIndex)?.episodes?.getOrNull(it.episodeIndex) != null)
        }
        return ProjectedCatalogItem(remote, entries.toList())
    }
}
