package com.ichichuang.cinema.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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
import com.ichichuang.cinema.core.ui.CenterHint
import com.ichichuang.cinema.core.ui.DetailPanel
import com.ichichuang.cinema.core.ui.FilterChip
import com.ichichuang.cinema.core.ui.FilterGroup
import com.ichichuang.cinema.core.ui.PosterCard
import com.ichichuang.cinema.core.ui.PosterSkeleton
import com.ichichuang.cinema.domain.model.Movie
import com.ichichuang.cinema.domain.model.MovieFilter
import com.ichichuang.cinema.domain.repository.AREA_OPTIONS
import com.ichichuang.cinema.domain.repository.FILTER_ALL
import com.ichichuang.cinema.domain.repository.LANG_OPTIONS
import com.ichichuang.cinema.feature.update.UpdateCenter
import com.ichichuang.cinema.feature.update.UpdateOverlay
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
    onOpenSearch: () -> Unit,
    updateCenter: UpdateCenter,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val updateState by updateCenter.state.collectAsState()
    val m = rememberTvMetrics()
    val screenW = LocalConfiguration.current.screenWidthDp
    val panelWidth: Dp = (screenW * 0.30f).dp

    // 每次"上屏"都做两件事（首次进入、从播放器/设置返回都会走到这里）：
    //   1. 刷一次进度 → 播放按钮变成"继续观看 12:34"
    //   2. 请求把焦点还给上次看的那部片 → 右侧详情、列表位置、滚动位置全部原样回显
    LaunchedEffect(Unit) {
        viewModel.refreshSelectedProgress()
        viewModel.onHomeVisible()
    }

    // 起来先悄悄问一句有没有新版本（查不到就当没这回事，绝不打扰）
    LaunchedEffect(Unit) { updateCenter.checkSilently() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
    ) {
        // 底层内容（海报墙 + 右侧详情）：更新浮层打开时整块关掉焦点，
        // 否则遥控器会"透过"浮层跑到海报墙 / 详情按钮上，用户会以为遥控器坏了
        Box(
            Modifier
                .fillMaxSize()
                .then(if (updateState.panelOpen) Modifier.focusProperties { canFocus = false } else Modifier)
        ) {
            PosterGrid(
                movies = state.movies,
                loading = state.loading,
                endReached = state.endReached,
                generation = state.gridGeneration,
                focusTarget = state.focusTarget,
                endPadding = panelWidth + m.dp(28f),
                needsSetup = state.needsSetup,
                error = state.error,
                pageError = state.pageError,
                loadingMore = state.loadingMore,
                onFocusMovie = viewModel::onMovieFocused,
                onClickMovie = onPlay,
                onNearEnd = viewModel::onVisibleIndexChanged,
                onOpenSettings = onOpenSettings,
                onRetry = viewModel::retry,
                onResetFilters = viewModel::resetFilters,
                onFocusConsumed = viewModel::onFocusTargetConsumed,
                // 筛选条是网格的第一项 —— 往下翻它自己滚走，把整屏让给海报
                header = {
                    Header(
                        categories = state.categories,
                        filter = state.filter,
                        total = state.total,
                        relaxedNote = state.relaxedNote,
                        yearOptions = state.yearOptions,
                        updateVersion = updateState.info?.versionName,
                        onCategory = viewModel::selectCategory,
                        onLang = viewModel::selectLang,
                        onArea = viewModel::selectArea,
                        onYear = viewModel::selectYear,
                        onOpenUpdate = updateCenter::openPanel,
                        onOpenSettings = onOpenSettings,
                        onOpenSearch = onOpenSearch,
                    )
                },
            )

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

        // 更新浮层盖在整屏之上（打开时压暗背景，注意力收在卡片上）
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

// ── 顶部：品牌 + 分类（接口自动生成）+ 三个筛选维度 ─────────────

@Composable
private fun Header(
    categories: List<String>,
    filter: MovieFilter,
    total: Int,
    relaxedNote: String?,
    yearOptions: List<String>,
    updateVersion: String?,
    onCategory: (String) -> Unit,
    onLang: (String) -> Unit,
    onArea: (String) -> Unit,
    onYear: (String) -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
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
                // 轻微字距：大标题上这一点点"松"，是 Apple TV 那种高级感的来源之一
                letterSpacing = m.sp(1f),
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
            // 发现新版本时的小金标：不弹窗、不遮内容，用户好奇才点
            // 用"横向展开 + 淡入"而不是缩放：旁边还有"设置"药丸，展开动画才不会让它跳位
            AnimatedVisibility(
                visible = updateVersion != null,
                enter = expandHorizontally(expandFrom = Alignment.Start, animationSpec = Motion.base()) + fadeIn(Motion.base()),
                exit = shrinkHorizontally(shrinkTowards = Alignment.Start, animationSpec = Motion.quick()) + fadeOut(Motion.quick()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        label = "新版本 ${updateVersion.orEmpty()}",
                        selected = true,
                        onClick = onOpenUpdate,
                    )
                    Spacer(Modifier.width(m.dp(12f)))
                }
            }
            FilterChip(label = "搜索", selected = false, onClick = onOpenSearch)
            Spacer(Modifier.width(m.dp(12f)))
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

        // 筛条件被放宽时的说明（只在真发生时出现，平时完全不占地方）
        // 用高度展开 + 淡入，而不是"啪"地挤进来把海报墙整体顶一下
        AnimatedVisibility(
            visible = relaxedNote != null,
            enter = expandVertically(animationSpec = Motion.base()) + fadeIn(Motion.base()),
            exit = shrinkVertically(animationSpec = Motion.quick()) + fadeOut(Motion.quick()),
        ) {
            Column {
                Spacer(Modifier.height(m.dp(10f)))
                Text(
                    text = relaxedNote.orEmpty(),
                    color = Gold.copy(alpha = 0.85f),
                    fontSize = m.sp(14f),
                    maxLines = 1,
                )
            }
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
    // 换条件时"地区 / 年份 / 部数"会一起变，直接换字会跳；交叉淡入淡出更稳
    Crossfade(
        targetState = parts.joinToString("  ·  "),
        animationSpec = Motion.quick(),
        label = "filterSummary",
    ) { shown ->
        Text(
            text = shown,
            color = TextSub,
            fontSize = m.sp(15f),
            maxLines = 1,
        )
    }
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
    focusTarget: String?,
    endPadding: Dp,
    needsSetup: Boolean,
    error: String?,
    pageError: String?,
    loadingMore: Boolean,
    onFocusMovie: (Movie) -> Unit,
    onClickMovie: (Movie) -> Unit,
    onNearEnd: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    onResetFilters: () -> Unit,
    onFocusConsumed: () -> Unit,
    /** 网格的第一项（筛选条）。放进网格是为了让它"往下翻时自动让位"，把整屏留给海报 */
    header: @Composable () -> Unit,
) {
    val m = rememberTvMetrics()
    val screenW = LocalConfiguration.current.screenWidthDp
    val columns = if (screenW >= 1400) 4 else 3
    val gridState = rememberLazyGridState()

    // 每部片一个焦点请求器：恢复焦点时按 id 找它，而不是靠索引（索引会变，id 不会）
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    // 已处理过的恢复请求：同一个目标只抢一次焦点，绝不和用户的按键打架
    var handledTarget by remember { mutableStateOf<String?>(null) }
    // 上一次真正滚动到了哪个 generation（用于区分"换筛选"和"导航往返"）
    var scrolledGeneration by remember { mutableStateOf(generation) }

    /**
     * 这一批"已入场"的海报。key 是 generation：换筛选/换分类重来一次，
     * 所以每次筛选变化都会看到一次整齐的交错入场。
     *
     * ⚠️ 必须是 rememberSaveable：去播放器再回到首页，整个组合会被重建，
     *    普通 remember 会让整墙海报再"闪一次"入场动画 —— 那正是"回显没做好"的观感来源。
     */
    val entered = rememberSaveable(
        generation,
        saver = Saver<SnapshotStateMap<String, Boolean>, ArrayList<String>>(
            save = { map -> ArrayList(map.filterValues { it }.keys) },
            restore = { ids ->
                mutableStateMapOf<String, Boolean>().apply { ids.forEach { put(it, true) } }
            },
        ),
    ) { mutableStateMapOf<String, Boolean>() }

    val contentPadding = PaddingValues(
        start = m.dp(48f),
        end = endPadding,
        top = m.dp(0f),   // 头部自带内边距
        bottom = m.dp(60f),
    )

    // 网格里第 0 项是筛选条，所以「影片下标」=「网格 item 下标」- 1
    val headerItems = 1

    // 滚到接近底部 → 交给 ViewModel 决定要不要加载下一页
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { last -> if (last >= headerItems) onNearEnd(last - headerItems) }
    }

    // 换筛选后回到顶部：否则会停在上一次的滚动位置，看起来像"没反应"。
    // ⚠️ 只在"筛选真的变了"时做：导航往返会重建这个组合，那种情况要保持原位不动。
    LaunchedEffect(generation) {
        if (generation == scrolledGeneration) return@LaunchedEffect
        scrolledGeneration = generation
        runCatching { gridState.scrollToItem(0) }
    }

    // 提示类界面（未配置片源 / 接口出错 / 筛选无结果）：筛选条照旧在最上面 ——
    // 特别是"筛不到片"的时候，用户必须能立刻改筛选，绝不能把入口藏起来
    @Composable
    fun HintPage(title: String, body: String, actionText: String, onAction: () -> Unit) {
        Column(Modifier.fillMaxSize()) {
            header()
            Box(Modifier.weight(1f)) {
                CenterHint(title = title, body = body, actionText = actionText, onAction = onAction)
            }
        }
    }

    when {
        // 还没配置片源 → 引导去设置（这是第一次使用的正常状态，不是错误）
        needsSetup -> HintPage(
            title = "还没有配置影视接口地址",
            body = "应用不内置任何片源。去「设置」里填入你自己的苹果CMS接口地址，这里就会出现内容。" +
                "想先看看界面长什么样，地址栏里填 demo 即可。",
            actionText = "去设置",
            onAction = onOpenSettings,
        )

        // 接口出错（地址写错 / 站点挂了 / 网络不通）→ 说人话 + 可以重试
        error != null && movies.isEmpty() -> HintPage(
            title = "没能取到片源数据",
            body = error,
            actionText = "重试",
            onAction = onRetry,
        )

        loading && movies.isEmpty() -> {
            // 骨架屏：加载时是"微光占位"，绝不是白屏或转圈
            // 头部也一起占位 —— 数据到货时布局不会"跳一下"
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(m.dp(20f)),
                verticalArrangement = Arrangement.spacedBy(m.dp(26f)),
                userScrollEnabled = false,
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { header() }
                items(12) { PosterSkeleton() }
            }
        }

        movies.isEmpty() -> HintPage(
            title = "这个筛选下暂时没有片子",
            body = "换个分类，或者把语言 / 地区 / 年份放宽一点。" +
                "（也可以直接重置成默认：全部分类 + 国语）",
            actionText = "重置筛选",
            onAction = onResetFilters,
        )

        else -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(m.dp(20f)),
                verticalArrangement = Arrangement.spacedBy(m.dp(26f)),
            ) {
                // 筛选条是网格的第一项：往下翻时它自己滚出屏幕，海报墙因此多出一整行
                item(span = { GridItemSpan(maxLineSpan) }) { header() }

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
                            modifier = Modifier.focusRequester(
                                requesters.getOrPut(movie.id) { FocusRequester() },
                            ),
                            onClick = { onClickMovie(movie) },
                            onFocused = { onFocusMovie(movie) },
                        )
                    }
                }
                // 底部状态：三种情况都交代一句，不让用户对着"没反应的列表"发呆
                when {
                    pageError != null -> item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = m.dp(20f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = pageError,
                                color = Gold.copy(alpha = 0.85f),
                                fontSize = m.sp(15f),
                                maxLines = 1,
                            )
                        }
                    }

                    loadingMore -> item(span = { GridItemSpan(maxLineSpan) }) {
                        // 呼吸感：会"轻轻明灭"的加载提示，比一行死文字更像"正在做事"
                        val pulse = rememberInfiniteTransition(label = "loadingPulse")
                        val pulseAlpha by pulse.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 900, easing = Motion.EaseInOut),
                                repeatMode = RepeatMode.Reverse,
                            ),
                            label = "loadingAlpha",
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = m.dp(20f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "正在加载…",
                                color = TextSub.copy(alpha = pulseAlpha),
                                fontSize = m.sp(16f),
                            )
                        }
                    }

                    endReached -> item(span = { GridItemSpan(maxLineSpan) }) {
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

    // 焦点回位 —— 首页"操作回显"的核心：
    //   ① 有恢复请求（首次加载 / 从播放器、设置返回 / 应用重启后恢复）
    //      → 先滚到那部片，再把焦点交给它：右侧详情、列表位置原样回来
    //   ② 没有请求 → 什么都不做（用户可能正在筛选条上按，绝不能抢焦点）
    // 同一个目标只处理一次（handledTarget），处理完回报 ViewModel 清掉请求。
    LaunchedEffect(focusTarget, movies.size) {
        val target = focusTarget ?: return@LaunchedEffect
        if (movies.isEmpty() || handledTarget == target) return@LaunchedEffect
        handledTarget = target

        val index = movies.indexOfFirst { it.id == target }
        // 等一帧：让恢复出来的滚动位置先落地，再判断目标是不是已经可见
        delay(50)
        if (index >= 0) {
            // 已经看得到的就绝不滚动 —— 滚动位置本身就是"回显"的一部分，
            // 只要目标不在屏幕上（首次加载 / 翻了很多页）才需要把它带进来。
            val itemIndex = index + headerItems
            val alreadyVisible = gridState.layoutInfo.visibleItemsInfo.any { it.index == itemIndex }
            if (!alreadyVisible) {
                runCatching { gridState.scrollToItem(itemIndex) }
                delay(60)
            }
        }
        // 目标可能在屏幕外、也可能刚好被复用：给它几次机会，成功后立刻收工
        repeat(4) {
            val requester = requesters[target]
            if (requester != null && runCatching { requester.requestFocus() }.isSuccess) {
                onFocusConsumed()
                return@LaunchedEffect
            }
            delay(90)
        }
        onFocusConsumed()
    }
}
