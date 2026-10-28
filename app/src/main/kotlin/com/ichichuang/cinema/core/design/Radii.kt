package com.ichichuang.cinema.core.design

import androidx.compose.ui.unit.dp

/**
 * Corner radii. [full] is a capsule: it is larger than any surface we draw, so
 * a rounded rect clipped with it always ends in a half-circle end cap.
 */
object Radii {
    /** Chip-sized radius (6dp). */
    val xs = 6.dp

    /** Small control (8dp). */
    val sm = 8.dp

    /** Poster tile / nav tile (10dp). */
    val md = 10.dp

    /** Focus outline on a poster tile (12dp). */
    val lg = 12.dp

    /** Floating panel (14dp). */
    val xl = 14.dp

    /** Capsule / pill. */
    val full = 999.dp
}
