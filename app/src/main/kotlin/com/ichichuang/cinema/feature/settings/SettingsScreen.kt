package com.ichichuang.cinema.feature.settings

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.ichichuang.cinema.feature.update.UpdateCenter
import com.ichichuang.cinema.feature.update.UpdateOverlay
import com.ichichuang.cinema.feature.update.UpdatePhase
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

/**
 * 设置页 —— **没有一个输入框**。
 *
 * 为什么（用户真机反馈）：电视上任何输入框一聚焦就弹系统键盘，
 * 用遥控器一格一格挪字母，是整台电视上最折磨人的操作。
 * 所以这里的做法是：
 * - 影视接口地址 → 「扫码配置」：电视在局域网上起个小网页，**手机扫码填**，
 *   点保存直接推到电视（保存完电视还会自动测一次，结果同步告诉手机）
 * - 更新地址同理（在手机页面的"高级"里）
 * - 电视上只保留**能一键按完**的动作：测试连接 / 检查更新 / 清缓存
 *
 * 排版规矩（页面级硬要求）：**所有容器都是固定尺寸**。
 * 测试结果、检查更新结果、缓存结果都放在**高度写死的槽位**里 ——
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

    val overlayOpen = updateState.panelOpen || state.configOpen

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
                text = "电视上不用打字：点「扫码配置」，用手机扫一下码就能改。",
                color = TextSub,
                fontSize = m.sp(16f),
                maxLines = 1,
            )
            Spacer(Modifier.height(m.dp(22f)))

            // ── 影视接口（唯一必须由用户提供的东西）────────────────
            SectionCard {
                Text("影视接口", color = TextMain, fontSize = m.sp(20f))
                Spacer(Modifier.height(m.dp(8f)))
                // 地址一行 + 状态一行：两个槽位高度写死，配置前后排版一致
                Text(
                    text = state.apiBase.takeIf { it.isNotBlank() }
                        ?: "还没配置 —— 应用不内置任何片源",
                    color = if (state.apiBase.isNotBlank()) TextMain.copy(alpha = 0.9f) else Gold.copy(alpha = 0.9f),
                    fontSize = m.sp(16f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(m.dp(16f)))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GoldButton(text = "扫码配置", onClick = vm::openConfig)
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
                        SourceTest.Idle -> "配置完成后点「测试连接」；也可以直接用手机扫的页面测"
                        else -> t.text
                    },
                    highlight = state.test.passed,
                )
            }

            Spacer(Modifier.height(m.dp(22f)))

            // ── 在线更新 ────────────────────────────────────────
            SectionCard {
                Text("在线更新", color = TextMain, fontSize = m.sp(20f))
                Spacer(Modifier.height(m.dp(8f)))
                Text(
                    text = state.updateUrl,
                    color = TextSub,
                    fontSize = m.sp(15f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(m.dp(16f)))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val checking = updateState.phase == UpdatePhase.CHECKING
                    DarkButton(
                        text = if (checking) "检查中…" else "检查更新",
                        width = m.dp(170f),
                        enabled = !checking,
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
                    text = updateState.message
                        ?: "默认走发布方的 GitHub 地址；连不上时可在手机配置页换成镜像。",
                    highlight = updateState.hasUpdate,
                )
            }

            Spacer(Modifier.height(m.dp(22f)))

            // ── 图片缓存：真能清掉的那一项 ──────────────────────
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("图片缓存", color = TextMain, fontSize = m.sp(20f))
                        Spacer(Modifier.height(m.dp(8f)))
                        Text(
                            text = state.cacheMessage
                                ?: "海报缓存攒多了会占空间。电视存储紧张时清一下，不影响观看。",
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
            SectionCard {
                Text("系统信息", color = TextMain, fontSize = m.sp(20f))
                Spacer(Modifier.height(m.dp(8f)))
                Text(
                    text = "电视的设置里找不到 Android 版本时，看这里。反馈问题时截这一屏即可。",
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
            }

            Spacer(Modifier.height(m.dp(26f)))
            Text(
                text = "「当前版本 v${updateCenter.currentVersionName}」 · 按遥控器「返回」键回到首页",
                color = TextSub,
                fontSize = m.sp(16f),
            )
        }

        // 扫码配置浮层（手机 → 电视）
        ConfigOverlay(
            visible = state.configOpen,
            url = state.serverUrl,
            message = state.serverMessage,
            passed = state.test.passed,
            onDismiss = vm::closeConfig,
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
        enter = fadeIn(Motion.enter()),
        exit = fadeOut(Motion.quick()),
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
                    .padding(horizontal = m.dp(36f), vertical = m.dp(30f)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "手机扫码配置",
                    color = TextMain,
                    fontSize = m.sp(26f),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(m.dp(8f)))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(m.dp(46f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "同一个 WiFi 下，用手机扫码；电脑也可以直接输入下面的网址。" +
                            "填好点保存，电视立即生效。",
                        color = TextSub,
                        fontSize = m.sp(15f),
                        lineHeight = m.sp(22f),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
                Spacer(Modifier.height(m.dp(14f)))

                // 二维码：固定 260dp 的白底方块（白底黑码，所有手机都认）
                Box(
                    Modifier
                        .size(m.dp(276f))
                        .clip(RoundedCornerShape(m.dp(16f)))
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    if (qr != null) {
                        Image(
                            bitmap = qr,
                            contentDescription = "配置地址二维码",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(m.dp(256f)),
                        )
                    } else {
                        Text(
                            text = if (url == null) "正在启动配置服务…" else "二维码生成失败",
                            color = Ink.copy(alpha = 0.7f),
                            fontSize = m.sp(16f),
                        )
                    }
                }

                Spacer(Modifier.height(m.dp(16f)))
                // 网址：给电脑用（固定一行，永远占着）
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(m.dp(30f)),
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

                Spacer(Modifier.height(m.dp(6f)))
                // 状态行：固定两行高度（等待 → 已收到 → 测试结果，都不会顶动别的）
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(m.dp(48f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = message.orEmpty(),
                        color = if (passed) Gold else TextSub,
                        fontSize = m.sp(15f),
                        lineHeight = m.sp(22f),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }

                Spacer(Modifier.height(m.dp(12f)))
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

/** 一节设置：深色卡片，内部所有行都是固定高度 */
@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    val m = rememberTvMetrics()
    Column(
        Modifier
            .fillMaxWidth()
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

/** 次要按钮：深底、不抢视线（设置页里除"扫码配置"以外的动作都用它） */
@Composable
private fun DarkButton(
    text: String,
    enabled: Boolean = true,
    width: Dp? = null,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
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
        Modifier
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
