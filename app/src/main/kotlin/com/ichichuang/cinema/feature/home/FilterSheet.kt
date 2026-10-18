package com.ichichuang.cinema.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.ichichuang.cinema.core.design.Gold
import com.ichichuang.cinema.core.design.Ink
import com.ichichuang.cinema.core.design.Motion
import com.ichichuang.cinema.core.design.Panel
import com.ichichuang.cinema.core.design.PanelHi
import com.ichichuang.cinema.core.design.TextMain
import com.ichichuang.cinema.core.design.TextSub
import com.ichichuang.cinema.core.design.rememberTvMetrics
import com.ichichuang.cinema.core.focus.tvFocus
import com.ichichuang.cinema.core.ui.FilterChip
import com.ichichuang.cinema.domain.model.DefaultMovieFilter
import com.ichichuang.cinema.domain.model.MovieFilter
import com.ichichuang.cinema.domain.repository.AREA_OPTIONS
import com.ichichuang.cinema.domain.repository.FILTER_ALL
import com.ichichuang.cinema.domain.repository.LANG_OPTIONS
import kotlinx.coroutines.delay

/**
 * 筛选浮层 —— 从页面下方**整屏滑上来**，选完点「完成」再滑下去。
 *
 * 为什么要整屏：Compose 的一行横向滚动条在电视上永远难用（一行太长、
 * 遥控器要按几十下、还容易抖动）。与其把 20 多个选项塞进一行，
 * 不如给它们一整屏，让它们**自己换行**——遥控器就只剩上下左右。
 *
 * 交互约定：
 * - 改动先落在草稿（draft）上，点「完成」才生效 —— 电视上不该点一下就重载一次列表
 * - 「重置」= 回到默认（全部分类 + 国语）
 * - 按遥控器「返回」= 放弃改动（和「取消」等价）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    visible: Boolean,
    filter: MovieFilter,
    categories: List<String>,
    yearOptions: List<String>,
    onApply: (MovieFilter) -> Unit,
    onClose: () -> Unit,
) {
    val m = rememberTvMetrics()
    var draft by remember { mutableStateOf(filter) }
    val firstChip = remember { FocusRequester() }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        // 每次打开都从"当前生效的筛选"开始编辑（而不是上次没点完成的草稿）
        draft = filter
        // 等滑入动画把内容挂上再要焦点，否则请求会落空
        delay(200)
        runCatching { firstChip.requestFocus() }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = Motion.enter()) { it } + fadeIn(Motion.enter()),
        exit = slideOutVertically(animationSpec = Motion.enter()) { it } + fadeOut(Motion.quick()),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (visible) BackHandler(onBack = onClose)

        Column(
            Modifier
                .fillMaxSize()
                .background(Ink)
                .padding(start = m.dp(56f), end = m.dp(56f), top = m.dp(28f), bottom = m.dp(22f)),
        ) {
            // ── 标题行（固定高度）──
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(m.dp(56f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "筛选",
                    color = TextMain,
                    fontSize = m.sp(28f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = m.sp(1f),
                )
                Spacer(Modifier.weight(1f))
                SheetAction(text = "重置", primary = false) { draft = DefaultMovieFilter }
                Spacer(Modifier.width(m.dp(14f)))
                SheetAction(text = "完成", primary = true) { onApply(draft) }
            }

            Spacer(Modifier.height(m.dp(8f)))

            // ── 四组筛选（换行网格，一行放不下就自己换行）──
            // 外面套了一层纵向滚动，纯粹是给"电视字号被系统调大"留的后路；
            // 正常字号下所有选项一屏内放得下，用户感觉不到它
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                FilterSection(
                    title = "分类 · 接口自动生成",
                    options = categories,
                    emptyHint = "等接口连通后，分类会自动出现在这里",
                    isSelected = { it == draft.category },
                    onSelect = { draft = draft.copy(category = it) },
                    firstFocus = firstChip,
                )
                FilterSection(
                    title = "语言",
                    options = LANG_OPTIONS,
                    isSelected = { it == draft.lang.ifBlank { FILTER_ALL } },
                    onSelect = { draft = draft.copy(lang = it.toFilterValue()) },
                )
                FilterSection(
                    title = "地区",
                    options = AREA_OPTIONS,
                    isSelected = { it == draft.area.ifBlank { FILTER_ALL } },
                    onSelect = { draft = draft.copy(area = it.toFilterValue()) },
                )
                FilterSection(
                    title = "年份",
                    options = yearOptions,
                    isSelected = { it == draft.year.ifBlank { FILTER_ALL } },
                    onSelect = { draft = draft.copy(year = it.toFilterValue()) },
                )
            }

            // ── 底部说明（固定一行）──
            Text(
                text = "选好后点「完成」生效；下次打开电视也会记住这次选择",
                color = TextSub,
                fontSize = m.sp(15f),
                maxLines = 1,
            )
        }
    }
}

private fun String.toFilterValue(): String = if (this == FILTER_ALL) "" else this

/** 一组筛选：小标题 + 换行药丸。第一个药丸可以被指定为"打开时先给焦点"的那一个 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(
    title: String,
    options: List<String>,
    isSelected: (String) -> Boolean,
    onSelect: (String) -> Unit,
    emptyHint: String? = null,
    firstFocus: FocusRequester? = null,
) {
    val m = rememberTvMetrics()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = m.dp(18f)),
    ) {
        Text(
            text = title,
            color = TextSub.copy(alpha = 0.8f),
            fontSize = m.sp(16f),
            letterSpacing = m.sp(0.5f),
        )
        Spacer(Modifier.height(m.dp(12f)))

        if (options.isEmpty()) {
            Text(
                text = emptyHint.orEmpty(),
                color = TextSub.copy(alpha = 0.6f),
                fontSize = m.sp(16f),
                maxLines = 1,
            )
            return@Column
        }

        // 六像素内边距：焦点放大的那一圈光晕需要呼吸空间，否则会被裁掉一条边
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(m.dp(6f)),
            horizontalArrangement = Arrangement.spacedBy(m.dp(12f)),
            verticalArrangement = Arrangement.spacedBy(m.dp(12f)),
        ) {
            options.forEachIndexed { index, option ->
                FilterChip(
                    label = option,
                    selected = isSelected(option),
                    big = true,
                    onClick = { onSelect(option) },
                    modifier = if (firstFocus != null && index == 0) {
                        Modifier.focusRequester(firstFocus)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

/** 浮层里的动作按钮：[完成] 用金色实底（主），[重置] 用深底（次） */
@Composable
private fun SheetAction(text: String, primary: Boolean, onClick: () -> Unit) {
    val m = rememberTvMetrics()
    val interaction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }

    val bg by animateColorAsState(
        targetValue = when {
            primary && focused -> Gold
            primary -> Gold.copy(alpha = 0.92f)
            focused -> PanelHi
            else -> Panel
        },
        animationSpec = Motion.quick(),
        label = "sheetActionBg",
    )
    val fg by animateColorAsState(
        targetValue = when {
            primary -> Ink
            focused -> TextMain
            else -> TextSub
        },
        animationSpec = Motion.quick(),
        label = "sheetActionFg",
    )
    // 按下时的回缩只用一条弹簧（和全应用其他按钮同源）
    Box(
        modifier = Modifier
            .tvFocus(
                cornerRadius = m.dp(24f),
                borderColor = if (primary) Color.White.copy(alpha = 0.9f) else Gold,
                interactionSource = interaction,
                onFocusChanged = { focused = it },
            )
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) { onClick() }
            .padding(horizontal = m.dp(30f), vertical = m.dp(14f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = m.sp(19f),
            fontWeight = FontWeight.Bold,
        )
    }
}
