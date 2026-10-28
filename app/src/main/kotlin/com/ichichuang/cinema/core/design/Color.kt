package com.ichichuang.cinema.core.design

import androidx.compose.ui.graphics.Color

// ── 全应用唯一色板 ────────────────────────────────────────────
// 原则：Netflix 的深色沉浸 + Apple TV 的克制。
// 任何页面都不许自己 new Color，一律从这里取。

/** 背景：近纯黑。电视上黑色必须干净，发灰立刻廉价 */
val Ink = Color(0xFF0A0A0C)

/** 卡片 / 浮层：靠明度差分层，不靠描边 */
val Panel = Color(0xFF16161A)

/** 卡片高亮态 */
val PanelHi = Color(0xFF22222A)

/** 强调色：暖金。Apple 的红太文艺、Netflix 的红会脏海报，暖金和任何海报都和谐 */
val Gold = Color(0xFFD4A056)

/** 主文字 */
val TextMain = Color(0xFFF5F5F7)

/** 次级文字 */
val TextSub = Color(0xFF8E8E93)

/** 长文本（简介） */
val TextBody = Color(0xFFC7C7CC)

/** Status: available / confirmed (10.8:1 on Ink). */
val Success = Color(0xFF8BD17C)

/** Status: degraded but usable (10.7:1 on Ink). */
val Warning = Color(0xFFE5B85C)

/**
 * Status: failed / destructive (6.6:1 on Ink).
 * Same value the old library filter panel already used for its error line,
 * so taking the token changes nothing visually.
 */
val Danger = Color(0xFFE57373)

/**
 * Unfocused outline. Decorative: it delimits a surface that is also delimited
 * by its own value difference, so it is not required to meet the 3:1
 * non-text contrast floor (see docs/design/TOKENS-AUDIT.md).
 */
val Border: Color = TextSub.copy(alpha = 0.55f)

/** 右侧详情浮层的遮罩：从透明过渡到实底 */
val Scrim: (Float) -> Color = { alpha -> Ink.copy(alpha = alpha) }

/**
 * Semantic names for the palette above.
 *
 * The raw values stay where they are — every screen that already imports
 * Ink/Panel/Gold keeps working and keeps its exact pixels. New UI imports
 * [Palette] instead, so "which grey is the secondary text" has one answer.
 */
object Palette {
    /** Page background. */
    val background = Ink

    /** Card / panel / sheet surface. */
    val surface = Panel

    /** Raised surface: focused card, selected row. */
    val surfaceRaised = PanelHi

    /** Primary text. */
    val textPrimary = TextMain

    /** Secondary text: labels, metadata, status lines. */
    val textSecondary = TextSub

    /** Long-form copy: synopsis, descriptions. */
    val textBody = TextBody

    /** Accent: selection underline, focus ring, primary action. */
    val accent = Gold

    val success = Success
    val warning = Warning
    val danger = Danger

    /** Unfocused outline (decorative, see [Border]). */
    val border = Border

    /** Focus ring colour. Identical to the accent on purpose: one "this is live" colour. */
    val focusRing = Gold

    /** Scrim for overlays: Ink at the requested opacity. */
    val scrim: (Float) -> Color = Scrim
}
