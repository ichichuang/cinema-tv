package com.ichichuang.cinema.domain.model

/**
 * 影片（领域模型）。
 *
 * 注意：这里刻意不包含任何"接口格式"的东西（没有 vod_id、vod_play_url 这种字段）。
 * 数据层的适配器负责把各家接口翻译成这个干净的模型。
 * 未来无论是苹果CMS、Jellyfin 还是本地文件，UI 都只认这一个类型。
 */
data class Movie(
    val id: String,
    val title: String,
    val year: Int,
    val score: Double,
    val genre: String,
    val minutes: Int,
    val synopsis: String,
    /** 海报图地址；为 null 时 UI 用渐变占位（不会出现灰块） */
    val posterUrl: String? = null,
    /** 备注标签，如"更新至12集""HD国语" */
    val remarks: String? = null,
    /** 语言（"国语"/"粤语"/…）。有些站不提供，所以默认空串 */
    val lang: String = "",
    /** 地区（"大陆"/"香港"/…）。同上 */
    val area: String = "",
    /** 来自哪个片源（接口地址）；空 = 演示数据/老数据。多源聚合时靠它把详情取回去 */
    val origin: String = "",
) {
    /**
     * 全局唯一键：不同片源的 vod_id 可能撞车（都是数字），带上来源才安全。
     * 列表 key、播放进度、焦点回显都用它 —— 同一部片不管从哪个入口进来，键都不变。
     */
    val uniqueKey: String get() = if (origin.isEmpty()) id else "$origin#$id"

    /**
     * 聚合去重键：片名（去空格标点、统一小写）+ 年份。
     * 同一部片在多个片源都出现时，只留排在前面的那个源的版本。
     */
    val mergeKey: String get() = buildString {
        append(title.filterNot { it.isWhitespace() || it in MERGE_IGNORE }.lowercase())
        if (year > 0) append('#').append(year)
    }
}

/** 聚合去重时抹掉的标点（《庆余年》 和 庆余年.2019 应该算同一部） */
private const val MERGE_IGNORE = "《》〈〉「」『』·•:：;；-—–~～!！?？,，.。'’\"“”()（）[]【】{}"
