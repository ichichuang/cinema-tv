package com.ichichuang.cinema.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cinema_settings")

/**
 * 应用设置的唯一存储点。
 *
 * 目前只有一项：**影视接口地址**（苹果CMS V10 标准格式）。
 *
 * ⚠️ 红线：应用内不内置任何片源地址。地址由用户在设置页自己填，
 *    填之前首页只显示一句引导，不会去连任何服务器。
 */
class CinemaSettings(private val context: Context) {

    private val keyApiBase = stringPreferencesKey("api_base")

    /** 接口地址（空串 = 还没配置）。改成 Flow，设置页一保存，首页自动重新加载。 */
    val apiBase: Flow<String> = context.dataStore.data.map { it[keyApiBase]?.trim() ?: "" }

    suspend fun setApiBase(value: String) {
        context.dataStore.edit { prefs ->
            val v = value.trim()
            if (v.isEmpty()) prefs.remove(keyApiBase) else prefs[keyApiBase] = v
        }
    }

    companion object {
        /**
         * 魔法值：接口地址填这个，就用内置演示数据（不联网）。
         * 用途：装到电视上先看界面手感，或者接口临时挂了时验证"问题在接口还是在应用"。
         */
        const val DEMO = "demo"

        /** 设置页给用户的示例格式，不指向任何真实站点 */
        const val PLACEHOLDER = "http://你的站点/api.php/provide/vod/"
    }
}
