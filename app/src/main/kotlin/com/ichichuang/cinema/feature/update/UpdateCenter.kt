package com.ichichuang.cinema.feature.update

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import androidx.compose.runtime.Immutable
import com.ichichuang.cinema.domain.model.UpdateInfo
import com.ichichuang.cinema.domain.repository.Updater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class UpdatePhase {
    /** 什么都没发生（默认状态，界面上什么都不显示） */
    IDLE,
    CHECKING,
    /** 已是最新 */
    UP_TO_DATE,
    /** 有新版本，等用户决定 */
    AVAILABLE,
    DOWNLOADING,
    /** 下载完了，等系统安装界面 */
    READY,
    ERROR,
}

@Immutable
data class UpdateUiState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val info: UpdateInfo? = null,
    val progress: Float = 0f,
    /** 一句给用户看的中文（结果和错误都走这里，不暴露 http 细节） */
    val message: String? = null,
    /** 下载好的安装包路径 */
    val apkPath: String? = null,
    /** 浮层是否打开：有新版本也不弹窗，用户点了才开 */
    val panelOpen: Boolean = false,
) {
    /** 首页右上角的金标只看这个 */
    val hasUpdate: Boolean get() = info != null && phase != UpdatePhase.ERROR
}

/**
 * 在线更新的状态中枢。
 *
 * 为什么是 Koin 单例而不是 ViewModel：**首页**（金标 + 浮层）和**设置页**（检查更新）
 * 必须共享同一份状态。各页面各建一个 ViewModel，就会出现"首页说没新版、设置页说有新版"
 * 这种听起来离谱但一定会发生的鬼故事。
 *
 * 生命周期跟随应用 —— 对一个常驻电视的应用来说，这本来就是对的。
 */
class UpdateCenter(
    context: Context,
    private val updater: Updater,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 当前装的版本（发布时对不上就是这里出问题） */
    val currentVersionName: String
    private val currentVersionCode: Int

    init {
        val info = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        currentVersionCode = if (info == null) 0 else installedVersionCode(info)
        currentVersionName = info?.versionName ?: "?"
    }

    /** PackageInfo.versionCode 从 API 28 起废弃（要读 longVersionCode） */
    @Suppress("DEPRECATION")
    private fun installedVersionCode(info: PackageInfo): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt() else info.versionCode

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    /** 应用起来了就悄悄查一次：查到只亮个小金标，查不到/连不上都当没这回事 */
    fun checkSilently() {
        val phase = _state.value.phase
        // 上次失败过就再给一次机会：开机时网络还没起来是常见情况，不该就此沉默一整晚
        if (phase != UpdatePhase.IDLE && phase != UpdatePhase.ERROR) return
        scope.launch { runCheck(force = false, whisper = true) }
    }

    /** 用户手动点「检查更新」：必须给个回话（成功、最新、失败都要说） */
    fun checkManually() {
        if (_state.value.phase == UpdatePhase.CHECKING || _state.value.phase == UpdatePhase.DOWNLOADING) return
        scope.launch { runCheck(force = true, whisper = false) }
    }

    private suspend fun runCheck(force: Boolean, whisper: Boolean) {
        _state.update { it.copy(phase = UpdatePhase.CHECKING, message = null) }
        try {
            val info = updater.check(currentVersionCode, force)
            _state.update {
                if (info == null) {
                    it.copy(
                        phase = UpdatePhase.UP_TO_DATE,
                        info = null,
                        message = if (whisper) null else "已是最新版本（v$currentVersionName）",
                    )
                } else {
                    it.copy(
                        phase = UpdatePhase.AVAILABLE,
                        info = info,
                        message = if (whisper) null else "发现新版本 v${info.versionName}",
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    phase = UpdatePhase.ERROR,
                    message = if (whisper) null else (e.message ?: "检查更新失败"),
                )
            }
        }
    }

    fun openPanel() = _state.update { it.copy(panelOpen = true) }

    fun closePanel() = _state.update { it.copy(panelOpen = false) }

    fun download() {
        val info = _state.value.info ?: return
        if (_state.value.phase == UpdatePhase.DOWNLOADING) return
        scope.launch {
            _state.update { it.copy(phase = UpdatePhase.DOWNLOADING, progress = 0f, message = null) }
            try {
                val file = updater.download(info) { p ->
                    _state.update { s ->
                        if (s.phase == UpdatePhase.DOWNLOADING) s.copy(progress = p) else s
                    }
                }
                _state.update {
                    it.copy(
                        phase = UpdatePhase.READY,
                        progress = 1f,
                        apkPath = file.absolutePath,
                        message = "下载完成，正在打开安装界面…",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(phase = UpdatePhase.ERROR, message = e.message ?: "下载失败")
                }
            }
        }
    }

    /** 系统安装界面已经打开了 —— 如实告诉用户接下来要做什么 */
    fun onInstallLaunched() = _state.update {
        it.copy(message = "已打开安装界面：选「安装」，装完回来就是新版本")
    }

    fun onInstallFailed() = _state.update {
        it.copy(message = "没能打开安装界面，先点「去允许」把权限打开")
    }
}
