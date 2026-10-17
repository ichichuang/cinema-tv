package com.ichichuang.cinema.core.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.ichichuang.cinema.core.design.Gold
import com.ichichuang.cinema.core.design.Ink
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
            Box(
                modifier = Modifier
                    .width(m.dp(170f))
                    .aspectRatio(2f / 3f)
                    .clip(shape),
            ) {
                PosterImage(
                    url = movie.posterUrl,
                    title = movie.title,
                    decodeWidth = 480,
                    decodeHeight = 720,
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

            // ── 元信息：接口给了什么就显示什么，没有的不占位 ──────
            val meta = buildMeta(movie, detail)
            Spacer(Modifier.height(m.dp(12f)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (movie.score > 0) {
                    Text(
                        text = "★ " + "%.1f".format(Locale.US, movie.score),
                        color = Gold,
                        fontSize = m.sp(19f),
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (meta.isNotEmpty()) {
                    Text(
                        text = (if (movie.score > 0) "  ·  " else "") + meta.joinToString("  ·  "),
                        color = TextSub,
                        fontSize = m.sp(18f),
                    )
                }
            }

            // ── 简介：详情没到之前给一句人话，不出现空白 ──────────
            val synopsis = detail?.synopsis?.takeIf { it.isNotBlank() }
                ?: movie.synopsis.takeIf { it.isNotBlank() }
            Spacer(Modifier.height(m.dp(16f)))
            Text(
                text = synopsis ?: when {
                    detailLoading -> "正在读取简介…"
                    movie.remarks != null -> movie.remarks
                    else -> ""
                },
                color = if (synopsis == null) TextSub else TextBody,
                fontSize = m.sp(17f),
                lineHeight = m.sp(28f),
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )

            // ── 线路/集数（有播放地址时才显示）────────────────────
            detail?.takeIf { it.lines.isNotEmpty() }?.let { d ->
                Spacer(Modifier.height(m.dp(12f)))
                Text(
                    text = "线路 ${d.lines.size} 条" +
                        if (d.episodeCount > 0) "  ·  共 ${d.episodeCount} 集" else "",
                    color = TextSub,
                    fontSize = m.sp(15f),
                )
            }

            Spacer(Modifier.height(m.dp(32f)))
            // 看过一半的片子，按钮直接变成"继续观看 12:34" —— 少一次思考
            PlayButton(
                onClick = onPlay,
                text = if (resumeMs > 0) "继续观看 ${formatTime(resumeMs)}" else "播放",
            )
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
