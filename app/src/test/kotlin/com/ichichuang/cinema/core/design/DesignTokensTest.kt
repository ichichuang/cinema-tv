package com.ichichuang.cinema.core.design

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Structural checks on the token ladders.
 *
 * These are not "does the constant equal itself" tests: they pin the rules the
 * design system promises (2dp grid, ascending scale, TV body-size floor) so a
 * later edit that breaks the rhythm fails here instead of on a television.
 */
class DesignTokensTest {

    @Test
    fun `spacing ladder stays on the 2dp grid`() {
        val ladder = listOf(
            Spacing.n2, Spacing.n4, Spacing.n6, Spacing.n8, Spacing.n10, Spacing.n12,
            Spacing.n14, Spacing.n16, Spacing.n18, Spacing.n20, Spacing.n24, Spacing.n32, Spacing.n48,
        )
        ladder.forEach { token ->
            val value = token.value
            assertEquals("$value dp is off the 2dp grid", 0f, value % 2f, 0.0001f)
        }
        assertEquals(ladder.map { it.value }, ladder.map { it.value }.sorted())
        assertEquals(ladder.map { it.value }.distinct().size, ladder.size)
    }

    @Test
    fun `spacing aliases keep the 4dp base contract`() {
        assertEquals(4f, Spacing.xs.value, 0f)
        assertEquals(8f, Spacing.sm.value, 0f)
        assertEquals(12f, Spacing.md.value, 0f)
        assertEquals(16f, Spacing.lg.value, 0f)
        assertEquals(24f, Spacing.xl.value, 0f)
        assertEquals(32f, Spacing.xxl.value, 0f)
        assertEquals(48f, Spacing.page.value, 0f)
    }

    @Test
    fun `radii ascend and full is larger than any surface radius`() {
        val ladder = listOf(Radii.xs, Radii.sm, Radii.md, Radii.lg, Radii.xl)
        assertEquals(ladder.map { it.value }, ladder.map { it.value }.sorted())
        ladder.forEach { assertTrue("${it.value} must be smaller than full", it.value < Radii.full.value) }
        assertEquals(999f, Radii.full.value, 0f)
    }

    @Test
    fun `tile sizes keep their aspect ratios`() {
        val landscape = TileSize.landscapeWidth.value / TileSize.landscapeHeight.value
        assertTrue("landscape tile must be 16:9, was $landscape", abs(landscape - 16f / 9f) < 0.02f)
        val wide = TileSize.wideWidth.value / TileSize.wideHeight.value
        assertTrue("wide tile must be 16:9, was $wide", abs(wide - 16f / 9f) < 0.02f)
        val portrait = TileSize.portraitWidth.value / TileSize.portraitHeight.value
        assertTrue("portrait tile must be 2:3, was $portrait", abs(portrait - 2f / 3f) < 0.02f)
        assertTrue(TileSize.wideWidth.value > TileSize.landscapeWidth.value)
    }

    @Test
    fun `type scale keeps body copy readable at ten feet`() {
        val readable = listOf(
            TypeScale.body, TypeScale.bodyLarge, TypeScale.title, TypeScale.titleLarge, TypeScale.display,
        )
        readable.forEach { size ->
            assertTrue("body-level text must be at least 20sp, was ${size.value}sp", size.value >= 20f)
        }
        assertTrue(TypeScale.label.value >= 18f)
        val ordered = readable.map { it.value }
        assertEquals(ordered, ordered.sorted())
        assertEquals(ordered.distinct().size, ordered.size)
        // Line height must leave room for CJK glyphs: at least 1.2x for every size that declares one.
        assertTrue(TypeScale.bodyLine.value >= TypeScale.body.value * 1.2f)
        assertTrue(TypeScale.titleLine.value >= TypeScale.title.value * 1.2f)
    }

    @Test
    fun `layers order content below nav below overlay below dialog below toast`() {
        val order = listOf(Layers.content, Layers.nav, Layers.overlay, Layers.dialog, Layers.toast)
        assertEquals(order, order.sorted())
        assertEquals(order.distinct().size, order.size)
        val elevations = listOf(Elevation.content, Elevation.nav, Elevation.overlay, Elevation.dialog, Elevation.toast)
        assertEquals(elevations.map { it.value }, elevations.map { it.value }.sorted())
    }

    @Test
    fun `motion tokens keep the documented ratio between enter and exit`() {
        val ratio = Motion.DUR_EXIT.toDouble() / Motion.DUR_ENTER
        assertTrue("exit must be ~75% of enter, was $ratio", ratio in 0.70..0.80)
        assertTrue(Motion.DUR_REDUCED in 1..50)
        assertTrue(Motion.STAGGER_MS > 0)
        // The whole stagger wave has to finish inside 300ms, independent of how long a
        // single page transition takes.
        assertTrue(Motion.STAGGER_TOTAL_MS in Motion.STAGGER_MS..300)
        assertTrue(Motion.ENTER_OFFSET_DP > 0f && Motion.ENTER_OFFSET_DP <= 12f)
        assertTrue(Motion.ENTER_SCALE_FROM in 0.90f..0.99f)
        assertEquals(Spacing.n4, Spacing.xs)
        assertEquals(4.dp, Spacing.xs)
        assertEquals(20.sp, TypeScale.body)
    }
}
