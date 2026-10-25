package com.ichichuang.cinema.feature.detail

import com.ichichuang.cinema.core.design.formatTime
import com.ichichuang.cinema.domain.model.catalog.ContentKind
import com.ichichuang.cinema.domain.model.catalog.FieldEvidence
import com.ichichuang.cinema.domain.model.catalog.FactIds
import com.ichichuang.cinema.domain.model.userdata.PlaybackSnapshot

/**
 * R15 详情投影：把作品证据、线路证据与个人记录投影为诚实显示状态。
 * 只读、无副作用：不写任何用户记录，也不需要网络。缺失保持缺失，冲突保持冲突。
 */
internal object DetailProjection {
    fun project(
        work: DetailWorkEvidence,
        routes: List<DetailFixtureRoute>,
        favorite: Boolean,
        resume: PlaybackSnapshot?,
        selectedRouteId: String?,
        selectedEpisodeId: String?,
    ): DetailUiState {
        val selection = resolveSelection(routes, resume, selectedRouteId, selectedEpisodeId)
        val route = routes.firstOrNull { it.routeId.value == selection.first }
        val routeUi = routes.map(::routeUi)
        val episodes = route?.episodes.orEmpty().map { episodeUi(it, selection.first ?: it.episodeId.value, resume) }
        val resumeMatchesSelection = resume != null && route != null &&
            resume.variantId?.value == route.routeId.value && resume.episodeId?.value == selection.second &&
            resume.positionMs > 0
        val playable = route?.state == DetailRouteState.AVAILABLE &&
            route.episodes.firstOrNull { it.episodeId.value == selection.second }?.uri != null
        val (primary, primaryText, explanation) = when {
            route == null -> Triple(null, "播放", if (routes.isEmpty()) "当前作品没有可用线路" else "请先选择线路")
            route.episodes.isEmpty() -> Triple(null, "播放", "该线路未提供剧集清单，未核实可播放内容")
            !playable && route.state != DetailRouteState.AVAILABLE -> Triple(null, "播放", routeStateText(route))
            !playable -> Triple(null, "播放", "所选剧集缺少已核实的可播地址")
            resumeMatchesSelection -> Triple(DetailPrimaryAction.RESUME, "继续播放 · ${formatTime(resume!!.positionMs)}", null)
            else -> Triple(DetailPrimaryAction.PLAY, "播放", null)
        }
        return DetailUiState(
            workId = work.workId,
            title = work.title,
            year = factUi(work.facts.year, "年份未知") { it.toString() },
            originalLanguages = factUi(work.facts.originalLanguages, "语言未知") { idsText(it) },
            countries = factUi(work.facts.countries, "地区未知") { idsText(it) },
            genres = factUi(work.facts.genres, "类型未知") { idsText(it) },
            contentKind = factUi(work.facts.contentKind, "类型未知") { kindText(it) },
            artworkUri = work.artworkUri,
            routes = routeUi,
            selectedRouteId = selection.first,
            selectedEpisodeId = selection.second,
            episodes = episodes,
            primaryAction = primary,
            primaryActionText = primaryText,
            primaryActionEnabled = playable,
            primaryActionExplanation = explanation,
            favoriteText = if (favorite) "取消收藏" else "收藏",
            availabilityText = availabilityText(routes),
        )
    }

    /** 默认选择优先精确恢复版本，其次首选当前可核实线路，再退到首条线路如实呈现。 */
    fun defaultSelection(routes: List<DetailFixtureRoute>, resume: PlaybackSnapshot?): Pair<String?, String?> {
        val byResume = resume?.variantId?.value?.let { variant -> routes.firstOrNull { it.routeId.value == variant } }
        val chosen = byResume ?: routes.firstOrNull { it.state == DetailRouteState.AVAILABLE } ?: routes.firstOrNull()
            ?: return null to null
        val episode = chosen.episodes.firstOrNull { it.episodeId.value == resume?.episodeId?.value }
            ?: chosen.episodes.firstOrNull { it.uri != null } ?: chosen.episodes.firstOrNull()
        return chosen.routeId.value to episode?.episodeId?.value
    }

    private fun resolveSelection(
        routes: List<DetailFixtureRoute>, resume: PlaybackSnapshot?,
        selectedRouteId: String?, selectedEpisodeId: String?,
    ): Pair<String?, String?> {
        val route = routes.firstOrNull { it.routeId.value == selectedRouteId }
            ?: return defaultSelection(routes, resume)
        val episode = route.episodes.firstOrNull { it.episodeId.value == selectedEpisodeId }
            ?: route.episodes.firstOrNull { it.episodeId.value == resume?.takeIf { it.variantId?.value == route.routeId.value }?.episodeId?.value }
            ?: route.episodes.firstOrNull { it.uri != null } ?: route.episodes.firstOrNull()
        return route.routeId.value to episode?.episodeId?.value
    }

    private fun routeUi(route: DetailFixtureRoute) = DetailRouteUi(
        routeId = route.routeId.value,
        label = route.label,
        sourceId = route.sourceId.value,
        state = route.state,
        stateText = routeStateText(route),
        available = route.state == DetailRouteState.AVAILABLE,
        episodeCountText = if (route.episodes.isEmpty()) null else "本线路 ${route.episodes.size} 集",
    )

    private fun episodeUi(episode: DetailFixtureEpisode, routeId: String, resume: PlaybackSnapshot?) = DetailEpisodeUi(
        episodeId = episode.episodeId.value,
        label = episode.label ?: "未提供集数名",
        orderText = episode.declaredOrder?.let { "第 $it 集" },
        playable = episode.uri != null,
        resumeText = resume?.takeIf { it.variantId?.value == routeId && it.episodeId?.value == episode.episodeId.value && it.positionMs > 0 }
            ?.let { "上次看到 ${formatTime(it.positionMs)}" },
    )

    private fun routeStateText(route: DetailFixtureRoute) = when (route.state) {
        DetailRouteState.AVAILABLE -> "已核实本地测试线路"
        DetailRouteState.DISABLED -> "该线路已被来源停用"
        DetailRouteState.UNREACHABLE -> "该线路当前不可达，未核实可播放内容"
        DetailRouteState.STALE -> "该线路数据已过期，未核实当前可播放内容"
    }

    private fun availabilityText(routes: List<DetailFixtureRoute>): String {
        if (routes.isEmpty()) return "当前作品没有已核实的线路"
        if (routes.none { it.state == DetailRouteState.AVAILABLE }) return "全部线路当前不可用，未核实可播放内容"
        if (routes.any { it.state != DetailRouteState.AVAILABLE }) return "部分线路不可用或未核实，可用线路以状态为准"
        return "各线路均为本地已核实测试线路"
    }

    private fun <T> factUi(evidence: FieldEvidence<T>, unknownText: String, render: (T) -> String): DetailFactUi =
        when (evidence) {
            is FieldEvidence.Confirmed -> DetailFactUi(render(evidence.value), DetailFactStatus.VERIFIED)
            is FieldEvidence.SourceReported ->
                if (evidence.value is FactIds && (evidence.value as FactIds).values.isEmpty())
                    DetailFactUi("无记录（来源报告为空）", DetailFactStatus.KNOWN_EMPTY)
                else DetailFactUi(render(evidence.value), DetailFactStatus.REPORTED)
            is FieldEvidence.Unknown -> DetailFactUi(unknownText, DetailFactStatus.UNKNOWN)
            is FieldEvidence.Conflict -> DetailFactUi("证据冲突，尚未裁决", DetailFactStatus.CONFLICT)
        }

    private fun idsText(ids: FactIds): String = if (ids.values.isEmpty()) "无记录" else ids.values.joinToString(" / ")
    private fun kindText(kind: ContentKind): String = when (kind) {
        ContentKind.MOVIE -> "电影"
        ContentKind.SERIES -> "剧集"
        ContentKind.VARIETY -> "综艺"
        ContentKind.ANIMATION -> "动画"
        ContentKind.DOCUMENTARY -> "纪录片"
        ContentKind.OTHER -> "其他"
    }
}
