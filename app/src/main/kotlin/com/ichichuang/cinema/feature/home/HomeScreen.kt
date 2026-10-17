package com.ichichuang.cinema.feature.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ichichuang.cinema.core.design.Gold
import com.ichichuang.cinema.core.design.Ink
import com.ichichuang.cinema.core.design.Motion
import com.ichichuang.cinema.core.design.Panel
import com.ichichuang.cinema.core.design.PanelHi
import com.ichichuang.cinema.core.design.TextMain
import com.ichichuang.cinema.core.design.TextSub
import com.ichichuang.cinema.core.design.rememberTvMetrics
import com.ichichuang.cinema.core.focus.tvFocus
import com.ichichuang.cinema.core.ui.DetailPanel
import com.ichichuang.cinema.core.ui.PosterCard
import com.ichichuang.cinema.domain.model.Movie
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.androidx.compose.koinViewModel

/**
 * 首页 —— 一屏搞定一切。
 *
 * 布局：顶部筛选条 + 左侧海报墙 + 右侧常驻详情。
 * 遥控器：左右切换分类，上下进海报墙，OK 直接播放。
 */
@Composable
fun HomeScreen(
    onPlay: (Movie) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val m = rememberTvMetrics()
    val screenW = LocalConfiguration.current.screenWidthDp
    val panelWidth: Dp = (screenW * 0.30f).dp

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
    ) {
        Column(Modifier.fillMaxSize()) {
            Header(
                category = state.category,
                categories = state.categories,
                onCategory = viewModel::selectCategory,
                onOpenSettings = onOpenSettings,
            )
            PosterGrid(
                movies = state.movies,
                loading = state.loading,
                endReached = state.endReached,
                endPadding = panelWidth + m.dp(28f),
                needsSetup = state.needsSetup,
                error = state.error,
                onFocusMovie = viewModel::onMovieFocused,
                onClickMovie = onPlay,
                onNearEnd = viewModel::onVisibleIndexChanged,
                onOpenSettings = onOpenSettings,
                onRetry = viewModel::retry,
            )
        }

        // 右侧常驻详情：随焦点实时变化，布局不动
        val shown = state.selected ?: state.movies.firstOrNull()
        if (shown != null) {
            DetailPanel(
                // 详情到货后用详情的版本（字段更全），没到就用列表里的简表
                movie = state.detail?.movie ?: shown,
                detail = state.detail,
                detailLoading = state.detailLoading,
                onPlay = { onPlay(shown) },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(panelWidth),
            )
        }
    }
}

// ── 顶栏 ─────────────────────────────────────────────────────

@Composable
private fun Header(
    category: String,
    categories: List<String>,
    onCategory: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val m = rememberTvMetrics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = m.dp(48f), end = m.dp(48f), top = m.dp(30f), bottom = m.dp(16f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "极简影院",
            color = TextMain,
            fontSize = m.sp(30f),
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(m.dp(32f)))

        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            categories.forEach { c ->
                Chip(
                    label = c,
                    selected = c == category,
                    onSelect = { onCategory(c) },
                )
            }
        }

        Chip(label = "设置", selected = false, onSelect = onOpenSettings)
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onSelect: () -> Unit) {
    val m = rememberTvMetrics()
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    val bg = when {
        selected -> Gold
        focused -> PanelHi
        else -> Panel
    }
    val fg = when {
        selected -> Ink
        focused -> TextMain
        else -> TextSub
    }

    Box(
        modifier = Modifier
            .padding(start = m.dp(12f))
            .clip(shape)
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onSelect() }
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = m.dp(24f), vertical = m.dp(12f)),
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = m.sp(19f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

// ── 海报墙 ───────────────────────────────────────────────────

@Composable
private fun PosterGrid(
    movies: List<Movie>,
    loading: Boolean,
    endReached: Boolean,
    endPadding: Dp,
    needsSetup: Boolean,
    error: String?,
    onFocusMovie: (Movie) -> Unit,
    onClickMovie: (Movie) -> Unit,
    onNearEnd: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    val m = rememberTvMetrics()
    val screenW = LocalConfiguration.current.screenWidthDp
    val columns = if (screenW >= 1400) 4 else 3
    val gridState = rememberLazyGridState()
    val firstFocus = remember { FocusRequester() }

    val contentPadding = PaddingValues(
        start = m.dp(48f),
        end = endPadding,
        top = m.dp(8f),
        bottom = m.dp(60f),
    )

    // 滚到接近底部 → 交给 ViewModel 决定要不要加载下一页
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { last -> if (last >= 0) onNearEnd(last) }
    }

    when {
        // 还没配置片源 → 引导去设置（这是第一次使用的正常状态，不是错误）
        needsSetup -> {
            CenterHint(
                title = "还没有配置影视接口地址",
                body = "应用不内置任何片源。去「设置」里填入你自己的苹果CMS接口地址，这里就会出现内容。" +
                    "想先看看界面长什么样，地址栏里填 demo 即可。",
                actionText = "去设置",
                onAction = onOpenSettings,
            )
        }

        // 接口出错（地址写错 / 站点挂了 / 网络不通）→ 说人话 + 可以重试
        error != null && movies.isEmpty() -> {
            CenterHint(
                title = "没能取到片源数据",
                body = error,
                actionText = "重试",
                onAction = onRetry,
            )
        }

        loading && movies.isEmpty() -> {
            // 骨架屏：加载时是"微光占位"，绝不是白屏或转圈
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(m.dp(20f)),
                verticalArrangement = Arrangement.spacedBy(m.dp(26f)),
                userScrollEnabled = false,
            ) {
                items(12) { PosterSkeleton() }
            }
        }

        movies.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("这个分类暂时没有内容", color = TextSub, fontSize = m.sp(22f))
            }
        }

        else -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(m.dp(20f)),
                verticalArrangement = Arrangement.spacedBy(m.dp(26f)),
            ) {
                itemsIndexed(movies, key = { _, movie -> movie.id }) { index, movie ->
                    PosterCard(
                        movie = movie,
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                        onClick = { onClickMovie(movie) },
                        onFocused = { onFocusMovie(movie) },
                    )
                }
                if (endReached) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = m.dp(20f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("已经到底啦", color = TextSub, fontSize = m.sp(16f))
                        }
                    }
                }
            }
        }
    }

    // 数据到位后自动把焦点放到第一张海报：开机 → 直接按 OK 就能看
    LaunchedEffect(movies.isNotEmpty()) {
        if (movies.isEmpty()) return@LaunchedEffect
        delay(150)
        runCatching { firstFocus.requestFocus() }
    }
}

/**
 * 居中提示卡（未配置片源 / 接口出错时用）。
 *
 * 它们都带一个**可聚焦**的主按钮，并且出现时自动拿焦点 ——
 * 遥控器用户不需要"摸索着找按钮在哪"，按 OK 就是最该做的那件事。
 */
@Composable
private fun CenterHint(
    title: String,
    body: String,
    actionText: String,
    onAction: () -> Unit,
) {
    val m = rememberTvMetrics()
    val focus = remember { FocusRequester() }
    val radius = m.dp(14f)

    LaunchedEffect(Unit) {
        delay(200)
        runCatching { focus.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = m.dp(48f)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = title,
            color = TextMain,
            fontSize = m.sp(30f),
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(m.dp(14f)))
        Text(
            text = body,
            color = TextSub,
            fontSize = m.sp(19f),
            lineHeight = m.sp(30f),
            modifier = Modifier.fillMaxWidth(0.55f),
        )
        Spacer(Modifier.height(m.dp(30f)))
        Box(
            modifier = Modifier
                .tvFocus(radius)
                .focusRequester(focus)
                .clip(RoundedCornerShape(radius))
                .background(Gold)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onAction() }
                .padding(horizontal = m.dp(30f), vertical = m.dp(16f)),
        ) {
            Text(actionText, color = Ink, fontSize = m.sp(20f), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PosterSkeleton() {
    val m = rememberTvMetrics()
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = Motion.EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    val shape = RoundedCornerShape(m.dp(14f))
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(Panel.copy(alpha = alpha))
        )
        Spacer(Modifier.height(m.dp(10f)))
        Box(
            Modifier
                .fillMaxWidth(0.7f)
                .height(m.dp(16f))
                .clip(RoundedCornerShape(m.dp(4f)))
                .background(Panel.copy(alpha = alpha))
        )
        Spacer(Modifier.height(m.dp(6f)))
        Box(
            Modifier
                .fillMaxWidth(0.5f)
                .height(m.dp(12f))
                .clip(RoundedCornerShape(m.dp(4f)))
                .background(Panel.copy(alpha = alpha))
        )
    }
}
