package com.ichichuang.cinema.feature.home.visual

import org.junit.Assert.*
import org.junit.Test

/** 设置切片四态投影与来源行文案的真实性规则。 */
class SettingsProjectionTest {

    private fun source(state: SettingsDisplayState): SettingsSourceState = requireNotNull(state.source)

    @Test
    fun `loading projects to LOADING with no source line`() {
        val state = SettingsProjection.project(
            loading = true, loadFailed = false, activeCount = 0,
            readStatus = HomeContentStatus.LOADING, failedSourceCount = 0,
        )
        assertEquals(SettingsPageStatus.LOADING, state.status)
        assertNull("加载中不得给出来源行", state.source)
    }

    @Test
    fun `zero active sources projects to EMPTY and honest guidance`() {
        val state = SettingsProjection.project(
            loading = false, loadFailed = false, activeCount = 0,
            readStatus = HomeContentStatus.UNCONFIGURED, failedSourceCount = 0,
        )
        assertEquals(SettingsPageStatus.EMPTY, state.status)
        assertEquals(0, source(state).activeCount)
        assertTrue("未配置必须明说未配置", SettingsProjection.sourceLine(source(state)).contains("未配置"))
    }

    @Test
    fun `read failure projects to ERROR and is retryable`() {
        val state = SettingsProjection.project(
            loading = false, loadFailed = true, activeCount = 2,
            readStatus = HomeContentStatus.FAILED, failedSourceCount = 0,
        )
        assertEquals(SettingsPageStatus.ERROR, state.status)
        assertTrue(SettingsProjection.retryable(source(state)))
        assertTrue(SettingsProjection.sourceLine(source(state)).contains("失败"))
    }

    @Test
    fun `configured sources project to CONTENT`() {
        val state = SettingsProjection.project(
            loading = false, loadFailed = false, activeCount = 3,
            readStatus = HomeContentStatus.READY, failedSourceCount = 0,
        )
        assertEquals(SettingsPageStatus.CONTENT, state.status)
        assertEquals(3, source(state).activeCount)
        assertTrue(SettingsProjection.sourceLine(source(state)).contains("已配置 3 个来源"))
    }

    @Test
    fun `offline is stated truthfully without claiming a total`() {
        val state = SettingsProjection.project(
            loading = false, loadFailed = false, activeCount = 1,
            readStatus = HomeContentStatus.OFFLINE, failedSourceCount = 0,
        )
        assertTrue(SettingsProjection.sourceLine(source(state)).contains("离线"))
        assertFalse(SettingsProjection.retryable(source(state)))
    }

    @Test
    fun `failed source count is stated not hidden`() {
        val state = SettingsProjection.project(
            loading = false, loadFailed = false, activeCount = 4,
            readStatus = HomeContentStatus.PARTIAL, failedSourceCount = 2,
        )
        val line = SettingsProjection.sourceLine(source(state))
        assertTrue("失败数必须如实说出", line.contains("2 个读取失败"))
        assertFalse("部分覆盖不得冒充完整", line.contains("已完成"))
    }
}
