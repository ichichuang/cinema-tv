package com.ichichuang.cinema.domain.repository

import com.ichichuang.cinema.domain.model.UpdateInfo
import java.io.File

/**
 * 在线更新。
 *
 * **能做的**：检查新版本 → 应用内下载（带进度）→ 调起系统安装界面（遥控器按一次「安装」）。
 *
 * **做不到的**：静默安装。安卓不允许侧载应用自己装自己 —— 这是系统安全限制，
 * 不是实现取舍。任何声称"一键无感升级"的方案，在非 root 电视上都是假的。
 */
interface Updater {

    /**
     * 查一次有没有新版本。已经是最新、或还没配置更新地址 → 返回 null。
     * 真的连不上就抛异常（调用方决定要不要让用户看见）。
     *
     * [force] = true 时忽略"刚查过"的缓存 —— 用户手动点「检查更新」必须真去查一次。
     */
    suspend fun check(currentVersionCode: Int, force: Boolean = false): UpdateInfo?

    /**
     * 下载新版本 APK，[onProgress] 回调 0f..1f。
     *
     * 返回前会做**安全校验**：包名、版本号、签名指纹必须跟当前装的这个应用一致，
     * 不一致就删掉并抛异常。理由很现实 —— 更新地址一旦被换掉或者被中间人劫持，
     * 没有这道校验就等于"应用自己给自己装木马"。
     */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File
}
