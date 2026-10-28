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
 * The value handed to [ProvideReducedMotion] at the probe root.
 *
 * The probe runtime is nullable at the root (it is only constructed once the
 * private debug authorization chain has passed), so the effective signal it
 * exposes is `Boolean?`. Outside the probe the runtime is `null` and the root
 * must provide `false` — full motion — because there is no reduced-motion UI
 * in the production path to drive it. Extracted so the null/non-null mapping
 * is a JVM-tested pure function instead of an inline `== true` that a reviewer
 * could silently hard-code to `false`.
 */
fun reducedMotionForRoot(runtimeSignal: Boolean?): Boolean = runtimeSignal == true

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
