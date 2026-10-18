package com.ichichuang.cinema.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import com.ichichuang.cinema.core.design.Ink
import com.ichichuang.cinema.core.design.Motion
import com.ichichuang.cinema.core.design.TextMain
import com.ichichuang.cinema.core.design.TextSub
import com.ichichuang.cinema.core.design.rememberTvMetrics
import com.ichichuang.cinema.core.ui.FilterChip
import com.ichichuang.cinema.domain.model.CategoryTree
import com.ichichuang.cinema.domain.model.MovieFilter
import com.ichichuang.cinema.domain.repository.AREA_OPTIONS
import com.ichichuang.cinema.domain.repository.CATEGORY_ALL
import com.ichichuang.cinema.domain.repository.FILTER_ALL
import com.ichichuang.cinema.domain.repository.LANG_OPTIONS
import kotlinx.coroutines.delay

/**
 * 筛选浮层 —— 从页面下方**整屏滑上来**。
 *
 * 为什么要整屏：Compose 的一行横向滚动条在电视上永远难用（一行太长、
 * 遥控器要按几十下、还容易抖动）。与其把 20 多个选项塞进一行，
 * 不如给它们一整屏，让它们**自己换行**——遥控器就只剩上下左右。
 *
 * 交互约定（按用户真机反馈改过）：
 * - **没有「保存 / 完成」那一行** —— 选好后直接按遥控器「返回」，
 *   回到首页立刻按新条件重筛（这次选择也会被记住）
 * - 遥控器「菜单」键和「返回」等价：也能"应用并关闭"（首页上菜单键是进浮层的门）
 * - 想重置：每个分区点「全部」；语言点「国语」（默认值）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    visible: Boolean,
    filter: MovieFilter,
    /** 接口返回的真实分类树 —— 分类/类型选项全部来自它，没有一份自造词表 */
    tree: CategoryTree,
    yearOptions: List<String>,
    onApply: (MovieFilter) -> Unit,
) {
    val m = rememberTvMetrics()
    var draft by remember { mutableStateOf(filter) }
    val firstChip = remember { FocusRequester() }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        // 每次打开都从"当前生效的筛选"开始编辑（而不是上次没用上的草稿）
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
        // 返回 = 应用并关闭（没有"保存"按钮，返回就是唯一出口）
        if (visible) BackHandler { onApply(draft) }

        Column(
            Modifier
                .fillMaxSize()
                .background(Ink)
                .padding(start = m.dp(56f), end = m.dp(56f), top = m.dp(28f), bottom = m.dp(22f))
                .onPreviewKeyEvent { e ->
                    // 菜单键 = 应用并关闭（和「返回」同一条路）。
                    // 首页那层用同一个键开门，这里用同一个键收尾 —— 按哪个键进的就按哪个键出。
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Menu) {
                        onApply(draft)
                        true
                    } else {
                        false
                    }
                },
        ) {
            // ── 标题行（固定高度）：左边标题，右边当前选择的摘要 ──
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
                Text(
                    text = draft.summaryText(),
                    color = TextSub,
                    fontSize = m.sp(17f),
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(m.dp(4f)))

            // ── 五组筛选（换行网格，一行放不下就自己换行）──
            // 外面套了一层纵向滚动：字号被系统调大时也不怕放不下；
            // 焦点移动时 Compose 会自动把选中的药丸滚进视野，遥控器只管上下左右
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                FilterSection(
                    title = "分类",
                    options = listOf(CATEGORY_ALL) + tree.tops,
                    emptyHint = "暂时没有分类",
                    isSelected = { it == draft.category },
                    onSelect = { cat ->
                        // 换分类 → 已经选中的类型如果不在新分类下就落掉（选项以接口为准）
                        val allowed = tree.typesFor(cat)
                        draft = draft.copy(
                            category = cat,
                            types = draft.types.filter { it in allowed },
                        )
                    },
                    firstFocus = firstChip,
                )
                // 类型 = 当前分类在接口里的**真实下级题材**，可多选：
                // 点一下选中（高亮）、再点取消；数了多个就是"这几个都要看"
                FilterSection(
                    title = "类型（可多选）",
                    options = tree.typesFor(draft.category),
                    emptyHint = "这个分类没有细分",
                    isSelected = { it in draft.types },
                    onSelect = { type ->
                        draft = draft.copy(
                            types = if (type in draft.types) draft.types - type else draft.types + type,
                        )
                    },
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
        }
    }
}

private fun String.toFilterValue(): String = if (this == FILTER_ALL) "" else this

/** 右上角摘要："电影 · 国语 · 喜剧"，什么都没选就是"全部影片" */
private fun MovieFilter.summaryText(): String {
    val parts = buildList {
        if (category != CATEGORY_ALL) add(category)
        addAll(activeParts)
    }
    return if (parts.isEmpty()) "全部影片" else parts.joinToString(" · ")
}

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
            .padding(top = m.dp(14f)),
    ) {
        Text(
            text = title,
            color = TextSub.copy(alpha = 0.8f),
            fontSize = m.sp(16f),
            letterSpacing = m.sp(0.5f),
        )
        Spacer(Modifier.height(m.dp(10f)))

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
