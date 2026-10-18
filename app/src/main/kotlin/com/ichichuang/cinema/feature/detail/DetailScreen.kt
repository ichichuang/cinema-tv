package com.ichichuang.cinema.feature.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.ichichuang.cinema.core.design.Gold
import com.ichichuang.cinema.core.design.Ink
import com.ichichuang.cinema.core.design.Motion
import com.ichichuang.cinema.core.design.TextBody
import com.ichichuang.cinema.core.design.TextMain
import com.ichichuang.cinema.core.design.TextSub
import com.ichichuang.cinema.core.design.rememberTvMetrics
import com.ichichuang.cinema.core.focus.tvFocus
import com.ichichuang.cinema.core.ui.PosterImage
import com.ichichuang.cinema.domain.model.Movie
import com.ichichuang.cinema.domain.model.MovieDetail
import com.ichichuang.cinema.feature.home.HomeViewModel
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * 详情页 —— 从屏幕右侧滑入的**全屏沉浸页**。
 *
 * 三条硬规矩（用户真机反馈后定的）：
 * 1. **整页不滚动**。所有内容按固定版式落在可视区里：标题 / 一行元信息 /
 *    四行简介 / 播放按钮。内容到货前后，位置一格都不动。
 * 2. **固定尺寸容器**。简介的框先按四行留好、按钮宽度写死 ——
 *    详情、进度这些"晚到的数据"只会往里填字，不会把排版挤变形。
 * 3. **背景是主角**。整屏大背景 + 缓慢推近（Ken Burns）+ 三段渐变压暗，
 *    不用模糊（Android 12 以下不支持 Modifier.blur，模糊会"有的电视有、有的没有"）。
 *
 * v0.8.0：页面上**不再出现任何"线路 / 集数"这类普通观众看不懂的字** ——
 * 点「播放」就用一条线路直接看，要换线路去播放页的菜单里面换（那是懂的人才去的地方）。
 *
 * 数据不新开 ViewModel：详情/进度在首页卡片被聚焦时已经拉过了，
 * 这里直接读同一份状态 —— 所以 99% 的情况下点开详情是"瞬间有内容"的。
 */
@Composable
fun DetailScreen(
    movieId: String,
    title: String,
    posterUrl: String?,
    viewModel: HomeViewModel,
    onPlay: (Movie) -> Unit,
    onBack: () -> Unit,
) {
    val m = rememberTvMetrics()
    val state by viewModel.state.collectAsState()
    val playFocus = remember { FocusRequester() }

    BackHandler(onBack = onBack)

    // 冷启动直接恢复到详情页（进程被杀）时，列表还没回来 —— 让 ViewModel 补一次详情/进度
    LaunchedEffect(movieId) { viewModel.onDetailOpened(movieId, title, posterUrl) }

    // 优先用列表里的完整数据；实在没有就用路由参数把画面顶住（不空白）。
    // ⚠️ 多源聚合时两个站可能有同一个数字 id：先按「id + 标题」找，找不到再单按 id 找 ——
    // 否则点击 A 源的海报，详情页可能显示 B 源同名 id 的另一部片。
    val movie = state.movies.firstOrNull { it.id == movieId && it.title == title }
        ?: state.selected?.takeIf { it.id == movieId && it.title == title }
        ?: state.movies.firstOrNull { it.id == movieId }
        ?: state.selected?.takeIf { it.id == movieId }
        ?: Movie(
            id = movieId,
            title = title,
            year = 0,
            score = 0.0,
            genre = "",
            minutes = 0,
            synopsis = "",
            posterUrl = posterUrl,
        )
    val detail = state.detail?.takeIf { it.movie.id == movieId }
    val poster = movie.posterUrl ?: posterUrl

    // 焦点直接给播放按钮：点开详情就能按 OK 开播，少按几次遥控器
    LaunchedEffect(movieId) {
        delay(220)
        runCatching { playFocus.requestFocus() }
    }

    // 内容"跟着页面上浮"：整页转场负责横向滑入，这一层让文案块轻微上浮 ——
    // 两层叠起来才是"翻页感"，而不是一块板子平移过来。
    // 只走 graphicsLayer（alpha + 位移），不参与布局测量，任何字号下都不会挤乱版式。
    val contentIn = remember { Animatable(0f) }
    LaunchedEffect(movieId) { contentIn.animateTo(1f, Motion.enter()) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink),
    ) {
        // ── 背景大图：极慢推近，静止的画面"活"起来 ──
        val ken = rememberInfiniteTransition(label = "backdrop")
        val zoom by ken.animateFloat(
            initialValue = 1f,
            targetValue = 1.09f,
            animationSpec = infiniteRepeatable(
                animation = Motion.linear<Float>(24000),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "backdropZoom",
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                },
        ) {
            PosterImage(
                url = poster,
                title = movie.title,
                decodeWidth = 1280,
                decodeHeight = 720,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // ── 渐变压暗：字压在图上永远清楚（不依赖模糊，全机型一致）──
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Ink.copy(alpha = 0.32f),
                        0.42f to Ink.copy(alpha = 0.58f),
                        0.78f to Ink.copy(alpha = 0.94f),
                        1f to Ink,
                    ),
                ),
        )

        // ── 主内容：固定版式，绝无滚动；整体随进入轻微上浮 ──
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.62f)
                .graphicsLayer {
                    alpha = contentIn.value
                    translationY = (1f - contentIn.value) * m.dp(26f).toPx()
                }
                .padding(start = m.dp(56f), bottom = m.dp(46f)),
        ) {
            // 标题行高随内容（标题在进页面时就已经确定，不存在"晚到导致跳动"）
            Text(
                text = movie.title,
                color = TextMain,
                fontSize = m.sp(42f),
                fontWeight = FontWeight.Bold,
                letterSpacing = m.sp(0.5f),
                lineHeight = m.sp(54f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(m.dp(10f)))

            // 元信息：固定一行高度
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(m.dp(28f)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = metaLine(movie, detail),
                    color = TextSub,
                    fontSize = m.sp(17f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(m.dp(12f)))

            // 简介：先按四行留好位置（详情慢到几秒，位置也不会动）
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(m.dp(124f)),
                contentAlignment = Alignment.TopStart,
            ) {
                Text(
                    text = synopsisText(movie, detail),
                    color = TextBody,
                    fontSize = m.sp(17f),
                    lineHeight = m.sp(29f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(m.dp(26f)))

            DetailPlayButton(
                label = if (state.resumeMs > 0) "继续观看 " + formatMs(state.resumeMs) else "播放",
                focusRequester = playFocus,
                onClick = { onPlay(movie) },
            )
        }
    }
}

/** 一行元信息：★ 8.7 · 2024 · 大陆 · 国语 · 动作 · 120 分钟 */
private fun metaLine(movie: Movie, detail: MovieDetail?): String = listOfNotNull(
    movie.score.takeIf { it > 0 }?.let { "★ " + "%.1f".format(Locale.US, it) },
    movie.year.takeIf { it > 0 }?.toString(),
    (detail?.area?.takeIf { it.isNotBlank() } ?: movie.area.takeIf { it.isNotBlank() }),
    (detail?.lang?.takeIf { it.isNotBlank() } ?: movie.lang.takeIf { it.isNotBlank() }),
    movie.genre.takeIf { it.isNotBlank() },
    movie.minutes.takeIf { it > 0 }?.let { "$it 分钟" },
).joinToString("  ·  ")

private fun synopsisText(movie: Movie, detail: MovieDetail?): String =
    detail?.synopsis?.takeIf { it.isNotBlank() }
        ?: movie.synopsis.takeIf { it.isNotBlank() }
        ?: "简介还在路上，稍等一下就会出现…"

private fun formatMs(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val minute = total % 3600 / 60
    val second = total % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, minute, second)
    } else {
        "%d:%02d".format(minute, second)
    }
}

/**
 * 主按钮：**固定尺寸**（宽 320 / 高 66，缩放后）。
 *
 * 文案会在「播放」和「继续观看 12:34」之间变 —— 按钮框不变，只换字，
 * 所以进度数据到货时按钮不会"长大一点"，整个版面纹丝不动。
 */
@Composable
private fun DetailPlayButton(
    label: String,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    val m = rememberTvMetrics()
    val interaction = remember { MutableInteractionSource() }
    val radius = m.dp(33f)
    var focused by remember { mutableStateOf(false) }

    val bg by animateColorAsState(
        targetValue = if (focused) Gold else Gold.copy(alpha = 0.92f),
        animationSpec = Motion.quick(),
        label = "detailPlayBg",
    )

    Row(
        modifier = Modifier
            .width(m.dp(320f))
            .height(m.dp(66f))
            .focusRequester(focusRequester)
            .tvFocus(
                cornerRadius = radius,
                borderColor = Color.White.copy(alpha = 0.9f),
                interactionSource = interaction,
                onFocusChanged = { focused = it },
            )
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "▶",
            color = Ink,
            fontSize = m.sp(20f),
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(m.dp(12f)))
        Crossfade(
            targetState = label,
            animationSpec = Motion.quick(),
            label = "playLabel",
        ) { shown ->
            Text(
                text = shown,
                color = Ink,
                fontSize = m.sp(21f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
