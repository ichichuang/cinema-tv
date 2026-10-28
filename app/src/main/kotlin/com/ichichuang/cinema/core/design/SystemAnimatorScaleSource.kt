package com.ichichuang.cinema.core.design

import android.content.ContentResolver
import android.provider.Settings

/**
 * Reads `Settings.Global.ANIMATOR_DURATION_SCALE`.
 *
 * 1f when the value is missing or unreadable: a device that never set the
 * global must not silently lose its animations, and neither must a device whose
 * settings provider refuses the read.
 *
 * The platform call sits behind a reader lambda instead of in the class body, so
 * the fallback rules (missing / unreadable / negative) are exercised on the JVM
 * without an Android `ContentResolver`.
 */
class SystemAnimatorScaleSource(private val readScale: () -> Float?) : AnimatorScaleSource {

    constructor(resolver: ContentResolver) : this({
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, DEFAULT_SCALE)
    })

    override fun animatorDurationScale(): Float =
        runCatching { readScale() }
            .getOrNull()
            ?.takeIf { scale -> scale >= 0f }
            ?: DEFAULT_SCALE

    companion object {
        /** Animations at normal speed: used when nothing readable is stored. */
        const val DEFAULT_SCALE = 1f
    }
}
