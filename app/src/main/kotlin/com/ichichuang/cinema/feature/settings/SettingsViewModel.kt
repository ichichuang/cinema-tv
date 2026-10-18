package com.ichichuang.cinema.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichichuang.cinema.data.settings.CinemaSettings
import com.ichichuang.cinema.data.settings.ImageCache
import com.ichichuang.cinema.domain.model.DefaultMovieFilter
import com.ichichuang.cinema.domain.repository.MovieRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** 接口连通性测试的结果（设置页那一行状态字） */
sealed interface SourceTest {
    val text: String

    /** 还没测过：界面上显示"点一下测测看"的引导文案（文案在 UI 里） */
    data object Idle : SourceTest {
        override val text: String = ""
    }

    data object Testing : SourceTest {
        override val text: String = "正在连接接口…"
    }

    data class Ok(override val text: String) : SourceTest

    data class Failed(override val text: String) : SourceTest

    val passed: Boolean get() = this is Ok
}

data class SettingsUiState(
    /** 当前生效的接口地址（空串 = 还没配置）。只读展示，改动全部来自手机扫码配置 */
    val apiBase: String = "",
    /** 当前生效的更新地址（只读展示） */
    val updateUrl: String = "",
    /** 接口连通性测试 */
    val test: SourceTest = SourceTest.Idle,
    /** 扫码配置浮层是否打开 */
    val configOpen: Boolean = false,
    /** 配置服务网址（手机/电脑要输入的地址）；还没起来就是 null */
    val serverUrl: String? = null,
    /** 配置服务状态说明（等待手机连接 / 已收到配置 / 测试结果） */
    val serverMessage: String? = null,
    /** 清缓存的反馈（"已清掉 32.4 MB 缓存"） */
    val cacheMessage: String? = null,
)

/**
 * 设置页逻辑。
 *
 * v0.7.0 起，这个页面**没有任何输入框**（电视上打字是灾难，真机反馈的原话是"反感"）：
 * 配置通过「扫码配置」在手机上完成 —— 电视只负责起一个局域网小网页、
 * 接收手机推来的配置、然后**自动测一次**把结果同时告诉电视和手机。
 */
class SettingsViewModel(
    private val settings: CinemaSettings,
    private val repository: MovieRepository,
    private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val server = ConfigServer(
        onSave = { apiBase, updateUrl -> applyFromPhone(apiBase, updateUrl) },
    )

    init {
        viewModelScope.launch {
            settings.apiBase.collect { addr -> _state.update { it.copy(apiBase = addr) } }
        }
        viewModelScope.launch {
            settings.updateUrl.collect { url -> _state.update { it.copy(updateUrl = url) } }
        }
    }

    // ── 接口连通性 ─────────────────────────────────────────────

    /**
     * 测一次能不能连通。
     *
     * 为什么直接用"取一页影片"来测：这是真实使用中最关键的那条路 ——
     * 走的是同一套编码兼容（GBK 谎报 UTF-8 那些坑）/ 三次降级逻辑。
     * 能取到片子才叫"能用"，能 ping 通不算。
     */
    fun testSource() {
        if (_state.value.test == SourceTest.Testing) return
        viewModelScope.launch { _state.update { it.copy(test = runTest()) } }
    }

    private suspend fun runTest(apiBase: String = _state.value.apiBase): SourceTest {
        if (apiBase.isBlank()) {
            return SourceTest.Failed("还没配置接口地址：先扫码配置一下")
        }
        return try {
            val page = repository.movies(DefaultMovieFilter, page = 1)
            val count = if (page.total > 0) page.total else page.movies.size
            if (page.movies.isEmpty()) {
                SourceTest.Failed("接口能连上，但一条数据都没有：检查地址是否写全（结尾的 / 别丢）")
            } else {
                SourceTest.Ok("已连通：取到 $count 部影片" + if (page.relaxed) "（本站筛选标注不全，已自动放宽）" else "")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SourceTest.Failed("连不上：" + (e.message ?: "未知原因"))
        }
    }

    // ── 扫码配置（手机 → 电视）──────────────────────────────────

    fun openConfig() {
        _state.update { it.copy(configOpen = true) }
        viewModelScope.launch {
            val url = withContext(Dispatchers.IO) {
                runCatching {
                    server.setCurrentValues(_state.value.apiBase, _state.value.updateUrl)
                    server.start()
                }.getOrNull()
            }
            _state.update {
                it.copy(
                    serverUrl = url,
                    serverMessage = if (url == null) {
                        "配置服务没能启动：检查电视网络连接后，关掉重开一次"
                    } else {
                        "等待手机连接…"
                    },
                )
            }
        }
    }

    fun closeConfig() {
        server.stop()
        _state.update { it.copy(configOpen = false, serverUrl = null, serverMessage = null) }
    }

    /**
     * 手机点「保存」之后：先落盘，再自动测一次连通性，结果原路返回给手机。
     *
     * 注意线程：这里跑在配置服务的线程上（runBlocking），
     * 所以所有状态更新必须走 StateFlow（线程安全），不要碰 UI。
     */
    private suspend fun applyFromPhone(apiBase: String, updateUrl: String): PhoneSaveResult {
        _state.update {
            it.copy(
                test = SourceTest.Testing,
                serverMessage = "已收到手机配置，正在测试连通性…",
            )
        }
        settings.setApiBase(apiBase)
        settings.setUpdateUrl(updateUrl)

        val result = runTest(apiBase)
        _state.update { it.copy(test = result, serverMessage = result.text) }
        return PhoneSaveResult(ok = result.passed, message = result.text)
    }

    // ── 缓存 ───────────────────────────────────────────────────

    /** 清空海报缓存：真清，并如实告诉用户清掉了多少 */
    fun clearImageCache() {
        viewModelScope.launch {
            val freed = ImageCache.clear(context)
            _state.update {
                it.copy(
                    cacheMessage = when {
                        freed == null -> "这次没清掉，重启一下应用再试"
                        freed <= 0L -> "缓存本来就是空的"
                        else -> "已清掉 " + "%.1f".format(Locale.US, freed / 1024.0 / 1024.0) + " MB 海报缓存"
                    },
                )
            }
        }
    }

    override fun onCleared() {
        // 离开设置页（或应用退出）就关掉配置服务：平时不监听任何端口
        server.stop()
    }
}
