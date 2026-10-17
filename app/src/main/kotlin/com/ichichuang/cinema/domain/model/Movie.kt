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
)
