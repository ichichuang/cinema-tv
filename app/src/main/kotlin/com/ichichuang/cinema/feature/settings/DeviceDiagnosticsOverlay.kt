package com.ichichuang.cinema.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 只读快照与按需分享；系统页面仅从明确的按钮点击打开。 */
@Composable
internal fun DeviceDiagnosticsOverlay(
    visible: Boolean,
    loading: Boolean,
    snapshot: DeviceDiagnosticsSnapshot?,
    message: String?,
    reportUrl: String?,
    reportLoading: Boolean,
    onRefresh: () -> Unit,
    onOpenSettings: (DiagnosticLaunch) -> Unit,
    onShareReport: () -> Unit,
    onStopReport: () -> Unit,
    onClose: () -> Unit,
) {
    val m = rememberTvMetrics()
    val scope = rememberCoroutineScope()
    val lifecycle = (LocalContext.current as? LifecycleOwner)?.lifecycle
    val sections = remember {
        listOf(
            "device" to "概览",
            "settings" to "系统入口",
            "declared" to "设置组件",
            "components" to "相关组件",
            "report" to "报告",
        )
    }
    val tabFocus = remember { List(sections.size) { FocusRequester() } }
    val shareFocus = remember { FocusRequester() }
    val refreshFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    val reportTopFocus = remember { FocusRequester() }
    val reportStopFocus = remember { FocusRequester() }
    // 动作行按需请求焦点（复用同一个 key 的 requester），从系统页返回时要找回它。
    val actionFocus = remember { HashMap<String, FocusRequester>() }
    val listState = rememberLazyListState()
    var sectionId by remember { mutableStateOf("device") }
    var pendingReturnKey by remember { mutableStateOf<String?>(null) }
    // 组件详情默认收起，避免几十上百条声明组件一次铺满电视屏幕。
    val expandedDetails = remember { mutableStateMapOf<String, Boolean>() }

    val section = snapshot?.sections?.firstOrNull { it.id == sectionId }
    val rows = buildDiagnosticRows(sectionId, section, loading, message, expandedDetails)
    val firstFocusableIndex = rows.indexOfFirst { it.focusable }
    val lastFocusableIndex = rows.indexOfLast { it.focusable }
    val selectedTabIndex = sections.indexOfFirst { it.first == sectionId }
    val selectedTabFocus = tabFocus.getOrElse(selectedTabIndex) { tabFocus.first() }

    val refreshEnabled = !loading && !reportLoading
    val downloadEnabled = snapshot != null && !loading && !reportLoading
    val stopEnabled = reportUrl != null && !reportLoading
    // 刷新/下载在加载时会临时禁用；向下离开内容区时必须落在仍然可聚焦的控件上。
    val firstFooterFocus = when {
        refreshEnabled -> refreshFocus
        downloadEnabled -> shareFocus
        else -> closeFocus
    }
    // 从页脚向上回到永远可见的标签行（或报告页仍可用的「关闭分享」）。
    val footerReturnFocus = when {
        sectionId == "report" && stopEnabled -> reportStopFocus
        else -> selectedTabFocus
    }

    LaunchedEffect(visible) {
        if (visible) {
            pendingReturnKey = null
            sectionId = "device"
            listState.scrollToItem(0)
            delay(Motion.DUR_ENTER.toLong())
            runCatching { tabFocus.first().requestFocus() }
        }
    }
    LaunchedEffect(sectionId) { listState.scrollToItem(0) }

    // 从系统设置页返回：把焦点还给刚刚触发的动作行；动作已消失则回到当前标签。
    val currentRows by rememberUpdatedState(rows)
    val currentSectionId by rememberUpdatedState(sectionId)
    val currentVisible by rememberUpdatedState(visible)
    DisposableEffect(lifecycle) {
        val owner = lifecycle
        if (owner == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
                val key = pendingReturnKey ?: return@LifecycleEventObserver
                pendingReturnKey = null
                if (!currentVisible) return@LifecycleEventObserver
                scope.launch {
                    // 等一帧，让系统页退出后的重组先完成。
                    delay(80)
                    val index = currentRows.indexOfFirst { it.key == key }
                    if (index >= 0) {
                        // LazyColumn 的屏幕外项目尚未组合，先滚到它再请求焦点。
                        runCatching { listState.scrollToItem(index) }
                        delay(80)
                    }
                    val requester = actionFocus[key]
                    // requestFocus() 在目标尚未挂载时会抛异常；成功返回即已交给该行。
                    val restored = index >= 0 && requester != null && runCatching {
                        requester.requestFocus()
                    }.isSuccess
                    if (!restored) {
                        val tabIndex = sections.indexOfFirst { it.first == currentSectionId }
                        runCatching { tabFocus.getOrElse(tabIndex) { tabFocus.first() }.requestFocus() }
                    }
                }
            }
            owner.addObserver(observer)
            onDispose { owner.removeObserver(observer) }
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
                .background(Scrim(0.86f))
                .focusProperties { canFocus = visible },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .width(m.dp(900f))
                    .height(m.dp(500f))
                    .clip(RoundedCornerShape(m.dp(22f)))
                    .background(Panel)
                    .padding(horizontal = m.dp(28f), vertical = m.dp(22f)),
            ) {
                Row(
                    Modifier.fillMaxWidth().height(m.dp(36f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("设备诊断", color = TextMain, fontSize = m.sp(26f), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(m.dp(18f)),
                            color = Gold,
                            strokeWidth = m.dp(2f),
                        )
                        Spacer(Modifier.width(m.dp(8f)))
                    }
                    Text(
                        text = if (loading) "正在读取…" else snapshot?.let { "采集于 ${it.collectedAt}" }.orEmpty(),
                        color = TextSub,
                        fontSize = m.sp(13f),
                        maxLines = 1,
                    )
                }
                Text(
                    text = "只读快照 · 上下键浏览全文；从系统页面返回后可手动刷新。",
                    color = TextSub,
                    fontSize = m.sp(14f),
                    modifier = Modifier.height(m.dp(24f)),
                    maxLines = 1,
                )
                Row(
                    Modifier.fillMaxWidth().height(m.dp(56f)),
                    horizontalArrangement = Arrangement.spacedBy(m.dp(12f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    sections.forEachIndexed { index, (id, title) ->
                        FilterChip(
                            label = title,
                            selected = sectionId == id,
                            onClick = { sectionId = id },
                            modifier = Modifier.width(m.dp(140f))
                                .focusRequester(tabFocus[index]),
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    if (sectionId == "report") {
                        DiagnosticReport(
                            url = reportUrl,
                            loading = reportLoading,
                            message = message,
                            onStop = {
                                runCatching { shareFocus.requestFocus() }
                                onStopReport()
                            },
                            topFocus = reportTopFocus,
                            stopFocus = reportStopFocus,
                            selectedTabFocus = selectedTabFocus,
                            footerFocus = firstFooterFocus,
                            stopEnabled = stopEnabled,
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = m.dp(12f), vertical = m.dp(10f)),
                            verticalArrangement = Arrangement.spacedBy(m.dp(10f)),
                        ) {
                            itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                                val isFirst = row.focusable && index == firstFocusableIndex
                                val isLast = row.focusable && index == lastFocusableIndex
                                val focusUp = if (isFirst) selectedTabFocus else null
                                val focusDown = if (isLast) firstFooterFocus else null
                                when (row) {
                                    is DiagnosticRow.Value -> DiagnosticValueRow(
                                        label = row.label,
                                        value = row.value,
                                        focusUp = focusUp,
                                        focusDown = focusDown,
                                    )

                                    is DiagnosticRow.Title -> Text(
                                        text = row.text,
                                        color = Gold,
                                        fontSize = m.sp(16f),
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.fillMaxWidth().padding(top = m.dp(4f)),
                                    )

                                    is DiagnosticRow.Action -> DiagnosticActionRow(
                                        target = row.target,
                                        focusRequester = remember(row.key) {
                                            actionFocus.getOrPut(row.key) { FocusRequester() }
                                        },
                                        focusUp = focusUp,
                                        focusDown = focusDown,
                                        onActivate = {
                                            pendingReturnKey = row.key
                                            onOpenSettings(row.target)
                                        },
                                    )

                                    is DiagnosticRow.Toggle -> DiagnosticToggleRow(
                                        title = row.title,
                                        hint = row.hint,
                                        expanded = row.expanded,
                                        onToggle = { expandedDetails[row.detailKey] = !row.expanded },
                                        focusUp = focusUp,
                                        focusDown = focusDown,
                                    )

                                    is DiagnosticRow.DetailTitle -> Text(
                                        text = row.text,
                                        color = TextMain,
                                        fontSize = m.sp(15f),
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.fillMaxWidth().padding(top = m.dp(6f)),
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(m.dp(10f)))
                Row(
                    Modifier.fillMaxWidth().height(m.dp(56f)),
                    horizontalArrangement = Arrangement.spacedBy(m.dp(16f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DarkButton(
                        text = if (loading) "刷新中…" else "刷新",
                        width = m.dp(160f),
                        enabled = refreshEnabled,
                        focusRequester = refreshFocus,
                        modifier = Modifier.focusProperties { up = footerReturnFocus },
                        onClick = {
                            runCatching { selectedTabFocus.requestFocus() }
                            onRefresh()
                            scope.launch { runCatching { listState.scrollToItem(0) } }
                        },
                    )
                    DarkButton(
                        text = if (reportLoading) "正在生成…" else "下载报告",
                        width = m.dp(180f),
                        enabled = downloadEnabled,
                        focusRequester = shareFocus,
                        modifier = Modifier.focusProperties { up = footerReturnFocus },
                        onClick = {
                            sectionId = "report"
                            runCatching { tabFocus.last().requestFocus() }
                            onShareReport()
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    DarkButton(
                        text = "关闭",
                        width = m.dp(160f),
                        focusRequester = closeFocus,
                        modifier = Modifier.focusProperties { up = footerReturnFocus },
                        onClick = onClose,
                    )
                }
            }
        }
    }
}

/** 内容区的可组合行模型：key 同时用于 LazyColumn 与“从系统页返回”的焦点定位。 */
private sealed interface DiagnosticRow {
    val key: String
    val focusable: Boolean

    data class Value(override val key: String, val label: String, val value: String) : DiagnosticRow {
        override val focusable: Boolean = true
    }

    data class Title(override val key: String, val text: String) : DiagnosticRow {
        override val focusable: Boolean = false
    }

    data class Action(override val key: String, val target: DiagnosticLaunch) : DiagnosticRow {
        override val focusable: Boolean = true
    }

    data class Toggle(
        override val key: String,
        val title: String,
        val hint: String,
        val expanded: Boolean,
        val detailKey: String,
    ) : DiagnosticRow {
        override val focusable: Boolean = true
    }

    data class DetailTitle(override val key: String, val text: String) : DiagnosticRow {
        override val focusable: Boolean = false
    }
}

private fun buildDiagnosticRows(
    sectionId: String,
    section: DiagnosticSection?,
    loading: Boolean,
    message: String?,
    expandedDetails: Map<String, Boolean>,
): List<DiagnosticRow> = buildList {
    if (message != null) {
        add(DiagnosticRow.Value("message", "最近操作", message))
    }
    add(DiagnosticRow.Value("boundary", "如何理解结果", boundaryText(sectionId)))
    if (section == null) {
        add(
            DiagnosticRow.Value(
                "empty",
                "当前状态",
                if (loading) "正在读取设备信息…" else "暂无结果，请选择下方「刷新」。",
            ),
        )
    }
    section?.entries?.forEachIndexed { entryIndex, entry ->
        add(DiagnosticRow.Title("title-$sectionId-$entryIndex", entry.title))
        // 打开动作单独占一整行，排在说明字段之前：遥控器上下键能直接落到它。
        // 加载中不生成动作行，避免把焦点留在即将禁用的控件上。
        if (entry.launch != null && !loading) {
            add(DiagnosticRow.Action("action-$sectionId-$entryIndex", entry.launch))
        }
        entry.fields.forEachIndexed { fieldIndex, field ->
            add(DiagnosticRow.Value("field-$sectionId-$entryIndex-$fieldIndex", field.label, field.value))
        }
        if (entry.details.isNotEmpty()) {
            val detailKey = "$sectionId-$entryIndex"
            val expanded = expandedDetails[detailKey] == true
            add(
                DiagnosticRow.Toggle(
                    key = "details-toggle-$detailKey",
                    title = "已声明 ${entry.details.size} 个组件",
                    hint = if (expanded) {
                        "按 OK 收起；以下为只读组件信息"
                    } else {
                        "按 OK 展开；以下为只读组件信息"
                    },
                    expanded = expanded,
                    detailKey = detailKey,
                ),
            )
            if (expanded) {
                entry.details.forEachIndexed { detailIndex, detail ->
                    add(DiagnosticRow.DetailTitle("detail-title-$detailKey-$detailIndex", detail.title))
                    detail.fields.forEachIndexed { fieldIndex, field ->
                        add(
                            DiagnosticRow.Value(
                                "detail-field-$detailKey-$detailIndex-$fieldIndex",
                                field.label,
                                field.value,
                            ),
                        )
                    }
                }
            }
        }
    }
}

private fun boundaryText(sectionId: String): String = when (sectionId) {
    "settings" -> "发现或成功打开入口，不证明页面内容可用。普通 HOME 解析不验证实体遥控器 HOME 键。"
    "declared" -> "按包名读取的声明组件（只读），与标准 Action 发现分开。未返回不等于未安装；声明存在不证明页面可用。只有标准入口和已核对候选提供打开按钮。"
    "components" -> "结果仅限本应用可见的相关组件；没有返回结果不等于未安装。权限与启用状态按当前快照显示。"
    else -> "这里读取设备公开信息，不会更改系统。报告不包含账号、私密标识或资源地址。"
}

/** 每一条只读信息都能聚焦；遥控器可带动列表滚动，长值完整换行。 */
@Composable
private fun DiagnosticValueRow(
    label: String,
    value: String,
    focusRequester: FocusRequester? = null,
    focusUp: FocusRequester? = null,
    focusDown: FocusRequester? = null,
) {
    val m = rememberTvMetrics()
    var focused by remember { mutableStateOf(false) }
    val radius = m.dp(10f)
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .tvFocus(radius, scale = 1.02f, onFocusChanged = { focused = it })
            .clip(RoundedCornerShape(radius))
            .background(if (focused) PanelHi else Ink.copy(alpha = 0.45f))
            .focusProperties {
                focusUp?.let { up = it }
                focusDown?.let { down = it }
            }
            .focusable()
            .padding(horizontal = m.dp(16f), vertical = m.dp(12f)),
    ) {
        Text(label, color = TextSub, fontSize = m.sp(13f))
        Spacer(Modifier.height(m.dp(4f)))
        Text(value.ifBlank { "未返回" }, color = TextMain, fontSize = m.sp(15f), lineHeight = m.sp(21f))
    }
}

/**
 * 系统设置打开动作：整行、金色强调、只有一个 clickable 焦点目标。
 * 一次确认键只走一次 onActivate；长按重复不会触发额外点击。
 */
@Composable
private fun DiagnosticActionRow(
    target: DiagnosticLaunch,
    focusRequester: FocusRequester?,
    focusUp: FocusRequester?,
    focusDown: FocusRequester?,
    onActivate: () -> Unit,
) {
    val m = rememberTvMetrics()
    val interaction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val radius = m.dp(12f)
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .tvFocus(radius, scale = 1.02f, interactionSource = interaction, onFocusChanged = { focused = it })
            .clip(RoundedCornerShape(radius))
            .background(if (focused) PanelHi else Gold.copy(alpha = 0.14f))
            .focusProperties {
                focusUp?.let { up = it }
                focusDown?.let { down = it }
            }
            .clickable(interactionSource = interaction, indication = null) { onActivate() }
            .padding(horizontal = m.dp(18f), vertical = m.dp(14f)),
    ) {
        Text(
            text = "打开${target.label}",
            color = Gold,
            fontSize = m.sp(17f),
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(m.dp(4f)))
        Text(
            text = "按 OK 提交打开请求；页面是否出现仍需你在电视上确认。",
            color = TextSub,
            fontSize = m.sp(13f),
        )
    }
}

/** 声明组件列表的展开/收起；与只读信息行一样可聚焦，不引入新的导航层级。 */
@Composable
private fun DiagnosticToggleRow(
    title: String,
    hint: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    focusRequester: FocusRequester? = null,
    focusUp: FocusRequester? = null,
    focusDown: FocusRequester? = null,
) {
    val m = rememberTvMetrics()
    val interaction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val radius = m.dp(10f)
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .tvFocus(radius, scale = 1.02f, interactionSource = interaction, onFocusChanged = { focused = it })
            .clip(RoundedCornerShape(radius))
            .background(if (focused) PanelHi else Ink.copy(alpha = 0.45f))
            .focusProperties {
                focusUp?.let { up = it }
                focusDown?.let { down = it }
            }
            .clickable(interactionSource = interaction, indication = null) { onToggle() }
            .padding(horizontal = m.dp(16f), vertical = m.dp(12f)),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = TextMain, fontSize = m.sp(15f), fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(m.dp(12f)))
            Text(if (expanded) "收起" else "展开", color = Gold, fontSize = m.sp(14f), fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(m.dp(4f)))
        Text(hint, color = TextSub, fontSize = m.sp(13f))
    }
}

@Composable
private fun DiagnosticReport(
    url: String?,
    loading: Boolean,
    message: String?,
    onStop: () -> Unit,
    topFocus: FocusRequester,
    stopFocus: FocusRequester,
    selectedTabFocus: FocusRequester,
    footerFocus: FocusRequester,
    stopEnabled: Boolean,
) {
    val m = rememberTvMetrics()
    val qr = remember(url) { url?.let { runCatching { QrCode.bitmap(it) }.getOrNull() } }
    // 关闭分享不可用时，最后一条只读行要把下键交给页脚，避免停在报告里出不去。
    val lastReadOnlyDown = if (stopEnabled) null else footerFocus
    Row(
        Modifier.fillMaxSize().padding(m.dp(12f)),
        horizontalArrangement = Arrangement.spacedBy(m.dp(26f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(m.dp(194f))
                .clip(RoundedCornerShape(m.dp(14f)))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            if (qr != null) {
                Image(qr, "只读诊断报告下载地址", Modifier.size(m.dp(178f)), contentScale = ContentScale.Fit)
            } else {
                Text(
                    text = when {
                        loading -> "正在生成报告…"
                        url != null -> "请使用右侧网址"
                        else -> "点击下方「下载报告」"
                    },
                    color = Ink,
                    fontSize = m.sp(13f),
                    modifier = Modifier.padding(m.dp(12f)),
                )
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxSize(),
            contentPadding = PaddingValues(m.dp(10f)),
            verticalArrangement = Arrangement.spacedBy(m.dp(10f)),
        ) {
            item(key = "report-url") {
                DiagnosticValueRow(
                    label = "同一 Wi-Fi · 手机扫码 / Mac 浏览器输入",
                    value = url ?: if (loading) "正在创建只读下载地址…" else "点击下方「下载报告」后显示地址。",
                    focusRequester = topFocus,
                    focusUp = selectedTabFocus,
                )
            }
            item(key = "report-validity") {
                DiagnosticValueRow(
                    label = "分享有效期",
                    value = "链接 5 分钟后失效；关闭分享或诊断也会立即失效。仅可下载这次诊断报告。",
                    // 只有它确实是最后一条只读行时才显式下跳，避免跳过状态行。
                    focusDown = if (message == null) lastReadOnlyDown else null,
                )
            }
            if (message != null) {
                item(key = "report-message") {
                    DiagnosticValueRow(
                        label = "当前状态",
                        value = message,
                        focusDown = lastReadOnlyDown,
                    )
                }
            }
            item(key = "report-stop") {
                DarkButton(
                    text = "关闭分享",
                    width = m.dp(170f),
                    enabled = stopEnabled,
                    focusRequester = stopFocus,
                    modifier = Modifier.focusProperties { if (stopEnabled) down = footerFocus },
                    onClick = onStop,
                )
            }
        }
    }
}
