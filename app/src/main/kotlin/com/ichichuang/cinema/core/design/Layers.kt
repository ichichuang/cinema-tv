package com.ichichuang.cinema.core.design

import androidx.compose.ui.unit.dp

/**
 * Stacking order (z). Named layers prevent `zIndex(2f)` vs `zIndex(3f)`
 * arguments between sibling screens: only the relative order matters.
 */
object Layers {
    const val content = 0f

    /** A focused tile is lifted above its neighbours so its glow is not clipped. */
    const val nav = 1f

    /** Bottom sheets, side panels, scrims. */
    const val overlay = 10f

    /** Modal dialogs: above overlays, below toasts. */
    const val dialog = 20f

    /** Transient confirmations: always on top, always short-lived. */
    const val toast = 30f
}

/**
 * Surface elevation. The visual system separates layers by value difference
 * (Ink -> Panel -> PanelHi), so elevation stays small: it exists to cast a
 * shadow under floating panels, not to fake a light source.
 */
object Elevation {
    val content = 0.dp
    val nav = 1.dp
    val overlay = 6.dp
    val dialog = 12.dp
    val toast = 16.dp
}
