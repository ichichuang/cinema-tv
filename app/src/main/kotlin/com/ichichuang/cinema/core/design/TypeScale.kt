package com.ichichuang.cinema.core.design

import androidx.compose.ui.unit.sp

/**
 * Type scale for a 10-foot canvas (1280x720 logical, ~3m viewing distance).
 *
 * Absolute floor: body text is never below 20sp. At three metres 16sp body
 * copy is unreadable on a 720p television, and "unreadable" is not a taste
 * question. [caption] is deliberately reserved for auxiliary metadata that is
 * duplicated elsewhere (a badge repeated in the title), never for a sentence
 * the user is expected to read.
 *
 * Line heights are ~1.4x the size for body sizes and ~1.2x for display sizes:
 * CJK glyphs need less extra leading than Latin descenders but cannot take
 * tight leading without looking like a wall.
 */
object TypeScale {
    /** Auxiliary only (16sp). Never a sentence. */
    val caption = 16.sp

    /** Buttons, tabs, chips (18sp). */
    val label = 18.sp

    /** Body copy floor (20sp). */
    val body = 20.sp
    val bodyLine = 28.sp

    /** Emphasised body, list section titles (24sp). */
    val bodyLarge = 24.sp
    val bodyLargeLine = 34.sp

    /** Panel and page titles (28sp). */
    val title = 28.sp
    val titleLine = 36.sp

    /** Screen headline (34sp). */
    val titleLarge = 34.sp
    val titleLargeLine = 42.sp

    /** Hero title (44sp). */
    val display = 44.sp
    val displayLine = 52.sp
}
