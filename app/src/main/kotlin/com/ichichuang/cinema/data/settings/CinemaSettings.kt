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
 * 两项：**影视接口地址**（苹果CMS V10 标准格式）+ **更新地址**（在线更新的清单地址）。
 *
 * ⚠️ 红线：应用内不内置任何片源地址。地址由用户在设置页自己填，
 *    填之前首页只显示一句引导，不会去连任何服务器。
 *    更新地址同理 —— 默认指向发布方的 GitHub Releases，随时可换成镜像。
 */
class CinemaSettings(private val context: Context) {

    private val keyApiBase = stringPreferencesKey("api_base")
    private val keyUpdateUrl = stringPreferencesKey("update_url")

    /** 接口地址（空串 = 还没配置）。改成 Flow，设置页一保存，首页自动重新加载。 */
    val apiBase: Flow<String> = context.dataStore.data.map { it[keyApiBase]?.trim() ?: "" }

    /** 更新清单地址；没填过就用默认值（不是空 —— 缺省也要能检查更新） */
    val updateUrl: Flow<String> = context.dataStore.data.map {
        it[keyUpdateUrl]?.trim()?.takeIf { url -> url.isNotEmpty() } ?: DEFAULT_UPDATE_URL
    }

    /**
     * 检查更新时实际要依次尝试的清单地址。
     * 用户自己填了就只试他填的那一条（他的选择优先）；没填过就按默认候选依次试。
     */
    val updateUrls: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val custom = prefs[keyUpdateUrl]?.trim().orEmpty()
        if (custom.isNotEmpty()) listOf(custom) else DEFAULT_UPDATE_URLS
    }

    suspend fun setApiBase(value: String) {
        context.dataStore.edit { prefs ->
            val v = value.trim()
            if (v.isEmpty()) prefs.remove(keyApiBase) else prefs[keyApiBase] = v
        }
    }

    suspend fun setUpdateUrl(value: String) {
        context.dataStore.edit { prefs ->
            val v = value.trim()
            // 填回默认值就等于"没改过"，存空串
            if (v.isEmpty() || v == DEFAULT_UPDATE_URL) prefs.remove(keyUpdateUrl) else prefs[keyUpdateUrl] = v
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

        /**
         * 默认更新清单候选（**依次尝试，哪个能用用哪个**）。
         *
         * ① GitHub release 附件 —— 发布方用 token 上传的那种，最"正规"。
         *    走 objects.githubusercontent.com（实测可达）。
         * ② 发布仓库里的文件，经 gh-proxy 转发 —— raw.githubusercontent.com 国内直连是死的，
         *    所以走代理。零密钥发布路线用这条。
         *
         * 两条指向同一个公开发布仓库。**换域名不用改应用** —— 这是把候选写成列表的原因：
         * 国内网络对 GitHub 各子域的通断经常变，写死一条迟早要重新发版来救。
         */
        val DEFAULT_UPDATE_URLS = listOf(
            "https://github.com/ichichuang/cinema-tv-releases/releases/latest/download/version.json",
            "https://gh-proxy.com/https://raw.githubusercontent.com/ichichuang/cinema-tv-releases/main/version.json",
        )

        /** 设置页里显示的默认值（第一条） */
        val DEFAULT_UPDATE_URL: String get() = DEFAULT_UPDATE_URLS.first()

        /** 设置页里的更新地址示例（用户换成自己的仓库 / 镜像就照这个格式） */
        const val UPDATE_PLACEHOLDER = "https://github.com/你的账号/你的仓库/releases/latest/download/version.json"
    }
}
