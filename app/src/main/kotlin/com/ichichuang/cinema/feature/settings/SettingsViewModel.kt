package com.ichichuang.cinema.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichichuang.cinema.data.CatalogSource
import com.ichichuang.cinema.data.SourceCatalog
import com.ichichuang.cinema.data.settings.CinemaSettings
import com.ichichuang.cinema.data.settings.ImageCache
import com.ichichuang.cinema.data.settings.SavedSource
import com.ichichuang.cinema.domain.model.DefaultMovieFilter
import com.ichichuang.cinema.domain.repository.MovieRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
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
    /** 当前生效的接口地址（空串 = 还没配置）。只读展示，改动来自「选择片源」或手机扫码 */
    val apiBase: String = "",
    /** 当前生效的源名字（"非凡" / "自定义"…） */
    val apiName: String = "",
    /** 当前生效的更新地址（只读展示） */
    val updateUrl: String = "",
    /** 接口连通性测试 */
    val test: SourceTest = SourceTest.Idle,
    /** 保存过的源清单（一键导入 / 手机添加，都会进这里） */
    val sources: List<SavedSource> = emptyList(),
    /** 推荐源清单（拉过一次就缓存，离线也能看） */
    val catalog: List<CatalogSource> = emptyList(),
    val catalogLoading: Boolean = false,
    val catalogError: String? = null,
    /** 「选择片源」浮层是否打开 */
    val pickerOpen: Boolean = false,
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
 * v0.7.0 起，这个页面**没有任何输入框**（电视上打字是灾难）：
 * 配置通过「选择片源」（一键导入推荐源）或「扫码配置」（手机填自己的地址）完成，
 * 电视只负责：起一个局域网小网页、接收手机推来的配置、然后**自动测一次**把结果
 * 同时告诉电视和手机。
 *
 * v0.8.0 起支持**多个源**：保存过的源都存在 DataStore 里，点一下切换；
 * 推荐清单从发布仓库拉取并缓存 —— 应用本体依然不内置任何片源（红线）。
 */
class SettingsViewModel(
    private val settings: CinemaSettings,
    private val repository: MovieRepository,
    private val catalog: SourceCatalog,
    private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val server = ConfigServer(
        onSave = { apiBase, updateUrl -> applyFromPhone(apiBase, updateUrl) },
        onSelect = { url -> selectFromPhone(url) },
        onSourcesPayload = { sourcesPayload() },
        onRefreshCatalog = { refreshCatalogFromPhone() },
    )

    init {
        viewModelScope.launch {
            settings.apiBase.collect { addr -> _state.update { it.copy(apiBase = addr) } }
        }
        viewModelScope.launch {
            settings.apiName.collect { name -> _state.update { it.copy(apiName = name) } }
        }
        viewModelScope.launch {
            settings.updateUrl.collect { url -> _state.update { it.copy(updateUrl = url) } }
        }
        viewModelScope.launch {
            settings.sources.collect { list -> _state.update { it.copy(sources = list) } }
        }
        // 缓存住的推荐清单先顶上：没网也能翻列表
        viewModelScope.launch {
            val cached = catalog.cached()
            if (cached.isNotEmpty()) _state.update { it.copy(catalog = cached) }
        }
    }

    // ── 片源选择（一键导入）─────────────────────────────────────

    fun openPicker() {
        _state.update { it.copy(pickerOpen = true, catalogError = null) }
        // 第一次打开、本地还没有清单 → 自动去拉（有缓存就不打扰）
        if (_state.value.catalog.isEmpty()) refreshCatalog()
    }

    fun closePicker() = _state.update { it.copy(pickerOpen = false) }

    fun refreshCatalog() {
        if (_state.value.catalogLoading) return
        _state.update { it.copy(catalogLoading = true, catalogError = null) }
        viewModelScope.launch {
            catalog.refresh()
                .onSuccess { list ->
                    _state.update { it.copy(catalog = list, catalogLoading = false, catalogError = null) }
                }
                .onFailure { e ->
                    _state.update { it.copy(catalogLoading = false, catalogError = friendlyError(e)) }
                }
        }
    }

    /** 电视上点了一条源：切换 + 关面板 + 自动测试（结果出现在设置页状态行） */
    fun selectSource(name: String, url: String) {
        _state.update { it.copy(pickerOpen = false) }
        viewModelScope.launch { activateAndTest(name, url) }
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
            return SourceTest.Failed("还没配置接口地址：先「选择片源」或扫码配置")
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

    /** 切换 + 融合进保存清单 + 测一次，一条龙（电视和手机共用） */
    private suspend fun activateAndTest(name: String, url: String): SourceTest {
        _state.update { it.copy(test = SourceTest.Testing) }
        runCatching { settings.activateSource(name, url) }
        val result = runTest(url)
        _state.update { it.copy(test = result) }
        return result
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
     * 手机保存"自定义地址"之后：先落盘，再自动测一次，结果原路返回给手机。
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
        runCatching { settings.setUpdateUrl(updateUrl) }
        val known = settings.sources.first().firstOrNull { it.url == apiBase.trim() }?.name
        val result = activateAndTest(known ?: CinemaSettings.DEFAULT_SOURCE_NAME, apiBase)
        _state.update { it.copy(serverMessage = result.text) }
        return PhoneSaveResult(ok = result.passed, message = result.text)
    }

    /** 手机上点了一条源（推荐源或已保存的源）→ 电视切换 + 测试 */
    private suspend fun selectFromPhone(url: String): PhoneSaveResult {
        val target = url.trim()
        if (target.isEmpty()) return PhoneSaveResult(false, "没收到要切换的地址")
        if (!target.startsWith("http://") && !target.startsWith("https://") && target != CinemaSettings.DEMO) {
            return PhoneSaveResult(false, "地址要以 http:// 或 https:// 开头")
        }
        val name = settings.sources.first().firstOrNull { it.url == target }?.name
            ?: _state.value.catalog.firstOrNull { it.url == target }?.name
            ?: CinemaSettings.DEFAULT_SOURCE_NAME
        _state.update { it.copy(serverMessage = "已收到手机的切换请求，正在测试…") }
        val result = activateAndTest(name, target)
        _state.update { it.copy(serverMessage = result.text) }
        return PhoneSaveResult(ok = result.passed, message = result.text)
    }

    /** 手机上点「获取推荐源」→ 电视去拉（拉完手机刷新列表就能看到） */
    private suspend fun refreshCatalogFromPhone(): PhoneSaveResult {
        _state.update { it.copy(catalogLoading = true, catalogError = null) }
        return catalog.refresh().fold(
            onSuccess = { list ->
                _state.update { it.copy(catalog = list, catalogLoading = false, catalogError = null) }
                PhoneSaveResult(true, "已获取 " + list.size + " 个推荐源，点一下就能用")
            },
            onFailure = { e ->
                val msg = friendlyError(e)
                _state.update { it.copy(catalogLoading = false, catalogError = msg) }
                PhoneSaveResult(false, msg)
            },
        )
    }

    /** 手机页渲染列表需要的数据（同一个源上，不存在跨域问题） */
    private suspend fun sourcesPayload(): String {
        val saved = settings.sources.first()
        val active = settings.apiBase.first()
        val list = _state.value.catalog.ifEmpty { catalog.cached() }
        return buildJsonObject {
            put("ok", true)
            put("active", active)
            putJsonArray("saved") {
                saved.forEach { source ->
                    addJsonObject {
                        put("name", source.name)
                        put("url", source.url)
                    }
                }
            }
            putJsonArray("catalog") {
                list.forEach { source ->
                    addJsonObject {
                        put("name", source.name)
                        put("url", source.url)
                        put("info", source.info)
                    }
                }
            }
        }.toString()
    }

    private fun friendlyError(e: Throwable): String =
        "获取推荐源失败：" + (e.message ?: "网络不通，稍后再试")

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
