package com.ichichuang.cinema.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.ichichuang.cinema.data.remote.DEFAULT_UA
import com.ichichuang.cinema.data.remote.SourceException
import com.ichichuang.cinema.data.remote.getText
import com.ichichuang.cinema.data.remote.normalizeBaseUrl
import com.ichichuang.cinema.data.settings.CinemaSettings
import com.ichichuang.cinema.domain.model.UpdateInfo
import com.ichichuang.cinema.domain.repository.Updater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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

    // Update redirects must not switch HTTPS to HTTP. Media clients keep their existing policy.
    private val updateClient = http.newBuilder().followSslRedirects(false).build()
    private val downloadMutex = Mutex()

    /** 下载专用：不设总时限（按读写超时兜底），复用共用 client 的连接池 */
    private val downloadClient: OkHttpClient = updateClient.newBuilder()
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
            if (!force && now - lastCheckAt < CHECK_INTERVAL_MS) {
                return@withContext lastResult?.takeIf { it.isNewerThan(currentVersionCode) }
            }

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
                    val info = UpdateManifestParser.parse(updateClient.getText(url), json)
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
            downloadMutex.withLock { downloadLocked(info, onProgress) }
        }

    private suspend fun downloadLocked(info: UpdateInfo, onProgress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        val target = File(dir, "cinema-${info.versionCode}.apk")

        // 之前下过就直接用（几十兆没必要重下），但仍然要重新校验一遍
        if (target.exists() && target.length() > 0L) {
            val verified = verify(target, info)
            onProgress(1f)
            return verified
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
                lastError = SourceException("下载地址格式不对，请检查更新清单")
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
            return verify(file, info)
        }
        throw lastError ?: SourceException("没有可用的下载地址")
    }

    /** 真正搬字节：流式写盘 + 按整数百分比回调进度 */
    private suspend fun fetch(
        url: HttpUrl,
        target: File,
        info: UpdateInfo,
        onProgress: (Float) -> Unit,
    ): File {
        val coroutineContext = currentCoroutineContext()
        return UpdateDownloadFiles.write(target, info.sizeBytes) { tmp ->
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
                                coroutineContext.ensureActive()
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
                throw e
            } catch (e: SourceException) {
                throw e
            } catch (e: Exception) {
                throw SourceException("下载中断了，检查一下电视的网络再试", e)
            }
        }
    }

    // ── 安全校验 ──────────────────────────────────────────────

    /**
     * 包名、版本号、签名指纹三项对照。
     * 任何一项不一致 → 删文件 + 报错。宁可更新失败，也不装一个来路不明的东西。
     */
    private fun verify(apk: File, info: UpdateInfo): File {
        val pm = context.packageManager
        val flags = archiveFlags()
        fun identity(pkg: PackageInfo) = UpdatePackageIdentity(
            pkg.packageName, versionCodeOf(pkg), fingerprints(pkg),
        )
        return UpdateArtifactVerifier(
            readInstalled = { identity(pm.getPackageInfo(context.packageName, flags)) },
            readArchive = { file -> pm.getPackageArchiveInfo(file.absolutePath, flags)?.let(::identity) },
        ).verify(apk, info)
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

    /** Return every current signer, not the signing history. Missing signers fail closed. */
    private fun fingerprints(info: PackageInfo): Set<String>? {
        val certs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        } ?: return null
        if (certs.isEmpty()) return null
        return certs.map { cert ->
            val digest = MessageDigest.getInstance("SHA-256").digest(cert.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    private companion object {
        /** 同一进程内重复检查的间隔：10 分钟 */
        const val CHECK_INTERVAL_MS = 10 * 60 * 1000L
    }
}
