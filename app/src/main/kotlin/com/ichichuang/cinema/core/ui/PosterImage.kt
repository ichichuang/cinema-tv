package com.ichichuang.cinema.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ichichuang.cinema.core.design.rememberTvMetrics
import com.ichichuang.cinema.core.design.placeholderGradient

/**
 * 海报图 —— 全应用唯一的图片加载入口。
 *
 * 两件事决定了它必须是"一个组件"而不是到处写 AsyncImage：
 *
 * 1. **解码尺寸硬限制**。电视是 32 位系统、内存紧张，
 *    而片源站给的海报动辄 1000×1500，按原尺寸解码一张就是 6MB。
 *    这里统一压到 360×540（电视上肉眼无差），内存占用降到 1/4。
 *
 * 2. **永远不出现灰块**。图没下载完 / 接口没给图 / 下载失败时，
 *    底下永远垫着一层按片名生成的渐变 + 首字，视觉上不掉价。
 */
@Composable
fun PosterImage(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    decodeWidth: Int = 360,
    decodeHeight: Int = 540,
) {
    val m = rememberTvMetrics()

    Box(
        modifier = modifier.background(Brush.linearGradient(placeholderGradient(title))),
    ) {
        // 垫底的首字：图没到之前它是主体，图到了就被盖住
        Text(
            text = title.take(1),
            color = Color.White.copy(alpha = 0.18f),
            fontSize = m.sp(72f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center),
        )

        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .size(decodeWidth, decodeHeight)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
