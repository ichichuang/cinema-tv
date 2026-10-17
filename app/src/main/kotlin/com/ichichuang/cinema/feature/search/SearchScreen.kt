package com.ichichuang.cinema.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ichichuang.cinema.core.design.Gold
import com.ichichuang.cinema.core.design.Ink
import com.ichichuang.cinema.core.design.Motion
import com.ichichuang.cinema.core.design.Panel
import com.ichichuang.cinema.core.design.TextMain
import com.ichichuang.cinema.core.design.TextSub
import com.ichichuang.cinema.core.design.rememberTvMetrics
import com.ichichuang.cinema.core.focus.tvFocus
import com.ichichuang.cinema.core.ui.CenterHint
import com.ichichuang.cinema.core.ui.DetailPanel
import com.ichichuang.cinema.core.ui.FilterChip
import com.ichichuang.cinema.core.ui.PosterCard
import com.ichichuang.cinema.core.ui.PosterSkeleton
import com.ichichuang.cinema.domain.model.Movie
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

/**
 * 搜索页。
 *
 * 输入用**系统输入法**（不是自绘键盘）：电视上要搜的是中文片名，
 * 自绘 26 个字母按不出汉字，而系统输入法带拼音、联想、语音 —— 这是电视上唯一正确的选择。
 *
 * 焦点设计：有结果时焦点落在结果墙上（从播放器退回来时还落回原来那部），
 * 只有"还没有结果"时才把焦点给输入框 —— 否则每次回页面系统输入法都会自己弹出来，很烦。
 */
@Composable
fun SearchScreen(
    onPlay: (Movie) -> Unit,
    onBack: () -> Unit,
    vm: SearchViewModel = koinViewModel(),
) {
    BackHandler(onBack = onBack)
    val state by vm.state.collectAsState()
    val m = rememberTvMetrics()
    val screenW = LocalConfiguration.current.screenWidthDp
    val panelWidth: Dp = (screenW * 0.30f).dp
    val columns = if (screenW >= 1400) 4 else 3
    val gridState = rememberLazyGridState()
    val fieldFocus = remember { FocusRequester() }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val searchInteraction = remember { MutableInteractionSource() }
    val clearInteraction = remember { MutableInteractionSource() }

    // 结果整体"浮"上来：换关键词重搜时有个入场，而不是"啪"地铺满整屏。
    // 第一次组合直接取目标值 —— 从播放器退回搜索页时不会再演一遍。
    val resultsAlpha by animateFloatAsState(
        targetValue = if (state.results.isEmpty() || state.loading) 0f else 1f,
        animationSpec = Motion.base(),
        label = "resultsAlpha",
    )

    // 回到这个页面（比如从播放器退回来）刷一次进度 → 按钮显示"继续观看 12:34"
    LaunchedEffect(Unit) { vm.refreshSelectedProgress() }

    // 焦点落位：有结果 → 落到当前那部（回来时就是上次那部）；没结果 → 给输入框，直接打字
    LaunchedEffect(state.results, state.loading) {
        if (state.loading) return@LaunchedEffect
        if (state.results.isEmpty()) {
            runCatching { fieldFocus.requestFocus() }
            return@LaunchedEffect
        }
        val id = state.selected?.id ?: state.results.first().id
        val index = state.results.indexOfFirst { it.id == id }
        delay(80)
        repeat(3) {
            val requester = requesters[id]
            if (requester != null && runCatching { requester.requestFocus() }.isSuccess) return@LaunchedEffect
            if (index >= 0) runCatching { gridState.scrollToItem(index) }
            delay(90)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
    ) {
        Column(Modifier.fillMaxSize()) {
            // ── 搜索框 + 搜索按钮 ───────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = m.dp(48f), end = m.dp(48f), top = m.dp(26f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(fieldFocus)
                        .tvFocus(m.dp(10f)),
                    singleLine = true,
                    textStyle = TextStyle(color = TextMain, fontSize = m.sp(20f)),
                    placeholder = {
                        Text(
                            text = "输入片名搜索（按 OK 调出输入法，支持拼音和语音）",
                            color = TextSub,
                            fontSize = m.sp(18f),
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.submit() }),
                )
                Spacer(Modifier.width(m.dp(14f)))
                // 空着按「搜索」不该是"没反应"：把焦点送回输入框，用户立刻就懂了
                val canSearch = state.query.isNotBlank()
                Box(
                    modifier = Modifier
                        .tvFocus(
                            cornerRadius = m.dp(10f),
                            borderColor = Color.White.copy(alpha = 0.9f),
                            interactionSource = searchInteraction,
                        )
                        .clip(RoundedCornerShape(m.dp(10f)))
                        .background(Gold.copy(alpha = if (canSearch) 1f else 0.45f))
                        .clickable(
                            interactionSource = searchInteraction,
                            indication = null,
                        ) {
                            if (canSearch) vm.submit() else runCatching { fieldFocus.requestFocus() }
                        }
                        .padding(horizontal = m.dp(30f), vertical = m.dp(19f)),
                ) {
                    Text(
                        text = "搜索",
                        color = Ink.copy(alpha = if (canSearch) 1f else 0.6f),
                        fontSize = m.sp(18f),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // ── 最近搜索：遥控器打字贵，搜过的词按两下就能重搜 ──
            if (state.history.isNotEmpty()) {
                Spacer(Modifier.height(m.dp(14f)))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = m.dp(48f), end = m.dp(48f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("最近", color = TextSub, fontSize = m.sp(14f))
                    Spacer(Modifier.width(m.dp(14f)))
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        state.history.forEach { word ->
                            FilterChip(
                                label = word,
                                selected = word == state.submitted,
                                onClick = { vm.searchHistory(word) },
                            )
                            Spacer(Modifier.width(m.dp(10f)))
                        }
                    }
                    Spacer(Modifier.width(m.dp(10f)))
                    Box(
                        modifier = Modifier
                            .tvFocus(m.dp(8f), interactionSource = clearInteraction)
                            .clip(RoundedCornerShape(m.dp(8f)))
                            .background(Panel)
                            .clickable(
                                interactionSource = clearInteraction,
                                indication = null,
                            ) { vm.clearHistory() }
                            .padding(horizontal = m.dp(16f), vertical = m.dp(11f)),
                    ) {
                        Text("清空", color = TextSub, fontSize = m.sp(15f))
                    }
                }
            }

            // ── 结果统计（有一句就够了，不多摆字）───────────────
            if (state.submitted.isNotBlank() && !state.loading && state.error == null && state.results.isNotEmpty()) {
                Spacer(Modifier.height(m.dp(12f)))
                Text(
                    text = "「${state.submitted}」找到 ${state.results.size} 部",
                    color = TextSub,
                    fontSize = m.sp(15f),
                    modifier = Modifier.padding(start = m.dp(48f)),
                )
            }

            // ── 结果区 ─────────────────────────────────────────
            Box(Modifier.weight(1f)) {
                when {
                    state.loading -> ResultGrid(columns = columns, endPadding = panelWidth + m.dp(28f)) {
                        items(8) { PosterSkeleton() }
                    }

                    state.error != null -> CenterHint(
                        title = "搜索没成功",
                        body = state.error.orEmpty(),
                        actionText = "重试",
                        onAction = vm::retry,
                    )

                    state.results.isNotEmpty() -> Box(
                        Modifier.graphicsLayer {
                            alpha = resultsAlpha
                            translationY = (1f - resultsAlpha) * 24.dp.toPx()
                        },
                    ) {
                        ResultGrid(
                            columns = columns,
                            endPadding = panelWidth + m.dp(28f),
                            state = gridState,
                        ) {
                            itemsIndexed(state.results, key = { _, movie -> movie.id }) { _, movie ->
                                PosterCard(
                                    movie = movie,
                                    modifier = Modifier
                                        // 重新搜过后结果重排时，Compose 自己负责位移动画
                                        .animateItem()
                                        .focusRequester(
                                            requesters.getOrPut(movie.id) { FocusRequester() },
                                        ),
                                    onClick = { onPlay(movie) },
                                    onFocused = { vm.onMovieFocused(movie) },
                                )
                            }
                            if (state.results.size >= 24) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text(
                                        text = "结果较多，这里只显示前 24 部 —— 把片名写得更具体会更好找",
                                        color = TextSub,
                                        fontSize = m.sp(15f),
                                        modifier = Modifier.padding(top = m.dp(18f)),
                                    )
                                }
                            }
                        }
                    }

                    state.submitted.isNotBlank() -> CenterHint(
                        title = "没搜到「${state.submitted}」",
                        body = "片名写短一点更容易搜到（比如只写「流浪地球」，别带副标题和年份）。" +
                            "也可以换个说法，或者回首页按分类翻一翻。",
                        actionText = "重新输入",
                        onAction = { runCatching { fieldFocus.requestFocus() } },
                    )

                    else -> FirstHint()
                }
            }
        }

        // 右侧常驻详情：和首页同一套（焦点停在哪部就显示哪部）
        val shown = state.selected
        if (shown != null && !state.loading) {
            DetailPanel(
                movie = state.detail?.movie ?: shown,
                detail = state.detail,
                detailLoading = state.detailLoading,
                resumeMs = state.resumeMs,
                onPlay = { onPlay(shown) },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(panelWidth),
            )
        }
    }
}

/** 结果网格：和首页同一套间距/内边距（右侧给详情面板留位置） */
@Composable
private fun ResultGrid(
    columns: Int,
    endPadding: Dp,
    state: LazyGridState? = null,
    content: LazyGridScope.() -> Unit,
) {
    val m = rememberTvMetrics()
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state ?: rememberLazyGridState(),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = m.dp(48f),
            end = endPadding,
            top = m.dp(16f),
            bottom = m.dp(40f),
        ),
        horizontalArrangement = Arrangement.spacedBy(m.dp(20f)),
        verticalArrangement = Arrangement.spacedBy(m.dp(26f)),
        content = content,
    )
}

/** 还没搜过时的开场：一句话说清怎么用，不摆废话 */
@Composable
private fun FirstHint() {
    val m = rememberTvMetrics()
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = m.dp(48f))
    ) {
        Column(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "搜你想看的片子",
                color = TextMain,
                fontSize = m.sp(28f),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(m.dp(14f)))
            Text(
                text = "在输入框按 OK 会调出系统输入法：可以拼音打字，也可以按住遥控器的语音键说话。" +
                    "搜过的词会留在上面「最近」里，下次按两下就能重搜。",
                color = TextSub,
                fontSize = m.sp(18f),
                lineHeight = m.sp(30f),
                modifier = Modifier.fillMaxWidth(0.62f),
            )
        }
    }
}
