package com.ichichuang.cinema.feature.settings

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.ichichuang.cinema.core.design.Gold
import com.ichichuang.cinema.core.design.Ink
import com.ichichuang.cinema.core.design.Motion
import com.ichichuang.cinema.core.design.Panel
import com.ichichuang.cinema.core.design.PanelHi
import com.ichichuang.cinema.core.design.Scrim
import com.ichichuang.cinema.core.design.TextMain
import com.ichichuang.cinema.core.design.TextSub
import com.ichichuang.cinema.core.design.rememberTvMetrics
import com.ichichuang.cinema.core.focus.tvFocus
import com.ichichuang.cinema.core.ui.FilterChip
import com.ichichuang.cinema.data.CatalogSource
import com.ichichuang.cinema.data.settings.CinemaSettings
import com.ichichuang.cinema.data.settings.SavedSource
import com.ichichuang.cinema.data.sourceUrlKey
import com.ichichuang.cinema.feature.update.UpdateCenter
import com.ichichuang.cinema.feature.update.UpdateOverlay
import com.ichichuang.cinema.feature.update.UpdatePhase
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

/**
 * 设置页 —— **没有一个输入框**。
 *
 * 电视上的配置只有两条路（都按遥控器能走完）：
 * - 「选择片源」：**一键导入** —— 推荐源清单（发布方实测排行）从网上拉下来，
 *   点一条就自动切换 + 测试；保存过的源也在这里，随时换回来
 * - 「扫码配置」：手机扫一下码，填自己的地址 / 改更新地址
 *
 * 排版规矩（页面级硬要求）：**所有容器都是固定尺寸**。
 * 源名字、地址、测试结果、检查更新结果、缓存结果全部放在**高度写死的槽位**里 ——
 * 有内容没内容、一行还是两行，都不会把下面的东西顶动。
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    updateCenter: UpdateCenter,
    vm: SettingsViewModel = koinViewModel(),
) {
    BackHandler(onBack = onBack)
    val m = rememberTvMetrics()
    val state by vm.state.collectAsState()
    val updateState by updateCenter.state.collectAsState()

    val overlayOpen = updateState.panelOpen || state.configOpen || state.pickerOpen || state.diagnosticsOpen

    val pickFocus = remember { FocusRequester() }
    val scanFocus = remember { FocusRequester() }
    val diagnosticsFocus = remember { FocusRequester() }
    var pickWasOpen by remember { mutableStateOf(false) }
    var configWasOpen by remember { mutableStateOf(false) }
    var diagnosticsWasOpen by remember { mutableStateOf(false) }

    // 浮层关掉后把焦点还给"打开它的那个按钮"（遥控器用户从哪进去回哪去）
    LaunchedEffect(state.pickerOpen) {
        if (state.pickerOpen) {
            pickWasOpen = true
        } else if (pickWasOpen) {
            pickWasOpen = false
            delay(80)
            runCatching { pickFocus.requestFocus() }
        }
    }
    LaunchedEffect(state.configOpen) {
        if (state.configOpen) {
            configWasOpen = true
        } else if (configWasOpen) {
            configWasOpen = false
            delay(80)
            runCatching { scanFocus.requestFocus() }
        }
    }
    LaunchedEffect(state.diagnosticsOpen) {
        if (state.diagnosticsOpen) {
            diagnosticsWasOpen = true
        } else if (diagnosticsWasOpen) {
            diagnosticsWasOpen = false
            delay(Motion.DUR_FAST.toLong())
            runCatching { diagnosticsFocus.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Ink)
                // 可滚动：电视字号被系统调大、或将来加设置项时，内容不会被裁在屏幕外
                .verticalScroll(rememberScrollState())
                .padding(m.dp(40f))
                // 浮层打开时关掉底下整屏的焦点（否则方向键会透过浮层乱跑）
                .then(if (overlayOpen) Modifier.focusProperties { canFocus = false } else Modifier),
        ) {
            Text(
                text = "设置",
                color = TextMain,
                fontSize = m.sp(30f),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(m.dp(8f)))
            Text(
                text = "点「选择资源」挑一个；想用自己的地址，就用手机扫码填。",
                color = TextSub,
                fontSize = m.sp(16f),
                maxLines = 1,
            )
            Spacer(Modifier.height(m.dp(22f)))

            // ── 资源（唯一必须由用户提供的东西）────────────────────
            SectionCard(appearIndex = 0) {
                Text("资源", color = TextMain, fontSize = m.sp(20f))
                Spacer(Modifier.height(m.dp(8f)))

                // 第 1 行：源名字 + 地址（固定 26dp 的槽位）
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(m.dp(26f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.apiBase.isBlank()) {
                        Text(
                            text = "还没添加 —— 应用本身不带内容",
                            color = Gold.copy(alpha = 0.9f),
                            fontSize = m.sp(16f),
                            maxLines = 1,
                        )
                    } else {
                        Text(
                            text = state.apiName.ifBlank { CinemaSettings.DEFAULT_SOURCE_NAME },
                            color = TextMain,
                            fontSize = m.sp(17f),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(m.dp(10f)))
                        Text(
                            text = state.apiBase,
                            color = TextSub,
                            fontSize = m.sp(15f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(Modifier.height(m.dp(6f)))

                // 第 2 行：一句话状态（固定 22dp 的槽位）
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(m.dp(22f)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = if (state.sources.isEmpty()) {
                            "点「选择资源」挑一个；自己的地址用手机扫码填"
                        } else {
                            "已添加 " + state.sources.size + " 个 · 已启用 " +
                                state.enabledUrls.size + " 个 · 首页会一起显示"
                        },
                        color = TextSub,
                        fontSize = m.sp(14f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(m.dp(16f)))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GoldButton(
                        text = "选择资源",
                        focusRequester = pickFocus,
                        onClick = vm::openPicker,
                    )
                    Spacer(Modifier.width(m.dp(14f)))
                    DarkButton(
                        text = "手机配置",
                        width = m.dp(170f),
                        focusRequester = scanFocus,
                        onClick = vm::openConfig,
                    )
                    Spacer(Modifier.width(m.dp(14f)))
                    DarkButton(
                        text = "测试连接",
                        width = m.dp(170f),
                        enabled = state.test != SourceTest.Testing,
                        onClick = vm::testSource,
                    )
                }
                Spacer(Modifier.height(m.dp(12f)))
                StatusLine(
                    text = when (val t = state.test) {
                        SourceTest.Idle -> "选好资源后，会自动测一次连接"
                        else -> t.text
                    },
                    highlight = state.test.passed,
                )
            }

            Spacer(Modifier.height(m.dp(22f)))

            // ── 首页列表展示：每行海报数（用户点名要的 4–8 档）────
            SectionCard(appearIndex = 1) {
                Text("首页列表展示", color = TextMain, fontSize = m.sp(20f))
                Spacer(Modifier.height(m.dp(8f)))
                Text(
                    text = "每行显示几个海报 · 改完回首页立刻生效",
                    color = TextSub,
                    fontSize = m.sp(15f),
                    maxLines = 1,
                )
                Spacer(Modifier.height(m.dp(14f)))
                Row(horizontalArrangement = Arrangement.spacedBy(m.dp(12f))) {
                    (CinemaSettings.MIN_HOME_COLUMNS..CinemaSettings.MAX_HOME_COLUMNS).forEach { n ->
                        FilterChip(
                            label = n.toString(),
                            selected = state.homeColumns == n,
                            big = true,
                            onClick = { vm.setHomeColumns(n) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(m.dp(22f)))

            // ── 在线更新 ────────────────────────────────────────
            SectionCard(appearIndex = 2) {
                Text("在线更新", color = TextMain, fontSize = m.sp(20f))
                Spacer(Modifier.height(m.dp(16f)))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val checking = updateState.phase == UpdatePhase.CHECKING
                    val downloading = updateState.phase == UpdatePhase.DOWNLOADING
                    DarkButton(
                        text = when {
                            checking -> "检查中…"
                            downloading -> "更新中…"
                            else -> "检查更新"
                        },
                        width = m.dp(170f),
                        enabled = !checking && !downloading,
                        onClick = updateCenter::checkManually,
                    )
                    // 「查看更新」的固定槽位：有新版才亮出来，位置永远留着
                    ViewUpdateSlot(
                        visible = updateState.hasUpdate,
                        onClick = updateCenter::openPanel,
                    )
                }
                Spacer(Modifier.height(m.dp(12f)))
                StatusLine(
                    // 正在后台下载时如实报进度：否则用户在设置页看到的是"什么都没发生"
                    text = updateState.message
                        ?: when (updateState.phase) {
                            UpdatePhase.DOWNLOADING ->
                                "正在下载新版本 ${(updateState.progress * 100).toInt()}%…"
                            else -> "有新版本会在首页提醒你。"
                        },
                    highlight = updateState.hasUpdate,
                )
            }

            Spacer(Modifier.height(m.dp(22f)))

            // ── 图片缓存：真能清掉的那一项 ──────────────────────
            SectionCard(appearIndex = 3) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("图片缓存", color = TextMain, fontSize = m.sp(20f))
                        Spacer(Modifier.height(m.dp(8f)))
                        Text(
                            text = state.cacheMessage
                                ?: "海报缓存攒多了会占空间，存储紧张时清一下。",
                            color = if (state.cacheMessage != null) Gold.copy(alpha = 0.9f) else TextSub,
                            fontSize = m.sp(15f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(m.dp(16f)))
                    DarkButton(text = "清空缓存", width = m.dp(170f), onClick = vm::clearImageCache)
                }
            }

            Spacer(Modifier.height(m.dp(22f)))

            // ── 系统信息：国产电视的「关于本机」经常压根不显示 Android 版本
            //    （只有型号/序列号），所以 App 里给一份固定可查的 ——
            //    反馈兼容问题时截这一屏就够，不用来回猜"你那台是什么系统"。
            SectionCard(appearIndex = 4) {
                Text("系统信息", color = TextMain, fontSize = m.sp(20f))
                Spacer(Modifier.height(m.dp(8f)))
                Text(
                    text = "反馈问题时截这一屏就行。",
                    color = TextSub,
                    fontSize = m.sp(15f),
                    maxLines = 1,
                )
                Spacer(Modifier.height(m.dp(12f)))
                Text(
                    text = rememberSystemInfo(),
                    color = TextMain.copy(alpha = 0.85f),
                    fontSize = m.sp(16f),
                    lineHeight = m.sp(26f),
                )
                Spacer(Modifier.height(m.dp(16f)))
                GoldButton(
                    text = "设备诊断",
                    width = m.dp(170f),
                    focusRequester = diagnosticsFocus,
                    onClick = vm::openDiagnostics,
                )
            }

            Spacer(Modifier.height(m.dp(26f)))
            Text(
                text = "当前版本 v${updateCenter.currentVersionName}",
                color = TextSub,
                fontSize = m.sp(16f),
            )
        }

        // 「选择片源」浮层（一键导入 + 已保存的源）
        SourcePicker(
            visible = state.pickerOpen,
            enabled = state.enabledUrls,
            saved = state.sources,
            catalog = state.catalog,
            loading = state.catalogLoading,
            error = state.catalogError,
            onToggle = vm::toggleSource,
            onRefresh = vm::refreshCatalog,
            onClose = vm::closePicker,
        )

        // 扫码配置浮层（手机 → 电视）
        ConfigOverlay(
            visible = state.configOpen,
            url = state.serverUrl,
            message = state.serverMessage,
            passed = state.test.passed,
            onDismiss = vm::closeConfig,
        )

        DeviceDiagnosticsOverlay(
            visible = state.diagnosticsOpen,
            loading = state.diagnosticsLoading,
            snapshot = state.diagnostics,
            message = state.diagnosticsMessage,
            reportUrl = state.diagnosticReportUrl,
            reportLoading = state.diagnosticReportLoading,
            onRefresh = vm::refreshDiagnostics,
            onOpenSettings = vm::openDiagnosticSettings,
            onShareReport = vm::shareDiagnosticReport,
            onStopReport = vm::stopDiagnosticReport,
            onClose = vm::closeDiagnostics,
        )

        // 更新浮层（和有新版时的下载/安装流程共用同一套界面）
        UpdateOverlay(
            state = updateState,
            currentVersionName = updateCenter.currentVersionName,
            onDownload = updateCenter::download,
            onDismiss = updateCenter::closePanel,
            onInstallLaunched = updateCenter::onInstallLaunched,
            onInstallFailed = updateCenter::onInstallFailed,
        )
    }
}

/**
 * 「选择片源」浮层 —— 一键导入就在这里。
 *
 * 上下两块：**推荐源**（从发布仓库拉的实测排行）和**已保存的源**。
 * v0.9.0 起是**勾选制**：点一条 = 启用 / 取消（可多选 → 首页聚合），
 * 勾选瞬间自动测一次。卡片整体尺寸固定，列表内部滚动、
 * 焦点走进列表时自动滚到看得见的位置 —— 排版不会因为清单长短变形。
 */
@Composable
private fun SourcePicker(
    visible: Boolean,
    enabled: List<String>,
    saved: List<SavedSource>,
    catalog: List<CatalogSource>,
    loading: Boolean,
    error: String?,
    onToggle: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    val m = rememberTvMetrics()
    val closeFocus = remember { FocusRequester() }
    val firstRow = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var autoFocused by remember { mutableStateOf(false) }

    // 地址按归一化比对："…/vod" 与 "…/vod/" 是同一个源（不会勾出两条、显示为两个状态）
    fun isActive(url: String) = enabled.any { sourceUrlKey(it) == sourceUrlKey(url) }
    // 「已保存的源」里跟推荐源清单重复的（同一接口换个马甲）不再重复显示 —— 避免一源两行
    val catalogKeys = catalog.mapTo(HashSet()) { sourceUrlKey(it.url) }
    val savedShown = saved.filterNot { sourceUrlKey(it.url) in catalogKeys }

    val hasRows = catalog.isNotEmpty() || savedShown.isNotEmpty()

    // 打开时：列表有内容 → 焦点给第一条（首选源）；
    // 列表空着（首次/拉取失败）→ 焦点给「获取 / 重新获取推荐源」——那正是此刻要做的事；
    // 清单是异步拉回来的：拉完自动把焦点挪过去（用户此时正等着列表出现）。
    LaunchedEffect(visible) { if (!visible) autoFocused = false }
    LaunchedEffect(visible, hasRows, loading) {
        if (!visible || autoFocused) return@LaunchedEffect
        delay(220)
        if (hasRows) {
            if (runCatching { firstRow.requestFocus() }.isSuccess) autoFocused = true
        } else if (!loading) {
            val okBtn = runCatching { retryFocus.requestFocus() }.isSuccess
            if (okBtn || runCatching { closeFocus.requestFocus() }.isSuccess) autoFocused = true
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(Motion.enter()) + scaleIn(initialScale = 0.96f, animationSpec = Motion.enter()),
        exit = fadeOut(Motion.quick()) + scaleOut(targetScale = 0.97f, animationSpec = Motion.quick()),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (visible) BackHandler(onBack = onClose)

        Box(
            Modifier
                .fillMaxSize()
                .background(Scrim(0.86f)),
            contentAlignment = Alignment.Center,
        ) {
            // ★ 固定尺寸卡片：清单长短、加载状态都不改变卡片大小。
            //   高度 500（不是 620！）：设计画布是 1000×562（见 TvMetrics），
            //   卡片必须留出上下边，尺寸超了会被屏幕裁掉一条。
            Column(
                Modifier
                    .width(m.dp(820f))
                    .height(m.dp(500f))
                    .clip(RoundedCornerShape(m.dp(22f)))
                    .background(Panel)
                    .padding(horizontal = m.dp(34f), vertical = m.dp(24f)),
            ) {
                // 头部（固定一行）
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(m.dp(44f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "选择资源",
                        color = TextMain,
                        fontSize = m.sp(26f),
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(m.dp(14f)))
                    Text(
                        text = "点一下启用，再点一下取消，可以多选",
                        color = TextSub,
                        fontSize = m.sp(14f),
                        maxLines = 1,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "已启用 " + enabled.size + " / " + CinemaSettings.MAX_ENABLED_SOURCES,
                        color = if (enabled.isEmpty()) TextSub else Gold,
                        fontSize = m.sp(14f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }

                Spacer(Modifier.height(m.dp(8f)))

                // 列表：剩余高度全部给它，内部滚动，焦点自动滚进视野
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(m.dp(8f)),
                ) {
                    if (catalog.isNotEmpty()) {
                        item(key = "cat-label") { SectionLabel("推荐") }
                        itemsIndexed(catalog, key = { _, s -> "c-" + s.url }) { index, source ->
                            SourceRow(
                                title = source.name,
                                subtitle = source.info.cleanSourceInfo().ifBlank { source.url },
                                active = isActive(source.url),
                                modifier = if (index == 0) Modifier.focusRequester(firstRow) else Modifier,
                                onClick = { onToggle(source.name, source.url) },
                            )
                        }
                    } else {
                        item(key = "cat-state") {
                            when {
                                loading -> CatalogStateRow(
                                    text = "正在获取推荐…",
                                    buttonText = "获取推荐",
                                    buttonEnabled = false,
                                    focusRequester = retryFocus,
                                    onFetch = onRefresh,
                                )

                                error != null -> CatalogStateRow(
                                    text = error,
                                    buttonText = "重新获取",
                                    buttonEnabled = true,
                                    focusRequester = retryFocus,
                                    onFetch = onRefresh,
                                )

                                else -> CatalogStateRow(
                                    text = "还没获取过推荐",
                                    buttonText = "获取推荐",
                                    buttonEnabled = true,
                                    focusRequester = retryFocus,
                                    onFetch = onRefresh,
                                )
                            }
                        }
                    }

                    if (savedShown.isNotEmpty()) {
                        item(key = "saved-label") { SectionLabel("已添加") }
                        itemsIndexed(savedShown, key = { _, s -> "s-" + s.url }) { index, source ->
                            SourceRow(
                                title = source.name,
                                subtitle = source.url,
                                active = isActive(source.url),
                                modifier = if (catalog.isEmpty() && index == 0) {
                                    Modifier.focusRequester(firstRow)
                                } else {
                                    Modifier
                                },
                                onClick = { onToggle(source.name, source.url) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(m.dp(10f)))

                // 底部：两个固定槽位的按钮（关闭 / 刷新列表）
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(m.dp(56f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DarkButton(
                        text = "关闭",
                        width = m.dp(170f),
                        focusRequester = closeFocus,
                        onClick = onClose,
                    )
                    // 固定槽位：勾到上限、拉取失败之类的提示都在这里出现 —— 位置写死，
                    // 有字样没字样都不顶动两侧按钮（AnimatedVisibility 在 Row 里会被
                    // 隐式接收者抢，这里用"永远存在的空 Box"更省心）
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = m.dp(18f)),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = if (catalog.isNotEmpty()) error.orEmpty() else "",
                            color = Gold.copy(alpha = 0.92f),
                            fontSize = m.sp(14f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    DarkButton(
                        text = if (loading) "刷新中…" else "刷新列表",
                        width = m.dp(170f),
                        enabled = !loading,
                        onClick = onRefresh,
                    )
                }
            }
        }
    }
}

/**
 * 清单里的 "3023ms" 只是发布方排序用的数字，界面上不显示（看的人根本不需要毫秒）。
 * 其余段（首选、16.5万部）原样保留，用 · 拼起来。
 */
private fun String.cleanSourceInfo(): String =
    split("·").map { it.trim() }.filterNot { it.endsWith("ms") }.joinToString(" · ")

/** 一条源：名字 + 说明（推荐里显示"首选 · 16.5万部"，已添加的显示地址） */
@Composable
private fun SourceRow(
    title: String,
    subtitle: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val m = rememberTvMetrics()
    val radius = m.dp(12f)
    val interaction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        targetValue = when {
            focused -> PanelHi
            active -> Gold.copy(alpha = 0.13f)
            else -> Color.White.copy(alpha = 0.05f)
        },
        animationSpec = Motion.focus(),
        label = "sourceRowBg",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(m.dp(62f))
            .tvFocus(radius, interactionSource = interaction, onFocusChanged = { focused = it })
            .clip(RoundedCornerShape(radius))
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) { onClick() }
            .padding(horizontal = m.dp(18f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (active) Gold else TextMain,
                fontSize = m.sp(17f),
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(m.dp(3f)))
            Text(
                text = subtitle,
                color = TextSub,
                fontSize = m.sp(13f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(m.dp(12f)))
        // 固定的状态槽位：只换字，不顶动标题
        Box(
            Modifier.width(m.dp(88f)),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (active) {
                Text(
                    text = "已启用",
                    color = Gold,
                    fontSize = m.sp(14f),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** 列表里的小节标题（固定高度） */
@Composable
private fun SectionLabel(text: String) {
    val m = rememberTvMetrics()
    Box(
        Modifier
            .fillMaxWidth()
            .height(m.dp(30f)),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(text = text, color = TextSub, fontSize = m.sp(14f))
    }
}

/** 清单没拉回来时的那一行：一句话 + 一个按钮（固定高度，和普通行一致） */
@Composable
private fun CatalogStateRow(
    text: String,
    buttonText: String,
    buttonEnabled: Boolean,
    focusRequester: FocusRequester? = null,
    onFetch: () -> Unit,
) {
    val m = rememberTvMetrics()
    Row(
        Modifier
            .fillMaxWidth()
            .height(m.dp(62f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!buttonEnabled && text.startsWith("正在")) {
            CircularProgressIndicator(
                modifier = Modifier.size(m.dp(20f)),
                color = Gold,
                strokeWidth = m.dp(2f),
            )
            Spacer(Modifier.width(m.dp(14f)))
        }
        Text(
            text = text,
            color = if (text.startsWith("获取推荐失败") || text.startsWith("连不上")) {
                Gold.copy(alpha = 0.9f)
            } else {
                TextSub
            },
            fontSize = m.sp(15f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(m.dp(14f)))
        DarkButton(
            text = buttonText,
            width = m.dp(150f),
            enabled = buttonEnabled,
            focusRequester = focusRequester,
            onClick = onFetch,
        )
    }
}

/**
 * 扫码配置浮层。
 *
 * 屏幕上是三样东西：**二维码**（手机扫）、**网址**（电脑输）、
 * 以及一行状态（等待连接 → 已收到配置 → 测试结果，手机和电视同步看到）。
 * 关掉它，配置服务就随之停掉 —— 电视不会一直监听端口。
 */
@Composable
private fun ConfigOverlay(
    visible: Boolean,
    url: String?,
    message: String?,
    passed: Boolean,
    onDismiss: () -> Unit,
) {
    val m = rememberTvMetrics()
    val qr = remember(url) {
        url?.let { runCatching { QrCode.bitmap(it) }.getOrNull() }
    }
    val closeFocus = remember { FocusRequester() }

    // 打开时把焦点交给「关闭」按钮（遥控器用户按 OK 就能退出去）
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        delay(180)
        runCatching { closeFocus.requestFocus() }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(Motion.enter()) + scaleIn(initialScale = 0.96f, animationSpec = Motion.enter()),
        exit = fadeOut(Motion.quick()) + scaleOut(targetScale = 0.97f, animationSpec = Motion.quick()),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (visible) BackHandler(onBack = onDismiss)

        Box(
            Modifier
                .fillMaxSize()
                .background(Scrim(0.86f)),
            contentAlignment = Alignment.Center,
        ) {
            // ★ 固定宽度卡片：二维码出没出来、状态几行字，卡片大小都不变
            Column(
                Modifier
                    .width(m.dp(560f))
                    .clip(RoundedCornerShape(m.dp(22f)))
                    .background(Panel)
                    .padding(horizontal = m.dp(36f), vertical = m.dp(22f)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "手机扫码配置",
                    color = TextMain,
                    fontSize = m.sp(24f),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(m.dp(6f)))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(m.dp(42f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "同一个 WiFi，手机扫码打开；电脑也可以输下面的网址。" +
                            "打开后就能添加资源。",
                        color = TextSub,
                        fontSize = m.sp(14f),
                        lineHeight = m.sp(20f),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
                Spacer(Modifier.height(m.dp(10f)))

                // 二维码：白底黑码，所有手机都认；尺寸受"画布 562 高"约束（见上）
                Box(
                    Modifier
                        .size(m.dp(230f))
                        .clip(RoundedCornerShape(m.dp(16f)))
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    if (qr != null) {
                        Image(
                            bitmap = qr,
                            contentDescription = "配置地址二维码",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(m.dp(210f)),
                        )
                    } else {
                        Text(
                            text = if (url == null) "正在启动配置服务…" else "二维码生成失败",
                            color = Ink.copy(alpha = 0.7f),
                            fontSize = m.sp(16f),
                        )
                    }
                }

                Spacer(Modifier.height(m.dp(12f)))
                // 网址：给电脑用（固定一行，永远占着）
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(m.dp(28f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = url ?: "",
                        color = Gold,
                        fontSize = m.sp(19f),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }

                Spacer(Modifier.height(m.dp(4f)))
                // 状态行：固定两行高度（等待 → 已收到 → 测试结果，都不会顶动别的）
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(m.dp(42f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = message.orEmpty(),
                        color = if (passed) Gold else TextSub,
                        fontSize = m.sp(14f),
                        lineHeight = m.sp(20f),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }

                Spacer(Modifier.height(m.dp(10f)))
                DarkButton(
                    text = "关闭",
                    width = m.dp(170f),
                    focusRequester = closeFocus,
                    onClick = onDismiss,
                )
            }
        }
    }
}

/**
 * 一节设置：深色卡片，内部所有行都是固定高度。
 *
 * [appearIndex]：进场时依次浮入的序号。页面打开时五张卡片像发牌一样
 * 一张接一张上来（alpha + 轻微上浮，只走 graphicsLayer，不参与布局，
 * 任何字号下都不会把版式挤乱）。序号越大出现越晚。
 */
@Composable
private fun SectionCard(
    appearIndex: Int = 0,
    content: @Composable () -> Unit,
) {
    val m = rememberTvMetrics()
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(appearIndex * 45L)
        appear.animateTo(1f, Motion.enter())
    }
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * m.dp(22f).toPx()
            }
            .clip(RoundedCornerShape(m.dp(16f)))
            .background(Panel)
            .padding(horizontal = m.dp(26f), vertical = m.dp(22f)),
    ) {
        content()
    }
}

/**
 * 「查看更新」的固定槽位。
 *
 * ⚠️ 和首页的金标一样，单独抽成组件：AnimatedVisibility 有 RowScope / ColumnScope
 * 两个"带接收者"的版本，写在 Row 的 Box 里会被隐式接收者抢走 → 编译不过。
 * 抽到这一层（没有 Row/Column 接收者）就干净了。
 */
@Composable
private fun ViewUpdateSlot(visible: Boolean, onClick: () -> Unit) {
    val m = rememberTvMetrics()
    Box(Modifier.width(m.dp(170f))) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(Motion.base()),
            exit = fadeOut(Motion.quick()),
        ) {
            GoldButton(text = "查看更新", onClick = onClick)
        }
    }
}

/** 状态行：固定一行高度 —— 文案在变，位置不变（这是"排版不抖"的关键） */
@Composable
private fun StatusLine(text: String, highlight: Boolean) {
    val m = rememberTvMetrics()
    Box(
        Modifier
            .fillMaxWidth()
            .height(m.dp(26f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            color = if (highlight) Gold.copy(alpha = 0.95f) else TextSub,
            fontSize = m.sp(15f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 系统信息拼一屏。
 *
 * 为什么要做进 App：国产电视（雷鸟/TCL 这类）的「关于本机」经常只显示型号和序列号，
 * Android 版本压根不给 —— 装第三方"设备信息" App 又要多折腾一次。
 * 而我们的 App 本来就在电视上，顺手看一眼最省事（反馈问题时截图即可）。
 *
 * 全是系统常量，不需要任何权限、不联网。
 */
@Composable
private fun rememberSystemInfo(): String {
    val context = LocalContext.current
    return remember {
        val dm = context.resources.displayMetrics
        val mem = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
            ?.let { am -> ActivityManager.MemoryInfo().also(am::getMemoryInfo) }
        val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
        buildString {
            append("Android 版本：").append(Build.VERSION.RELEASE)
            append("（API ").append(Build.VERSION.SDK_INT).append("）\n")
            append("处理器架构：").append(Build.SUPPORTED_ABIS.joinToString(" / ")).append('\n')
            append("屏幕：").append(dm.widthPixels).append(" × ").append(dm.heightPixels).append('\n')
            if (mem != null) {
                append("运行内存：")
                    .append(String.format("%.1f", mem.totalMem / 1024.0 / 1024 / 1024))
                    .append(" GB\n")
            }
            append("设备：").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append("应用版本：").append(pkg.versionName).append("（").append(pkg.versionCode).append("）")
        }
    }
}

/**
 * 主按钮（金色）。
 *
 * ⚠️ 刻意**没有** animateContentSize：设置页的规矩是"尺寸固定"，
 * 按钮宽度写死，文案再变也不许动（见用户反馈第 2 条）。
 */
@Composable
private fun GoldButton(
    text: String,
    width: Dp? = null,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val m = rememberTvMetrics()
    val radius = m.dp(10f)
    val interaction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        targetValue = if (focused) Gold else Gold.copy(alpha = 0.92f),
        animationSpec = Motion.quick(),
        label = "goldButtonBg",
    )
    Box(
        Modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .tvFocus(
                cornerRadius = radius,
                borderColor = Color.White.copy(alpha = 0.9f),
                interactionSource = interaction,
                onFocusChanged = { focused = it },
            )
            .clip(RoundedCornerShape(radius))
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) { onClick() }
            .padding(horizontal = m.dp(26f), vertical = m.dp(16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Ink, fontSize = m.sp(18f), fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** 次要按钮：深底、不抢视线（设置页里除「选择片源」以外的动作都用它） */
@Composable
internal fun DarkButton(
    text: String,
    enabled: Boolean = true,
    width: Dp? = null,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val m = rememberTvMetrics()
    val radius = m.dp(10f)
    val interaction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        targetValue = if (focused && enabled) PanelHi else PanelHi.copy(alpha = 0.6f),
        animationSpec = Motion.focus(),
        label = "darkButtonBg",
    )
    Box(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .tvFocus(radius, interactionSource = interaction, onFocusChanged = { focused = it })
            .clip(RoundedCornerShape(radius))
            .background(bg)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
            ) { onClick() }
            .padding(horizontal = m.dp(22f), vertical = m.dp(16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = TextMain.copy(alpha = if (enabled) 1f else 0.5f),
            fontSize = m.sp(17f),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
