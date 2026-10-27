package com.ichichuang.cinema.feature.player

/**
 * R16 物理按键周期状态机（纯逻辑，无 Android 依赖）。
 *
 * 每个“物理按键周期”（一次 DOWN … 到配对 UP / CANCEL）至多产生一次激活：
 * - OK/Enter/Back：DOWN 不激活；短按只在完整短周期的 UP 触发一次；
 *   长按在阈值（平台 repeat 事件或流逝时间，先到者）触发一次，且绝不先出短按。
 * - 方向键：DOWN 立即移动一次（保留既有裸播放器语义），随后按节奏有界自动重复。
 * 滞留键、丢失 UP、孤儿 UP、ACTION_CANCEL、焦点丢失只作废周期，绝不产生幽灵点击。
 * “检查超时”由运行时的既有心跳驱动（不新增第二计时权威）。
 */
internal enum class RemoteKeyActivation { PRESS, LONG_PRESS, REPEAT }

internal data class RemoteKeyEvent(val action: Int, val keyCode: Int, val repeatCount: Int, val atMs: Long) {
    companion object {
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val ACTION_CANCEL = 2
        const val ACTION_MULTIPLE = 3
    }
}

internal class RemoteKeyCycle(
    private val longPressThresholdMs: Long = 400L,
    private val repeatDelayMs: Long = 400L,
    private val repeatIntervalMs: Long = 200L,
) {
    /** 平台长按会送 repeatCount>0 的 DOWN：这些键视为“激活型”（短/长按语义）。 */
    private fun isActivationKey(code: Int) = code == 23 || code == 66 || code == 4 // DPAD_CENTER / ENTER / BACK

    private var heldKey: Int? = null
    private var downAtMs = 0L
    private var longFired = false
    private var nextRepeatAtMs = 0L

    /** 当前周期持有的物理键（时间驱动派发需要还原键码）。 */
    val heldKeyCode: Int? get() = heldKey

    fun onEvent(event: RemoteKeyEvent): List<RemoteKeyActivation> = when (event.action) {
        RemoteKeyEvent.ACTION_DOWN -> onDown(event)
        RemoteKeyEvent.ACTION_UP -> onUp(event)
        RemoteKeyEvent.ACTION_CANCEL -> { onCancel(event.keyCode); emptyList() }
        else -> emptyList()
    }

    private fun onDown(event: RemoteKeyEvent): List<RemoteKeyActivation> {
        val key = heldKey
        if (key != null && key != event.keyCode) return emptyList()      // 已有周期持有：忽略第二键
        if (key == null || event.repeatCount == 0) {
            // repeatCount==0 只能是新的物理按下：即使同键（上一个 UP 丢失）也重开周期，
            // 绝不沿用旧 downAtMs/longFired —— 防幽灵长按与吞短按（评审确认项）。
            heldKey = event.keyCode
            downAtMs = event.atMs
            longFired = false
            nextRepeatAtMs = event.atMs + repeatDelayMs
            return if (isActivationKey(event.keyCode)) emptyList() else listOf(RemoteKeyActivation.PRESS)
        }
        // 同键后续 DOWN（平台重复）：repeat 事件也必须满足流逝阈值，早到的重复不算长按。
        if (isActivationKey(event.keyCode)) {
            return if (!longFired && event.repeatCount > 0 && event.atMs - downAtMs >= longPressThresholdMs) {
                longFired = true
                listOf(RemoteKeyActivation.LONG_PRESS)
            } else emptyList()
        }
        return if (event.atMs >= nextRepeatAtMs) {
            nextRepeatAtMs = event.atMs + repeatIntervalMs
            listOf(RemoteKeyActivation.REPEAT)
        } else emptyList()
    }

    private fun onUp(event: RemoteKeyEvent): List<RemoteKeyActivation> {
        val key = heldKey ?: return emptyList()                          // 孤儿 UP：忽略
        if (key != event.keyCode) return emptyList()                     // 他人之 UP：不成对清理
        heldKey = null
        if (!isActivationKey(key)) return emptyList()
        // R17 目标 A：长按可能在 ticker 心跳之前松开。此时 elapsed 已过阈值但 longFired
        // 仍为 false，绝不能再当作短按。补发 LONG_PRESS（消费语义与 checkTimeouts 一致：
        // 长 OK 揭示浮层、长 Back 无动作），并置 longFired 以保持 exactly-once。
        if (!longFired && event.atMs - downAtMs >= longPressThresholdMs) {
            longFired = true
            return listOf(RemoteKeyActivation.LONG_PRESS)
        }
        return if (!longFired) listOf(RemoteKeyActivation.PRESS) else emptyList()
    }

    private fun onCancel(keyCode: Int) {
        if (heldKey == keyCode) heldKey = null                           // 作废周期，无激活
    }

    /** 焦点丢失/图层切换：作废当前周期（后续 UP 不再激活）。 */
    fun revoke() { heldKey = null }

    /** 时间驱动：无平台 repeat 的长按判定 + 方向键有界重复；每次调用至多各发一次。 */
    fun checkTimeouts(nowMs: Long): List<RemoteKeyActivation> {
        val key = heldKey ?: return emptyList()
        if (isActivationKey(key)) {
            if (!longFired && nowMs - downAtMs >= longPressThresholdMs) {
                longFired = true
                return listOf(RemoteKeyActivation.LONG_PRESS)
            }
            return emptyList()
        }
        if (nowMs >= nextRepeatAtMs) {
            nextRepeatAtMs = nowMs + repeatIntervalMs
            return listOf(RemoteKeyActivation.REPEAT)
        }
        return emptyList()
    }
}
