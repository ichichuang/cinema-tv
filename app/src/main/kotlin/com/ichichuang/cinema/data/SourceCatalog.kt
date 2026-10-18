package com.ichichuang.cinema.data

import com.ichichuang.cinema.data.remote.getText
import com.ichichuang.cinema.data.settings.CinemaSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.io.IOException

/** 推荐源清单里的一条：名字 + 接口地址 + 一句说明（"首选 · 9.9万部 · 565ms"） */
data class CatalogSource(val name: String, val url: String, val info: String)

/**
 * 「推荐源」清单。
 *
 * 为什么是"拉取 + 缓存"而不是写死在 APK 里：
 *  - 应用不内置任何片源（红线还立着）—— 这是发布方维护的一份**可选**列表，
 *    不选它，应用照样是"空手"状态
 *  - 源站隔三差五换域名，清单放在服务端改一下就行，不用重新发版
 *  - 拉下来缓存进 DataStore：之后断网也能翻出列表来挑
 *
 * 候选地址两个：gh-proxy 优先（刚发布的改动立刻可见）、jsDelivr 兜底（国内快但缓存久）。
 */
class SourceCatalog(
    private val http: OkHttpClient,
    private val json: Json,
    private val settings: CinemaSettings,
) {

    /** 本地缓存（没拉过就是空列表） */
    suspend fun cached(): List<CatalogSource> = parse(settings.catalogJson.first())

    /** 依次尝试候选地址拉取；成功就写缓存并返回 */
    suspend fun refresh(): Result<List<CatalogSource>> {
        var last: Throwable? = null
        for (raw in CinemaSettings.DEFAULT_CATALOG_URLS) {
            val url = raw.toHttpUrlOrNull() ?: continue
            try {
                // getText 自带 UA / 超时 / 编码嗅探，和拉更新清单走同一套
                val text = http.getText(url)
                val list = parse(text)
                if (list.isNotEmpty()) {
                    settings.saveCatalog(text)
                    return Result.success(list)
                }
                last = IOException("推荐里一条都没有")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
            }
        }
        return Result.failure(last ?: IOException("连不上推荐"))
    }

    private fun parse(raw: String?): List<CatalogSource> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val root = json.parseToJsonElement(raw).jsonObject
            val items = root["sources"] as? JsonArray ?: return emptyList()
            items.mapNotNull { element ->
                val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val url = obj["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val info = obj["info"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (name.isEmpty() || url.isEmpty()) null else CatalogSource(name, url, info)
            }
        }.getOrDefault(emptyList())
    }
}
