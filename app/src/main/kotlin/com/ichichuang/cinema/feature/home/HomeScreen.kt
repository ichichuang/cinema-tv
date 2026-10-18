package com.ichichuang.cinema.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.ichichuang.cinema.core.ui.CenterHint
import com.ichichuang.cinema.core.ui.FilterChip
import com.ichichuang.cinema.core.ui.PosterCard
import com.ichichuang.cinema.core.ui.PosterSkeleton
import com.ichichuang.cinema.domain.model.DefaultMovieFilter
import com.ichichuang.cinema.domain.model.Movie
import com.ichichuang.cinema.domain.model.MovieFilter
import com.ichichuang.cinema.domain.repository.CATEGORY_ALL
import com.ichichuang.cinema.feature.update.UpdateCenter
import com.ichichuang.cinema.feature.update.UpdateOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 首页 —— 上下两个区，界限分明。
 *
 * ```
 * ┌──────────────────────────────────────────────┐
 * │ 极简影院                      新版本 · 设置 │ ← 固定高度
 * │ [筛选]  当前条件横向滚动…            共 N 部 │ ← 固定高度
 * │ （放宽提示：固定一行，没有内容也占着）      │
 * ├──────────────────────────────────────────────┤
 * │  海报墙（点击 → 详情页从右侧滑入）          │
 * └──────────────────────────────────────────────┘
 * ```
 *
 * 为什么改成这样（用户真机反馈）：
 * - 右侧常驻详情卡片太拥挤 → 直接去掉，点卡片进全屏详情页
 * - 筛选条一行太长、遥控器要按几十下 → 收进「筛选」按钮，点开是整屏浮层
 * - 顶部区和海报墙**各自固定**，任何动态内容（新版本金标、放宽提示、结果数）
 *   都在预留好的槽位里淡入淡出，不会顶动下面的排版
 *
 * 遥控器路径：海报墙 → 上 → 「筛选」 → 上 →「设置」。
 * 搜索功能已按用户要求整体移除（电视上打字是最糟的体验）。
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    updateCenter: UpdateCenter,
    onOpenDetail: (Movie) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val updateState by updateCenter.state.collectAsState()
    val m = rememberTvMetrics()

    var sheetOpen by remember { mutableStateOf(false) }
    val filterButtonFocus = remember { FocusRequester() }
    var sheetWasOpen by remember { mutableStateOf(false) }

    // 每次"上屏"都做两件事（首次进入、从详情/播放器/设置返回都会走到这里）：
    //   1. 刷一次进度（回来时按钮才知道该不该变成"继续观看"）
    //   2. 请求把焦点还给上次看的那部片 —— 滚动位置、焦点位置全部原样回显
    LaunchedEffect(Unit) {
        viewModel.refreshSelectedProgress()
        viewModel.onHomeVisible()
    }

    // 起来先悄悄问一句有没有新版本（查不到就当没这回事，绝不打扰）
    LaunchedEffect(Unit) { updateCenter.checkSilently() }

    // 筛选浮层关掉后，把焦点还给「筛选」按钮（用户就是从那儿进去的）
    LaunchedEffect(sheetOpen) {
        if (sheetOpen) {
            sheetWasOpen = true
        } else if (sheetWasOpen) {
            sheetWasOpen = false
            delay(80)
            runCatching { filterButtonFocus.requestFocus() }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink),
    ) {
        // 底层内容：浮层（筛选 / 更新）打开时整块关掉焦点，
        // 否则遥控器会"透过"浮层跑到海报墙上，用户会以为遥控器坏了
        Column(
            Modifier
                .fillMaxSize()
                .then(
                    if (updateState.panelOpen || sheetOpen) {
                        Modifier.focusProperties { canFocus = false }
                    } else {
                        Modifier
                    },
                ),
        ) {
            TopBar(
                filter = state.filter,
                total = state.total,
                relaxedNote = state.relaxedNote,
                updateVersion = updateState.info?.versionName,
                filterFocus = filterButtonFocus,
                onOpenSheet = { sheetOpen = true },
                onOpenSettings = onOpenSettings,
                onOpenUpdate = updateCenter::openPanel,
            )

            // ⚠️ 必须套一层带 weight 的 Box：Column 里的孩子不限定高度时会被当成
            //    "无限高"，而 LazyVerticalGrid 遇到无限高会直接抛异常（0.7.0 的坑）。
            //    这个 Box 把"剩余高度"卡死，海报墙才有确定尺寸。
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                PosterGrid(
                    movies = state.movies,
                    loading = state.loading,
                    endReached = state.endReached,
                    generation = state.gridGeneration,
                    focusTarget = state.focusTarget,
                    columns = state.columns,
                    needsSetup = state.needsSetup,
                    error = state.error,
                    pageError = state.pageError,
                    loadingMore = state.loadingMore,
                    onFocusMovie = viewModel::onMovieFocused,
                    onClickMovie = onOpenDetail,
                    onNearEnd = viewModel::onVisibleIndexChanged,
                    onOpenSettings = onOpenSettings,
                    onRetry = viewModel::retry,
                    onResetFilters = viewModel::resetFilters,
                    onFocusConsumed = viewModel::onFocusTargetConsumed,
                )
            }
        }

        // 筛选浮层：整屏从下往上滑入；选好按「返回」→ 自动生效并滑出
        FilterSheet(
            visible = sheetOpen,
            filter = state.filter,
            categories = state.categories,
            yearOptions = state.yearOptions,
            onApply = { next ->
                sheetOpen = false
                viewModel.applyFilter(next)
            },
        )

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

// ── 顶部固定区 ───────────────────────────────────────────────

/**
 * 顶部：两行 + 一行提示槽，**每一行都是写死高度的**。
 *
 * 所有"有时候有、有时候没有"的东西（新版本金标、放宽提示、结果数）都放在
 * 预留槽位里淡入淡出 —— 出现和消失都不会把下面的海报墙顶动一像素。
 */
@Composable
private fun TopBar(
    filter: MovieFilter,
    total: Int,
    relaxedNote: String?,
    updateVersion: String?,
    filterFocus: FocusRequester,
    onOpenSheet: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUpdate: () -> Unit,
) {
    val m = rememberTvMetrics()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = m.dp(48f), end = m.dp(48f), top = m.dp(26f)),
    ) {
        // ── 第一行：品牌（左） + 新版本金标 / 设置（右），高度固定 ──
        Row(
            Modifier
                .fillMaxWidth()
                .height(m.dp(46f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "极简影院",
                color = TextMain,
                fontSize = m.sp(28f),
                fontWeight = FontWeight.Bold,
                // 轻微字距：大标题上这一点点"松"，是 Apple TV 那种高级感的来源之一
                letterSpacing = m.sp(1f),
            )
            Spacer(Modifier.weight(1f))

            // 金标的固定槽位：没有新版本时它就是一段空白，绝不改变右边按钮的位置
            UpdateBadgeSlot(
                version = updateVersion,
                onClick = onOpenUpdate,
            )
            Spacer(Modifier.width(m.dp(14f)))
            FilterChip(label = "设置", selected = false, onClick = onOpenSettings)
        }

        Spacer(Modifier.height(m.dp(14f)))

        // ── 第二行：[筛选] 按钮 + 当前筛选条件（横向滚动展示）──
        Row(
            Modifier
                .fillMaxWidth()
                .height(m.dp(48f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SheetButton(
                onClick = onOpenSheet,
                active = filter != DefaultMovieFilter,
                modifier = Modifier.focusRequester(filterFocus),
            )
            Spacer(Modifier.width(m.dp(18f)))

            ActiveFilterStrip(
                filter = filter,
                modifier = Modifier.weight(1f),
            )

            Spacer(Modifier.width(m.dp(16f)))
            // 结果数的固定槽位（接口没给总数时就是空白）
            Box(
                Modifier.width(m.dp(130f)),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (total > 0) {
                    Crossfade(
                        targetState = total,
                        animationSpec = Motion.quick(),
                        label = "totalCount",
                    ) { shown ->
                        Text(
                            text = "共 $shown 部",
                            color = TextSub,
                            fontSize = m.sp(15f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(m.dp(8f)))

        // ── 放宽提示：固定一行高度的槽位（没有内容时也是这个高度）──
        RelaxedNoteSlot(note = relaxedNote)
    }
}

/**
 * 「新版本 xx」金标：固定宽度的槽位，有新版才亮出来。
 *
 * ⚠️ 单独抽成一个组件，而不是直接写在 Row 里：AnimatedVisibility 既有
 * 顶级版本、也有 RowScope/ColumnScope 版本，嵌在 Box 里调会被"隐式接收者"
 * 抢走导致编译不过 —— 抽出来（在这一层没有 Row/Column 接收者）最干净。
 */
@Composable
private fun UpdateBadgeSlot(version: String?, onClick: () -> Unit) {
    val m = rememberTvMetrics()
    Box(
        Modifier.width(m.dp(200f)),
        contentAlignment = Alignment.CenterEnd,
    ) {
        AnimatedVisibility(
            visible = version != null,
            enter = fadeIn(Motion.base()),
            exit = fadeOut(Motion.quick()),
        ) {
            FilterChip(
                label = "新版本 ${version.orEmpty()}",
                selected = true,
                onClick = onClick,
            )
        }
    }
}

/** 放宽筛选的说明：固定一行高度，出现/消失都不顶动下面的海报墙 */
@Composable
private fun RelaxedNoteSlot(note: String?) {
    val m = rememberTvMetrics()
    Box(
        Modifier
            .fillMaxWidth()
            .height(m.dp(22f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        AnimatedVisibility(
            visible = note != null,
            enter = fadeIn(Motion.base()),
            exit = fadeOut(Motion.quick()),
        ) {
            Text(
                text = note.orEmpty(),
                color = Gold.copy(alpha = 0.85f),
                fontSize = m.sp(14f),
                maxLines = 1,
            )
        }
    }
}

/** 「筛选」按钮：左侧的主入口。有任何非默认筛选时亮一颗小金点（槽位固定，不加宽按钮） */
@Composable
private fun SheetButton(
    onClick: () -> Unit,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val m = rememberTvMetrics()
    val interaction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }

    val bg by animateColorAsState(
        targetValue = if (focused) PanelHi else Panel,
        animationSpec = Motion.focus(),
        label = "sheetButtonBg",
    )
    val fg by animateColorAsState(
        targetValue = if (focused) Gold else TextMain,
        animationSpec = Motion.quick(),
        label = "sheetButtonFg",
    )

    Row(
        modifier = modifier
            .tvFocus(
                cornerRadius = m.dp(24f),
                interactionSource = interaction,
                onFocusChanged = { focused = it },
            )
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) { onClick() }
            .padding(horizontal = m.dp(26f), vertical = m.dp(12f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "筛选",
            color = fg,
            fontSize = m.sp(19f),
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(m.dp(10f)))
        // 小金点：位置和尺寸恒定，只切换颜色 —— 不会让按钮"长胖"
        Box(
            Modifier
                .size(m.dp(8f))
                .clip(CircleShape)
                .background(if (active) Gold else Color.Transparent),
        )
    }
}

/**
 * 当前筛选条件：横向滚动展示（分类 + 语言 + 地区 + 年份）。
 *
 * 刻意**不可聚焦**：它只是"我现在筛的是什么"的说明牌，
 * 所有修改都在筛选浮层里做。这样遥控器就少了一排要按过去的按钮 ——
 * 也顺手躲开了"横向滚动 + 焦点自动滚入"互相抖动那个坑。
 */
@Composable
private fun ActiveFilterStrip(
    filter: MovieFilter,
    modifier: Modifier = Modifier,
) {
    val m = rememberTvMetrics()
    val chips = buildList {
        if (filter.category != CATEGORY_ALL) add(filter.category)
        addAll(filter.activeParts)
    }
    val listState = rememberLazyListState()

    // 条件一变就平滑滚回开头（保证最左、最关心的那一条永远完整可见）
    LaunchedEffect(chips) {
        runCatching { listState.animateScrollToItem(0) }
    }

    LazyRow(
        state = listState,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(m.dp(10f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chips.isEmpty()) {
            item {
                Text(
                    text = "全部分类 · 不限语言 / 地区 / 年份",
                    color = TextSub.copy(alpha = 0.75f),
                    fontSize = m.sp(15f),
                    maxLines = 1,
                )
            }
        }
        items(chips) { label ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Panel)
                    .padding(horizontal = m.dp(16f), vertical = m.dp(8f)),
            ) {
                Text(
                    text = label,
                    color = TextMain.copy(alpha = 0.85f),
                    fontSize = m.sp(15f),
                    maxLines = 1,
                )
            }
        }
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
    columns: Int,
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
) {
    val m = rememberTvMetrics()
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
     * ⚠️ 必须是 rememberSaveable：去详情/播放器再回到首页，整个组合会被重建，
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

    // 每行留白的讲究：焦点高亮会把海报微微放大（1.06 倍）+ 一圈描边，
    // 间距必须大于"放大溢出的部分"，否则聚焦的海报会被切掉一条边。
    // （之前 3 列 + 20 间距时溢出比间距还大，就是你看到的"被截掉"。）
    val contentPadding = PaddingValues(
        start = m.dp(48f),
        end = m.dp(48f),
        top = m.dp(16f),
        bottom = m.dp(64f),
    )

    // 滚到接近底部 → 交给 ViewModel 决定要不要加载下一页
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { last -> if (last >= 0) onNearEnd(last) }
    }

    // 换筛选后回到顶部：否则会停在上一次的滚动位置，看起来像"没反应"。
    // ⚠️ 只在"筛选真的变了"时做：导航往返会重建这个组合，那种情况要保持原位不动。
    LaunchedEffect(generation) {
        if (generation == scrolledGeneration) return@LaunchedEffect
        scrolledGeneration = generation
        runCatching { gridState.scrollToItem(0) }
    }

    when {
        // 还没配置片源 → 引导去设置（这是第一次使用的正常状态，不是错误）
        needsSetup -> Box(Modifier.fillMaxSize()) {
            CenterHint(
                title = "还没有配置影视接口地址",
                body = "应用不内置任何片源。去「设置」→「扫码配置」，用手机扫一下码，把" +
                    "你自己的苹果CMS接口地址填进去，这里就会出现内容。" +
                    "想先看看界面长什么样，在手机上填 demo 即可。",
                actionText = "去设置",
                onAction = onOpenSettings,
            )
        }

        // 接口出错（地址写错 / 站点挂了 / 网络不通）→ 说人话 + 可以重试
        error != null && movies.isEmpty() -> Box(Modifier.fillMaxSize()) {
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
                horizontalArrangement = Arrangement.spacedBy(m.dp(24f)),
                verticalArrangement = Arrangement.spacedBy(m.dp(30f)),
                userScrollEnabled = false,
            ) {
                items(12) { PosterSkeleton() }
            }
        }

        movies.isEmpty() -> Box(Modifier.fillMaxSize()) {
            CenterHint(
                title = "这个筛选下暂时没有片子",
                body = "换个分类或类型，把语言 / 地区 / 年份放宽一点，" +
                    "也可以回到「筛选」里把条件点成「全部」。",
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
                horizontalArrangement = Arrangement.spacedBy(m.dp(24f)),
                verticalArrangement = Arrangement.spacedBy(m.dp(30f)),
            ) {
                itemsIndexed(movies, key = { _, movie -> movie.uniqueKey }) { index, movie ->
                    // 第一次出现在这个筛选下的海报：淡入 + 轻微放大，按顺序一张张出来
                    if (entered[movie.uniqueKey] != true) {
                        LaunchedEffect(movie.uniqueKey) {
                            delay(index.coerceAtMost(ANIM_MAX_ITEMS) * ANIM_STAGGER_MS)
                            entered[movie.uniqueKey] = true
                        }
                    }
                    val shownIn = entered[movie.uniqueKey] == true
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
                                requesters.getOrPut(movie.uniqueKey) { FocusRequester() },
                            ),
                            onClick = { onClickMovie(movie) },
                            onFocused = { onFocusMovie(movie) },
                        )
                    }
                }
                // 底部状态：三种情况都交代一句，不让用户对着"没反应的列表"发呆
                when {
                    pageError != null -> item {
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

                    loadingMore -> item {
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

                    endReached -> item {
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
    //   ① 有恢复请求（首次加载 / 从详情、播放器、设置返回 / 应用重启后恢复）
    //      → 先滚到那部片，再把焦点交给它：列表位置原样回来
    //   ② 没有请求 → 什么都不做（用户可能正在按筛选按钮，绝不能抢焦点）
    // 同一个目标只处理一次（handledTarget），处理完回报 ViewModel 清掉请求。
    LaunchedEffect(focusTarget, movies.size) {
        val target = focusTarget ?: return@LaunchedEffect
        if (movies.isEmpty() || handledTarget == target) return@LaunchedEffect
        handledTarget = target

        val index = movies.indexOfFirst { it.uniqueKey == target }
        // 等一帧：让恢复出来的滚动位置先落地，再判断目标是不是已经可见
        delay(50)
        if (index >= 0) {
            // 已经看得到的就绝不滚动 —— 滚动位置本身就是"回显"的一部分，
            // 只要目标不在屏幕上（首次加载 / 翻了很多页）才需要把它带进来。
            val alreadyVisible = gridState.layoutInfo.visibleItemsInfo.any { it.index == index }
            if (!alreadyVisible) {
                runCatching { gridState.scrollToItem(index) }
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
