package com.ichichuang.cinema.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** 一条"看到哪了"的记录 */
data class WatchPoint(
    val lineIndex: Int,
    val episodeIndex: Int,
    val positionMs: Long,
    val durationMs: Long,
)

private val Context.progressStore: DataStore<Preferences> by preferencesDataStore(name = "cinema_progress")

/**
 * 观看进度。
 *
 * 存成紧凑字符串 `线路,集,位置毫秒,总长毫秒` —— 刻意不用 Room：
 * 这里就是几十条键值对，为它引注解处理器（KSP）会让构建变慢、还多一个版本风险，
 * 用已有的 DataStore 顺手就办了。
 *
 * 存的位置也不贪：暂停、切集、切线路、退出、每 10 秒各存一次 ——
 * 最坏情况只丢 10 秒进度。
 */
class PlaybackProgress(private val context: Context) {

    suspend fun get(movieId: String): WatchPoint? {
        val raw = context.progressStore.data.first()[key(movieId)] ?: return null
        val p = raw.split(',')
        if (p.size != 4) return null
        return WatchPoint(
            lineIndex = p[0].toIntOrNull() ?: return null,
            episodeIndex = p[1].toIntOrNull() ?: return null,
            positionMs = p[2].toLongOrNull() ?: return null,
            durationMs = p[3].toLongOrNull() ?: 0L,
        )
    }

    suspend fun save(movieId: String, point: WatchPoint) {
        if (movieId.isBlank()) return
        context.progressStore.edit { prefs ->
            prefs[key(movieId)] =
                "${point.lineIndex},${point.episodeIndex},${point.positionMs},${point.durationMs}"
        }
    }

    suspend fun clear(movieId: String) {
        context.progressStore.edit { it.remove(key(movieId)) }
    }

    private fun key(movieId: String) = stringPreferencesKey("wp_$movieId")
}
