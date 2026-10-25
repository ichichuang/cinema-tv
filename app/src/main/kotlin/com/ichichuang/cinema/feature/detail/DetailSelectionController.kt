package com.ichichuang.cinema.feature.detail

import com.ichichuang.cinema.domain.model.catalog.WorkId

/**
 * R15 选择控制器：纯状态机（无 Android 依赖），管理动作/线路/集数三层焦点、
 * 弹层陷阱、稳定身份选择与刷新令牌。焦点（focusedKey）与选择
 * （selectedRouteId/selectedEpisodeId）永远分立；用户输入使一切迟到恢复失效。
 */
internal enum class DetailSheetKind { LINES, EPISODES }

internal enum class DetailDirection { UP, DOWN, LEFT, RIGHT }

internal sealed interface DetailCommand {
    data object None : DetailCommand
    data class OpenSheet(val kind: DetailSheetKind) : DetailCommand
    data class SelectRoute(val routeId: String) : DetailCommand
    data class SelectEpisode(val episodeId: String) : DetailCommand
    data class RequestPlay(val routeId: String, val episodeId: String) : DetailCommand
    data object RequestFavorite : DetailCommand
    data object RequestBack : DetailCommand
    data object SheetDismissed : DetailCommand
    data class RouteRemoved(val nearestRouteId: String?) : DetailCommand
}

internal class DetailSelectionController {
    val actionKeys = listOf("播放操作", "选择线路", "选择集数", "收藏操作", "详情返回")

    var workId: WorkId? = null
        private set
    var routes: List<DetailFixtureRoute> = emptyList()
        private set
    var selectedRouteId: String? = null
        private set
    var selectedEpisodeId: String? = null
        private set
    var focusedKey: String? = null
        private set
    var sheet: DetailSheetKind? = null
        private set
    var sheetFocusedKey: String? = null
        private set
    var pendingRestoreKey: String? = null
        private set
    var inputEpoch: Long = 0
        private set
    private var refreshToken: Long = 0
    private var refreshOwner: Long = 0

    fun install(workId: WorkId, routes: List<DetailFixtureRoute>, selectedRouteId: String?, selectedEpisodeId: String?, focusKey: String?) {
        this.workId = workId
        this.routes = routes
        this.selectedRouteId = selectedRouteId
        this.selectedEpisodeId = selectedEpisodeId
        focusedKey = focusKey?.takeIf { it in zoneKeys(it) || it in actionKeys } ?: actionKeys.first()
        sheet = null; sheetFocusedKey = null; pendingRestoreKey = focusKey
    }

    fun lineKeys(): List<String> = routes.map { "线路:${it.routeId.value}" }
    fun episodeKeys(): List<String> = selectedRoute()?.episodes?.map { "集数:${it.episodeId.value}" } ?: emptyList()
    fun sheetKeys(): List<String> = when (sheet) {
        DetailSheetKind.LINES -> lineKeys().map { it.replace("线路:", "线路单:") }
        DetailSheetKind.EPISODES -> episodeKeys().map { it.replace("集数:", "集数单:") }
        null -> emptyList()
    }

    private fun selectedRoute() = routes.firstOrNull { it.routeId.value == selectedRouteId }
    private fun zoneKeys(key: String?): List<String> = when {
        key == null -> emptyList()
        key.startsWith("线路:") -> lineKeys()
        key.startsWith("集数:") -> episodeKeys()
        else -> actionKeys
    }

    /** 任何真实用户移动都使迟到的焦点恢复请求失效（N04）。 */
    private fun userInput() { inputEpoch++; pendingRestoreKey = null }

    fun move(direction: DetailDirection): Boolean {
        val sheetKeys = sheetKeys()
        if (sheet != null) {
            if (direction != DetailDirection.UP && direction != DetailDirection.DOWN) return true
            val index = sheetKeys.indexOf(sheetFocusedKey).coerceAtLeast(0)
            val next = when (direction) {
                DetailDirection.UP -> index - 1; else -> index + 1
            }
            if (next in sheetKeys.indices) { userInput(); sheetFocusedKey = sheetKeys[next] }
            return true
        }
        val key = focusedKey ?: actionKeys.first()
        val zone = zoneKeys(key)
        val index = zone.indexOf(key)
        fun jump(target: String?) { if (target != null) { userInput(); focusedKey = target } }
        return when {
            key in actionKeys -> when (direction) {
                DetailDirection.LEFT -> { jump(actionKeys.getOrNull((index - 1).coerceAtLeast(0))); true }
                DetailDirection.RIGHT -> { jump(actionKeys.getOrNull((index + 1).coerceAtMost(actionKeys.lastIndex))); true }
                DetailDirection.UP -> true
                DetailDirection.DOWN -> {
                    val lines = lineKeys(); val episodes = episodeKeys()
                    // 动作行下移优先线路；无线路但有剧集（不可能出现，但保持确定性）则进剧集。
                    jump(lines.firstOrNull() ?: episodes.firstOrNull()); true
                }
            }
            key.startsWith("线路:") -> when (direction) {
                DetailDirection.LEFT -> { jump(lineKeys().getOrNull((index - 1).coerceAtLeast(0))); true }
                DetailDirection.RIGHT -> { jump(lineKeys().getOrNull((index + 1).coerceAtMost(lineKeys().lastIndex))); true }
                DetailDirection.UP -> { jump(actionKeys.getOrNull(index.coerceAtMost(actionKeys.lastIndex))); true }
                DetailDirection.DOWN -> { jump(episodeKeys().firstOrNull()); true }
            }
            key.startsWith("集数:") -> when (direction) {
                DetailDirection.UP -> {
                    val episodes = episodeKeys()
                    if (index <= 0) jump(lineKeys().getOrNull(0) ?: actionKeys.first()) else jump(episodes[index - 1]); true
                }
                DetailDirection.DOWN -> { jump(episodeKeys().getOrNull((index + 1).coerceAtMost(episodeKeys().lastIndex))); true }
                else -> true
            }
            else -> false
        }
    }

    fun confirm(): DetailCommand {
        sheet?.let { open ->
            val key = sheetFocusedKey ?: sheetKeys().firstOrNull() ?: return DetailCommand.None
            val id = key.substringAfter(':')
            return when (open) {
                DetailSheetKind.LINES -> selectRoute(id).also { closeSheetToOpener() }
                DetailSheetKind.EPISODES -> selectEpisode(id).also { closeSheetToOpener() }
            }
        }
        val key = focusedKey ?: return DetailCommand.None
        return confirmOn(key)
    }

    fun confirmOn(key: String): DetailCommand {
        focusedKey = key
        return when {
            key == "播放操作" -> {
                val route = selectedRouteId; val episode = selectedEpisodeId
                if (route == null || episode == null) DetailCommand.None else DetailCommand.RequestPlay(route, episode)
            }
            key == "选择线路" -> openSheet(DetailSheetKind.LINES)
            key == "选择集数" -> openSheet(DetailSheetKind.EPISODES)
            key == "收藏操作" -> DetailCommand.RequestFavorite
            key == "详情返回" -> DetailCommand.RequestBack
            key.startsWith("线路:") -> selectRoute(key.substringAfter(':'))
            key.startsWith("集数:") -> selectEpisode(key.substringAfter(':'))
            else -> DetailCommand.None
        }
    }

    private fun openSheet(kind: DetailSheetKind): DetailCommand {
        sheet = kind
        sheetFocusedKey = when (kind) {
            DetailSheetKind.LINES -> "线路单:$selectedRouteId"
            DetailSheetKind.EPISODES -> "集数单:$selectedEpisodeId"
        }.takeIf { it in sheetKeys() } ?: sheetKeys().firstOrNull()
        return DetailCommand.OpenSheet(kind)
    }

    private fun closeSheetToOpener() {
        val opener = focusedKey?.takeIf { it in actionKeys || it.startsWith("线路:") || it.startsWith("集数:") } ?: actionKeys.first()
        sheet = null; sheetFocusedKey = null
        pendingRestoreKey = opener
    }

    fun back(): DetailCommand {
        if (sheet != null) { closeSheetToOpener(); return DetailCommand.SheetDismissed }
        return DetailCommand.RequestBack
    }

    fun selectRoute(routeId: String): DetailCommand {
        if (routes.none { it.routeId.value == routeId }) return DetailCommand.None
        userInput()
        selectedRouteId = routeId
        // 新线路的默认剧集只从该线路自己的清单取（优先已核实可播），绝不沿用其他线路选择。
        selectedEpisodeId = routes.firstOrNull { it.routeId.value == routeId }?.episodes
            ?.firstOrNull { it.uri != null }?.episodeId?.value
            ?: routes.firstOrNull { it.routeId.value == routeId }?.episodes?.firstOrNull()?.episodeId?.value
        return DetailCommand.SelectRoute(routeId)
    }

    /** 恢复路径直接落点（非用户输入，不推进代次）：屏幕应用迟到/弹层恢复时调用。 */
    fun overrideFocus(key: String) {
        if (sheet != null) { if (key in sheetKeys()) sheetFocusedKey = key; return }
        if (key.startsWith("线路:") || key.startsWith("集数:") || key in actionKeys) focusedKey = key
    }

    fun selectEpisode(episodeId: String): DetailCommand {
        val exists = selectedRoute()?.episodes?.any { it.episodeId.value == episodeId } == true
        if (!exists) return DetailCommand.None
        userInput()
        selectedEpisodeId = episodeId
        return DetailCommand.SelectEpisode(episodeId)
    }

    /** 发起线路/剧集刷新，返回令牌；只有最新令牌的完成才被接受（E10）。 */
    fun beginRefresh(): Long { refreshToken++; refreshOwner = refreshToken; return refreshToken }

    fun completeRefresh(token: Long, routes: List<DetailFixtureRoute>): DetailCommand.RouteRemoved? {
        if (token != refreshOwner) return null
        val oldRoutes = this.routes
        val oldSelected = selectedRouteId
        this.routes = routes
        var nearest: String? = null
        if (oldSelected != null && routes.none { it.routeId.value == oldSelected }) {
            // 顶替位置：被移除线路的旧索引在新列表中对应的稳定线路（N07），不跳无关位置。
            val oldIndex = oldRoutes.indexOfFirst { it.routeId.value == oldSelected }
            nearest = routes.getOrNull(oldIndex)?.routeId?.value ?: routes.lastOrNull()?.routeId?.value
            selectedRouteId = nearest ?: DetailProjection.defaultSelection(routes, null).first
            selectedEpisodeId = null
        }
        if (selectedEpisodeId != null && selectedRoute()?.episodes?.none { it.episodeId.value == selectedEpisodeId } == true)
            selectedEpisodeId = null
        // 焦点线路若随刷新消失：恢复目标也是“顶替位置”的稳定线路（N07），不取首项。
        val focus = focusedKey
        if (focus != null && focus.startsWith("线路:") &&
            routes.none { "线路:${it.routeId.value}" == focus }) {
            val focusIndex = oldRoutes.indexOfFirst { "线路:${it.routeId.value}" == focus }
            val focusReplacement = (if (focusIndex >= 0) routes.getOrNull(focusIndex) else null)?.routeId?.value
                ?: nearest ?: lineKeys().firstOrNull()?.substringAfter("线路:") ?: actionKeys.first()
            pendingRestoreKey = "线路:$focusReplacement"
        }
        if (sheet == DetailSheetKind.LINES && routes.isEmpty()) { sheet = null; sheetFocusedKey = null }
        return DetailCommand.RouteRemoved(nearest)
    }

    fun consumeRestore(): String? { val value = pendingRestoreKey; pendingRestoreKey = null; return value }
}
