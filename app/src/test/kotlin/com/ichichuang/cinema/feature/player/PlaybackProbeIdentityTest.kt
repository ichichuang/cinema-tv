package com.ichichuang.cinema.feature.player

import com.ichichuang.cinema.data.userdata.PlaybackTarget
import com.ichichuang.cinema.domain.model.*
import com.ichichuang.cinema.domain.model.catalog.*
import org.junit.Assert.*
import org.junit.Test

class PlaybackProbeIdentityTest {
    private val detail = MovieDetail(Movie("id", "测试", 0, 0.0, "", 0, "", origin = "source"),
        lines = listOf(PlayLine("line", listOf(Episode("一", "file:/synthetic/a"), Episode("二", "file:/synthetic/b")))))
    private val a = PlaybackTarget(WorkId("supplied-work"), EpisodeId("supplied-a"), VariantId("supplied-cut"))
    private val item = ProbeMedia(detail, 0, 0, a)
    private fun catalog(vararg items: ProbeMedia) = object : PlaybackProbeCatalog { override val probeMedia = items.toList() }
    @Test fun explicit_source_episode_and_timebase_are_reused_without_guessing() {
        assertEquals(a, ProbeIdentity.resolve(catalog(item), detail, 0, 0)?.target)
    }
    @Test fun changed_lineup_never_matches_same_numeric_index() {
        val changed = detail.copy(lines = listOf(detail.lines[0].copy(episodes = detail.lines[0].episodes.reversed())))
        assertNull(ProbeIdentity.resolve(catalog(item), changed, 0, 0))
        assertNull(ProbeIdentity.resolve(catalog(item), detail.copy(movie = detail.movie.copy(origin = "other")), 0, 0))
    }
    @Test fun duplicate_locator_or_identity_is_refused() {
        assertNull(ProbeIdentity.resolve(catalog(item, item), detail, 0, 0))
        assertNull(ProbeIdentity.resolve(catalog(item, item.copy(episodeIndex = 1)), detail, 0, 0))
    }
    @Test fun missing_work_episode_or_variant_identity_is_refused() {
        assertNull(ProbeIdentity.resolve(catalog(item.copy(target = a.copy(episodeId = null))), detail, 0, 0))
        assertNull(ProbeIdentity.resolve(catalog(item.copy(target = a.copy(variantId = null))), detail, 0, 0))
        assertNull(ProbeIdentity.resolve(catalog(item), detail.copy(movie = detail.movie.copy(id = "")), 0, 0))
    }
}
