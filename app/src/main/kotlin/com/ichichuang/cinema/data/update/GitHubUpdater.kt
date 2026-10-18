package com.ichichuang.cinema.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.ichichuang.cinema.data.remote.DEFAULT_UA
import com.ichichuang.cinema.data.remote.SourceException
import com.ichichuang.cinema.data.remote.asInt
import com.ichichuang.cinema.data.remote.asText
import com.ichichuang.cinema.data.remote.getText
import com.ichichuang.cinema.data.remote.normalizeBaseUrl
import com.ichichuang.cinema.data.settings.CinemaSettings
import com.ichichuang.cinema.domain.model.UpdateInfo
import com.ichichuang.cinema.domain.repository.Updater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 在线更新的实现：读一份托管的 `version.json` → 下载 APK → 校验 → 交系统安装。
 *
 * 几个刻意的决定：
 *
 * 1. **更新地址由用户填**（默认指向发布方 GitHub Releases 的 latest 清单）。
 *    和片源地址一个道理：应用不把"命运"绑死在某个域名上。
 *    电视那边连不上 GitHub 时，换成镜像地址即可 —— 不用重新发版。
 *
 * 2. **清单字段名宽容**：versionCode / version_code / code 都认，
 *    apkUrl / apk_url / url / download_url 都认。写清单的是人，不是编译器。
 *
 * 3. **下载用独立的 http client**：共用的那个 25 秒 callTimeout 会把
 *    几十兆的下载腰斩（这是电视上最典型的"下到一半就失败"）。
 *
 * 4. **落地必校验**：包名 + 版本号 + 签名指纹。这三项一致，才敢交给系统去装。
 */
class GitHubUpdater(
    private val context: Context,
    private val http: OkHttpClient,
    private val json: Json,
    private val settings: CinemaSettings,
) : Updater {

    /** 下载专用：不设总时限（按读写超时兜底），复用共用 client 的连接池 */
    private val downloadClient: OkHttpClient = http.newBuilder()
        .callTimeout(0, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    // 同一进程内 10 分钟内不重复问服务器（启动时静默查一次，翻来覆去查没意义）
    private var lastCheckAt = 0L
    private var lastResult: UpdateInfo? = null

    override suspend fun check(currentVersionCode: Int, force: Boolean): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (!force && now - lastCheckAt < CHECK_INTERVAL_MS) return@withContext lastResult

            // 候选清单地址依次试：国内网络对 GitHub 各子域的通断经常变，
            // 多写一条候选，比"连不上就重新发一版"划算得多
            val candidates = settings.updateUrls.first().map { it.trim() }.filter { it.isNotEmpty() }
            if (candidates.isEmpty()) return@withContext null

            var lastError: Exception? = null
            for (raw in candidates) {
                val url = normalizeBaseUrl(raw)
                if (url == null) {
                    lastError = SourceException("更新地址格式不对，去设置里检查一下")
                    continue
                }
                try {
                    val info = parseManifest(http.getText(url))
                    lastCheckAt = now
                    lastResult = info.takeIf { it.isNewerThan(currentVersionCode) }
                    return@withContext lastResult
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 只留第一条错误：用户关心的主要是"默认地址为什么不行"
                    if (lastError == null) lastError = e
                    continue
                }
            }
            throw lastError ?: SourceException("没有可用的更新地址")
        }

    override suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "update").apply { mkdirs() }
            val target = File(dir, "cinema-${info.versionCode}.apk")

            // 之前下过就直接用（几十兆没必要重下），但仍然要重新校验一遍
            if (target.exists() && target.length() > 0L) {
                onProgress(1f)
                return@withContext verify(target, info)
            }

            // 主地址 + 镜像依次试：国内网络下"下载域名断掉"是常态，不是意外
            val candidates = (listOf(info.apkUrl) + listOfNotNull(info.mirrorUrl))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

            var lastError: Exception? = null
            var attempt = 0
            for (raw in candidates) {
                val url = normalizeBaseUrl(raw)
                if (url == null) {
                    lastError = SourceException("下载地址格式不对：$raw")
                    continue
                }
                if (attempt > 0) onProgress(0f) // 换线路，进度条从头来
                attempt++
                val file = try {
                    fetch(url, target, info, onProgress)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 只留第一条错误：主地址的失败原因比镜像的更有信息量
                    if (lastError == null) lastError = e
                    continue
                }
                // 校验失败（包名/版本/签名不符）是硬失败 —— 不换镜像重试，直接报错
                return@withContext verify(file, info)
            }
            throw lastError ?: SourceException("没有可用的下载地址")
        }

    /** 真正搬字节：流式写盘 + 按整数百分比回调进度 */
    private fun fetch(
        url: HttpUrl,
        target: File,
        info: UpdateInfo,
        onProgress: (Float) -> Unit,
    ): File {
        val tmp = File(target.parentFile, "${target.nameWithoutExtension}.part")
        tmp.delete()
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", DEFAULT_UA)
                .build()
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw SourceException("下载失败（HTTP ${response.code}）")
                }
                val body = response.body
                // contentLength 是权威值；没有就退回清单里写的大小
                val total = body.contentLength().takeIf { it > 0 } ?: info.sizeBytes
                body.byteStream().use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        var lastPct = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            read += n
                            if (total > 0) {
                                // 只在整数百分比变化时回调，避免每秒上百次重组
                                val pct = (read * 100 / total).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            tmp.delete()
            throw e
        } catch (e: SourceException) {
            tmp.delete()
            throw e
        } catch (e: Exception) {
            tmp.delete()
            throw SourceException("下载中断了，检查一下电视的网络再试", e)
        }

        if (info.sizeBytes > 0 && tmp.length() != info.sizeBytes) {
            tmp.delete()
            throw SourceException("下载不完整（应该 ${info.sizeText}），已丢弃")
        }
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw SourceException("保存安装包失败，电视存储空间可能不够")
        }
        return target
    }

    // ── 清单解析 ──────────────────────────────────────────────

    private fun parseManifest(text: String): UpdateInfo {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: throw SourceException("更新清单不是合法 JSON")

        val code = obj["versionCode"].asInt()
            ?: obj["version_code"].asInt()
            ?: obj["code"].asInt()
            ?: throw SourceException("更新清单里缺了版本号")

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

        return UpdateInfo(
            versionCode = code,
            versionName = name,
            apkUrl = apk,
            notes = notes,
            sizeBytes = size,
            mirrorUrl = mirror,
        )
    }

    // ── 安全校验 ──────────────────────────────────────────────

    /**
     * 包名、版本号、签名指纹三项对照。
     * 任何一项不一致 → 删文件 + 报错。宁可更新失败，也不装一个来路不明的东西。
     */
    private fun verify(apk: File, info: UpdateInfo): File {
        val pm = context.packageManager
        val flags = archiveFlags()

        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags)
        if (archive == null) {
            apk.delete()
            throw SourceException("下载下来的文件不是有效的安装包，已丢弃")
        }
        if (archive.packageName != context.packageName) {
            apk.delete()
            throw SourceException("安装包包名对不上，已丢弃")
        }

        val archiveCode = versionCodeOf(archive)
        if (archiveCode != info.versionCode.toLong()) {
            apk.delete()
            throw SourceException("安装包版本对不上（清单说 ${info.versionCode}，包里是 $archiveCode），已丢弃")
        }

        val mine = runCatching { fingerprint(pm.getPackageInfo(context.packageName, flags)) }.getOrNull()
        val theirs = fingerprint(archive)
        if (mine != null && theirs != null && mine != theirs) {
            apk.delete()
            throw SourceException("安装包签名和当前应用不一致，出于安全已丢弃")
        }
        return apk
    }

    private fun archiveFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

    /** 签名证书的 SHA-256（十六进制）。取不到返回 null（老系统/异常包），此时不阻断安装。 */
    private fun fingerprint(info: PackageInfo): String? {
        val cert = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()
        } ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        /** 同一进程内重复检查的间隔：10 分钟 */
        const val CHECK_INTERVAL_MS = 10 * 60 * 1000L
    }
}
