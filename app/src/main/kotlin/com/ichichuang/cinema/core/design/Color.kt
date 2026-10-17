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

/** 右侧详情浮层的遮罩：从透明过渡到实底 */
val Scrim: (Float) -> Color = { alpha -> Ink.copy(alpha = alpha) }
