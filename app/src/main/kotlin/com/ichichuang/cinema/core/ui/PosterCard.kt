package com.ichichuang.cinema.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.ichichuang.cinema.core.design.Gold
import com.ichichuang.cinema.core.design.Ink
import com.ichichuang.cinema.core.design.Motion
import com.ichichuang.cinema.core.design.TextMain
import com.ichichuang.cinema.core.design.TextSub
import com.ichichuang.cinema.core.design.rememberTvMetrics
import com.ichichuang.cinema.core.focus.tvFocus
import com.ichichuang.cinema.domain.model.Movie
import java.util.Locale

/**
 * 海报卡 —— 首页的基础单元，也是全应用"焦点手感"的标准样本。
 *
 * 图片加载统一走 [PosterImage]（内部是 Coil3 + 解码尺寸限制 + 渐变兜底），
 * 所以这里只管排版、焦点和角标。
 */
@Composable
fun PosterCard(
    movie: Movie,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
) {
    val m = rememberTvMetrics()
    val radius = m.dp(14f)
    val shape = RoundedCornerShape(radius)
    // 焦点和按压共用同一个交互源：按下时立刻缩一下，是"这台电视跟手"的关键
    val interaction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    // 未聚焦时标题偏灰、聚焦时亮起来：扫视整墙时焦点落在哪儿一眼就能看出来
    val titleColor by animateColorAsState(
        targetValue = if (focused) TextMain else TextMain.copy(alpha = 0.72f),
        animationSpec = Motion.quick(),
        label = "posterTitle",
    )

    Column(
        modifier = modifier
            // ⭐ 一行拿到统一焦点动效（放大 + 光晕 + 按压回弹）
            .tvFocus(
                cornerRadius = radius,
                interactionSource = interaction,
                onFocusChanged = { isFocused ->
                    focused = isFocused
                    if (isFocused) onFocused()
                },
            )
            .clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) { onClick() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape),
        ) {
            // 真实海报（图没到 / 没有图时自动退化成渐变 + 首字，永远不会是灰块）
            PosterImage(
                url = movie.posterUrl,
                title = movie.title,
                decodeWidth = 360,
                decodeHeight = 540,
            )

            // 右上角评分（片源没给评分就不显示，不占位）
            if (movie.score > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(m.dp(10f))
                        .clip(RoundedCornerShape(m.dp(6f)))
                        .background(Ink.copy(alpha = 0.6f))
                        .padding(horizontal = m.dp(8f), vertical = m.dp(4f)),
                ) {
                    Text(
                        text = "★ " + "%.1f".format(Locale.US, movie.score),
                        color = Gold,
                        fontSize = m.sp(15f),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // 左下角备注（如"更新至12集"）
            movie.remarks?.let { r ->
                Text(
                    text = r,
                    color = TextMain,
                    fontSize = m.sp(14f),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(m.dp(10f))
                        .clip(RoundedCornerShape(m.dp(6f)))
                        .background(Ink.copy(alpha = 0.6f))
                        .padding(horizontal = m.dp(8f), vertical = m.dp(4f)),
                )
            }
        }

        Spacer(Modifier.height(m.dp(10f)))
        Text(
            text = movie.title,
            color = titleColor,
            fontSize = m.sp(20f),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // 有年份显示年份、有分类显示分类，都没有就显示备注，全没有就不留空行
        val subtitle = listOfNotNull(
            movie.year.takeIf { it > 0 }?.toString(),
            movie.genre.takeIf { it.isNotBlank() },
        ).joinToString(" · ").ifBlank { movie.remarks.orEmpty() }
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                color = TextSub,
                fontSize = m.sp(15f),
                maxLines = 1,
            )
        }
    }
}
