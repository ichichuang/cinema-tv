package com.ichichuang.cinema.feature.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.ichichuang.cinema.core.design.Gold
import com.ichichuang.cinema.core.design.Ink
import com.ichichuang.cinema.core.design.TextMain
import com.ichichuang.cinema.core.design.TextSub
import com.ichichuang.cinema.core.design.rememberTvMetrics
import com.ichichuang.cinema.core.focus.tvFocus
import com.ichichuang.cinema.data.settings.CinemaSettings
import org.koin.androidx.compose.koinViewModel

/**
 * 设置页。
 *
 * 只有真正需要用户碰的东西才在这里 —— 目前核心就一项：**影视接口地址**。
 * （应用不内置任何片源，地址必须由用户自己填，这是红线。）
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = koinViewModel(),
) {
    BackHandler(onBack = onBack)
    val m = rememberTvMetrics()
    val state by vm.state.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(m.dp(48f))
    ) {
        Text(
            text = "设置",
            color = TextMain,
            fontSize = m.sp(32f),
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(m.dp(28f)))

        // ── 接口地址（唯一需要输入的项）────────────────────────
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

        Spacer(Modifier.height(m.dp(30f)))
        SettingRow("分类语言", "简体中文")
        SettingRow("字幕样式", "默认")
        SettingRow("清空图片缓存", "自动管理")
        SettingRow("跳过片头片尾", "关")

        Spacer(Modifier.weight(1f))
        Text(
            text = "按遥控器「返回」键回到首页",
            color = TextSub,
            fontSize = m.sp(16f),
        )
    }
}

@Composable
private fun GoldButton(text: String, onClick: () -> Unit) {
    val m = rememberTvMetrics()
    val radius = m.dp(10f)
    Box(
        Modifier
            .tvFocus(radius)
            .clip(RoundedCornerShape(radius))
            .background(Gold)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(horizontal = m.dp(26f), vertical = m.dp(16f)),
    ) {
        Text(text, color = Ink, fontSize = m.sp(18f), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingRow(title: String, value: String) {
    val m = rememberTvMetrics()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = m.dp(14f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = TextMain, fontSize = m.sp(20f))
        Spacer(Modifier.weight(1f))
        Text(value, color = TextSub, fontSize = m.sp(18f))
    }
}
