package com.ichichuang.cinema.core.design

import androidx.compose.ui.unit.dp

/**
 * Fixed component sizes that are not rhythm (spacing) and not radius.
 * Keeping them here means a tile never re-declares its own width.
 */
object Sizes {
    /** Hairline divider (may also be written as 1.dp; kept for symmetry). */
    val hairline = 1.dp

    /** Selection indicator: nav underline, playback progress bar (3dp). */
    val indicator = 3.dp

    /** Selected dot on a chip (8dp). */
    val dot = 8.dp

    /** Skeleton dot (10dp). */
    val dotLarge = 10.dp

    /** Selected-state underline width under a nav label (24dp). */
    val navUnderline = 24.dp
}

/** Pure-stroke icon sizes on the 24dp grid. */
object IconSize {
    /** Inline icon next to a label (18dp). */
    val inline = 18.dp

    /** Root navigation icon (20dp). */
    val nav = 20.dp

    /** Placeholder icon inside a poster that has no artwork (26dp). */
    val placeholder = 26.dp
}

/**
 * Poster tile geometry. The landscape pair is 16:9; the portrait pair is 2:3.
 * Wide is the hero-size landscape tile used by the first row.
 */
object TileSize {
    val landscapeWidth = 208.dp
    val landscapeHeight = 117.dp
    val wideWidth = 232.dp
    val wideHeight = 130.dp
    val portraitWidth = 120.dp
    val portraitHeight = 180.dp
}
