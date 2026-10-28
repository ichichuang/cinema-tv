package com.ichichuang.cinema.core.design

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The live "should animations be reduced" signal for a subtree.
 *
 * Two independent sources, merged through [shouldReduceMotion]:
 *  - the in-app "减弱动效" setting, carried by [userSetting];
 *  - the platform animator duration scale, read through [scaleSource].
 *
 * The class is deliberately a thin, constructor-injected wrapper so the whole
 * chain is JVM-testable without an Android `Context`: a test injects a fake
 * `AnimatorScaleSource` and a `MutableStateFlow<Boolean>`, then asserts that a
 * change to the setting re-emits [reduced] (the "setting changes -> value
 * changes" contract the probe root depends on).
 */
class ReduceMotionController(
    private val scaleSource: AnimatorScaleSource,
    private val userSetting: Flow<Boolean>,
) {
    /**
     * Emits `true` when the user enabled the setting OR the platform scale is 0.
     * Re-evaluated on every setting emission; the scale is read fresh per emission
     * so a mid-session accessibility change is not missed.
     */
    val reduced: Flow<Boolean> = userSetting.map { setting ->
        shouldReduceMotion(setting, scaleSource.animatorDurationScale())
    }
}
