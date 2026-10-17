package com.ichichuang.cinema.domain.repository

import com.ichichuang.cinema.domain.model.MovieFilter

/**
 * 筛选条件的记忆。
 *
 * 接口放 domain、实现放 data：UI 层只认这个接口，不认识 DataStore。
 * 语义：用户没主动改过 → 返回默认筛选（国语）；改过 → 记住他改的。
 */
interface FilterStore {
    suspend fun load(): MovieFilter
    suspend fun save(filter: MovieFilter)
}
