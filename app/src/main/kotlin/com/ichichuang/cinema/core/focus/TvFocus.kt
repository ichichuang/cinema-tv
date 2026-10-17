package com.ichichuang.cinema.core.focus

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import com.ichichuang.cinema.core.design.Gold
import com.ichichuang.cinema.core.design.Motion
import com.ichichuang.cinema.core.design.rememberTvMetrics

/**
 * ⭐ 焦点系统 —— 这个工程的"高级感"地基。
 *
 * TV 交互的核心只有一个：焦点。所有可点组件都必须长一样：放大 + 金边 + 回弹。
 * 所以把它封装成一行调用，新页面零成本获得统一手感。
 *
 * 正确用法（顺序很重要）：
 * ```
 * val radius = m.dp(14f)
 * Box(
 *     Modifier
 *         .tvFocus(radius)                      // ← 放最前
 *         .clip(RoundedCornerShape(radius))     // ← 裁剪
 *         .background(brush)                    // ← 背景
 *         .clickable { ... }                    // ← 点击
 * )
 * ```
 * 边框由本函数在最顶层绘制（drawWithContent 后画），
 * 所以不会被背景盖住 —— 这是很多自绘焦点组件会踩的坑。
 *
 * 注意：这里收的是「圆角半径」而不是「Shape」。
 * 原因：Compose 1.12 移除了 drawOutline 扩展函数（已用字节码确认），
 * 所以边框改用 DrawScope 的成员函数 drawRoundRect 绘制 —— 它只需要半径。
 *
 * @param cornerRadius 圆角半径，必须和调用方 clip 用的圆角一致
 * @param scale        放大比例，默认 1.06（Motion.FOCUS_SCALE）
 * @param borderColor  焦点边框色，默认暖金
 * @param onFocusChanged 焦点变化回调（用来更新右侧详情等联动内容）
 */
@Composable
fun Modifier.tvFocus(
    cornerRadius: Dp,
    scale: Float = Motion.FOCUS_SCALE,
    borderColor: Color = Gold,
    borderWidthDp: Float = 2f,
    onFocusChanged: ((Boolean) -> Unit)? = null,
): Modifier {
    val m = rememberTvMetrics()
    var focused by remember { mutableStateOf(false) }
    val callback by rememberUpdatedState(onFocusChanged)

    val progress by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = Motion.focus(),
        label = "tvFocus",
    )

    val borderWidth = m.dp(borderWidthDp)

    return this
        .graphicsLayer {
            val s = 1f + (scale - 1f) * progress
            scaleX = s
            scaleY = s
        }
        .zIndex(if (focused) 1f else 0f)
        .onFocusChanged {
            focused = it.isFocused
            callback?.invoke(it.isFocused)
        }
        .drawWithContent {
            drawContent()
            if (progress > 0.01f) {
                val sw = borderWidth.toPx()
                val r = cornerRadius.toPx()
                // 描边压在内容外沿：往里缩半个线宽，避免被父容器裁掉一半
                val inset = sw / 2f
                drawRoundRect(
                    color = borderColor,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - sw, size.height - sw),
                    cornerRadius = CornerRadius((r - inset).coerceAtLeast(0f)),
                    alpha = progress,
                    style = Stroke(width = sw),
                )
            }
        }
}

/** 常用于给列表/网格留出焦点放大不被裁掉的呼吸空间 */
val FocusBleed: PaddingValues
    @Composable get() {
        val m = rememberTvMetrics()
        return PaddingValues(m.dp(14f))
    }
