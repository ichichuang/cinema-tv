package com.ichichuang.cinema.domain.model

import java.util.Locale

/**
 * 一个新版本的样子 —— 来自发布方托管的那份 `version.json`。
 *
 * 字段故意做得宽容：清单是人手写的，谁都可能把 versionCode 写成字符串、
 * 把下载地址叫 url 而不是 apkUrl。解析层（GitHubUpdater）会把这些都兜住，
 * 到这里的已经是「一定能用」的最小集合。
 */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    /** 新版本 APK 的直链（https 更稳，但 http 也认） */
    val apkUrl: String,
    /** 更新说明，多行用 \n 分隔；没写就空串 */
    val notes: String = "",
    /** 安装包大小（字节）。清单里没写就是 0 —— 界面不显示"0 MB"这种废话 */
    val sizeBytes: Long = 0L,
    /**
     * 备用下载地址（镜像）。
     * 用途很现实：国内直连 GitHub 的下载域名有时会断 —— 主地址下不动就自动换这条。
     */
    val mirrorUrl: String? = null,
    /** Optional legacy-compatible integrity check, not independent publisher authentication. */
    val sha256: String? = null,
) {
    fun isNewerThan(currentVersionCode: Int): Boolean = versionCode > currentVersionCode

    /** "12.4 MB"；没有大小信息时是空串 */
    val sizeText: String
        get() = if (sizeBytes > 0) {
            String.format(Locale.US, "%.1f MB", sizeBytes / 1024.0 / 1024.0)
        } else {
            ""
        }
}
