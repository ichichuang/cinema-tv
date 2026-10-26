package com.ichichuang.cinema.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * U02：缓冲、暂停、就绪、结束、错误互不冒充 —— 纯投影层判据。
 * onIsPlayingChanged(false) 不等于用户暂停；BUFFERING 不等于播完。
 */
class PlayerStatusProjectionTest {

    @Test
    fun buffering_is_never_user_pause_or_completion() {
        val s = PlayerStatusProjection.project(PlayerFacts(state = PlayerRuntimeState.BUFFERING,
            isPlaying = false, playWhenReady = true, error = null))
        assertTrue(s.buffering)
        assertFalse(s.userPaused)
        assertFalse(s.ended)
        assertEquals("缓冲中", s.statusText)
    }

    @Test
    fun ready_not_playing_with_play_when_ready_false_is_user_pause() {
        val s = PlayerStatusProjection.project(PlayerFacts(PlayerRuntimeState.READY,
            isPlaying = false, playWhenReady = false, error = null))
        assertTrue(s.userPaused)
        assertFalse(s.buffering)
        assertEquals("已暂停", s.statusText)
    }

    @Test
    fun ready_but_not_playing_with_play_when_ready_true_is_not_user_pause() {
        // 播放被抑制（如音频焦点丢失）但用户意图是播放：不得显示“已暂停”。
        val s = PlayerStatusProjection.project(PlayerFacts(PlayerRuntimeState.READY,
            isPlaying = false, playWhenReady = true, error = null))
        assertFalse(s.userPaused)
        assertFalse(s.buffering)
    }

    @Test
    fun ended_is_not_completion_of_anything_else() {
        val s = PlayerStatusProjection.project(PlayerFacts(PlayerRuntimeState.ENDED,
            isPlaying = false, playWhenReady = false, error = null))
        assertTrue(s.ended)
        assertFalse(s.buffering)
        assertFalse(s.userPaused)
        assertEquals("已播完", s.statusText)
    }

    @Test
    fun error_state_is_never_confused_with_buffering_or_pause() {
        val s = PlayerStatusProjection.project(PlayerFacts(PlayerRuntimeState.IDLE,
            isPlaying = false, playWhenReady = false, error = "播放失败（测试）"))
        assertTrue(s.failed)
        assertFalse(s.buffering)
        assertFalse(s.userPaused)
        assertEquals("播放失败（测试）", s.statusText)
    }

    @Test
    fun idle_without_error_is_preparing() {
        val s = PlayerStatusProjection.project(PlayerFacts(PlayerRuntimeState.IDLE,
            isPlaying = false, playWhenReady = false, error = null))
        assertEquals("准备中", s.statusText)
    }
}
