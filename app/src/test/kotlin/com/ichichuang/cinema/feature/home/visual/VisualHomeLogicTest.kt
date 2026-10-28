package com.ichichuang.cinema.feature.home.visual

import com.ichichuang.cinema.domain.model.catalog.SourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the production pure functions in [VisualHomeLogic] — the same code
 * the runtime delegates to — so a mutation that guts a dispatch branch, drops the
 * retry key, discards a failure reason, hard-codes the source count, disables the
 * entrance, or removes the offline re-check is caught without a stand-in.
 */
class VisualHomeLogicTest {

    @Test
    fun `减弱动效 resolves to a toggle effect not a no-op`() {
        // M2b/M5a: gutting the toggle (or its dispatch branch) must leave this failing.
        assertEquals(SettingsEffect.TOGGLE_REDUCE_MOTION, settingsEffectFor("减弱动效"))
        assertTrue("toggle must not be a no-op", settingsEffectFor("减弱动效") != SettingsEffect.NONE)
    }

    @Test
    fun `a confirm on the retry key triggers a retry only when the key is focusable`() {
        val failed = SettingsProjection.project(
            loading = false, loadFailed = true, activeCount = 2,
            readStatus = HomeContentStatus.FAILED, failedSourceCount = 0,
        )
        // The retry key is focusable in a failed state, so a confirm retries.
        assertTrue(isRetryConfirm(SettingsSourceSection.RETRY_KEY, failed))
        // A confirm on any other key never retries.
        assertFalse(isRetryConfirm("减弱动效", failed))

        // In a non-retryable state the retry key is absent, so it cannot confirm.
        val ready = SettingsProjection.project(
            loading = false, loadFailed = false, activeCount = 3,
            readStatus = HomeContentStatus.READY, failedSourceCount = 0,
        )
        assertFalse(isRetryConfirm(SettingsSourceSection.RETRY_KEY, ready))
    }

    @Test
    fun `a read failure always yields a non-null reason`() {
        // M11: dropping the reason (recording null) must leave the projection
        // unable to state a cause; the pure derivation never drops it.
        assertEquals("网络超时", readFailureReason(IllegalStateException("网络超时")))
        assertEquals("未知原因", readFailureReason(null))
        assertEquals("未知原因", readFailureReason(IllegalStateException("")))
    }

    @Test
    fun `source count is never hard-coded to zero`() {
        // M13: a mutation that returns 0 for a non-empty set must fail this.
        assertEquals(0, configuredSourceCount(emptySet()))
        assertEquals(2, configuredSourceCount(setOf(SourceId("s1"), SourceId("s2"))))
        assertTrue("non-empty sources must not report zero", configuredSourceCount(setOf(SourceId("s1"))) > 0)
    }

    @Test
    fun `the entrance is triggered after the first composition`() {
        // M15: never flipping the entrance flag would leave the page invisible.
        assertTrue(shouldTriggerEntrance(firstCompositionDone = true))
    }

    @Test
    fun `a retry always re-checks connectivity before reloading`() {
        // M16: removing the offline refresh from retry must fail this.
        val steps = retrySteps()
        assertEquals(listOf("refreshOffline", "reload"), steps)
        assertTrue("retry must re-check connectivity", "refreshOffline" in steps)
        assertEquals("reload must come after the connectivity re-check", "reload", steps.last())
    }
}
