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

    /**
     * Exit easing: slow start, fast finish. Leaving content must not linger --
     * the user already knows they dismissed it, so the tail is compressed.
     */
    val EaseIn: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    /** 时长等级（毫秒） */
    const val DUR_FAST = 140   // 小元素状态变化：chip 高亮、图标变色
    const val DUR_BASE = 200   // 标准过渡：条目出现
    const val DUR_ENTER = 280  // 页面级进入/浮层滑入
    const val DUR_SWAP = 260   // 大块内容换人（详情面板换片）

    /** Exits are ~75% of [DUR_ENTER]: leaving must feel faster than arriving. */
    const val DUR_EXIT = 210

    /**
     * Reduced-motion floor. Short enough to read as an instant state change,
     * long enough that a hard cut does not flicker on an OLED panel.
     */
    const val DUR_REDUCED = 40

    /** Delay between adjacent items of a staggered list entrance. */
    const val STAGGER_MS = 40

    /** Ceiling for a whole stagger wave: the tail of a long list must not wait. */
    const val STAGGER_TOTAL_MS = 300

    /** Skeleton shimmer cycle. Slow on purpose: a fast pulse reads as an error blink. */
    const val DUR_PULSE = 900

    /** Entrance displacement (dp) and start scale: fade + 8dp rise + 0.96 -> 1. */
    const val ENTER_OFFSET_DP = 8f
    const val ENTER_SCALE_FROM = 0.96f

    /** 焦点光晕的不透明度：细金边 + 一圈软光晕，深色背景上才"亮得起来" */
    const val GLOW_ALPHA = 0.20f

    /**
     * Resolved enter/exit numbers.
     *
     * Deliberately plain data instead of an `EnterTransition`: durations,
     * offsets and scale endpoints are the part that must be verifiable
     * (including "reduced means zero displacement"), and they are testable
     * without a composition.
     */
    data class Transition(
        val durationMs: Int,
        val easing: Easing,
        /** Signed vertical displacement in dp; positive is towards the bottom edge. */
        val offsetDp: Float,
        val scaleFrom: Float,
        val scaleTo: Float,
    )

    /**
     * Entrance: fade in while rising 8dp and scaling 0.96 -> 1, ease-out.
     * Under reduced motion: a 40ms cross-fade, no displacement, no scale.
     */
    fun enterSpec(reduced: Boolean = false): Transition =
        if (reduced) Transition(DUR_REDUCED, LinearEasing, 0f, 1f, 1f)
        else Transition(DUR_ENTER, EaseOut, ENTER_OFFSET_DP, ENTER_SCALE_FROM, 1f)

    /**
     * Exit: fade out while drifting up 8dp and scaling 1 -> 0.96, ease-in,
     * 75% of the entrance duration.
     */
    fun exitSpec(reduced: Boolean = false): Transition =
        if (reduced) Transition(DUR_REDUCED, LinearEasing, 0f, 1f, 1f)
        else Transition(DUR_EXIT, EaseIn, -ENTER_OFFSET_DP, 1f, ENTER_SCALE_FROM)

    /** Focus scale under reduced motion is 1f: nothing moves, the ring alone carries the state. */
    fun focusScale(reduced: Boolean = false): Float = if (reduced) 1f else FOCUS_SCALE

    /** Press feedback under reduced motion is 1f for the same reason. */
    fun pressScale(reduced: Boolean = false): Float = if (reduced) 1f else PRESS_SCALE

    /**
     * Entrance delay for item [index] of a staggered list.
     * Zero under reduced motion; clamped to [STAGGER_TOTAL_MS] otherwise.
     */
    fun staggerDelayMs(index: Int, reduced: Boolean = false): Int =
        if (reduced) 0 else (index.coerceAtLeast(0) * STAGGER_MS).coerceAtMost(STAGGER_TOTAL_MS)

    /** 焦点动效：带轻微回弹（弹簧），遥控器移动时有"肉感"而不是死板线性 */
    fun <T> focus(reduced: Boolean = false): FiniteAnimationSpec<T> =
        if (reduced) reducedSpec()
        else spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)

    /** 更快、无回弹：用于颜色/透明度等不该弹的东西 */
    fun <T> quick(reduced: Boolean = false): FiniteAnimationSpec<T> =
        if (reduced) reducedSpec() else tween(DUR_FAST, easing = EaseOut)

    fun <T> base(reduced: Boolean = false): FiniteAnimationSpec<T> =
        if (reduced) reducedSpec() else tween(DUR_BASE, easing = EaseOut)

    fun <T> enter(reduced: Boolean = false): FiniteAnimationSpec<T> =
        if (reduced) reducedSpec() else tween(DUR_ENTER, easing = EaseOut)

    /** Exit spec paired with [enter]: shorter, ease-in. */
    fun <T> exit(reduced: Boolean = false): FiniteAnimationSpec<T> =
        if (reduced) reducedSpec() else tween(DUR_EXIT, easing = EaseIn)

    /**
     * 按压反馈：比焦点更硬更快（刚度高、阻尼低）。
     * 遥控器按下去的那一瞬间必须"有回弹的实感" —— 这是电视上最容易缺、
     * 也最影响"这台电视好用吗"的一环。用弹簧而不是 tween：手感是"摁下去"而不是"变过去"。
     */
    fun <T> press(reduced: Boolean = false): FiniteAnimationSpec<T> =
        if (reduced) reducedSpec()
        else spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessHigh)

    /**
     * 必须**连续**的动画（播放进度条、详情页背景缓慢推近）：线性、无缓动。
     *
     * 返回类型刻意是 DurationBasedAnimationSpec（而不是更宽的 FiniteAnimationSpec）：
     * 只有"时长型"的动画才能塞进 infiniteRepeatable —— 类型窄一点，
     * 调用方（比如详情页的背景推近）就不用自己再写一遍 tween 了。
     *
     * Intentionally not routed through reduced motion: this animates a value the
     * user is reading (playback position), not a decorative entrance, and
     * snapping it would make the bar lie about where it is heading.
     */
    fun <T> linear(ms: Int): DurationBasedAnimationSpec<T> = tween(ms, easing = LinearEasing)

    /** 大块内容换人：淡入 + 微微上浮，用"换镜头"的方式替代硬切 */
    fun <T> swap(reduced: Boolean = false): FiniteAnimationSpec<T> =
        if (reduced) reducedSpec() else tween(DUR_SWAP, easing = EaseOut)

    /**
     * Skeleton shimmer cycle (one direction). Feed it to `infiniteRepeatable`
     * with [androidx.compose.animation.core.RepeatMode.Reverse].
     */
    fun <T> pulse(): DurationBasedAnimationSpec<T> = tween(DUR_PULSE, easing = EaseInOut)

    /** The single reduced-motion spec: a 40ms linear cross-fade, nothing else. */
    private fun <T> reducedSpec(): FiniteAnimationSpec<T> = tween(DUR_REDUCED, easing = LinearEasing)
}
