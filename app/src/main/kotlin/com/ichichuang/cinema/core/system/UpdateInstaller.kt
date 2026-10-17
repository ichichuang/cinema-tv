package com.ichichuang.cinema.core.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * 把"下载好的 APK"交给系统安装。
 *
 * 这里封装的都是安卓侧载的现实细节，不掺业务逻辑：
 *
 * 1. **FileProvider** —— 直接给系统一个 file:// 路径，Android 7 以上会抛
 *    FileUriExposedException。必须用 content:// + 临时授权。
 * 2. **"允许安装未知应用"** —— Android 8 以上要求每个应用单独申请这个权限，
 *    没申请时安装界面根本打不开。要先判断，再引导用户去系统页面开。
 * 3. **API 24/25**（这台电视可能是）—— 那会儿这是全局开关，没有 per-app 概念，
 *    所以判断条件里要放行，不能拦着。
 */
object UpdateInstaller {

    /**
     * 现在能不能调起安装界面。
     * 只判断"系统层面允许不允许"，不判断用户点不点 —— 那个我们管不了。
     */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** 跳到系统的「安装未知应用」授权页（针对本应用）。老系统没有这个页面就静默失败。 */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** 调起安装界面。返回是否成功打开（没打开通常是权限没给）。 */
    fun install(context: Context, apkPath: String): Boolean = runCatching {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File(apkPath),
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // 从 Application context 起 Activity 必须带这个 flag
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        true
    }.getOrDefault(false)
}
