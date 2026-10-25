package com.ichichuang.cinema.feature.detail

import com.ichichuang.cinema.domain.filter.StrictWorkFacts
import com.ichichuang.cinema.domain.model.catalog.ContentKind
import com.ichichuang.cinema.domain.model.catalog.EpisodeId
import com.ichichuang.cinema.domain.model.catalog.EvidenceReference
import com.ichichuang.cinema.domain.model.catalog.FieldEvidence
import com.ichichuang.cinema.domain.model.catalog.FactIds
import com.ichichuang.cinema.domain.model.catalog.SourceId
import com.ichichuang.cinema.domain.model.catalog.VariantId
import com.ichichuang.cinema.domain.model.catalog.WorkId
import com.ichichuang.cinema.domain.model.userdata.PlaybackSnapshot
import com.ichichuang.cinema.domain.model.userdata.WatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R15 详情投影 JVM 断言：身份、事实诚实性与线路/剧集键控（P/E/A 行的 JVM 侧证据）。 */
class DetailProjectionTest {
    private val reference = EvidenceReference(SourceId("test-source"), null, "测试私有文件", "round15-fixture")

    private fun facts(
        year: FieldEvidence<Int> = FieldEvidence.Confirmed(1994, reference),
        languages: FieldEvidence<FactIds> = FieldEvidence.Confirmed(FactIds.of(setOf("ja")), reference),
        countries: FieldEvidence<FactIds> = FieldEvidence.Confirmed(FactIds.of(setOf("JP")), reference),
        genres: FieldEvidence<FactIds> = FieldEvidence.Confirmed(FactIds.of(setOf("horror")), reference),
        kind: FieldEvidence<ContentKind> = FieldEvidence.Confirmed(ContentKind.MOVIE, reference),
    ) = StrictWorkFacts(year, countries, genres, kind, languages, languages)

    private fun work(
        id: String = "w-1", title: String = "午夜凶铃", yr: FieldEvidence<Int> = FieldEvidence.Confirmed(1994, reference),
        lang: FieldEvidence<FactIds> = FieldEvidence.Confirmed(FactIds.of(setOf("ja")), reference),
    ) = DetailWorkEvidence(WorkId(id), title, facts(year = yr, languages = lang))

    private fun episode(id: String, label: String? = null, order: Int? = null, uri: String? = "file:///tmp/a.wav") =
        DetailFixtureEpisode(EpisodeId(id), label, order, uri)

    private fun route(
        id: String, label: String = "备用线路", state: DetailRouteState = DetailRouteState.AVAILABLE,
        episodes: List<DetailFixtureEpisode> = listOf(episode("ep-1", "第一夜", 1)),
        audio: Set<String> = emptySet(),
    ) = DetailFixtureRoute(VariantId(id), "entry-$id", SourceId("test-source"), "item-$id", label, state,
        episodes, audio, observedAt = 5, ruleVersion = "round15")

    private fun resume(variant: String, episode: String, position: Long = 8_000, work: String = "w-1") =
        PlaybackSnapshot(WorkId(work), EpisodeId(episode), VariantId(variant), "session-a", 1, position, 12_000,
            WatchState.IN_PROGRESS, 1)

    private fun project(
        w: DetailWorkEvidence = work(), routes: List<DetailFixtureRoute>, favorite: Boolean = false,
        resume: PlaybackSnapshot? = null, selectedRouteId: String? = null, selectedEpisodeId: String? = null,
    ) = DetailProjection.project(w, routes, favorite, resume, selectedRouteId, selectedEpisodeId)

    // P01 同题同年不同稳定身份
    @Test fun canonical_work_id_drives_detail() {
        val left = project(work(id = "w-1", title = "同名之作"), listOf(route("ra"), route("rb")))
        val right = project(work(id = "w-2", title = "同名之作"), listOf(route("rc"), route("rd")))
        assertNotEquals(left.workId, right.workId)
        assertEquals(listOf("ra", "rb"), left.routes.map { it.routeId })
        assertEquals(listOf("rc", "rd"), right.routes.map { it.routeId })
    }

    // P02 证据与来源绑定
    @Test fun source_entries_are_provenance_bound() {
        val state = project(routes = listOf(route("ra", state = DetailRouteState.STALE).copy(sourceId = SourceId("mirror-source"))))
        assertEquals("mirror-source", state.routes.single().sourceId)
        assertTrue(state.routes.single().stateText.isNotBlank())
    }

    // P03 未知年份不填值
    @Test fun unknown_year_is_not_filled() {
        val state = project(w = work(yr = FieldEvidence.Unknown()), routes = listOf(route("ra")))
        assertEquals(DetailFactStatus.UNKNOWN, state.year.status)
        assertEquals("年份未知", state.year.text)
        assertFalse(state.year.text.any { it.isDigit() })
    }

    // P04 冲突事实不静默裁决
    @Test fun conflicted_fields_are_not_silent_facts() {
        val conflict = FieldEvidence.Conflict.of(listOf(
            FieldEvidence.SourceReported(FactIds.of(setOf("ja")), reference),
            FieldEvidence.SourceReported(FactIds.of(setOf("ko")), reference)))
        val state = project(w = work(lang = conflict), routes = listOf(route("ra")))
        assertEquals(DetailFactStatus.CONFLICT, state.originalLanguages.status)
        assertTrue(state.originalLanguages.text.contains("冲突"))
        assertFalse(state.originalLanguages.text.contains("ja"))
    }

    // P05 音轨/字幕不改变作品语言
    @Test fun audio_subtitles_not_work_language() {
        val state = project(routes = listOf(route("ra", audio = setOf("zh"))))
        assertEquals("ja", state.originalLanguages.text)
        assertFalse(state.allTexts().any { it.contains("zh") && it != state.routes.single().label })
    }

    // P06 + E01 同作品全部线路独立可寻址（同标签不合并）
    @Test fun same_work_all_distinct_routes_preserved() {
        val state = project(routes = listOf(
            route("ra", label = "同样名字的线路"), route("rb", label = "同样名字的线路")))
        assertEquals(2, state.routes.size)
        assertEquals(listOf("ra", "rb"), state.routes.map { it.routeId })
        assertTrue(state.routes.all { it.label == "同样名字的线路" })
    }

    // P08 过期/停用/不可达不冒充可播
    @Test fun source_status_freshness_disclosed() {
        val state = project(routes = listOf(route("ra", state = DetailRouteState.STALE)), selectedRouteId = "ra")
        assertFalse(state.routes.single().available)
        assertFalse(state.primaryActionEnabled)
        assertTrue(state.primaryActionExplanation?.contains("过期") == true)
    }

    // P09 看过最新集不等于全剧完结，不造总数/评分
    @Test fun not_finished_without_work_evidence() {
        val state = project(routes = listOf(route("ra", episodes = listOf(
            episode("ep-1", "第一夜", 1), episode("ep-2", "第二夜", 2)))),
            resume = resume("ra", "ep-2"))
        assertEquals(DetailPrimaryAction.RESUME, state.primaryAction)
        assertTrue(state.allTexts().none { it.contains("看完") || it.contains("完结") || it.contains("评分") || it.contains("豆瓣") })
        assertEquals("本线路 2 集", state.routes.single().episodeCountText)
    }

    // E04 进度按版本独立键控
    @Test fun episode_progress_is_variant_scoped() {
        val state = project(routes = listOf(
            route("ra", episodes = listOf(episode("ep-1", "第一夜", 1))),
            route("rb", episodes = listOf(episode("ep-1", "第一夜", 1)))),
            resume = resume("ra", "ep-1"), selectedRouteId = "rb", selectedEpisodeId = "ep-1")
        assertEquals("rb", state.selectedRouteId)
        assertNull(state.episodes.single().resumeText)
        assertEquals(DetailPrimaryAction.PLAY, state.primaryAction)
    }

    // E05 未知集数不按序号捏造
    @Test fun unknown_episode_number_no_fabrication() {
        val state = project(routes = listOf(route("ra", episodes = listOf(episode("ep-1")))))
        assertEquals("未提供集数名", state.episodes.single().label)
        assertNull(state.episodes.single().orderText)
        assertFalse(state.episodes.single().label.contains("第"))
    }

    // E05 空清单不造“第 1 集”
    @Test fun empty_routes_no_fake_episode_one() {
        val state = project(routes = listOf(route("ra", episodes = emptyList())))
        assertTrue(state.episodes.isEmpty())
        assertFalse(state.primaryActionEnabled)
        assertTrue(state.primaryActionExplanation?.contains("剧集") == true)
        assertNull(state.routes.single().episodeCountText)
    }

    // E07 换线不携带旧版本 8000ms 检查点
    @Test fun switch_line_does_not_copy_resume() {
        val state = project(routes = listOf(
            route("ra", episodes = listOf(episode("ep-1", "第一夜", 1), episode("ep-2", "第二夜", 2))),
            route("rb", episodes = listOf(episode("ep-1", "第一夜", 1)))),
            resume = resume("ra", "ep-2", position = 8_000), selectedRouteId = "rb", selectedEpisodeId = "ep-1")
        assertEquals("rb", state.selectedRouteId)
        assertEquals(DetailPrimaryAction.PLAY, state.primaryAction)
        assertNull(state.episodes.single().resumeText)
        assertTrue(state.primaryActionText.contains("播放"))
    }

    // E07 反向：回到精确版本恢复“继续播放”
    @Test fun exact_variant_restores_resume_action() {
        val state = project(routes = listOf(
            route("ra", episodes = listOf(episode("ep-1", "第一夜", 1), episode("ep-2", "第二夜", 2))),
            route("rb", episodes = listOf(episode("ep-1", "第一夜", 1)))),
            resume = resume("ra", "ep-2", position = 8_000), selectedRouteId = "ra", selectedEpisodeId = "ep-2")
        assertEquals(DetailPrimaryAction.RESUME, state.primaryAction)
        assertTrue(state.primaryActionText.contains("继续播放"))
        assertEquals("上次看到 00:08", state.episodes.first { it.episodeId == "ep-2" }.resumeText)
    }

    // A05 不可用线路不自动回退到别的线路
    @Test fun unavailable_play_disables_without_fallback() {
        val state = project(routes = listOf(
            route("ra", state = DetailRouteState.DISABLED),
            route("rb", state = DetailRouteState.AVAILABLE)),
            selectedRouteId = "ra")
        assertEquals("ra", state.selectedRouteId)
        assertFalse(state.primaryActionEnabled)
        assertTrue(state.primaryActionExplanation?.contains("停用") == true)
    }

    // 默认选择：恢复精确版本优先，否则首选可核实线路
    @Test fun default_selection_prefers_resume_then_available() {
        val routes = listOf(
            route("ra", state = DetailRouteState.DISABLED, episodes = listOf(episode("ep-9"))),
            route("rb", episodes = listOf(episode("ep-1", "第一夜", 1))))
        assertEquals("rb" to "ep-1", DetailProjection.defaultSelection(routes, null))
        assertEquals("ra" to "ep-9", DetailProjection.defaultSelection(routes, resume("ra", "ep-9")))
        val allDead = listOf(route("rd", state = DetailRouteState.UNREACHABLE))
        assertEquals("rd", DetailProjection.defaultSelection(allDead, null).first)
    }

    // 收藏文案来自实际状态
    @Test fun favorite_text_reflects_state() {
        assertEquals("收藏", project(routes = listOf(route("ra")), favorite = false).favoriteText)
        assertEquals("取消收藏", project(routes = listOf(route("ra")), favorite = true).favoriteText)
    }

    // 无线路时整体诚实空态
    @Test fun no_routes_truthful_state() {
        val state = project(routes = emptyList())
        assertTrue(state.routes.isEmpty())
        assertFalse(state.primaryActionEnabled)
        assertTrue(state.availabilityText.contains("线路"))
    }
}
