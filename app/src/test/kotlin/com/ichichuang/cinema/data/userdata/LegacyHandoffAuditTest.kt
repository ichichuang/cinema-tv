package com.ichichuang.cinema.data.userdata

import com.ichichuang.cinema.data.local.migration.*
import com.ichichuang.cinema.domain.model.catalog.*
import com.ichichuang.cinema.domain.model.userdata.WatchState
import org.junit.Assert.*
import org.junit.Test

class LegacyHandoffAuditTest {
    private val target = PlaybackTarget(WorkId("declared-work"), EpisodeId("declared-episode"), VariantId("declared-cut"))
    private val link = HandoffLink("fixture-source", "movie", 0, 2, target)
    private fun entry(kind: LegacyStoreKind, key: String, value: Any) = LegacyEntry(kind, key, LegacyValue.from(value))
    private val progress = entry(LegacyStoreKind.PROGRESS, "wp_fixture-source#movie", "0,2,2000,0")
    private fun snapshot(vararg entries: LegacyEntry) = LegacySnapshot.create(entries.toList(), -123)
    private fun audit(s: LegacySnapshot, links: List<HandoffLink> = listOf(link), expected: String = s.contentDigest) =
        LegacyHandoffAudit.inspect(s, HandoffManifest(expected, "explicit-fixture-catalog", links))
    @Test fun declared_exact_source_episode_and_variant_restore_last_position_with_unknown_duration() {
        val s = snapshot(progress)
        val a = audit(s)
        assertTrue(a.reason, a.eligible)
        val restored = a.restored.single()
        assertEquals(target.workId, restored.workId); assertEquals(target.episodeId, restored.episodeId)
        assertEquals(target.variantId, restored.variantId); assertEquals(2000L, restored.positionMs)
        assertNull(restored.durationMs); assertEquals(-123L, restored.capturedAt)
        assertEquals(WatchState.IN_PROGRESS, restored.watchState)
    }
    @Test fun explicit_zero_and_position_larger_than_stale_duration_are_preserved() {
        for (raw in listOf("0,2,0,0", "0,2,2000,1000")) {
            val a = audit(snapshot(progress.copy(value = LegacyValue.from(raw))))
            assertTrue(a.eligible); assertEquals(raw.split(',')[2].toLong(), a.restored.single().positionMs)
        }
    }
    @Test fun bare_id_and_missing_origin_never_become_canonical() {
        assertFalse(audit(snapshot(progress.copy(key = "wp_movie"))).eligible)
        assertFalse(audit(snapshot(progress), listOf(link.copy(origin = ""))).eligible)
    }
    @Test fun cross_source_collision_and_conflicting_mapping_are_refused() {
        assertFalse(audit(snapshot(progress), listOf(link.copy(origin = "other"))).eligible)
        val conflict = link.copy(target = target.copy(workId = WorkId("other")))
        assertFalse(audit(snapshot(progress), listOf(link, conflict)).eligible)
    }
    @Test fun reordered_episode_and_changed_timebase_are_not_guessed() {
        assertFalse(audit(snapshot(progress), listOf(link.copy(episode = 1))).eligible)
        assertFalse(audit(snapshot(progress), listOf(link.copy(target = target.copy(variantId = null)))).eligible)
    }
    @Test fun every_visible_progress_must_be_resolvable() {
        val s = snapshot(progress, progress.copy(key = "wp_other#movie"))
        assertFalse(audit(s).eligible); assertEquals(2, s.entries.size)
    }
    @Test fun malformed_wrong_type_and_unknown_records_block_but_raw_values_remain() {
        for (value in listOf<Any>("0,2,unknown,0", "0,2,-1,0", 9L)) {
            val e = progress.copy(value = LegacyValue.from(value)); val s = snapshot(e)
            assertFalse(audit(s).eligible); assertEquals(e.value, s.entries.single().value)
        }
    }
    @Test fun browse_and_filter_values_are_preserved_without_fabricating_a_navigation_snapshot() {
        val s = snapshot(progress, entry(LegacyStoreKind.BROWSE, "last_movie_id", link.movieKey),
            entry(LegacyStoreKind.BROWSE, "last_index", 9001), entry(LegacyStoreKind.FILTERS, "f_lang", ""))
        assertTrue(audit(s).eligible); assertEquals(1, audit(s).restored.size)
        assertFalse(audit(snapshot(progress, entry(LegacyStoreKind.BROWSE, "last_movie_id", "bare"))).eligible)
    }
    @Test fun stable_content_ignores_capture_time_and_iteration_order_but_not_one_changed_value() {
        val e = entry(LegacyStoreKind.FILTERS, "f_area", "中文")
        val a = snapshot(progress, e)
        val b = LegacySnapshot.create(listOf(e, progress), 999)
        assertEquals(a.contentDigest, b.contentDigest)
        assertEquals(audit(a).mappingDigest, audit(b).mappingDigest)
        val c = snapshot(progress, e.copy(value = LegacyValue.from("变化")))
        assertNotEquals(a.contentDigest, c.contentDigest)
        assertFalse(audit(c, expected = a.contentDigest).eligible)
    }
    @Test fun changed_declared_catalog_changes_verified_mapping_fingerprint() {
        val s = snapshot(progress)
        val a = audit(s)
        val b = LegacyHandoffAudit.inspect(s, HandoffManifest(s.contentDigest, "changed-fixture-catalog", listOf(link)))
        assertNotEquals(a.mappingDigest, b.mappingDigest)
    }
}
