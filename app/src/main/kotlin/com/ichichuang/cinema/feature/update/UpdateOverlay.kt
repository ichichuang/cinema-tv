package com.ichichuang.cinema.feature.update

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.ichichuang.cinema.core.design.Gold
import com.ichichuang.cinema.core.design.Ink
import com.ichichuang.cinema.core.design.Motion
import com.ichichuang.cinema.core.design.Panel
import com.ichichuang.cinema.core.design.PanelHi
import com.ichichuang.cinema.core.design.Scrim
import com.ichichuang.cinema.core.design.TextBody
import com.ichichuang.cinema.core.design.TextMain
import com.ichichuang.cinema.core.design.TextSub
import com.ichichuang.cinema.core.design.rememberTvMetrics
import com.ichichuang.cinema.core.focus.tvFocus
import com.ichichuang.cinema.core.system.UpdateInstaller
import kotlinx.coroutines.delay

/**
 * 在线更新浮层。
 *
 * 设计原则：**不打扰，但诚实**。
 * - 有新版本不弹窗，只在首页右上角亮一个小金标；用户好奇才点开这里
 * - 每一步只给一个明确的动作，遥控器不用做选择
 * - 下载完自动调起系统安装界面；安卓强制的"确认安装"那一步如实告诉用户（不假装能全自动）
 * - 背景遮罩点不了（电视上误触一次＝重新下载几十兆）
 */
@Composable
fun UpdateOverlay(
    state: UpdateUiState,
    currentVersionName: String,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    onInstallLaunched: () -> Unit,
    onInstallFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state.panelOpen,
        enter = fadeIn(Motion.enter()) + scaleIn(initialScale = 0.94f, animationSpec = Motion.enter()),
        exit = fadeOut(Motion.quick()),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Scrim(0.78f)),
            contentAlignment = Alignment.Center,
        ) {
            UpdateCard(
                state = state,
                currentVersionName = currentVersionName,
                onDownload = onDownload,
                onDismiss = onDismiss,
                onInstallLaunched = onInstallLaunched,
                onInstallFailed = onInstallFailed,
            )
        }
    }

    if (state.panelOpen) BackHandler(onBack = onDismiss)
}

@Composable
private fun UpdateCard(
    state: UpdateUiState,
    currentVersionName: String,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    onInstallLaunched: () -> Unit,
    onInstallFailed: () -> Unit,
) {
    val m = rememberTvMetrics()
    val context = LocalContext.current
    val info = state.info
    // 系统层面是否允许本应用安装未知来源的应用（决定"还差一步"提示要不要出现）
    val canInstall = UpdateInstaller.canInstall(context)

    val primaryFocus = remember { FocusRequester() }
    var autoInstalledFor by remember { mutableStateOf<String?>(null) }

    // 浮层一开就把焦点给主按钮 —— 否则遥控器要先"摸黑"找方向键
    LaunchedEffect(state.panelOpen, state.phase) {
        if (state.panelOpen) {
            delay(160) // 等 AnimatedVisibility 把内容真正挂上
            runCatching { primaryFocus.requestFocus() }
        }
    }

    // 下载完成 → 自动调起系统安装界面（每个文件只自动一次，剩下交给按钮）
    LaunchedEffect(state.phase, state.apkPath) {
        val path = state.apkPath
        if (state.phase == UpdatePhase.READY && path != null && autoInstalledFor != path) {
            autoInstalledFor = path
            if (canInstall) {
                if (UpdateInstaller.install(context, path)) onInstallLaunched() else onInstallFailed()
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth(0.72f)
            .clip(RoundedCornerShape(m.dp(22f)))
            .background(Panel)
            .padding(horizontal = m.dp(42f), vertical = m.dp(36f)),
    ) {
        when (state.phase) {
            UpdatePhase.DOWNLOADING -> {
                Headline(
                    title = "正在下载新版本",
                    sub = info?.sizeText?.takeIf { it.isNotBlank() }?.let { "别急着关电视 · $it" } ?: "别急着关电视",
                )
                Spacer(Modifier.height(m.dp(22f)))
                ProgressLine(state.progress)
                Spacer(Modifier.height(m.dp(16f)))
                Text(
                    text = "${(state.progress * 100).toInt()}%",
                    color = TextSub,
                    fontSize = m.sp(16f),
                )
            }

            UpdatePhase.READY -> {
                Headline("下载完成", "系统马上会弹出安装界面，按遥控器选「安装」就行")
                Spacer(Modifier.height(m.dp(10f)))
                Text(
                    text = state.message.orEmpty(),
                    color = Gold.copy(alpha = 0.9f),
                    fontSize = m.sp(15f),
                    maxLines = 2,
                )
                Spacer(Modifier.height(m.dp(24f)))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 权限还没给：先引导去授权，这一步是安卓的硬要求（每个应用单独授权）
                    if (!canInstall) {
                        PanelAction("去允许安装", primary = true, focus = primaryFocus) {
                            UpdateInstaller.openInstallPermissionSettings(context)
                        }
                        Spacer(Modifier.width(m.dp(14f)))
                    }
                    PanelAction(
                        text = "安装",
                        primary = canInstall,
                        focus = if (canInstall) primaryFocus else null,
                    ) {
                        val path = state.apkPath
                        if (path != null && UpdateInstaller.install(context, path)) onInstallLaunched() else onInstallFailed()
                    }
                    Spacer(Modifier.width(m.dp(14f)))
                    PanelAction("稍后", primary = false) { onDismiss() }
                }
            }

            UpdatePhase.UP_TO_DATE -> {
                Headline("已是最新版本", "当前 v$currentVersionName，有新版本会在这里告诉你")
                Spacer(Modifier.height(m.dp(24f)))
                PanelAction("知道了", primary = true, focus = primaryFocus) { onDismiss() }
            }

            UpdatePhase.ERROR -> {
                Headline("更新没成功", state.message ?: "说不清哪里出了问题，再试一次")
                Spacer(Modifier.height(m.dp(24f)))
                Row {
                    if (info != null) {
                        PanelAction("重试下载", primary = true, focus = primaryFocus) { onDownload() }
                        Spacer(Modifier.width(m.dp(14f)))
                    }
                    PanelAction("关闭", primary = info == null, focus = if (info == null) primaryFocus else null) { onDismiss() }
                }
            }

            else -> {
                // AVAILABLE / CHECKING / IDLE：一起处理，反正都只差"要不要更新"这一个决定
                val available = info != null
                Headline(
                    title = if (available) "发现新版本 v${info?.versionName}" else "检查更新",
                    sub = if (available) {
                        buildString {
                            append("当前 v$currentVersionName")
                            info?.sizeText?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                        }
                    } else {
                        "正在问服务器有没有新版本…"
                    },
                )
                info?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    Spacer(Modifier.height(m.dp(20f)))
                    Text(
                        text = notes,
                        color = TextBody,
                        fontSize = m.sp(16f),
                        maxLines = 5,
                    )
                }
                Spacer(Modifier.height(m.dp(26f)))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PanelAction("立即更新", primary = true, focus = primaryFocus) { onDownload() }
                    Spacer(Modifier.width(m.dp(14f)))
                    PanelAction("以后再说", primary = false) { onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun Headline(title: String, sub: String) {
    val m = rememberTvMetrics()
    Text(title, color = TextMain, fontSize = m.sp(26f), fontWeight = FontWeight.Bold)
    if (sub.isNotBlank()) {
        Spacer(Modifier.height(m.dp(8f)))
        Text(sub, color = TextSub, fontSize = m.sp(15f), maxLines = 2)
    }
}

@Composable
private fun ProgressLine(progress: Float) {
    val m = rememberTvMetrics()
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = Motion.base(),
        label = "updateProgress",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(m.dp(8f))
            .clip(RoundedCornerShape(50))
            .background(PanelHi),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(Gold),
        )
    }
}

@Composable
private fun PanelAction(
    text: String,
    primary: Boolean,
    focus: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val m = rememberTvMetrics()
    val radius = m.dp(12f)
    Box(
        Modifier
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .tvFocus(radius)
            .clip(RoundedCornerShape(radius))
            .background(if (primary) Gold else PanelHi)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(horizontal = m.dp(28f), vertical = m.dp(15f)),
    ) {
        Text(
            text = text,
            color = if (primary) Ink else TextMain,
            fontSize = m.sp(18f),
            fontWeight = FontWeight.Bold,
        )
    }
}
