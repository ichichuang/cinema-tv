package com.ichichuang.cinema.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.ichichuang.cinema.core.design.Gold
import com.ichichuang.cinema.core.design.Ink
import com.ichichuang.cinema.core.design.Motion
import com.ichichuang.cinema.core.design.Panel
import com.ichichuang.cinema.core.design.TextMain
import com.ichichuang.cinema.core.design.TextSub
import com.ichichuang.cinema.core.design.rememberTvMetrics
import com.ichichuang.cinema.core.focus.tvFocus
import com.ichichuang.cinema.data.settings.CinemaSettings
import com.ichichuang.cinema.feature.update.UpdateCenter
import com.ichichuang.cinema.feature.update.UpdateOverlay
import com.ichichuang.cinema.feature.update.UpdatePhase
import org.koin.androidx.compose.koinViewModel

/**
 * 设置页。
 *
 * 只有真正需要用户碰的东西才在这里 —— 两件：
 * 1. **影视接口地址**（应用不内置任何片源，这是红线）
 * 2. **更新地址**（默认是发布方的 GitHub 地址；连不上时用户可换成镜像，不用重装）
 *
 * 检查更新也放这里，但**发现新版本的金标在首页** —— 设置页是"我要改东西"才来的地方，
 * 日常发现有新版应该是首页顺手看到的。
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
    val scroll = rememberScrollState()

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Ink)
                // 可滚动：电视字号调大、或将来加设置项时，内容不会被裁在屏幕外
                .verticalScroll(scroll)
                .padding(m.dp(40f))
                // 更新浮层打开时关掉底下整屏的焦点（否则方向键会透过浮层乱跑）
                .then(if (updateState.panelOpen) Modifier.focusProperties { canFocus = false } else Modifier)
        ) {
            Text(
                text = "设置",
                color = TextMain,
                fontSize = m.sp(30f),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(m.dp(22f)))

            // ── 接口地址（唯一必须由用户提供的东西）────────────────
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("影视接口地址", color = TextMain, fontSize = m.sp(20f))
                    Spacer(Modifier.height(m.dp(10f)))
                    OutlinedTextField(
                        value = state.draft,
                        onValueChange = vm::onDraftChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocus(m.dp(10f)),
                        singleLine = true,
                        textStyle = TextStyle(color = TextMain, fontSize = m.sp(16f)),
                        placeholder = {
                            Text(
                                text = CinemaSettings.PLACEHOLDER,
                                color = TextSub,
                                fontSize = m.sp(15f),
                            )
                        },
                    )
                }
                Spacer(Modifier.width(m.dp(18f)))
                GoldButton(
                    text = if (state.justSaved) "已保存 ✓" else "保存",
                    onClick = vm::save,
                )
            }
            Spacer(Modifier.height(m.dp(10f)))
            Text(
                text = "填你的苹果CMS接口地址（形如 http://站点/api.php/provide/vod/）。" +
                    "填 demo 可以使用内置演示数据看界面。",
                color = TextSub,
                fontSize = m.sp(15f),
            )

            Spacer(Modifier.height(m.dp(22f)))

            // ── 更新地址 + 检查更新 ─────────────────────────────
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("更新地址（version.json）", color = TextMain, fontSize = m.sp(20f))
                    Spacer(Modifier.height(m.dp(10f)))
                    OutlinedTextField(
                        value = state.updateDraft,
                        onValueChange = vm::onUpdateDraftChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocus(m.dp(10f)),
                        singleLine = true,
                        textStyle = TextStyle(color = TextMain, fontSize = m.sp(16f)),
                        placeholder = {
                            Text(
                                text = CinemaSettings.UPDATE_PLACEHOLDER,
                                color = TextSub,
                                fontSize = m.sp(15f),
                            )
                        },
                    )
                }
                Spacer(Modifier.width(m.dp(18f)))
                GoldButton(
                    text = if (state.justSavedUpdate) "已保存 ✓" else "保存",
                    onClick = vm::saveUpdate,
                )
                Spacer(Modifier.width(m.dp(10f)))
                DarkButton(text = "恢复默认", onClick = vm::resetUpdate)
            }
            Spacer(Modifier.height(m.dp(10f)))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "默认指向发布方的 GitHub 地址。电视连不上时换成镜像地址即可，" +
                        "换完就能继续收到更新，不用重装应用。",
                    color = TextSub,
                    fontSize = m.sp(15f),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(m.dp(16f)))
                val checking = updateState.phase == UpdatePhase.CHECKING
                DarkButton(
                    text = if (checking) "检查中…" else "检查更新",
                    enabled = !checking,
                    onClick = updateCenter::checkManually,
                )
                if (updateState.hasUpdate) {
                    Spacer(Modifier.width(m.dp(12f)))
                    GoldButton(text = "查看更新", onClick = updateCenter::openPanel)
                }
            }

            // 检查结果（成功/最新/失败都在这一行，人话，不暴露 http 细节）
            updateState.message?.let { msg ->
                Spacer(Modifier.height(m.dp(10f)))
                Text(
                    text = msg,
                    color = Gold.copy(alpha = 0.9f),
                    fontSize = m.sp(15f),
                    maxLines = 2,
                )
            }

            Spacer(Modifier.height(m.dp(22f)))

            // ── 图片缓存：真能清掉的那一项 ──────────────────────
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("图片缓存", color = TextMain, fontSize = m.sp(20f))
                    Spacer(Modifier.height(m.dp(6f)))
                    Text(
                        text = state.cacheMessage
                            ?: "海报缓存攒多了会占空间。电视存储紧张时清一下，不影响观看。",
                        color = if (state.cacheMessage != null) Gold.copy(alpha = 0.9f) else TextSub,
                        fontSize = m.sp(15f),
                    )
                }
                Spacer(Modifier.width(m.dp(16f)))
                DarkButton(text = "清空缓存", onClick = vm::clearImageCache)
            }

            Spacer(Modifier.height(m.dp(26f)))
            Text(
                text = "「当前版本 v${updateCenter.currentVersionName}」 · 按遥控器「返回」键回到首页",
                color = TextSub,
                fontSize = m.sp(16f),
            )
        }

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

@Composable
private fun GoldButton(text: String, onClick: () -> Unit) {
    val m = rememberTvMetrics()
    val radius = m.dp(10f)
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .tvFocus(
                cornerRadius = radius,
                borderColor = Color.White.copy(alpha = 0.9f),
                interactionSource = interaction,
            )
            .clip(RoundedCornerShape(radius))
            .background(Gold)
            // 按钮文案会在「保存 / 已保存 ✓」之间变，宽度跟着平滑过渡，别"啪"地一跳
            .animateContentSize(animationSpec = Motion.swap())
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) { onClick() }
            .padding(horizontal = m.dp(26f), vertical = m.dp(16f)),
    ) {
        Text(text, color = Ink, fontSize = m.sp(18f), fontWeight = FontWeight.Bold)
    }
}

/** 次要按钮：深底、不抢视线（设置页里除"保存"以外的动作都用它） */
@Composable
private fun DarkButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val m = rememberTvMetrics()
    val radius = m.dp(10f)
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .tvFocus(radius, interactionSource = interaction)
            .clip(RoundedCornerShape(radius))
            .background(Panel)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
            ) { onClick() }
            .padding(horizontal = m.dp(22f), vertical = m.dp(16f)),
    ) {
        Text(
            text = text,
            color = TextMain.copy(alpha = if (enabled) 1f else 0.5f),
            fontSize = m.sp(17f),
            fontWeight = FontWeight.Medium,
        )
    }
}
