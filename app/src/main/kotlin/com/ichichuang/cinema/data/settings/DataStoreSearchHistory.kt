package com.ichichuang.cinema.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ichichuang.cinema.domain.repository.SearchHistoryStore
import kotlinx.coroutines.flow.first

private val Context.searchPrefs: DataStore<Preferences> by preferencesDataStore(name = "cinema_search")

/**
 * 搜索历史的本地记忆（DataStore）。
 *
 * 用单个字符串存（换行分隔）而不是 StringSet：**顺序有意义** ——
 * "最近搜的"必须排在最前，Set 不保证顺序。
 * 搜索词里不可能有换行，所以分隔符用换行是安全的。
 */
class DataStoreSearchHistory(private val context: Context) : SearchHistoryStore {

    private val keyHistory = stringPreferencesKey("history")

    override suspend fun load(): List<String> =
        context.searchPrefs.data.first()[keyHistory]
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    override suspend fun save(items: List<String>) {
        context.searchPrefs.edit { prefs ->
            prefs[keyHistory] = items.filter { it.isNotBlank() }.joinToString("\n")
        }
    }
}
