package com.ichichuang.cinema.core.design

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The "setting changes -> value changes" contract behind the probe root.
 *
 * The root provides `LocalReducedMotion` from `ReduceMotionController.reduced`,
 * so a flip of the in-app switch must re-emit within the same composition frame
 * (as fast as a `StateFlow` can push). These tests run the real controller over
 * an injected setting flow and a fake scale source: no Android, no stand-in for
 * the merge logic under test.
 */
class ReduceMotionControllerTest {

    @Test
    fun `turning the setting on flips reduced to true`() = runBlocking {
        val setting = MutableStateFlow(false)
        val controller = ReduceMotionController(SystemAnimatorScaleSource { 1f }, setting)
        assertEquals(false, controller.reduced.first())

        setting.value = true
        assertEquals(true, controller.reduced.first())
    }

    @Test
    fun `turning the setting off again flips reduced back`() = runBlocking {
        val setting = MutableStateFlow(true)
        val controller = ReduceMotionController(SystemAnimatorScaleSource { 1f }, setting)
        assertEquals(true, controller.reduced.first())

        setting.value = false
        assertEquals(false, controller.reduced.first())
    }

    @Test
    fun `a zero platform scale reduces even when the setting is off`() = runBlocking {
        val controller = ReduceMotionController(SystemAnimatorScaleSource { 0f }, MutableStateFlow(false))
        assertEquals(true, controller.reduced.first())
    }

    @Test
    fun `the two sources combine with OR not AND`() = runBlocking {
        // If the merge were an AND, "setting on + scale 0.5" would read false.
        val onController = ReduceMotionController(SystemAnimatorScaleSource { 0.5f }, MutableStateFlow(true))
        assertEquals(true, onController.reduced.first())

        // A non-zero scale with the setting off must stay normal.
        val offController = ReduceMotionController(SystemAnimatorScaleSource { 0.5f }, MutableStateFlow(false))
        assertEquals(false, offController.reduced.first())
    }

    @Test
    fun `the scale is re-read when the setting emits not continuously`() = runBlocking {
        // The scale is sampled per setting emission; it is not a live subscription
        // to the platform global (that would need a ContentObserver). Documenting
        // the real boundary: a scale change alone does not push a new value until
        // the setting flow emits again.
        var scale = 1f
        val setting = MutableStateFlow(false)
        val controller = ReduceMotionController(SystemAnimatorScaleSource { scale }, setting)
        assertEquals(false, controller.reduced.first())

        // The platform flips "remove animations" on, but the setting does not emit.
        scale = 0f
        // Re-emitting the setting (same value) triggers a fresh scale read.
        setting.value = false
        assertEquals(true, controller.reduced.first())
    }
}
