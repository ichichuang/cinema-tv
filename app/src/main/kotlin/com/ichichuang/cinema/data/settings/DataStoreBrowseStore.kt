package com.ichichuang.cinema.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ichichuang.cinema.domain.repository.BrowseMemory
import com.ichichuang.cinema.domain.repository.BrowseStore
import kotlinx.coroutines.flow.first

private val Context.browsePrefs: DataStore<Preferences> by preferencesDataStore(name = "cinema_browse")

/**
 * 浏览位置的本地记忆（DataStore）。
 *
 * 单独一个小文件：它是"临时状态"（看到哪儿了），寿命比设置短、比内存长 ——
 * 用户随时可能换筛选、换片源站，这份记忆要能跟着变，不能污染配置文件。
 *
 * 写入由 ViewModel 做防抖（焦点移动 700ms 才落一次盘）：电视遥控器划得快，
 * 每动一下就写一次 DataStore 是没必要的开销。
 */
class DataStoreBrowseStore(private val context: Context) : BrowseStore {

    private val keyMovieId = stringPreferencesKey("last_movie_id")
    private val keyIndex = intPreferencesKey("last_index")

    override suspend fun load(): BrowseMemory {
        val p = context.browsePrefs.data.first()
        return BrowseMemory(
            movieId = p[keyMovieId]?.takeIf { it.isNotBlank() },
            index = p[keyIndex] ?: 0,
        )
    }

    override suspend fun save(memory: BrowseMemory) {
        context.browsePrefs.edit { p ->
            val id = memory.movieId
            if (id.isNullOrBlank()) p.remove(keyMovieId) else p[keyMovieId] = id
            p[keyIndex] = memory.index.coerceAtLeast(0)
        }
    }
}
