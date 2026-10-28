package com.ichichuang.cinema.core.design

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The platform animator scale is read behind a lambda so the fallback rules are
 * verifiable on the JVM: an unreadable or nonsense global must not silently
 * remove every animation in the app, and a real 0 must survive.
 */
class SystemAnimatorScaleSourceTest {

    @Test
    fun `a normal scale is passed through`() {
        assertEquals(1f, SystemAnimatorScaleSource { 1f }.animatorDurationScale(), 0f)
        assertEquals(0.5f, SystemAnimatorScaleSource { 0.5f }.animatorDurationScale(), 0f)
    }

    @Test
    fun `a zero scale survives`() {
        // "Remove animations" in the system accessibility settings writes 0.
        assertEquals(0f, SystemAnimatorScaleSource { 0f }.animatorDurationScale(), 0f)
    }

    @Test
    fun `a missing global falls back to one`() {
        assertEquals(1f, SystemAnimatorScaleSource { null }.animatorDurationScale(), 0f)
    }

    @Test
    fun `an unreadable global falls back to one`() {
        val source = SystemAnimatorScaleSource { throw SecurityException("settings provider denied") }
        assertEquals(1f, source.animatorDurationScale(), 0f)
    }

    @Test
    fun `a negative scale is not trusted`() {
        assertEquals(1f, SystemAnimatorScaleSource { -2f }.animatorDurationScale(), 0f)
    }

    @Test
    fun `the read value decides reduction together with the user setting`() {
        val removed = SystemAnimatorScaleSource { 0f }.animatorDurationScale()
        assertEquals(true, shouldReduceMotion(userSetting = false, animatorDurationScale = removed))

        val normal = SystemAnimatorScaleSource { 1f }.animatorDurationScale()
        assertEquals(false, shouldReduceMotion(userSetting = false, animatorDurationScale = normal))
    }
}
