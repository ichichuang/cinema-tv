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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
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
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

/**
 * 播放页。
 *
 * 设计原则（Apple TV 那套"克制"）：
 *  - 平时画面上**什么都不放**，只有一个隐形的键位层 —— 沉浸感就是这么来的
 *  - 一按键才浮出控件，4 秒后自己消失
 *  - 进度条是一根极细的金线，不做滑块（电视上用遥控器拖进度并不好用，
 *    ←/→ 快退快进 10 秒更顺手）
 *  - 线路/选集是底部升起的一块面板，不跳转新页面
 *
 * 遥控器键位（全电视通用习惯）：
 *  OK = 播放/暂停 · ←/→ = 快退/快进 10 秒 · ↑/↓ = 选集与线路 · 返回 = 退出
 */
@Composable
fun PlayerScreen(
    movieId: String,
    title: String,
    onBack: () -> Unit,
    vm: PlayerViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsState()
    val m = rememberTvMetrics()
    val rootFocus = remember { FocusRequester() }

    // 上屏就开播；movieId 不变不会重复开始
    LaunchedEffect(movieId) { vm.start(movieId, title) }

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
        if (state.listVisible) vm.hideList() else {
            vm.pauseAndSave()
            onBack()
        }
    }

    // 控件自动隐藏（每次有操作都会刷新时间戳 → 重新计时）
    LaunchedEffect(state.controlsShownAt, state.listVisible) {
        if (state.listVisible) return@LaunchedEffect
        delay(4000)
        vm.hideControls()
    }

    // 临时提示几秒后自己消失
    LaunchedEffect(state.hint) {
        if (state.hint != null) {
            delay(3500)
            vm.clearHint()
        }
    }

    // 面板收起后把焦点还给根层，否则方向键会乱跑
    LaunchedEffect(state.listVisible, state.error) {
        if (!state.listVisible && state.error == null) {
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
                // 面板打开时，方向键交给面板自己处理，只保留返回
                if (state.listVisible) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.Spacebar, Key.NumPadEnter -> {
                        vm.togglePlay(); true
                    }
                    Key.DirectionLeft -> {
                        vm.seekBy(-10_000); true
                    }
                    Key.DirectionRight -> {
                        vm.seekBy(10_000); true
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
                        vm.toggleList(); true
                    }
                    Key.MediaPlayPause -> {
                        vm.togglePlay(); true
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

        // ③ 顶部信息（标题 + 当前线路/集）—— 淡入 + 从上轻轻落下
        AnimatedVisibility(
            visible = state.controlsVisible && state.error == null,
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
                if (state.episodeName.isNotBlank() || state.hasLines) {
                    Spacer(Modifier.height(m.dp(6f)))
                    Text(
                        text = buildString {
                            state.lines.getOrNull(state.lineIndex)?.name?.let { append(it) }
                            if (state.episodeName.isNotBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append(state.episodeName)
                            }
                        },
                        color = TextSub,
                        fontSize = m.sp(16f),
                    )
                }
            }
        }

        // ④ 底部进度 + 键位提示 —— 淡入 + 从下轻轻升起
        AnimatedVisibility(
            visible = state.controlsVisible && state.error == null,
            enter = fadeIn(Motion.base()) + slideInVertically(Motion.base()) { it / 5 },
            exit = fadeOut(Motion.quick()) + slideOutVertically(Motion.quick()) { it / 5 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ProgressBar(state = state)
        }

        // ⑤ 临时提示（自动换线路、已续播…）：淡入淡出 + 轻微上浮，不是"啪"地出现又消失
        //    退出时 state.hint 已经变 null，所以留住最后一句文案，否则淡出没内容可放
        //    （在副作用里写，绝不在组合期写状态）
        var lastHint by remember { mutableStateOf("") }
        LaunchedEffect(state.hint) { state.hint?.let { lastHint = it } }
        AnimatedVisibility(
            visible = state.hint != null,
            enter = fadeIn(Motion.base()) + slideInVertically(Motion.base()) { it / 2 },
            exit = fadeOut(Motion.quick()),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = m.dp(if (state.controlsVisible) 170f else 60f)),
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

        // ⑥ 选集 / 线路面板
        AnimatedVisibility(
            visible = state.listVisible,
            enter = fadeIn(Motion.base()) + slideInVertically(Motion.enter()) { it / 3 },
            exit = fadeOut(Motion.quick()) + slideOutVertically(Motion.quick()) { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            EpisodePanel(state = state, onLine = vm::selectLine, onEpisode = vm::selectEpisode)
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
            Text(
                text = "OK 播放/暂停    ←→ 快退快进 10 秒    ↑↓ 选集与线路",
                color = TextSub.copy(alpha = 0.75f),
                fontSize = m.sp(14f),
            )
        }
    }
}

/** 底部升起的选集面板：上排线路，下面集数网格 */
@Composable
private fun EpisodePanel(
    state: PlayerUiState,
    onLine: (Int) -> Unit,
    onEpisode: (Int) -> Unit,
) {
    val m = rememberTvMetrics()
    val gridState = rememberLazyGridState()
    val episodeFocus = remember { FocusRequester() }

    // 打开时滚到当前集并把焦点放上去（不用手忙脚乱找"看的是第几集"）
    LaunchedEffect(state.listVisible) {
        if (!state.listVisible) return@LaunchedEffect
        delay(60)
        runCatching {
            if (state.episodes.isNotEmpty()) {
                gridState.scrollToItem(state.episodeIndex)
                episodeFocus.requestFocus()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = m.dp(22f), topEnd = m.dp(22f)))
            .background(Panel.copy(alpha = 0.97f))
            .padding(horizontal = m.dp(56f), vertical = m.dp(26f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "选集与线路",
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

        // 线路（多于一条才有意义）
        if (state.lines.size > 1) {
            Spacer(Modifier.height(m.dp(16f)))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(m.dp(12f))) {
                itemsIndexed(state.lines) { index, line ->
                    val selected = index == state.lineIndex
                    LineChip(
                        text = "${line.name} · ${line.episodes.size} 集",
                        selected = selected,
                        onClick = { onLine(index) },
                    )
                }
            }
        }

        // 集数
        if (state.episodes.isNotEmpty()) {
            Spacer(Modifier.height(m.dp(18f)))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = m.dp(88f)),
                state = gridState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(m.dp(300f)),
                horizontalArrangement = Arrangement.spacedBy(m.dp(10f)),
                verticalArrangement = Arrangement.spacedBy(m.dp(10f)),
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
        }
    }
}

@Composable
private fun LineChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val m = rememberTvMetrics()
    val radius = m.dp(10f)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
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
