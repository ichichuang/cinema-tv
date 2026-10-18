package com.ichichuang.cinema.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cinema_settings")

/** 一个保存下来的片源：名字（展示用）+ 接口地址 */
data class SavedSource(val name: String, val url: String)

/** 一次导入的收成：新增了几个、顺手启用了几个 */
data class ImportOutcome(val added: Int, val enabled: Int)

/**
 * 应用设置的唯一存储点。
 *
 * 内容：
 *  - **片源**：当前生效的接口地址 + 名字，外加一份「保存过的源」清单（用户可能配多个，随时切换）
 *  - **更新地址**：在线更新的清单地址
 *  - **快进/快退步长**：播放器里那几档（10/15/30/60 秒），手机上的"倍速习惯"搬到了遥控器
 *  - **推荐源清单缓存**：一键导入用的排行（从发布仓库拉，缓存下来离线也能看）
 *
 * ⚠️ 红线（依然成立）：应用**不内置任何片源地址**。推荐清单是发布方维护的一份
 *    *可选* 列表（URL 公开可改），首次导入时才联网拉取；不挑不选的话，
 *    应用照样是"空手"状态，首页只会引导你去配置。
 */
class CinemaSettings(private val context: Context) {

    private val keyApiBase = stringPreferencesKey("api_base")
    private val keyApiName = stringPreferencesKey("api_name")
    private val keySources = stringPreferencesKey("saved_sources")
    private val keyEnabled = stringPreferencesKey("enabled_sources")
    private val keyUpdateUrl = stringPreferencesKey("update_url")
    private val keySeekStep = longPreferencesKey("seek_step_ms")
    private val keyHomeCols = longPreferencesKey("home_columns")
    private val keyCatalog = stringPreferencesKey("source_catalog")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** 当前接口地址（空串 = 还没配置）。改成 Flow，一保存，首页自动重新加载。 */
    val apiBase: Flow<String> = context.dataStore.data.map { it[keyApiBase]?.trim() ?: "" }

    /** 当前源的名字（"非凡" / "自定义"…）；老版本升级上来的可能为空 */
    val apiName: Flow<String> = context.dataStore.data.map { it[keyApiName]?.trim() ?: "" }

    /** 保存过的源清单（新加的排前面） */
    val sources: Flow<List<SavedSource>> = context.dataStore.data.map { decodeSources(it[keySources]) }

    /**
     * 启用中的片源地址（**多选**）。首页会把所有启用源的结果聚在一起。
     *
     * 兼容老版本（只有 apiBase 单源）：enabled 为空时把 apiBase 视作唯一启用源；
     * 演示模式（demo）不算片源。
     */
    val activeUrls: Flow<List<String>> = context.dataStore.data.map { deriveActive(it) }

    /** 首页每行海报数（4..8，默认 5） */
    val homeColumns: Flow<Int> = context.dataStore.data.map {
        (it[keyHomeCols] ?: DEFAULT_HOME_COLUMNS.toLong()).toInt()
            .coerceIn(MIN_HOME_COLUMNS, MAX_HOME_COLUMNS)
    }

    /**
     * "来源组合签名"：启用集合或主源一变化它就变。
     * 首页拿它当"配置变了 → 整墙重载"的信号（见 MovieRepository.config）。
     */
    val sourceSignature: Flow<String> = combine(apiBase, activeUrls) { base, urls ->
        when {
            urls.isNotEmpty() -> urls.joinToString("|")
            base == DEMO -> DEMO
            else -> base
        }
    }

    /** 播放器快进/快退步长（毫秒） */
    val seekStepMs: Flow<Long> = context.dataStore.data.map { it[keySeekStep] ?: DEFAULT_SEEK_STEP_MS }

    /** 推荐源清单的原始 JSON 缓存（没拉过就是 null） */
    val catalogJson: Flow<String?> = context.dataStore.data.map {
        it[keyCatalog]?.takeIf { raw -> raw.isNotBlank() }
    }

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

    /**
     * 激活一个片源（**只留这一个**：切换 + 记忆 + 并入保存清单）。
     *
     * 一个动作干三件事，是为了让两个入口（电视上的「选择片源」、手机上的一键切换）
     * 共用同一条逻辑 —— 不存在"从手机换的源电视上找不到"这种事。
     * 想同时用好几个源（首页聚合），用 toggleSource 逐个加。
     */
    suspend fun activateSource(name: String, url: String) {
        val cleanUrl = url.trim()
        context.dataStore.edit { prefs ->
            if (cleanUrl.isEmpty()) {
                prefs.remove(keyApiBase)
                prefs.remove(keyApiName)
                prefs.remove(keyEnabled)
                return@edit
            }
            val cleanName = name.trim().takeIf { it.isNotEmpty() } ?: DEFAULT_SOURCE_NAME
            prefs[keyApiBase] = cleanUrl
            prefs[keyApiName] = cleanName

            // 启用集合跟着走：演示模式 = 不启用任何真实源；真实地址 = 只启用它
            if (cleanUrl == DEMO) {
                prefs.remove(keyEnabled)
            } else {
                prefs[keyEnabled] = encodeUrls(listOf(cleanUrl))
            }

            // 顺手并入保存清单：同一个地址只留一条，最新的排最前，超出上限就丢最旧的
            val rest = decodeSources(prefs[keySources]).filterNot { it.url == cleanUrl }
            val merged = listOf(SavedSource(cleanName, cleanUrl)) + rest
            prefs[keySources] = encodeSources(merged.take(MAX_SAVED_SOURCES))
        }
    }

    /**
     * 打开/关闭一个片源的「启用」状态（首页会聚合所有启用源）。
     *
     * 与 activateSource 的分工：activate = 只留这一个；toggle = 多选加减。
     * @return false = 到了启用上限（电视扛不住太多并发源），本次没生效
     */
    suspend fun toggleSource(url: String): Boolean {
        val clean = url.trim()
        if (clean.isEmpty() || clean == DEMO) return false
        var accepted = true
        context.dataStore.edit { prefs ->
            val current = deriveActive(prefs)
            val next = if (clean in current) current - clean else current + clean
            if (clean !in current && next.size > MAX_ENABLED_SOURCES) {
                accepted = false
                return@edit
            }
            prefs[keyEnabled] = encodeUrls(next)
            // 主源口径跟着第一个启用源走（老逻辑 / 设置页状态行都看它）
            if (next.isEmpty()) {
                prefs.remove(keyApiBase)
                prefs.remove(keyApiName)
            } else {
                prefs[keyApiBase] = next.first()
                val saved = decodeSources(prefs[keySources])
                prefs[keyApiName] = saved.firstOrNull { it.url == next.first() }?.name
                    ?: DEFAULT_SOURCE_NAME
            }
        }
        return accepted
    }

    /** 一次性读取启用中的片源（数据层的取数路径用） */
    suspend fun activeUrlsNow(): List<String> = deriveActive(context.dataStore.data.first())

    private fun deriveActive(prefs: Preferences): List<String> {
        val stored = decodeUrls(prefs[keyEnabled]).filter { it != DEMO }
        if (stored.isNotEmpty()) return stored
        val legacy = prefs[keyApiBase]?.trim().orEmpty()
        return if (legacy.isEmpty() || legacy == DEMO) emptyList() else listOf(legacy)
    }

    /** 首页每行海报数（4..8） */
    suspend fun setHomeColumns(columns: Int) {
        context.dataStore.edit { prefs ->
            prefs[keyHomeCols] = columns.coerceIn(MIN_HOME_COLUMNS, MAX_HOME_COLUMNS).toLong()
        }
    }

    /**
     * 批量并入保存清单（TVBox 导入用）：同地址去重、新的排前面。
     * @return 实际新增的数量
     */
    suspend fun mergeSavedSources(incoming: List<SavedSource>): Int {
        if (incoming.isEmpty()) return 0
        var added = 0
        context.dataStore.edit { prefs ->
            val existing = decodeSources(prefs[keySources])
            val seen = existing.mapTo(HashSet()) { it.url }
            val fresh = incoming.filter { seen.add(it.url) }
            added = fresh.size
            if (added > 0) {
                prefs[keySources] = encodeSources((fresh + existing).take(MAX_SAVED_SOURCES))
            }
        }
        return added
    }

    /**
     * 只并入保存清单（不动启用集合）—— 推荐源被勾选时顺手记住它，
     * 不然下次想找回来就得重新拉清单。
     */
    suspend fun rememberSource(name: String, url: String) {
        val clean = url.trim()
        if (clean.isEmpty()) return
        mergeSavedSources(
            listOf(SavedSource(name.trim().takeIf { it.isNotEmpty() } ?: DEFAULT_SOURCE_NAME, clean)),
        )
    }

    /**
     * 导入一批片源：先并入保存清单，再把还没启用的自动补几个上来（不超过启用上限）。
     * 这样导入完首页立刻就有内容，不用一个个手动勾。
     */
    suspend fun importSources(incoming: List<SavedSource>): ImportOutcome {
        val added = mergeSavedSources(incoming)
        var enabledCount = activeUrlsNow().size
        var turnedOn = 0
        for (source in incoming) {
            if (enabledCount >= MAX_ENABLED_SOURCES) break
            if (source.url in activeUrlsNow()) continue
            if (toggleSource(source.url)) {
                enabledCount += 1
                turnedOn += 1
            }
        }
        return ImportOutcome(added = added, enabled = turnedOn)
    }

    /** 保存更新清单地址（空串 = 恢复默认候选） */
    suspend fun setUpdateUrl(url: String) {
        val clean = url.trim()
        context.dataStore.edit { prefs ->
            if (clean.isEmpty() || clean in DEFAULT_UPDATE_URLS) {
                prefs.remove(keyUpdateUrl)
            } else {
                prefs[keyUpdateUrl] = clean
            }
        }
    }

    suspend fun setSeekStep(ms: Long) {
        context.dataStore.edit { prefs ->
            prefs[keySeekStep] = ms.coerceIn(5_000L, 600_000L)
        }
    }

    suspend fun saveCatalog(raw: String) {
        if (raw.isBlank()) return
        context.dataStore.edit { prefs -> prefs[keyCatalog] = raw }
    }

    /** 当前生效的接口地址（读一次，给筛选/测试这类一次性逻辑用） */
    suspend fun currentApiBase(): String = apiBase.first()

    // ── 保存清单的编解码 ───────────────────────────────────────
    // 用 JSON 存（而不是自己拼分隔符）—— 名字里出现空格/斜杠/中文都不会坏

    private fun encodeSources(list: List<SavedSource>): String = buildJsonArray {
        list.forEach { source ->
            add(
                buildJsonObject {
                    put("name", source.name)
                    put("url", source.url)
                },
            )
        }
    }.toString()

    private fun decodeSources(raw: String?): List<SavedSource> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.parseToJsonElement(raw).jsonArray.mapNotNull { element ->
                val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val url = obj["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (url.isEmpty()) null else SavedSource(name.ifEmpty { DEFAULT_SOURCE_NAME }, url)
            }
        }.getOrDefault(emptyList())
    }

    // 启用集合全是地址字符串（地址里不可能有换行符）→ 直接用换行分隔存，
    // 比 JSON 再套一层更皮实，也不会踩序列化库的边角注释

    private fun encodeUrls(list: List<String>): String =
        list.joinToString("\n")

    private fun decodeUrls(raw: String?): List<String> =
        raw.orEmpty()
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    companion object {
        /**
         * 魔法值：接口地址填这个，就用内置演示数据（不联网）。
         * 用途：装到电视上先看界面手感，或者接口临时挂了时验证"问题在接口还是在应用"。
         */
        const val DEMO = "demo"

        /** 用户自己填的地址的默认名字 */
        const val DEFAULT_SOURCE_NAME = "自定义"

        /** 保存清单上限（多仓导入可能一下进来很多，留足空间） */
        private const val MAX_SAVED_SOURCES = 256

        /** 同时启用（首页聚合）的片源上限：再多并发会让电视加载一页等太久 */
        const val MAX_ENABLED_SOURCES = 8

        /** 首页每行海报数：可选档与默认值 */
        const val MIN_HOME_COLUMNS = 4
        const val MAX_HOME_COLUMNS = 8
        const val DEFAULT_HOME_COLUMNS = 5

        /** 播放器快进步长的默认档：15 秒 */
        const val DEFAULT_SEEK_STEP_MS = 15_000L

        /** 播放器面板里可选的几档步长 */
        val SEEK_STEP_OPTIONS = listOf(10_000L, 15_000L, 30_000L, 60_000L)

        /** 设置页给用户的示例格式，不指向任何真实站点 */
        const val PLACEHOLDER = "http://你的站点/api.php/provide/vod/"

        /**
         * 默认更新清单候选（**依次尝试，哪个能用用哪个**）。
         *
         * ① gh-proxy 直通发布仓库里的 version.json —— 无缓存，**刚发完的版本立刻能看到**。
         * ② 同一份文件走 jsDelivr —— 国内快且稳，但分支地址有 CDN 缓存（最多十几小时），
         *    所以只当兜底、不当首选。
         */
        val DEFAULT_UPDATE_URLS = listOf(
            "https://gh-proxy.com/https://raw.githubusercontent.com/ichichuang/cinema-tv-releases/main/version.json",
            "https://cdn.jsdelivr.net/gh/ichichuang/cinema-tv-releases@main/version.json",
        )

        /**
         * 「推荐源」清单的候选地址（和更新清单同一套思路：gh-proxy 优先、jsDelivr 兜底）。
         *
         * 清单本身是发布方维护的一份**公开可改**的 JSON：改源、加源不用重新发版。
         * 拉下来会缓存到本地，之后断网也能翻出列表来选。
         */
        val DEFAULT_CATALOG_URLS = listOf(
            "https://gh-proxy.com/https://raw.githubusercontent.com/ichichuang/cinema-tv-releases/main/sources.json",
            "https://cdn.jsdelivr.net/gh/ichichuang/cinema-tv-releases@main/sources.json",
        )

        /** 设置页里显示的默认值（第一条） */
        val DEFAULT_UPDATE_URL: String get() = DEFAULT_UPDATE_URLS.first()
    }
}
