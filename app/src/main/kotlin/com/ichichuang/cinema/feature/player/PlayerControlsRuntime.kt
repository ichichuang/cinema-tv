package com.ichichuang.cinema.feature.player

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.ichichuang.cinema.domain.model.userdata.CommitResult
import com.ichichuang.cinema.domain.model.catalog.WorkId
import com.ichichuang.cinema.feature.browse.BrowseProbeFixture
import com.ichichuang.cinema.feature.browse.BrowseProbeRuntime
import com.ichichuang.cinema.feature.detail.WorkDetailFixture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** 私有授权只属于一次性测试档案，绝不进入 Intent 或打包资产。 */
internal object PlayerControlsAuthorization {
    const val FILE = "round16-player-controls.authorization"
    const val TOKEN = "cinema-round16-disposable-player-controls"
    fun requireOwned(context: Context) {
        val file = File(context.noBackupFilesDir, FILE)
        check(file.isFile && file.length() == TOKEN.length.toLong() && file.readText() == TOKEN) {
            "播放器控制测试缺少独立授权"
        }
    }
}

/** 原生测试钩子（与既有探针钩子同族，无外部激活入口）。 */
internal object PlayerControlsTestHooks {
    val activeLeases = AtomicInteger(0)
    /** E05/E06：扣住一次换集/换线，制造迟到的会话切换。 */
    var holdSwitch: CompletableDeferred<Unit>? = null
    /** P03：扣住退出前的检查点落盘。 */
    var holdBoundary: CompletableDeferred<Unit>? = null
    val keyTrace = java.util.ArrayDeque<String>()
    fun traceKey(value: String) { if (keyTrace.size >= 64) keyTrace.removeFirst(); keyTrace.addLast(value) }
    val commandTrace = java.util.ArrayDeque<String>()
    fun traceCommand(value: String) { if (commandTrace.size >= 64) commandTrace.removeFirst(); commandTrace.addLast(value) }
    /** 禁止seek命令的实际派发（S05 断言零调用时置位）。 */
    var forbidSeekCalls = false
    val seekCalls = AtomicInteger(0)
    var disableControlFocus = false
    /** 测试专用：抑制空闲自动隐藏（该行为本身由 U03 专项验证）。 */
    var suppressIdleHide = false
    val failedFocusRequests = AtomicInteger(0)
    val focusTrace = java.util.ArrayDeque<String>()
    fun traceFocus(value: String) { if (focusTrace.size >= 64) focusTrace.removeFirst(); focusTrace.addLast(value) }
}

/**
 * R16 播放器控制运行时：唯一按键解释者 + 投影 + 命令执行。
 * 不新建第二个播放器/检查点写入者 —— 播放与落盘权威仍是
 * PersonalLibraryRuntime 的 ExoPlayer 与 RoomPlaybackProbe；
 * 本类只做读投影与经既有权威的确认派发。
 */
internal class PlayerControlsRuntime(
    private val context: Context,
    private val browse: BrowseProbeRuntime,
    private val scope: CoroutineScope,
    private val fixture: BrowseProbeFixture,
) {
    private val personal get() = requireNotNull(browse.personal)
    private val player: Player get() = personal.player
    val controller = PlayerControlsController()
    private val keyCycle = RemoteKeyCycle()
    private val detailFixture by lazy { WorkDetailFixture(context) }

    var display by mutableStateOf<PlayerDisplay?>(null); private set
    var pendingExitFailure by mutableStateOf<String?>(null); private set
    // 画面消费的 Compose 状态镜像：控制器本身是纯字段（JVM 可测），
    // 这里在每次投影后同步，保证 picker/焦点/预览变化真实触发重组。
    var uiOverlay by mutableStateOf(false); private set
    var uiPicker by mutableStateOf<PlayerPickerKind?>(null); private set
    var uiFocus by mutableStateOf<String?>(null); private set
    var uiPickerFocus by mutableStateOf<String?>(null); private set
    var uiPreview by mutableStateOf<Long?>(null); private set
    private var layerAnnounced = false
    private var lastInputAtMs = 0L
    private var listener: Player.Listener? = null
    private var ticker: Job? = null

    // ── 生命周期 ─────────────────────────────────────────────────

    fun start() {
        if (listener != null) return
        val bound = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                    events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
                    events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) ||
                    events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                    events.contains(Player.EVENT_TIMELINE_CHANGED)) reproject()
            }
            override fun onPlayerError(error: PlaybackException) {
                reproject(friendlyError(error))
            }
        }
        player.addListener(bound)
        listener = bound
        ticker = scope.launch {
            while (true) {
                delay(200)
                if (!browse.isPlayer) {
                    if (layerAnnounced) {
                        layerAnnounced = false; controller.closePicker(); controller.cancelPreview()
                        keyCycle.revoke()   // 离层作废进行中的物理按键周期
                    }
                    continue
                }
                val now = SystemClock.uptimeMillis()
                val held = keyCycle.heldKeyCode
                if (held != null) {
                    val synthetic = KeyEvent(KeyEvent.ACTION_DOWN, held)
                    for (activation in keyCycle.checkTimeouts(now)) dispatch(activation, synthetic) { }
                }
                // 空闲隐藏：仅在真实播放中、无选择器/预览/错误/缓冲时；选择器与预览交互永不被计时器隐藏。
                if (!PlayerControlsTestHooks.suppressIdleHide && controller.overlayVisible && now - lastInputAtMs >= IDLE_HIDE_MS) {
                    val p = player
                    if (p.isPlaying && !p.isLoading && display?.error == null && controller.hideAllowed()) {
                        controller.hideByTimer()
                        reproject()
                    }
                }
                // 浮层可见期间的有界 UI 位置心跳：只读投影，不产生第二检查点计时器。
                if (browse.isPlayer && (controller.overlayVisible || controller.picker != null) &&
                    display?.error == null) reproject()
            }
        }
        reproject()
    }

    fun close() {
        ticker?.cancel()
        listener?.let { player.removeListener(it) }
        listener = null
    }

    // ── 按键仲裁（唯一输入层） ────────────────────────────────────

    fun key(event: KeyEvent, exit: () -> Unit): Boolean {
        if (!browse.isPlayer) return false
        val code = event.keyCode
        if (code !in HANDLED_KEYS) return false
        if (event.action == KeyEvent.ACTION_MULTIPLE) return true
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return true
        // 系统取消的按键（FLAG_CANCELED 的 UP）映射为周期作废，绝不激活。
        val mapped = RemoteKeyEvent(
            action = if (event.action == KeyEvent.ACTION_UP && event.isCanceled) RemoteKeyEvent.ACTION_CANCEL
            else if (event.action == KeyEvent.ACTION_DOWN) RemoteKeyEvent.ACTION_DOWN else RemoteKeyEvent.ACTION_UP,
            keyCode = code, repeatCount = event.repeatCount, atMs = event.eventTime)
        PlayerControlsTestHooks.traceKey("key code=$code action=${event.action} repeat=${event.repeatCount}")
        for (activation in keyCycle.onEvent(mapped)) dispatch(activation, event, exit)
        return true
    }

    /** 只读暴露：画面渲染真实视频面用；播放权威不变。 */
    fun currentPlayer(): Player = player

    /** 窗口失焦（评审确认项）：进行中的物理按键周期立即作废，恢复聚焦后不得补偿触发。 */
    fun windowFocusGained(focused: Boolean) {
        if (!focused) keyCycle.revoke()
    }

    /** 语义/指针入口：与按键 OK 走同一确认管线（选择器打开时由调用方禁用背景）。 */
    fun activateControl(key: String) {
        if (!browse.isPlayer || controller.picker != null) return
        controller.revealByUser()
        controller.overrideFocus(key)
        onActivationPress(KeyEvent.KEYCODE_DPAD_CENTER) { }
    }

    /** 选择器条目的语义/指针入口（仅可用项会绑定）。 */
    fun activatePicker(key: String) {
        if (!browse.isPlayer || controller.picker == null) return
        controller.overridePickerFocus(key)
        when (val command = controller.confirm()) {
            is PlayerControlCommand.SelectEpisode -> selectTarget(command.episodeId, currentVariantId())
            is PlayerControlCommand.SelectLine -> selectTarget(newLineEpisode(command.variantId), command.variantId)
            else -> reproject()
        }
    }

    private fun dispatch(activation: RemoteKeyActivation, event: KeyEvent, exit: () -> Unit) {
        val code = event.keyCode
        PlayerControlsTestHooks.traceKey("activation=$activation code=$code picker=${controller.picker} overlay=${controller.overlayVisible}")
        lastInputAtMs = SystemClock.uptimeMillis()
        val directional = code in DIRECTION_KEYS
        when (activation) {
            RemoteKeyActivation.PRESS -> if (directional) onDirectionPress(code) else onActivationPress(code, exit)
            RemoteKeyActivation.REPEAT -> if (directional) onDirectionPress(code)
            RemoteKeyActivation.LONG_PRESS -> if (!directional) onActivationLongPress(code)
        }
    }

    private fun onDirectionPress(code: Int) {
        val direction = when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> PlayerDirection.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> PlayerDirection.RIGHT
            KeyEvent.KEYCODE_DPAD_UP -> PlayerDirection.UP
            else -> PlayerDirection.DOWN
        }
        if (controller.picker != null) { controller.pickerMove(direction); reproject(); return }
        if (controller.overlayVisible) {
            // 可见层：左右一次只移动一个焦点/预览目标（时间轴预览逻辑内聚在控制器）。
            controller.move(direction)
            reproject(); return
        }
        // 隐藏层：保留既有裸播放器方向语义（K01），但只在真实能力允许时执行。
        when (direction) {
            PlayerDirection.LEFT, PlayerDirection.RIGHT -> bareSeek(if (direction == PlayerDirection.LEFT) -SEEK_STEP_MS else SEEK_STEP_MS)
            PlayerDirection.UP -> switchAdjacentEpisode(-1)
            PlayerDirection.DOWN -> switchAdjacentEpisode(+1)
        }
    }

    private fun onActivationPress(code: Int, exit: () -> Unit) {
        if (code == KeyEvent.KEYCODE_BACK) { exitLayer(exit); return }
        when (val command = if (controller.picker != null || controller.overlayVisible) controller.confirm() else null) {
            null -> { controller.revealByUser(); reproject() }   // 隐藏层 OK：仅揭示控件
            PlayerControlCommand.TogglePlay -> togglePlay()
            is PlayerControlCommand.SeekTo -> confirmedSeek(command.positionMs)
            PlayerControlCommand.PickerOpened -> reproject()
            is PlayerControlCommand.SelectEpisode -> selectTarget(command.episodeId, currentVariantId())
            is PlayerControlCommand.SelectLine -> selectTarget(newLineEpisode(command.variantId), command.variantId)
            PlayerControlCommand.Retry -> retryPlayback()
            PlayerControlCommand.None, PlayerControlCommand.FocusMoved,
            PlayerControlCommand.PreviewCancelled, PlayerControlCommand.PickerDismissed,
            PlayerControlCommand.OverlayDismissed, PlayerControlCommand.ExitLayer -> reproject()
        }
    }

    private fun onActivationLongPress(code: Int) {
        if (code == KeyEvent.KEYCODE_BACK) return
        // 长 OK：揭开出控件层的快捷方式；所有操作仍可用短 OK 完成（K10）。
        controller.revealByUser()
        reproject()
    }

    private fun exitLayer(exit: () -> Unit) {
        val command = controller.back()
        when (command) {
            PlayerControlCommand.PickerDismissed, PlayerControlCommand.OverlayDismissed,
            PlayerControlCommand.PreviewCancelled -> { pendingExitFailure = null; reproject() }
            else -> {
                // 退出播放层：先走既有检查点屏障；失败如实呈现且不无限困住 Back（P03）。
                if (pendingExitFailure != null) { pendingExitFailure = null; browse.back(exit); return }
                scope.launch {
                    PlayerControlsTestHooks.holdBoundary?.await()
                    var saved: CommitResult? = null
                    try { saved = personal.boundary() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { saved = CommitResult.Failed("boundary_error") }
                    if (saved != null && saved != CommitResult.Committed) {
                        pendingExitFailure = "进度尚未保存，原记录保持；再按一次返回直接退出"
                        reproject()
                    } else browse.back(exit)
                }
            }
        }
    }

    // ── 隐藏层裸语义（K01/S05/S06） ──────────────────────────────

    private fun bareSeek(deltaMs: Long) {
        if (!player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            hint("当前内容不支持快进快退"); return
        }
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
        val target = (player.currentPosition + deltaMs).coerceAtLeast(0L).let { if (duration != null) it.coerceAtMost(duration) else it }
        realSeekTo(target)
        hint((if (deltaMs >= 0) "快进 " else "快退 ") + (kotlin.math.abs(deltaMs) / 1000) + " 秒")
    }

    private fun switchAdjacentEpisode(delta: Int) {
        val variant = currentVariantId() ?: run { hint("当前线路没有可切换的剧集"); return }
        val episodes = evidenceEpisodes(variant)
        if (episodes.size < 2) { hint("当前线路仅一集"); return }
        val index = episodes.indexOfFirst { it.episodeId.value == currentEpisodeId() }
        if (index < 0) { hint("当前线路没有可切换的剧集"); return }
        val next = index + delta
        if (next !in episodes.indices) { hint(if (delta > 0) "已经是最后一集" else "已经是第一集"); return }
        selectTarget(episodes[next].episodeId.value, variant)
    }

    // ── 命令执行（全部经既有权威与真实能力检查） ──────────────────

    private fun togglePlay() {
        if (!player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) { hint("当前状态不能播放或暂停"); return }
        PlayerControlsTestHooks.traceCommand("togglePlay")
        if (player.isPlaying) player.pause()
        else {
            if (player.playbackState == Player.STATE_ENDED &&
                player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) player.seekTo(0L)
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
        reproject()
    }

    private fun confirmedSeek(targetMs: Long) {
        if (!player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) { hint("当前内容不支持拖动进度"); return }
        if (player.duration == C.TIME_UNSET) { hint("时长未知，无法拖动进度"); return }
        realSeekTo(targetMs)
        hint("已跳转")
    }

    private fun realSeekTo(positionMs: Long) {
        if (PlayerControlsTestHooks.forbidSeekCalls) { PlayerControlsTestHooks.seekCalls.incrementAndGet(); return }
        PlayerControlsTestHooks.seekCalls.incrementAndGet()
        player.seekTo(positionMs)
    }

    private fun retryPlayback() {
        PlayerControlsTestHooks.traceCommand("retry")
        scope.launch {
            try { personal.retry() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { hint("重试未成功，原记录保持") }
            reproject()
        }
    }

    /** 播放器内换集/换线：既有身份管线 + 换前检查点屏障；迟到结果按代次拒绝（E05/E06）。 */
    private fun selectTarget(episodeId: String?, variantId: String?) {
        if (episodeId == null || variantId == null) { hint("所选线路或剧集不可用，未切换"); return }
        val workId = currentWorkId() ?: run { hint("所选线路或剧集不可用，未切换"); return }
        // R17 goal A: reserve the selection generation synchronously on the caller thread, so a
        // newer selection always carries a higher generation than an older one still in flight.
        // The switch runs through PersonalLibraryRuntime.selectFromPlayer, whose authoritative
        // stale check is inside RoomPlaybackProbe.select's serialized queue (before any player
        // or Room mutation).
        val generation = personal.reserveSelection()
        PlayerControlsTestHooks.traceCommand("select:$variantId/$episodeId@$generation")
        scope.launch {
            try {
                // Test-only hold (E05/E06): stall the selection right before its blocking
                // Room-bound switch so a newer selection can reserve a higher generation first.
                PlayerControlsTestHooks.holdSwitch?.await()
                val selected = personal.selectFromPlayer(workId, episodeId, variantId, generation)
                if (!selected) {
                    if (personal.isSelectionStale(generation)) PlayerControlsTestHooks.traceCommand("stale-drop:$generation")
                    hint(personal.message.ifBlank { "所选线路或剧集不可用，未切换" })
                } else hint("已切换")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { hint("切换未完成，原记录保持") }
            reproject()
        }
    }

    // ── 投影（唯一事实来源：真实 Player + 证据档案 + 控制器瞬态） ──

    private fun currentWorkId(): String? = personal.selectedMedia?.target?.workId?.value
    private fun currentVariantId(): String? = personal.selectedMedia?.target?.variantId?.value
    private fun currentEpisodeId(): String? = personal.selectedMedia?.target?.episodeId?.value

    /** 换线路的新线路默认剧集：只取该线路自身证据中首个可播剧集，绝不沿用旧线路选择（E03）。 */
    private fun newLineEpisode(variantId: String): String? =
        evidenceEpisodes(variantId).firstOrNull { it.uri != null }?.episodeId?.value

    private fun evidenceRoutes(workId: String) = detailFixture.routes(WorkId(workId))
    private fun evidenceEpisodes(variantId: String): List<com.ichichuang.cinema.feature.detail.DetailFixtureEpisode> =
        evidenceRoutes(currentWorkId() ?: return emptyList())
            .firstOrNull { it.routeId.value == variantId }?.episodes ?: emptyList()

    fun reproject(forcedError: String? = null) {
        val workId = currentWorkId()
        val media = personal.selectedMedia
        if (workId == null || media == null) {
            display = PlayerDisplay.empty
            return
        }
        // 进入播放层首次就位：揭示控件（暂停态常驻），返回后离层复位。
        if (!layerAnnounced) {
            layerAnnounced = true
            controller.revealByUser()
        }
        val routes = evidenceRoutes(workId)
        val variant = currentVariantId()
        val episode = currentEpisodeId()
        val route = routes.firstOrNull { it.routeId.value == variant }
        val episodes = route?.episodes ?: emptyList()
        val duration = player.duration.let { if (it == C.TIME_UNSET || it <= 0) null else it }
        val seekAvailable = player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        val sticky = display?.facts?.error
        val error = forcedError ?: sticky?.takeIf { player.playbackState == Player.STATE_IDLE }
        val lines = routes.map { PlayerPickerLine(it.routeId.value, it.label, it.state == com.ichichuang.cinema.feature.detail.DetailRouteState.AVAILABLE &&
            it.episodes.any { ep -> ep.uri != null }, it.episodes.size.takeIf { n -> n > 0 }) }
        val episodeItems = episodes.map { PlayerPickerEpisode(it.episodeId.value,
            it.label ?: "未提供集数名", it.uri != null && route?.state == com.ichichuang.cinema.feature.detail.DetailRouteState.AVAILABLE) }
        controller.install(duration, seekAvailable, lines, episodeItems,
            variant ?: "", episode ?: "", player.currentPosition, retryAvailable = error != null)
        val facts = PlayerFacts(when (player.playbackState) {
            Player.STATE_READY -> PlayerRuntimeState.READY
            Player.STATE_BUFFERING -> PlayerRuntimeState.BUFFERING
            Player.STATE_ENDED -> PlayerRuntimeState.ENDED
            else -> PlayerRuntimeState.IDLE
        }, player.isPlaying, player.playWhenReady, error)
        val status = PlayerStatusProjection.project(facts)
        display = PlayerDisplay(
            workId = workId,
            title = fixture.works.firstOrNull { it.id.value == workId }?.title ?: "本地合成媒体",
            episodeId = episode, variantId = variant,
            episodeLabel = episodes.firstOrNull { it.episodeId.value == episode }?.label,
            lineLabel = route?.label,
            facts = facts, status = status,
            positionMs = player.currentPosition,
            durationMs = duration,
            bufferedMs = player.bufferedPosition.let { if (it == C.TIME_UNSET) 0L else it },
            playPauseAvailable = player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE),
            seekAvailable = seekAvailable,
            hint = pendingHint, pendingExitFailure = pendingExitFailure, error = error)
        syncControllerState()
        trace()
    }

    private var pendingHint: String? = null
    private var hintNonce = 0L
    private fun hint(text: String) {
        pendingHint = text; hintNonce++
        lastInputAtMs = SystemClock.uptimeMillis()
        reproject()
        // 瞬时提示自动消失（评审项）：反馈停留 3 秒，不永久占据画面。
        val nonce = hintNonce
        scope.launch {
            delay(3_000)
            if (nonce == hintNonce && pendingHint == text) { pendingHint = null; reproject() }
        }
    }

    fun consumeHint(): String? { val value = pendingHint; if (value != null) { pendingHint = null; reproject() }; return value }

    fun focusArrived(key: String) {
        PlayerControlsTestHooks.traceFocus(key)
        val expected = if (controller.picker != null) controller.pickerFocusedId else controller.focusedControlId
        if (key != expected) PlayerControlsTestHooks.failedFocusRequests.incrementAndGet()
    }

    private fun friendlyError(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> "连不上播放地址：线路可能挂了，或需要换线路"
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "播放地址返回错误：这条线路的地址已失效"
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> "媒体格式不支持：这个地址可能不是有效的视频流"
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "这台电视解不了这个视频编码"
        // 未知错误码不向用户外泄英文标识（评审项）；诊断细节只进一次性 trace 档案。
        else -> "播放失败，请重试"
    }

    /** 控制器瞬态 → Compose 镜像（reproject 尾部调用）。 */
    private fun syncControllerState() {
        uiOverlay = controller.overlayVisible
        uiPicker = controller.picker
        uiFocus = controller.focusedControlId
        uiPickerFocus = controller.pickerFocusedId
        uiPreview = controller.previewMs
    }

    private fun trace() {
        val media = personal.selectedMedia
        File(context.noBackupFilesDir, "round16-player-trace.json").writeText(JSONObject()
            .put("pid", android.os.Process.myPid())
            .put("work", media?.target?.workId?.value)
            .put("episode", media?.target?.episodeId?.value)
            .put("variant", media?.target?.variantId?.value)
            .put("overlay", controller.overlayVisible).put("picker", controller.picker?.name)
            .put("focus", controller.focusedControlId).put("pickerFocus", controller.pickerFocusedId)
            .put("preview", controller.previewMs).put("position", player.currentPosition)
            .put("duration", player.duration.let { if (it == C.TIME_UNSET) JSONObject.NULL else it })
            .put("playing", player.isPlaying).put("playWhenReady", player.playWhenReady)
            .put("state", player.playbackState)
            .put("seekAvailable", player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
            .put("keys", org.json.JSONArray(PlayerControlsTestHooks.keyTrace.toList().takeLast(20)))
            .put("commands", org.json.JSONArray(PlayerControlsTestHooks.commandTrace.toList().takeLast(20)))
            .toString())
    }

    private companion object {
        const val IDLE_HIDE_MS = 5_000L
        const val SEEK_STEP_MS = 15_000L   // 与既有配置的默认步长一致（CinemaSettings.DEFAULT_SEEK_STEP_MS）
        val HANDLED_KEYS = setOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK)
        val DIRECTION_KEYS = setOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)
    }
}

internal data class PlayerDisplay(
    val workId: String, val title: String,
    val episodeId: String?, val variantId: String?,
    val episodeLabel: String?, val lineLabel: String?,
    val facts: PlayerFacts, val status: PlayerStatusUi,
    val positionMs: Long, val durationMs: Long?, val bufferedMs: Long,
    val playPauseAvailable: Boolean, val seekAvailable: Boolean,
    val hint: String?, val pendingExitFailure: String?, val error: String?,
) {
    companion object {
        val empty = PlayerDisplay("", "本地合成媒体", null, null, null, null,
            PlayerFacts(PlayerRuntimeState.IDLE, false, false, null),
            PlayerStatusProjection.project(PlayerFacts(PlayerRuntimeState.IDLE, false, false, null)),
            0L, null, 0L, false, false, null, null, null)
    }
}
