package com.ichichuang.cinema.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.ichichuang.cinema.core.design.Gold
import com.ichichuang.cinema.core.design.Ink
import com.ichichuang.cinema.core.design.Motion
import com.ichichuang.cinema.core.design.TextBody
import com.ichichuang.cinema.core.design.TextMain
import com.ichichuang.cinema.core.design.TextSub
import com.ichichuang.cinema.core.design.formatTime
import com.ichichuang.cinema.core.design.rememberTvMetrics
import com.ichichuang.cinema.domain.model.Movie
import com.ichichuang.cinema.domain.model.MovieDetail
import java.util.Locale

/**
 * 右侧常驻详情面板。
 *
 * 这是"极简"的关键设计：详情不做成单独页面，而是常驻在右侧，
 * 内容随焦点实时变化 —— 布局永远不跳，用户永远不用"进详情再退出来"。
 *
 * 数据有两层：列表给的简表立刻显示（标题/年份/评分），
 * 详情接口给的简介稍后到货（detail 从 null 变成有值，界面自然补全）。
 *
 * 换片是**淡出淡入**（不是硬切）：遥控器扫过一排海报时，
 * 右侧像"换镜头"一样过渡 —— 硬切会让扫视过程显得很躁。
 */
@Composable
fun DetailPanel(
    movie: Movie,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    detail: MovieDetail? = null,
    detailLoading: Boolean = false,
    resumeMs: Long = 0L,
) {
    val m = rememberTvMetrics()
    val shape = RoundedCornerShape(m.dp(16f))
    val scroll = rememberScrollState()

    // 过渡期间新旧两份内容要同时渲染，所以各留一份按 id 的快照：
    // 直接用外层的 movie/detail，两份内容会都是新片，动画看着像"没动"。
    val movieById = remember { mutableMapOf<String, Movie>() }
    val detailById = remember { mutableMapOf<String, MovieDetail?>() }
    movieById[movie.id] = movie
    detailById[movie.id] = detail
    // 32 位电视内存紧：只留最近 64 部（当前这部永远不动）
    if (movieById.size > 64) {
        movieById.keys.filter { it != movie.id }
            .take(movieById.size - 64)
            .forEach { stale ->
                movieById.remove(stale)
                detailById.remove(stale)
            }
    }

    // 换片 → 回到顶部。不做这一步的话，新片的简介会"接着上一部片的滚动位置"显示，
    // 看起来像内容加载错了 —— 这是最容易被忽略、又最容易被察觉的细节。
    LaunchedEffect(movie.id) { runCatching { scroll.scrollTo(0) } }

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
                // 可滚动是**安全网**：简介长一点、电视字号大一点、屏幕矮一点，
                // 内容都不会被裁在屏幕外。重点：播放按钮永远能滚到（它是可聚焦的，焦点会带它进来）。
                .verticalScroll(scroll)
                .padding(horizontal = m.dp(30f), vertical = m.dp(22f)),
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedContent(
                targetState = movie.id,
                transitionSpec = {
                    // 新片轻轻浮上来并淡入，旧片快速淡出
                    (fadeIn(Motion.swap()) + slideInVertically(Motion.swap()) { it / 12 })
                        .togetherWith(fadeOut(Motion.quick()))
                },
                label = "detailSwap",
            ) { targetId ->
                val shown = movieById[targetId] ?: movie
                val shownDetail = detailById[targetId]

                Column {
                    Box(
                        modifier = Modifier
                            .width(m.dp(140f))
                            .aspectRatio(2f / 3f)
                            .clip(shape),
                    ) {
                        PosterImage(
                            url = shown.posterUrl,
                            title = shown.title,
                            decodeWidth = 480,
                            decodeHeight = 720,
                        )
                    }

                    Spacer(Modifier.height(m.dp(20f)))
                    Text(
                        text = shown.title,
                        color = TextMain,
                        fontSize = m.sp(27f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = m.sp(0.3f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // ── 元信息：接口给了什么就显示什么，没有的不占位 ──────
                    val meta = buildMeta(shown, shownDetail)
                    Spacer(Modifier.height(m.dp(10f)))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (shown.score > 0) {
                            Text(
                                text = "★ " + "%.1f".format(Locale.US, shown.score),
                                color = Gold,
                                fontSize = m.sp(18f),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (meta.isNotEmpty()) {
                            Text(
                                text = (if (shown.score > 0) "  ·  " else "") +
                                    meta.joinToString("  ·  "),
                                color = TextSub,
                                fontSize = m.sp(16f),
                            )
                        }
                    }

                    // ── 简介：详情没到之前给一句人话，不出现空白 ──────────
                    val synopsis = shownDetail?.synopsis?.takeIf { it.isNotBlank() }
                        ?: shown.synopsis.takeIf { it.isNotBlank() }
                    Spacer(Modifier.height(m.dp(12f)))
                    Text(
                        text = synopsis ?: when {
                            detailLoading -> "正在读取简介…"
                            shown.remarks != null -> shown.remarks
                            else -> ""
                        },
                        color = if (synopsis == null) TextSub else TextBody,
                        fontSize = m.sp(16f),
                        lineHeight = m.sp(26f),
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // ── 线路/集数（有播放地址时才显示）────────────────────
                    shownDetail?.takeIf { it.lines.isNotEmpty() }?.let { d ->
                        Spacer(Modifier.height(m.dp(10f)))
                        Text(
                            text = "线路 ${d.lines.size} 条" +
                                if (d.episodeCount > 0) "  ·  共 ${d.episodeCount} 集" else "",
                            color = TextSub,
                            fontSize = m.sp(14f),
                        )
                    }

                    Spacer(Modifier.height(m.dp(24f)))
                    // 看过一半的片子，按钮直接变成"继续观看 12:34" —— 少一次思考
                    PlayButton(
                        onClick = onPlay,
                        text = if (resumeMs > 0) "继续观看 ${formatTime(resumeMs)}" else "播放",
                    )
                }
            }
        }
    }
}

/** 把"有值的"元信息拼成一串：没有的字段直接不出现，不留空占位 */
private fun buildMeta(movie: Movie, detail: MovieDetail?): List<String> = buildList {
    if (movie.year > 0) add(movie.year.toString())
    movie.genre.takeIf { it.isNotBlank() }?.let { add(it) }
    if (movie.minutes > 0) add("${movie.minutes} 分钟")
    detail?.area?.takeIf { it.isNotBlank() && it != "未知" }?.let { add(it) }
    detail?.lang?.takeIf { it.isNotBlank() && it != "未知" }?.let { add(it) }
}
