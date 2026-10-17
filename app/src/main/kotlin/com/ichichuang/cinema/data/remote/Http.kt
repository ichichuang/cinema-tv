package com.ichichuang.cinema.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.concurrent.TimeUnit

/**
 * 片源接口的 http 层。
 *
 * 这里集中处理三个"中国在线影视接口"的现实问题（不是理论问题，是必踩的）：
 *
 * 1. **明文 http** —— 绝大多数片源站没有 https（清单里已开 usesCleartextTraffic）
 * 2. **防盗链 UA** —— 不少站对 OkHttp 默认 UA 直接返回空数据，必须伪装成浏览器
 * 3. **GBK 编码** —— 老 CMS 站返回的 JSON 是 GBK 字节，还常常骗你说自己是 utf-8，
 *    所以不能信 header，得按字节读、UTF-8 解不出来就退 GBK
 */

/** 伪装成浏览器 + 播放器常见 UA，很多接口靠这个放行 */
const val DEFAULT_UA =
    "Mozilla/5.0 (Linux; Android 9; TV) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Safari/537.36"

/** 片源级错误（给用户看的都是人话，不暴露 http 细节） */
class SourceException(message: String, cause: Throwable? = null) : Exception(message, cause)

fun buildHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .callTimeout(25, TimeUnit.SECONDS)
    .followRedirects(true)
    .retryOnConnectionFailure(true)
    .build()

/**
 * 把用户填的一串地址变成可用的 URL。
 * 容错：不写 http:// 会自动补；末尾多余的问号、斜杠都会收拾干净。
 * 地址里已经带的查询参数（比如某些站要求 ?key=xxx）会原样保留。
 */
fun normalizeBaseUrl(raw: String): HttpUrl? {
    val trimmed = raw.trim().trimEnd('?', '&', ' ')
    if (trimmed.isEmpty()) return null
    val withScheme = when {
        trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) -> trimmed
        else -> "http://$trimmed"
    }
    return withScheme.toHttpUrlOrNull()
}

/** 同步 GET（包在 IO 线程上），返回按正确编码解出来的文本 */
suspend fun OkHttpClient.getText(url: HttpUrl): String =
    withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DEFAULT_UA)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            // 一些片源站会校验 Referer 防盗链，带上自己站点的根地址
            .header("Referer", "${url.scheme}://${url.host}/")
            .build()

        try {
            newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw SourceException("接口返回 HTTP ${response.code}")
                }
                val body = response.body
                val bytes = body.bytes()
                if (bytes.isEmpty()) throw SourceException("接口返回了空内容")
                decodeBody(bytes, body.contentType()?.charset())
            }
        } catch (e: SourceException) {
            throw e
        } catch (e: CancellationException) {
            // 协程被取消不是"网络错误"，必须原样抛出，否则会打断 ViewModel 的正常取消
            throw e
        } catch (e: Exception) {
            throw SourceException("连不上接口：${e.message ?: e.javaClass.simpleName}", e)
        }
    }

/**
 * 编码嗅探。规则：
 *  1. 服务器明确声明了非 UTF-8 的字符集 → 直接听它的
 *  2. 否则先按严格 UTF-8 解；解不动说明其实是 GBK（很多站谎报 utf-8）
 */
private val strictUtf8 = Charsets.UTF_8.newDecoder()
    .onMalformedInput(CodingErrorAction.REPORT)
    .onUnmappableCharacter(CodingErrorAction.REPORT)

private fun decodeBody(bytes: ByteArray, declared: Charset?): String {
    if (declared != null && declared != Charsets.UTF_8 && declared != Charsets.ISO_8859_1) {
        return try {
            String(bytes, declared)
        } catch (_: Exception) {
            fallbackDecode(bytes)
        }
    }
    return fallbackDecode(bytes)
}

private fun fallbackDecode(bytes: ByteArray): String = try {
    strictUtf8.decode(ByteBuffer.wrap(bytes)).toString()
} catch (_: CharacterCodingException) {
    // GB18030 是 GBK 的超集，能解 GBK 也能解 GB2312
    try {
        String(bytes, Charset.forName("GB18030"))
    } catch (_: Exception) {
        String(bytes, Charsets.UTF_8)
    }
}
