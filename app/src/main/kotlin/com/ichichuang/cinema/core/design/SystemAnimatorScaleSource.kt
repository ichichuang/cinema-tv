package com.ichichuang.cinema.core.design

import android.content.ContentResolver
import android.provider.Settings

/**
 * Reads `Settings.Global.ANIMATOR_DURATION_SCALE`.
 *
 * 1f when the value is missing or unreadable: a device that never set the
 * global must not silently lose its animations.
 */
class SystemAnimatorScaleSource(private val resolver: ContentResolver) : AnimatorScaleSource {
    override fun animatorDurationScale(): Float =
        runCatching { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
            .getOrDefault(1f)
            .takeIf { it >= 0f } ?: 1f
}
