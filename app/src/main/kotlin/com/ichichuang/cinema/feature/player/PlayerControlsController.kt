package com.ichichuang.cinema.feature.player

/**
 * R16 播放器控制层控制器（纯状态机，无 Android 依赖）。
 *
 * 只管理“可见层”的瞬态焦点/浮层/时间轴预览/选择器；持久身份
 * （作品/剧集/版本）与检查点始终归 Room 管线所有。焦点
 * （focusedControlId/pickerFocusedId）与选择（当前身份）永远分立；
 * 用户输入使一切迟到恢复失效。
 */
internal enum class PlayerPickerKind { LINES, EPISODES }
internal enum class PlayerDirection { UP, DOWN, LEFT, RIGHT }

internal sealed interface PlayerControlCommand {
    data object None : PlayerControlCommand
    data object FocusMoved : PlayerControlCommand
    data object TogglePlay : PlayerControlCommand
    data class SeekTo(val positionMs: Long) : PlayerControlCommand
    data object PreviewCancelled : PlayerControlCommand
    data object PickerOpened : PlayerControlCommand
    data object PickerDismissed : PlayerControlCommand
    data class SelectEpisode(val episodeId: String) : PlayerControlCommand
    data class SelectLine(val variantId: String) : PlayerControlCommand
    data object Retry : PlayerControlCommand
    data object OverlayDismissed : PlayerControlCommand
    data object ExitLayer : PlayerControlCommand
}

internal data class PlayerPickerLine(val variantId: String, val label: String, val available: Boolean, val episodeCount: Int?)
internal data class PlayerPickerEpisode(val episodeId: String, val label: String, val playable: Boolean)

internal class PlayerControlsController {
    companion object {
        val CONTROL_IDS = listOf("播放控制", "时间轴", "选集", "线路", "重试")
    }

    var overlayVisible = false; private set
    var focusedControlId: String? = null; private set
    var previewMs: Long? = null; private set
    var picker: PlayerPickerKind? = null; private set
    var pickerFocusedId: String? = null; private set

    private var durationMs: Long? = null
    private var seekAvailable = false
    private var retryAvailable = false
    private var seekStepMs = 15_000L
    private var positionMs = 0L
    private var lines: List<PlayerPickerLine> = emptyList()
    private var episodeList: List<PlayerPickerEpisode> = emptyList()
    private var currentVariantId: String? = null
    private var currentEpisodeId: String? = null
    private var pendingRestore: String? = null

    /** 播放状态变化时重装；瞬态焦点/浮层保留。 */
    fun install(durationMs: Long?, seekAvailable: Boolean, lines: List<PlayerPickerLine>,
        episodes: List<PlayerPickerEpisode>, currentVariantId: String, currentEpisodeId: String, positionMs: Long,
        retryAvailable: Boolean = false, seekStepMs: Long = 15_000L) {
        this.durationMs = durationMs
        this.seekAvailable = seekAvailable
        this.retryAvailable = retryAvailable
        this.seekStepMs = seekStepMs
        this.positionMs = positionMs
        this.lines = lines
        this.episodeList = episodes
        this.currentVariantId = currentVariantId
        this.currentEpisodeId = currentEpisodeId
        // 真实能力变化后，滞留的预览目标不再可信：清空（提交语义由运行时对 Media3 负责）。
        if (durationMs == null || !seekAvailable) cancelPreview()
        else previewMs = previewMs?.coerceIn(0L, durationMs)
        if (focusedControlId !in enabledControls() && focusedControlId != null)
            focusedControlId = enabledControls().firstOrNull()
        if (picker != null) syncPickerAfterDataChange()
    }

    fun controlEnabled(id: String): Boolean = when (id) {
        "播放控制" -> true
        "时间轴" -> durationMs != null && seekAvailable
        "选集" -> episodeList.isNotEmpty()
        "线路" -> lines.isNotEmpty()
        "重试" -> retryAvailable
        else -> false
    }

    private fun enabledControls(): List<String> = CONTROL_IDS.filter(::controlEnabled)

    // ── 浮层揭示 / 隐藏资格（U03/U04） ─────────────────────────────

    /** 用户显式揭示：焦点进入首个可用控件（选择器打开时揭示只维持选择器）。 */
    fun revealByUser(): Boolean {
        userMoved()
        if (picker != null) return true
        overlayVisible = true
        if (focusedControlId == null || !controlEnabled(focusedControlId!!))
            focusedControlId = enabledControls().firstOrNull()
        return true
    }

    /** 自动隐藏资格：任何进行中的交互（选择器/时间轴预览）都拒绝隐藏。 */
    fun hideAllowed(): Boolean = overlayVisible && picker == null && previewMs == null

    fun hideByTimer(): Boolean {
        if (!hideAllowed()) return false
        overlayVisible = false
        return true
    }

    // ── 可见层导航（K02）：方向键只移动焦点，一次一格 ───────────────

    fun move(direction: PlayerDirection): PlayerControlCommand {
        if (picker != null) return pickerMoveCommand(direction)
        if (!overlayVisible) return PlayerControlCommand.None
        // 时间轴捕获左右键：聚焦即预览（一次一键，真实时长内钳制），不移动焦点；
        // 上下键离开时间轴（上→播放控制，下→选集/线路）。
        val onTimeline = focusedControlId == "时间轴" && controlEnabled("时间轴")
        if (onTimeline && (direction == PlayerDirection.LEFT || direction == PlayerDirection.RIGHT)) {
            if (durationMs != null) {
                userMoved()
                val delta = if (direction == PlayerDirection.LEFT) -seekStepMs else seekStepMs
                val base = previewMs ?: positionMs
                previewMs = (base + delta).coerceIn(0L, durationMs!!)
            }
            return PlayerControlCommand.FocusMoved
        }
        if (direction == PlayerDirection.UP || direction == PlayerDirection.DOWN) {
            // 行切换：时间轴 ↑播放控制/↓选集；动作行 ↑进入时间轴（可用时）。
            if (focusedControlId == "时间轴" && controlEnabled("时间轴")) {
                userMoved(); cancelPreview()
                focusedControlId = if (direction == PlayerDirection.UP) "播放控制"
                else enabledControls().firstOrNull { it != "时间轴" && it != "播放控制" } ?: focusedControlId
                return PlayerControlCommand.FocusMoved
            }
            if (direction == PlayerDirection.UP && focusedControlId != "时间轴" && controlEnabled("时间轴")) {
                userMoved(); cancelPreview()
                focusedControlId = "时间轴"
                return PlayerControlCommand.FocusMoved
            }
            return PlayerControlCommand.None
        }
        val controls = enabledControls()
        val index = controls.indexOf(focusedControlId).coerceAtLeast(0)
        val next = when (direction) {
            PlayerDirection.LEFT -> (index - 1).coerceAtLeast(0)
            else -> (index + 1).coerceAtMost(controls.lastIndex)
        }
        userMoved()
        focusedControlId = controls.getOrNull(next) ?: focusedControlId
        if (focusedControlId != "时间轴") cancelPreview()
        return PlayerControlCommand.FocusMoved
    }

    /** OK 确认：时间轴预览确认 / 控件激活。 */
    fun confirm(): PlayerControlCommand {
        picker?.let { return pickerConfirm() }
        val id = focusedControlId ?: return PlayerControlCommand.None
        if (!controlEnabled(id)) return PlayerControlCommand.None
        previewMs?.let { target ->
            if (id == "时间轴") {
                previewMs = null
                return PlayerControlCommand.SeekTo(target)
            }
        }
        return when (id) {
            "播放控制" -> PlayerControlCommand.TogglePlay
            "时间轴" -> PlayerControlCommand.None        // 无预览的时间轴 OK：无操作（预览由方向键建立）
            "选集" -> openPickerInternal(PlayerPickerKind.EPISODES)
            "线路" -> openPickerInternal(PlayerPickerKind.LINES)
            "重试" -> PlayerControlCommand.Retry
            else -> PlayerControlCommand.None
        }
    }

    /** Back 逐层：选择器 → 浮层 → 请求退出播放层（K07）。 */
    fun back(): PlayerControlCommand {
        previewMs?.let { previewMs = null; return PlayerControlCommand.PreviewCancelled }
        picker?.let { closePicker(); return PlayerControlCommand.PickerDismissed }
        if (overlayVisible) { overlayVisible = false; return PlayerControlCommand.OverlayDismissed }
        return PlayerControlCommand.ExitLayer
    }

    // ── 时间轴预览（S01–S04/S06）：目标只在 [0, 真实时长] 内 ───────

    fun beginPreview(fromMs: Long): Boolean {
        if (!controlEnabled("时间轴")) return false
        userMoved()
        previewMs = fromMs.coerceAtLeast(0L)
        return true
    }

    fun adjustPreview(deltaMs: Long) {
        val target = previewMs ?: return
        val duration = durationMs ?: return
        previewMs = (target + deltaMs).coerceIn(0L, duration)
    }

    fun cancelPreview() { previewMs = null }

    // ── 选择器（E01/E02/E04/K08）：证据身份、禁用项不聚焦 ──────────

    private fun openPickerInternal(kind: PlayerPickerKind): PlayerControlCommand {
        val hasItems = when (kind) {
            PlayerPickerKind.LINES -> lines.isNotEmpty()
            PlayerPickerKind.EPISODES -> episodeList.isNotEmpty()
        }
        if (!hasItems) return PlayerControlCommand.None
        picker = kind
        pickerFocusedId = defaultPickerFocus(kind)
        return PlayerControlCommand.PickerOpened
    }

    /** 打开选择器（运行时/测试入口）；证据清单为空时不打开。 */
    fun openPicker(kind: PlayerPickerKind): PlayerControlCommand = openPickerInternal(kind)

    fun closePicker() { picker = null; pickerFocusedId = null }

    private fun defaultPickerFocus(kind: PlayerPickerKind): String? = when (kind) {
        PlayerPickerKind.LINES -> {
            val current = "picker:$currentVariantId"
            lineKeys().firstOrNull { it == current }
                ?: lineKeys().firstOrNull { key -> lines.firstOrNull { "picker:${it.variantId}" == key }?.available == true }
        }
        PlayerPickerKind.EPISODES ->
            episodeKeys().firstOrNull { it == "picker:$currentEpisodeId" } ?: episodeKeys().firstOrNull()
    }

    private fun lineKeys(): List<String> = lines.map { "picker:${it.variantId}" }
    private fun episodeKeys(): List<String> = episodeList.map { "picker:${it.episodeId}" }

    val pickerLines: List<PlayerPickerLine> get() = lines
    val pickerEpisodes: List<PlayerPickerEpisode> get() = episodeList
    val pickerCurrentVariantId: String? get() = currentVariantId
    val pickerCurrentEpisodeId: String? get() = currentEpisodeId

    private fun pickerMoveCommand(direction: PlayerDirection): PlayerControlCommand {
        pickerMove(direction)
        return PlayerControlCommand.FocusMoved
    }

    fun pickerMove(direction: PlayerDirection): String? {
        val kind = picker ?: return null
        if (direction != PlayerDirection.UP && direction != PlayerDirection.DOWN) return pickerFocusedId
        val keys = when (kind) {
            PlayerPickerKind.LINES -> lineKeys()
            PlayerPickerKind.EPISODES -> episodeKeys()
        }
        val focusable = keys.filter { key -> pickerItemAvailable(kind, key.substringAfter("picker:")) }
        val index = focusable.indexOf(pickerFocusedId).coerceAtLeast(0)
        val next = when (direction) {
            PlayerDirection.UP -> (index - 1).coerceAtLeast(0)
            else -> (index + 1).coerceAtMost(focusable.lastIndex)
        }
        userMoved()
        pickerFocusedId = focusable.getOrNull(next) ?: focusable.firstOrNull()
        return pickerFocusedId
    }

    /** 禁用线路/剧集不可激活（E02）。 */
    private fun pickerItemAvailable(kind: PlayerPickerKind, id: String): Boolean = when (kind) {
        PlayerPickerKind.LINES -> lines.firstOrNull { it.variantId == id }?.available == true
        PlayerPickerKind.EPISODES -> episodeList.firstOrNull { it.episodeId == id }?.playable == true
    }

    private fun pickerConfirm(): PlayerControlCommand {
        val kind = picker ?: return PlayerControlCommand.None
        val id = pickerFocusedId?.substringAfter("picker:")
            ?: defaultPickerFocus(kind)?.substringAfter("picker:") ?: return PlayerControlCommand.None
        if (!pickerItemAvailable(kind, id)) return PlayerControlCommand.None
        closePicker()
        return when (kind) {
            PlayerPickerKind.LINES -> PlayerControlCommand.SelectLine(id)
            PlayerPickerKind.EPISODES -> PlayerControlCommand.SelectEpisode(id)
        }
    }

    private fun syncPickerAfterDataChange() {
        val kind = picker ?: return
        val keys = when (kind) {
            PlayerPickerKind.LINES -> lineKeys()
            PlayerPickerKind.EPISODES -> episodeKeys()
        }
        if (pickerFocusedId !in keys || (pickerFocusedId?.substringAfter("picker:")
                ?.let { pickerItemAvailable(kind, it) } == false))
            pickerFocusedId = defaultPickerFocus(kind)
    }

    // ── 焦点恢复（K09）：用户真实输入使迟到恢复失效 ─────────────────

    fun focusControl(id: String) {
        if (!overlayVisible || picker != null) return
        if (id in enabledControls()) focusedControlId = id
    }

    /** 运行时/漂移恢复路径（非用户输入，不推进代次）：允许落在禁用控件上以便其后的确认/导航自证安全。 */
    fun overrideFocus(id: String) {
        if (picker != null) { overridePickerFocus("picker:$id"); return }
        if (id in CONTROL_IDS) focusedControlId = id
    }

    fun overridePickerFocus(key: String) {
        val kind = picker ?: return
        val id = key.substringAfter("picker:")
        if (pickerItemAvailable(kind, id)) pickerFocusedId = key
    }

    /** 请求选择器聚焦某键（仅可用项生效），返回实际聚焦键。 */
    fun pickerFocused(key: String): String? {
        overridePickerFocus(key)
        return pickerFocusedId
    }

    fun requestRestore(id: String) { if (picker == null) pendingRestore = id }

    fun consumeRestore(): String? { val value = pendingRestore; pendingRestore = null; return value }

    private fun userMoved() { pendingRestore = null }
}
