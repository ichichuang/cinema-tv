package com.ichichuang.cinema.domain.repository

/**
 * 搜索历史的记忆。
 *
 * 电视上打字是全应用**最贵**的操作（遥控器一个个按字母、还要拼音选字），
 * 所以搜过的词必须留下：下次想看同一部片，按两下就能重搜，而不是再打一遍。
 */
interface SearchHistoryStore {

    /** 最近搜过的词，最新的在最前面 */
    suspend fun load(): List<String>

    suspend fun save(items: List<String>)
}
