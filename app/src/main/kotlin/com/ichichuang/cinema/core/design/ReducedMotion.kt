package com.ichichuang.cinema.core.design

/**
 * Whether the user asked for reduced motion.
 *
 * Two independent sources, either one wins:
 *  - the in-app "减弱动效" setting ([userSetting]);
 *  - the platform animator duration scale being 0 (`Settings.Global.ANIMATOR_DURATION_SCALE`),
 *    which is what "remove animations" in the system accessibility settings sets.
 *
 * Kept as a pure function with primitives so the truth table is JVM-testable:
 * the Android read lives behind [AnimatorScaleSource].
 */
fun shouldReduceMotion(userSetting: Boolean, animatorDurationScale: Float): Boolean =
    userSetting || animatorDurationScale == 0f

/**
 * Seam over the platform animator scale.
 *
 * Tests never construct the Android implementation; production reads it once
 * per composition and feeds the value into [shouldReduceMotion].
 */
interface AnimatorScaleSource {
    /** 1f = animations at normal speed, 0f = animations removed. */
    fun animatorDurationScale(): Float
}
