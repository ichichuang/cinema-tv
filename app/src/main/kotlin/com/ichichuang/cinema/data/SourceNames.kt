package com.ichichuang.cinema.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 片源名字的清洗与地址归一 —— 配置页 / 电视端 / 导入共用。
 *
 * 为什么需要：社区 TVBox 仓库（尤其"每日测速"类多仓）会把**测速历史写进名字**，
 * 例如 "LB资源 [392ms] ✅ [2108ms] ✅ [493ms] ✅ …" 几十条堆在一起。
 * 原样显示就是一整墙乱码，还顶得列表排版全乱。
 *
 * 清洗规则（只清理展示，不改接口）：
 *  ① 从**第一个测速标记**处截断（[123ms] / [timeout] / [连接超时] / [已失效]…）
 *  ② 去掉 emoji 和装饰符号（✅❌⬜◉★…）
 *  ③ 合并空白；超过 20 个字就截断
 *
 * 返回空串 = 这个名字实在救不回来（调用方应该回退到域名）。
 */
fun sanitizeSourceName(raw: String): String {
    var s = raw.trim()
    if (s.isEmpty()) return s

    // ① 第一个测速标记之前的部分才算名字
    val marker = Regex(
        """\s*\[[^\]\s]{0,32}?(?:\d+\s*ms|timeout|超时|无效|失效|错误|失败|拒绝|无响应)[^\]]{0,32}\]""",
        RegexOption.IGNORE_CASE,
    )
    marker.find(s)?.let { s = s.substring(0, it.range.first) }

    // ② 图形符号不是名字
    s = s.replace(Regex("""[\p{So}\p{Sk}\u200D\uFE0F\u20E3\u2190-\u21FF]"""), " ")

    // ③ 合并空白、封顶长度
    s = s.replace(Regex("""\s+"""), " ").trim()
    if (s.length > 20) s = s.take(19) + "…"
    return s
}

/**
 * 地址"归一化"：只用于**比较 / 去重**，不用于真实请求。
 *
 * 同一个接口，社区仓库里有写 "…/vod" 的、有写 "…/vod/" 的 —— 不归一就会出现
 * "非凡"和"非凡影视"同接口两条并排的怪状（用户截图里真实出现过）。
 */
fun sourceUrlKey(url: String): String = url.trim().trimEnd('/')

/** 从地址取域名（片源名字清洗后为空时的兜底显示名） */
fun sourceHost(url: String): String = url.trim().toHttpUrlOrNull()?.host ?: url.trim()
