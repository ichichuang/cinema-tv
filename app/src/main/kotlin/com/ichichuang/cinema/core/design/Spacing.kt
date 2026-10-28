package com.ichichuang.cinema.core.design

import androidx.compose.ui.unit.dp

/**
 * Spacing ladder — the only source of gaps and paddings.
 *
 * The grid is 2dp. The numeric tokens carry the dp value in their name so a
 * one-off position ("icon sits 6dp from its label") never turns into a literal
 * at the call site. The semantic aliases below cover the common rhythm used by
 * the 4dp-based layout spec; prefer them when they fit.
 *
 * Rule: a component that writes `12.dp` is a defect. Take the token.
 */
object Spacing {
    /** Half step, for hairlines-adjacent gaps (icon baseline vs text baseline). */
    val n2 = 2.dp
    val n4 = 4.dp
    val n6 = 6.dp
    val n8 = 8.dp
    val n10 = 10.dp
    val n12 = 12.dp
    val n14 = 14.dp
    val n16 = 16.dp
    val n18 = 18.dp
    val n20 = 20.dp
    val n24 = 24.dp
    val n28 = 28.dp
    val n32 = 32.dp
    val n36 = 36.dp
    val n48 = 48.dp

    // ── Semantic aliases (4dp base) ─────────────────────────────
    /** 1 grid step: the smallest gap that still reads as a gap on a TV. */
    val xs = n4

    /** 2 grid steps: icon to label, chip to chip. */
    val sm = n8

    /** 3 grid steps: default list gap. */
    val md = n12

    /** 4 grid steps: block gap. */
    val lg = n16

    /** 6 grid steps: section gap. */
    val xl = n24

    /** 8 grid steps: hero gap, page top padding. */
    val xxl = n32

    /** Screen side margin at 10-foot distance. */
    val page = n48
}
