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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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
    val tabFocus = remember { List(5) { FocusRequester() } }
    val shareFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var sectionId by remember { mutableStateOf("device") }
    val sections = listOf(
        "device" to "概览",
        "settings" to "系统入口",
        "declared" to "设置组件",
        "components" to "相关组件",
        "report" to "报告",
    )
    // 组件详情默认收起，避免几十上百条声明组件一次铺满电视屏幕。
    val expandedDetails = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(visible) {
        if (visible) {
            sectionId = "device"
            listState.scrollToItem(0)
            delay(Motion.DUR_ENTER.toLong())
            runCatching { tabFocus.first().requestFocus() }
        }
    }
    LaunchedEffect(sectionId) { listState.scrollToItem(0) }

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
                        )
                    } else {
                        val section = snapshot?.sections?.firstOrNull { it.id == sectionId }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = m.dp(12f), vertical = m.dp(10f)),
                            verticalArrangement = Arrangement.spacedBy(m.dp(10f)),
                        ) {
                            if (message != null) {
                                item(key = "message") { DiagnosticValueRow("最近操作", message) }
                            }
                            item(key = "boundary") {
                                DiagnosticValueRow(
                                    "如何理解结果",
                                    when (sectionId) {
                                        "settings" -> "发现或成功打开入口，不证明页面内容可用。普通 HOME 解析不验证实体遥控器 HOME 键。"
                                        "declared" -> "按包名读取的声明组件（只读），与标准 Action 发现分开。未返回不等于未安装；声明存在不证明页面可用。只有标准入口和已核对候选提供打开按钮。"
                                        "components" -> "结果仅限本应用可见的相关组件；没有返回结果不等于未安装。权限与启用状态按当前快照显示。"
                                        else -> "这里读取设备公开信息，不会更改系统。报告不包含账号、私密标识或资源地址。"
                                    },
                                )
                            }
                            if (section == null) {
                                item(key = "empty") {
                                    DiagnosticValueRow("当前状态", if (loading) "正在读取设备信息…" else "暂无结果，请选择下方「刷新」。")
                                }
                            }
                            section?.entries?.forEachIndexed { entryIndex, entry ->
                                item(key = "title-$sectionId-$entryIndex") {
                                    Row(
                                        Modifier.fillMaxWidth().padding(top = m.dp(4f)),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = entry.title,
                                            color = Gold,
                                            fontSize = m.sp(16f),
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f),
                                        )
                                        entry.launch?.let { target ->
                                            Spacer(Modifier.width(m.dp(16f)))
                                            DarkButton(
                                                text = "打开${target.label}",
                                                enabled = !loading,
                                                onClick = { onOpenSettings(target) },
                                            )
                                        }
                                    }
                                }
                                entry.fields.forEachIndexed { fieldIndex, field ->
                                    item(key = "field-$sectionId-$entryIndex-$fieldIndex") {
                                        DiagnosticValueRow(field.label, field.value)
                                    }
                                }
                                if (entry.details.isNotEmpty()) {
                                    val detailKey = "$sectionId-$entryIndex"
                                    val expanded = expandedDetails[detailKey] == true
                                    item(key = "details-toggle-$detailKey") {
                                        DiagnosticToggleRow(
                                            title = "已声明 ${entry.details.size} 个组件",
                                            hint = if (expanded) {
                                                "按 OK 收起；以下为只读组件信息"
                                            } else {
                                                "按 OK 展开；以下为只读组件信息"
                                            },
                                            expanded = expanded,
                                            onToggle = { expandedDetails[detailKey] = !expanded },
                                        )
                                    }
                                    if (expanded) {
                                        entry.details.forEachIndexed { detailIndex, detail ->
                                            item(key = "detail-$detailKey-$detailIndex") {
                                                Text(
                                                    text = detail.title,
                                                    color = TextMain,
                                                    fontSize = m.sp(15f),
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.fillMaxWidth().padding(top = m.dp(6f)),
                                                )
                                            }
                                            detail.fields.forEachIndexed { fieldIndex, field ->
                                                item(key = "detail-field-$detailKey-$detailIndex-$fieldIndex") {
                                                    DiagnosticValueRow(field.label, field.value)
                                                }
                                            }
                                        }
                                    }
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
                        enabled = !loading && !reportLoading,
                        onClick = {
                            runCatching { tabFocus[sections.indexOfFirst { it.first == sectionId }].requestFocus() }
                            onRefresh()
                        },
                    )
                    DarkButton(
                        text = if (reportLoading) "正在生成…" else "下载报告",
                        width = m.dp(180f),
                        enabled = snapshot != null && !loading && !reportLoading,
                        focusRequester = shareFocus,
                        onClick = {
                            sectionId = "report"
                            runCatching { tabFocus.last().requestFocus() }
                            onShareReport()
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    DarkButton(text = "关闭", width = m.dp(160f), onClick = onClose)
                }
            }
        }
    }
}

/** 每一条只读信息都能聚焦；遥控器可带动列表滚动，长值完整换行。 */
@Composable
private fun DiagnosticValueRow(label: String, value: String) {
    val m = rememberTvMetrics()
    var focused by remember { mutableStateOf(false) }
    val radius = m.dp(10f)
    Column(
        Modifier
            .fillMaxWidth()
            .tvFocus(radius, scale = 1.02f, onFocusChanged = { focused = it })
            .clip(RoundedCornerShape(radius))
            .background(if (focused) PanelHi else Ink.copy(alpha = 0.45f))
            .focusable()
            .padding(horizontal = m.dp(16f), vertical = m.dp(12f)),
    ) {
        Text(label, color = TextSub, fontSize = m.sp(13f))
        Spacer(Modifier.height(m.dp(4f)))
        Text(value.ifBlank { "未返回" }, color = TextMain, fontSize = m.sp(15f), lineHeight = m.sp(21f))
    }
}

/** 声明组件列表的展开/收起；与只读信息行一样可聚焦，不引入新的导航层级。 */
@Composable
private fun DiagnosticToggleRow(
    title: String,
    hint: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val m = rememberTvMetrics()
    val interaction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val radius = m.dp(10f)
    Column(
        Modifier
            .fillMaxWidth()
            .tvFocus(radius, scale = 1.02f, interactionSource = interaction, onFocusChanged = { focused = it })
            .clip(RoundedCornerShape(radius))
            .background(if (focused) PanelHi else Ink.copy(alpha = 0.45f))
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
private fun DiagnosticReport(url: String?, loading: Boolean, message: String?, onStop: () -> Unit) {
    val m = rememberTvMetrics()
    val qr = remember(url) { url?.let { runCatching { QrCode.bitmap(it) }.getOrNull() } }
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
            item {
                DiagnosticValueRow(
                    "同一 Wi-Fi · 手机扫码 / Mac 浏览器输入",
                    url ?: if (loading) "正在创建只读下载地址…" else "点击下方「下载报告」后显示地址。",
                )
            }
            item {
                DiagnosticValueRow("分享有效期", "链接 5 分钟后失效；关闭分享或诊断也会立即失效。仅可下载这次诊断报告。")
            }
            if (message != null) item { DiagnosticValueRow("当前状态", message) }
            item {
                DarkButton(text = "关闭分享", width = m.dp(170f), enabled = url != null && !loading, onClick = onStop)
            }
        }
    }
}
