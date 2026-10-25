package com.ichichuang.cinema.feature.home.visual

import com.ichichuang.cinema.domain.model.catalog.CatalogItem
import com.ichichuang.cinema.domain.model.catalog.CatalogResult
import com.ichichuang.cinema.domain.model.catalog.QueryCoverage
import com.ichichuang.cinema.domain.model.catalog.SourceId
import com.ichichuang.cinema.domain.model.catalog.WorkId
import org.junit.Assert.*
import org.junit.Test

/** 首页纯投影的真实性规则（H01–H13 的 JVM 部分；不涉及任何存储写入）。 */
class HomeProjectionTest {
    private fun result(coverage: QueryCoverage = QueryCoverage.COMPLETE, hasMore: Boolean = false,
        failed: Set<SourceId> = emptySet(), known: Long? = 3) = CatalogResult.create("q1",
        (1..3).map { CatalogItem(WorkId("w$it"), "合成作品$it") }, coverage, failedSourceIds = failed,
        knownMatchedCount = known, hasMore = hasMore, snapshotRevision = 7)

    private fun catalog(vararg ids: String = arrayOf("w1", "w2", "w3")) =
        ids.map { HomeCatalogEntry(it, "合成作品$it", null, HomeImageShape.UNKNOWN) }

    private fun entry(id: String, inProgress: Boolean = false, position: Long? = null, duration: Long? = null,
        episode: String? = null, completed: Boolean = false) =
        HomePersonalEntry(id, "已保存作品$id", "测试来源", episode, "v1", position, duration, inProgress, completed)

    @Test fun no_sources_does_not_create_fake_hero() {
        val state = HomeProjection.project(catalog(), result(), sourcesConfigured = false,
            continueEntries = listOf(entry("w1", inProgress = true, position = 90_000, duration = 120_000)),
            recentEntries = emptyList(), offline = false, loadFailed = false, loading = false)
        assertEquals(HomeContentStatus.UNCONFIGURED, state.contentStatus)
        assertTrue("未配置来源时仍应显示真实续播记录", state.continueCards.isNotEmpty())
        assertEquals("w1", state.continueCards.single().workId)
        assertTrue("未配置来源不得虚构目录内容", state.catalogCards.isNotEmpty() || state.totalKnown == null)
        assertNull("未配置来源不得给出总数", state.totalKnown)
    }

    @Test fun offline_still_shows_saved_continue_and_recent() {
        val state = HomeProjection.project(catalog(), result(), sourcesConfigured = true,
            continueEntries = listOf(entry("w1", inProgress = true, position = 60_000, duration = 120_000)),
            recentEntries = listOf(entry("w2")), offline = true, loadFailed = false, loading = false)
        assertEquals(HomeContentStatus.OFFLINE, state.contentStatus)
        assertEquals(listOf("w1"), state.continueCards.map { it.workId })
        assertEquals(listOf("w2"), state.recentCards.map { it.workId })
    }

    @Test fun partial_coverage_does_not_claim_global_total() {
        val state = HomeProjection.project(catalog(), result(coverage = QueryCoverage.PARTIAL, hasMore = true, known = null),
            sourcesConfigured = true, continueEntries = emptyList(), recentEntries = emptyList(),
            offline = false, loadFailed = false, loading = false)
        assertEquals(HomeContentStatus.PARTIAL, state.contentStatus)
        assertTrue(state.hasMore)
        assertNull("部分覆盖不得冒充全库总数", state.totalKnown)
    }

    @Test fun empty_local_page_does_not_claim_query_complete() {
        val state = HomeProjection.project(catalog(), result(coverage = QueryCoverage.PARTIAL, hasMore = true, known = null),
            sourcesConfigured = true, continueEntries = emptyList(), recentEntries = emptyList(),
            offline = false, loadFailed = false, loading = false)
        assertNotEquals(HomeContentStatus.READY, state.contentStatus)
        assertEquals(HomeContentStatus.PARTIAL, state.contentStatus)
    }

    @Test fun complete_empty_shows_truthful_empty_state() {
        val completeEmpty = CatalogResult.create("q1", emptyList(), QueryCoverage.COMPLETE,
            knownMatchedCount = 0, snapshotRevision = 7)
        val state = HomeProjection.project(emptyList(), completeEmpty, sourcesConfigured = true,
            continueEntries = emptyList(), recentEntries = emptyList(), offline = false, loadFailed = false, loading = false)
        assertEquals(HomeContentStatus.READY, state.contentStatus)
        assertEquals(0, state.totalKnown)
        assertTrue(state.catalogCards.isEmpty())
    }

    @Test fun unknown_title_or_poster_does_not_fabricate_metadata() {
        val state = HomeProjection.project(listOf(HomeCatalogEntry("w9", "已保存作品w9", null, HomeImageShape.UNKNOWN)),
            result(), sourcesConfigured = true, continueEntries = emptyList(),
            recentEntries = listOf(HomePersonalEntry("w8", "已保存作品w8", null, null, null, null, null, false, false)),
            offline = false, loadFailed = false, loading = false)
        val unknown = state.recentCards.single()
        assertEquals("w8", unknown.workId)
        assertNull("无来源标签时不得编造来源", unknown.secondaryText)
        assertNull("无图不得编造图", unknown.artworkUri)
        assertEquals(HomeImageShape.UNKNOWN, unknown.imageShape)
    }

    @Test fun work_id_survives_multi_source_change() {
        val before = HomeProjection.project(catalog("w1"), result(), sourcesConfigured = true,
            continueEntries = listOf(entry("w1", inProgress = true)), recentEntries = emptyList(),
            offline = false, loadFailed = false, loading = false)
        val after = HomeProjection.project(listOf(HomeCatalogEntry("w1", "合成作品1-换源", "file:///new.png", HomeImageShape.PORTRAIT)),
            result(), sourcesConfigured = true, continueEntries = listOf(entry("w1", inProgress = true)),
            recentEntries = emptyList(), offline = false, loadFailed = false, loading = false)
        assertEquals("换源/换图不得改写稳定作品 ID", before.continueCards.single().workId, after.continueCards.single().workId)
        assertEquals("file:///new.png", after.continueCards.single().artworkUri)
    }

    @Test fun progress_card_uses_acknowledged_episode_variant_and_position() {
        val state = HomeProjection.project(catalog(), result(), sourcesConfigured = true,
            continueEntries = listOf(entry("w1", inProgress = true, position = 8_000, duration = 12_000, episode = "round12-episode-2")),
            recentEntries = emptyList(), offline = false, loadFailed = false, loading = false)
        val progress = state.continueCards.single().progressText
        assertNotNull(progress)
        assertTrue("进度文案必须含已确认剧集", progress!!.contains("round12-episode-2"))
        assertTrue("进度文案必须含已确认位置", progress.contains("0:08"))
        assertTrue("进度文案必须含已知总长", progress.contains("0:12"))
    }

    @Test fun unknown_duration_keeps_position_without_total() {
        val state = HomeProjection.project(catalog(), result(), sourcesConfigured = true,
            continueEntries = listOf(entry("w1", inProgress = true, position = 8_000, duration = null, episode = null)),
            recentEntries = emptyList(), offline = false, loadFailed = false, loading = false)
        val progress = state.continueCards.single().progressText!!
        assertTrue(progress.contains("0:08"))
        assertFalse("未知时长不得显示总量", progress.contains("/"))
    }

    @Test fun completed_episode_text_does_not_claim_series_finished() {
        val state = HomeProjection.project(catalog(), result(), sourcesConfigured = true,
            continueEntries = listOf(entry("w1", inProgress = true, completed = true)),
            recentEntries = emptyList(), offline = false, loadFailed = false, loading = false)
        assertEquals("本集已看完", state.continueCards.single().progressText)
    }

    @Test fun watch_browsing_does_not_mutate_history() {
        val recent = listOf(entry("w2"))
        val first = HomeProjection.project(catalog(), result(), sourcesConfigured = true,
            continueEntries = emptyList(), recentEntries = recent, offline = false, loadFailed = false, loading = false)
        val second = HomeProjection.project(catalog(), result(), sourcesConfigured = true,
            continueEntries = listOf(entry("w1", inProgress = true)), recentEntries = recent,
            offline = false, loadFailed = false, loading = false)
        assertEquals("投影不得改写历史输入", first.recentCards, second.recentCards)
    }

    @Test fun failed_sources_are_counted_not_hidden() {
        val state = HomeProjection.project(catalog(),
            result(coverage = QueryCoverage.PARTIAL, failed = setOf(SourceId("s1"), SourceId("s2")), known = null),
            sourcesConfigured = true, continueEntries = emptyList(), recentEntries = emptyList(),
            offline = false, loadFailed = false, loading = false)
        assertEquals(HomeContentStatus.PARTIAL, state.contentStatus)
        assertEquals(2, state.failedSourceCount)
    }

    @Test fun read_failure_shows_failed_and_keeps_loading_false() {
        val state = HomeProjection.project(catalog(), null, sourcesConfigured = true,
            continueEntries = listOf(entry("w1", inProgress = true)), recentEntries = emptyList(),
            offline = false, loadFailed = true, loading = false)
        assertEquals(HomeContentStatus.FAILED, state.contentStatus)
        assertTrue("读取失败时个人记录仍在", state.continueCards.isNotEmpty())
    }

    @Test fun loading_is_truthful_before_first_result() {
        val state = HomeProjection.project(catalog(), null, sourcesConfigured = false,
            continueEntries = emptyList(), recentEntries = emptyList(), offline = true, loadFailed = false, loading = true)
        assertEquals(HomeContentStatus.LOADING, state.contentStatus)
    }

    @Test fun read_failure_outranks_loading_flag() {
        val state = HomeProjection.project(catalog(), null, sourcesConfigured = true,
            continueEntries = emptyList(), recentEntries = emptyList(), offline = false, loadFailed = true, loading = true)
        assertEquals(HomeContentStatus.FAILED, state.contentStatus)
    }
}
