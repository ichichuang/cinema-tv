package com.ichichuang.cinema.core.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reduced motion behaviour, exercised against the production `Motion` object
 * (no stand-in): under reduction every spec collapses to the 40ms floor, every
 * displacement becomes zero and no scale is applied.
 */
class ReducedMotionTest {

    @Test
    fun `shouldReduceMotion truth table`() {
        // Neither source asks for reduction.
        assertEquals(false, shouldReduceMotion(userSetting = false, animatorDurationScale = 1f))
        assertEquals(false, shouldReduceMotion(userSetting = false, animatorDurationScale = 0.5f))
        // User turned it on in settings.
        assertEquals(true, shouldReduceMotion(userSetting = true, animatorDurationScale = 1f))
        // Platform "remove animations" sets the scale to 0.
        assertEquals(true, shouldReduceMotion(userSetting = false, animatorDurationScale = 0f))
        // Both.
        assertEquals(true, shouldReduceMotion(userSetting = true, animatorDurationScale = 0f))
    }

    @Test
    fun `reducedMotionForRoot maps the nullable probe signal to the root value`() {
        // The probe runtime is null outside the authorized debug path: full motion.
        assertEquals(false, reducedMotionForRoot(null))
        // The effective signal is passed through verbatim when the runtime exists.
        assertEquals(false, reducedMotionForRoot(false))
        assertEquals(true, reducedMotionForRoot(true))
    }

    @Test
    fun `enter transition under reduced motion has no offset and no scale`() {
        val enter = Motion.enterSpec(reduced = true)
        assertEquals(Motion.DUR_REDUCED, enter.durationMs)
        assertTrue("reduced duration must stay under 50ms", enter.durationMs in 1..50)
        assertEquals(0f, enter.offsetDp, 0f)
        assertEquals(1f, enter.scaleFrom, 0f)
        assertEquals(1f, enter.scaleTo, 0f)
        assertSame(LinearEasing, enter.easing)
    }

    @Test
    fun `exit transition under reduced motion has no offset and no scale`() {
        val exit = Motion.exitSpec(reduced = true)
        assertEquals(Motion.DUR_REDUCED, exit.durationMs)
        assertEquals(0f, exit.offsetDp, 0f)
        assertEquals(1f, exit.scaleFrom, 0f)
        assertEquals(1f, exit.scaleTo, 0f)
    }

    @Test
    fun `full motion enter fades in from 8dp below at 96 percent scale`() {
        val enter = Motion.enterSpec()
        assertEquals(Motion.DUR_ENTER, enter.durationMs)
        assertEquals(Motion.ENTER_OFFSET_DP, enter.offsetDp, 0f)
        assertEquals(Motion.ENTER_SCALE_FROM, enter.scaleFrom, 0f)
        assertEquals(1f, enter.scaleTo, 0f)
        assertSame(Motion.EaseOut, enter.easing)
    }

    @Test
    fun `full motion exit is faster than enter and eased in`() {
        val enter = Motion.enterSpec()
        val exit = Motion.exitSpec()
        assertEquals(Motion.DUR_EXIT, exit.durationMs)
        assertTrue("exit must be faster than enter", exit.durationMs < enter.durationMs)
        val ratio = exit.durationMs.toDouble() / enter.durationMs
        assertTrue("exit/enter ratio must sit in 0.70..0.80, was $ratio", ratio in 0.70..0.80)
        assertSame(Motion.EaseIn, exit.easing)
        assertEquals(-Motion.ENTER_OFFSET_DP, exit.offsetDp, 0f)
        assertEquals(1f, exit.scaleFrom, 0f)
        assertEquals(Motion.ENTER_SCALE_FROM, exit.scaleTo, 0f)
    }

    @Test
    fun `full motion keeps the documented durations and springs`() {
        assertEquals(Motion.DUR_FAST, (Motion.quick<Float>() as TweenSpec<Float>).durationMillis)
        assertEquals(Motion.DUR_BASE, (Motion.base<Float>() as TweenSpec<Float>).durationMillis)
        assertEquals(Motion.DUR_ENTER, (Motion.enter<Float>() as TweenSpec<Float>).durationMillis)
        assertEquals(Motion.DUR_EXIT, (Motion.exit<Float>() as TweenSpec<Float>).durationMillis)
        assertEquals(Motion.DUR_SWAP, (Motion.swap<Float>() as TweenSpec<Float>).durationMillis)
        assertSame(Motion.EaseIn, (Motion.exit<Float>() as TweenSpec<Float>).easing)
        assertTrue(Motion.focus<Float>() is SpringSpec<*>)
        assertTrue(Motion.press<Float>() is SpringSpec<*>)
        assertEquals(Motion.DUR_PULSE, (Motion.pulse<Float>() as TweenSpec<Float>).durationMillis)
    }

    @Test
    fun `reduced motion collapses every spec to the floor`() {
        val specs = listOf(
            "quick" to Motion.quick<Float>(reduced = true),
            "base" to Motion.base<Float>(reduced = true),
            "enter" to Motion.enter<Float>(reduced = true),
            "exit" to Motion.exit<Float>(reduced = true),
            "swap" to Motion.swap<Float>(reduced = true),
            "focus" to Motion.focus<Float>(reduced = true),
            "press" to Motion.press<Float>(reduced = true),
        )
        for ((name, spec) in specs) {
            assertTrue("$name must become a tween under reduced motion, was ${spec::class.simpleName}", spec is TweenSpec<*>)
            val duration = (spec as TweenSpec<*>).durationMillis
            assertEquals("$name duration", Motion.DUR_REDUCED, duration)
            assertTrue("$name must not exceed 50ms", duration <= 50)
        }
    }

    @Test
    fun `reduced motion removes focus and press scale`() {
        assertEquals(1f, Motion.focusScale(reduced = true), 0f)
        assertEquals(1f, Motion.pressScale(reduced = true), 0f)
        assertEquals(Motion.FOCUS_SCALE, Motion.focusScale(), 0f)
        assertEquals(Motion.PRESS_SCALE, Motion.pressScale(), 0f)
        assertNotEquals(1f, Motion.FOCUS_SCALE)
    }

    @Test
    fun `stagger is spaced by forty milliseconds and capped under full motion`() {
        assertEquals(0, Motion.staggerDelayMs(0))
        assertEquals(Motion.STAGGER_MS, Motion.staggerDelayMs(1))
        assertEquals(Motion.STAGGER_MS * 3, Motion.staggerDelayMs(3))
        assertEquals(Motion.STAGGER_TOTAL_MS, Motion.staggerDelayMs(50))
        assertEquals(Motion.STAGGER_TOTAL_MS, Motion.staggerDelayMs(1_000))
        assertEquals(0, Motion.staggerDelayMs(-4))
        assertTrue("the whole wave must fit in 300ms", Motion.staggerDelayMs(50) <= 300)
    }

    @Test
    fun `stagger is disabled under reduced motion`() {
        assertEquals(0, Motion.staggerDelayMs(0, reduced = true))
        assertEquals(0, Motion.staggerDelayMs(7, reduced = true))
        assertEquals(0, Motion.staggerDelayMs(1_000, reduced = true))
    }

    @Test
    fun `continuous value animation is not collapsed by reduced motion`() {
        // A progress bar animates a value the user is reading; snapping it would misreport.
        assertTrue(Motion.linear<Float>(300) is TweenSpec<*>)
        assertEquals(300, (Motion.linear<Float>(300) as TweenSpec<Float>).durationMillis)
    }
}
