package com.ichichuang.cinema.data

import com.ichichuang.cinema.data.remote.getText
import com.ichichuang.cinema.data.settings.SavedSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

/**
 * TVBox 单仓 / 多仓 JSON 导入器 —— 用户明确要的："我这份文件得能导进来"。
 *
 * 支持两种贴法（手机配置页里操作）：
 *  ① 直接贴 **JSON 内容**（单仓的 sites[]、多仓的 urls[] 都行）
 *  ② 贴 **订阅地址**（http/https），应用去把它抓下来再解析
 *
 * 解析规则（宽容优先，不认识的字段一律忽略）：
 *  - sites[]     → 每个站的 api/ext/url 字段里找接口地址，看起来像苹果CMS 的才收
 *  - urls[]      → 多仓：逐个抓取（最多 8 个），拿到 sites[] 再收
 *  - 非 http 地址（比如 "./本机.json"）、非 CMS 地址 → 跳过并如实说明
 *
 * 还有一个电视端的现实：raw.githubusercontent.com 在电视网络里基本够不着 ——
 * 遇到 GitHub raw 地址自动改走 gh-proxy 镜像（和更新/推荐源用的是同一招）。
 */
class TvboxImport(private val http: OkHttpClient) {

    data class Result(
        val sources: List<SavedSource>,
        /** 实际抓取过的订阅地址数量 */
        val repos: Int,
        /** 人话说明（跳过了什么、哪里没抓动） */
        val notes: List<String>,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** 入口：贴的是 JSON 就当 JSON 解析，是地址就抓回来再解析 */
    suspend fun fromTextOrUrl(input: String): Result {
        val text = input.trim()
        if (text.isEmpty()) return Result(emptyList(), 0, listOf("没有内容可以导入"))
        return if (text.startsWith("http://", true) || text.startsWith("https://", true)) {
            importFromUrl(text)
        } else {
            importFromJson(text, fromRepo = false)
        }
    }

    private suspend fun importFromUrl(url: String): Result {
        val body = runCatching { fetch(rewrite(url)) }.getOrElse { e ->
            return Result(emptyList(), 0, listOf("订阅地址没抓动：" + (e.message ?: "网络不通")))
        }
        return importFromJson(body, fromRepo = true)
    }

    private suspend fun importFromJson(text: String, fromRepo: Boolean): Result {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            ?: return Result(emptyList(), 0, listOf("这段内容不是有效的 JSON"))
        val notes = mutableListOf<String>()
        val found = LinkedHashMap<String, SavedSource>()   // url -> 源

        // 形态一：单仓/配置本身（sites[]）
        collectSites(root, found)

        // 形态二：多仓（urls[]）→ 逐个抓订阅，再收里面的 sites[]
        val allRepos = collectRepos(root)
        val repos = allRepos.take(MAX_REPOS)
        var fetched = if (fromRepo) 1 else 0
        if (allRepos.size > repos.size) {
            notes.add("订阅地址太多，只处理了前 ${repos.size} 个")
        }
        val relative = repos.count { it == null }
        if (relative > 0) {
            notes.add("跳过了 $relative 个相对路径地址（形如 ./xxx.json，电视上抓不到）")
        }
        repos.forEach { repoUrl ->
            if (repoUrl == null) return@forEach
            val body = runCatching { fetch(rewrite(repoUrl)) }.getOrNull()
            if (body == null) {
                notes.add("打不开：${hostOf(repoUrl)}")
                return@forEach
            }
            fetched += 1
            val sub = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
            if (sub == null) {
                notes.add("不是 JSON：${hostOf(repoUrl)}")
                return@forEach
            }
            collectSites(sub, found)
        }

        if (found.isEmpty() && notes.isEmpty()) {
            notes.add("这里面没找到能用的苹果CMS 接口")
        }
        return Result(found.values.toList(), fetched, notes)
    }

    // ── 内部解析 ───────────────────────────────────────────────

    /** sites[]：{name|key, api|ext|url} —— 只看 http 开头、像苹果CMS 的 */
    private fun collectSites(root: JsonObject, out: MutableMap<String, SavedSource>) {
        val arr = root["sites"] as? JsonArray ?: return
        arr.forEach { el ->
            val o = el as? JsonObject ?: return@forEach
            val api = listOf("api", "ext", "url").firstNotNullOfOrNull { key ->
                (o[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            } ?: return@forEach
            if (!looksLikeCms(api)) return@forEach
            val rawName = (o["name"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                .ifEmpty { (o["key"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty() }
            // 社区仓库常把测速历史写进名字（"LB资源 [392ms] ✅ [2108ms] ✅…"）——
            // 收进来的那一刻就清洗干净；地址按归一化去重（末尾斜杠不算两个源）
            val clean = sanitizeSourceName(rawName)
            out.putIfAbsent(sourceUrlKey(api), SavedSource(clean.ifEmpty { hostOf(api) }, api))
        }
    }

    /** urls[]：["http://…", {name, url}] —— 只要 http 地址，相对路径没法从电视上抓 */
    private fun collectRepos(root: JsonObject): List<String?> {
        val arr = root["urls"] as? JsonArray ?: return emptyList()
        return arr.map { el ->
            val raw = when (el) {
                is JsonPrimitive -> el.contentOrNull?.trim()
                is JsonObject -> (el["url"] as? JsonPrimitive)?.contentOrNull?.trim()
                else -> null
            }
            raw?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
        }
    }

    private fun looksLikeCms(url: String): Boolean {
        if (!(url.startsWith("http://", true) || url.startsWith("https://", true))) return false
        val u = url.lowercase()
        return u.contains("/api.php") || u.contains("provide/vod") ||
            u.endsWith("/vod") || u.endsWith("/vod/")
    }

    /** GitHub raw 在电视网络上基本够不着 —— 自动改走 gh-proxy 镜像 */
    private fun rewrite(url: String): String {
        val host = hostOf(url)
        return when {
            host == "raw.githubusercontent.com" -> "https://gh-proxy.com/$url"
            host == "github.com" && url.contains("/raw/") -> "https://gh-proxy.com/$url"
            else -> url
        }
    }

    private suspend fun fetch(url: String): String {
        val httpUrl = url.toHttpUrlOrNull() ?: error("地址格式不对：$url")
        return http.getText(httpUrl)
    }

    private fun hostOf(url: String): String = url.toHttpUrlOrNull()?.host ?: url

    private companion object {
        /** 一次导入最多再展开这么多个订阅地址（防呆：多仓里挂几十个也不至于卡死） */
        const val MAX_REPOS = 8
    }
}
