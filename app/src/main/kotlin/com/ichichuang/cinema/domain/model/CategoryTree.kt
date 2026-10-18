package com.ichichuang.cinema.domain.model

import com.ichichuang.cinema.domain.repository.CATEGORY_ALL

/**
 * 真·分类树（名字全部来自接口原文，绝不自造词）。
 *
 * - [tops] = 顶层分类（电影片 / 连续剧 / 综艺 / 动漫片 …）
 * - [children] = 顶层分类名 → 它的下级类型（动作片 / 喜剧片 / 悬疑 …）
 * - [flat] = **平铺站**（老模板不返回 type_pid，所有分类平着给）的全部名字：
 *   它们既是"分类"也是"类型"，多选题材时就从这里挑
 *
 * 多个启用源的树在仓库层合并展示：**有什么分类就列什么**。
 * 数据层按这些名字去各站解析真实 type_id 来筛（t= 参数）。
 */
data class CategoryTree(
    val tops: List<String> = emptyList(),
    val children: Map<String, List<String>> = emptyMap(),
    val flat: List<String> = emptyList(),
) {
    /** 全部可选类型（下级类型 + 平铺名字，去重）——"分类 = 全部"时列它们 */
    val allTypes: List<String>
        get() = (flat + children.values.flatten()).distinct()

    /** 某个分类下的可选类型；分类 = "全部" 时给全部 */
    fun typesFor(category: String): List<String> =
        if (category == CATEGORY_ALL) allTypes
        else (children[category].orEmpty() + flat).distinct()

    companion object {
        /** 多源合并：顶层/下级/平铺都取并集（先出现的不动，保持稳定顺序） */
        fun merge(trees: List<CategoryTree>): CategoryTree {
            val tops = LinkedHashSet<String>()
            val kids = LinkedHashMap<String, LinkedHashSet<String>>()
            val flats = LinkedHashSet<String>()
            trees.forEach { tree ->
                tops += tree.tops
                tree.children.forEach { (k, v) -> kids.getOrPut(k) { LinkedHashSet() } += v }
                flats += tree.flat
            }
            return CategoryTree(tops.toList(), kids.mapValues { it.value.toList() }, flats.toList())
        }
    }
}
