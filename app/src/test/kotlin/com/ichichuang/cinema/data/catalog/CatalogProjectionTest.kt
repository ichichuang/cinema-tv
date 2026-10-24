package com.ichichuang.cinema.data.catalog

import com.ichichuang.cinema.domain.model.catalog.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CatalogProjectionTest {
    private val source = SourceId("synthetic-A")
    private val entries = listOf(DeclaredCatalogEntry("opaque-entry-A", EpisodeId("opaque-episode-A"), VariantId("opaque-version-A"), 0, 0),
        DeclaredCatalogEntry("opaque-entry-B", EpisodeId("opaque-episode-B"), VariantId("opaque-version-B"), 1, 0))
    private val raw = Json.parseToJsonElement("""{"vod_id":"17","vod_name":"synthetic-work","vod_play_from":"synthetic-cut-A§§§synthetic-cut-B","vod_play_url":"synthetic-episode§fixture:route-A§§§synthetic-episode§fixture:route-B"}""".replace('§', '$')).jsonObject
    @Test fun independently_declared_versions_and_episodes_remain_distinct() {
        val n = MaccmsCatalogNormalizer.normalize(source, raw, 4)
        val p = CatalogProjection.project(n, entries)!!
        assertEquals("17", p.remote.id)
        assertEquals(listOf("opaque-entry-A", "opaque-entry-B"), p.entries.map { it.entryId })
        assertEquals(2, p.remote.playSources.size)
    }
    @Test fun missing_provider_id_cannot_project_from_title_hash() {
        val n = MaccmsCatalogNormalizer.normalize(source, Json.parseToJsonElement("""{"vod_name":"synthetic-work"}""").jsonObject, 4)
        assertNull(CatalogProjection.project(n, emptyList()))
    }
    @Test fun duplicated_entry_or_invalid_route_coordinate_is_rejected() {
        val n = MaccmsCatalogNormalizer.normalize(source, raw, 4)
        assertThrows(IllegalArgumentException::class.java) { CatalogProjection.project(n, entries + entries.first()) }
        assertThrows(IllegalArgumentException::class.java) { CatalogProjection.project(n, listOf(entries.first().copy(lineIndex = 9))) }
    }
    @Test fun unchanged_explicit_ids_survive_route_url_change_without_deriving_identity_from_url() {
        val changed = JsonObject(raw + ("vod_play_url" to JsonPrimitive("synthetic-episode\$fixture:replacement-A\$\$\$synthetic-episode\$fixture:replacement-B")))
        val p = CatalogProjection.project(MaccmsCatalogNormalizer.normalize(source, changed, 5), entries)!!
        assertEquals(entries, p.entries)
        assertEquals("fixture:replacement-A", p.remote.playSources.first().episodes.first().url)
    }
}
