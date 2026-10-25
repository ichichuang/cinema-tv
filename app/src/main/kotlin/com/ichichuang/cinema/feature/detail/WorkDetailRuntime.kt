package com.ichichuang.cinema.feature.detail

import android.content.Context
import android.view.KeyEvent
import androidx.compose.runtime.*
import com.ichichuang.cinema.domain.model.catalog.WorkId
import com.ichichuang.cinema.domain.model.userdata.PlaybackSnapshot
import com.ichichuang.cinema.feature.browse.BrowseProbeFixture
import com.ichichuang.cinema.feature.browse.BrowseProbeRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Private authorization belongs to the disposable profile, never an Intent or bundled asset. */
internal object WorkDetailAuthorization {
    const val FILE = "round15-work-detail.authorization"
    const val TOKEN = "cinema-round15-disposable-work-detail"
    fun requireOwned(context: Context) {
        val file = File(context.noBackupFilesDir, FILE)
        check(file.isFile && file.length() == TOKEN.length.toLong() && file.readText() == TOKEN) {
            "作品详情测试缺少独立授权"
        }
    }
}

/** Bounded native-test hooks; same family as the earlier probe hooks, no external activation. */
internal object WorkDetailTestHooks {
    val activeLeases = AtomicInteger(0)
    /** E10：扣住线路刷新交付，制造迟到/取消的刷新。 */
    var holdRouteRefresh: CompletableDeferred<Unit>? = null
    /** A04/E10：扣住确认派发，验证派发时点校验与迟到回调。 */
    var beforePlayDispatch: CompletableDeferred<Unit>? = null
    val dispatchTrace = java.util.ArrayDeque<String>()
    fun traceDispatch(value: String) { if (dispatchTrace.size >= 32) dispatchTrace.removeFirst(); dispatchTrace.addLast(value) }
    var disableActionFocus = false
    val failedFocusRequests = AtomicInteger(0)
    val focusTrace = java.util.ArrayDeque<String>()
    fun traceFocus(value: String) { if (focusTrace.size >= 32) focusTrace.removeFirst(); focusTrace.addLast(value) }
}

/** 已确认（提交过）的详情选择上下文；未提交的弹层操作绝不落盘。 */
internal class WorkDetailContextStore(private val file: File) {
    fun save(workId: String, routeId: String?, episodeId: String?) {
        file.writeText(JSONObject().put("workId", workId)
            .put("routeId", routeId ?: JSONObject.NULL)
            .put("episodeId", episodeId ?: JSONObject.NULL).toString())
    }
    // org.json 对显式 JSON null 的 optString 给出 "null" 字面量：与 reproject 同一口径，读回时归一化。
    private fun optId(root: JSONObject, key: String): String? =
        root.optString(key).takeUnless { it.isBlank() || it == "null" }
    fun load(): Triple<String, String?, String?>? = runCatching {
        val root = JSONObject(file.readText())
        Triple(root.getString("workId"), optId(root, "routeId"), optId(root, "episodeId"))
    }.getOrNull()
}

/**
 * R15 作品详情运行时：只读投影 + 既有权威的确认派发。
 * 打开/聚焦/换线/取消弹层绝不写用户记录；显式 OK 才经既有
 * 收藏事务与 R06 守卫播放管线派发精确（作品, 剧集, 版本）。
 */
internal class WorkDetailRuntime(
    private val context: Context,
    private val browse: BrowseProbeRuntime,
    private val scope: CoroutineScope,
    private val fixture: BrowseProbeFixture,
) {
    val controller = DetailSelectionController()
    private val detailFixture = WorkDetailFixture(context)
    private val contextStore = WorkDetailContextStore(File(context.noBackupFilesDir, "round15-detail-context.json"))
    private val held = mutableSetOf<Int>()

    var display by mutableStateOf<DetailUiState?>(null); private set
    var focusedKey by mutableStateOf<String?>(null); private set
    var sheet by mutableStateOf<DetailSheetKind?>(null); private set
    var sheetFocusedKey by mutableStateOf<String?>(null); private set
    var restoreRequest by mutableStateOf<String?>(null); private set
    var focusCorrection by mutableIntStateOf(0); private set
    var message by mutableStateOf(""); private set
    var dispatchBusy by mutableStateOf(false); private set
    var routeRefreshBusy by mutableStateOf(false); private set
    var installedWorkId by mutableStateOf<String?>(null); private set

    private val personal get() = browse.personal
    private val workId: WorkId? get() = (browse.selected ?: browse.navigation?.snapshot?.focusedWorkId)

    /** 详情层每次进入/换作品时安装；已确认上下文恢复，未提交弹层状态从不恢复。 */
    fun open() {
        val id = workId ?: return
        if (installedWorkId == id.value) { reproject(); return }
        val routes = detailFixture.routes(id)
        val committed = contextStore.load()?.takeIf { it.first == id.value }
        val fallback = DetailProjection.defaultSelection(routes, resumeFor(id))
        val selectedRoute = committed?.second ?: fallback.first
        val selectedEpisode = committed?.third
            ?: routes.firstOrNull { it.routeId.value == selectedRoute }?.episodes
                ?.firstOrNull { it.episodeId.value == resumeFor(id)?.episodeId?.value }?.episodeId?.value
            ?: fallback.second
        controller.install(id, routes, selectedRoute, selectedEpisode, controller.actionKeys.first())
        installedWorkId = id.value
        message = ""
        reproject()
        // 主操作被禁用（如无可用线路）时初始焦点落到首个可聚焦动作，避免焦点空洞。
        if (display?.primaryActionEnabled == false && controller.focusedKey == "播放操作")
            controller.overrideFocus("选择线路")
        WorkDetailTestHooks.traceDispatch("open:${id.value}")
    }

    private fun resumeFor(id: WorkId): PlaybackSnapshot? =
        personal?.continuing?.firstOrNull { it.workId == id.value }?.snapshot

    private fun reproject() {
        val id = workId ?: return
        if (installedWorkId != id.value) return
        val known = fixture.works.firstOrNull { it.id == id }
        // org.json 对显式 JSON null 的 optString 会给出 "null" 字面量（BrowseProbeFixture 沿用该行为）：
        // 详情层在消费前归一化，缺图就是缺图，绝不让 "null" 伪装成图源地址。
        val artwork = known?.artworkUri?.takeUnless { it.isBlank() || it == "null" }
        val evidence = DetailWorkEvidence(id, known?.title ?: "本地作品记录", known?.facts
            ?: com.ichichuang.cinema.domain.filter.StrictWorkFacts(), artwork)
        display = DetailProjection.project(evidence, controller.routes,
            personal?.isFavorite(id.value) == true, resumeFor(id),
            controller.selectedRouteId, controller.selectedEpisodeId)
        syncFromController()
    }

    /** 个人记录（收藏/续播）真实变化后重投影：按钮与徽标必须反映已提交状态。 */
    fun personalRecordsChanged() { if (installedWorkId != null) reproject() }

    /** 页面状态行：详情自身状态优先，其次既有个人运行时的如实消息。 */
    fun currentStatus(): String = message.ifBlank { personal?.message.orEmpty() }

    private fun syncFromController() {
        focusedKey = controller.focusedKey; sheet = controller.sheet; sheetFocusedKey = controller.sheetFocusedKey
        restoreRequest = controller.pendingRestoreKey
    }

    /** 屏幕消费恢复请求：恢复目标同时成为当前焦点键；失败计数供 N04 断言。 */
    fun consumeRestore(): String? {
        val value = controller.consumeRestore()
        if (value == null) WorkDetailTestHooks.failedFocusRequests.incrementAndGet()
        else controller.overrideFocus(value)
        syncFromController(); return value
    }

    /** 真实 Compose 焦点落点：只做遥测与漂移检测（重组导致的原生漂移会被拉回权威焦点）。 */
    fun focusArrived(key: String) {
        WorkDetailTestHooks.traceFocus(key)
        val expected = if (controller.sheet != null) controller.sheetFocusedKey else controller.focusedKey
        if (key != expected) focusCorrection++
    }

    /** 线路/剧集证据刷新：真实重进或测试注入的迟到刷新都走同一令牌管线（E10）。 */
    fun refreshRoutes() {
        val id = workId ?: return
        routeRefreshBusy = true
        val token = controller.beginRefresh()
        scope.launch {
            try {
                WorkDetailTestHooks.holdRouteRefresh?.await()
                val fresh = WorkDetailFixture(context).routes(id)
                controller.completeRefresh(token, fresh)
                reproject()
            } finally { routeRefreshBusy = false }
        }
    }

    fun key(event: KeyEvent, exit: () -> Unit): Boolean {
        if (!browse.isDetail || browse.isPlayer) return false
        val code = event.keyCode
        if (code !in HANDLED_KEYS) return false
        if (event.action == KeyEvent.ACTION_UP) { held.remove(code); return true }
        if (event.action != KeyEvent.ACTION_DOWN) return true
        if (code in OK_KEYS && (!held.add(code) || event.repeatCount > 0)) return true
        if (installedWorkId == null) open()
        when (code) {
            KeyEvent.KEYCODE_BACK -> {
                val command = controller.back()
                handle(command, exit)
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> handle(controller.confirm(), exit)
            KeyEvent.KEYCODE_DPAD_UP -> move(DetailDirection.UP)
            KeyEvent.KEYCODE_DPAD_DOWN -> move(DetailDirection.DOWN)
            KeyEvent.KEYCODE_DPAD_LEFT -> move(DetailDirection.LEFT)
            KeyEvent.KEYCODE_DPAD_RIGHT -> move(DetailDirection.RIGHT)
        }
        return true
    }

    private fun move(direction: DetailDirection) {
        controller.move(direction)
        syncFromController(); reproject()
    }

    /** 语义/指针确认入口：与按键 OK 走同一确认管线（弹层锁定时调用方已禁用）。 */
    fun activate(key: String) {
        if (!browse.isDetail || browse.isPlayer || browse.personal == null) return
        if (installedWorkId == null) open()
        handle(controller.confirmOn(key)) { }
    }

    private fun handle(command: DetailCommand, exit: () -> Unit) {
        trace()
        when (command) {
            is DetailCommand.SelectRoute, is DetailCommand.SelectEpisode -> {
                commitContext(); reproject()
            }
            is DetailCommand.RequestPlay -> scope.launch { dispatchPlay(command.routeId, command.episodeId) }
            DetailCommand.RequestFavorite -> scope.launch {
                workId?.value?.let { id -> personal?.favorite(id) }
                reproject()
            }
            DetailCommand.RequestBack -> browse.back(exit)
            is DetailCommand.OpenSheet, DetailCommand.SheetDismissed, is DetailCommand.RouteRemoved ->
                { if (command is DetailCommand.RouteRemoved) commitContext(); reproject() }
            DetailCommand.None -> reproject()
        }
        syncFromController()
    }

    private fun commitContext() {
        val id = workId ?: return
        contextStore.save(id.value, controller.selectedRouteId, controller.selectedEpisodeId)
        WorkDetailTestHooks.traceDispatch("commit:${controller.selectedRouteId}/${controller.selectedEpisodeId}")
    }

    /** 显式确认的播放派发：派发时点（含被扣住后恢复的瞬间）再校验线路可用与身份。 */
    private suspend fun dispatchPlay(routeId: String, episodeId: String) {
        val id = workId?.value ?: return
        WorkDetailTestHooks.traceDispatch("play:$routeId/$episodeId")
        if (dispatchBusy) return
        dispatchBusy = true
        try {
            WorkDetailTestHooks.beforePlayDispatch?.await()
            val route = controller.routes.firstOrNull { it.routeId.value == routeId }
            val episode = route?.episodes?.firstOrNull { it.episodeId.value == episodeId }
            if (route == null || route.state != DetailRouteState.AVAILABLE || episode?.uri == null) {
                message = "所选线路当前不可用，未开始播放；可更换线路后重试"
                reproject(); return
            }
            val prepared = personal?.prepareTarget(id, episodeId, routeId) == true
            WorkDetailTestHooks.traceDispatch("prepared:$prepared")
            if (prepared) {
                message = ""
                browse.enterPlayerPrepared(WorkId(id))
                WorkDetailTestHooks.traceDispatch("enter-player:${browse.isPlayer}")
            } else {
                message = personal?.message?.takeIf { it.isNotBlank() } ?: "播放资料未核实，未开始播放"
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message = "播放资料读取失败，请重试；原记录保持" }
        finally { dispatchBusy = false; reproject() }
    }

    fun trace() {
        val id = workId
        File(context.noBackupFilesDir, "round15-detail-trace.json").writeText(JSONObject()
            .put("pid", android.os.Process.myPid()).put("work", id?.value)
            .put("route", controller.selectedRouteId).put("episode", controller.selectedEpisodeId)
            .put("focus", controller.focusedKey).put("sheet", controller.sheet?.name)
            .put("message", message).put("dispatches", org.json.JSONArray(WorkDetailTestHooks.dispatchTrace.toList()))
            .toString())
    }

    private companion object {
        val HANDLED_KEYS = setOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK)
        val OK_KEYS = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK)
    }
}
