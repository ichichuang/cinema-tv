package com.ichichuang.cinema.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.toBitmap
import com.ichichuang.cinema.core.design.Panel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 海报图 —— 全应用唯一的图片加载入口。
 *
 * 三件事决定了它必须是"一个组件"而不是到处写 AsyncImage：
 *
 * 1. **解码尺寸硬限制**。电视是 32 位系统、内存紧张，
 *    而片源站给的海报动辄 1000×1500，按原尺寸解码一张就是 6MB。
 *    这里统一压到 360×540（电视上肉眼无差），内存占用降到 1/4。
 *
 * 2. **兜底永远安静**。图没到 / 站点没给图 / 下载失败时，垫一个
 *    和全局底色同调的深色块 —— 不闪白、不花哨、绝不用大字凑数。
 *
 * 3. **挡掉站点自动生成的"字块封面"**。有的站没海报就现画一张
 *    「纯色底 + 首字」的大图，在深色墙里像贴满彩纸。加载成功后先用
 *    [TextCoverGuard] 认一下，是字块就换成安静的深色块（结论按 URL 缓存）。
 */
@Composable
fun PosterImage(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    decodeWidth: Int = 360,
    decodeHeight: Int = 540,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val src = url?.takeIf { it.isNotBlank() }

    // 已知结论直接沿用：是字块封面就连图都不加载了
    val known = remember(src) { src?.let(TextCoverGuard::cached) }
    var hidden by remember(src) { mutableStateOf(known == true) }
    var checked by remember(src) { mutableStateOf(known != null) }

    Box(
        modifier = modifier.background(Panel),
    ) {
        if (src != null && !hidden) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(src)
                    .crossfade(true)
                    .size(decodeWidth, decodeHeight)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { state ->
                    if (!checked) {
                        checked = true
                        val bmp = runCatching { state.result.image.toBitmap() }.getOrNull()
                        if (bmp != null) {
                            scope.launch {
                                val isTextCover = withContext(Dispatchers.Default) {
                                    TextCoverGuard.detect(bmp)
                                }
                                TextCoverGuard.put(src, isTextCover)
                                if (isTextCover) hidden = true
                            }
                        }
                    }
                },
            )
        }
    }
}
