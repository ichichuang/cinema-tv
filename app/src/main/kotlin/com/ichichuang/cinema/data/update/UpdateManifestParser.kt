package com.ichichuang.cinema.data.update

import com.ichichuang.cinema.data.remote.SourceException
import com.ichichuang.cinema.data.remote.asInt
import com.ichichuang.cinema.data.remote.asText
import com.ichichuang.cinema.domain.model.UpdateInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

internal object UpdateManifestParser {
    fun parse(text: String, json: Json): UpdateInfo {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: throw SourceException("更新清单不是合法 JSON")

        val code = obj["versionCode"].asInt()
            ?: obj["version_code"].asInt()
            ?: obj["code"].asInt()
            ?: throw SourceException("更新清单里缺了版本号")
        if (code <= 0) throw SourceException("更新清单的版本号不对")

        val name = obj["versionName"].asText()
            ?: obj["version_name"].asText()
            ?: obj["name"].asText()
            ?: code.toString()

        val apk = obj["apkUrl"].asText()
            ?: obj["apk_url"].asText()
            ?: obj["url"].asText()
            ?: obj["download_url"].asText()
            ?: throw SourceException("更新清单里没有下载地址")

        val notes = (obj["notes"].asText()
            ?: obj["changelog"].asText()
            ?: obj["desc"].asText()
            ?: obj["description"].asText())
            ?.replace("\\n", "\n")
            ?.trim()
            ?: ""

        // 大小只认纯字节数；写成 "13MB" 这种就当作没写（宁可少显示，不显示错的）
        val size = (obj["sizeBytes"].asText() ?: obj["size_bytes"].asText())
            ?.trim()
            ?.toLongOrNull()
            ?: 0L

        val mirror = (obj["mirrorUrl"].asText() ?: obj["mirror_url"].asText())?.trim()?.takeIf { it.isNotEmpty() }
        val digest = if (obj.containsKey("sha256")) {
            (obj["sha256"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?.takeIf { Regex("[0-9a-fA-F]{64}").matches(it) }
                ?: throw SourceException("更新清单的摘要格式不对")
        } else null

        return UpdateInfo(
            versionCode = code,
            versionName = name,
            apkUrl = apk,
            notes = notes,
            sizeBytes = size,
            mirrorUrl = mirror,
            sha256 = digest,
        )
    }

}
