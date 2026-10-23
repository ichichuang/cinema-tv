package com.ichichuang.cinema.data.update

import com.ichichuang.cinema.data.remote.SourceException
import com.ichichuang.cinema.domain.model.UpdateInfo
import java.io.File
import java.security.MessageDigest

internal data class UpdatePackageIdentity(
    val packageName: String,
    val versionCode: Long,
    val signers: Set<String>?,
)

/** PackageManager remains the Android adapter; policy and file rejection are testable on the JVM. */
internal class UpdateArtifactVerifier(
    private val readInstalled: () -> UpdatePackageIdentity?,
    private val readArchive: (File) -> UpdatePackageIdentity?,
) {
    fun verify(apk: File, info: UpdateInfo): File {
        var accepted = false
        try {
            if (!apk.isFile || apk.length() <= 0L) {
                throw SourceException("安装包为空或无法读取，已丢弃")
            }
            if (info.sizeBytes < 0 || (info.sizeBytes > 0 && apk.length() != info.sizeBytes)) {
                throw SourceException("安装包大小与清单不符，已丢弃")
            }
            info.sha256?.let { expected ->
                if (!Regex("[0-9a-fA-F]{64}").matches(expected)) {
                    throw SourceException("更新清单的摘要格式不对，已丢弃")
                }
                val digest = MessageDigest.getInstance("SHA-256")
                apk.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(expected, ignoreCase = true)) {
                    throw SourceException("安装包内容校验失败，已丢弃")
                }
            }
            val archive = runCatching { readArchive(apk) }.getOrNull()
            if (archive == null) {
                throw SourceException("下载下来的文件不是有效的安装包，已丢弃")
            }
            val installed = runCatching { readInstalled() }.getOrNull()
            if (archive.packageName != installed?.packageName) {
                throw SourceException("安装包包名对不上，已丢弃")
            }
            if (archive.versionCode != info.versionCode.toLong()) {
                throw SourceException("安装包版本对不上，已丢弃")
            }
            if (info.versionCode <= 0 || archive.versionCode <= installed.versionCode) {
                throw SourceException("安装包不是比当前应用更新的版本，已丢弃")
            }
            if (!UpdateSignaturePolicy.matches(installed.signers, archive.signers)) {
                throw SourceException("无法确认安装包签名与当前应用一致，出于安全已丢弃")
            }
            accepted = true
            return apk
        } finally {
            // Lookup, I/O, cancellation and policy failures all remove the rejected cached file.
            if (!accepted) apk.delete()
        }
    }
}
