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
 * 年份筛选项：只列接口**真能按年筛**的近几年。
 *
 * 刻意不做"更早"——接口的 year 参数只接受具体年份，"更早"没法表达，
 * 摆出来就是骗用户（点了没反应或者结果不对）。
 */
fun yearOptions(currentYear: Int): List<String> =
    listOf(FILTER_ALL) + (currentYear downTo currentYear - 5).map { it.toString() }
