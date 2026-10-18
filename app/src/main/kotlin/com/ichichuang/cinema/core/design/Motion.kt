package com.ichichuang.cinema.core.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
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
    const val DUR_SWAP = 260   // 大块内容换人（详情面板换片）

    /** 焦点光晕的不透明度：细金边 + 一圈软光晕，深色背景上才"亮得起来" */
    const val GLOW_ALPHA = 0.20f

    /** 焦点动效：带轻微回弹（弹簧），遥控器移动时有"肉感"而不是死板线性 */
    fun <T> focus(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)

    /** 更快、无回弹：用于颜色/透明度等不该弹的东西 */
    fun <T> quick(): FiniteAnimationSpec<T> = tween(DUR_FAST, easing = EaseOut)
    fun <T> base(): FiniteAnimationSpec<T> = tween(DUR_BASE, easing = EaseOut)
    fun <T> enter(): FiniteAnimationSpec<T> = tween(DUR_ENTER, easing = EaseOut)

    /**
     * 按压反馈：比焦点更硬更快（刚度高、阻尼低）。
     * 遥控器按下去的那一瞬间必须"有回弹的实感" —— 这是电视上最容易缺、
     * 也最影响"这台电视好用吗"的一环。用弹簧而不是 tween：手感是"摁下去"而不是"变过去"。
     */
    fun <T> press(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessHigh)

    /**
     * 必须**连续**的动画（播放进度条、详情页背景缓慢推近）：线性、无缓动。
     *
     * 返回类型刻意是 DurationBasedAnimationSpec（而不是更宽的 FiniteAnimationSpec）：
     * 只有"时长型"的动画才能塞进 infiniteRepeatable —— 类型窄一点，
     * 调用方（比如详情页的背景推近）就不用自己再写一遍 tween 了。
     */
    fun <T> linear(ms: Int): DurationBasedAnimationSpec<T> = tween(ms, easing = LinearEasing)

    /** 大块内容换人：淡入 + 微微上浮，用"换镜头"的方式替代硬切 */
    fun <T> swap(): FiniteAnimationSpec<T> = tween(DUR_SWAP, easing = EaseOut)
}
