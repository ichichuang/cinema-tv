package com.ichichuang.cinema.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ichichuang.cinema.domain.model.DefaultMovieFilter
import com.ichichuang.cinema.domain.model.MovieFilter
import com.ichichuang.cinema.domain.repository.CATEGORY_ALL
import com.ichichuang.cinema.domain.repository.FilterStore
import kotlinx.coroutines.flow.first

internal val Context.filterPrefs: DataStore<Preferences> by preferencesDataStore(name = "cinema_filters")

/**
 * 筛选条件的本地记忆（DataStore）。
 *
 * 用独立的小文件而不是塞进 CinemaSettings：设置是"配置"（接口地址），
 * 筛选是"使用习惯"，两者寿命和语义都不同 —— 分开存，读的时候互不打扰。
 */
class DataStoreFilterStore(private val context: Context) : FilterStore {

    private val keyCategory = stringPreferencesKey("f_category")
    private val keyLang = stringPreferencesKey("f_lang")
    private val keyArea = stringPreferencesKey("f_area")
    private val keyYear = stringPreferencesKey("f_year")
    private val keyTypes = stringPreferencesKey("f_types")

    override suspend fun load(): MovieFilter {
        val p = context.filterPrefs.data.first()
        return MovieFilter(
            category = p[keyCategory] ?: CATEGORY_ALL,
            // 从没存过 → 默认国语；用户主动选了"全部"存进来的是空串，也会原样返回
            lang = p[keyLang] ?: DefaultMovieFilter.lang,
            area = p[keyArea] ?: "",
            year = p[keyYear] ?: "",
            // 类型是多选（换行分隔）；老版本没这个键 → 空 = 不限
            types = p[keyTypes]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList(),
        )
    }

    override suspend fun save(filter: MovieFilter) {
        context.filterPrefs.editLegacy(context) { p ->
            p[keyCategory] = filter.category
            p[keyLang] = filter.lang
            p[keyArea] = filter.area
            p[keyYear] = filter.year
            if (filter.types.isEmpty()) p.remove(keyTypes) else p[keyTypes] = filter.types.joinToString("\n")
        }
    }
}
