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
}
