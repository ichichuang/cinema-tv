package com.ichichuang.cinema.feature.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
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
import com.ichichuang.cinema.core.ui.DetailPanel
import com.ichichuang.cinema.core.ui.FilterChip
import com.ichichuang.cinema.core.ui.FilterGroup
import com.ichichuang.cinema.core.ui.PosterCard
import com.ichichuang.cinema.domain.model.Movie
import com.ichichuang.cinema.domain.model.MovieFilter
import com.ichichuang.cinema.domain.repository.AREA_OPTIONS
import com.ichichuang.cinema.domain.repository.FILTER_ALL
import com.ichichuang.cinema.domain.repository.LANG_OPTIONS
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.androidx.compose.koinViewModel

/**
 * 首页 —— 一屏搞定一切。
 *
 * 布局：筛选区（两行）+ 左侧海报墙 + 右侧常驻详情。
 * 遥控器：左右切筛选/分类（焦点自动跟随滚动），上下进海报墙，OK 直接播放。
 *
 * 筛选设计：分类（接口自动生成）+ 语言 + 地区 + 年份 三个可选维度，
 * 默认只看国语（用户改过就记住他改的）。
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

    // 从播放器回到首页时刷一次进度：按钮会变成"继续观看 12:34"
    LaunchedEffect(Unit) { viewModel.refreshSelectedProgress() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
    ) {
        Column(Modifier.fillMaxSize()) {
            Header(
                categories = state.categories,
                filter = state.filter,
                total = state.total,
                relaxedNote = state.relaxedNote,
                yearOptions = state.yearOptions,
                onCategory = viewModel::selectCategory,
                onLang = viewModel::selectLang,
                onArea = viewModel::selectArea,
                onYear = viewModel::selectYear,
                onOpenSettings = onOpenSettings,
            )
            PosterGrid(
                movies = state.movies,
                loading = state.loading,
                endReached = state.endReached,
                generation = state.gridGeneration,
                endPadding = panelWidth + m.dp(28f),
                needsSetup = state.needsSetup,
                error = state.error,
                onFocusMovie = viewModel::onMovieFocused,
                onClickMovie = onPlay,
                onNearEnd = viewModel::onVisibleIndexChanged,
                onOpenSettings = onOpenSettings,
                onRetry = viewModel::retry,
                onResetFilters = viewModel::resetFilters,
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

// ── 顶部：品牌 + 分类（接口自动生成）+ 三个筛选维度 ─────────────

@Composable
private fun Header(
    categories: List<String>,
    filter: MovieFilter,
    total: Int,
    relaxedNote: String?,
    yearOptions: List<String>,
    onCategory: (String) -> Unit,
    onLang: (String) -> Unit,
    onArea: (String) -> Unit,
    onYear: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val m = rememberTvMetrics()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = m.dp(48f), end = m.dp(48f), top = m.dp(26f), bottom = m.dp(8f)),
    ) {
        // 第一行：品牌 + 分类 + 设置
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "极简影院",
                color = TextMain,
                fontSize = m.sp(28f),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(m.dp(26f)))

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterGroup(
                    title = "",
                    options = categories,
                    selected = filter.category,
                    onSelect = onCategory,
                    big = true,
                )
            }

            Spacer(Modifier.width(m.dp(16f)))
            FilterChip(label = "设置", selected = false, onClick = onOpenSettings)
        }

        Spacer(Modifier.height(m.dp(12f)))

        // 第二行：语言 / 地区 / 年份（左，可滚动）+ 当前筛选摘要（右，固定）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterGroup(
                    title = "语言",
                    options = LANG_OPTIONS,
                    selected = filter.lang.ifBlank { FILTER_ALL },
                    onSelect = onLang,
                )
                FilterGroup(
                    title = "地区",
                    options = AREA_OPTIONS,
                    selected = filter.area.ifBlank { FILTER_ALL },
                    onSelect = onArea,
                    dividerBefore = true,
                )
                FilterGroup(
                    title = "年份",
                    options = yearOptions,
                    selected = filter.year.ifBlank { FILTER_ALL },
                    onSelect = onYear,
                    dividerBefore = true,
                )
            }

            Spacer(Modifier.width(m.dp(20f)))
            FilterSummary(filter = filter, total = total)
        }

        // 筛条件被放宽时的说明（只在真发生时出现，平时不占地方）
        relaxedNote?.let { note ->
            Spacer(Modifier.height(m.dp(10f)))
            Text(
                text = note,
                color = Gold.copy(alpha = 0.85f),
                fontSize = m.sp(14f),
                maxLines = 1,
            )
        }
    }
}

/** 当前筛选摘要："国语 · 大陆 · 共 128 部"（没有可说的就不显示，不摆废字） */
@Composable
private fun FilterSummary(filter: MovieFilter, total: Int) {
    val m = rememberTvMetrics()
    val parts = buildList {
        addAll(filter.activeParts)
        if (total > 0) add("共 $total 部")
    }
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString("  ·  "),
        color = TextSub,
        fontSize = m.sp(15f),
        maxLines = 1,
    )
}

// ── 海报墙 ───────────────────────────────────────────────────

/** 交错入场：每张延 30ms；只给前 12 张做（32 位电视不值当为第 20 张算动画） */
private const val ANIM_STAGGER_MS = 30L
private const val ANIM_MAX_ITEMS = 12

@Composable
private fun PosterGrid(
    movies: List<Movie>,
    loading: Boolean,
    endReached: Boolean,
    generation: Int,
    endPadding: Dp,
    needsSetup: Boolean,
    error: String?,
    onFocusMovie: (Movie) -> Unit,
    onClickMovie: (Movie) -> Unit,
    onNearEnd: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    onResetFilters: () -> Unit,
) {
    val m = rememberTvMetrics()
    val screenW = LocalConfiguration.current.screenWidthDp
    val columns = if (screenW >= 1400) 4 else 3
    val gridState = rememberLazyGridState()
    val firstFocus = remember { FocusRequester() }
    var autoFocused by remember { mutableStateOf(false) }

    // 这一批"已入场"的海报。key 是 generation：换筛选/换分类就重来，
    // 所以每次筛选变化都会看到一次整齐的交错入场。
    val entered = remember(generation) { mutableStateMapOf<String, Boolean>() }

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

    // 换筛选后回到顶部：否则会停在上一次的滚动位置，看起来像"没反应"
    LaunchedEffect(generation) {
        if (generation > 0) runCatching { gridState.scrollToItem(0) }
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
            CenterHint(
                title = "这个筛选下暂时没有片子",
                body = "换个分类，或者把语言 / 地区 / 年份放宽一点。" +
                    "（也可以直接重置成默认：全部分类 + 国语）",
                actionText = "重置筛选",
                onAction = onResetFilters,
            )
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
                    // 第一次出现在这个筛选下的海报：淡入 + 轻微放大，按顺序一张张出来
                    if (entered[movie.id] != true) {
                        LaunchedEffect(movie.id) {
                            delay(index.coerceAtMost(ANIM_MAX_ITEMS) * ANIM_STAGGER_MS)
                            entered[movie.id] = true
                        }
                    }
                    val shownIn = entered[movie.id] == true
                    val alpha by animateFloatAsState(
                        targetValue = if (shownIn) 1f else 0f,
                        animationSpec = Motion.base(),
                        label = "posterAlpha",
                    )
                    val scale by animateFloatAsState(
                        targetValue = if (shownIn) 1f else 0.92f,
                        animationSpec = Motion.base(),
                        label = "posterScale",
                    )

                    Box(
                        Modifier
                            // 列表增删/换筛选时，Compose 自己负责移动动画
                            .animateItem()
                            .graphicsLayer {
                                this.alpha = alpha
                                scaleX = scale
                                scaleY = scale
                            },
                    ) {
                        PosterCard(
                            movie = movie,
                            modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            onClick = { onClickMovie(movie) },
                            onFocused = { onFocusMovie(movie) },
                        )
                    }
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

    // 第一次拿到数据 → 焦点自动落到第一张海报：开机按 OK 就能看。
    // 只做一次：之后用户可能正在筛选条上调条件，绝不能把焦点抢走。
    LaunchedEffect(movies.isNotEmpty()) {
        if (movies.isEmpty() || autoFocused) return@LaunchedEffect
        delay(180)
        autoFocused = true
        runCatching { firstFocus.requestFocus() }
    }
}

/**
 * 居中提示卡（未配置片源 / 接口出错 / 筛选无结果时用）。
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
