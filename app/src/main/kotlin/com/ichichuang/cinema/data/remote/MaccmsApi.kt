package com.ichichuang.cinema.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

/**
 * 苹果CMS V10 标准接口适配器。
 *
 * 参数规格来自官方源码核实：
 *   ac = list（分类+简表）/ videolist（列表）/ detail（详情）
 *   t  = 分类 id        pg = 页码        wd = 关键词        ids = 影片 id
 *   at = 输出格式，**必须显式写 json**（老站点默认吐 XML）
 *
 * 两个必须容忍的现实（写死了会崩）：
 *   1. 同一个字段，有的站返回数字、有的站返回字符串（vod_id / vod_score / total …）
 *      → 所以全部按 JsonElement 读，再自己转
 *   2. 站点之间字段名大写/小写、缺字段都有 → 全部可空 + 缺了给默认值
 */

// ── 接口层的数据形状（只属于 data 层，外面看不见）────────────────

data class RemoteCategory(
    val typeId: String,
    val name: String,
    val pid: String?,
)

data class RemoteEpisode(val name: String, val url: String)

data class RemotePlaySource(val name: String, val episodes: List<RemoteEpisode>)

data class RemoteVod(
    val id: String,
    val name: String,
    val pic: String?,
    val remarks: String?,
    val year: String?,
    val area: String?,
    val lang: String?,
    val score: String?,
    val typeName: String?,
    /** 题材/剧情分类（vod_class，如 "喜剧 动作"）；本地兜底筛"类型"用它 */
    val classes: String?,
    /** 详情接口才有的剧情简介（原始 HTML） */
    val content: String?,
    val playSources: List<RemotePlaySource>,
)

data class RemotePage(
    val items: List<RemoteVod>,
    val page: Int,
    val pageCount: Int,
    val total: Int,
)

class MaccmsApi(
    private val http: OkHttpClient,
    private val json: Json,
) {

    /** 分类树。苹果CMS 用 type_pid 表示父子关系，这里只取顶层（电影/电视剧/综艺/动漫…） */
    suspend fun categories(base: HttpUrl): List<RemoteCategory> {
        val root = request(base, "ac" to "list")
        val arr = root["class"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o["type_id"].asText() ?: return@mapNotNull null
            val name = o["type_name"].asText()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val pid = o["type_pid"].asText()
            RemoteCategory(typeId = id, name = name, pid = pid)
        }.filter { it.pid == null || it.pid == "0" || it.pid == it.typeId }
    }

    /**
     * 影片列表：按分类（typeId 为 null = 不分类，取全库）或按关键词。
     *
     * 语言/地区/年份/题材是苹果CMS videolist 的**服务器端**筛选参数：
     * 带了它们，站点直接返回筛好的结果（最省流量）。不支持的站会忽略它们
     * （返回空 或 原样返回全部）—— 两种情况都由调用方（MaccmsSource）兜底。
     */
    suspend fun list(
        base: HttpUrl,
        typeId: String?,
        page: Int,
        keyword: String? = null,
        lang: String? = null,
        area: String? = null,
        year: String? = null,
        movieClass: String? = null,
    ): RemotePage {
        val params = mutableListOf("ac" to "videolist", "pg" to page.toString())
        typeId?.let { params += "t" to it }
        keyword?.takeIf { it.isNotBlank() }?.let { params += "wd" to it }
        lang?.takeIf { it.isNotBlank() }?.let { params += "lang" to it }
        area?.takeIf { it.isNotBlank() }?.let { params += "area" to it }
        year?.takeIf { it.isNotBlank() }?.let { params += "year" to it }
        movieClass?.takeIf { it.isNotBlank() }?.let { params += "class" to it }

        val root = request(base, *params.toTypedArray())
        val arr = root["list"] as? JsonArray ?: JsonArray(emptyList())
        val items = arr.mapNotNull { (it as? JsonObject)?.toRemoteVod() }
        return RemotePage(
            items = items,
            page = root["page"].asInt() ?: page,
            pageCount = root["pagecount"].asInt() ?: 1,
            total = root["total"].asInt() ?: items.size,
        )
    }

    /** 影片详情（含剧情简介与播放地址） */
    suspend fun detail(base: HttpUrl, id: String): RemoteVod? {
        val root = request(base, "ac" to "detail", "ids" to id)
        val arr = root["list"] as? JsonArray ?: return null
        return arr.firstOrNull()?.let { (it as? JsonObject)?.toRemoteVod() }
    }

    // ── 内部：发请求 + 解析 ────────────────────────────────────

    private suspend fun request(base: HttpUrl, vararg params: Pair<String, String>): JsonObject {
        val urlBuilder = base.newBuilder().setQueryParameter("at", "json")
        // 参数顺序固定：先 ac，再其它（个别老站的 ac 处理对顺序敏感）
        val ordered = params.sortedBy { if (it.first == "ac") 0 else 1 }
        ordered.forEach { (k, v) -> urlBuilder.setQueryParameter(k, v) }

        val text = http.getText(urlBuilder.build())
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw SourceException("接口返回的不是 JSON（可能填错了地址，或站点需要 key）：${text.take(80)}", e)
        }

        // code: 1 = 成功；0/其它 = 失败，msg 里有人话原因
        val code = root["code"].asText()
        if (code != null && code != "1" && code != "0") {
            throw SourceException(root["msg"].asText() ?: "接口返回 code=$code")
        }
        val msg = root["msg"].asText()
        if (code == "0" && !msg.isNullOrBlank()) {
            throw SourceException(msg)
        }
        return root
    }

    private fun JsonObject.toRemoteVod(): RemoteVod? {
        val id = this["vod_id"].asText()?.trim().orEmpty()
        val name = this["vod_name"].asText()?.trim().orEmpty()
        if (name.isEmpty()) return null
        return RemoteVod(
            id = id.ifEmpty { name.hashCode().toString() },
            name = name,
            pic = this["vod_pic"].asText()?.trim()?.takeIf { it.isNotEmpty() },
            remarks = this["vod_remarks"].asText()?.trim()?.takeIf { it.isNotEmpty() },
            year = this["vod_year"].asText()?.trim()?.takeIf { it.isNotEmpty() },
            area = this["vod_area"].asText()?.trim()?.takeIf { it.isNotEmpty() },
            lang = this["vod_lang"].asText()?.trim()?.takeIf { it.isNotEmpty() },
            score = this["vod_score"].asText()?.trim()?.takeIf { it.isNotEmpty() },
            typeName = this["type_name"].asText()?.trim()?.takeIf { it.isNotEmpty() },
            classes = this["vod_class"].asText()?.trim()?.takeIf { it.isNotEmpty() },
            content = this["vod_content"].asText()?.trim()?.takeIf { it.isNotEmpty() },
            playSources = parsePlaySources(
                from = this["vod_play_from"].asText(),
                url = this["vod_play_url"].asText(),
            ),
        )
    }
}

/**
 * 解析播放地址。苹果CMS 的格式：
 *   vod_play_from = 线路1$$$线路2$$$线路3
 *   vod_play_url  = 第1集$http://…#第2集$http://…$$$线路2的…
 * 两串是同长度的数组，用 $$$ 分割后一一对应。
 */
fun parsePlaySources(from: String?, url: String?): List<RemotePlaySource> {
    if (from.isNullOrBlank() || url.isNullOrBlank()) return emptyList()
    val names = from.split("$$$")
    val groups = url.split("$$$")
    return names.mapIndexedNotNull { index, rawName ->
        val group = groups.getOrNull(index) ?: return@mapIndexedNotNull null
        val name = rawName.trim().ifEmpty { "线路${index + 1}" }
        val episodes = group.split("#").mapNotNull { raw ->
            val item = raw.trim()
            if (item.isEmpty()) return@mapNotNull null
            val sep = item.indexOf('$')
            when {
                sep <= 0 -> RemoteEpisode("正片", item)
                else -> {
                    val epName = item.substring(0, sep).trim().ifEmpty { "正片" }
                    val epUrl = item.substring(sep + 1).trim()
                    if (epUrl.isEmpty()) null else RemoteEpisode(epName, epUrl)
                }
            }
        }
        if (episodes.isEmpty()) null else RemotePlaySource(name, episodes)
    }
}

// ── JsonElement 取值工具（接口返回数字/字符串混用，全在这里消化）────

internal fun JsonElement?.asText(): String? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> content
    is JsonArray -> null
    is JsonObject -> null
}

internal fun JsonElement?.asInt(): Int? =
    asText()?.trim()?.let { s -> s.toDoubleOrNull()?.toInt() ?: null }

internal fun JsonElement?.asDouble(): Double? =
    asText()?.trim()?.toDoubleOrNull()

/** 去掉接口返回内容里的 HTML 标签（简介基本都是富文本） */
internal fun String.stripHtml(): String =
    this.replace(Regex("<[^>]*>"), " ")
        .replace(Regex("&nbsp;?"), " ")
        .replace(Regex("&[a-zA-Z]+;"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
