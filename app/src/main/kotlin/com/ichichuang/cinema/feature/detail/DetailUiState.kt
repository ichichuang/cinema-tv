package com.ichichuang.cinema.feature.detail

import com.ichichuang.cinema.domain.filter.StrictWorkFacts
import com.ichichuang.cinema.domain.model.catalog.EpisodeId
import com.ichichuang.cinema.domain.model.catalog.SourceId
import com.ichichuang.cinema.domain.model.catalog.VariantId
import com.ichichuang.cinema.domain.model.catalog.WorkId

/**
 * R15 作品详情切片的纯显示契约：只呈现已有证据，缺失/冲突/未核实各自成态，
 * 绝不把未知 coerce 成像真的值。全部为不可变数据，供投影（DetailProjection）
 * 输出、Compose 屏幕消费。
 */

/** 单条事实的呈现态：已证实／来源报告／未知／已知为空／证据冲突。 */
internal enum class DetailFactStatus { VERIFIED, REPORTED, UNKNOWN, KNOWN_EMPTY, CONFLICT }

internal data class DetailFactUi(val text: String, val status: DetailFactStatus)

/** 线路（播放路由）证据状态：来源停用／不可达／过期／当前可核实。 */
internal enum class DetailRouteState { AVAILABLE, DISABLED, UNREACHABLE, STALE }

/** 私有测试档案中的线路剧集声明：身份与来源标签均为声明值，缺省保持缺省。 */
internal data class DetailFixtureEpisode(
    val episodeId: EpisodeId,
    val label: String?,
    val declaredOrder: Int?,
    val uri: String?,
)

/** 私有测试档案中的线路（源条目 + 播放版本）声明。 */
internal data class DetailFixtureRoute(
    val routeId: VariantId,
    val entryId: String,
    val sourceId: SourceId,
    val sourceItemId: String,
    val label: String,
    val state: DetailRouteState,
    val episodes: List<DetailFixtureEpisode>,
    /** 线路音轨声明只描述线路本身，绝不改写作品语言事实（P05）。 */
    val audioLanguages: Set<String> = emptySet(),
    val observedAt: Long = 0,
    val ruleVersion: String = "",
)

internal data class DetailFixtureWork(val workId: WorkId, val routes: List<DetailFixtureRoute>)

/** 投影输入：作品证据（来自 R08 严格事实），与线路证据分开传入。 */
internal data class DetailWorkEvidence(
    val workId: WorkId,
    val title: String,
    val facts: StrictWorkFacts,
    val artworkUri: String? = null,
)

internal data class DetailRouteUi(
    val routeId: String,
    val label: String,
    val sourceId: String,
    val state: DetailRouteState,
    val stateText: String,
    val available: Boolean,
    /** 仅当线路自带剧集清单时呈现“本线路 N 集”；清单未提供即为 null，不伪造总数。 */
    val episodeCountText: String?,
)

internal data class DetailEpisodeUi(
    val episodeId: String,
    val label: String,
    val orderText: String?,
    val playable: Boolean,
    /** 仅当已提交进度精确匹配（作品, 剧集, 版本）时呈现，绝不跨版本携带。 */
    val resumeText: String?,
)

internal enum class DetailPrimaryAction { RESUME, PLAY }

internal data class DetailUiState(
    val workId: WorkId,
    val title: String,
    val year: DetailFactUi,
    val originalLanguages: DetailFactUi,
    val countries: DetailFactUi,
    val genres: DetailFactUi,
    val contentKind: DetailFactUi,
    val artworkUri: String?,
    val routes: List<DetailRouteUi>,
    val selectedRouteId: String?,
    val selectedEpisodeId: String?,
    val episodes: List<DetailEpisodeUi>,
    val primaryAction: DetailPrimaryAction?,
    val primaryActionText: String,
    val primaryActionEnabled: Boolean,
    val primaryActionExplanation: String?,
    val favoriteText: String,
    val availabilityText: String,
) {
    /** L06 诚实性自检：所有呈现文本里不得出现伪造的评分/全局总数/完结声明。 */
    fun allTexts(): List<String> = listOf(title, year.text, originalLanguages.text, countries.text,
        genres.text, contentKind.text, availabilityText, primaryActionText) +
        primaryActionExplanation?.let(::listOf).orEmpty() +
        routes.flatMap { listOf(it.label, it.stateText) + it.episodeCountText?.let(::listOf).orEmpty() } +
        episodes.flatMap { listOf(it.label) + it.orderText?.let(::listOf).orEmpty() + it.resumeText?.let(::listOf).orEmpty() }
}
