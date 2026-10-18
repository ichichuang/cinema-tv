package com.ichichuang.cinema.core.ui

import android.graphics.Bitmap
import android.util.LruCache

/**
 * 「字块封面」识别。
 *
 * 背景：有些片源站没拿到真实海报时，会自动生成一张「纯色底 + 片子首字」的封面图
 * （品红/绿/蓝的大色块上摆一个字）。这种图在深色海报墙里非常扎眼，
 * 看起来像排版坏了 —— 其实图是站点给的，但我们能认出来，换成一个安静的深色块。
 *
 * 判定方式（缩到 32×48 的小图上统计，2bit/通道量化）：
 * 1. 颜色极单调：不同颜色 ≤ [MAX_COLORS] 个桶（真实海报的照片细节天然几十个桶）；
 * 2. 一个主色占据 ≥ [MIN_TOP_RATIO] 的像素（字和底同色系会被量化进同一个桶，占比更高）；
 * 3. 主色是鲜艳的中亮度色（饱和度够、不太暗）——灰/黑/白的默认图不动，它们本来就不扎眼。
 *
 * 结论按 URL 缓存：同一张图只分析一次，滚动来回都不重新算。
 */
internal object TextCoverGuard {

    private const val W = 32
    private const val H = 48
    private const val MAX_COLORS = 8
    private const val MIN_TOP_RATIO = 0.55f
    private const val MIN_SAT = 80
    private const val MIN_VALUE = 64

    /** url → 是否字块封面。数量不多（只有海报墙见过的图），256 条够任何列表翻很久 */
    private val cache = LruCache<String, Boolean>(256)

    fun cached(url: String): Boolean? = cache.get(url)

    fun put(url: String, isTextCover: Boolean) {
        cache.put(url, isTextCover)
    }

    /**
     * 看一眼就给结论。调用方放后台线程；任何异常都当作"不是字块封面"（宁可原样显示，不乱换）。
     */
    fun detect(image: Bitmap): Boolean = runCatching {
        // Coil 默认解码成硬件位图，getPixels/copy 前先落成软件位图
        val soft = if (image.config == Bitmap.Config.HARDWARE) {
            image.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            image
        } ?: return@runCatching false

        val scaled = Bitmap.createScaledBitmap(soft, W, H, false)
        val px = IntArray(W * H)
        scaled.getPixels(px, 0, W, 0, 0, W, H)
        if (scaled !== soft) scaled.recycle()
        if (soft !== image) soft.recycle()

        val count = HashMap<Int, Int>(16)
        val sumR = HashMap<Int, Long>(16)
        val sumG = HashMap<Int, Long>(16)
        val sumB = HashMap<Int, Long>(16)
        for (p in px) {
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF
            val q = ((r ushr 6) shl 4) or ((g ushr 6) shl 2) or (b ushr 6)
            count[q] = (count[q] ?: 0) + 1
            sumR[q] = (sumR[q] ?: 0L) + r
            sumG[q] = (sumG[q] ?: 0L) + g
            sumB[q] = (sumB[q] ?: 0L) + b
        }

        if (count.size > MAX_COLORS) return@runCatching false

        val top = count.entries.maxByOrNull { it.value } ?: return@runCatching false
        if (top.value.toFloat() / px.size < MIN_TOP_RATIO) return@runCatching false

        val n = top.value.toLong()
        val r = (sumR[top.key] ?: 0L) / n
        val g = (sumG[top.key] ?: 0L) / n
        val b = (sumB[top.key] ?: 0L) / n
        val sat = maxOf(r, g, b) - minOf(r, g, b)
        val value = maxOf(r, g, b)

        sat >= MIN_SAT && value >= MIN_VALUE
    }.getOrDefault(false)
}
