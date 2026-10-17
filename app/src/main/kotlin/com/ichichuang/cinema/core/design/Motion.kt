package com.ichichuang.cinema.core.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * 全应用动效规范。
 *
 * 为什么要有这个文件：TV 上的「高级感」80% 来自动效的时机和曲线，
 * 而不是库。散落在页面里的 tween(180) 会让每个页面手感都不一样。
 * 规则：任何动画只能从这里取值。
 */
object Motion {

    /** 焦点缩放目标比例：1.06 是"能感觉到但不夸张"的甜点 */
    const val FOCUS_SCALE = 1.06f

    /** 按下的缩放比例 */
    const val PRESS_SCALE = 0.97f

    /** Apple 风格缓动：起步快、收尾稳，比默认的 LinearEasing/AccelerateDecelerate 高级得多 */
    val EaseOut: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EaseInOut: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** 时长等级（毫秒） */
    const val DUR_FAST = 140   // 小元素状态变化：chip 高亮、图标变色
    const val DUR_BASE = 200   // 标准过渡：条目出现
    const val DUR_ENTER = 280  // 页面级进入/浮层滑入

    /** 焦点动效：带轻微回弹（弹簧），遥控器移动时有"肉感"而不是死板线性 */
    fun <T> focus(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)

    /** 更快、无回弹：用于颜色/透明度等不该弹的东西 */
    fun <T> quick(): FiniteAnimationSpec<T> = tween(DUR_FAST, easing = EaseOut)
    fun <T> base(): FiniteAnimationSpec<T> = tween(DUR_BASE, easing = EaseOut)
    fun <T> enter(): FiniteAnimationSpec<T> = tween(DUR_ENTER, easing = EaseOut)
}
