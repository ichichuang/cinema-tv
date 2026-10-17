package com.ichichuang.cinema.core.design

/**
 * 毫秒 → "12:34" / "1:02:03"。
 *
 * 放在 core 里而不是播放器里：进度条、详情面板的"继续观看 12:34"、
 * 以后的"最近观看"都要用它 —— 而 core 不能反向依赖 feature。
 */
fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
