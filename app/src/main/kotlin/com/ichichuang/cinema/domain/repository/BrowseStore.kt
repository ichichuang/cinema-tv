package com.ichichuang.cinema.domain.repository

/**
 * 上次浏览到哪儿了。
 *
 * 为什么要存这个：电视是最"没有耐心"的设备 ——
 *   · 用户从首页进了播放器再退回来，焦点必须还停在刚才那部片上（否则右侧详情变了、列表跳顶部，用起来像换了个应用）
 *   · 电视系统的内存策略很激进，应用切到后台经常直接被杀；下次打开如果回到第 1 部片，
 *     用户得重新翻半天才能找回刚才看的
 *
 * 所以这份记忆是"两用"：导航往返（靠 ViewModel 里的状态就够）+ 进程被杀后重启（必须落盘）。
 */
interface BrowseStore {

    /**
     * 读上次的浏览位置。
     * [movieId] 为空表示还没有记录（第一次使用）—— 这时首页按默认行为落在第一张海报上。
     */
    suspend fun load(): BrowseMemory

    suspend fun save(memory: BrowseMemory)
}

/**
 * @param movieId 上次焦点停在哪部片（用它恢复最稳：翻页顺序变了、接口换了也不怕）
 * @param index   它的位置。**只作为兜底** —— 万一那部片在新筛选下不存在了，就按位置落回去
 */
data class BrowseMemory(
    val movieId: String? = null,
    val index: Int = 0,
)
