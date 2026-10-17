package com.ichichuang.cinema.domain.repository

/**
 * "全部"是客户端造出来的分类（接口本身没有这一项，对应"不分类=取全库"）。
 * 放在这里做唯一定义，避免各处写字面量"全部"对不上。
 */
const val CATEGORY_ALL = "全部"
