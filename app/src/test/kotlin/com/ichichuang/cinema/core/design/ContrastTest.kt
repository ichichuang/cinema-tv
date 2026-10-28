package com.ichichuang.cinema.core.design

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2.1 contrast ratios for the semantic palette.
 *
 * The colour values are frozen (R18 does not repaint the product), so this test
 * is a *report* with hard assertions on the pairs the UI actually puts on
 * screen. If a pair ever drops below its floor the build fails, and the message
 * carries the measured ratio so nobody has to re-derive it.
 */
class ContrastTest {

    /** WCAG relative luminance of an opaque colour. */
    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun contrast(foreground: Color, background: Color): Double {
        val a = luminance(foreground)
        val b = luminance(background)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    /** Flatten a translucent foreground onto its background, as the compositor does. */
    private fun composite(foreground: Color, background: Color): Color {
        val alpha = foreground.alpha
        fun mix(f: Float, b: Float) = f * alpha + b * (1f - alpha)
        return Color(mix(foreground.red, background.red), mix(foreground.green, background.green), mix(foreground.blue, background.blue))
    }

    private data class Pair(val name: String, val foreground: Color, val background: Color, val floor: Double)

    private val textPairs = listOf(
        Pair("textPrimary/background", Palette.textPrimary, Palette.background, 4.5),
        Pair("textPrimary/surface", Palette.textPrimary, Palette.surface, 4.5),
        Pair("textPrimary/surfaceRaised", Palette.textPrimary, Palette.surfaceRaised, 4.5),
        Pair("textSecondary/background", Palette.textSecondary, Palette.background, 4.5),
        Pair("textSecondary/surface", Palette.textSecondary, Palette.surface, 4.5),
        Pair("textSecondary/surfaceRaised", Palette.textSecondary, Palette.surfaceRaised, 4.5),
        Pair("textBody/background", Palette.textBody, Palette.background, 4.5),
        Pair("textBody/surface", Palette.textBody, Palette.surface, 4.5),
        Pair("accent/background", Palette.accent, Palette.background, 4.5),
        Pair("accent/surface", Palette.accent, Palette.surface, 4.5),
        Pair("success/background", Palette.success, Palette.background, 4.5),
        Pair("success/surface", Palette.success, Palette.surface, 4.5),
        Pair("warning/background", Palette.warning, Palette.background, 4.5),
        Pair("warning/surface", Palette.warning, Palette.surface, 4.5),
        Pair("danger/background", Palette.danger, Palette.background, 4.5),
        Pair("danger/surface", Palette.danger, Palette.surface, 4.5),
        // Button label sits on the accent fill.
        Pair("background/accent", Palette.background, Palette.accent, 4.5),
        // Non-text: the focus ring is the only signal that a control is live.
        Pair("focusRing/background", Palette.focusRing, Palette.background, 3.0),
        Pair("focusRing/surfaceRaised", Palette.focusRing, Palette.surfaceRaised, 3.0),
    )

    @Test
    fun `every required pair meets its WCAG floor`() {
        val report = StringBuilder("WCAG contrast report:\n")
        val failures = mutableListOf<String>()
        for (pair in textPairs) {
            val ratio = contrast(pair.foreground, pair.background)
            val verdict = if (ratio >= pair.floor) "PASS" else "FAIL"
            report.append(
                String.format("  %-30s %6.2f:1  floor %.1f  %s%n", pair.name, ratio, pair.floor, verdict),
            )
            if (ratio < pair.floor) {
                failures += "${pair.name} = ${"%.2f".format(ratio)}:1 < ${pair.floor}"
            }
        }
        // Reference only: the unfocused border is decorative (a surface is also delimited by
        // its own value difference), so it is reported but not asserted.
        for (background in listOf(Palette.background, Palette.surface)) {
            val flat = composite(Palette.border, background)
            report.append(String.format("  %-30s %6.2f:1  decorative%n", "border on flattened surface", contrast(flat, background)))
        }
        println(report)
        assertTrue("contrast failures:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun `semantic palette points at the frozen brand values`() {
        assertTrue(Palette.background == Ink)
        assertTrue(Palette.surface == Panel)
        assertTrue(Palette.surfaceRaised == PanelHi)
        assertTrue(Palette.textPrimary == TextMain)
        assertTrue(Palette.textSecondary == TextSub)
        assertTrue(Palette.accent == Gold)
        assertTrue(Palette.focusRing == Gold)
        // Secondary text must stay dimmer than primary text, or the hierarchy disappears.
        assertTrue(luminance(Palette.textSecondary) < luminance(Palette.textPrimary))
        assertTrue(luminance(Palette.background) < luminance(Palette.surface))
        assertTrue(luminance(Palette.surface) < luminance(Palette.surfaceRaised))
    }
}
