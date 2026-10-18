package com.ichichuang.cinema.feature.player

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.ui.compose.PlayerSurface
import com.ichichuang.cinema.core.design.Gold
import com.ichichuang.cinema.core.design.Ink
import com.ichichuang.cinema.core.design.Motion
import com.ichichuang.cinema.core.design.Panel
import com.ichichuang.cinema.core.design.TextBody
import com.ichichuang.cinema.core.design.TextMain
import com.ichichuang.cinema.core.design.TextSub
import com.ichichuang.cinema.core.design.formatTime
import com.ichichuang.cinema.core.design.rememberTvMetrics
import com.ichichuang.cinema.core.focus.tvFocus
import com.ichichuang.cinema.data.settings.CinemaSettings
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

/**
 * 播放页。
 *
 * 设计原则（Apple TV 那套"克制"）：
 *  - 平时画面上**什么都不放**，只有一个隐形的键位层 —— 沉浸感就是这么来的
 *  - 一按键才浮出控件，4 秒后自己消失
 *  - 进度条是一根极细的金线，不做滑块（"按一下走一段"比拖滑块遥控友好得多）
 *  - 播放设置（步长 / 线路 / 选集）是底部升起的一块面板，不跳转新页面
 *
 * 遥控器键位（v0.8.0 定的"二维导航"）：
 *  - ←/→ = 时间轴：快退 / 快进（幅度 10/15/30/60 秒可选）
 *  - ↑/↓ = 剧集轴：上一集 / 下一集（单集影片自动降级为呼出 / 收起控件）
 *  - OK = 播放/暂停（长按 = 播放设置，兜底没有菜单键的遥控器）
 *  - 菜单键 = 播放设置面板；返回 = 收面板 / 退出
 */
@Composable
fun PlayerScreen(
    movieId: String,
    title: String,
    origin: String,
    onBack: () -> Unit,
    vm: PlayerViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsState()
    val m = rememberTvMetrics()
    val rootFocus = remember { FocusRequester() }

    // 上屏就开播；movieId 不变不会重复开始
    LaunchedEffect(movieId, origin) { vm.start(movieId, title, origin) }

    // 播放时不许熄屏（电视上最烦的就是看一半黑屏）
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            vm.pauseAndSave()
        }
    }

    // 切到后台（按 Home 键 / 去别的应用 / 电视待机）→ 自动暂停并记住进度。
    // 不做这一步的话：画面没了、声音还在响，用户会以为应用坏了。
    // 注意 onStop 不会销毁这个界面，所以要单独监听生命周期，光靠上面的 onDispose 不够。
    DisposableEffect(activity) {
        // ComponentActivity 同时是 LifecycleOwner；这里只关心"退到后台要暂停"
        val lifecycle = (activity as? LifecycleOwner)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vm.pauseAndSave()
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    // 返回键：面板开着先收面板，否则退出并落盘进度
    BackHandler {
        if (state.panelVisible) {
            vm.hidePanel()
        } else {
            vm.pauseAndSave()
            onBack()
        }
    }

    // 控件自动隐藏（每次有操作都会刷新时间戳 → 重新计时）；面板开着时不隐藏
    LaunchedEffect(state.controlsShownAt, state.panelVisible) {
        if (state.panelVisible) return@LaunchedEffect
        delay(4000)
        vm.hideControls()
    }

    // 临时提示几秒后自己消失（hintNonce 让"同一句提示连按多次"也能重新计时）
    LaunchedEffect(state.hint, state.hintNonce) {
        if (state.hint != null) {
            delay(3500)
            vm.clearHint()
        }
    }

    // 面板收起后把焦点还给根层，否则方向键会乱跑
    LaunchedEffect(state.panelVisible, state.error) {
        if (!state.panelVisible && state.error == null) {
            runCatching { rootFocus.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                // 菜单键：呼出/收起播放设置（面板开着也要能按它收）。
                // 错误浮层盖在上面时不响应 —— 否则面板会"悄悄打开"在错误层背后。
                if (e.key == Key.Menu) {
                    if (state.error == null) vm.togglePanel()
                    return@onPreviewKeyEvent true
                }

                // 面板打开时，方向键交给面板自己处理，只保留返回
                if (state.panelVisible) return@onPreviewKeyEvent false

                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.Spacebar, Key.NumPadEnter -> {
                        // 长按 OK = 呼出播放设置（有些遥控器的"菜单"键映射不出来，这是兜底）
                        if (e.nativeKeyEvent.repeatCount == 0) vm.togglePlay() else vm.togglePanel()
                        true
                    }

                    Key.DirectionLeft -> {
                        vm.seekBackward(); true
                    }

                    Key.DirectionRight -> {
                        vm.seekForward(); true
                    }

                    // 上下 = 剧集轴：上一集 / 下一集；单集影片没有剧集轴，降级成显示/隐藏控件
                    Key.DirectionUp -> {
                        if (state.hasEpisodes) vm.prevEpisode() else vm.showControls()
                        true
                    }

                    Key.DirectionDown -> {
                        if (state.hasEpisodes) vm.nextEpisode() else vm.hideControls()
                        true
                    }

                    Key.MediaPlayPause -> {
                        vm.togglePlay(); true
                    }

                    Key.MediaNext -> {
                        if (state.hasEpisodes) vm.nextEpisode()
                        true
                    }

                    Key.MediaPrevious -> {
                        if (state.hasEpisodes) vm.prevEpisode()
                        true
                    }

                    else -> false
                }
            }
            .focusable(),
    ) {
        // ① 画面本体
        PlayerSurface(
            player = vm.player,
            modifier = Modifier.fillMaxSize(),
        )

        // ② 缓冲转圈（只在等缓冲时出现，且做得小一点、暗一点）—— 淡入淡出，别"闪"一下
        AnimatedVisibility(
            visible = state.buffering && state.error == null,
            enter = fadeIn(Motion.base()),
            exit = fadeOut(Motion.base()),
            modifier = Modifier.align(Alignment.Center),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.width(m.dp(46f)),
                color = Gold,
                strokeWidth = m.dp(3f),
            )
        }

        // ③ 顶部信息（标题 + 当前集）—— 淡入 + 从上轻轻落下。面板开着时收起来
        AnimatedVisibility(
            visible = state.controlsVisible && !state.panelVisible && state.error == null,
            enter = fadeIn(Motion.base()) + slideInVertically(Motion.base()) { -it / 5 },
            exit = fadeOut(Motion.quick()) + slideOutVertically(Motion.quick()) { -it / 5 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Ink.copy(alpha = 0.92f), Color.Transparent),
                        ),
                    )
                    .padding(horizontal = m.dp(56f), vertical = m.dp(30f)),
            ) {
                Text(
                    text = state.title.ifBlank { title },
                    color = TextMain,
                    fontSize = m.sp(30f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 只有多集影片才显示"第几集"（电影显示这个反而是噪音）
                if (state.hasEpisodes && state.episodeName.isNotBlank()) {
                    Spacer(Modifier.height(m.dp(6f)))
                    Text(
                        text = state.episodeName,
                        color = TextSub,
                        fontSize = m.sp(16f),
                    )
                }
            }
        }

        // ④ 底部进度 + 键位提示 —— 淡入 + 从下轻轻升起
        AnimatedVisibility(
            visible = state.controlsVisible && !state.panelVisible && state.error == null,
            enter = fadeIn(Motion.base()) + slideInVertically(Motion.base()) { it / 5 },
            exit = fadeOut(Motion.quick()) + slideOutVertically(Motion.quick()) { it / 5 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ProgressBar(state = state)
        }

        // ⑤ 临时提示（快进多少秒 / 上一集下一集 / 自动换线路…）：
        //    淡入淡出 + 轻微上浮，不是"啪"地出现又消失
        //    退出时 state.hint 已经变 null，所以留住最后一句文案，否则淡出没内容可放
        var lastHint by remember { mutableStateOf("") }
        LaunchedEffect(state.hint) { state.hint?.let { lastHint = it } }
        AnimatedVisibility(
            visible = state.hint != null,
            enter = fadeIn(Motion.base()) + slideInVertically(Motion.base()) { it / 2 },
            exit = fadeOut(Motion.quick()),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = m.dp(
                        if (state.controlsVisible && !state.panelVisible) 170f else 60f,
                    ),
                ),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(m.dp(10f)))
                    .background(Ink.copy(alpha = 0.86f))
                    .border(
                        width = m.dp(1f),
                        color = Gold.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(m.dp(10f)),
                    )
                    .padding(horizontal = m.dp(22f), vertical = m.dp(12f)),
            ) {
                Text(
                    text = lastHint,
                    color = TextBody,
                    fontSize = m.sp(16f),
                )
            }
        }

        // ⑥ 播放设置面板（步长 / 线路 / 选集）
        AnimatedVisibility(
            visible = state.panelVisible,
            enter = fadeIn(Motion.base()) + slideInVertically(Motion.enter()) { it / 3 },
            exit = fadeOut(Motion.quick()) + slideOutVertically(Motion.quick()) { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayPanel(
                state = state,
                onSeekStep = vm::setSeekStep,
                onLine = vm::selectLine,
                onEpisode = vm::selectEpisode,
            )
        }

        // ⑦ 错误（说人话 + 告诉用户下一步按什么）：淡入 + 轻微放大，不是"拍"在用户脸上
        var lastError by remember { mutableStateOf("") }
        LaunchedEffect(state.error) { state.error?.let { lastError = it } }
        AnimatedVisibility(
            visible = state.error != null,
            enter = fadeIn(Motion.base()) + scaleIn(initialScale = 0.96f, animationSpec = Motion.base()),
            exit = fadeOut(Motion.quick()),
        ) {
            ErrorOverlay(
                message = lastError,
                canSwitchLine = state.lines.size > 1,
                onRetry = vm::retry,
                onSwitchLine = {
                    vm.selectLine((state.lineIndex + 1) % state.lines.size.coerceAtLeast(1))
                },
            )
        }

        // ⑧ 加载中（取详情这几百毫秒）：淡入，别让用户盯着一块突然变黑的屏
        AnimatedVisibility(
            visible = state.loading,
            enter = fadeIn(Motion.base()),
            exit = fadeOut(Motion.quick()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Ink.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "正在获取播放地址…",
                    color = TextSub,
                    fontSize = m.sp(18f),
                )
            }
        }
    }
}

/** 底部：一根金线 + 两侧时间 + 一行键位提示 */
@Composable
private fun ProgressBar(state: PlayerUiState) {
    val m = rememberTvMetrics()
    val target = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    // 位置 500ms 才上报一次，直接跟着跳会"一抽一抽"；
    // 用等长线性插值补上中间的过程，看着才像真的在走。
    val fraction by animateFloatAsState(
        targetValue = target,
        animationSpec = Motion.linear(460),
        label = "progressFraction",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Ink.copy(alpha = 0.94f)),
                ),
            )
            .padding(horizontal = m.dp(56f), vertical = m.dp(28f)),
    ) {
        // 进度轨道
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(m.dp(4f))
                .clip(RoundedCornerShape(m.dp(2f)))
                .background(Color.White.copy(alpha = 0.18f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(m.dp(4f))
                    .clip(RoundedCornerShape(m.dp(2f)))
                    .background(Gold),
            )
        }

        Spacer(Modifier.height(m.dp(12f)))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (state.playing) "⏸  正在播放" else "▶  已暂停",
                color = TextMain,
                fontSize = m.sp(16f),
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(m.dp(18f)))
            Text(
                text = formatTime(state.positionMs),
                color = TextSub,
                fontSize = m.sp(16f),
            )
            if (state.durationMs > 0) {
                Text(
                    text = " / ${formatTime(state.durationMs)}",
                    color = TextSub.copy(alpha = 0.7f),
                    fontSize = m.sp(16f),
                )
            }
            Spacer(Modifier.weight(1f))
            // 键位提示跟着实际能力走：多集影片底下提示的是"上/下一集"，
            // 单集影片提示"显示/隐藏"—— 提示永远和按键真的会做的事一致
            Text(
                text = buildString {
                    append("←→ 快退/快进 ").append(state.seekStepMs / 1000).append(" 秒")
                    append("    ")
                    append(if (state.hasEpisodes) "↑↓ 上一集 / 下一集" else "↑↓ 显示 / 隐藏控件")
                    append("    菜单 播放设置")
                },
                color = TextSub.copy(alpha = 0.75f),
                fontSize = m.sp(14f),
            )
        }
    }
}

/**
 * 播放设置面板（底部升起）：**步长 / 线路 / 选集** 三块。
 *
 * 高度是"按内容形态算死的"：有哪些块在打开时就确定 ——
 * 步长永远有、线路多于一条才有、多集影片才有选集 ——
 * 打开期间做什么操作（换步长、换线路）都不会改变面板高度，排版纹丝不动。
 * 选集网格用剩余高度 + 内部滚动：200 集也不怕。
 */
@Composable
private fun PlayPanel(
    state: PlayerUiState,
    onSeekStep: (Long) -> Unit,
    onLine: (Int) -> Unit,
    onEpisode: (Int) -> Unit,
) {
    val m = rememberTvMetrics()
    val gridState = rememberLazyGridState()
    val episodeFocus = remember { FocusRequester() }
    val stepFocus = remember { FocusRequester() }

    val showLines = state.lines.size > 1
    val showEpisodes = state.episodes.size > 1

    // 打开时：多集影片滚到当前集、把焦点放上去（不用手忙脚乱找"看的是第几集"）；
    // 否则把焦点放在当前步长那档上
    LaunchedEffect(state.panelVisible) {
        if (!state.panelVisible) return@LaunchedEffect
        delay(120)
        if (showEpisodes) runCatching { gridState.scrollToItem(state.episodeIndex) }
        repeat(6) {
            val ok = runCatching {
                if (showEpisodes) episodeFocus.requestFocus() else stepFocus.requestFocus()
            }.isSuccess
            if (ok) return@LaunchedEffect
            delay(80)
        }
    }

    // 高度按内容形态算死（打开期间任何操作都不变）。间距里留足了"焦点放大 + 光晕"
    // 的呼吸空间：总计最高 540 < 画布 562，不会被屏幕上下裁掉。
    val panelHeight = m.dp(
        182f +
            (if (showLines) 108f else 0f) +
            (if (showEpisodes) 250f else 0f),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(panelHeight)
            .clip(RoundedCornerShape(topStart = m.dp(22f), topEnd = m.dp(22f)))
            .background(Panel.copy(alpha = 0.97f))
            .padding(horizontal = m.dp(56f), vertical = m.dp(26f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(m.dp(36f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "播放设置",
                color = TextMain,
                fontSize = m.sp(22f),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "按「返回」收起",
                color = TextSub,
                fontSize = m.sp(14f),
            )
        }

        Spacer(Modifier.height(m.dp(14f)))

        // ── 快进 / 快退的幅度 ──
        PanelLabel("快退 / 快进一次走多远")
        Spacer(Modifier.height(m.dp(10f)))
        Row(horizontalArrangement = Arrangement.spacedBy(m.dp(14f))) {
            CinemaSettings.SEEK_STEP_OPTIONS.forEach { step ->
                val selected = step == state.seekStepMs
                PanelChip(
                    text = (step / 1000).toString() + " 秒",
                    selected = selected,
                    modifier = if (selected) Modifier.focusRequester(stepFocus) else Modifier,
                    onClick = { onSeekStep(step) },
                )
            }
        }

        // ── 线路（多于一条才有意义）──
        if (showLines) {
            Spacer(Modifier.height(m.dp(16f)))
            PanelLabel("线路")
            Spacer(Modifier.height(m.dp(10f)))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(m.dp(14f)),
                // 6px 内边距：高亮药丸会微微放大加描边，LazyRow 会在自己的边界处把
                // 放大出来的部分裁掉 —— 留出这块空间，聚焦时才不会"被切一条边"
                contentPadding = PaddingValues(vertical = m.dp(6f)),
            ) {
                itemsIndexed(state.lines) { index, line ->
                    PanelChip(
                        text = line.name,
                        selected = index == state.lineIndex,
                        onClick = { onLine(index) },
                    )
                }
            }
        }

        // ── 选集 ──
        if (showEpisodes) {
            Spacer(Modifier.height(m.dp(16f)))
            PanelLabel("选集")
            Spacer(Modifier.height(m.dp(10f)))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = m.dp(88f)),
                state = gridState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                // 同上：给聚焦放大的格子留呼吸空间，边缘的高亮不会被裁
                contentPadding = PaddingValues(m.dp(4f)),
                horizontalArrangement = Arrangement.spacedBy(m.dp(12f)),
                verticalArrangement = Arrangement.spacedBy(m.dp(12f)),
            ) {
                itemsIndexed(state.episodes) { index, ep ->
                    val isCurrent = index == state.episodeIndex
                    EpisodeCell(
                        name = ep.name,
                        isCurrent = isCurrent,
                        modifier = if (isCurrent) Modifier.focusRequester(episodeFocus) else Modifier,
                        onClick = { onEpisode(index) },
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

/** 面板里的小节标题（固定一行高度） */
@Composable
private fun PanelLabel(text: String) {
    val m = rememberTvMetrics()
    Box(
        Modifier
            .fillMaxWidth()
            .height(m.dp(24f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = text, color = TextSub, fontSize = m.sp(15f))
    }
}

/** 面板里的胶囊按钮（步长 / 线路共用） */
@Composable
private fun PanelChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val m = rememberTvMetrics()
    val radius = m.dp(10f)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .tvFocus(radius, interactionSource = interaction)
            .clip(RoundedCornerShape(radius))
            .background(if (selected) Gold.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f))
            // 电视上没有水波纹（那是手机的语言）：关掉 indication，改由 tvFocus 给按压反馈
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) { onClick() }
            .padding(horizontal = m.dp(20f), vertical = m.dp(12f)),
    ) {
        Text(
            text = text,
            color = if (selected) Gold else TextBody,
            fontSize = m.sp(16f),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun EpisodeCell(
    name: String,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val m = rememberTvMetrics()
    val radius = m.dp(10f)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .tvFocus(radius, borderColor = Gold, interactionSource = interaction)
            .clip(RoundedCornerShape(radius))
            .background(
                if (isCurrent) Gold.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.07f),
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) { onClick() }
            .padding(horizontal = m.dp(12f), vertical = m.dp(14f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name,
            color = if (isCurrent) Gold else TextMain,
            fontSize = m.sp(15f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 错误面板：人话 + 明确的下一步（换线路 / 重试） */
@Composable
private fun ErrorOverlay(
    message: String,
    canSwitchLine: Boolean,
    onRetry: () -> Unit,
    onSwitchLine: () -> Unit,
) {
    val m = rememberTvMetrics()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(m.dp(620f))
                .clip(RoundedCornerShape(m.dp(18f)))
                .background(Panel)
                .padding(m.dp(36f)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "播不了",
                color = TextMain,
                fontSize = m.sp(26f),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(m.dp(12f)))
            Text(
                text = message,
                color = TextBody,
                fontSize = m.sp(17f),
            )
            Spacer(Modifier.height(m.dp(28f)))
            Row(horizontalArrangement = Arrangement.spacedBy(m.dp(16f))) {
                ActionButton(text = "重试", primary = true, onClick = onRetry)
                if (canSwitchLine) {
                    ActionButton(text = "换线路", primary = false, onClick = onSwitchLine)
                }
            }
            Spacer(Modifier.height(m.dp(18f)))
            Text(
                text = "按「返回」回首页",
                color = TextSub.copy(alpha = 0.8f),
                fontSize = m.sp(14f),
            )
        }
    }
}

@Composable
private fun ActionButton(text: String, primary: Boolean, onClick: () -> Unit) {
    val m = rememberTvMetrics()
    val radius = m.dp(12f)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .tvFocus(
                cornerRadius = radius,
                borderColor = if (primary) Color.White.copy(alpha = 0.9f) else Gold,
                interactionSource = interaction,
            )
            .clip(RoundedCornerShape(radius))
            .background(if (primary) Gold else Color.White.copy(alpha = 0.1f))
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) { onClick() }
            .padding(horizontal = m.dp(30f), vertical = m.dp(14f)),
    ) {
        Text(
            text = text,
            color = if (primary) Ink else TextMain,
            fontSize = m.sp(18f),
            fontWeight = FontWeight.SemiBold,
        )
    }
}
