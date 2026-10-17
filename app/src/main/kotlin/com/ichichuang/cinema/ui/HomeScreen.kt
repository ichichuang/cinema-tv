package com.ichichuang.cinema.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

/**
 * 极简影院 · 首页（阶段 0 骨架）
 *
 * 设计原则：一屏搞定。左边海报墙，右边常驻详情，永远不用"翻页"。
 * 遥控器：← → ↑ ↓ 移动焦点，OK 确认，分类条在最上方。
 */
@Composable
fun CinemaApp() {
    CinemaTheme {
        val m = rememberTvMetrics()
        val screenW = LocalConfiguration.current.screenWidthDp
        val panelWidth: Dp = (screenW * 0.30f).dp

        var category by remember { mutableStateOf(DEMO_CATEGORIES.first()) }
        var selected by remember { mutableStateOf(DEMO_MOVIES.first()) }
        var hint by remember { mutableStateOf<String?>(null) }

        val movies = remember(category) {
            if (category == "全部") DEMO_MOVIES else DEMO_MOVIES.filter { it.genre == category }
        }

        // 底部提示 2 秒后自动消失
        LaunchedEffect(hint) {
            if (hint != null) {
                delay(2000)
                hint = null
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Ink)
        ) {
            Column(Modifier.fillMaxSize()) {
                Header(category) { category = it }
                PosterGrid(
                    movies = movies,
                    endPadding = panelWidth + m.dp(28f),
                    onFocus = { selected = it },
                    onClick = { hint = "▶ 播放功能将在阶段 3 接入（现在先打磨界面）" },
                )
            }

            // 右侧常驻详情：内容随焦点变化，布局从不跳动
            DetailPanel(
                movie = selected,
                onPlay = { hint = "▶ 「${selected.title}」播放接入中，阶段 3 见" },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(panelWidth),
            )

            hint?.let {
                Text(
                    text = it,
                    color = Ink,
                    fontSize = m.sp(19f),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = m.dp(36f))
                        .clip(RoundedCornerShape(50))
                        .background(Gold.copy(alpha = 0.94f))
                        .padding(horizontal = m.dp(26f), vertical = m.dp(13f)),
                )
            }
        }
    }
}

// ── 顶栏：应用名 + 分类筛选条 ─────────────────────────────────

@Composable
private fun Header(category: String, onCategory: (String) -> Unit) {
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

        // 分类 chips：横向可滚动，焦点自动跟随
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DEMO_CATEGORIES.forEach { c ->
                Chip(
                    label = c,
                    selected = c == category,
                    onSelect = { onCategory(c) },
                )
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onSelect: () -> Unit) {
    val m = rememberTvMetrics()
    var focused by remember { mutableStateOf(false) }
    val p by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(140),
        label = "chipFocus",
    )
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
            .border(m.dp(2f), Gold.copy(alpha = if (selected) 0f else p), shape)
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

// ── 海报墙 ────────────────────────────────────────────────────

@Composable
private fun PosterGrid(
    movies: List<Movie>,
    endPadding: Dp,
    onFocus: (Movie) -> Unit,
    onClick: (Movie) -> Unit,
) {
    val m = rememberTvMetrics()
    val screenW = LocalConfiguration.current.screenWidthDp
    val columns = if (screenW >= 1400) 4 else 3
    val firstFocus = remember { FocusRequester() }

    if (movies.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("这个分类暂时没有内容", color = TextSub, fontSize = m.sp(22f))
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = m.dp(48f),
            end = endPadding,
            top = m.dp(8f),
            bottom = m.dp(60f),
        ),
        horizontalArrangement = Arrangement.spacedBy(m.dp(20f)),
        verticalArrangement = Arrangement.spacedBy(m.dp(26f)),
    ) {
        itemsIndexed(movies, key = { _, movie -> movie.id }) { index, movie ->
            PosterCard(
                movie = movie,
                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                onFocus = { onFocus(movie) },
                onClick = { onClick(movie) },
            )
        }
    }

    // 启动后自动把焦点放到第一张海报上：开机 → 直接就能按 OK
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { firstFocus.requestFocus() }
    }
}

@Composable
private fun PosterCard(
    movie: Movie,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    val m = rememberTvMetrics()
    var focused by remember { mutableStateOf(false) }
    val p by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(180),
        label = "cardFocus",
    )
    val shape = RoundedCornerShape(m.dp(14f))

    Column(
        modifier = modifier
            // 焦点放大 6%：这是"值钱感"的全部来源，其他地方全部克制
            .graphicsLayer {
                val s = 1f + 0.06f * p
                scaleX = s
                scaleY = s
            }
            .zIndex(if (focused) 1f else 0f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(Brush.linearGradient(listOf(movie.colorA, movie.colorB)))
                .border(m.dp(2f), Gold.copy(alpha = p), shape),
        ) {
            // 没有真实海报时的占位：片名首字，只有 16% 不透明度，安静但有质感
            Text(
                text = movie.title.take(1),
                color = Color.White.copy(alpha = 0.16f),
                fontSize = m.sp(76f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
            // 评分角标
            Text(
                text = "★ ${movie.score}",
                color = Gold,
                fontSize = m.sp(15f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(m.dp(10f))
                    .clip(RoundedCornerShape(m.dp(6f)))
                    .background(Ink.copy(alpha = 0.6f))
                    .padding(horizontal = m.dp(8f), vertical = m.dp(4f)),
            )
        }

        Spacer(Modifier.height(m.dp(10f)))
        Text(
            text = movie.title,
            color = TextMain,
            fontSize = m.sp(20f),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${movie.year} · ${movie.genre}",
            color = TextSub,
            fontSize = m.sp(16f),
            maxLines = 1,
        )
    }
}

// ── 右侧常驻详情面板 ──────────────────────────────────────────

@Composable
private fun DetailPanel(movie: Movie, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val m = rememberTvMetrics()
    val shape = RoundedCornerShape(m.dp(16f))

    Box(
        modifier = modifier.background(
            Brush.horizontalGradient(
                listOf(Color.Transparent, Ink.copy(alpha = 0.92f), Ink),
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(m.dp(34f)),
            verticalArrangement = Arrangement.Center,
        ) {
            // 海报（大字封面）
            Box(
                modifier = Modifier
                    .width(m.dp(170f))
                    .aspectRatio(2f / 3f)
                    .clip(shape)
                    .background(Brush.linearGradient(listOf(movie.colorA, movie.colorB))),
            ) {
                Text(
                    text = movie.title.take(1),
                    color = Color.White.copy(alpha = 0.2f),
                    fontSize = m.sp(64f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            Spacer(Modifier.height(m.dp(26f)))
            Text(
                text = movie.title,
                color = TextMain,
                fontSize = m.sp(32f),
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(m.dp(12f)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "★ ${movie.score}",
                    color = Gold,
                    fontSize = m.sp(19f),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "  ·  ${movie.year}  ·  ${movie.genre}  ·  ${movie.minutes} 分钟",
                    color = TextSub,
                    fontSize = m.sp(18f),
                )
            }
            Spacer(Modifier.height(m.dp(16f)))
            Text(
                text = movie.synopsis,
                color = Color(0xFFC7C7CC),
                fontSize = m.sp(17f),
                lineHeight = m.sp(28f),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(m.dp(32f)))
            PlayButton(onClick = onPlay)
        }
    }
}

@Composable
private fun PlayButton(onClick: () -> Unit) {
    val m = rememberTvMetrics()
    var focused by remember { mutableStateOf(false) }
    val p by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(160),
        label = "playFocus",
    )

    Row(
        modifier = Modifier
            .graphicsLayer {
                val s = 1f + 0.05f * p
                scaleX = s
                scaleY = s
            }
            .clip(RoundedCornerShape(50))
            .background(if (focused) Gold else Gold.copy(alpha = 0.88f))
            .border(m.dp(2f), Color.White.copy(alpha = 0.7f * p), RoundedCornerShape(50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = m.dp(38f), vertical = m.dp(16f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "▶",
            color = Ink,
            fontSize = m.sp(22f),
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(m.dp(14f)))
        Text(
            text = "播放",
            color = Ink,
            fontSize = m.sp(23f),
            fontWeight = FontWeight.Bold,
        )
    }
}
