package com.ichichuang.cinema.feature.player

/**
 * U02：播放事实 → 界面状态的唯一投影。
 * 缓冲 / 用户暂停 / 抑制播放 / 播完 / 失败互不冒充：
 * onIsPlayingChanged(false) 不等于用户暂停，BUFFERING 不等于播完。
 */
internal enum class PlayerRuntimeState { IDLE, BUFFERING, READY, ENDED }

internal data class PlayerFacts(val state: PlayerRuntimeState, val isPlaying: Boolean,
    val playWhenReady: Boolean, val error: String?)

internal data class PlayerStatusUi(val buffering: Boolean, val userPaused: Boolean, val ended: Boolean,
    val failed: Boolean, val statusText: String)

internal object PlayerStatusProjection {
    fun project(facts: PlayerFacts): PlayerStatusUi {
        facts.error?.let { error ->
            return PlayerStatusUi(buffering = false, userPaused = false, ended = false, failed = true, statusText = error)
        }
        return when (facts.state) {
            PlayerRuntimeState.BUFFERING ->
                PlayerStatusUi(buffering = true, userPaused = false, ended = false, failed = false, statusText = "缓冲中")
            PlayerRuntimeState.READY ->
                if (facts.isPlaying) PlayerStatusUi(false, false, false, false, "正在播放")
                else PlayerStatusUi(false, userPaused = !facts.playWhenReady, ended = false, failed = false,
                    statusText = if (facts.playWhenReady) "等待播放" else "已暂停")
            PlayerRuntimeState.ENDED ->
                PlayerStatusUi(false, false, ended = true, failed = false, statusText = "已播完")
            PlayerRuntimeState.IDLE ->
                PlayerStatusUi(false, false, false, false, statusText = "准备中")
        }
    }
}
