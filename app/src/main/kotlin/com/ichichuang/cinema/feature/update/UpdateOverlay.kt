package com.ichichuang.cinema.feature.update

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay

/**
 * 在线更新浮层。
 *
 * 设计原则：**不打扰，但诚实**。
 * - 有新版本不弹窗，只在首页右上角亮一个小金标；用户好奇才点开这里
 * - 每一步只给一个明确的动作，遥控器不用做选择
 * - 下载完自动调起系统安装界面；安卓强制的"确认安装"那一步如实告诉用户（不假装能全自动）
 * - 背景遮罩点不了（电视上误触一次＝重新下载几十兆）
 *
 * 排版规矩（v0.7.0 定的）：**卡片是一个固定尺寸的容器**。
 * 标题区 / 内容区 / 按钮区三段各自高度写死，检查中 → 有新版本 → 下载中 → 安装
 * 这几次界面切换，卡片既不"长大"也不"缩小"，屏幕不会跟着闪。
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
        exit = fadeOut(Motion.quick()) + scaleOut(targetScale = 0.97f, animationSpec = Motion.quick()),
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
    // 系统层面是否允许本应用安装未知来源的应用（决定"还差一步"提示要不要出现）。
    // ⚠️ 必须跟着生命周期刷新：用户去系统设置里开完权限回来，如果这里还停在旧值，
    //    卡片会继续显示「去允许安装」——再点一次又跳系统页，看起来就像坏了。
    var canInstall by remember { mutableStateOf(UpdateInstaller.canInstall(context)) }
    val lifecycle = (LocalContext.current as? LifecycleOwner)?.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canInstall = UpdateInstaller.canInstall(context)
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    val primaryFocus = remember { FocusRequester() }
    var autoInstalledFor by remember { mutableStateOf<String?>(null) }

    // 浮层一开就把焦点给主按钮 —— 否则遥控器要先"摸黑"找方向键
    LaunchedEffect(state.panelOpen, state.phase) {
        if (state.panelOpen) {
            delay(160) // 等 AnimatedVisibility 把内容真正挂上
            runCatching { primaryFocus.requestFocus() }
        }
    }

    // 下载完成 → 自动调起系统安装界面（每个文件只自动一次，剩下交给按钮）。
    // canInstall 也作为触发条件：用户刚把权限开好、切回应用的那一瞬间，
    // 这里会立刻自动弹安装界面 —— "去允许 → 允许 → 回来"一条龙，不用再找按钮。
    LaunchedEffect(state.phase, state.apkPath, canInstall) {
        val path = state.apkPath
        if (state.phase == UpdatePhase.READY && path != null && autoInstalledFor != path) {
            autoInstalledFor = path
            if (canInstall) {
                if (UpdateInstaller.install(context, path)) onInstallLaunched() else onInstallFailed()
            }
        }
    }

    // 三段式：标题（固定） / 内容（吃掉剩余） / 动作（固定）
    val title: String
    val sub: String
    when (state.phase) {
        UpdatePhase.DOWNLOADING -> {
            title = "正在下载新版本"
            sub = info?.sizeText?.takeIf { it.isNotBlank() }?.let { "别急着关电视 · $it" } ?: "别急着关电视"
        }

        UpdatePhase.READY -> {
            title = "下载完成"
            sub = "系统马上会弹出安装界面，按遥控器选「安装」就行"
        }

        UpdatePhase.UP_TO_DATE -> {
            title = "已是最新版本"
            sub = "当前 v$currentVersionName，有新版本会在这里告诉你"
        }

        UpdatePhase.ERROR -> {
            title = "更新没成功"
            sub = state.message ?: "说不清哪里出了问题，再试一次"
        }

        else -> {
            // AVAILABLE / CHECKING / IDLE：一起处理，反正都只差"要不要更新"这一个决定
            val available = info != null
            title = if (available) "发现新版本 v${info?.versionName}" else "检查更新"
            sub = if (available) {
                buildString {
                    append("当前 v$currentVersionName")
                    info?.sizeText?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                }
            } else {
                "正在问服务器有没有新版本…"
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth(0.68f)
            // ★ 固定高度：所有阶段共用同一个卡片大小（内容多少都不改变外框）
            .height(m.dp(420f))
            .clip(RoundedCornerShape(m.dp(22f)))
            .background(Panel)
            .padding(horizontal = m.dp(42f), vertical = m.dp(32f)),
    ) {
        // ── 标题区：固定高度 ──
        Column(
            Modifier
                .fillMaxWidth()
                .height(m.dp(92f)),
        ) {
            Text(
                text = title,
                color = TextMain,
                fontSize = m.sp(26f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(m.dp(8f)))
            Text(
                text = sub,
                color = TextSub,
                fontSize = m.sp(15f),
                lineHeight = m.sp(22f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // ── 内容区：吃掉剩余高度（进度条 / 更新说明 / 结果说明都住在这里）──
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when (state.phase) {
                UpdatePhase.DOWNLOADING -> Column {
                    Spacer(Modifier.height(m.dp(6f)))
                    ProgressLine(state.progress)
                    Spacer(Modifier.height(m.dp(14f)))
                    Text(
                        text = "${(state.progress * 100).toInt()}%",
                        color = TextSub,
                        fontSize = m.sp(16f),
                    )
                }

                UpdatePhase.READY -> Column {
                    Spacer(Modifier.height(m.dp(6f)))
                    Text(
                        text = state.message.orEmpty(),
                        color = Gold.copy(alpha = 0.9f),
                        fontSize = m.sp(15f),
                        lineHeight = m.sp(22f),
                        maxLines = 2,
                    )
                }

                UpdatePhase.UP_TO_DATE -> Column {
                    Spacer(Modifier.height(m.dp(6f)))
                    Text(
                        text = "有新版本时首页右上角会亮一个小金标，点它就回到这里。",
                        color = TextBody,
                        fontSize = m.sp(16f),
                        lineHeight = m.sp(24f),
                        maxLines = 3,
                    )
                }

                UpdatePhase.ERROR -> Column {
                    Spacer(Modifier.height(m.dp(6f)))
                    Text(
                        text = "没下载成功通常和网络有关。片源和更新清单是两条独立的路，" +
                            "接口能用不代表更新地址也能用；再试一次多半就好了。",
                        color = TextBody,
                        fontSize = m.sp(16f),
                        lineHeight = m.sp(24f),
                        maxLines = 4,
                    )
                }

                else -> Column {
                    Spacer(Modifier.height(m.dp(6f)))
                    val notes = info?.notes?.takeIf { it.isNotBlank() }
                    Text(
                        text = notes ?: "这次更新会直接覆盖安装，观看记录、筛选和接口地址都会保留。",
                        color = TextBody,
                        fontSize = m.sp(16f),
                        lineHeight = m.sp(26f),
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // ── 动作区：固定高度，每个阶段都只有一行按钮 ──
        Row(
            Modifier
                .fillMaxWidth()
                .height(m.dp(56f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state.phase) {
                UpdatePhase.DOWNLOADING -> PanelAction("后台继续", primary = false, focus = primaryFocus) {
                    onDismiss()
                }

                UpdatePhase.READY -> {
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

                UpdatePhase.UP_TO_DATE -> PanelAction("知道了", primary = true, focus = primaryFocus) {
                    onDismiss()
                }

                UpdatePhase.ERROR -> {
                    if (info != null) {
                        PanelAction("重试下载", primary = true, focus = primaryFocus) { onDownload() }
                        Spacer(Modifier.width(m.dp(14f)))
                    }
                    PanelAction(
                        text = "关闭",
                        primary = info == null,
                        focus = if (info == null) primaryFocus else null,
                    ) { onDismiss() }
                }

                else -> {
                    PanelAction("立即更新", primary = true, focus = primaryFocus) { onDownload() }
                    Spacer(Modifier.width(m.dp(14f)))
                    PanelAction("以后再说", primary = false) { onDismiss() }
                }
            }
        }
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
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .tvFocus(
                cornerRadius = radius,
                borderColor = if (primary) Color.White.copy(alpha = 0.9f) else Gold,
                interactionSource = interaction,
            )
            .clip(RoundedCornerShape(radius))
            .background(if (primary) Gold else PanelHi)
            .clickable(
                interactionSource = interaction,
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
