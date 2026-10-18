package com.ichichuang.cinema.domain.repository

/**
 * "全部" = 该维度不过滤。
 * 放在这里做唯一定义，避免各处写字面量"全部"对不上（筛选条、摘要、存储都靠它）。
 */
const val FILTER_ALL = "全部"

/**
 * 语言筛选项。
 *
 * ⚠️ 这是**请求参数**的取值（苹果CMS 的 lang 字段），不是界面文字。
 * 站点之间写法不统一（有的写"普通话"），所以数据层在本地兜底筛选时会
 * 把 国语/普通话/中文/汉语/华语 都当成国语 —— 见 MaccmsMovieRepository.matchesLang()。
 */
val LANG_OPTIONS = listOf(FILTER_ALL, "国语", "粤语", "英语", "日语", "韩语")

/** 地区筛选项（同样是请求参数取值，本地兜底时 大陆/内地/中国 互相认） */
val AREA_OPTIONS = listOf(
    FILTER_ALL, "大陆", "香港", "台湾", "美国", "韩国", "日本", "英国", "法国", "泰国",
)

/**
 * 类型（题材）筛选**没有固定词表** —— 词表是假的根源。
 *
 * 用户点名："这种分类你要从真实接口中读取，有什么分类就给我什么选项。"
 * 所以"类型"一节改由 CategoryTree（接口 ac=list 返回的真实分类树）驱动，
 * 见 domain/model/CategoryTree.kt。这里不再保留任何猜测词。
 */

/**
 * 年份筛选项：从今年一直列到 2000（接口的 year 参数原生支持任意具体年份）。
 *
 * 为什么是 2000 而不是"更早"：再往前的片子在各站存量极少，
 * 而接口的 year 参数只接受具体年份，"更早"这种话摆出来就是骗用户
 * （点了没反应或者结果不对）—— 所以只列真能筛的具体年份。
 *
 * 0.9.4 及以前只列"今年往前 5 年"（2026→2021），用户反馈"看不到老片"；
 * 现在把窗口开到 2000，想看老片（90 年代除外）都有对应年份可选。
 */
fun yearOptions(currentYear: Int): List<String> =
    listOf(FILTER_ALL) + (currentYear downTo maxOf(2000, currentYear - 40)).map { it.toString() }
