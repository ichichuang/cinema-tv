package com.ichichuang.cinema.core.design

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Whether every animation in this subtree must be reduced.
 *
 * Provided once near the root (see the settings screen for the switch and the
 * platform scale read). Components must read it instead of asking the platform
 * again, so the whole screen changes mode in the same frame.
 */
val LocalReducedMotion = compositionLocalOf { false }

@Composable
fun ProvideReducedMotion(reduced: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalReducedMotion provides reduced, content = content)
}

/**
 * Enter transition for a page, sheet or dialog: fade + 8dp rise + 0.96 -> 1.
 *
 * Only alpha, scale and translation change, and all three are done by the
 * animation system on a graphics layer: no layout pass per frame.
 * Under reduced motion the movement is dropped and only the fade remains.
 */
@Composable
fun rememberMotionEnter(reduced: Boolean = LocalReducedMotion.current): EnterTransition {
    val density = LocalDensity.current
    return remember(reduced, density) {
        val spec = Motion.enterSpec(reduced)
        val fade = tween<Float>(spec.durationMs, easing = spec.easing)
        if (reduced) {
            fadeIn(fade)
        } else {
            val offsetPx = with(density) { spec.offsetDp.dp.roundToPx() }
            val slide = tween<IntOffset>(spec.durationMs, easing = spec.easing)
            fadeIn(fade) +
                slideInVertically(slide) { offsetPx } +
                scaleIn(fade, initialScale = spec.scaleFrom)
        }
    }
}

/**
 * Exit transition paired with [rememberMotionEnter]: shorter (75%), ease-in,
 * fade down/up without new layout.
 */
@Composable
fun rememberMotionExit(reduced: Boolean = LocalReducedMotion.current): ExitTransition {
    val density = LocalDensity.current
    return remember(reduced, density) {
        val spec = Motion.exitSpec(reduced)
        val fade = tween<Float>(spec.durationMs, easing = spec.easing)
        if (reduced) {
            fadeOut(fade)
        } else {
            val offsetPx = with(density) { spec.offsetDp.dp.roundToPx() }
            val slide = tween<IntOffset>(spec.durationMs, easing = spec.easing)
            fadeOut(fade) +
                slideOutVertically(slide) { offsetPx } +
                scaleOut(fade, targetScale = spec.scaleTo)
        }
    }
}
