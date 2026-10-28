package com.ichichuang.cinema.feature.home.visual

import com.ichichuang.cinema.domain.model.catalog.SourceId
import org.junit.Assert.*
import org.junit.Test

/** 设置切片四态投影与来源行文案的真实性规则。 */
class SettingsProjectionTest {

    private fun source(state: SettingsDisplayState): SettingsSourceState = requireNotNull(state.source)

    @Test
    fun `every settings item resolves to a non-null action`() {
        // A mutation that guts any item to a no-op must fail this: the label is the
        // single dispatch key and "减弱动效" in particular must map to the toggle.
        assertEquals(SettingsAction.TOGGLE_LARGE_TEXT, settingsActionFor("大字模式"))
        assertEquals(SettingsAction.TOGGLE_FILTER, settingsActionFor("筛选栏"))
        assertEquals(SettingsAction.CHANGE_COLUMNS, settingsActionFor("列数"))
        assertEquals(SettingsAction.TOGGLE_REDUCE_MOTION, settingsActionFor("减弱动效"))
        assertEquals(SettingsAction.REFRESH, settingsActionFor("刷新目录"))
        assertEquals(SettingsAction.RELOAD, settingsActionFor("重载首页"))
        assertEquals(null, settingsActionFor("不存在的条目"))
    }

    @Test
    fun `configured source count derives from the fixture source ids only`() {
        // The settings slice and the home screen share this single fact; there is no
        // DataStore read behind it, so the two can never disagree.
        assertEquals(0, configuredSourceCount(emptySet()))
        assertEquals(1, configuredSourceCount(setOf(SourceId("s1"))))
        assertEquals(3, configuredSourceCount(setOf(SourceId("s1"), SourceId("s2"), SourceId("s3"))))
    }

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
    fun `offline is stated truthfully and is retryable`() {
        val state = SettingsProjection.project(
            loading = false, loadFailed = false, activeCount = 1,
            readStatus = HomeContentStatus.OFFLINE, failedSourceCount = 0,
        )
        assertTrue(SettingsProjection.sourceLine(source(state)).contains("离线"))
        assertTrue("离线必须有重试入口", SettingsProjection.retryable(source(state)))
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
        assertTrue("部分失败也要有重试", SettingsProjection.retryable(source(state)))
    }

    @Test
    fun `a partial read with no failed sources is not retryable`() {
        val state = SettingsProjection.project(
            loading = false, loadFailed = false, activeCount = 2,
            readStatus = HomeContentStatus.PARTIAL, failedSourceCount = 0,
        )
        assertFalse("无失败的部分覆盖不该给死胡同重试", SettingsProjection.retryable(source(state)))
    }

    @Test
    fun `a failed read states the observed reason`() {
        val state = SettingsProjection.project(
            loading = false, loadFailed = true, activeCount = 2,
            readStatus = HomeContentStatus.FAILED, failedSourceCount = 0, reason = "网络超时",
        )
        val line = SettingsProjection.sourceLine(source(state))
        assertTrue("失败必须给出可读原因", line.contains("网络超时"))
        assertEquals("网络超时", source(state).reason)
    }

    @Test
    fun `the settings focus order includes the retry key only when retryable`() {
        val empty = SettingsProjection.project(
            loading = false, loadFailed = false, activeCount = 3,
            readStatus = HomeContentStatus.READY, failedSourceCount = 0,
        )
        val ok = settingsFocusKeys(empty)
        assertEquals(listOf("大字模式", "筛选栏", "列数", "减弱动效", "刷新目录", "重载首页"), ok)
        assertFalse("无失败时不该有重试焦点", SettingsSourceSection.RETRY_KEY in ok)

        val failed = SettingsProjection.project(
            loading = false, loadFailed = true, activeCount = 2,
            readStatus = HomeContentStatus.FAILED, failedSourceCount = 0,
        )
        val withRetry = settingsFocusKeys(failed)
        assertEquals(
            listOf("大字模式", "筛选栏", "列数", "减弱动效", SettingsSourceSection.RETRY_KEY, "刷新目录", "重载首页"),
            withRetry,
        )
        // The retry key must be reachable by up/down D-pad: it sits in the ordered list.
        val retryIndex = withRetry.indexOf(SettingsSourceSection.RETRY_KEY)
        assertTrue("重试键必须在焦点序列里", retryIndex >= 0)
    }
}
